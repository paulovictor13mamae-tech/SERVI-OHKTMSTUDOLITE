package ohkt.world;

import ohkt.graphics.Mesh;
import ohkt.physics.AABB;
import ohkt.physics.PhysicsWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Interiores funcionais: lojas, hospital, delegacia, concessionaria,
 * imobiliaria e casas seguras. Ao entrar, a cena troca para o interior
 * (sem telas de carregamento visiveis).
 */
public final class InteriorManager {

    public static final class Interior {
        public final String id;
        public final String name;
        public final String type; // CAFE, ARMAS, ROUPAS, HOSPITAL...
        public final int floorCol, wallCol, counterCol;
        public final boolean safehouse; // permite salvar/dormir
        public final boolean heals;

        Interior(String id, String name, String type, int floor, int wall, int counter, boolean safehouse, boolean heals) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.floorCol = floor;
            this.wallCol = wall;
            this.counterCol = counter;
            this.safehouse = safehouse;
            this.heals = heals;
        }
    }

    public static final java.util.Map<String, Interior> INTERIORS = new java.util.HashMap<>();

    static {
        put(new Interior("CAFE", "Café Farol", "CAFE", 0xff6b4f3a, 0xffc8b8a0, 0xff7a5c3a, false, false));
        put(new Interior("LANCHONETE", "Forno de Ouro", "LANCHONETE", 0xff8a6a4a, 0xffe8d8b8, 0xffc04030, false, true));
        put(new Interior("ROUPAS", "Brechó da Duda", "ROUPAS", 0xff8a8a92, 0xffe0d0e0, 0xffa060a0, false, false));
        put(new Interior("ARMERIA", "Casa do Ferreiro", "ARMERIA", 0xff4a4a4e, 0xff5a5a60, 0xff6a4a2a, false, false));
        put(new Interior("MERCADO", "Mercado Bem-Estar", "MERCADO", 0xff7a7a76, 0xffd8e0d8, 0xff3a8a4a, false, false));
        put(new Interior("HOSPITAL", "Hospital Santa Clara", "HOSPITAL", 0xffc8ccd0, 0xfff0f4f4, 0xffd8e8f0, false, true));
        put(new Interior("DELEGACIA", "12ª Delegacia", "DELEGACIA", 0xff5a5e66, 0xffc8ccd4, 0xff3a5a9a, false, false));
        put(new Interior("CONCESSIONARIA", "AutoVaurora", "CONCESSIONARIA", 0xff50545c, 0xffd8e8f0, 0xff30c0d0, false, false));
        put(new Interior("IMOBILIARIA", "Chaves & Filhos", "IMOBILIARIA", 0xff8a8272, 0xfff0e8d8, 0xff20d0a0, false, false));
        put(new Interior("CASA_MAE", "Casa da Dona Lurdes", "CASA", 0xff8a6a4a, 0xffe0d0b8, 0xff7a5c3a, true, true));
        put(new Interior("APARTAMENTO", "Apartamento Beira-Mar", "CASA", 0xff9a8a72, 0xffe8e0d0, 0xff8a6a4a, true, true));
        put(new Interior("COBERTURA", "Cobertura Vaurora", "CASA", 0xff6a6a72, 0xffd8d8e0, 0xffb0a890, true, true));
        put(new Interior("GALPAO", "Galpão do Porto", "CASA", 0xff555a5e, 0xff707880, 0xff8a8a86, true, false));
    }

    private static void put(Interior i) { INTERIORS.put(i.id, i); }

    public static Interior get(String id) { return INTERIORS.get(id); }

    /** Runtime do interior ativo. */
    public static final class Active {
        public final Interior def;
        public Mesh mesh;
        public final List<AABB> colliders = new ArrayList<>();
        public final float[] exitPos = new float[2];       // ponto de saida dentro do interior
        public final float[] shopkeeper = new float[2];    // posicao do balcao
        public final float[] returnPos = new float[3];     // posicao de retorno no mundo (x,z,yaw)
        public final PhysicsWorld physics = new PhysicsWorld();

        Active(Interior def) { this.def = def; }
    }

    public Active active;

    /** Constrói o interior e registra colisores. */
    public Active enter(String interiorId, float retX, float retZ, float retYaw) {
        Interior def = get(interiorId);
        if (def == null) return null;
        Active a = new Active(def);
        a.returnPos[0] = retX;
        a.returnPos[1] = retZ;
        a.returnPos[2] = retYaw;

        Mesh.Builder b = new Mesh.Builder();
        float w = 14, d = 10, h = 3.2f;
        float x0 = -w / 2, x1 = w / 2, z0 = -d / 2, z1 = d / 2;

        // chao e teto
        b.groundQuad(x0, z0, x1, z1, 0.02f, def.floorCol);
        b.quad(x0, h, z0, x1, h, z0, x1, h, z1, x0, h, z1, 0xffb8b4ac, false);
        // paredes
        b.wallQuads(x0, z0, x1, z1, h, def.wallCol);
        // balcao em L
        float cy = 1.05f;
        b.box(-5.5f, 0, z1 - 4.5f, 2.5f, cy, z1 - 3.2f, def.counterCol);
        // saida (porta na parede sul)
        b.box(-1f, 0, z1 - 0.1f, 1f, 2.6f, z1 + 0.1f, 0xff30241c);
        a.exitPos[0] = 0;
        a.exitPos[1] = z1 - 1.5f;
        a.shopkeeper[0] = -1.5f;
        a.shopkeeper[1] = z1 - 4.0f;

        // decoracao por tipo
        switch (def.type) {
            case "CAFE":
            case "LANCHONETE":
                for (int i = 0; i < 3; i++) {
                    b.box(-4.5f + i * 4, 0, z0 + 2.5f, -3.3f + i * 4, 0.75f, z0 + 3.7f, 0xff7a5c3a);
                    b.box(-4.2f + i * 4, 0.75f, z0 + 2.8f, -3.6f + i * 4, 1.05f, z0 + 3.4f, 0xffc8e8f0);
                }
                break;
            case "ARMAS":
                b.box(x0 + 0.6f, 0.8f, -2.5f, x0 + 1.2f, 2f, 2.5f, 0xff3a3a3e);
                for (int i = 0; i < 4; i++) {
                    b.box(x0 + 1.25f, 1f + i * 0.35f, -2.4f, x0 + 1.6f, 1.25f + i * 0.35f, -1.8f, 0xff9a8a50);
                }
                break;
            case "ROUPAS":
                for (int i = 0; i < 3; i++) {
                    b.box(x0 + 1f, 0, z0 + 1.5f + i * 2.4f, x0 + 2.6f, 2.2f, z0 + 1.9f + i * 2.4f, 0xffb0b0c0);
                }
                break;
            case "HOSPITAL":
                b.box(3f, 0, z0 + 1f, 5.5f, 0.7f, 3.5f, 0xffe8f0f0);
                b.box(3.4f, 0.7f, z0 + 1.4f, 4.2f, 1f, z0 + 2.2f, 0xff40c060);
                break;
            case "DELEGACIA":
                b.box(3.5f, 0, z0 + 1f, 5f, 1.6f, z0 + 2.2f, 0xff2a4a8a);
                break;
            case "CONCESSIONARIA":
                b.box(-4f, 0, z0 + 1.5f, -1f, 0.5f, z0 + 3f, 0xffd02020);
                break;
            case "CASA":
                b.box(3.2f, 0, z0 + 1f, 5.8f, 0.6f, z0 + 2.6f, 0xff8a6a4a); // cama
                b.box(3.2f, 0.6f, z0 + 1f, 5.8f, 0.75f, z0 + 2.6f, 0xffe8e0d0);
                b.box(x0 + 1f, 0, -3.5f, x0 + 2.2f, 1.4f, -2.3f, 0xff5a4432); // guarda-roupa
                break;
            default:
                b.box(x0 + 1f, 0, -3f, x0 + 2.4f, 1.8f, -1.6f, def.counterCol);
                break;
        }

        a.mesh = b.seal();
        // colisores: paredes e balcao
        a.physics.addStatic(new AABB(x0 - 1, 0, z0 - 1, x1 + 1, h, z0, a));
        a.physics.addStatic(new AABB(x0 - 1, 0, z1, x1 + 1, h, z1 + 1, a));
        a.physics.addStatic(new AABB(x0 - 1, 0, z0 - 1, x0, h, z1 + 1, a));
        a.physics.addStatic(new AABB(x1, 0, z0 - 1, x1 + 1, h, z1 + 1, a));
        a.physics.addStatic(new AABB(-5.5f, 0, z1 - 4.5f, 2.5f, cy, z1 - 3.2f, a));
        active = a;
        return a;
    }

    public void exit() {
        active = null;
    }
}
