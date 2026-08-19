package ohkt.mission;

import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.graphics.Renderer3D;
import ohkt.npc.NPC;
import ohkt.utils.ColorUtil;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gerenciador de missões: progressão da campanha, objetivos, marcadores,
 * falha/conclusão e spawn de entidades de missão (inimigos, aliados, veículos).
 */
public final class MissionManager {

    public final List<Mission> campaign = new ArrayList<>();
    public int campaignIdx;
    public final List<String> completed = new ArrayList<>();
    public Mission current;
    public final CutscenePlayer cutscene = new CutscenePlayer();
    public final Random rnd = new Random(717);

    // corrida
    public final List<Vehicle> raceOpponents = new ArrayList<>();
    public int raceCheckpoint;
    public float raceTimer;
    public boolean raceActive;

    /** Spawn de oponentes de corrida ao redor do primeiro checkpoint. */
    public void spawnRaceOpponents(Game g, List<float[]> checkpoints) {
        float[] start = checkpoints.get(0);
        float[] next = checkpoints.get(1);
        float yaw = (float) Math.atan2(next[0] - start[0], -(next[1] - start[1]));
        for (int i = 0; i < 3; i++) {
            float off = (i - 1) * 3f;
            Vehicle v = g.vehicles.spawn(VehicleType.byKindHint("SPORTS"),
                    start[0] + off, start[1] - 4 - Math.abs(off), yaw, 0xff208040 + i * 0x1000 * 32);
            v.mission = true;
            NPC d = g.npcs.spawn(NPC.Type.MISSION_DRIVER, v.pos.x, v.pos.z);
            d.vehicle = v;
            v.driver = d;
            d.brain = NPC.Brain.WANDER;
            d.raceCheckpoint = 1;
            d.raceTargetX = checkpoints.get(1)[0];
            d.raceTargetZ = checkpoints.get(1)[1];
            raceOpponents.add(v);
        }
        raceActive = true;
    }

    public void init(Game g) {
        campaign.addAll(Campaign.build(g));
        // retomar progresso
        campaignIdx = 0;
        while (campaignIdx < campaign.size() && completed.contains(campaign.get(campaignIdx).id)) {
            campaignIdx++;
        }
    }

    public Mission nextMission() {
        if (campaignIdx < campaign.size()) return campaign.get(campaignIdx);
        return null;
    }

    public void start(Game g, Mission m) {
        if (current != null) fail(g, "outra missão iniciada");
        current = m;
        m.active = true;
        m.idx = 0;
        m.failed = false;
        m.done = false;
        m.raceCheckpoint = 0;
        m.raceTimer = 0;
        if (m.onEnter != null) m.onEnter.accept(g);
        g.bus.post(EventBus.Type.MISSION_STARTED, m);
        g.hud.notify("Missão iniciada: " + m.name);
        g.audio.play("MISSION_START", g.player.pos.x, g.player.pos.y, g.player.pos.z, 0.6f, 1f);
        if (m.introDialog != null && m.introDialog.length > 0) {
            float px = g.player.pos.x, pz = g.player.pos.z;
            cutscene.start(g, m.introDialog,
                    px - 6, 4, pz - 6, px + 4, 2.5f, pz + 3, px, 1.5f, pz,
                    () -> g.police.wantedSystem.suppressed = false);
        }
    }

    public void update(Game g, float dt) {
        cutscene.update(g, dt);
        if (current == null || cutscene.isActive()) return;
        Mission m = current;
        if (m.failed || m.allComplete()) return;

        Objective o = m.currentObjective();
        if (o == null) {
            complete(g);
            return;
        }
        boolean ok = false;

        switch (o.type) {
            case GOTO:
                ok = dist2(g, o.x, o.z) < o.r * o.r;
                break;
            case DRIVE_TO:
            case DELIVER:
                ok = g.player.state == ohkt.player.Player.State.DRIVING && dist2(g, o.x, o.z) < o.r * o.r;
                if (!ok && o.time > 0) {
                    o.time -= dt;
                    if (o.time <= 0) fail(g, "tempo esgotado");
                }
                break;
            case ENTER_VEHICLE: {
                Vehicle v = g.player.vehicle;
                if (v != null) {
                    ok = o.tag.equals("*") || o.tag.equals(missionTagOf(v));
                }
                break;
            }
            case KILL_TAG: {
                int dead = countDeadTag(g, o.tag);
                ok = dead >= o.count;
                break;
            }
            case DESTROY_VEHICLE_TAG: {
                Vehicle v = findVehicleTag(g, o.tag);
                ok = v != null && v.destroyed;
                break;
            }
            case ESCAPE_POLICE:
                ok = g.police.wantedSystem.stars == 0;
                break;
            case SURVIVE_TIME:
                o.time -= dt;
                ok = o.time <= 0;
                break;
            case PROTECT_ALLY: {
                NPC ally = findTag(g, o.tag);
                if (ally == null || ally.dead) {
                    fail(g, "o informante morreu");
                    return;
                }
                ally.missionPersistent = true;
                o.time -= dt;
                ok = o.time <= 0;
                break;
            }
            case RACE:
                ok = updateRace(g, o, dt);
                break;
            case CUTSCENE:
                ok = true;
                break;
            default:
                ok = true;
        }

        if (ok) {
            m.idx++;
            g.audio.play("OBJECTIVE_OK", g.player.pos.x, 1, g.player.pos.z, 0.5f, 1f);
            g.bus.post(EventBus.Type.OBJECTIVE_CHANGED);
            if (m.allComplete()) complete(g);
        }

        // falha padrão
        if (g.player.state == ohkt.player.Player.State.BUSTED && !m.failed) {
            fail(g, "você foi preso");
        }
        // proteção: aliado morrendo fora do objetivo atual
        if (m.id.equals("m7")) {
            NPC ally = findTag(g, "informante");
            if (ally != null && ally.dead && !m.failed) {
                fail(g, "o informante morreu");
            }
        }
    }

