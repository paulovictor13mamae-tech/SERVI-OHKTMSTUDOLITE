package ohkt.world;

/** Bairros/distritos da cidade de Porto Aurora. */
public enum District {
    CENTRO("Centro Financeiro"),
    COMERCIAL("Zona Comercial"),
    RESIDENCIAL("Jardim das Acácias"),
    INDUSTRIAL("Vila do Metal"),
    PORTO("Cais do Sul"),
    PARQUE("Parque Aurora"),
    PERIFERIA("Periferia Norte"),
    MISTO("Bairro Misto"),
    ILHA("Ilha do Farol");

    public final String label;

    District(String label) { this.label = label; }

    /** Densidade alvo de pedestres (0..1). */
    public float pedDensity() {
        switch (this) {
            case CENTRO: return 1f;
            case COMERCIAL: return 0.9f;
            case MISTO: return 0.6f;
            case RESIDENCIAL: return 0.5f;
            case PERIFERIA: return 0.35f;
            case PORTO: return 0.25f;
            case INDUSTRIAL: return 0.2f;
            case PARQUE: return 0.55f;
            case ILHA: return 0.1f;
            default: return 0.5f;
        }
    }

    /** Criminalidade noturna (spawn de criminosos). */
    public float crime() {
        switch (this) {
            case PORTO: return 0.8f;
            case INDUSTRIAL: return 0.7f;
            case PERIFERIA: return 0.5f;
            case CENTRO: return 0.25f;
            default: return 0.15f;
        }
    }

    public int groundColor() {
        switch (this) {
            case PARQUE: return 0xff3d6b35;
            case PORTO: return 0xff5a5a58;
            case INDUSTRIAL: return 0xff54524e;
            case PERIFERIA: return 0xff6e5f43;
            case RESIDENCIAL: return 0xff4f5e3c;
            default: return 0xff4c4c4e; // concreto urbano
        }
    }

    public int roadColor() {
        switch (this) {
            case PERIFERIA: return 0xff3f3a34;
            case PORTO: return 0xff37373a;
            default: return 0xff333438;
        }
    }
}
