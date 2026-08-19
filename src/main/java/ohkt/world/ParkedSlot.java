package ohkt.world;

/** Vaga de veiculo estacionado (rua ou estacionamento). */
public final class ParkedSlot {
    public final float x, z, yaw;
    public final String typeHint; // tipo de veiculo sugerido
    public boolean occupied;

    public ParkedSlot(float x, float z, float yaw, String typeHint) {
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        this.typeHint = typeHint;
    }
}
