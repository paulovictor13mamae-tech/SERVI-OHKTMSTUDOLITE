package ohkt.physics;

/** Caixa alinhada aos eixos (colisor estatico do mundo). */
public final class AABB {
    public final float minX, minY, minZ, maxX, maxY, maxZ;
    /** Dono lógico (chunk) para remoção em lote. */
    public final Object owner;

    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Object owner) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.owner = owner;
    }

    public boolean overlapsY(float y0, float y1) {
        return y1 > minY && y0 < maxY;
    }

    public boolean containsPoint(float x, float y, float z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public float centerX() { return (minX + maxX) * 0.5f; }

    public float centerZ() { return (minZ + maxZ) * 0.5f; }
}
