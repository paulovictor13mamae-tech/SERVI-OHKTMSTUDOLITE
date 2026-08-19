package ohkt.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Malha estatica indexada com cor por face (flat shading).
 * Construida com MeshBuilder e selada para render rapido.
 */
public final class Mesh {

    public float[] verts;      // xyz por vertice
    public int[] indices;      // triples de faces
    public int[] colors;       // cor por face
    public float[] normals;    // normal xyz por face
    public boolean[] emissive; // por face: ignora luz direta
    public int faceCount;

    private Mesh() {}

    /** Construtor incremental de malhas (chunk estatico, props). */
    public static final class Builder {
        private final List<Float> v = new ArrayList<>();
        private final List<Integer> idx = new ArrayList<>();
        private final List<Integer> col = new ArrayList<>();
        private final List<Float> nrm = new ArrayList<>();
        private final List<Boolean> emi = new ArrayList<>();

        public int vertex(float x, float y, float z) {
            v.add(x); v.add(y); v.add(z);
            return v.size() / 3 - 1;
        }

        /** Face com normal explicita. */
        public void face(int a, int b, int c, float nx, float ny, float nz, int color, boolean emissive) {
            idx.add(a); idx.add(b); idx.add(c);
            col.add(color);
            nrm.add(nx); nrm.add(ny); nrm.add(nz);
            emi.add(emissive);
        }

