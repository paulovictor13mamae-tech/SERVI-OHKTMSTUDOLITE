package ohkt.graphics;

import ohkt.utils.ColorUtil;
import ohkt.utils.Mat4;
import ohkt.utils.Vec3;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Renderer 3D por software: rasterizador de triangulos com z-buffer,
 * clipping no plano near, iluminacao flat (sol direcional + ambiente),
 * neblina por distancia, blend alfa/aditivo, billboards e sombras blob.
 *
 * Desenha em um BufferedImage (INT_RGB) que a Window blita na tela.
 */
public final class Renderer3D {

    public int width, height;
    public BufferedImage image;
    private int[] rgb;
    private float[] depth;

    public final Camera cam = new Camera();

    // iluminacao do frame
    private float sunX = 0.3f, sunY = 0.8f, sunZ = 0.2f; // direcao PARA o sol
    private float sunR = 1f, sunG = 0.98f, sunB = 0.92f; // intensidade cor (0..1+)
    private float ambR = 0.42f, ambG = 0.44f, ambB = 0.5f;
    private int fogColor = 0xffb8cfe0;
    private float fogAmount = 1f; // 0 = sem neblina alem do horizonte
    private float nightFactor = 0f; // 0 dia .. 1 noite
    private float far = 420f, fogStart = 190f;

    // estatisticas (debug)
    public int trisDrawn, trisCulled, spritesDrawn;

    private static final float W_MIN = 0.08f;

    // scratch (sem alocacao por frame)
    private final float[] ca = new float[4], cb = new float[4], cc = new float[4], cd = new float[4];
    private final float[][] polyIn = new float[8][4];
    private final float[][] polyOut = new float[8][4];
    private final int[] starX, starY, starB;

    public Renderer3D() {
        Random r = new Random(991);
        starX = new int[160];
        starY = new int[160];
        starB = new int[160];
        for (int i = 0; i < 160; i++) {
            starX[i] = r.nextInt(2000);
            starY[i] = (int) (r.nextInt(1000) * 0.6f);
            starB[i] = 120 + r.nextInt(135);
        }
    }

    public void init(int w, int h) {
        this.width = w;
        this.height = h;
        this.rgb = new int[w * h];
        this.depth = new float[w * h];
        // imagem com raster apontando direto para o buffer (blit sem cópia)
        java.awt.image.DataBufferInt db = new java.awt.image.DataBufferInt(rgb, rgb.length);
        java.awt.image.WritableRaster raster = java.awt.image.Raster.createPackedRaster(
                db, w, h, w, new int[]{0x00ff0000, 0x0000ff00, 0x000000ff}, null);
        this.image = new BufferedImage(
                new java.awt.image.DirectColorModel(24, 0x00ff0000, 0x0000ff00, 0x000000ff),
                raster, false, null);
    }

    /** Chamado a cada frame antes de desenhar. */
    public void begin(int skyTop, int skyHorizon,
                      float sx, float sy, float sz,
                      float sr, float sg, float sb,
                      float ar, float ag, float ab,
                      float night, float fogAmt, float farDist) {
        this.sunX = sx; this.sunY = sy; this.sunZ = sz;
        this.sunR = sr; this.sunG = sg; this.sunB = sb;
        this.ambR = ar; this.ambG = ag; this.ambB = ab;
        this.fogColor = skyHorizon;
        this.fogAmount = fogAmt;
        this.nightFactor = night;
        this.far = farDist;
        this.fogStart = farDist * 0.42f;
        this.trisDrawn = 0; this.trisCulled = 0; this.spritesDrawn = 0;
        java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY);

