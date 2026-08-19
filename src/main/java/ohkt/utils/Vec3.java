package ohkt.utils;

/**
 * Vetor 3D mutavel de uso geral (metros / direcoes / cores normalizadas).
 * Metodos encadeados retornam this para evitar alocacoes.
 */
public final class Vec3 {
    public float x, y, z;

    public Vec3() { this(0, 0, 0); }

    public Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vec3(Vec3 o) { this.x = o.x; this.y = o.y; this.z = o.z; }

    public Vec3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }

    public Vec3 set(Vec3 o) { this.x = o.x; this.y = o.y; this.z = o.z; return this; }

    public Vec3 copy() { return new Vec3(x, y, z); }

    public Vec3 add(Vec3 o) { x += o.x; y += o.y; z += o.z; return this; }

    public Vec3 add(float ox, float oy, float oz) { x += ox; y += oy; z += oz; return this; }

    public Vec3 sub(Vec3 o) { x -= o.x; y -= o.y; z -= o.z; return this; }

    public Vec3 sub(float ox, float oy, float oz) { x -= ox; y -= oy; z -= oz; return this; }

    public Vec3 mul(float s) { x *= s; y *= s; z *= s; return this; }

    public Vec3 addScaled(Vec3 o, float s) { x += o.x * s; y += o.y * s; z += o.z * s; return this; }

    public float len() { return (float) Math.sqrt(x * x + y * y + z * z); }

    public float lenSq() { return x * x + y * y + z * z; }

    public Vec3 norm() {
        float l = len();
        if (l > 1e-8f) { x /= l; y /= l; z /= l; }
        return this;
    }

    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    /** this = this x o */
    public Vec3 cross(Vec3 o) {
        float nx = y * o.z - z * o.y;
        float ny = z * o.x - x * o.z;
        float nz = x * o.y - y * o.x;
        x = nx; y = ny; z = nz;
        return this;
    }

    public float dst(Vec3 o) {
        float dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float dstSq(Vec3 o) {
        float dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float dst(float ox, float oy, float oz) {
        float dx = x - ox, dy = y - oy, dz = z - oz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Vec3 lerp(Vec3 o, float t) {
        x += (o.x - x) * t; y += (o.y - y) * t; z += (o.z - z) * t;
        return this;
    }

    public boolean isZero() { return x == 0 && y == 0 && z == 0; }

    public static float dst(Vec3 a, Vec3 b) { return a.dst(b); }

    public static float len(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    @Override
    public String toString() { return String.format("(%.2f, %.2f, %.2f)", x, y, z); }
}