    private boolean updateRace(Game g, Objective o, float dt) {
        m: {
            raceTimer += dt;
            Vehicle v = g.player.vehicle;
            if (v == null) break m;
            float[] cp = o.checkpoints.get(raceCheckpoint);
            float dx = v.pos.x - cp[0], dz = v.pos.z - cp[1];
            if (dx * dx + dz * dz < o.checkpointRadius * o.checkpointRadius) {
                raceCheckpoint++;
                g.audio.play("CHECKPOINT", cp[0], 1, cp[1], 0.55f, 1.2f);
                if (raceCheckpoint >= o.checkpoints.size()) {
                    clearRace(g);
                    return true;
                }
                o.x = o.checkpoints.get(raceCheckpoint)[0];
                o.z = o.checkpoints.get(raceCheckpoint)[1];
            }
            // oponentes
            for (Vehicle ov : raceOpponents) {
                if (ov.destroyed) continue;
                NPC d = (NPC) ov.driver;
                if (d == null) continue;
                d.driveTowardsPublic(g, ov, d.raceTargetX, d.raceTargetZ, 16f, dt);
                float ddx = ov.pos.x - d.raceTargetX, ddz = ov.pos.z - d.raceTargetZ;
                if (ddx * ddx + ddz * ddz < 36f) {
                    d.raceCheckpoint++;
                    if (d.raceCheckpoint >= o.checkpoints.size()) {
                        fail(g, "perdeu a corrida");
                        return false;
                    }
                    float[] next = o.checkpoints.get(d.raceCheckpoint);
                    d.raceTargetX = next[0];
                    d.raceTargetZ = next[1];
                }
            }
            return false;
        }
        return false;
    }

    private float dist2(Game g, float x, float z) {
        float dx = g.player.pos.x - x, dz = g.player.pos.z - z;
        return dx * dx + dz * dz;
    }

    public void complete(Game g) {
        Mission m = current;
        if (m == null || m.done) return;
        m.done = true;
        m.active = false;
        g.economy.earn(m.reward, "missão " + m.name);
        if (m.onComplete != null) m.onComplete.accept(g);
        if (m.onExit != null) m.onExit.accept(g);
        if (!completed.contains(m.id)) completed.add(m.id);
        campaignIdx = Math.max(campaignIdx, campaign.indexOf(m) + 1);
        current = null;
        clearRace(g);
        g.bus.post(EventBus.Type.MISSION_COMPLETED, m);
        g.hud.notify("Missão concluída! +" + "R$" + m.reward);
        g.audio.play("MISSION_OK", g.player.pos.x, 1, g.player.pos.z, 0.7f, 1f);
        g.saveSystem.autosave(g, "progresso");
        // helicóptero/polícia fora das missões
        g.police.clearWanted(true);
    }

    public void fail(Game g, String reason) {
        Mission m = current;
        if (m == null || m.failed) return;
        m.failed = true;
        m.active = false;
        if (m.onExit != null) m.onExit.accept(g);
        current = null;
        clearRace(g);
        g.bus.post(EventBus.Type.MISSION_FAILED, m);
        g.hud.notify("Missão falhou: " + reason);
        g.audio.play("MISSION_FAIL", g.player.pos.x, 1, g.player.pos.z, 0.7f, 0.8f);
    }

    /** Repetir a última missão (após falha). */
    public void retry(Game g) {
        Mission next = nextMission();
        if (next != null && current == null) {
            start(g, next);
        }
    }

