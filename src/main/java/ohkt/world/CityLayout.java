package ohkt.world;

import ohkt.utils.MathX;

/**
 * Layout deterministico da cidade de Porto Aurora.
 * Malha de quadras 26x26 com avenidas, bairros, parque, porto, praia,
 * calçadão para a Ilha do Farol e mar ao sul.
 */
public final class CityLayout {

    public static final int NB = 26;               // quadras por lado
    public static final float BLOCK = 44f;         // distancia entre eixos de rua
    public static final float ORIGIN = -NB * BLOCK / 2f; // -572
    public static final float MAJOR_HALF = 8f;     // avenida: 16m
    public static final float MINOR_HALF = 5f;     // rua: 10m
    public static final float SIDEWALK = 3f;

    // agua ao sul
    public static final float BEACH_Z = ORIGIN + NB * BLOCK + 10f;   // inicio da praia
    public static final float WATER_Z = ORIGIN + NB * BLOCK + 40f;   // inicio do mar
    public static final float ISLAND_X = roadCoord(14);
    public static final float ISLAND_Z = WATER_Z + 170f;
    public static final float ISLAND_R = 58f;

    // limites do mundo
    public static final float MIN_X = ORIGIN - 90;
    public static final float MAX_X = ORIGIN + NB * BLOCK + 90;
    public static final float MIN_Z = ORIGIN - 90;
    public static final float MAX_Z = ISLAND_Z + ISLAND_R + 60;

    private final long seed;

    public CityLayout(long seed) {
        this.seed = seed;
        MathX.seedNoise(seed);
    }

    public long seed() { return seed; }

    public static float roadCoord(int k) { return ORIGIN + k * BLOCK; }

    public static boolean isMajor(int k) { return k % 4 == 2; }

    public static float halfWidth(int k) { return isMajor(k) ? MAJOR_HALF : MINOR_HALF; }

    /** Quadra (i,j) -> distrito. */
    public static District blockDistrict(int i, int j) {
        if (i == 13 && j == 13) return District.CENTRO; // praca central (tratada a parte)
        if (i >= 3 && i <= 4 && j >= 3 && j <= 4) return District.PARQUE;
        if (i >= 10 && i <= 15 && j >= 10 && j <= 15) return District.CENTRO;
        if (j >= 22 && j <= 25 && i >= 2 && i <= 23) return District.PORTO;
        if (i >= 20 && i <= 25 && j >= 6 && j <= 20) return District.INDUSTRIAL;
        if (i >= 8 && i <= 17 && j >= 8 && j <= 17) return District.COMERCIAL;
        if (i >= 1 && i <= 7 && j >= 5 && j <= 20) return District.RESIDENCIAL;
        if (j <= 4) return District.PERIFERIA;
        return District.MISTO;
    }

    public static District districtAt(float x, float z) {
        if (z > WATER_Z) {
            float dx = x - ISLAND_X, dz = z - ISLAND_Z;
            if (dx * dx + dz * dz < ISLAND_R * ISLAND_R * 1.4f) return District.ILHA;
            return District.PORTO;
        }
        int i = clampBlock((int) Math.floor((x - ORIGIN) / BLOCK));
        int j = clampBlock((int) Math.floor((z - ORIGIN) / BLOCK));
        if (x < ORIGIN || x > ORIGIN + NB * BLOCK || z < ORIGIN || z > ORIGIN + NB * BLOCK) {
            return District.PERIFERIA; // fora da malha
        }
        return blockDistrict(i, j);
    }

    static int clampBlock(int v) {
        return MathX.clamp(v, 0, NB - 1);
    }

    /** Distrito usado para pedestres proximos de (x,z). */
    public static boolean inCity(float x, float z) {
        return x >= ORIGIN - 60 && x <= ORIGIN + NB * BLOCK + 60
                && z >= ORIGIN - 60 && z <= WATER_Z;
    }

    public static boolean onIsland(float x, float z) {
        float dx = x - ISLAND_X, dz = z - ISLAND_Z;
        return dx * dx + dz * dz < ISLAND_R * ISLAND_R;
    }