        /** Quad (ordem anti-horaria vista de fora). Normal calculada. */
        public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                         float x2, float y2, float z2, float x3, float y3, float z3,
                         int color, boolean emissive) {
            // normal = cross(p1-p0, p3-p0)
            float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
            float bx = x3 - x0, by = y3 - y0, bz = z3 - z0;
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9f) { nx = 0; ny = 1; nz = 0; l = 1; }
            nx /= l; ny /= l; nz /= l;
            int i0 = vertex(x0, y0, z0);
            int i1 = vertex(x1, y1, z1);
            int i2 = vertex(x2, y2, z2);
            int i3 = vertex(x3, y3, z3);
            face(i0, i1, i2, nx, ny, nz, color, emissive);
            face(i0, i2, i3, nx, ny, nz, color, emissive);
        }

        /** Caixa alinhada aos eixos com cores por face. */
        public void box(float minx, float miny, float minz, float maxx, float maxy, float maxz,
                        int colorTop, int colorSide, int colorBottom, boolean emissiveTop) {
            int ct = colorTop, cb = colorBottom;
            // topo
            quad(minx, maxy, minz, minx, maxy, maxz, maxx, maxy, maxz, maxx, maxy, minz, ct, emissiveTop);
            // base
            quad(minx, miny, minz, maxx, miny, minz, maxx, miny, maxz, minx, miny, maxz, cb, false);
            // norte (-z)
            quad(minx, miny, minz, minx, maxy, minz, maxx, maxy, minz, maxx, miny, minz, colorSide, false);
            // sul (+z)
            quad(minx, miny, maxz, maxx, miny, maxz, maxx, maxy, maxz, minx, maxy, maxz, colorSide, false);
            // oeste (-x)
            quad(minx, miny, minz, minx, miny, maxz, minx, maxy, maxz, minx, maxy, minz, colorSide, false);
            // leste (+x)
            quad(maxx, miny, minz, maxx, maxy, minz, maxx, maxy, maxz, maxx, miny, maxz, colorSide, false);
        }

        public void box(float minx, float miny, float minz, float maxx, float maxy, float maxz, int color) {
            box(minx, miny, minz, maxx, maxy, maxz, color, color, ColorUtilShade.darker(color), false);
        }

        /** Quad no plano do chao (y=0) subdividido em 2x2 (melhora neblina). */
        public void groundQuad(float x0, float z0, float x1, float z1, int color) {
            groundQuad(x0, z0, x1, z1, 0f, color);
        }

        /** Quad no plano do chao com altura e subdivisao 2x2. */
        public void groundQuad(float x0, float z0, float x1, float z1, float y, int color) {
            float xm = (x0 + x1) / 2f, zm = (z0 + z1) / 2f;
            float xs[] = {x0, xm, x1}, zs[] = {z0, zm, z1};
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    quad(xs[a], y, zs[b], xs[a], y, zs[b + 1], xs[a + 1], y, zs[b + 1], xs[a + 1], y, zs[b], color, false);
                }
            }
        }

        /** Quatro paredes de uma caixa ate a altura h. */
        public void wallQuads(float x0, float z0, float x1, float z1, float h, int color) {
            // norte (-z)
            quad(x0, 0, z0, x0, h, z0, x1, h, z0, x1, 0, z0, color, false);
            // sul (+z)
            quad(x0, 0, z1, x1, 0, z1, x1, h, z1, x0, h, z1, color, false);
            // oeste (-x)
            quad(x0, 0, z0, x0, 0, z1, x0, h, z1, x0, h, z0, color, false);
            // leste (+x)
            quad(x1, 0, z0, x1, h, z0, x1, h, z1, x1, 0, z1, color, false);
        }

        /**
         * Janela em uma das 4 faces (0=N,1=S,2=W,3=E) com winding correto.
         * u cresce ao longo da face; v e altura.
         */
        public void windowQuad(int face, float x0, float z0, float x1, float z1,
                               float u0, float u1, float v0, float v1, int color, boolean emissive) {
            final float OFF = 0.035f;
            switch (face) {
                case 0: quad(x0 + u0, v0, z0 - OFF, x0 + u0, v1, z0 - OFF, x0 + u1, v1, z0 - OFF, x0 + u1, v0, z0 - OFF, color, emissive); break;
                case 1: quad(x0 + u0, v0, z1 + OFF, x0 + u1, v0, z1 + OFF, x0 + u1, v1, z1 + OFF, x0 + u0, v1, z1 + OFF, color, emissive); break;
                case 2: quad(x0 - OFF, v0, z0 + u0, x0 - OFF, v0, z0 + u1, x0 - OFF, v1, z0 + u1, x0 - OFF, v1, z0 + u0, color, emissive); break;
                default: quad(x1 + OFF, v0, z0 + u0, x1 + OFF, v1, z0 + u0, x1 + OFF, v1, z0 + u1, x1 + OFF, v0, z0 + u1, color, emissive); break;
            }
        }

        public static int darker(int c) { return ColorUtilShade.darker(c); }

        /** Cilindro aproximado em torno de (cx,cz), raio r, y0..y1, n lados. */
        public void cylinder(float cx, float y0, float z, float r, float y1, int n, int colorSide, int colorTop) {
            int base = v.size() / 3;
            for (int i = 0; i <= n; i++) {
                double a = Math.PI * 2 * i / n;
                float px = cx + (float) Math.cos(a) * r;
                float pz = z + (float) Math.sin(a) * r;
                vertex(px, y0, pz);
                vertex(px, y1, pz);
            }
            for (int i = 0; i < n; i++) {
                int a0 = base + i * 2, a1 = base + i * 2 + 1, b0 = base + (i + 1) * 2, b1 = base + (i + 1) * 2 + 1;
                double am = Math.PI * 2 * (i + 0.5) / n;
                float nx = (float) Math.cos(am), nz = (float) Math.sin(am);
                face(a1, b1, b0, nx, 0, nz, colorSide, false);
                face(a1, b0, a0, nx, 0, nz, colorSide, false);
            }
            // topo em leque
            int c = vertex(cx, y1, z);
            for (int i = 0; i < n; i++) {
                int a0 = base + i * 2 + 1, b0 = base + (i + 1) * 2 + 1;
                face(c, b0, a0, 0, 1, 0, colorTop, false);
            }
        }

        public Mesh seal() {
            Mesh m = new Mesh();
            m.verts = new float[v.size()];
            for (int i = 0; i < m.verts.length; i++) m.verts[i] = v.get(i);
            m.indices = new int[idx.size()];
            for (int i = 0; i < m.indices.length; i++) m.indices[i] = idx.get(i);
            m.colors = new int[col.size()];
            for (int i = 0; i < m.colors.length; i++) m.colors[i] = col.get(i);
            m.normals = new float[nrm.size()];
            for (int i = 0; i < m.normals.length; i++) m.normals[i] = nrm.get(i);
            m.emissive = new boolean[emi.size()];
            for (int i = 0; i < m.emissive.length; i++) m.emissive[i] = emi.get(i);
            m.faceCount = m.indices.length / 3;
            return m;
        }
    }

    /** Auxiliar interno para tons de caixa. */
    static final class ColorUtilShade {
        static int darker(int c) {
            int r = (int) (((c >> 16) & 0xff) * 0.6f);
            int g = (int) (((c >> 8) & 0xff) * 0.6f);
            int b = (int) ((c & 0xff) * 0.6f);
            return 0xff000000 | (r << 16) | (g << 8) | b;
        }
    }
}
