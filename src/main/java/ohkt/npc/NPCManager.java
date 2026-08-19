package ohkt.npc;

import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleType;
import ohkt.world.CityLayout;
import ohkt.world.District;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * População dinâmica: densidade por horário/bairro, pedestres com rotinas,
 * motoristas de tráfego, criminosos noturnos e aliados de missão.
 * Atualização com LOD (NPCs distantes atualizam com menos frequência).
 */
public final class NPCManager {

    private final List<NPC> npcs = new ArrayList<>();
    private final Random rnd = new Random(2024);
    private static final int MAX_NPCS = 42;
    private static final int MAX_TRAFFIC = 14;

    public List<NPC> list() { return npcs; }

    public NPC spawn(NPC.Type type, float x, float z) {
        NPC n = new NPC(type, x, z);
        npcs.add(n);
        return n;
    }

    public void update(Game g, float dt) {
        populationControl(g, dt);

        Iterator<NPC> it = npcs.iterator();
        while (it.hasNext()) {
            NPC n = it.next();
            // despawn por distância (exceto persistentes de missão)
            if (!n.missionPersistent) {
                float dx = n.pos.x - g.player.pos.x, dz = n.pos.z - g.player.pos.z;
                boolean far = dx * dx + dz * dz > (n.vehicle != null ? 230 * 230 : 160 * 160);
                if (far && n.type != NPC.Type.ALLY) {
                    if (n.vehicle != null) {
                        if (n.vehicle.driver == n) {
                            n.vehicle.driver = null;
                            n.vehicle.driverInput.clear();
                        }
                        n.vehicle = null;
                    }
                    it.remove();
                    continue;
                }
            }
            // LOD de atualização
            float dx = n.pos.x - g.player.pos.x, dz = n.pos.z - g.player.pos.z;
            float d2 = dx * dx + dz * dz;
            n.lodAcc += dt;
            float interval = d2 < 60 * 60 ? 0f : (d2 < 110 * 110 ? 0.033f : 0.1f);
            if (n.lodAcc >= interval) {
                float step = n.lodAcc;
                n.lodAcc = 0;
                n.update(g, step);
            }
            // atropelamento
            if (!n.dead && n.vehicle == null) {
                for (Vehicle v : g.vehicles.list()) {
                    if (Math.abs(v.forwardSpeed()) < 3f) continue;
                    float fx = (float) Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw);
                    float relx = n.pos.x - v.pos.x, relz = n.pos.z - v.pos.z;
                    float along = relx * fx + relz * fz;
                    float side = relx * (float) Math.cos(v.yaw) + relz * (float) Math.sin(v.yaw);
                    if (along > 0 && along < v.type.hz + 0.6f && Math.abs(side) < v.type.hx + 0.35f) {
                        n.hitByVehicle(g, v);
                        break;
                    }
                }
            }
            if (n.delete) it.remove();
        }
    }

    private void populationControl(Game g, float dt) {
        float hour = g.world.time.hour;
        float hourFactor = (hour > 6.5f && hour < 22f) ? 1f : (hour > 22f || hour < 5f ? 0.18f : 0.5f);
        District d = CityLayout.districtAt(g.player.pos.x, g.player.pos.z);
        int targetPeds = Math.round(20 * d.pedDensity() * hourFactor);
        int targetTraffic = Math.round(MAX_TRAFFIC * (hour > 6f && hour < 23f ? 1f : 0.35f));

        int peds = 0, traffic = 0, criminals = 0;
        for (NPC n : npcs) {
            if (n.dead) continue;
            if (n.vehicle != null && n.isTraffic()) traffic++;
            else if (n.type == NPC.Type.CRIMINAL || n.type == NPC.Type.GANG) criminals++;
            else if (n.type == NPC.Type.PEDESTRIAN) peds++;
        }

        if (peds < targetPeds && npcs.size() < MAX_NPCS) {
            float[] p = g.world.sidewalkPointNear(
                    g.player.pos.x + rnd.nextFloat() * 90 - 45,
                    g.player.pos.z + rnd.nextFloat() * 90 - 45, rnd);
            if (CityLayout.inCity(p[0], p[1])) {
                spawn(NPC.Type.PEDESTRIAN, p[0], p[1]);
            }
        }
        if (traffic < targetTraffic) {
            spawnTrafficCar(g);
        }
        // criminosos à noite em bairros perigosos
        boolean night = hour > 21 || hour < 4.5f;
        int targetCrim = night ? Math.round(6 * d.crime()) : 0;
        if (criminals < targetCrim && npcs.size() < MAX_NPCS) {
            float[] p = g.world.sidewalkPointNear(
                    g.player.pos.x + rnd.nextFloat() * 80 - 40,
                    g.player.pos.z + rnd.nextFloat() * 80 - 40, rnd);
            spawn(rnd.nextFloat() < 0.3f ? NPC.Type.GANG : NPC.Type.CRIMINAL, p[0], p[1]);
        }
    }

    private void spawnTrafficCar(Game g) {
        int[] node = g.world.roadGraph.randomNodeNear(rnd, g.player.pos.x, g.player.pos.z, 2, 4);
        float x = CityLayout.roadCoord(node[0]);
        float z = CityLayout.roadCoord(node[1]);
        float dx = x - g.player.pos.x, dz = z - g.player.pos.z;
        if (dx * dx + dz * dz < 60 * 60) return; // perto demais
        District d = CityLayout.districtAt(x, z);
        String hint;
        switch (d) {
            case CENTRO: hint = rnd.nextFloat() < 0.35f ? "TAXI" : "SEDAN"; break;
            case COMERCIAL: hint = rnd.nextFloat() < 0.2f ? "TAXI" : (rnd.nextFloat() < 0.5f ? "HATCH" : "SEDAN"); break;
            case INDUSTRIAL: hint = rnd.nextFloat() < 0.5f ? "TRUCK" : "VAN"; break;
            case PORTO: hint = "TRUCK"; break;
            case RESIDENCIAL: hint = rnd.nextFloat() < 0.4f ? "HATCH" : "PICKUP"; break;
            case PERIFERIA: hint = rnd.nextFloat() < 0.35f ? "MOTO" : "HATCH"; break;
            default: hint = rnd.nextFloat() < 0.25f ? "SPORTS" : "SEDAN";
        }
        VehicleType type = VehicleType.byKindHint(hint);
        int[] palette = {0xffc02020, 0xff2050c0, 0xffe8e8e8, 0xff18181c, 0xff30a040, 0xffd8b028, 0xff8898b0};
        Vehicle v = g.vehicles.spawn(type, x, z, rnd.nextFloat() * (float) Math.PI * 2, type.kind.equals("TAXI") || type.kind.equals("POLICE") ? type.defaultPaint : palette[rnd.nextInt(palette.length)]);
        NPC driver = spawn(NPC.Type.PEDESTRIAN, x, z);
        driver.vehicle = v;
        v.driver = driver;
        driver.brain = NPC.Brain.WANDER;
        driver.nodeA = node;
        driver.nodeB = g.world.roadGraph.randomNodeNear(rnd, x, z, 1, 2);
        v.lightsOn = g.world.time.isNight();
    }

    // ---------------- eventos ----------------

    public void onGunshot(Game g, float x, float z) {
        for (NPC n : npcs) {
            if (n.dead) continue;
            n.hearGunshot(g, x, z);
        }
    }

    public void onExplosion(Game g, float x, float z) {
        for (NPC n : npcs) {
            if (n.dead) continue;
            float d = n.pos.dst(x, n.pos.y, z);
            if (d < 80) {
                if (n.vehicle != null) {
                    n.brain = NPC.Brain.PANIC_DRIVE;
                    n.timer = 8;
                } else {
                    n.panic(x, z);
                }
            }
        }
    }

    public void render(Game g, ohkt.graphics.Renderer3D r) {
        for (NPC n : npcs) {
            float dx = n.pos.x - r.cam.pos.x, dz = n.pos.z - r.cam.pos.z;
            if (dx * dx + dz * dz > 180 * 180) continue;
            if (!r.sphereVisible(n.pos.x, n.pos.y + 1, n.pos.z, 2)) continue;
            if (n.vehicle != null) continue; // motorista desenhado pelo veículo (invisível)
            n.render(g, r, 80);
        }
    }

    /** Remove todos (novo jogo/carregar). */
    public void clear() {
        npcs.clear();
    }

    /** Contagem para HUD/debug. */
    public int aliveCount() {
        int c = 0;
        for (NPC n : npcs) {
            if (!n.dead) c++;
        }
        return c;
    }
}
