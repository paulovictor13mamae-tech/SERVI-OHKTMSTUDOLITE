package ohkt.police;

import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.graphics.Renderer3D;
import ohkt.npc.NPC;
import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleType;
import ohkt.world.CityLayout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Polícia de Porto Aurora: dispatcher por nível de procurado, viaturas,
 * policiais a pé, bloqueios de rua, helicóptero e prisão.
 */
public final class PoliceSystem {

    public final WantedSystem wantedSystem = new WantedSystem();
    public float arrestProgress;

    private final List<NPC> officers = new ArrayList<>();      // policiais (a pé ou dirigindo)
    private final List<Vehicle> cars = new ArrayList<>();
    private final List<Vehicle> roadblocks = new ArrayList<>();
    private final Random rnd = new Random(555);

    private float dispatchTimer;
    private float roadblockTimer;
    private float lastSeenTimer;

    // helicóptero
    private boolean heliActive;
    private float heliX, heliY = 34f, heliZ, heliYaw, heliRotor;
    private float heliShootTimer;

    private float sirenAudioTimer;

    public void update(Game g, float dt) {
        int stars = wantedSystem.stars;

        // visibilidade do jogador pela polícia
        boolean seen = false;
        for (NPC o : officers) {
            if (o.dead) continue;
            if (o.pos.dst(g.player.pos) < 40 && o.hasLineOfSightPublic(g)) {
                seen = true;
                break;
            }
        }
        if (!seen && heliActive) seen = true;
        wantedSystem.update(dt, g.world.time.worldTime, stars > 0 && seen);
        if (stars == 0 && !wantedSystem.suppressed) {
            arrestProgress = 0;
            recallAll(g);
        }

        // dispatcher
        dispatchTimer -= dt;
        if (dispatchTimer <= 0 && stars > 0) {
            dispatchTimer = 4.5f - stars * 0.6f;
            dispatch(g, stars);
        }

        // bloqueios a partir de 3 estrelas
        roadblockTimer -= dt;
        if (stars >= 3 && roadblockTimer <= 0) {
            roadblockTimer = 24f;
            trySpawnRoadblock(g);
        }

        // helicóptero a partir de 4
        if (stars >= 4 && !heliActive) {
            spawnHeli(g);
        } else if (stars < 4 && heliActive) {
            heliActive = false;
        }
        if (heliActive) updateHeli(g, dt);

        // prisão: progresso compartilhado decai
        if (arrestProgress > 0) {
            arrestProgress = Math.max(0, arrestProgress - dt * 0.4f);
        }

        updateUnits(g, dt);
        updateSirenAudio(g, dt);
    }

    // ---------------- dispatcher ----------------

    private void dispatch(Game g, int stars) {
        int desiredCars = Math.min(7, stars == 1 ? 1 : stars == 2 ? 2 : stars == 3 ? 3 : stars + 1);
        int desiredFoot = stars >= 2 ? 2 + stars : 1;

        int carCount = 0, footCount = 0;
        for (NPC o : officers) {
            if (o.dead) continue;
            if (o.vehicle != null) carCount++;
            else footCount++;
        }

        if (carCount < desiredCars) spawnPatrolCar(g, stars);
        if (footCount < desiredFoot) spawnFootOfficer(g);
    }

    private void spawnPatrolCar(Game g, int stars) {
        int[] node = g.world.roadGraph.randomNodeNear(rnd, g.player.pos.x, g.player.pos.z, 2, 4);
        float x = CityLayout.roadCoord(node[0]);
        float z = CityLayout.roadCoord(node[1]);
        Vehicle v = g.vehicles.spawn(VehicleType.byKindHint("POLICE"), x, z,
                (float) Math.atan2(g.player.pos.x - x, -(g.player.pos.z - z)), VehicleType.byKindHint("POLICE").defaultPaint);
        v.sirenOn = true;
        v.lightsOn = true;
        cars.add(v);
        NPC officer = g.npcs.spawn(NPC.Type.COP, x, z);
        officer.vehicle = v;
        v.driver = officer;
        officer.brain = NPC.Brain.CHASE_PLAYER;
        officers.add(officer);
    }

