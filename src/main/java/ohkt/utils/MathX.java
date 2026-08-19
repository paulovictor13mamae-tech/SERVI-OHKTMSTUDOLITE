package ohkt.utils;

import java.util.Random;

/** Matematica utilitaria + ruido Perlin (deterministico por seed). */
public final class MathX {
    public static final float PI = (float) Math.PI;

    private MathX() {}

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static float len(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public static float smooth(float t) { return t * t * (3 - 2 * t); }

    public static float wrapAngle(float a) {
        while (a > PI) a -= 2 * PI;
        while (a < -PI) a += 2 * PI;
        return a;
    }

    /** Diferenca assinada entre angulos (-PI..PI). */
    public static float angleDiff(float from, float to) { return wrapAngle(to - from); }

    public static float approach(float cur, float target, float maxDelta) {
        float d = target - cur;
        if (d > maxDelta) d = maxDelta;
        if (d < -maxDelta) d = -maxDelta;
        return cur + d;
    }

    public static boolean chance(Random rnd, float p) { return rnd.nextFloat() < p; }

    public static float randRange(Random rnd, float a, float b) { return a + rnd.nextFloat() * (b - a); }

    public static int randRange(Random rnd, int a, int b) { return a + rnd.nextInt(b - a + 1); }

    public static <T> T pick(Random rnd, T[] arr) { return arr[rnd.nextInt(arr.length)]; }

    // ---------------- Perlin ----------------

    private static final int[] PERM = new int[512];

    /** Inicializa permutacao Perlin deterministica. */
    public static void seedNoise(long seed) {
        Random r = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) PERM[i] = p[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static float grad(int hash, float x, float y) {
        switch (hash & 7) {
            case 0: return x + y;
            case 1: return x - y;
            case 2: return -x + y;
            case 3: return -x - y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }

    /** Ruido 2D em [-1,1]. Chame seedNoise antes. */
    public static float perlin(float x, float y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        float u = fade(xf), v = fade(yf);
        int aa = PERM[PERM[xi] + yi];
        int ab = PERM[PERM[xi] + yi + 1];
        int ba = PERM[PERM[xi + 1] + yi];
        int bb = PERM[PERM[xi + 1] + yi + 1];
        float x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        float x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    /** Hash inteiro deterministico. */
    public static long hash(long x) {
        x ^= x >>> 33; x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33; x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    public static long hash2(long a, long b) { return hash(a * 341873128712L + b * 132897987541L); }

    /** RNG deterministico por coordenadas de chunk (mundo estavel). */
    public static Random chunkRandom(long worldSeed, int cx, int cz) {
        return new Random(worldSeed ^ hash2(cx, cz));
    }
}
