package ohkt.mission;

import ohkt.engine.Game;
import ohkt.npc.NPC;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;
import ohkt.world.CityLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Atividades secundárias: táxi, entregas, recompensas e caçada (rampage).
 * Corridas avulsas usam as pistas da campanha via marcador no porto.
 */
public final class SideActivities {

    public enum Job { NONE, TAXI, DELIVERY, BOUNTY, RAMPAGE }

    public Job job = Job.NONE;
    public int jobStage;              // 0=ir ao ponto, 1=entrega/alvo
    public float jobTimer;
    public int jobCount;
    public float jobX, jobZ;
    public String jobLabel = "";
    public final Random rnd = new Random(88);

    private NPC bountyTarget;
    private NPC taxiPassenger;

    public boolean busy() { return job != Job.NONE; }

    public void update(Game g, float dt) {
        if (job == Job.NONE) {
            tryStartFromContext(g);
            return;
        }
        switch (job) {
            case TAXI: updateTaxi(g, dt); break;
            case DELIVERY: updateDelivery(g, dt); break;
            case BOUNTY: updateBounty(g, dt); break;
            case RAMPAGE: updateRampage(g, dt); break;
            default: break;
        }
    }

    /** Início contextual: entrar num táxi (E), marcador da pizzaria, etc. */
    private void tryStartFromContext(Game g) {
        Vehicle v = g.player.vehicle;
        if (v != null && v.type.kind.equals("TAXI") && g.input.justPressed(ohkt.engine.Settings.Action.INTERACT)
                && g.missions.current == null) {
            startTaxi(g);
        }
    }

    // ---------------- táxi ----------------

    private void startTaxi(Game g) {
        job = Job.TAXI;
        jobStage = 0;
        jobCount = 0;
        g.hud.notify("Turno de táxi iniciado! Busque o passageiro.");
        newTaxiFare(g);
    }

    private void newTaxiFare(Game g) {
        float[] p = g.world.sidewalkPointNear(g.player.pos.x + rnd.nextFloat() * 120 - 60,
                g.player.pos.z + rnd.nextFloat() * 120 - 60, rnd);
        jobX = p[0];
        jobZ = p[1];
        jobLabel = "Busque o passageiro";
        taxiPassenger = g.npcs.spawn(NPC.Type.PEDESTRIAN, p[0], p[1]);
        taxiPassenger.brain = NPC.Brain.WAIT;
        taxiPassenger.timer = 999;
        taxiPassenger.missionPersistent = true;
        jobStage = 0;
    }

    private void updateTaxi(Game g, float dt) {
        Vehicle v = g.player.vehicle;
        if (v == null || !v.type.kind.equals("TAXI")) {
            end(g, "Turno encerrado", 0);
            return;
        }
        if (jobStage == 0) {
            float d = Vec3.len(v.pos.x - jobX, 0, v.pos.z - jobZ);
            g.hud.setJobPrompt(jobLabel + String.format(" (%dm)", (int) d));
            if (d < 6 && Math.abs(v.forwardSpeed()) < 1.5f) {
                if (taxiPassenger != null && !taxiPassenger.dead) {
                    taxiPassenger.delete = true;
                }
                float[] p = g.world.sidewalkPointNear(g.player.pos.x + rnd.nextFloat() * 300 - 150,
                        g.player.pos.z + rnd.nextFloat() * 300 - 150, rnd);
                jobX = p[0];
                jobZ = p[1];
                jobLabel = "Leve o passageiro ao destino";
                jobStage = 1;
                g.hud.notify("Passageiro a bordo!");
            }
        } else {
            float d = Vec3.len(v.pos.x - jobX, 0, v.pos.z - jobZ);
            g.hud.setJobPrompt(jobLabel + String.format(" (%dm)", (int) d));
            if (d < 7 && Math.abs(v.forwardSpeed()) < 1.5f) {
                int fare = 40 + rnd.nextInt(80);
                g.economy.earn(fare, "táxi");
                jobCount++;
                g.hud.notify("Corrida concluída! R$" + fare);
                g.audio.play("CASH", v.pos.x, 1, v.pos.z, 0.5f, 1f);
                if (jobCount >= 5) {
                    end(g, "Turno de táxi concluído (" + jobCount + " corridas)", 150);
                } else {
                    newTaxiFare(g);
                }
            }
        }
    }

    // ---------------- entregas ----------------

    public void startDelivery(Game g) {
        job = Job.DELIVERY;
        jobStage = 0;
        jobCount = 0;
        jobTimer = 150;
        float[] lanche = CityLayout.specialPos(CityLayout.Special.LANCHONETE);
        jobX = lanche[0];
        jobZ = lanche[1];
        jobLabel = "Pegue os pacotes no Forno de Ouro";
        g.hud.notify("Entregas do Forno de Ouro: 5 pacotes em 2:30!");
    }

