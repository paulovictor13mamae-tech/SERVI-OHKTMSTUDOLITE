package ohkt.world;

import ohkt.graphics.Mesh;
import ohkt.graphics.Renderer3D;
import ohkt.physics.AABB;
import ohkt.physics.PhysicsWorld;
import ohkt.utils.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mundo aberto: cidade em chunks com streaming, praia, mar, calçadão e a
 * Ilha do Farol. Gerencia tempo, clima e grafo de ruas.
 */
public final class World {

    public final long seed;
    public final PhysicsWorld physics = new PhysicsWorld();
    public final RoadGraph roadGraph = new RoadGraph();
    public final TimeSystem time = new TimeSystem();
    public final WeatherSystem weather = new WeatherSystem();

    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final List<int[]> buildQueue = new ArrayList<>();
    public int loadRadius = 3;

    // geometria global (ilha, calçadão, praia)
    private Mesh islandMeshDay, islandMeshNight, causewayMesh;
    private final List<float[]> islandLamps = new ArrayList<>();

    public interface ChunkListener {
        void onChunkLoaded(Chunk c);

        void onChunkUnloaded(Chunk c);
    }

    private final List<ChunkListener> chunkListeners = new ArrayList<>();

    public World(long seed) {
        this.seed = seed;
        buildGlobalGeometry();
        addBoundaryColliders();
    }

    public void addChunkListener(ChunkListener l) { chunkListeners.add(l); }

    private void buildGlobalGeometry() {
        Random rnd = new Random(seed ^ 0x5eed17);
        Mesh.Builder day = new Mesh.Builder();
        Mesh.Builder night = new Mesh.Builder();
        Mesh.Builder causeway = new Mesh.Builder();

        float ix = CityLayout.ISLAND_X, iz = CityLayout.ISLAND_Z, ir = CityLayout.ISLAND_R;

        // praia (faixa de areia entre cidade e mar)
        float bz0 = CityLayout.BEACH_Z, wz = CityLayout.WATER_Z;
        day.groundQuad(CityLayout.MIN_X, bz0, CityLayout.MAX_X, wz, 0.02f, 0xffc8b888);
        night.groundQuad(CityLayout.MIN_X, bz0, CityLayout.MAX_X, wz, 0.02f, 0xff3a382e);

        // ilha
        for (int a = 0; a < 26; a++) {
            double ang = Math.PI * 2 * a / 26;
            double ang2 = Math.PI * 2 * (a + 1) / 26;
            float r0 = ir * (0.9f + rnd.nextFloat() * 0.15f);
            float r1 = ir * (0.9f + rnd.nextFloat() * 0.15f);
            float x0 = ix + (float) Math.cos(ang) * r0, z0 = iz + (float) Math.sin(ang) * r0;
            float x1 = ix + (float) Math.cos(ang2) * r1, z1 = iz + (float) Math.sin(ang2) * r1;
            day.groundQuad(x0 - 2, z0 - 2, x1 + 2, z1 + 2, 0.1f, 0xffd4bc8c);
            night.groundQuad(x0 - 2, z0 - 2, x1 + 2, z1 + 2, 0.1f, 0xff46402f);
        }
        // gramado interno
        day.groundQuad(ix - ir * 0.75f, iz - ir * 0.75f, ix + ir * 0.75f, iz + ir * 0.75f, 0.12f, 0xff48753c);
        night.groundQuad(ix - ir * 0.75f, iz - ir * 0.75f, ix + ir * 0.75f, iz + ir * 0.75f, 0.12f, 0xff1c2c1a);

        // farol
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.cylinder(ix, 0.1f, iz + 8, 3.2f, 22f, 10, 0xfff0f0f0, 0xfff8f8f8);
            b.cylinder(ix, 22f, iz + 8, 2.2f, 23.5f, 8, 0xffc02020, 0xffd03030);
            // casa do faroleiro
            b.wallQuads(ix - 14, iz - 6, ix - 6, iz + 2, 4f, 0xffc8b8a0);
            b.box(ix - 14.5f, 4f, iz - 6.5f, ix - 5.5f, 5.6f, iz + 2.5f, 0xff8a4a34);
            // palms fake (troncos altos com folha caixa)
            for (int p = 0; p < 5; p++) {
                float px = ix - 20 + rnd.nextFloat() * 40;
                float pz = iz - 20 + rnd.nextFloat() * 40;
                b.box(px - 0.2f, 0, pz - 0.2f, px + 0.2f, 5.5f, pz + 0.2f, 0xff8a6c48);
                b.box(px - 1.6f, 5.5f, pz - 1.6f, px + 1.6f, 6.3f, pz + 1.6f, 0xff2e7840);
            }
        }
        physics.addStatic(new AABB(ix - 3.3f, 0, iz + 8 - 3.3f, ix + 3.3f, 22, iz + 8 + 3.3f, this));
        physics.addStatic(new AABB(ix - 14, 0, iz - 6, ix - 6, 4, iz + 2, this));
        islandLamps.add(new float[]{ix, 21f, iz + 8});

