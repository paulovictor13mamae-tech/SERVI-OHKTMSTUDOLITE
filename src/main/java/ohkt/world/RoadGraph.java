package ohkt.world;

import ohkt.utils.MathX;

import java.util.ArrayList;
import java.util.List;

/**
 * Grafo de ruas (nos nas interseccoes) usado por trafego, policia e pedestres.
 */
public final class RoadGraph {

    public final int n = CityLayout.NB + 1; // 27x27 nos

    public float nodeX(int kx) { return CityLayout.roadCoord(kx); }

    public float nodeZ(int kz) { return CityLayout.roadCoord(kz); }

    public boolean inRange(int kx, int kz) {
        return kx >= 0 && kx < n && kz >= 0 && kz < n;
    }

    /** No mais proximo. */
    public int[] nearestNode(float x, float z) {
        int kx = Math.round((x - CityLayout.ORIGIN) / CityLayout.BLOCK);
        int kz = Math.round((z - CityLayout.ORIGIN) / CityLayout.BLOCK);
        return new int[]{MathX.clamp(kx, 0, n - 1), MathX.clamp(kz, 0, n - 1)};
    }

    /**
     * Fase do semaforo: 0=NS verde, 1=NS amarelo, 2=EW verde, 3=EW amarelo.
     * Sem semaforo quando ambas as ruas sao menores (retorna -1).
     */
    public int lightPhase(int kx, int kz, float worldTime) {
        boolean major = CityLayout.isMajor(kx) || CityLayout.isMajor(kz);
        if (!major) return -1;
        float t = (worldTime + (kx * 3 + kz * 5)) % 16f;
        if (t < 7) return 0;
        if (t < 8) return 1;
        if (t < 15) return 2;
        return 3;
    }

    /** Pode atravessar/cruzar na direcao NS (movendo-se em Z)? */
    public boolean greenForNS(int phase) { return phase == 0 || phase == 1; }

    /** Waypoint de faixa (mao direita) entre dois nos adjacentes. */
    public float[] lanePoint(int fromKx, int fromKz, int toKx, int toKz, float t) {
        float x0 = CityLayout.roadCoord(fromKx), z0 = CityLayout.roadCoord(fromKz);
        float x1 = CityLayout.roadCoord(toKx), z1 = CityLayout.roadCoord(toKz);
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        dx /= len; dz /= len;
        // direita = (-dz, dx)
        float roadK = Math.abs(dx) > 0.5 ? CityLayout.halfWidth(toKx) : CityLayout.halfWidth(toKz);
        float lane = Math.min(roadK - 2.2f, 3.2f);
        return new float[]{
                x0 + (x1 - x0) * t - dz * lane,
                z0 + (z1 - z0) * t + dx * lane
        };
    }

    /** Vizinhos validos de um no. */
    public List<int[]> neighbors(int kx, int kz) {
        List<int[]> out = new ArrayList<>(4);
        if (inRange(kx + 1, kz)) out.add(new int[]{kx + 1, kz});
        if (inRange(kx - 1, kz)) out.add(new int[]{kx - 1, kz});
        if (inRange(kx, kz + 1)) out.add(new int[]{kx, kz + 1});
        if (inRange(kx, kz - 1)) out.add(new int[]{kx, kz - 1});
        return out;
    }

    /** Caminho A* simples entre nos (grid -> quase reto, mas generico). */
    public List<int[]> path(int fromKx, int fromKz, int toKx, int toKz) {
        if (!inRange(fromKx, fromKz) || !inRange(toKx, toKz)) return null;
        java.util.PriorityQueue<int[]> open = new java.util.PriorityQueue<>(64,
                (a, b) -> Integer.compare(a[2], b[2]));
        int size = n * n;
        int[] g = new int[size];
        int[] parent = new int[size];
        boolean[] closed = new boolean[size];
        java.util.Arrays.fill(parent, -1);
        java.util.Arrays.fill(g, Integer.MAX_VALUE);
        int start = fromKz * n + fromKx;
        int goal = toKz * n + toKx;
        g[start] = 0;
        open.add(new int[]{start, 0, Math.abs(toKx - fromKx) + Math.abs(toKz - fromKz)});
        while (!open.isEmpty()) {
            int[] cur = open.poll();
            int node = cur[0];
            if (node == goal) break;
            if (closed[node]) continue;
            closed[node] = true;
            int cx = node % n, cz = node / n;
            for (int[] nb : neighbors(cx, cz)) {
                int nn = nb[1] * n + nb[0];
                if (closed[nn]) continue;
                int ng = g[node] + 1;
                if (ng < g[nn]) {
                    g[nn] = ng;
                    parent[nn] = node;
                    open.add(new int[]{nn, ng, ng + Math.abs(toKx - nb[0]) + Math.abs(toKz - nb[1])});
                }
            }
        }
        if (parent[goal] == -1 && goal != start) return null;
        List<int[]> path = new ArrayList<>();
        int cur = goal;
        while (cur != -1) {
            path.add(new int[]{cur % n, cur / n});
            cur = parent[cur];
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /** No aleatorio proximo de (x,z) dentro de um raio em nos. */
    public int[] randomNodeNear(java.util.Random rnd, float x, float z, int minNodes, int maxNodes) {
        int[] base = nearestNode(x, z);
        for (int tries = 0; tries < 12; tries++) {
            int d = minNodes + rnd.nextInt(Math.max(1, maxNodes - minNodes + 1));
            int dir = rnd.nextInt(4);
            int kx = base[0], kz = base[1];
            if (dir == 0) kx += d; else if (dir == 1) kx -= d;
            else if (dir == 2) kz += d; else kz -= d;
            if (inRange(kx, kz)) return new int[]{kx, kz};
        }
        return base;
    }
}