    private void spawnFootOfficer(Game g) {
        float ang = rnd.nextFloat() * (float) Math.PI * 2;
        float d = 40 + rnd.nextFloat() * 30;
        float[] p = g.world.sidewalkPointNear(g.player.pos.x + (float) Math.cos(ang) * d, g.player.pos.z + (float) Math.sin(ang) * d, rnd);
        NPC officer = g.npcs.spawn(NPC.Type.COP, p[0], p[1]);
        officer.brain = NPC.Brain.CHASE_PLAYER;
        officers.add(officer);
    }

    private void trySpawnRoadblock(Game g) {
        ohkt.player.Player pl = g.player;
        if (pl.state != ohkt.player.Player.State.DRIVING || pl.vehicle == null) return;
        float sp = Math.abs(pl.vehicle.forwardSpeed());
        if (sp < 8) return;
        float fx = (float) Math.sin(pl.vehicle.yaw), fz = (float) -Math.cos(pl.vehicle.yaw);
        float bx = pl.pos.x + fx * 90, bz = pl.pos.z + fz * 90;
        int[] node = g.world.roadGraph.nearestNode(bx, bz);
        float nx = CityLayout.roadCoord(node[0]), nz = CityLayout.roadCoord(node[1]);
        // só em ruas (evita dentro de prédios)
        float yaw = Math.abs(fx) > Math.abs(fz) ? 0 : (float) (Math.PI / 2);
        for (int i = -1; i <= 1; i += 2) {
            Vehicle v = g.vehicles.spawn(VehicleType.byKindHint("POLICE"),
                    nx + (yaw == 0 ? 0 : i * 2.6f), nz + (yaw == 0 ? i * 4.5f : 0), yaw + 0.35f,
                    VehicleType.byKindHint("POLICE").defaultPaint);
            v.sirenOn = true;
            v.lightsOn = true;
            roadblocks.add(v);
            NPC officer = g.npcs.spawn(NPC.Type.COP, v.pos.x + 2, v.pos.z + 2);
            officer.brain = NPC.Brain.CHASE_PLAYER;
            officers.add(officer);
        }
        g.hud.notify("A polícia montou um bloqueio!");
    }

    private void updateUnits(Game g, float dt) {
        Iterator<NPC> it = officers.iterator();
        while (it.hasNext()) {
            NPC o = it.next();
            if (o.delete) {
                it.remove();
                continue;
            }
            if (o.dead) {
                // substituir
                if (wantedSystem.stars > 0 && rnd.nextFloat() < 0.4f) {
                    wantedSystem.crime(g.world.time.worldTime, "COP_KILLED", true);
                }
                it.remove();
                continue;
            }
            // policial que chegou perto demais do jogador a pé
            if (o.vehicle != null && wantedSystem.stars == 0) {
                o.pullOutOfVehicle();
                it.remove();
            }
        }
        // limpar viaturas destruídas/distantes
        Iterator<Vehicle> vit = cars.iterator();
        while (vit.hasNext()) {
            Vehicle v = vit.next();
            boolean far = Math.abs(v.pos.x - g.player.pos.x) > 260 || Math.abs(v.pos.z - g.player.pos.z) > 260;
            if (v.destroyed || (far && v.driver == null)) vit.remove();
        }
        Iterator<Vehicle> rit = roadblocks.iterator();
        while (rit.hasNext()) {
            Vehicle v = rit.next();
            if (v.destroyed) {
                rit.remove();
                continue;
            }
            boolean far = Math.abs(v.pos.x - g.player.pos.x) > 280 || Math.abs(v.pos.z - g.player.pos.z) > 280;
            if (far) rit.remove();
        }
    }