    private void updateDelivery(Game g, float dt) {
        jobTimer -= dt;
        if (jobTimer <= 0) {
            end(g, "Tempo esgotado nas entregas", 0);
            return;
        }
        float d = Vec3.len(g.player.pos.x - jobX, 0, g.player.pos.z - jobZ);
        g.hud.setJobPrompt(jobLabel + String.format(" (%dm) %02d:%02d", (int) d, (int) jobTimer / 60, (int) jobTimer % 60));
        if (d < 5) {
            jobCount++;
            g.audio.play("CHECKPOINT", jobX, 1, jobZ, 0.5f, 1.1f);
            if (jobCount >= 5) {
                end(g, "Entregas concluídas!", 300);
            } else {
                float[] p = g.world.sidewalkPointNear(g.player.pos.x + rnd.nextFloat() * 200 - 100,
                        g.player.pos.z + rnd.nextFloat() * 200 - 100, rnd);
                jobX = p[0];
                jobZ = p[1];
                jobLabel = "Entregue o pacote " + (jobCount + 1) + "/5";
            }
        }
    }

    // ---------------- recompensa (bounty) ----------------

    public void startBounty(Game g) {
        job = Job.BOUNTY;
        jobStage = 0;
        float[] porto = CityLayout.specialPos(CityLayout.Special.POSTO_D);
        float x = porto[0] + rnd.nextFloat() * 60 - 30;
        float z = porto[1] + rnd.nextFloat() * 60 - 30;
        jobX = x;
        jobZ = z;
        jobLabel = "Encontre o procurado";
        bountyTarget = g.npcs.spawn(NPC.Type.GANG, x, z);
        bountyTarget.health = 160;
        bountyTarget.missionPersistent = true;
        bountyTarget.brain = NPC.Brain.WANDER;
        g.hud.notify("Recompensa: elimine o fugitivo dos Corvos!");
    }

    private void updateBounty(Game g, float dt) {
        if (bountyTarget == null || bountyTarget.dead) {
            end(g, "Recompensa coletada!", 500);
            return;
        }
        bountyTarget.missionPersistent = true;
        float d = Vec3.len(g.player.pos.x - bountyTarget.pos.x, 0, g.player.pos.z - bountyTarget.pos.z);
        g.hud.setJobPrompt("Procurado" + String.format(" (%dm)", (int) d));
        if (d < 15 && bountyTarget.brain != NPC.Brain.FIGHT) {
            bountyTarget.brain = NPC.Brain.FIGHT; // reage ao ser encurralado
        }
    }

    // ---------------- caçada (rampage) ----------------

    public void startRampage(Game g) {
        job = Job.RAMPAGE;
        jobTimer = 90;
        jobCount = 0;
        jobLabel = "Caçada: elimine Corvos";
        float[] porto = CityLayout.specialPos(CityLayout.Special.GALPAO_PORTO);
        for (int i = 0; i < 8; i++) {
            float a = (float) (Math.PI * 2 * i / 8);
            NPC n = g.npcs.spawn(NPC.Type.GANG, porto[0] + (float) Math.cos(a) * 20, porto[1] + (float) Math.sin(a) * 20);
            n.missionPersistent = true;
        }
        g.hud.notify("Caçada: elimine 8 Corvos em 90s!");
    }

    private void updateRampage(Game g, float dt) {
        jobTimer -= dt;
        if (jobTimer <= 0) {
            end(g, "A caçada acabou (" + jobCount + "/8)", jobCount >= 8 ? 400 : 0);
            return;
        }
        int alive = 0;
        for (NPC n : g.npcs.list()) {
            if (n.type == NPC.Type.GANG && n.missionPersistent && !n.dead) alive++;
        }
        jobCount = 8 - alive;
        g.hud.setJobPrompt(String.format("Caçada: %d/8 — %02ds", jobCount, (int) jobTimer));
        if (alive == 0) {
            end(g, "Caçada perfeita!", 800);
        }
    }

    // ---------------- fim ----------------

    private void end(Game g, String msg, int bonus) {
        if (bonus > 0) g.economy.earn(bonus, "atividade");
        g.hud.notify(msg + (bonus > 0 ? " +R$" + bonus : ""));
        if (taxiPassenger != null) {
            taxiPassenger.delete = true;
            taxiPassenger = null;
        }
        if (bountyTarget != null) {
            if (!bountyTarget.dead) bountyTarget.delete = true;
            bountyTarget = null;
        }
        for (NPC n : new ArrayList<>(g.npcs.list())) {
            if (n.missionPersistent && n.type == NPC.Type.GANG && g.missions.current == null) {
                n.missionPersistent = false;
                n.delete = !n.dead;
            }
        }
        job = Job.NONE;
        g.hud.setJobPrompt(null);
    }

    /** Marcador do trabalho atual para o minimapa. */
    public float[] marker() {
        if (job == Job.NONE) return null;
        return new float[]{jobX, jobZ};
    }

    public void render(Game g, ohkt.graphics.Renderer3D r) {
        if (job == Job.NONE) return;
        r.drawBeacon(jobX, jobZ, ohkt.world.World.groundHeight(jobX, jobZ), 7, 0.9f, 0xff40e0a0);
    }
}