        // calçadão para a ilha
        float cw = 9f;
        causeway.groundQuad(ix - cw, wz - 30, ix + cw, iz - 2, 0.1f, 0xff6a6a68);
        for (Mesh.Builder b : new Mesh.Builder[]{causeway}) {
            // guardas laterais
            b.box(ix - cw, 0, wz - 30, ix - cw + 0.4f, 0.9f, iz - 2, 0xffb8b0a0);
            b.box(ix + cw - 0.4f, 0, wz - 30, ix + cw, 0.9f, iz - 2, 0xffb8b0a0);
        }
        physics.addStatic(new AABB(ix - cw, 0, wz - 30, ix - cw + 0.4f, 0.9f, iz - 2, this));
        physics.addStatic(new AABB(ix + cw - 0.4f, 0, wz - 30, ix + cw, 0.9f, iz - 2, this));
        // faixa central amarela do calçadão
        causeway.groundQuad(ix - 0.15f, wz - 28, ix + 0.15f, iz - 4, 0.105f, 0xffb8a030);

        causewayMesh = causeway.seal();
        islandMeshDay = day.seal();
        islandMeshNight = night.seal();
    }

    private void addBoundaryColliders() {
        float t = 200f; // altura das paredes invisiveis
        physics.addStatic(new AABB(CityLayout.MIN_X - 10, 0, CityLayout.MIN_Z - 10, CityLayout.MIN_X, t, CityLayout.MAX_Z, this));
        physics.addStatic(new AABB(CityLayout.MAX_X, 0, CityLayout.MIN_Z - 10, CityLayout.MAX_X + 10, t, CityLayout.MAX_Z, this));
        physics.addStatic(new AABB(CityLayout.MIN_X - 10, 0, CityLayout.MIN_Z - 10, CityLayout.MAX_X + 10, t, CityLayout.MIN_Z, this));
        physics.addStatic(new AABB(CityLayout.MIN_X - 10, 0, CityLayout.MAX_Z, CityLayout.MAX_X + 10, t, CityLayout.MAX_Z + 10, this));
    }

    // ---------------- streaming ----------------

    public void update(float dt, float px, float pz, boolean withWindows) {
        time.update(dt);
        weather.update(dt);
        int[] pc = CityLayout.blockOf(px, pz);
        // enfileira chunks faltantes
        buildQueue.clear();
        for (int di = -loadRadius; di <= loadRadius; di++) {
            for (int dj = -loadRadius; dj <= loadRadius; dj++) {
                int i = pc[0] + di, j = pc[1] + dj;
                if (i < 0 || i >= CityLayout.NB || j < 0 || j >= CityLayout.NB) continue;
                if (!chunks.containsKey(key(i, j))) {
                    buildQueue.add(new int[]{i, j, di * di + dj * dj});
                }
            }
        }
        buildQueue.sort((a, b) -> Integer.compare(a[2], b[2]));
        // constrói até 1 chunk por frame (streaming suave)
        if (!buildQueue.isEmpty()) {
            int[] next = buildQueue.remove(0);
            loadChunk(next[0], next[1], withWindows);
        }
        // descarrega distantes
        int lim = loadRadius + 1;
        List<Long> toRemove = null;
        for (Map.Entry<Long, Chunk> e : chunks.entrySet()) {
            Chunk c = e.getValue();
            if (Math.abs(c.i - pc[0]) > lim || Math.abs(c.j - pc[1]) > lim) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(e.getKey());
            }
        }
        if (toRemove != null) {
            for (Long k : toRemove) {
                Chunk c = chunks.remove(k);
                c.unload(physics);
                for (ChunkListener l : chunkListeners) l.onChunkUnloaded(c);
            }
        }
    }

    private void loadChunk(int i, int j, boolean withWindows) {
        Chunk c = new Chunk(this, i, j);
        c.build(physics, withWindows);
        chunks.put(key(i, j), c);
        for (ChunkListener l : chunkListeners) l.onChunkLoaded(c);
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xffffffffL);
    }

    public Chunk chunkAt(int i, int j) { return chunks.get(key(i, j)); }

    public java.util.Collection<Chunk> loadedChunks() { return chunks.values(); }

    // ---------------- render ----------------

    public void render(Renderer3D r, float camX, float camZ, float detailDist, float farDist, int quality) {
        boolean night = time.isNight();
        float nightF = time.nightFactor();

        // agua
        if (camZ > CityLayout.BEACH_Z - 80 || nearIsland(camX, camZ)) {
            renderWater(r, camX, camZ);
        }

        // chunks
        for (Chunk c : chunks.values()) {
            float dx = c.cx - camX, dz = c.cz - camZ;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist - c.radius > farDist) continue;
            if (!r.sphereVisible(c.cx, 6, c.cz, c.radius + 30)) continue;
            Mesh m = dist < detailDist ? (night ? c.nightMesh : c.dayMesh) : c.shellMesh;
            r.drawMesh(m, 0, 0, 0, 0, 1);
        }

        // ilha e calçadão
        if (camZ > CityLayout.BEACH_Z - 120) {
            if (r.sphereVisible(CityLayout.ISLAND_X, 10, CityLayout.ISLAND_Z, CityLayout.ISLAND_R + 30)) {
                r.drawMesh(night ? islandMeshNight : islandMeshDay, 0, 0, 0, 0, 1);
            }
            r.drawMesh(causewayMesh, 0, 0, 0, 0, 1);
        }

        // lampadas acesas a noite
        if (nightF > 0.25f) {
            for (Chunk c : chunks.values()) {
                float dx = c.cx - camX, dz = c.cz - camZ;
                if (dx * dx + dz * dz > 120 * 120) continue;
                for (float[] lamp : c.lamps) {
                    if (!r.sphereVisible(lamp[0], lamp[1], lamp[2], 2)) continue;
                    r.drawSprite(lamp[0], lamp[1], lamp[2], 1.6f, ColorUtil.rgba(255, 235, 170, (int) (140 * nightF)), 2);
                    r.drawGroundDisk(lamp[0] + (lamp[0] - c.cx) * 0.02f, lamp[2], 5.5f, 0.14f,
                            ColorUtil.rgba(255, 230, 150, (int) (38 * nightF)), 2);
                }
            }
            for (float[] lamp : islandLamps) {
                r.drawSprite(lamp[0], lamp[1], lamp[2], 2.4f, ColorUtil.rgba(255, 240, 190, 150), 2);
            }
        }

        // semaforos dinamicos (estados)
        for (Chunk c : chunks.values()) {
            for (int[] node : c.trafficNodes) {
                float nx = CityLayout.roadCoord(node[0]), nz = CityLayout.roadCoord(node[1]);
                float dx = nx - camX, dz = nz - camZ;
                if (dx * dx + dz * dz > 130 * 130) continue;
                int phase = roadGraph.lightPhase(node[0], node[1], time.worldTime);
                if (phase < 0) continue;
                boolean nsGreen = phase == 0 || phase == 1;
                int colNS = nsGreen ? 0xff30e040 : 0xffe03020;
                int colEW = !nsGreen && phase != 3 ? 0xff30e040 : 0xffe03020;
                float hw = CityLayout.halfWidth(node[0]);
                float hz = CityLayout.halfWidth(node[1]);
                // lampadas NS (norte e sul do cruzamento)
                r.drawBox(nx, 5.4f, nz - hz - 0.6f, 0.22f, 0.22f, 0.22f, 0, 0, 0, colNS, true);
                r.drawBox(nx, 5.4f, nz + hz + 0.6f, 0.22f, 0.22f, 0.22f, 0, 0, 0, colNS, true);
                r.drawBox(nx - hw - 0.6f, 5.4f, nz, 0.22f, 0.22f, 0.22f, 0, 0, 0, colEW, true);
                r.drawBox(nx + hw + 0.6f, 5.4f, nz, 0.22f, 0.22f, 0.22f, 0, 0, 0, colEW, true);
            }
        }
    }

    private boolean nearIsland(float x, float z) {
        float dx = x - CityLayout.ISLAND_X, dz = z - CityLayout.ISLAND_Z;
        return dx * dx + dz * dz < 200 * 200;
    }

    private void renderWater(Renderer3D r, float camX, float camZ) {
        // grade de quads de agua ao redor da camera (animada por tempo)
        float t = time.worldTime;
        int base = ColorUtil.mix(0xff1c4a68, 0xff2a5a7c, 0.5f + 0.5f * (float) Math.sin(t * 0.2f));
        int waterCol = ColorUtil.withAlpha(ColorUtil.mix(base, 0x0a1420, time.nightFactor() * 0.6f), 210);
        float x0 = camX - 70, x1 = camX + 70;
        float z0 = camZ - 70, z1 = camZ + 70;
        for (float x = x0; x < x1; x += 35) {
            for (float z = z0; z < z1; z += 35) {
                if (z < CityLayout.WATER_Z && !CityLayout.isWater(x + 17, z + 17)) continue;
                r.drawGroundQuad(x, z, x + 35, z + 35, 0.02f, waterCol, false);
            }
        }
    }

    // ---------------- consultas ----------------

    public static float groundHeight(float x, float z) {
        return CityLayout.groundHeight(x, z);
    }

    public static boolean isWater(float x, float z) {
        return CityLayout.isWater(x, z);
    }

    /** Porta mais proxima dentro do raio. */
    public Door nearestDoor(float x, float z, float maxDist) {
        Door best = null;
        float bestD = maxDist * maxDist;
        for (Chunk c : chunks.values()) {
            float dx = c.cx - x, dz = c.cz - z;
            if (dx * dx + dz * dz > (maxDist + 60) * (maxDist + 60)) continue;
            for (Door d : c.doors) {
                float ddx = d.x - x, ddz = d.z - z;
                float dd = ddx * ddx + ddz * ddz;
                if (dd < bestD) {
                    bestD = dd;
                    best = d;
                }
            }
        }
        return best;
    }

    /** Posicao livre na calada mais proxima (spawn de NPCs/eventos). */
    public float[] sidewalkPointNear(float x, float z, Random rnd) {
        for (int tries = 0; tries < 10; tries++) {
            int i = CityLayout.clampBlock((int) Math.floor((x - CityLayout.ORIGIN) / CityLayout.BLOCK) + rnd.nextInt(3) - 1);
            int j = CityLayout.clampBlock((int) Math.floor((z - CityLayout.ORIGIN) / CityLayout.BLOCK) + rnd.nextInt(3) - 1);
            Chunk c = chunkAt(i, j);
            if (c != null && !c.pedSpawns.isEmpty()) {
                float[] p = c.pedSpawns.get(rnd.nextInt(c.pedSpawns.size()));
                return new float[]{p[0], p[1]};
            }
        }
        return new float[]{x, z};
    }
}