    // ---------------- spawn de entidades de missão ----------------

    public void spawnGang(Game g, String tag, float x, float z, int count, float radius, boolean around) {
        for (int i = 0; i < count; i++) {
            float a = (float) (Math.PI * 2 * i / count);
            float r = around ? radius * (0.3f + rnd.nextFloat() * 0.7f) : rnd.nextFloat() * radius;
            NPC n = g.npcs.spawn(NPC.Type.GANG, x + (float) Math.cos(a) * r, z + (float) Math.sin(a) * r);
            n.missionTag = tag;
            n.missionPersistent = true;
            n.brain = NPC.Brain.WANDER;
            n.health = 70;
        }
    }

    public void spawnBoss(Game g, String tag, float x, float z) {
        NPC n = g.npcs.spawn(NPC.Type.GANG, x, z);
        n.missionTag = tag;
        n.missionPersistent = true;
        n.health = 320;
        n.look.shirt = 0xff101014;
        n.look.scale = 1.15f;
    }

    public void spawnAlly(Game g, String tag, float x, float z) {
        NPC n = g.npcs.spawn(NPC.Type.MISSION_TARGET, x, z);
        n.missionTag = tag;
        n.missionPersistent = true;
        n.brain = NPC.Brain.WAIT;
        n.timer = 3;
        n.health = 140;
    }

    public Vehicle spawnMissionVehicle(Game g, String tag, String typeId, float x, float z, float yaw) {
        Vehicle v = g.vehicles.spawn(VehicleType.byId(typeId), x, z, yaw, 0xff3850a0);
        v.mission = true;
        v.missionTag = tag;
        return v;
    }

    public void clearTag(Game g, String tag) {
        for (NPC n : new ArrayList<>(g.npcs.list())) {
            if (tag.equals(n.missionTag)) {
                n.missionPersistent = false;
                if (!n.dead) n.delete = true;
            }
        }
        // aliado do m7 segue no carro
        if (tag.equals("informante")) {
            NPC ally = findTag(g, "informante");
            if (ally != null && !ally.dead) ally.delete = true;
        }
    }

    public void clearVehicleTag(Game g, String tag) {
        for (Vehicle v : g.vehicles.list()) {
            if (tag.equals(v.missionTag)) v.mission = false;
        }
    }

    public int countDeadTag(Game g, String tag) {
        int c = 0;
        for (NPC n : g.npcs.list()) {
            if (tag.equals(n.missionTag) && n.dead) c++;
        }
        // npcs removidos contam como mortos para missões antigas
        c += countCleared(g, tag);
        return c;
    }

    private final java.util.Map<String, Integer> cleared = new java.util.HashMap<>();

    private int countCleared(Game g, String tag) {
        return cleared.getOrDefault(tag, 0);
    }

    /** Registra contagem ao limpar tags. */
    public void noteCleared(String tag, int deadCount) {
        cleared.merge(tag, deadCount, Integer::sum);
    }

    public NPC findTag(Game g, String tag) {
        for (NPC n : g.npcs.list()) {
            if (tag.equals(n.missionTag) && !n.dead) return n;
        }
        return null;
    }

    public Vehicle findVehicleTag(Game g, String tag) {
        for (Vehicle v : g.vehicles.list()) {
            if (tag.equals(v.missionTag)) return v;
        }
        return null;
    }

    private String missionTagOf(Vehicle v) {
        return v.missionTag;
    }

    // ---------------- corrida ----------------

    public void clearRace(Game g) {
        for (Vehicle v : raceOpponents) {
            if (v.driver instanceof NPC) {
                ((NPC) v.driver).pullOutOfVehicle();
            }
            v.mission = true;
        }
        raceOpponents.clear();
        raceActive = false;
    }

    // ---------------- render ----------------

    public void render(Game g, Renderer3D r) {
        Mission m = current;
        if (m != null && !cutscene.isActive()) {
            Objective o = m.currentObjective();
            if (o != null) {
                float y = ohkt.world.World.groundHeight(o.x, o.z);
                r.drawBeacon(o.x, o.z, y, 9, 1.1f, 0xffffd020);
                if (o.type == Objective.Type.RACE && o.checkpoints != null) {
                    float[] cp = o.checkpoints.get(raceCheckpoint);
                    r.drawBeacon(cp[0], cp[1], 0, 7, 1.6f, 0xff30c0ff);
                }
            }
        }
        // aliado indicado
        NPC ally = findTag(g, "informante");
        if (ally != null && !ally.dead) {
            r.drawSprite(ally.pos.x, ally.pos.y + 2.6f, ally.pos.z, 0.5f, ColorUtil.rgba(80, 220, 255, 160), 2);
        }
    }
}
