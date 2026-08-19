package ohkt.utils;

/** Utilidades de cor ARGB. Cores sempre int ARGB. */
public final class ColorUtil {
    private ColorUtil() {}

    public static int rgb(int r, int g, int b) { return 0xff000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b); }

    public static int rgba(int r, int g, int b, int a) { return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b); }

    public static int withAlpha(int argb, int a) { return (argb & 0x00ffffff) | (clamp255(a) << 24); }

    public static int r(int c) { return (c >> 16) & 0xff; }

    public static int g(int c) { return (c >> 8) & 0xff; }

    public static int b(int c) { return c & 0xff; }

    public static int a(int c) { return (c >>> 24) & 0xff; }

    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /** Mistura a->b pelo fator t (0..1). */
    public static int mix(int c0, int c1, float t) {
        if (t < 0) t = 0; else if (t > 1) t = 1;
        int r = (int) (r(c0) + (r(c1) - r(c0)) * t);
        int g = (int) (g(c0) + (g(c1) - g(c0)) * t);
        int b = (int) (b(c0) + (b(c1) - b(c0)) * t);
        return rgb(r, g, b);
    }

    /** Multiplica brilho (0..~2). */
    public static int shade(int c, float f) {
        return rgb((int) (r(c) * f), (int) (g(c) * f), (int) (b(c) * f));
    }

    /** HSL para RGB (h 0..360, s,l 0..1). */
    public static int hsl(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float hp = (h % 360f) / 60f;
        float x = c * (1 - Math.abs((hp % 2) - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = l - c / 2;
        return rgb((int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
    }

    /** Variacao deterministica de uma cor base. */
    public static int vary(int base, long seed, float amount) {
        float h = (seed & 1023) / 1023f * amount - amount / 2;
        return rgb((int) (r(base) * (1 + h)), (int) (g(base) * (1 + h)), (int) (b(base) * (1 + h)));
    }

    /** Escurece/mostra como molhado (chuva). */
    public static int wet(int c, float wetness) {
        return mix(c, rgb(20, 24, 34), wetness * 0.45f);
    }
}
