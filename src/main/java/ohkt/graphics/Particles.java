package ohkt.graphics;

import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;

import java.util.Random;

/**
 * Sistema de particulas com object pooling (zero alocacao por emissao).
 * Suporta fumaca, fogo, fascas, sangue, caixotes, chuva e explosoes.
 */
public final class Particles {
    public static final int MAX = 2600;
    public static final int MODE_ALPHA = 0, MODE_ADD = 1, MODE_TRAIL = 2;

    private final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX];
    private final float[] size0 = new float[MAX], size1 = new float[MAX];
    private final int[] col0 = new int[MAX], col1 = new int[MAX];
    private final float[] grav = new float[MAX], drag = new float[MAX];
    private final int[] mode = new int[MAX];
    private final boolean[] active = new boolean[MAX];
    private int cursor = 0;
    public int count;
    private final Random rnd = new Random(7);

    private int alloc() {
        for (int i = 0; i < MAX; i++) {
            int idx = (cursor + i) % MAX;
            if (!active[idx]) {
                cursor = (idx + 1) % MAX;
                return idx;
            }
        }
        return -1;
    }

    private void spawn(float x, float y, float z, float dx, float dy, float dz,
                       float lifeSec, float s0, float s1, int c0, int c1,
                       float g, float dr, int m) {
        int i = alloc();
        if (i < 0) return;
        active[i] = true;
        px[i] = x; py[i] = y; pz[i] = z;
        vx[i] = dx; vy[i] = dy; vz[i] = dz;
        life[i] = lifeSec; maxLife[i] = lifeSec;
        size0[i] = s0; size1[i] = s1;
        col0[i] = c0; col1[i] = c1;
        grav[i] = g; drag[i] = dr;
        mode[i] = m;
    }

    public void update(float dt) {
        count = 0;
        for (int i = 0; i < MAX; i++) {
            if (!active[i]) continue;
            life[i] -= dt;
            if (life[i] <= 0) { active[i] = false; continue; }
            vy[i] -= grav[i] * dt;
            float d = 1 - drag[i] * dt;
            vx[i] *= d; vy[i] *= d; vz[i] *= d;
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt;
            if (py[i] < 0.02f && mode[i] != MODE_TRAIL) { py[i] = 0.02f; vy[i] = 0; vx[i] *= 0.8f; vz[i] *= 0.8f; }
            count++;
        }
    }

    public void render(Renderer3D r) {
        for (int i = 0; i < MAX; i++) {
            if (!active[i]) continue;
            float t = 1 - life[i] / maxLife[i];
            float size = MathX.lerp(size0[i], size1[i], t);
            int col = ColorUtil.mix(col0[i], col1[i], t);
            if (mode[i] == MODE_TRAIL) {
                r.drawTracer(px[i], py[i], pz[i], px[i] - vx[i] * 0.03f, py[i] - vy[i] * 0.03f, pz[i] - vz[i] * 0.03f, size, col);
            } else {
                r.drawSprite(px[i], py[i], pz[i], size, col, mode[i]);
            }
        }
    }

    // ---------------- emissores ----------------

    public void muzzleFlash(float x, float y, float z, float dx, float dy, float dz) {
        for (int i = 0; i < 4; i++) {
            spawn(x, y, z, dx * 6 + rnd.nextFloat() * 3, dy * 6 + rnd.nextFloat() * 3, dz * 6 + rnd.nextFloat() * 3,
                    0.06f, 0.5f, 0.05f, 0xffffe0a0, 0x30ff6000, 0, 0, MODE_ADD);
        }
    }

    public void impactSparks(float x, float y, float z, float nx, float ny, float nz) {
        for (int i = 0; i < 8; i++) {
            spawn(x + nx * 0.05f, y + ny * 0.05f, z + nz * 0.05f,
                    nx * 4 + rnd.nextFloat() * 6 - 3, ny * 4 + rnd.nextFloat() * 5, nz * 4 + rnd.nextFloat() * 6 - 3,
                    0.28f, 0.09f, 0.01f, 0xffffffb0, 0x50ff8000, 9.8f, 1.5f, MODE_ADD);
        }
        spawn(x, y, z, 0, 0.6f, 0, 0.5f, 0.25f, 0.55f, 0x60808080, 0x00808080, 0, 0, MODE_ALPHA);
    }

    public void blood(float x, float y, float z) {
        for (int i = 0; i < 10; i++) {
            spawn(x, y, z, rnd.nextFloat() * 4 - 2, rnd.nextFloat() * 3, rnd.nextFloat() * 4 - 2,
                    0.5f, 0.1f, 0.04f, 0xffa01818, 0xff600808, 9.8f, 0.8f, MODE_ALPHA);
        }
    }

    public void smoke(float x, float y, float z, float amount) {
        for (int i = 0; i < amount; i++) {
            spawn(x + rnd.nextFloat() * 0.5f - 0.25f, y, z + rnd.nextFloat() * 0.5f - 0.25f,
                    rnd.nextFloat() * 0.6f - 0.3f, 1.2f + rnd.nextFloat(), rnd.nextFloat() * 0.6f - 0.3f,
                    1.6f + rnd.nextFloat(), 0.35f, 1.6f, 0x50505050, 0x00444444, -0.4f, 0.4f, MODE_ALPHA);
        }
    }

    public void fire(float x, float y, float z, float amount) {
        for (int i = 0; i < amount; i++) {
            spawn(x + rnd.nextFloat() * 0.6f - 0.3f, y + rnd.nextFloat() * 0.3f, z + rnd.nextFloat() * 0.6f - 0.3f,
                    rnd.nextFloat() * 0.8f - 0.4f, 2 + rnd.nextFloat() * 2.5f, rnd.nextFloat() * 0.8f - 0.4f,
                    0.5f + rnd.nextFloat() * 0.4f, 0.7f, 0.15f, 0xffffc040, 0x20ff3000, -1.5f, 0.8f, MODE_ADD);
        }
    }

    public void explosion(float x, float y, float z) {
        fire(x, y + 0.5f, z, 60);
        smoke(x, y + 1, z, 40);
        for (int i = 0; i < 30; i++) {
            float a = rnd.nextFloat() * 6.28f, s = 4 + rnd.nextFloat() * 14;
            spawn(x, y + 0.5f, z, (float) Math.cos(a) * s, 3 + rnd.nextFloat() * 12, (float) Math.sin(a) * s,
                    0.9f, 0.12f, 0.02f, 0xffffe0b0, 0x30ff6010, 9.8f, 0.4f, MODE_ADD);
        }
        spawn(x, y + 0.6f, z, 0, 0.4f, 0, 0.5f, 1f, 7f, 0x90fff0c0, 0x00fff0c0, 0, 0, MODE_ADD);
    }

    public void casing(float x, float y, float z, float dirX, float dirZ) {
        spawn(x, y, z, dirX * 2 + rnd.nextFloat() - 0.5f, 2 + rnd.nextFloat(), dirZ * 2 + rnd.nextFloat() - 0.5f,
                1.2f, 0.05f, 0.05f, 0xffd0b040, 0xffd0b040, 9.8f, 0.2f, MODE_ADD);
    }

    public void tireSmoke(float x, float y, float z) {
        spawn(x, y, z, rnd.nextFloat() * 0.5f, 0.7f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * 0.5f,
                0.9f, 0.4f, 1.4f, 0x70909090, 0x00909090, -0.2f, 1.2f, MODE_ALPHA);
    }

    public void splash(float x, float y, float z) {
        for (int i = 0; i < 6; i++) {
            spawn(x, y, z, rnd.nextFloat() * 3 - 1.5f, rnd.nextFloat() * 3 + 1, rnd.nextFloat() * 3 - 1.5f,
                    0.4f, 0.08f, 0.02f, 0xc0d0e8f0, 0x4090b0d0, 9.8f, 0.5f, MODE_ALPHA);
        }
    }

    /** Chuva: gotas ao redor da camera. Chamar por frame com intensidade 0..1. */
    public void updateRain(float intensity, float cx, float cy, float cz, float windX, float windZ) {
        if (intensity <= 0.01f) return;
        int n = (int) (26 * intensity);
        for (int i = 0; i < n; i++) {
            float x = cx + rnd.nextFloat() * 44 - 22;
            float z = cz + rnd.nextFloat() * 44 - 22;
            float y = cy + 12 + rnd.nextFloat() * 10;
            spawn(x, y, z, windX * 2, -26 - rnd.nextFloat() * 6, windZ * 2,
                    1.1f, 0.02f, 0.02f, 0xa0b8c8e0, 0xa0b8c8e0, 0, 0, MODE_TRAIL);
        }
    }

    public void moneyPickupFx(float x, float y, float z) {
        for (int i = 0; i < 8; i++) {
            spawn(x, y, z, rnd.nextFloat() * 3 - 1.5f, 2 + rnd.nextFloat() * 2, rnd.nextFloat() * 3 - 1.5f,
                    0.7f, 0.1f, 0.06f, 0xff40e050, 0xff40e050, 9.8f, 0.3f, MODE_ADD);
        }
    }
}
