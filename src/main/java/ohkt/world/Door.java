package ohkt.world;

/** Porta/ponto de interacao no mundo (lojas, interiores, servicos). */
public final class Door {
    public final float x, z, yaw;
    /** Id do interior (null = apenas acao externa). */
    public final String interiorId;
    /** Acao externa: POSTO, OFICINA, CONCESSIONARIA_EXIB... null se interior. */
    public final String action;
    public final String label;

    public Door(float x, float z, float yaw, String interiorId, String action, String label) {
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        this.interiorId = interiorId;
        this.action = action;
        this.label = label;
    }
}