        cam.update((float) width / height);
        drawSky(skyTop, skyHorizon);
    }

    private void drawSky(int top, int horizon) {
        int h2 = height / 2;
        for (int y = 0; y < height; y++) {
            float t = Math.min(1f, y / (float) h2);
            t = t * t * 0.9f + t * 0.1f;
            int c = ColorUtil.mix(top, horizon, t);
            int row = y * width;
            for (int x = 0; x < width; x++) rgb[row + x] = c;
        }
        // estrelas
        if (nightFactor > 0.05f) {
            int n = (int) (160 * nightFactor);
            for (int i = 0; i < n; i++) {
                int x = (starX[i] + (int) (cam.yaw * 60)) % width;
                if (x < 0) x += width;
                int y = starY[i] * height / 600 - (int) (cam.pitch * height * 0.5f);
                if (y < 0 || y >= height) continue;
                int b = starB[i];
                rgb[y * width + x] = ColorUtil.rgb(b, b, (int) (b * 1.05f));
            }
        }
    }

    /** Disco do sol/lua projetado na esfera celeste. */
    public void drawCelestialBody(Vec3 dirToBody, int color, float radius) {
        Vec3 p = new Vec3(cam.pos).addScaled(dirToBody, 300);
        Mat4.transform(cam.viewProj.m, p.x, p.y, p.z, 1, ca);
        if (ca[3] <= 0.1f) return;
        float inv = 1f / ca[3];
        float sx = (ca[0] * inv * 0.5f + 0.5f) * width;
        float sy = (0.5f - ca[1] * inv * 0.5f) * height;
        float r = radius * width * 0.0018f;
        drawScreenGlow(sx, sy, r, color, 0.35f);
    }

    /** Halo suave em coordenadas de tela (sol, lua, brilho). */
    public void drawScreenGlow(float sx, float sy, float r, int color, float intensity) {
        int x0 = Math.max(0, (int) (sx - r)), x1 = Math.min(width - 1, (int) (sx + r));
        int y0 = Math.max(0, (int) (sy - r)), y1 = Math.min(height - 1, (int) (sy + r));
        int cr = ColorUtil.r(color), cg = ColorUtil.g(color), cb2 = ColorUtil.b(color);
        for (int y = y0; y <= y1; y++) {
            float dy = (y - sy) / r;
            for (int x = x0; x <= x1; x++) {
                float dx = (x - sx) / r;
                float d = dx * dx + dy * dy;
                if (d > 1) continue;
                float f = (1 - d) * (1 - d) * intensity;
                int i = y * width + x;
                int dr = rgb[i];
                int nr = (int) (((dr >> 16) & 0xff) + cr * f);
                int ng = (int) (((dr >> 8) & 0xff) + cg * f);
                int nb = (int) ((dr & 0xff) + cb2 * f);
                rgb[i] = ColorUtil.rgb(nr, ng, nb);
            }
        }
    }

    // ---------------- iluminacao ----------------

    /** Cor final de uma face: diffuse do sol + ambiente + neblina. */
    private int shade(int base, float nx, float ny, float nz, boolean emissive, float dist) {
        int r = (base >> 16) & 0xff, g = (base >> 8) & 0xff, b = base & 0xff;
        if (!emissive) {
            float diff = Math.max(0, nx * sunX + ny * sunY + nz * sunZ);
            float lr = ambR + sunR * diff;
            float lg = ambG + sunG * diff;
            float lb = ambB + sunB * diff;
            // preenchimento do chao/ceu
            float hemi = 0.12f * Math.max(0, ny);
            lr += hemi; lg += hemi; lb += hemi;
            r = (int) (r * lr); g = (int) (g * lg); b = (int) (b * lb);
            if (r > 255) r = 255; if (g > 255) g = 255; if (b > 255) b = 255;
        }
        // neblina
        if (dist > fogStart) {
            float t = (dist - fogStart) / (far - fogStart) * fogAmount;
            if (t > 1) t = 1;
            r += (((fogColor >> 16) & 0xff) - r) * t;
            g += (((fogColor >> 8) & 0xff) - g) * t;
            b += ((fogColor & 0xff) - b) * t;
        }
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    // ---------------- pipeline ----------------

    private void toClip(float x, float y, float z, float[] out) {
        Mat4.transform(cam.viewProj.m, x, y, z, 1, out);
    }

    /** Triangulo em clip space, com cor ja sombreada. mode: 0 opaco, 1 alfa, 2 aditivo. */
    private void triClip(float[] a, float[] b, float[] c, int color, int mode) {
        int alpha = (color >>> 24) & 0xff;
        if (alpha == 0) return;
        if (a[3] < W_MIN && b[3] < W_MIN && c[3] < W_MIN) { trisCulled += 2; return; }
        if (a[3] >= W_MIN && b[3] >= W_MIN && c[3] >= W_MIN) {
            raster(a, b, c, color, mode);
            return;
        }
        // Sutherland-Hodgman contra plano w = W_MIN
        float[][] in = polyIn, out = polyOut;
        in[0][0] = a[0]; in[0][1] = a[1]; in[0][2] = a[2]; in[0][3] = a[3];
        in[1][0] = b[0]; in[1][1] = b[1]; in[1][2] = b[2]; in[1][3] = b[3];
        in[2][0] = c[0]; in[2][1] = c[1]; in[2][2] = c[2]; in[2][3] = c[3];
        int n = 3;
        int oc = 0;
        for (int i = 0; i < n; i++) {
            float[] P = in[i];
            float[] Q = in[(i + 1) % n];
            float pw = P[3], qw = Q[3];
            boolean pin = pw >= W_MIN, qin = qw >= W_MIN;
            if (pin) { out[oc][0] = P[0]; out[oc][1] = P[1]; out[oc][2] = P[2]; out[oc][3] = P[3]; oc++; }
            if (pin != qin) {
                float t = (W_MIN - pw) / (qw - pw);
                out[oc][0] = P[0] + (Q[0] - P[0]) * t;
                out[oc][1] = P[1] + (Q[1] - P[1]) * t;
                out[oc][2] = P[2] + (Q[2] - P[2]) * t;
                out[oc][3] = W_MIN;
                oc++;
            }
        }
        for (int i = 1; i < oc - 1; i++) {
            raster(out[0], out[i], out[i + 1], color, mode);
        }
    }

    private void raster(float[] A, float[] B, float[] C, int color, int mode) {
        float ia = 1f / A[3], ib = 1f / B[3], ic = 1f / C[3];
        float x0 = (A[0] * ia * 0.5f + 0.5f) * width;
        float y0 = (0.5f - A[1] * ia * 0.5f) * height;
        float x1 = (B[0] * ib * 0.5f + 0.5f) * width;
        float y1 = (0.5f - B[1] * ib * 0.5f) * height;
        float x2 = (C[0] * ic * 0.5f + 0.5f) * width;
        float y2 = (0.5f - C[1] * ic * 0.5f) * height;

        float det = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (det > -0.6f && det < 0.6f) { trisCulled++; return; }

        int minX = Math.max(0, (int) Math.floor(min3(x0, x1, x2)));
        int maxX = Math.min(width - 1, (int) Math.ceil(max3(x0, x1, x2)));
        int minY = Math.max(0, (int) Math.floor(min3(y0, y1, y2)));
        int maxY = Math.min(height - 1, (int) Math.ceil(max3(y0, y1, y2)));
        if (minX > maxX || minY > maxY) { trisCulled++; return; }

        float invDet = 1f / det;
        // gradientes dos barycentricos
        float l0x = (y1 - y2) * invDet, l0y = (x2 - x1) * invDet;
        float l1x = (y2 - y0) * invDet, l1y = (x0 - x2) * invDet;
        float l2x = (y0 - y1) * invDet, l2y = (x1 - x0) * invDet;

        float alpha = ((color >>> 24) & 0xff) / 255f;
        int base = 0x00ffffff & color;
        int br = (base >> 16) & 0xff, bg = (base >> 8) & 0xff, bb = base & 0xff;
        boolean opaque = mode == 0 && alpha >= 0.999f;

        for (int y = minY; y <= maxY; y++) {
            float px = minX + 0.5f, py = y + 0.5f;
            float b0 = (x1 - px) * l0y - (y1 - py) * l0x + l0x * x1 * 0 + 0; // recalc abaixo
            // calculo direto (simples e correto):
            b0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) * invDet;
            float b1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) * invDet;
            float b2 = 1f - b0 - b1;
            float step0 = l0x, step1 = l1x, step2 = l2x;
            if (b0 < 0) b0 = 0; // clamp nao usado; testes abaixo decidem
            int rowOff = y * width;
            for (int x = minX; x <= maxX; x++, b0 += step0, b1 += step1, b2 += step2) {
                if (b0 < 0 || b1 < 0 || b2 < 0) continue;
                float iw = b0 * ia + b1 * ib + b2 * ic;
                if (iw <= 0) continue;
                float w = 1f / iw;
                int idx = rowOff + x;
                if (w < depth[idx]) {
                    depth[idx] = w;
                    if (opaque) {
                        rgb[idx] = 0xff000000 | (br << 16) | (bg << 8) | bb;
                    } else {
                        int d = rgb[idx];
                        if (mode == 2) { // aditivo
                            int nr = (int) (((d >> 16) & 0xff) + br * alpha);
                            int ng = (int) (((d >> 8) & 0xff) + bg * alpha);
                            int nb = (int) ((d & 0xff) + bb * alpha);
                            rgb[idx] = ColorUtil.rgb(nr, ng, nb);
                        } else { // alfa
                            int nr = (int) (((d >> 16) & 0xff) + (br - ((d >> 16) & 0xff)) * alpha);
                            int ng = (int) (((d >> 8) & 0xff) + (bg - ((d >> 8) & 0xff)) * alpha);
                            int nb = (int) ((d & 0xff) + (bb - (d & 0xff)) * alpha);
                            rgb[idx] = 0xff000000 | (nr << 16) | (ng << 8) | nb;
                        }
                    }
                    trisDrawn++;
                }
            }
        }
    }

    private static float min3(float a, float b, float c) { return a < b ? (a < c ? a : c) : (b < c ? b : c); }

    private static float max3(float a, float b, float c) { return a > b ? (a > c ? a : c) : (b > c ? b : c); }

    // ---------------- desenho de geometria ----------------

    /** Malha estatica posicionada. Suporta yaw e escala uniforme. */
    public void drawMesh(Mesh m, float px, float py, float pz, float yaw, float scale) {
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        float[] V = m.verts;
        float[] N = m.normals;
        int[] I = m.indices;
        float cpx = cam.pos.x, cpy = cam.pos.y, cpz = cam.pos.z;
        for (int f = 0; f < m.faceCount; f++) {
            int i0 = I[f * 3], i1 = I[f * 3 + 1], i2 = I[f * 3 + 2];
            // rotaciona normal
            float nx = N[f * 3], ny = N[f * 3 + 1], nz = N[f * 3 + 2];
            float wnx = nx * cs - nz * sn;
            float wny = ny;
            float wnz = nx * sn + nz * cs;
            // vertices mundo
            float ax = V[i0 * 3] * scale + px, ay = V[i0 * 3 + 1] * scale + py, az = V[i0 * 3 + 2] * scale + pz;
            float bx = V[i1 * 3] * scale + px, by = V[i1 * 3 + 1] * scale + py, bz = V[i1 * 3 + 2] * scale + pz;
            float cx2 = V[i2 * 3] * scale + px, cy2 = V[i2 * 3 + 1] * scale + py, cz2 = V[i2 * 3 + 2] * scale + pz;
            float mx = (ax + bx + cx2) / 3f, my = (ay + by + cy2) / 3f, mz = (az + bz + cz2) / 3f;
            // backface por normal
            if ((cpx - mx) * wnx + (cpy - my) * wny + (cpz - mz) * wnz <= 0) { trisCulled += 2; continue; }
            float dist = cam.pos.dst(mx, my, mz);
            int col = shade(m.colors[f], wnx, wny, wnz, m.emissive[f], dist);
            int alpha = (col >>> 24) & 0xff;
            toClip(ax, ay, az, ca);
            toClip(bx, by, bz, cb);
            toClip(cx2, cy2, cz2, cc);
            triClip(ca, cb, cc, col, alpha >= 250 ? 0 : 1);
            trisCulled++; // conta 1 "face" (2 tris)
        }
    }

    private final float[] axes = new float[9];

    private void rotAxes(float yaw, float pitch, float roll) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float Xx = cy, Xy = 0, Xz = sy;
        float Yx = 0, Yy = 1, Yz = 0;
        float Zx = -sy, Zy = 0, Zz = cy;
        if (pitch != 0) {
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            float nYx = Yx * cp + Zx * sp, nYy = Yy * cp + Zy * sp, nYz = Yz * cp + Zz * sp;
            float nZx = Zx * cp - Yx * sp, nZy = Zy * cp - Yy * sp, nZz = Zz * cp - Yz * sp;
            Yx = nYx; Yy = nYy; Yz = nYz;
            Zx = nZx; Zy = nZy; Zz = nZz;
        }
        if (roll != 0) {
            float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
            float nXx = Xx * cr - Yx * sr, nXy = Xy * cr - Yy * sr, nXz = Xz * cr - Yz * sr;
            float nYx = Xx * sr + Yx * cr, nYy = Xy * sr + Yy * cr, nYz = Xz * sr + Yz * cr;
            Xx = nXx; Xy = nXy; Xz = nXz;
            Yx = nYx; Yy = nYy; Yz = nYz;
        }
        axes[0] = Xx; axes[1] = Xy; axes[2] = Xz;
        axes[3] = Yx; axes[4] = Yy; axes[5] = Yz;
        axes[6] = Zx; axes[7] = Zy; axes[8] = Zz;
    }

    /** Caixa orientada. Normalmente usada para entidades dinamicas. */
    public void drawBox(float cx, float cy, float cz, float hx, float hy, float hz,
                        float yaw, float pitch, float roll, int color, boolean emissive) {
        rotAxes(yaw, pitch, roll);
        float Xx = axes[0], Xy = axes[1], Xz = axes[2];
        float Yx = axes[3], Yy = axes[4], Yz = axes[5];
        float Zx = axes[6], Zy = axes[7], Zz = axes[8];
        // 8 cantos
        for (int i = 0; i < 8; i++) {
            float sx = (i & 1) == 0 ? -hx : hx;
            float sy = (i & 2) == 0 ? -hy : hy;
            float sz = (i & 4) == 0 ? -hz : hz;
            corner[i][0] = cx + Xx * sx + Yx * sy + Zx * sz;
            corner[i][1] = cy + Xy * sx + Yy * sy + Zy * sz;
            corner[i][2] = cz + Xz * sx + Yz * sy + Zz * sz;
            toClip(corner[i][0], corner[i][1], corner[i][2], cornerClip[i]);
        }
        // faces: (indices de canto) normais
        face4(0, 2, 3, 1, Xx, Xy, Xz, color, emissive, false);   // -X
        face4(4, 5, 7, 6, -Xx, -Xy, -Xz, color, emissive, false); // +X
        face4(0, 1, 5, 4, -Yx, -Yy, -Yz, color, emissive, false); // -Y
        face4(2, 6, 7, 3, Yx, Yy, Yz, color, emissive, false);   // +Y
        face4(0, 4, 6, 2, -Zx, -Zy, -Zz, color, emissive, false); // -Z
        face4(1, 3, 7, 5, Zx, Zy, Zz, color, emissive, false);   // +Z
    }

    private final float[][] corner = new float[8][3];
    private final float[][] cornerClip = new float[8][4];

    private void face4(int a, int b, int c, int d, float nx, float ny, float nz, int color, boolean emissive, boolean noCull) {
        float mx = (corner[a][0] + corner[b][0] + corner[c][0] + corner[d][0]) * 0.25f;
        float my = (corner[a][1] + corner[b][1] + corner[c][1] + corner[d][1]) * 0.25f;
        float mz = (corner[a][2] + corner[b][2] + corner[c][2] + corner[d][2]) * 0.25f;
        if (!noCull && (cam.pos.x - mx) * nx + (cam.pos.y - my) * ny + (cam.pos.z - mz) * nz <= 0) {
            trisCulled += 2;
            return;
        }
        int col = shade(color, nx, ny, nz, emissive, cam.pos.dst(mx, my, mz));
        triClip(cornerClip[a], cornerClip[b], cornerClip[c], col, 0);
        triClip(cornerClip[a], cornerClip[c], cornerClip[d], col, 0);
    }

    /** Quad alinhado aos eixos no plano Y (chao, agua, marcacoes). */
    public void drawGroundQuad(float x0, float z0, float x1, float z1, float y, int color, boolean emissive) {
        toClip(x0, y, z0, ca);
        toClip(x1, y, z0, cb);
        toClip(x1, y, z1, cc);
        toClip(x0, y, z1, cd);
        float mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        float dist = cam.pos.dst(mx, y, mz);
        int col = shade(color, 0, 1, 0, emissive, dist);
        int alpha = (col >>> 24) & 0xff;
        int mode = alpha >= 250 ? 0 : 1;
        triClip(ca, cb, cc, col, mode);
        triClip(ca, cc, cd, col, mode);
    }

    /** Billboard quadrado centrado em (x,y,z). */
    public void drawSprite(float x, float y, float z, float size, int color, int mode) {
        float rx = (float) Math.cos(cam.yaw), rz = (float) Math.sin(cam.yaw);
        float s = size * 0.5f;
        float ax = x - rx * s, az = z - rz * s;
        float bx = x + rx * s, bz = z + rz * s;
        toClip(ax, y - s, az, ca);
        toClip(bx, y - s, bz, cb);
        toClip(bx, y + s, bz, cc);
        toClip(ax, y + s, az, cd);
        triClip(ca, cb, cc, color, mode);
        triClip(ca, cc, cd, color, mode);
        spritesDrawn++;
    }

    /** Linha 3D grossa (tracers, cabos). */
    public void drawTracer(float x0, float y0, float z0, float x1, float y1, float z1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = Vec3.len(dx, dy, dz);
        if (len < 1e-4f) return;
        dx /= len; dy /= len; dz /= len;
        // perpendicular aproximada: cross(dir, camForward)
        Vec3 f = cam.forward(new Vec3());
        float px = dy * f.z - dz * f.y, py = dz * f.x - dx * f.z, pz = dx * f.y - dy * f.x;
        float pl = Vec3.len(px, py, pz);
        if (pl < 1e-4f) { px = 1; py = 0; pz = 0; pl = 1; }
        px = px / pl * width * 0.5f; py = py / pl * width * 0.5f; pz = pz / pl * width * 0.5f;
        toClip(x0 - px, y0 - py, z0 - pz, ca);
        toClip(x0 + px, y0 + py, z0 + pz, cb);
        toClip(x1 + px, y1 + py, z1 + pz, cc);
        toClip(x1 - px, y1 - py, z1 - pz, cd);
        triClip(ca, cb, cc, color, 2);
        triClip(ca, cc, cd, color, 2);
    }

    /** Sombra blob eliptica no chao. */
    public void drawShadowBlob(float x, float z, float r, float strength, float groundY) {
        drawGroundPolygon(x, z, r, 0.05f + groundY, ColorUtil.rgba(0, 0, 0, (int) (170 * strength)), 1);
    }

    /** Disco no plano do chao (poca de luz, marcadores). */
    public void drawGroundDisk(float x, float z, float r, float groundY, int color, int mode) {
        drawGroundPolygon(x, z, r, groundY, color, mode);
    }

    private final float[][] diskClip = new float[10][4];

    private void drawGroundPolygon(float x, float z, float r, float y, int color, int mode) {
        if ((color >>> 24) == 0) return;
        if (!cam.frustum.sphereVisible(x, y, z, r + 1)) return;
        float px = cam.pos.x - x, pz = cam.pos.z - z;
        float dist = Vec3.len(px, 0, pz);
        if (dist - r > 0) {
            float t = Math.max(0, (dist - r - fogStart)) / (far - fogStart) * fogAmount;
            if (t > 0.92f) return;
        }
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            toClip(x + (float) Math.cos(a) * r, y, z + (float) Math.sin(a) * r, diskClip[i]);
        }
        for (int i = 1; i < 9; i++) {
            triClip(diskClip[0], diskClip[i], diskClip[i + 1], color, mode);
        }
    }

    /** Coluna de luz vertical (marcador de missao). */
    public void drawBeacon(float x, float z, float baseY, float h, float radius, int color) {
        float rx = (float) Math.cos(cam.yaw), rz = (float) Math.sin(cam.yaw);
        float topAlpha = 30;
        // quad inferior (mais opaco) e superior (mais transparente)
        for (int seg = 0; seg < 4; seg++) {
            float y0 = baseY + h * seg / 4f;
            float y1 = baseY + h * (seg + 1) / 4f;
            int a = (int) (120 * (1f - seg / 4f));
            int c0 = ColorUtil.withAlpha(color, a);
            toClip(x - rx * radius, y0, z - rz * radius, ca);
            toClip(x + rx * radius, y0, z + rz * radius, cb);
            toClip(x + rx * radius, y1, z + rz * radius, cc);
            toClip(x - rx * radius, y1, z - rz * radius, cd);
            triClip(ca, cb, cc, c0, 1);
            triClip(ca, cc, cd, c0, 1);
        }
        drawGroundDisk(x, z, radius * 1.6f, baseY + 0.06f, ColorUtil.withAlpha(color, (int) (topAlpha + 60)), 2);
    }

    public boolean sphereVisible(float x, float y, float z, float r) {
        return cam.frustum.sphereVisible(x, y, z, r);
    }

    public int[] pixels() { return rgb; }

    public void setNightFactor(float n) { this.nightFactor = n; }

    public float nightFactor() { return nightFactor; }
}