    /** No calçadão (ponte-terrapleno) para a ilha. */
    public static boolean onCauseway(float x, float z) {
        return z > BEACH_Z - BLOCK && z < ISLAND_Z - 6 && Math.abs(x - ISLAND_X) < 9f;
    }

    public static boolean isWater(float x, float z) {
        if (z <= WATER_Z) return false;
        if (onIsland(x, z)) return false;
        if (onCauseway(x, z)) return false;
        return true;
    }

    /**
     * Altura do chao: 0 na rua, 0.12 no chao da quadra, agua -1.6.
     */
    public static float groundHeight(float x, float z) {
        if (isWater(x, z)) return -1.6f;
        if (onIsland(x, z) || onCauseway(x, z)) return 0.1f;
        if (x < ORIGIN || x > ORIGIN + NB * BLOCK || z < ORIGIN || z > ORIGIN + NB * BLOCK) {
            return 0f; // periferia externa / praia
        }
        int i = clampBlock((int) Math.floor((x - ORIGIN) / BLOCK));
        int j = clampBlock((int) Math.floor((z - ORIGIN) / BLOCK));
        // dentro da faixa de rua?
        float lx = x - roadCoord(i), lz = z - roadCoord(j);
        boolean nearX = Math.abs(lx) <= halfWidth(i);
        boolean nearZ = Math.abs(lz) <= halfWidth(j);
        if (nearX || nearZ) return 0f;
        District d = blockDistrict(i, j);
        if (d == District.PARQUE) return 0.04f;
        return 0.12f;
    }

    /** Quadra de coordenadas de mundo. */
    public static int[] blockOf(float x, float z) {
        return new int[]{
                MathX.clamp((int) Math.floor((x - ORIGIN) / BLOCK), 0, NB - 1),
                MathX.clamp((int) Math.floor((z - ORIGIN) / BLOCK), 0, NB - 1)
        };
    }

    /** Estabelecimentos fixos da campanha: quadra -> tipo de porta. */
    public enum Special {
        PRACA(13, 13),
        HOSPITAL(11, 9),
        DELEGACIA(13, 9),
        CONCESSIONARIA(9, 12),
        OFICINA(16, 12),
        IMOBILIARIA(12, 9),
        POSTO_A(10, 10), POSTO_B(18, 8), POSTO_C(5, 16), POSTO_D(21, 14),
        ARMERIA(14, 8),
        ESTACIONAMENTO_A(11, 12), ESTACIONAMENTO_B(8, 15),
        BRECHO(10, 9),
        LANCHONETE(12, 11),
        GALPAO_PORTO(4, 23),
        APARTAMENTO(16, 13),
        COBERTURA(15, 13),
        GALPAO_CASA(6, 23),
        CASA_MAE(6, 6); // casa inicial (safehouse de historia)

        public final int i, j;

        Special(int i, int j) {
            this.i = i;
            this.j = j;
        }

        public static Special at(int i, int j) {
            for (Special s : values()) {
                if (s.i == i && s.j == j) return s;
            }
            return null;
        }
    }

    /** Centro de uma quadra especial em coordenadas de mundo. */
    public static float[] specialPos(Special s) {
        return new float[]{
                (roadCoord(s.i) + roadCoord(s.i + 1)) / 2f,
                (roadCoord(s.j) + roadCoord(s.j + 1)) / 2f
        };
    }

    /** Nome de rua ficticio deterministico. */
    public String streetName(int k, boolean avenue) {
        String[] aves = {"Av. das Aurora", "Av. do Cais", "Av. Dom Pedro", "Av. Atlântica", "Av. Central", "Av. dos Navegantes", "Av. Vaurora"};
        String[] ruas = {"Rua dos Ipês", "Rua Farol", "Rua Marte", "Rua Bento Rosa", "Rua 7 de Abril", "Rua das Gaivotas", "Rua Trevo", "Rua Santa Marta", "Rua do Ouro", "Rua Nove"};
        long h = MathX.hash2(seed, k);
        return avenue ? aves[(int) Math.abs(h % aves.length)] : ruas[(int) Math.abs(h % ruas.length)];
    }
}