    private void recallAll(Game g) {
        for (NPC o : officers) {
            if (!o.dead) {
                if (o.vehicle != null) {
                    o.pullOutOfVehicle();
                }
                o.delete = true; // sai de cena
            }
        }
        officers.clear();
        for (Vehicle v : cars) {
            v.sirenOn = false;
            if (v.driver == null) v.mission = true; // permite despawn
        }
        cars.clear();
        for (Vehicle v : roadblocks) v.sirenOn = false;
        roadblocks.clear();
        heliActive = false;
    }

    public void clearWanted(boolean full) {
        wantedSystem.clear();
        arrestProgress = 0;
    }

    // ---------------- helicóptero ----------------

    private void spawnHeli(Game g) {
        heliActive = true;
        heliX = g.player.pos.x - 60;
        heliZ = g.player.pos.z - 60;
        heliY = 34;
        g.hud.notify("Helicóptero da PM a caminho!");
    }

    private void updateHeli(Game g, float dt) {
        ohkt.player.Player p = g.player;
        float orbit = g.world.time.worldTime * 0.25f;
        float tx = p.pos.x + (float) Math.cos(orbit) * 26;
        float tz = p.pos.z + (float) Math.sin(orbit) * 26;
        float ty = 30 + (float) Math.sin(orbit * 2.3f) * 3;
        heliX = MathX.approach(heliX, tx, dt * 12);
        heliY = MathX.approach(heliY, ty, dt * 6);
        heliZ = MathX.approach(heliZ, tz, dt * 12);
        float dx = p.pos.x - heliX, dz = p.pos.z - heliZ;
        heliYaw = (float) Math.atan2(dx, -dz);
        heliRotor += dt * 30;

        // metralhadora a 5 estrelas
        if (wantedSystem.stars >= 5) {
            heliShootTimer -= dt;
            float dist = Vec3.len(p.pos.x - heliX, p.pos.y - heliY, p.pos.z - heliZ);
            if (heliShootTimer <= 0 && dist < 60) {
                heliShootTimer = 0.22f;
                float mx = heliX, my = heliY - 1, mz = heliZ;
                float dirx = p.pos.x - mx + rnd.nextFloat() * 3 - 1.5f;
                float diry = p.pos.y + 1 - my;
                float dirz = p.pos.z - mz + rnd.nextFloat() * 3 - 1.5f;
                float l = Vec3.len(dirx, diry, dirz);
                g.particles.muzzleFlash(mx + dirx / l, my, mz + dirz / l, dirx / l, diry / l, dirz / l);
                g.audio.play("SHOT_VESPA", mx, my, mz, 0.5f, 0.9f);
                // 35% de acertar
                if (rnd.nextFloat() < 0.35f) {
                    p.takeDamage(g, 6, "helicóptero");
                }
                g.combat.raycastShot(g, mx, my, mz, dirx / l, diry / l, dirz / l, 70f, 5, false, 4);
            }
        }
        // sirene áudio
        if (sirenAudioTimer <= 0) {
            sirenAudioTimer = 0.5f;
            g.audio.play("HELI", heliX, heliY, heliZ, 0.6f, 1f);
        }
    }

