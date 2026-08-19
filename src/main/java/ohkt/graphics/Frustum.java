package ohkt.graphics;

import ohkt.utils.Mat4;

/** Frustum de visao extraido da matriz view-projection para culling. */
public final class Frustum {
    /** 6 planos (a,b,c,d) normalizados: dentro quando ax+by+cz+d >= 0. */
    public final float[][] planes = new float[6][4];

    public void fromMatrix(float[] m) {
        // coluna-major: row_i(j) = m[j*4+i]
        // left = row3+row0, right = row3-row0, bottom = row3+row1, top = row3-row1, near = row3+row2, far = row3-row2
        extract(0, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]);
        extract(1, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]);
        extract(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]);
        extract(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]);
        extract(4, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]);
        extract(5, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]);
    }

    private void extract(int i, float a, float b, float c, float d) {
        float len = (float) Math.sqrt(a * a + b * b + c * c);
        if (len < 1e-9f) len = 1;
        planes[i][0] = a / len;
        planes[i][1] = b / len;
        planes[i][2] = c / len;
        planes[i][3] = d / len;
    }

    public boolean sphereVisible(float x, float y, float z, float radius) {
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < -radius) return false;
        }
        return true;
    }

    public boolean boxVisible(float minx, float miny, float minz, float maxx, float maxy, float maxz) {
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            float px = p[0] >= 0 ? maxx : minx;
            float py = p[1] >= 0 ? maxy : miny;
            float pz = p[2] >= 0 ? maxz : minz;
            if (p[0] * px + p[1] * py + p[2] * pz + p[3] < 0) return false;
        }
        return true;
    }
}
