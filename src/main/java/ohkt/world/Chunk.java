package ohkt.world;

import ohkt.graphics.Mesh;
import ohkt.physics.AABB;
import ohkt.physics.PhysicsWorld;
import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chunk = uma quadra da cidade (com a rua a oeste e ao norte).
 * Geracao 100% deterministica por seed do mundo; gera malha proxima (dia/noite),
 * malha LOD distante, colisores e pontos de interesse (portas, vagas, lampadas).
 */
public final class Chunk {

    public final int i, j;
    public final District district;
    public final float cx, cz;      // centro
    public final float radius;      // raio de culling
    public float maxY = 8;

    public Mesh dayMesh, nightMesh, shellMesh;
    public final List<AABB> colliders = new ArrayList<>();
    public final List<float[]> lamps = new ArrayList<>();
    public final List<int[]> trafficNodes = new ArrayList<>();
    public final List<ParkedSlot> parkedSlots = new ArrayList<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<float[]> pedSpawns = new ArrayList<>();

    private final World world;

    public Chunk(World world, int i, int j) {
        this.world = world;
        this.i = i;
        this.j = j;
        this.district = CityLayout.blockDistrict(i, j);
        this.cx = (CityLayout.roadCoord(i) + CityLayout.roadCoord(i + 1)) / 2f;
        this.cz = (CityLayout.roadCoord(j) + CityLayout.roadCoord(j + 1)) / 2f;
        this.radius = CityLayout.BLOCK * 0.75f + 6;
    }

    public void build(PhysicsWorld physics, boolean withWindows) {
        Random rnd = MathX.chunkRandom(world.seed, i, j);
        Mesh.Builder day = new Mesh.Builder();
        Mesh.Builder night = new Mesh.Builder();
        Mesh.Builder shell = new Mesh.Builder();

        float rx0 = CityLayout.roadCoord(i), rx1 = CityLayout.roadCoord(i + 1);
        float rz0 = CityLayout.roadCoord(j), rz1 = CityLayout.roadCoord(j + 1);
        float hwW = CityLayout.halfWidth(i);   // rua vertical i (oeste do chunk)
        float hwN = CityLayout.halfWidth(j);   // rua horizontal j (norte do chunk)

        // ---------- chao da quadra ----------
        float bx0 = rx0 + hwW + CityLayout.SIDEWALK;
        float bx1 = rx1 - CityLayout.halfWidth(i + 1) - CityLayout.SIDEWALK;
        float bz0 = rz0 + hwN + CityLayout.SIDEWALK;
        float bz1 = rz1 - CityLayout.halfWidth(j + 1) - CityLayout.SIDEWALK;

        District d = district;
        CityLayout.Special special = CityLayout.Special.at(i, j);

        // ---------- ruas ----------
        buildRoads(day, rnd, rx0, rz0, hwW, hwN, i, j);
        // replica no shell apenas asfalto basico
        int roadCol = d.roadColor();
        shell.groundQuad(rx0 - hwW, rz0 - hwN, rx1, rz1, roadCol);

        // ---------- calada + meio-fio ----------
        int walkCol = 0xff8f8d86;
        int kerbCol = 0xff6f6d66;
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.groundQuad(rx0 - hwW, rz0 - hwN, bx1, bz0, walkCol);           // norte
            b.groundQuad(rx0 - hwW, bz1, bx1, rz1 - CityLayout.halfWidth(j + 1), walkCol); // sul
            b.groundQuad(rx0 - hwW, bz0, bx0, bz1, walkCol);                 // oeste
            b.groundQuad(bx1, bz0, rx1 - CityLayout.halfWidth(i + 1), bz1, walkCol); // leste
            // meio-fio (faixa vertical baixa)
            b.quad(bx0, 0.12f, bz0, bx1, 0.12f, bz0, bx1, 0, bz0, bx0, 0, bz0, kerbCol, false);
            b.quad(bx0, 0.12f, bz1, bx0, 0, bz1, bx1, 0, bz1, bx1, 0.12f, bz1, kerbCol, false);
            b.quad(bx0, 0.12f, bz0, bx0, 0, bz0, bx0, 0, bz1, bx0, 0.12f, bz1, kerbCol, false);
            b.quad(bx1, 0.12f, bz0, bx1, 0.12f, bz1, bx1, 0, bz1, bx1, 0, bz0, kerbCol, false);
        }
        addPedSpawnCorners(bx0, bz0, bx1, bz1);

        // ---------- conteudo por tipo ----------
        if (special != null) {
            buildSpecial(day, night, shell, special, bx0, bz0, bx1, bz1, rnd, withWindows);
        } else {
            switch (d) {
                case CENTRO: buildDowntown(day, night, shell, bx0, bz0, bx1, bz1, rnd, withWindows); break;
                case COMERCIAL: buildCommercial(day, night, shell, bx0, bz0, bx1, bz1, rnd, withWindows); break;
                case RESIDENCIAL: buildResidential(day, night, shell, bx0, bz0, bx1, bz1, rnd); break;
                case INDUSTRIAL: buildIndustrial(day, night, shell, bx0, bz0, bx1, bz1, rnd); break;
                case PORTO: buildPort(day, night, shell, bx0, bz0, bx1, bz1, rnd); break;
                case PARQUE: buildPark(day, night, shell, bx0, bz0, bx1, bz1, rnd); break;
                case PERIFERIA: buildShanty(day, night, shell, bx0, bz0, bx1, bz1, rnd); break;
                case ILHA: break;
                default:
                    if (rnd.nextFloat() < 0.5f) buildCommercial(day, night, shell, bx0, bz0, bx1, bz1, rnd, withWindows);
                    else buildResidential(day, night, shell, bx0, bz0, bx1, bz1, rnd);
            }
        }