    private void updateSirenAudio(Game g, float dt) {
        sirenAudioTimer -= dt;
        if (sirenAudioTimer > 0) return;
        sirenAudioTimer = 0.42f;
        // sirene da viatura mais próxima
        Vehicle best = null;
        float bestD = 90;
        for (Vehicle v : cars) {
            if (!v.sirenOn || v.destroyed) continue;
            float d = v.pos.dst(g.player.pos);
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        if (best != null) {
            g.audio.play("SIREN", best.pos.x, best.pos.y, best.pos.z, 0.55f, 1f);
        }
    }

    // ---------------- eventos ----------------

    public void onEvent(Game g, EventBus.Event e) {
        switch (e.type) {
            case GUNSHOT: {
                float x = e.get(0), z = e.get(2);
                float d = Vec3.len(g.player.pos.x - x, 0, g.player.pos.z - z);
                if (d < 2) {
                    wantedSystem.crime(g.world.time.worldTime, "TIRO", anyWitness(g, x, z));
                    wantedSystem.post(g.bus);
                }
                break;
            }
            case PED_KILLED: {
                NPC n = e.get(0);
                if (nearPlayerAction(g, n.pos.x, n.pos.z)) {
                    wantedSystem.crime(g.world.time.worldTime, "PED_KILLED", anyWitness(g, n.pos.x, n.pos.z));
                    wantedSystem.post(g.bus);
                }
                break;
            }
            case COP_KILLED: {
                NPC n = e.get(0);
                if (nearPlayerAction(g, n.pos.x, n.pos.z)) {
                    wantedSystem.crime(g.world.time.worldTime, "COP_KILLED", true);
                    wantedSystem.post(g.bus);
                }
                break;
            }
            case CRIME: {
                String type = e.get(0);
                Boolean witnessed = e.get(1);
                wantedSystem.crime(g.world.time.worldTime, type, witnessed != null && witnessed);
                wantedSystem.post(g.bus);
                break;
            }
            case NPC_CALLS_POLICE: {
                if (wantedSystem.stars == 0 && !wantedSystem.suppressed) {
                    wantedSystem.crime(g.world.time.worldTime, "DENUNCIA", true);
                    wantedSystem.post(g.bus);
                    g.hud.notify("Alguém denunciou você à polícia!");
                }
                break;
            }
            default:
                break;
        }
    }

    private boolean nearPlayerAction(Game g, float x, float z) {
        return Vec3.len(g.player.pos.x - x, 0, g.player.pos.z - z) < 60;
    }

    private boolean anyWitness(Game g, float x, float z) {
        for (NPC n : g.npcs.list()) {
            if (n.dead || n.type == NPC.Type.COP) continue;
            if (n.pos.dst(x, n.pos.y, z) < 35) return true;
        }
        return g.police != null && wantedSystem.stars > 0;
    }

    // ---------------- render ----------------

    public void render(Game g, Renderer3D r) {
        if (!heliActive) return;
        if (!r.sphereVisible(heliX, heliY, heliZ, 12)) return;
        float night = g.world.time.nightFactor();
        // corpo
        r.drawBox(heliX, heliY, heliZ, 1.1f, 1.2f, 2.6f, heliYaw, 0.12f, 0, 0xff2a3a5a, false);
        r.drawBox(heliX, heliY - 0.4f, heliZ, 0.7f, 0.5f, 0.7f, heliYaw, 0, 0, 0xff88c8e8, false);
        // cauda
        float fx = (float) Math.sin(heliYaw), fz = (float) -Math.cos(heliYaw);
        r.drawBox(heliX - fx * 3.4f, heliY + 0.3f, heliZ - fz * 3.4f, 0.25f, 0.4f, 1.4f, heliYaw, 0, 0, 0xff2a3a5a, false);
        // rotor
        float rotorAng = heliRotor;
        for (int i = 0; i < 2; i++) {
            float a = rotorAng + i * (float) Math.PI / 2;
            r.drawBox(heliX + (float) Math.cos(a) * 3, heliY + 1.5f, heliZ + (float) Math.sin(a) * 3, 3f, 0.06f, 0.2f, a, 0, 0, 0xff303034, false);
        }
        // holofote à noite
        if (night > 0.3f) {
            r.drawSprite(heliX, heliY - 1, heliZ, 1.4f, ColorUtil.rgba(255, 255, 220, 150), 2);
            ohkt.player.Player p = g.player;
            float t = Math.min(1, night);
            r.drawGroundDisk(p.pos.x, p.pos.z, 4.5f, ohkt.world.World.groundHeight(p.pos.x, p.pos.z) + 0.05f,
                    ColorUtil.rgba(255, 255, 210, (int) (50 * t)), 2);
        }
        // luz piscante
        boolean flip = ((int) (g.world.time.worldTime * 3)) % 2 == 0;
        r.drawSprite(heliX, heliY + 0.6f, heliZ, 1.2f, ColorUtil.rgba(flip ? 255 : 40, 40, flip ? 40 : 255, 120), 2);
    }

    public boolean heliActive() { return heliActive; }

    public int officerCount() { return officers.size(); }
}
