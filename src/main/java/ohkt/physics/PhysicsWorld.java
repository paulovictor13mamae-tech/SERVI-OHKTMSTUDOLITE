package ohkt.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fisica do mundo: colisores estaticos (predios, props) em spatial hash
 * para consulta rapida de circulos (personagens/veiculos) e raycasts (balas, camera).
 */
public final class PhysicsWorld {

    public static final float GRAVITY = 18f;
    private static final float CELL = 16f;

    private final Map<Long, List<AABB>> grid = new HashMap<>();
    private final Map<Object, List<AABB>> byOwner = new HashMap<>();
    private final List<AABB> queryResult = new ArrayList<>();

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public void addStatic(AABB box) {
        int x0 = cell(box.minX), x1 = cell(box.maxX);
        int z0 = cell(box.minZ), z1 = cell(box.maxZ);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                grid.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(box);
            }
        }
        byOwner.computeIfAbsent(box.owner, k -> new ArrayList<>()).add(box);
    }

    public void removeOwner(Object owner) {
        List<AABB> boxes = byOwner.remove(owner);
        if (boxes == null) return;
        for (AABB box : boxes) {
            int x0 = cell(box.minX), x1 = cell(box.maxX);
            int z0 = cell(box.minZ), z1 = cell(box.maxZ);
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    List<AABB> cellList = grid.get(key(cx, cz));
                    if (cellList != null) {
                        cellList.remove(box);
                        if (cellList.isEmpty()) grid.remove(key(cx, cz));
                    }
                }
            }
        }
    }

    public void clear() {
        grid.clear();
        byOwner.clear();
    }

    private static int cell(float v) { return (int) Math.floor(v / CELL); }

    /** Coleta AABBs proximos do ponto (reutiliza lista interna). */
    public List<AABB> query(float x, float z, float radius) {
        queryResult.clear();
        int x0 = cell(x - radius), x1 = cell(x + radius);
        int z0 = cell(z - radius), z1 = cell(z + radius);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                List<AABB> cellList = grid.get(key(cx, cz));
                if (cellList == null) continue;
                for (AABB b : cellList) {
                    if (!queryResult.contains(b)) queryResult.add(b);
                }
            }
        }
        return queryResult;
    }

    /**
     * Resolve colisao de um circulo (XZ) contra AABBs na faixa vertical [y0,y1].
     * Empurra (x,z) para fora e retorna a magnitude do empurrão.
     */
    public float resolveCircle(Position pos, float radius, float y0, float y1) {
        List<AABB> near = query(pos.x, pos.z, radius + 2f);
        float pushed = 0;
        for (int i = 0; i < near.size(); i++) {
            AABB b = near.get(i);
            if (!b.overlapsY(y0, y1)) continue;
            // ponto mais proximo no box
            float cx = Math.max(b.minX, Math.min(pos.x, b.maxX));
            float cz = Math.max(b.minZ, Math.min(pos.z, b.maxZ));
            float dx = pos.x - cx, dz = pos.z - cz;
            float d2 = dx * dx + dz * dz;
            if (d2 > radius * radius) continue;
            if (d2 < 1e-8f) {
                // dentro do box: empurra pelo eixo de menor penetracao
                float pushW = Math.min(pos.x - b.minX, b.maxX - pos.x);
                float pushD = Math.min(pos.z - b.minZ, b.maxZ - pos.z);
                if (pushW < pushD) {
                    pos.x += (pos.x - b.centerX() > 0 ? 1 : -1) * (pushW + radius);
                    pushed = Math.max(pushed, pushW + radius);
                } else {
                    pos.z += (pos.z - b.centerZ() > 0 ? 1 : -1) * (pushD + radius);
                    pushed = Math.max(pushed, pushD + radius);
                }
                continue;
            }
            float d = (float) Math.sqrt(d2);
            float push = radius - d;
            pos.x += dx / d * push;
            pos.z += dz / d * push;
            pushed = Math.max(pushed, push);
        }
        return pushed;
    }

    /** Raycast contra AABBs estaticos. Retorna no objeto hit (reuso). */
    public RaycastHit raycast(float ox, float oy, float oz, float dx, float dy, float dz, float maxDist) {
        RaycastHit out = new RaycastHit();
        out.set(false, maxDist, ox + dx * maxDist, oy + dy * maxDist, oz + dz * maxDist, 0, 1, 0);
        // DDA grosseiro por celulas percorridas
        int steps = (int) (maxDist / CELL) + 2;
        int cx = cell(ox), cz = cell(oz);
        int lastCx = cell(ox + dx * maxDist), lastCz = cell(oz + dz * maxDist);
        int stepX = Integer.compare(lastCx, cx), stepZ = Integer.compare(lastCz, cz);
        List<AABB> tested = new ArrayList<>();
        float bestT = maxDist + 1;
        AABB best = null;
        float bnx = 0, bny = 0, bnz = 0;
        for (int i = 0; i < steps; i++) {
            List<AABB> cellList = grid.get(key(cx, cz));
            if (cellList != null) {
                for (AABB b : cellList) {
                    if (tested.contains(b)) continue;
                    tested.add(b);
                    float t = slabRay(ox, oy, oz, dx, dy, dz, b, out);
                    if (out.hit && t < bestT) {
                        bestT = t;
                        best = b;
                        bnx = out.nx; bny = out.ny; bnz = out.nz;
                    }
                }
            }
            if (cx == lastCx && cz == lastCz) break;
            if (cx != lastCx) cx += stepX;
            if (cz != lastCz) cz += stepZ;
            if (Math.abs(cx - cell(ox)) > steps || Math.abs(cz - cell(oz)) > steps) break;
        }
        if (best != null && bestT <= maxDist) {
            out.set(true, bestT, ox + dx * bestT, oy + dy * bestT, oz + dz * bestT, bnx, bny, bnz);
        } else {
            out.set(false, maxDist, ox + dx * maxDist, oy + dy * maxDist, oz + dz * maxDist, 0, 1, 0);
        }
        return out;
    }

    /** Ray-AABB slab; escreve normal em hit e retorna t. */
    private float slabRay(float ox, float oy, float oz, float dx, float dy, float dz, AABB b, RaycastHit out) {
        float tmin = 0, tmax = Float.MAX_VALUE;
        float nx = 0, ny = 0, nz = 0;
        // eixo X
        if (Math.abs(dx) < 1e-9f) {
            if (ox < b.minX || ox > b.maxX) { out.hit = false; return -1; }
        } else {
            float t1 = (b.minX - ox) / dx, t2 = (b.maxX - ox) / dx;
            float sign = -1;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; sign = 1; }
            if (t1 > tmin) { tmin = t1; nx = sign; ny = 0; nz = 0; }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) { out.hit = false; return -1; }
        }
        if (Math.abs(dy) < 1e-9f) {
            if (oy < b.minY || oy > b.maxY) { out.hit = false; return -1; }
        } else {
            float t1 = (b.minY - oy) / dy, t2 = (b.maxY - oy) / dy;
            float sign = -1;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; sign = 1; }
            if (t1 > tmin) { tmin = t1; nx = 0; ny = sign; nz = 0; }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) { out.hit = false; return -1; }
        }
        if (Math.abs(dz) < 1e-9f) {
            if (oz < b.minZ || oz > b.maxZ) { out.hit = false; return -1; }
        } else {
            float t1 = (b.minZ - oz) / dz, t2 = (b.maxZ - oz) / dz;
            float sign = -1;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; sign = 1; }
            if (t1 > tmin) { tmin = t1; nx = 0; ny = 0; nz = sign; }
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) { out.hit = false; return -1; }
        }
        out.hit = true;
        out.nx = nx; out.ny = ny; out.nz = nz;
        return tmin;
    }

    /** Posicao mutavel simples usada na resolucao. */
    public static final class Position {
        public float x, z;

        public Position(float x, float z) {
            this.x = x;
            this.z = z;
        }
    }
}
