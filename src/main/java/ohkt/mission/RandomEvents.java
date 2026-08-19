package ohkt.mission;

import ohkt.engine.Game;
import ohkt.npc.NPC;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;

import java.util.Random;

/** Eventos aleatórios pelo mapa: assaltos, carona, emboscadas, acidentes. */
public final class RandomEvents {

    private float timer = 45;
    private final Random rnd = new Random(999);

    public NPC mugger, victim;
    public NPC strandedDriver;
    public float caronaX, caronaZ;
    public boolean caronaActive;

    public void update(Game g, float dt) {
        if (mugger != null) {
            if (mugger.dead) {
                g.economy.earn(120, "bom cidadão");
                g.hud.notify("Você deteve o assaltante! +R$120");
                if (victim != null) victim.panic(mugger.pos.x, mugger.pos.z);
                mugger = null;
                victim = null;
            } else if (victim == null || victim.dead) {
                mugger = null;
            } else {
                mugger.brain = NPC.Brain.FIGHT;
            }
        }
        if (caronaActive) {
            float d = Vec3.len(g.player.pos.x - caronaX, 0, g.player.pos.z - caronaZ);
            if (d < 6 && g.player.state == ohkt.player.Player.State.DRIVING) {
                g.economy.earn(80, "carona");
                g.hud.notify("O motorista agradeceu a carona! +R$80");
                caronaActive = false;
                if (strandedDriver != null) strandedDriver.delete = true;
            } else if (d > 400) {
                caronaActive = false;
                if (strandedDriver != null) strandedDriver.delete = true;
            }
        }

        timer -= dt;
        if (timer > 0) return;
        timer = 70 + rnd.nextFloat() * 60;
        if (g.missions.current != null || g.sideActivities.busy()) return;

        float roll = rnd.nextFloat();
        if (roll < 0.45f) {
            startMugging(g);
        } else if (roll < 0.8f) {
            startCarona(g);
        } else {
            startGangAmbush(g);
        }
    }

    private void startMugging(Game g) {
        float[] p = g.world.sidewalkPointNear(g.player.pos.x + rnd.nextFloat() * 60 - 30,
                g.player.pos.z + rnd.nextFloat() * 60 - 30, rnd);
        mugger = g.npcs.spawn(NPC.Type.CRIMINAL, p[0] + 4, p[1]);
        victim = g.npcs.spawn(NPC.Type.PEDESTRIAN, p[0], p[1]);
        victim.panic(mugger.pos.x, mugger.pos.z);
        mugger.brain = NPC.Brain.FIGHT;
        g.hud.notify("Um assalto está acontecendo perto de você!");
    }

    private void startCarona(Game g) {
        int[] node = g.world.roadGraph.randomNodeNear(rnd, g.player.pos.x, g.player.pos.z, 1, 2);
        float x = ohkt.world.CityLayout.roadCoord(node[0]) + 6;
        float z = ohkt.world.CityLayout.roadCoord(node[1]);
        strandedDriver = g.npcs.spawn(NPC.Type.PEDESTRIAN, x, z);
        strandedDriver.brain = NPC.Brain.WAIT;
        strandedDriver.timer = 120;
        strandedDriver.missionPersistent = true;
        caronaX = x;
        caronaZ = z;
        caronaActive = true;
        g.hud.notify("Um motorista ficou sem combustível — dê uma carona!");
    }

    private void startGangAmbush(Game g) {
        float[] p = g.world.sidewalkPointNear(g.player.pos.x + rnd.nextFloat() * 40 - 20,
                g.player.pos.z + rnd.nextFloat() * 40 - 20, rnd);
        for (int i = 0; i < 2; i++) {
            NPC n = g.npcs.spawn(NPC.Type.GANG, p[0] + i * 3, p[1] + i * 2);
            n.brain = NPC.Brain.FIGHT;
        }
        g.hud.notify("Corvos te emboscaram!");
    }

    public float[] marker() {
        if (caronaActive) return new float[]{caronaX, caronaZ};
        return null;
    }

    public void render(Game g, ohkt.graphics.Renderer3D r) {
        if (caronaActive) {
            r.drawBeacon(caronaX, caronaZ, 0, 6, 0.8f, 0xffe08030);
        }
        if (mugger != null && !mugger.dead) {
            r.drawBeacon(mugger.pos.x, mugger.pos.z, 0, 5, 0.7f, 0xffff3030);
        }
    }
}