        // ---------- props de rua ----------
        buildStreetProps(day, night, rnd, rx0, rz0, hwW, hwN, bx0, bz0, bx1, bz1);

        dayMesh = day.seal();
        nightMesh = night.seal();
        shellMesh = shell.seal();
        for (AABB a : colliders) physics.addStatic(a);
    }

    public void unload(PhysicsWorld physics) {
        physics.removeOwner(this);
    }

    // ================= RUAS =================

    private void buildRoads(Mesh.Builder b, Random rnd, float rx0, float rz0, float hwW, float hwN, int i, int j) {
        int roadCol = district.roadColor();
        float rx1 = CityLayout.roadCoord(i + 1);
        float rz1 = CityLayout.roadCoord(j + 1);
        float hwE = CityLayout.halfWidth(i + 1);
        float hwS = CityLayout.halfWidth(j + 1);
        // intersecao (i,j)
        b.groundQuad(rx0 - hwW, rz0 - hwN, rx0 + hwW, rz0 + hwN, roadCol);
        // rua vertical i (entre intersecoes j e j+1)
        b.groundQuad(rx0 - hwW, rz0 + hwN, rx0 + hwW, rz1 - hwS, roadCol);
        // rua horizontal j (entre intersecoes i e i+1)
        b.groundQuad(rx0 + hwW, rz0 - hwN, rx1 - hwE, rz0 + hwN, roadCol);

        // faixas
        int vertMajor = CityLayout.isMajor(i) ? 1 : 0;
        int horMajor = CityLayout.isMajor(j) ? 1 : 0;
        // vertical: centro amarelo tracejado
        float z0 = rz0 + hwN + 1, z1 = rz1 - hwS - 1;
        for (float z = z0; z < z1 - 3; z += 7) {
            b.groundQuad(rx0 - 0.15f, z, rx0 + 0.15f, Math.min(z + 3, z1), 0xffb8a030);
            if (vertMajor == 1) {
                b.groundQuad(rx0 + 3.9f, z, rx0 + 4.25f, Math.min(z + 3, z1), 0xffc8c8c8);
                b.groundQuad(rx0 - 4.25f, z, rx0 - 3.9f, Math.min(z + 3, z1), 0xffc8c8c8);
            }
        }
        float x0 = rx0 + hwW + 1, x1 = rx1 - hwE - 1;
        for (float x = x0; x < x1 - 3; x += 7) {
            b.groundQuad(x, rz0 - 0.15f, Math.min(x + 3, x1), rz0 + 0.15f, 0xffb8a030);
            if (horMajor == 1) {
                b.groundQuad(x, rz0 + 3.9f, Math.min(x + 3, x1), rz0 + 4.25f, 0xffc8c8c8);
                b.groundQuad(x, rz0 - 4.25f, Math.min(x + 3, x1), rz0 - 3.9f, 0xffc8c8c8);
            }
        }
        // faixas de pedestre nas aproximacoes da intersecao (i,j)
        crosswalk(b, rx0, rz0 - hwN, hwW);
        crosswalk(b, rx0, rz0 + hwN, hwW);
        crosswalkH(b, rx0 - hwW, rz0, hwN);
        crosswalkH(b, rx0 + hwW, rz0, hwN);

        // vagas na rua (lado leste da rua vertical / lado sul da horizontal)
        if (rnd.nextFloat() < 0.75f) {
            float px = rx0 + hwW - 1.6f;
            for (float z = z0 + 2; z < z1 - 6; z += 7.5f) {
                if (rnd.nextFloat() < 0.42f) {
                    parkedSlots.add(new ParkedSlot(px, z, 0, streetCarHint(rnd)));
                }
            }
        }

        // semaforos em intersecoes com avenida
        if (CityLayout.isMajor(i) || CityLayout.isMajor(j)) {
            trafficNodes.add(new int[]{i, j});
        }
    }

    private String streetCarHint(Random rnd) {
        switch (district) {
            case CENTRO: return rnd.nextFloat() < 0.3f ? "SPORTS" : "SEDAN";
            case COMERCIAL: return rnd.nextFloat() < 0.2f ? "TAXI" : (rnd.nextFloat() < 0.5f ? "SEDAN" : "HATCH");
            case RESIDENCIAL: return rnd.nextFloat() < 0.5f ? "HATCH" : "PICKUP";
            case INDUSTRIAL: return rnd.nextFloat() < 0.6f ? "TRUCK" : "VAN";
            case PORTO: return "TRUCK";
            case PERIFERIA: return rnd.nextFloat() < 0.4f ? "MOTO" : "HATCH";
            default: return "SEDAN";
        }
    }

    private void crosswalk(Mesh.Builder b, float x, float zEdge, float hw) {
        // zeira perpendicular a rua vertical (atravessando em X)
        float zIn = zEdge + (zEdge < CityLayout.roadCoord(j) ? 1.2f : -1.2f - 2.4f);
        for (int s = 0; s < 5; s++) {
            float sx = x - hw + 1.2f + s * ((2 * hw - 2.4f) / 5f);
            b.groundQuad(sx, Math.min(zEdge, zIn), sx + 0.9f, Math.max(zEdge, zIn) + 2.4f, 0xffb0b0aa);
        }
    }

    private void crosswalkH(Mesh.Builder b, float xEdge, float z, float hw) {
        float xIn = xEdge + (xEdge < CityLayout.roadCoord(i) ? 1.2f : -1.2f - 2.4f);
        for (int s = 0; s < 5; s++) {
            float sz = z - hw + 1.2f + s * ((2 * hw - 2.4f) / 5f);
            b.groundQuad(Math.min(xEdge, xIn), sz, Math.max(xEdge, xIn) + 2.4f, sz + 0.9f, 0xffb0b0aa);
        }
    }

    private void addPedSpawnCorners(float bx0, float bz0, float bx1, float bz1) {
        pedSpawns.add(new float[]{bx0, bz0});
        pedSpawns.add(new float[]{bx1, bz0});
        pedSpawns.add(new float[]{bx0, bz1});
        pedSpawns.add(new float[]{bx1, bz1});
        pedSpawns.add(new float[]{(bx0 + bx1) / 2, bz0});
        pedSpawns.add(new float[]{(bx0 + bx1) / 2, bz1});
    }

    // ================= CONSTRUCAO DE PREDIOS =================

    /** Predio com grade de janelas (dia: vidro escuro; noite: acesas). */
    private void addBuilding(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                             float x0, float z0, float x1, float z1, float h,
                             int wall, int roof, Random rnd, boolean withWindows) {
        float w = x1 - x0, dp = z1 - z0;
        int glassDay = 0xff223244;
        int glassNightOff = 0xff111a26;
        int[] litColors = {0xffffd080, 0xffffe8b0, 0xffc8d8ff, 0xfff0e0c0};
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            boolean isNightMesh = b == night;
            // paredes
            b.wallQuads(x0, z0, x1, z1, h, wall);
            // janelas
            if (withWindows) {
                for (int face = 0; face < 4; face++) {
                    float len = face < 2 ? w : dp;
                    int cols = Math.max(1, (int) (len / 3.2f));
                    int rows = Math.max(1, (int) ((h - 2.4f) / 3.4f));
                    for (int c = 0; c < cols; c++) {
                        for (int rw = 0; rw < rows; rw++) {
                            float u0 = 0.8f + c * (len - 1.6f) / cols;
                            float v0 = 1.8f + rw * (h - 3.4f) / Math.max(1, rows - 1 + 0.001f);
                            if (v0 + 1.5f > h - 0.8f) continue;
                            boolean lit = isNightMesh && rnd.nextFloat() < 0.38f;
                            int col = isNightMesh ? (lit ? litColors[rnd.nextInt(litColors.length)] : glassNightOff) : glassDay;
                            b.windowQuad(face, x0, z0, x1, z1, u0, u0 + 1.7f, v0, v0 + 1.6f, col, lit);
                        }
                    }
                }
            }
            // cobertura
            b.box(x0 - 0.2f, h, z0 - 0.2f, x1 + 0.2f, h + 0.35f, z1 + 0.2f, roof);
            // caixa d'agua/ar
            if (h > 14 && rnd.nextFloat() < 0.7f) {
                b.box(x0 + 1.5f, h + 0.35f, z0 + 1.5f, x0 + 4f, h + 2.6f, z0 + 4f, 0xff8a8a86);
            }
        }
        shell.box(x0, 0, z0, x1, h, z1, wall, wall, Mesh.Builder.darker(wall), false);
        colliders.add(new AABB(x0, 0, z0, x1, h, z1, this));
        maxY = Math.max(maxY, h + 3);
    }

    /** Predio comercial com toldo e letreiro. */
    private void addShop(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                         float x0, float z0, float x1, float z1, float h,
                         int wall, int signColor, Random rnd, String doorId, String doorLabel, boolean withWindows) {
        addBuilding(day, night, shell, x0, z0, x1, z1, h, wall, 0xff6b5a4a, rnd, withWindows);
        // porta na face sul
        float doorX = (x0 + x1) / 2;
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.quad(doorX - 0.9f, 0, z1 + 0.04f, doorX - 0.9f, 2.4f, z1 + 0.04f,
                    doorX + 0.9f, 2.4f, z1 + 0.04f, doorX + 0.9f, 0, z1 + 0.04f, 0xff30241c, false);
            // toldo
            b.box(doorX - 2.2f, 2.9f, z1 - 0.1f, doorX + 2.2f, 3.15f, z1 + 1.6f, ColorUtil.vary(0xffa03030, rnd.nextLong(), 0.4f));
            // letreiro emissor
            b.box(x0 + 0.4f, h - 1.4f, z1 - 0.15f, x1 - 0.4f, h - 0.3f, z1 + 0.06f, signColor, signColor, signColor, true);
        }
        if (doorId != null) {
            doors.add(new Door(doorX, z1 + 1.2f, (float) Math.PI, doorId, null, doorLabel));
        }
    }

    private void addTree(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell, float x, float z, Random rnd) {
        float h = 2.6f + rnd.nextFloat() * 1.6f;
        int canopy = ColorUtil.vary(0xff2e5c28, rnd.nextLong(), 0.35f);
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.box(x - 0.18f, 0, z - 0.18f, x + 0.18f, h, z + 0.18f, 0xff5a4432);
            float cs = 1.4f + rnd.nextFloat() * 0.8f;
            b.box(x - cs, h - 0.4f, z - cs, x + cs, h + 1.2f, z + cs, canopy);
            b.box(x - cs * 0.65f, h + 1.2f, z - cs * 0.65f, x + cs * 0.65f, h + 2f, z + cs * 0.65f, ColorUtil.shade(canopy, 1.15f));
        }
        colliders.add(new AABB(x - 0.3f, 0, z - 0.3f, x + 0.3f, h, z + 0.3f, this));
    }

    private void addLamp(Mesh.Builder day, Mesh.Builder night, float x, float z, float flip) {
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            boolean nightMesh = b == night;
            b.box(x - 0.1f, 0, z - 0.1f, x + 0.1f, 5.6f, z + 0.1f, 0xff3a3a3c);
            b.box(x - 0.1f, 5.4f, z - 0.1f + flip * 1.1f, x + 0.1f, 5.6f, z + 0.1f + flip * 1.1f, 0xff3a3a3c);
            int headCol = nightMesh ? 0xffffe8b0 : 0xffb8b8a8;
            b.box(x - 0.35f, 5.25f, z - 0.35f + flip * 1.1f, x + 0.35f, 5.55f, z + 0.35f + flip * 1.1f, headCol, headCol, headCol, nightMesh);
        }
        lamps.add(new float[]{x + flip * 1.1f, 5.4f, z});
        colliders.add(new AABB(x - 0.15f, 0, z - 0.15f, x + 0.15f, 5.6f, z + 0.15f, this));
    }

    private void addBench(Mesh.Builder day, Mesh.Builder night, float x, float z, float yaw) {
        float cs = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.box(x - cs * 0.9f - sn * 0.25f, 0.3f, z - sn * 0.9f - cs * 0.25f,
                    x + cs * 0.9f - sn * 0.25f, 0.5f, z + sn * 0.9f - cs * 0.25f, 0xff7a5c3a);
            b.box(x - cs * 0.9f + sn * 0.25f, 0.5f, z - sn * 0.9f + cs * 0.25f,
                    x + cs * 0.9f + sn * 0.25f, 1.1f, z + sn * 0.9f + cs * 0.25f, 0xff7a5c3a);
        }
    }

    private void addHydrant(Mesh.Builder day, Mesh.Builder night, float x, float z) {
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.box(x - 0.14f, 0, z - 0.14f, x + 0.14f, 0.55f, z + 0.14f, 0xffb02818);
        }
    }

    private void addTrash(Mesh.Builder day, Mesh.Builder night, float x, float z) {
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.box(x - 0.3f, 0, z - 0.3f, x + 0.3f, 0.8f, z + 0.3f, 0xff3c4a3c);
        }
        colliders.add(new AABB(x - 0.35f, 0, z - 0.35f, x + 0.35f, 0.8f, z + 0.35f, this));
    }

    private void addContainer(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell, float x, float z, float yaw, Random rnd, float y0) {
        int[] cols = {0xffa04028, 0xff2860a0, 0xff2f7a36, 0xffa07020, 0xff707878};
        int c = cols[rnd.nextInt(cols.length)];
        float cs = (float) Math.cos(yaw) * 3f, sn = (float) Math.sin(yaw) * 3f;
        float w = 1.25f;
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.box(x - cs - w, y0, z - sn - w, x + cs, y0 + 2.6f, z + sn + w, c);
        }
        shell.box(x - Math.abs(cs) - w, 0, z - Math.abs(sn) - w, x + Math.abs(cs) + w, y0 + 2.6f, z + Math.abs(sn) + w, c);
        colliders.add(new AABB(Math.min(x - cs, x + cs) - w, y0, Math.min(z - sn, z + sn) - w,
                Math.max(x - cs, x + cs) + w, y0 + 2.6f, Math.max(z - sn, z + sn) + w, this));
        maxY = Math.max(maxY, y0 + 2.8f);
    }

    // ================= DISTRITOS =================

    private void buildDowntown(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                               float x0, float z0, float x1, float z1, Random rnd, boolean withWindows) {
        int[] walls = {0xff5a6a7c, 0xff6a7484, 0xff48586c, 0xff7c8894, 0xff556070};
        int count = 1 + rnd.nextInt(2);
        for (int b = 0; b < count; b++) {
            float w = (x1 - x0) * (count == 1 ? 0.8f : 0.42f);
            float dp = (z1 - z0) * (count == 1 ? 0.8f : (0.4f + rnd.nextFloat() * 0.4f));
            float bx = count == 1 ? (x0 + x1) / 2 - w / 2 : (b == 0 ? x0 + 1.5f : x1 - 1.5f - w);
            float bz = z0 + 1.5f + rnd.nextFloat() * Math.max(1, (z1 - z0 - dp - 3));
            float h = 26f + rnd.nextFloat() * 62f;
            addBuilding(day, night, shell, bx, bz, bx + w, bz + dp, h, walls[rnd.nextInt(walls.length)], 0xff3c4048, rnd, withWindows);
        }
    }

    private void buildCommercial(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                                 float x0, float z0, float x1, float z1, Random rnd, boolean withWindows) {
        int[] walls = {0xff9a8878, 0xff8a94a0, 0xffa08878, 0xff788878, 0xff9c8c6c};
        int[] signs = {0xffff5040, 0xff40b0ff, 0xffffc030, 0xff40e080, 0xffff70b0, 0xffe0e040};
        String[] shopTypes = {"CAFE", "ROUPAS", "LANCHONETE", "MERCADO"};
        String[] shopNames = {"Café Farol", "Brechó da Duda", "Lanche Estrela", "Mercado Bem-Estar", "Pão & Cia", "Loja Girassol"};
        int n = 2 + rnd.nextInt(2);
        float totalW = x1 - x0 - 3;
        float w = totalW / n;
        for (int s = 0; s < n; s++) {
            float bx = x0 + 1.5f + s * w;
            float h = 8 + rnd.nextFloat() * 9;
            int idx = rnd.nextInt(shopTypes.length);
            boolean withDoor = rnd.nextFloat() < 0.6f;
            addShop(day, night, shell, bx, z0 + 2, Math.min(bx + w - 1, x1 - 1), z1, h,
                    walls[rnd.nextInt(walls.length)], signs[rnd.nextInt(signs.length)], rnd,
                    withDoor ? shopTypes[idx] : null, shopNames[rnd.nextInt(shopNames.length)], withWindows);
        }
    }

    private void buildResidential(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                                  float x0, float z0, float x1, float z1, Random rnd) {
        int[] walls = {0xffc8b8a0, 0xffb0c0b8, 0xffd0b090, 0xffa8b8c8, 0xffc0a8a0};
        int n = 2;
        for (int hx = 0; hx < 2; hx++) {
            for (int hz = 0; hz < n; hz++) {
                if (rnd.nextFloat() < 0.18f) continue; // lote vazio
                float w = (x1 - x0) / 2 - 3.5f;
                float d = (z1 - z0) / 2 - 3.5f;
                float bx = x0 + 2.5f + hx * (w + 5);
                float bz = z0 + 2.5f + hz * (d + 5);
                float h = 4.5f + rnd.nextFloat() * 2.5f;
                int wall = walls[rnd.nextInt(walls.length)];
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.wallQuads(bx, bz, bx + w, bz + d, h, wall);
                    // telhado
                    b.box(bx - 0.5f, h, bz - 0.5f, bx + w + 0.5f, h + 1.6f, bz + d + 0.5f, 0xff8a4a34);
                    // porta e janelas simples
                    b.quad(bx + w / 2 - 0.7f, 0, bz + d + 0.04f, bx + w / 2 - 0.7f, 2.1f, bz + d + 0.04f,
                            bx + w / 2 + 0.7f, 2.1f, bz + d + 0.04f, bx + w / 2 + 0.7f, 0, bz + d + 0.04f, 0xff4a3428, false);
                    b.quad(bx + 1, 1.1f, bz + d + 0.04f, bx + 1, 2.1f, bz + d + 0.04f,
                            bx + 2.4f, 2.1f, bz + d + 0.04f, bx + 2.4f, 1.1f, bz + d + 0.04f, 0xff35506a, false);
                }
                shell.box(bx, 0, bz, bx + w, h + 1.6f, bz + d, wall);
                colliders.add(new AABB(bx, 0, bz, bx + w, h, bz + d, this));
                maxY = Math.max(maxY, h + 2);
                // arvore no quintal
                if (rnd.nextFloat() < 0.6f) {
                    addTree(day, night, shell, bx + w + 2f, bz + d / 2, rnd);
                }
                if (rnd.nextFloat() < 0.35f) {
                    parkedSlots.add(new ParkedSlot(bx + w / 2, bz + d + 2.2f, (float) Math.PI, "HATCH"));
                }
            }
        }
        // sem portas genericas em zonas residenciais (casas sem interior)
    }

    private void buildIndustrial(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                                 float x0, float z0, float x1, float z1, Random rnd) {
        float w = (x1 - x0) * 0.72f;
        float h = 9 + rnd.nextFloat() * 5;
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.wallQuads(x0 + 2, z0 + 2, x0 + 2 + w, z1 - 2, h, 0xff707880);
            b.box(x0 + 2, h, z0 + 2, x0 + 2 + w, h + 0.4f, z1 - 2, 0xff5c646c);
            // portao
            b.quad(x0 + 2 + w / 2 - 3, 0, z1 + 0.05f, x0 + 2 + w / 2 - 3, 4.5f, z1 + 0.05f,
                    x0 + 2 + w / 2 + 3, 4.5f, z1 + 0.05f, x0 + 2 + w / 2 + 3, 0, z1 + 0.05f, 0xff4a4e54, false);
            // chamine
            b.cylinder(x1 - 4, 0, z0 + 5, 1.6f, h + 10, 8, 0xff8a4438, 0xff9a5448);
        }
        shell.box(x0 + 2, 0, z0 + 2, x0 + 2 + w, h, z1 - 2, 0xff707880);
        colliders.add(new AABB(x0 + 2, 0, z0 + 2, x0 + 2 + w, h, z1 - 2, this));
        colliders.add(new AABB(x1 - 5.6f, 0, z0 + 3.4f, x1 - 2.4f, h + 10, z0 + 6.6f, this));
        maxY = Math.max(maxY, h + 11);
        // contenedores
        int nC = 2 + rnd.nextInt(4);
        for (int c = 0; c < nC; c++) {
            addContainer(day, night, shell, x0 + 4 + rnd.nextFloat() * (x1 - x0 - 8), z0 + 4 + rnd.nextFloat() * (z1 - z0 - 8),
                    rnd.nextFloat() * 0.5f, rnd, 0);
        }
        for (int c = 0; c < 2; c++) {
            parkedSlots.add(new ParkedSlot(x1 - 3, z0 + 8 + c * 9, 0, rnd.nextFloat() < 0.5f ? "TRUCK" : "VAN"));
        }
    }

    private void buildPort(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                           float x0, float z0, float x1, float z1, Random rnd) {
        // patio de contenedores
        for (int c = 0; c < 6 + rnd.nextInt(5); c++) {
            float px = x0 + 3 + rnd.nextFloat() * (x1 - x0 - 6);
            float pz = z0 + 3 + rnd.nextFloat() * (z1 - z0 - 6);
            float y0 = rnd.nextFloat() < 0.3f ? 2.6f : 0;
            addContainer(day, night, shell, px, pz, rnd.nextFloat() * 0.4f, rnd, y0);
        }
        // guindaste simples
        if (rnd.nextFloat() < 0.5f) {
            float gx = (x0 + x1) / 2, gz = (z0 + z1) / 2;
            for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                b.box(gx - 1f, 0, gz - 12f, gx + 1f, 20f, gz - 9f, 0xffb08828);
                b.box(gx - 1f, 0, gz + 9f, gx + 1f, 20f, gz + 12f, 0xffb08828);
                b.box(gx - 1.2f, 19f, gz - 13f, gx + 1.2f, 21f, gz + 13f, 0xffc89838);
            }
            colliders.add(new AABB(gx - 1.2f, 0, gz - 12f, gx + 1.2f, 20f, gz - 9f, this));
            colliders.add(new AABB(gx - 1.2f, 0, gz + 9f, gx + 1.2f, 20f, gz + 12f, this));
            maxY = Math.max(maxY, 21);
        }
        // galpao
        float w = (x1 - x0) * 0.5f;
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.wallQuads(x0 + 2, z0 + 2, x0 + 2 + w, z0 + 2 + (z1 - z0) * 0.5f, 8f, 0xff606468);
            b.box(x0 + 2, 8f, z0 + 2, x0 + 2 + w, 8.4f, z0 + 2 + (z1 - z0) * 0.5f, 0xff54585c);
        }
        shell.box(x0 + 2, 0, z0 + 2, x0 + 2 + w, z0 + 2 + (z1 - z0) * 0.5f, 8f, 0xff606468);
        colliders.add(new AABB(x0 + 2, 0, z0 + 2, x0 + 2 + w, 8f, z0 + 2 + (z1 - z0) * 0.5f, this));
        parkedSlots.add(new ParkedSlot((x0 + x1) / 2, z1 - 3, (float) Math.PI, "TRUCK"));
    }

    private void buildPark(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                           float x0, float z0, float x1, float z1, Random rnd) {
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.groundQuad(x0, z0, x1, z1, 0.06f, 0xff3d6b35);
            // caminhos
            b.groundQuad(x0, (z0 + z1) / 2 - 1.5f, x1, (z0 + z1) / 2 + 1.5f, 0.07f, 0xffa89878);
            b.groundQuad((x0 + x1) / 2 - 1.5f, z0, (x0 + x1) / 2 + 1.5f, z1, 0.07f, 0xffa89878);
            // lago
            float lx = x0 + (x1 - x0) * 0.25f, lz = z0 + (z1 - z0) * 0.25f;
            b.groundQuad(lx - 5, lz - 4, lx + 5, lz + 4, 0.08f, 0xff2c5a74);
            // fonte central
            float fx = (x0 + x1) / 2, fz = (z0 + z1) / 2;
            b.cylinder(fx, 0.07f, fz, 2.6f, 0.8f, 10, 0xff9a9a92, 0xffb0b0a8);
            b.cylinder(fx, 0.8f, fz, 0.7f, 2.6f, 8, 0xffaaaaa2, 0xffc0c0b8);
        }
        int trees = 8 + rnd.nextInt(6);
        for (int t = 0; t < trees; t++) {
            float tx = x0 + 3 + rnd.nextFloat() * (x1 - x0 - 6);
            float tz = z0 + 3 + rnd.nextFloat() * (z1 - z0 - 6);
            if (Math.abs(tx - (x0 + x1) / 2) < 3 || Math.abs(tz - (z0 + z1) / 2) < 3) continue;
            addTree(day, night, shell, tx, tz, rnd);
        }
        addBench(day, night, (x0 + x1) / 2 + 4, (z0 + z1) / 2 + 4, 0.6f);
        addBench(day, night, (x0 + x1) / 2 - 4, (z0 + z1) / 2 - 4, 0.6f + (float) Math.PI);
        maxY = Math.max(maxY, 6);
    }

    private void buildShanty(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                             float x0, float z0, float x1, float z1, Random rnd) {
        int[] walls = {0xffa08868, 0xff88a088, 0xffa8a078, 0xff987868, 0xff8a8a98};
        for (int hx = 0; hx < 2; hx++) {
            for (int hz = 0; hz < 2; hz++) {
                if (rnd.nextFloat() < 0.25f) continue;
                float w = (x1 - x0) / 2 - 4;
                float bx = x0 + 3 + hx * (w + 8);
                float bz = z0 + 3 + hz * (w + 8);
                float h = 3 + rnd.nextFloat() * 1.5f;
                int wall = walls[rnd.nextInt(walls.length)];
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.wallQuads(bx, bz, bx + w, bz + w, h, wall);
                    b.box(bx - 0.3f, h, bz - 0.3f, bx + w + 0.3f, h + 0.8f, bz + w + 0.3f, 0xff7a7a72);
                }
                shell.box(bx, 0, bz, bx + w, h + 0.8f, bz + w, wall);
                colliders.add(new AABB(bx, 0, bz, bx + w, h, bz + w, this));
            }
        }
    }

    // ================= BLOCOS ESPECIAIS =================

    private void buildSpecial(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                              CityLayout.Special s, float x0, float z0, float x1, float z1,
                              Random rnd, boolean withWindows) {
        float midX = (x0 + x1) / 2, midZ = (z0 + z1) / 2;
        switch (s) {
            case PRACA: {
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.groundQuad(x0, z0, x1, z1, 0.12f, 0xff9a9284);
                    b.groundQuad(midX - 6, midZ - 6, midX + 6, midZ + 6, 0.13f, 0xff8a8274);
                    b.cylinder(midX, 0.13f, midZ, 3.2f, 1f, 12, 0xffb0a898, 0xffc0b8a8);
                    b.cylinder(midX, 1f, midZ, 0.8f, 3.2f, 8, 0xffaaa89a, 0xffc0beb0);
                }
                for (int t = 0; t < 6; t++) {
                    float a = t / 6f * (float) Math.PI * 2;
                    addTree(day, night, shell, midX + (float) Math.cos(a) * 10, midZ + (float) Math.sin(a) * 10, rnd);
                }
                addBench(day, night, midX + 8, midZ - 8, 2.4f);
                addBench(day, night, midX - 8, midZ + 8, 0.8f);
                maxY = Math.max(maxY, 6);
                break;
            }
            case HOSPITAL: {
                addBuilding(day, night, shell, x0 + 3, z0 + 3, x1 - 3, z1 - 6, 16, 0xffe8e8e4, 0xffd0d0cc, rnd, withWindows);
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    // cruz vermelha
                    b.box(midX - 0.6f, 12, z1 - 6 + 0.06f, midX + 0.6f, 16, z1 - 6 + 0.24f, 0xffc02020, 0xffc02020, 0xffc02020, true);
                    b.box(midX - 2, 13.4f, z1 - 6 + 0.06f, midX + 2, 14.6f, z1 - 6 + 0.24f, 0xffc02020, 0xffc02020, 0xffc02020, true);
                }
                doors.add(new Door(midX, z1 - 4.5f, (float) Math.PI, "HOSPITAL", null, "Hospital Santa Clara"));
                break;
            }
            case DELEGACIA: {
                addBuilding(day, night, shell, x0 + 3, z0 + 3, x1 - 3, z1 - 6, 10, 0xffc8ccd4, 0xffb0b4bc, rnd, withWindows);
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.box(x0 + 4, 7, z1 - 6 + 0.06f, x1 - 4, 8.6f, z1 - 6 + 0.24f, 0xff2850a0, 0xff2850a0, 0xff2850a0, true);
                }
                doors.add(new Door(midX, z1 - 4.5f, (float) Math.PI, "DELEGACIA", null, "12ª Delegacia"));
                parkedSlots.add(new ParkedSlot(x1 - 4, z1 - 3, 0, "POLICE"));
                parkedSlots.add(new ParkedSlot(x0 + 4, z1 - 3, 0, "POLICE"));
                break;
            }
            case CONCESSIONARIA: {
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.box(x0 + 2, 0, z0 + 2, x1 - 2, 0.15f, z1 - 2, 0xff50545c);
                    // vidraças
                    b.wallQuads(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 5.5f, 0xffb8d8e8);
                    b.box(x0 + 2, 5.5f, z0 + 2, x1 - 2, 6f, z1 - 2, 0xff8a9098);
                    b.box(x0 + 6, 6f, z1 - 6, x1 - 6, 7.4f, z1 - 5.8f, 0xff30c0d0, 0xff30c0d0, 0xff30c0d0, true);
                }
                shell.box(x0 + 2, 0, z0 + 2, x1 - 2, 6f, z1 - 2, 0xffb0c8d8);
                colliders.add(new AABB(x0 + 2, 0, z0 + 2, x1 - 2, 5.5f, z1 - 2, this));
                doors.add(new Door(midX, z1 - 3.5f, (float) Math.PI, "CONCESSIONARIA", null, "AutoVaurora"));
                parkedSlots.add(new ParkedSlot(midX - 6, z1 - 6, 0.2f, "SPORTS"));
                parkedSlots.add(new ParkedSlot(midX, z1 - 6, 0.2f, "SPORTS"));
                parkedSlots.add(new ParkedSlot(midX + 6, z1 - 6, 0.2f, "SEDAN"));
                break;
            }
            case OFICINA: {
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.wallQuads(x0 + 2, z0 + 2, x1 - 2, z1 - 4, 7f, 0xff787068);
                    b.box(x0 + 2, 7f, z0 + 2, x1 - 2, 7.4f, z1 - 4, 0xff6a625a);
                    b.box(x0 + 5, 5.5f, z1 - 4 + 0.05f, x1 - 5, 7f, z1 - 4 + 0.2f, 0xffffa020, 0xffffa020, 0xffffa020, true);
                }
                shell.box(x0 + 2, 0, z0 + 2, x1 - 2, 7.4f, z1 - 4, 0xff787068);
                colliders.add(new AABB(x0 + 2, 0, z0 + 2, x1 - 2, 7f, z1 - 4, this));
                doors.add(new Door(midX, z1 - 2f, (float) Math.PI, null, "OFICINA", "Oficina do Nino"));
                break;
            }
            case IMOBILIARIA: {
                addShop(day, night, shell, x0 + 4, z0 + 4, x1 - 4, z1 - 4, 6, 0xffb0a890, 0xff20d0a0, rnd, "IMOBILIARIA", "Chaves & Filhos", false);
                break;
            }
            case ARMERIA: {
                addShop(day, night, shell, x0 + 4, z0 + 4, x1 - 4, z1 - 4, 6, 0xff6a6a72, 0xffff4030, rnd, "ARMERIA", "Casa do Ferreiro", false);
                break;
            }
            case BRECHO: {
                addShop(day, night, shell, x0 + 4, z0 + 4, x1 - 4, z1 - 4, 6, 0xffc8a8b8, 0xffff60c0, rnd, "ROUPAS", "Brechó da Duda", false);
                break;
            }
            case LANCHONETE: {
                addShop(day, night, shell, x0 + 4, z0 + 4, x1 - 4, z1 - 4, 5.5f, 0xffe0c090, 0xffffe040, rnd, "LANCHONETE", "Forno de Ouro", false);
                break;
            }
            case CASA_MAE: {
                buildResidential(day, night, shell, x0, z0, x1, z1, rnd);
                doors.add(new Door(midX, z1 - 1.5f, (float) Math.PI, "CASA_MAE", null, "Casa da Dona Lurdes"));
                break;
            }
            case ESTACIONAMENTO_A:
            case ESTACIONAMENTO_B: {
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.groundQuad(x0, z0, x1, z1, 0.12f, 0xff3c3e42);
                }
                for (float px = x0 + 3; px < x1 - 3; px += 3.2f) {
                    for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                        b.groundQuad(px, z0 + 3, px + 0.18f, z1 - 3, 0.125f, 0xffb0b0aa);
                    }
                    parkedSlots.add(new ParkedSlot(px + 1.5f, (z0 + z1) / 2, (float) Math.PI / 2, rnd.nextFloat() < 0.3f ? "SPORTS" : "SEDAN"));
                    if (rnd.nextFloat() < 0.8f) parkedSlots.get(parkedSlots.size() - 1).occupied = false;
                }
                break;
            }
            case POSTO_A:
            case POSTO_B:
            case POSTO_C:
            case POSTO_D: {
                buildGasStation(day, night, shell, x0, z0, x1, z1, midX, midZ, rnd);
                break;
            }
            case APARTAMENTO: {
                addBuilding(day, night, shell, x0 + 4, z0 + 4, x1 - 4, z1 - 6, 22, 0xffb8b0a0, 0xff9a9284, rnd, withWindows);
                doors.add(new Door(midX, z1 - 4.5f, (float) Math.PI, "APARTAMENTO", null, "Apartamento Beira-Mar"));
                break;
            }
            case COBERTURA: {
                addBuilding(day, night, shell, x0 + 4, z0 + 4, x1 - 4, z1 - 6, 34, 0xff8a94a4, 0xff6a7484, rnd, withWindows);
                doors.add(new Door(midX, z1 - 4.5f, (float) Math.PI, "COBERTURA", null, "Cobertura Vaurora"));
                break;
            }
            case GALPAO_CASA: {
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.wallQuads(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 9f, 0xff606468);
                    b.box(x0 + 2, 9f, z0 + 2, x1 - 2, 9.4f, z1 - 2, 0xff54585c);
                }
                shell.box(x0 + 2, 0, z0 + 2, x1 - 2, 9.4f, z1 - 2, 0xff606468);
                colliders.add(new AABB(x0 + 2, 0, z0 + 2, x1 - 2, 9f, z1 - 2, this));
                doors.add(new Door(midX, z1 - 2.5f, (float) Math.PI, "GALPAO_CASA", null, "Galpão (sua propriedade)"));
                maxY = Math.max(maxY, 10);
                break;
            }
            case GALPAO_PORTO: {
                for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                    b.wallQuads(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 11f, 0xff50585e);
                    b.box(x0 + 2, 11f, z0 + 2, x1 - 2, 11.5f, z1 - 2, 0xff464e54);
                    b.box(midX - 4, 0, z1 - 2 + 0.05f, midX + 4, 6f, z1 - 2 + 0.3f, 0xff3a4046);
                }
                shell.box(x0 + 2, 0, z0 + 2, x1 - 2, 11.5f, z1 - 2, 0xff50585e);
                colliders.add(new AABB(x0 + 2, 0, z0 + 2, x1 - 2, 11f, z1 - 2, this));
                parkedSlots.add(new ParkedSlot(midX, z1 + 4, 0, "TRUCK"));
                maxY = Math.max(maxY, 12);
                break;
            }
        }
    }

    private void buildGasStation(Mesh.Builder day, Mesh.Builder night, Mesh.Builder shell,
                                 float x0, float z0, float x1, float z1, float midX, float midZ, Random rnd) {
        for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
            b.groundQuad(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 0.12f, 0xff54565a);
            // cobertura
            for (float px : new float[]{midX - 7, midX + 7}) {
                b.box(px - 0.3f, 0, midZ - 5, px + 0.3f, 5.4f, midZ - 4.4f, 0xff8a8a88);
                b.box(px - 0.3f, 0, midZ + 4.4f, px + 0.3f, 5.4f, midZ + 5f, 0xff8a8a88);
            }
            b.box(midX - 9, 5.4f, midZ - 6, midX + 9, 6f, midZ + 6, 0xffd02828, 0xffd02828, 0xffa02020, false);
            // bombas
            for (float px : new float[]{midX - 5, midX + 5}) {
                b.box(px - 0.5f, 0.12f, midZ - 0.8f, px + 0.5f, 1.6f, midZ + 0.8f, 0xffe8e8e4);
            }
            // lojinha
            b.wallQuads(x1 - 10, z1 - 9, x1 - 3, z1 - 3, 3.6f, 0xffc8c4b8);
            b.box(x1 - 10, 3.6f, z1 - 9, x1 - 3, 4f, z1 - 3, 0xffb02828);
        }
        colliders.add(new AABB(x1 - 10, 0, z1 - 9, x1 - 3, 3.6f, z1 - 3, this));
        colliders.add(new AABB(midX - 9, 5f, midZ - 6, midX + 9, 6f, midZ + 6, this)); // cobertura (so afeta voo)
        doors.add(new Door(midX, midZ + 2, 0, null, "POSTO", "Posto Girassol"));
        maxY = Math.max(maxY, 7);
    }

    // ================= PROPS DE RUA =================

    private void buildStreetProps(Mesh.Builder day, Mesh.Builder night, Random rnd,
                                  float rx0, float rz0, float hwW, float hwN,
                                  float bx0, float bz0, float bx1, float bz1) {
        // lampadas ao longo da calada norte e oeste
        float zStart = rz0 + hwN + 2;
        float zEnd = bz1;
        for (float z = zStart; z < zEnd; z += 22) {
            addLamp(day, night, bx0 - 1.4f, z, 1);
        }
        float xStart = rx0 + hwW + 2;
        float xEnd = bx1;
        for (float x = xStart; x < xEnd; x += 22) {
            addLamp(day, night, x, bz0 - 1.4f, -1);
        }
        // hidrante / lixeira / bancos
        if (rnd.nextFloat() < 0.5f) addHydrant(day, night, bx0 - 1.5f, bz1 - 4);
        if (rnd.nextFloat() < 0.6f) addTrash(day, night, bx1 + 1.5f, bz0 + 4);
        if (district == District.PARQUE || district == District.RESIDENCIAL) {
            if (rnd.nextFloat() < 0.5f) addBench(day, night, bx0 + 2, bz0 + 2, 0.5f);
        }
        // ponto de onibus em avenidas
        if (CityLayout.isMajor(j) && rnd.nextFloat() < 0.4f) {
            float sx = bx0 + (rnd.nextFloat() < 0.5f ? 2 : (bx1 - bx0) - 2);
            for (Mesh.Builder b : new Mesh.Builder[]{day, night}) {
                b.box(sx - 2, 0, bz0 - 2.4f, sx + 2, 0.5f, bz0 - 1.6f, 0xff7a7060);
                b.box(sx - 2, 2.6f, bz0 - 2.6f, sx + 2, 2.9f, bz0 - 1.4f, 0xff3a5a8a);
                b.box(sx - 0.15f, 0, bz0 - 2.5f, sx + 0.15f, 2.6f, bz0 - 2.2f, 0xff3a5a8a);
            }
        }
    }
}
