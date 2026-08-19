package ohkt.vehicle;

/** Catálogo de veículos fictícios de Porto Aurora. */
public final class VehicleType {

    public final String id, name;
    public final String kind; // SEDAN, HATCH, SPORTS, VAN, TRUCK, PICKUP, MOTO, TAXI, POLICE
    public final float hx, hz;        // meia-largura / meia-comprimento
    public final float bodyH;         // altura do corpo
    public final float wheelR, wheelHalf;
    public final float accel;         // m/s^2
    public final float topSpeed;      // m/s
    public final float grip;          // aderencia lateral 0..1
    public final float turnRate;      // rad/s
    public final float brakeForce;
    public final float mass;
    public final float fuelCap, fuelUse;
    public final int price;
    public final int defaultPaint;

    public VehicleType(String id, String name, String kind, float hx, float hz, float bodyH,
                       float wheelR, float wheelHalf, float accel, float topSpeed, float grip,
                       float turnRate, float brakeForce, float mass, float fuelCap, float fuelUse,
                       int price, int defaultPaint) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.hx = hx;
        this.hz = hz;
        this.bodyH = bodyH;
        this.wheelR = wheelR;
        this.wheelHalf = wheelHalf;
        this.accel = accel;
        this.topSpeed = topSpeed;
        this.grip = grip;
        this.turnRate = turnRate;
        this.brakeForce = brakeForce;
        this.mass = mass;
        this.fuelCap = fuelCap;
        this.fuelUse = fuelUse;
        this.price = price;
        this.defaultPaint = defaultPaint;
    }

    public boolean isMotorcycle() { return kind.equals("MOTO"); }

    public static final VehicleType[] CATALOG = {
            new VehicleType("BREZA", "Fusquinha Breza", "HATCH", 0.82f, 1.75f, 0.62f, 0.31f, 0.2f,
                    7.5f, 30f, 0.9f, 2.1f, 16f, 1000, 32, 0.035f, 4800, 0xffc8b028),
            new VehicleType("ESTRELA", "Sedã Estrela", "SEDAN", 0.9f, 2.15f, 0.68f, 0.33f, 0.22f,
                    8f, 34f, 0.88f, 1.95f, 17f, 1250, 45, 0.045f, 7200, 0xff8898b0),
            new VehicleType("ANDARILHO", "Andarilho GT", "SPORTS", 0.92f, 2.1f, 0.55f, 0.33f, 0.24f,
                    13.5f, 47f, 0.95f, 2.25f, 20f, 1100, 50, 0.075f, 34000, 0xffc02020),
            new VehicleType("LUME", "Táxi Lume", "TAXI", 0.9f, 2.15f, 0.68f, 0.33f, 0.22f,
                    8.2f, 35f, 0.88f, 1.95f, 17f, 1250, 45, 0.045f, 9500, 0xffe8c020),
            new VehicleType("CASCATA", "Van Cascata", "VAN", 1.05f, 2.5f, 0.95f, 0.36f, 0.22f,
                    6.2f, 28f, 0.8f, 1.7f, 15f, 1900, 60, 0.055f, 15500, 0xffd8d8d8),
            new VehicleType("TOURO", "Caminhão Touro", "TRUCK", 1.25f, 3.4f, 1.15f, 0.48f, 0.28f,
                    5.2f, 26f, 0.72f, 1.35f, 14f, 3800, 110, 0.085f, 28000, 0xff3850a0),
            new VehicleType("SERRA", "Caminhonete Serra", "PICKUP", 1.0f, 2.3f, 0.78f, 0.38f, 0.24f,
                    7.6f, 32f, 0.85f, 1.85f, 17f, 1800, 65, 0.06f, 16000, 0xff3a7a48),
            new VehicleType("GARCA", "Moto Garça", "MOTO", 0.38f, 1.0f, 0.5f, 0.3f, 0.12f,
                    11.5f, 41f, 0.92f, 2.6f, 14f, 260, 14, 0.03f, 7800, 0xff202028),
            new VehicleType("GAVIAO", "Viatura Gavião", "POLICE", 0.92f, 2.2f, 0.68f, 0.34f, 0.23f,
                    10.5f, 41f, 0.93f, 2.2f, 19f, 1350, 50, 0.055f, 0, 0xffe8e8ec),
    };

    public static VehicleType byId(String id) {
        for (VehicleType t : CATALOG) {
            if (t.id.equals(id)) return t;
        }
        return CATALOG[0];
    }

    public static VehicleType byKindHint(String hint) {
        switch (hint) {
            case "SPORTS": return byId("ANDARILHO");
            case "SEDAN": return byId("ESTRELA");
            case "TAXI": return byId("LUME");
            case "VAN": return byId("CASCATA");
            case "TRUCK": return byId("TOURO");
            case "PICKUP": return byId("SERRA");
            case "MOTO": return byId("GARCA");
            case "POLICE": return byId("GAVIAO");
            default: return byId("BREZA");
        }
    }
}
