package ohkt.utils;

/**
 * Matriz 4x4 coluna-major (estilo OpenGL). Usada apenas para view/projection
 * e extracao de frustum; transformacoes de objetos sao feitas inline no renderer.
 */
public final class Mat4 {
    public final float[] m = new float[16];

    public Mat4() { identity(); }

    public Mat4 identity() {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    /** Projecao perspectiva. fovY em graus. */
    public Mat4 perspective(float fovY, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovY) * 0.5));
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1;
        m[14] = 2 * far * near / (near - far);
        return this;
    }

    /** Matriz de camera olhando de eye para target. */
    public Mat4 lookAt(Vec3 eye, Vec3 target, Vec3 up) {
        float zx = eye.x - target.x, zy = eye.y - target.y, zz = eye.z - target.z;
        float zl = Vec3.len(zx, zy, zz);
        if (zl < 1e-8f) { zz = 1f; zl = 1f; }
        zx /= zl; zy /= zl; zz /= zl;
        // x = cross(up, z)
        float xx = up.y * zz - up.z * zy;
        float xy = up.z * zx - up.x * zz;
        float xz = up.x * zy - up.y * zx;
        float xl = Vec3.len(xx, xy, xz);
        if (xl < 1e-8f) { xx = 1; xy = 0; xz = 0; xl = 1; }
        xx /= xl; xy /= xl; xz /= xl;
        // y = cross(z, x)
        float yx = zy * xz - zz * xy;
        float yy = zz * xx - zx * xz;
        float yz = zx * xy - zy * xx;

        m[0] = xx; m[4] = xy; m[8] = xz;
        m[1] = yx; m[5] = yy; m[9] = yz;
        m[2] = zx; m[6] = zy; m[10] = zz;
        m[3] = 0; m[7] = 0; m[11] = 0;
        m[12] = -(xx * eye.x + xy * eye.y + xz * eye.z);
        m[13] = -(yx * eye.x + yy * eye.y + yz * eye.z);
        m[14] = -(zx * eye.x + zy * eye.y + zz * eye.z);
        m[15] = 1;
        return this;
    }

    /** out = a * b (nao alias-safe: use temporarios quando out == a ou b). */
    public static void mul(float[] out, float[] a, float[] b) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
    }

    /** Transforma ponto (x,y,z,w) -> out4[0..3]. */
    public static void transform(float[] mat, float x, float y, float z, float w, float[] out4) {
        out4[0] = mat[0] * x + mat[4] * y + mat[8] * z + mat[12] * w;
        out4[1] = mat[1] * x + mat[5] * y + mat[9] * z + mat[13] * w;
        out4[2] = mat[2] * x + mat[6] * y + mat[10] * z + mat[14] * w;
        out4[3] = mat[3] * x + mat[7] * y + mat[11] * z + mat[15] * w;
    }
}
