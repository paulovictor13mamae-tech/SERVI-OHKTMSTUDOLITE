package ohkt.npc;

import ohkt.combat.CombatSystem;
import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.graphics.Renderer3D;
import ohkt.player.HumanoidRenderer;
import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;
import ohkt.world.CityLayout;

import java.util.Random;

/**
 * NPC da cidade: pedestres, motoristas, criminosos, aliados, alvos de missão
 * e policiais (a pé). IA por estados: rotina, fuga, denúncia, briga, perseguição.
 */
public final class NPC {

    public enum Type { PEDESTRIAN, CRIMINAL, GANG, ALLY, MISSION_TARGET, COP, MERCHANT, MISSION_DRIVER }

    public enum Brain {
        WANDER, CROSS, TALK, FLEE, REPORT, FIGHT, CHASE_PLAYER, FOLLOW_PLAYER, DRIVE, PANIC_DRIVE, WAIT, DEAD
    }

    public final Type type;
    public Brain brain = Brain.WANDER;
    public final Vec3 pos = new Vec3();
    public float yaw;
    public float health = 65;
    public boolean dead;
    public float deadTimer;
    public boolean delete;

    public final HumanoidRenderer.Look look = new HumanoidRenderer.Look();
    public float phase;
    public float speed = 1.8f;

    public float timer;
    public float fear;
    public float talkLineTimer;
    public NPC talkPartner;
    public Vec3 target = new Vec3();

    // condução
    public Vehicle vehicle;
    public int[] nodeA, nodeB;      // nós de navegação atuais
    public float cruiseSpeed = 9f;
    public boolean ranRed;

    // policial / luta
    public float shootTimer;
    public float arrestTimer;
    public float meleeTimer;

    // corridas
    public float raceTargetX, raceTargetZ;
    public int raceCheckpoint;

    // despawn/missão
    public boolean missionPersistent;
    public String missionTag;
    public float lodAcc; // acumulador para atualização com LOD

    private static final String[] CHAT = {
            "Dizem que o farol acende sozinho...",
            "O preço do pão subiu de novo.",
            "Cuidado com os Corvos no cais.",
            "Achu que vou pegar o próximo ônibus.",
            "Esse calor não tá normal.",
            "Ouviste os tiros ontem à noite?",
            "O ferry para a ilha ainda funciona?",
    };
    private static final String[] PANIC = {
            "Socorro!", "Tiros!", "Corre!", "Ele tem uma arma!", "Chama a polícia!",
    };
    private static final Random RND = new Random(31337);

    public NPC(Type type, float x, float z) {
        this.type = type;
        this.pos.set(x, 0, z);
        this.target.set(x, 0, z);
        randomizeLook();
        switch (type) {
            case COP: health = 90; speed = 5.4f; break;
            case CRIMINAL: health = 55; speed = 4.8f; break;
            case GANG: health = 70; speed = 5.0f; break;
            case ALLY: health = 120; speed = 5.2f; break;
            default: health = 60; speed = 1.7f + RND.nextFloat() * 0.7f;
        }
    }

    private void randomizeLook() {
        int[] skins = {0xffc8a080, 0xffa87858, 0xff8a5c40, 0xffe0b898, 0xff6a4832};
        int[] shirts = {0xffd04040, 0xff4060d0, 0xff40b060, 0xffe0c040, 0xffd0d0d0, 0xff8040a0, 0xffe88040, 0xff40c0c0};
        int[] pants = {0xff3a3a44, 0xff5a4a3a, 0xff2a3a5a, 0xff4a4a3a};
        int[] hairs = {0xff2a2018, 0xff181410, 0xff4a3828, 0xff101010, 0xff6a5a3a};
        look.set(skins[RND.nextInt(skins.length)], shirts[RND.nextInt(shirts.length)],
                pants[RND.nextInt(pants.length)], 0xff30241c, hairs[RND.nextInt(hairs.length)]);
        if (type == Type.COP) {
            look.shirt = 0xff2a4a8a;
            look.pants = 0xff1c2840;
        }
        if (type == Type.GANG) {
            look.shirt = 0xff1c1c22;
        }
        if (type == Type.MISSION_DRIVER) {
            look.shirt = 0xffc8a020;
        }
    }

    // ---------------- dano ----------------

    public void takeDamage(float dmg) {
        if (dead) return;
        health -= dmg;
        if (health <= 0) {
            dead = true;
            brain = Brain.DEAD;
        } else if (type == Type.PEDESTRIAN || type == Type.CRIMINAL) {
            panic(pos.x, pos.z);
        }
    }

    public void knockback(float vx, float vz) {
        if (vehicle != null) return;
        pos.x += vx * 0.08f;
        pos.z += vz * 0.08f;
    }

    public void panic(float fromX, float fromZ) {
        if (dead) return;
        if (type == Type.COP || type == Type.GANG) return;
        brain = Brain.FLEE;
        fear = 1;
        timer = 5 + RND.nextFloat() * 5;
        float dx = pos.x - fromX, dz = pos.z - fromZ;
        float l = Math.max(0.01f, Vec3.len(dx, 0, dz));
        target.set(pos.x + dx / l * 50, 0, pos.z + dz / l * 50);
        talkLineTimer = 2.5f;
    }

    public void pullOutOfVehicle() {
        if (vehicle != null) {
            vehicle.driver = null;
            vehicle.driverInput.clear();
            Vehicle v = vehicle;
            vehicle = null;
            pos.set(v.pos.x + 1.5f, 0, v.pos.z);
            nodeA = null;
            nodeB = null;
        }
    }

    // ---------------- update ----------------

    public void update(Game g, float dt) {
        if (dead) {
            deadTimer += dt;
            if (deadTimer > 30) delete = true;
            return;
        }
        if (talkLineTimer > 0) talkLineTimer -= dt;

        if (vehicle != null) {
            updateDriving(g, dt);
            return;
        }

        switch (brain) {
            case WANDER: wander(g, dt); break;
            case TALK: talk(dt); break;
            case FLEE: flee(g, dt); break;
            case REPORT: report(g, dt); break;
            case FIGHT: fight(g, dt); break;
            case CHASE_PLAYER: chasePlayer(g, dt); break;
            case FOLLOW_PLAYER: followPlayer(g, dt); break;
            case WAIT: timer -= dt; if (timer <= 0) brain = Brain.WANDER; break;
            default: break;
        }

        // gravidade/chão
        float gh = g == null ? 0 : ohkt.world.World.groundHeight(pos.x, pos.z);
        pos.y = MathX.approach(pos.y, gh, dt * 10f);
    }

    private void moveTowards(Game g, float tx, float tz, float speed, float dt) {
        float dx = tx - pos.x, dz = tz - pos.z;
        float d = Vec3.len(dx, 0, dz);
        if (d > 0.05f) {
            float nx = dx / d, nz = dz / d;
            pos.x += nx * speed * dt;
            pos.z += nz * speed * dt;
            float wantYaw = (float) Math.atan2(nx, -nz);
            yaw += MathX.angleDiff(yaw, wantYaw) * Math.min(1, dt * 10f);
            phase += speed * dt * 2.2f;
        }
        // colisão com prédios
        ohkt.physics.PhysicsWorld.Position pp = new ohkt.physics.PhysicsWorld.Position(pos.x, pos.z);
        g.world.physics.resolveCircle(pp, 0.35f, pos.y + 0.1f, pos.y + 1.6f);
        pos.x = pp.x;
        pos.z = pp.z;
    }

    private void wander(Game g, float dt) {
        float d = pos.dst(target.x, 0, target.z);
        if (d < 1.5f) {
            // novo destino: rotina por horário
            pickRoutineTarget(g);
            if (RND.nextFloat() < 0.1f) {
                brain = Brain.WAIT;
                timer = 1 + RND.nextFloat() * 3;
            }
        } else {
            moveTowards(g, target.x, target.z, speed, dt);
            // conversa casual
            if (RND.nextFloat() < dt * 0.02f) {
                for (NPC other : g.npcs.list()) {
                    if (other == this || other.dead || other.brain != Brain.WANDER) continue;
                    if (pos.dst(other.pos) < 1.6f) {
                        brain = Brain.TALK;
                        other.brain = Brain.TALK;
                        talkPartner = other;
                        other.talkPartner = this;
                        timer = 3 + RND.nextFloat() * 4;
                        other.timer = timer;
                        break;
                    }
                }
            }
            // criminosos provocam
            if ((type == Type.CRIMINAL || type == Type.GANG) && g.police.wantedSystem.stars == 0) {
                float pd = pos.dst(g.player.pos);
                if (pd < 3.5f && g.player.weapon().id != 0 && RND.nextFloat() < dt * 0.35f) {
                    brain = Brain.FIGHT;
                }
            }
        }
    }

    /** Rotinas diárias: manhã vai ao centro, tarde comercial, noite volta pra casa. */
    private void pickRoutineTarget(Game g) {
        float[] p = g.world.sidewalkPointNear(pos.x, pos.z, RND);
        float hour = g.world.time.hour;
        // viés de distrito por horário
        float biasX = 0, biasZ = 0;
        if (hour > 7 && hour < 10) {
            biasX = CityLayout.roadCoord(13) - pos.x;
            biasZ = CityLayout.roadCoord(13) - pos.z;
        } else if (hour > 17 && hour < 20) {
            biasX = CityLayout.roadCoord(6) - pos.x;
            biasZ = CityLayout.roadCoord(6) - pos.z;
        }
        target.set(p[0] + biasX * 0.06f, 0, p[1] + biasZ * 0.06f);
    }

    private void talk(float dt) {
        timer -= dt;
        if (talkPartner != null && !talkPartner.dead) {
            float wantYaw = (float) Math.atan2(talkPartner.pos.x - pos.x, -(talkPartner.pos.z - pos.z));
            yaw += MathX.angleDiff(yaw, wantYaw) * Math.min(1, dt * 6f);
        }
        if (timer <= 0) {
            if (talkPartner != null && talkPartner.talkPartner == this) {
                talkPartner.brain = Brain.WANDER;
                talkPartner.talkPartner = null;
            }
            talkPartner = null;
            brain = Brain.WANDER;
        }
    }

    private void flee(Game g, float dt) {
        timer -= dt;
        moveTowards(g, target.x, target.z, 5.2f, dt);
        if (timer <= 0) {
            brain = Brain.WANDER;
            fear = 0;
        }
    }

    private void report(Game g, float dt) {
        timer -= dt;
        // mão no ar, parado
        if (timer <= 0) {
            g.bus.post(EventBus.Type.NPC_CALLS_POLICE, this);
            panic(pos.x - 5, pos.z - 5);
        }
    }

    private void fight(Game g, float dt) {
        ohkt.player.Player p = g.player;
        if (p.state == ohkt.player.Player.State.DEAD || p.state == ohkt.player.Player.State.BUSTED) {
            brain = Brain.WANDER;
            return;
        }
        float d = pos.dst(p.pos);
        if (d > 24) {
            brain = Brain.WANDER;
            return;
        }
        if (d > 1.7f) {
            moveTowards(g, p.pos.x, p.pos.z, 4.6f, dt);
        } else {
            yaw += MathX.angleDiff(yaw, (float) Math.atan2(p.pos.x - pos.x, -(p.pos.z - pos.z))) * Math.min(1, dt * 8f);
            meleeTimer -= dt;
            if (meleeTimer <= 0) {
                meleeTimer = 0.9f;
                p.takeDamage(g, type == Type.GANG ? 10 : 7, "briga");
                g.audio.play("PUNCH", pos.x, 1, pos.z, 0.45f, 1f);
                g.camera.addShake(0.2f);
            }
        }
    }

    private void chasePlayer(Game g, float dt) {
        ohkt.player.Player p = g.player;
        if (g.police.wantedSystem.stars <= 0) {
            brain = Brain.WANDER;
            return;
        }
        float d = pos.dst(p.pos);
        boolean canShoot = g.police.wantedSystem.stars >= 3;
        if (d > 30) {
            moveTowards(g, p.pos.x, p.pos.z, speed, dt);
        } else if (d > 9 && !hasLineOfSight(g)) {
            moveTowards(g, p.pos.x, p.pos.z, speed, dt);
        } else if (d > 2.1f) {
            if (canShoot) {
                shootTimer -= dt;
                yaw += MathX.angleDiff(yaw, (float) Math.atan2(p.pos.x - pos.x, -(p.pos.z - pos.z))) * Math.min(1, dt * 8f);
                if (shootTimer <= 0 && hasLineOfSight(g)) {
                    shootTimer = 0.8f + RND.nextFloat() * 0.7f;
                    g.combat.npcShootAtPlayer(g, this, 0.45f + g.police.wantedSystem.stars * 0.08f, 8);
                }
            } else {
                moveTowards(g, p.pos.x, p.pos.z, speed, dt);
            }
        } else {
            // tentativa de prisão
            yaw += MathX.angleDiff(yaw, (float) Math.atan2(p.pos.x - pos.x, -(p.pos.z - pos.z))) * Math.min(1, dt * 10f);
            float pSpeed = Vec3.len(p.vel.x, 0, p.vel.z);
            if (pSpeed < 2.2f && p.state == ohkt.player.Player.State.ON_FOOT) {
                g.police.arrestProgress += dt;
                if (g.police.arrestProgress > 1.7f) {
                    p.busted(g);
                }
            }
        }
    }

    private boolean hasLineOfSight(Game g) {
        ohkt.player.Player p = g.player;
        float ox = pos.x, oy = pos.y + 1.4f, oz = pos.z;
        float dx = p.pos.x - ox, dy = p.pos.y + 1.1f - oy, dz = p.pos.z - oz;
        float l = Vec3.len(dx, dy, dz);
        ohkt.physics.RaycastHit hit = g.world.physics.raycast(ox, oy, oz, dx / l, dy / l, dz / l, l);
        return !hit.hit;
    }

    /** Acesso público para a polícia calcular linha de visão. */
    public boolean hasLineOfSightPublic(Game g) {
        return hasLineOfSight(g);
    }

    private void followPlayer(Game g, float dt) {
        ohkt.player.Player p = g.player;
        float d = pos.dst(p.pos);
        if (d > 4f) {
            moveTowards(g, p.pos.x, p.pos.z, Math.min(6.2f, d), dt);
        }
        // defende o jogador de inimigos próximos
        NPC threat = null;
        for (NPC n : g.npcs.list()) {
            if (!n.dead && (n.brain == Brain.FIGHT) && n.pos.dst(p.pos) < 10) {
                threat = n;
                break;
            }
        }
        if (threat != null) {
            float td = pos.dst(threat.pos);
            if (td > 1.8f) {
                moveTowards(g, threat.pos.x, threat.pos.z, 6f, dt);
            } else {
                meleeTimer -= dt;
                if (meleeTimer <= 0) {
                    meleeTimer = 0.8f;
                    threat.takeDamage(14);
                    g.particles.blood(threat.pos.x, threat.pos.y + 1.2f, threat.pos.z);
                }
            }
        }
    }

    // ---------------- condução ----------------

    private void updateDriving(Game g, float dt) {
        Vehicle v = vehicle;
        if (v == null || v.destroyed) {
            pullOutOfVehicle();
            panic(pos.x, pos.z);
            return;
        }
        if (v.driver != this) {
            vehicle = null;
            return;
        }
        if (brain == Brain.CHASE_PLAYER) {
            drivePursuit(g, dt, v);
        } else {
            driveTraffic(g, dt, v);
        }
    }

    /** Tráfego normal: segue o grafo de ruas com faixa, semáforo e frenagem. */
    private void driveTraffic(Game g, float dt, Vehicle v) {
        if (nodeA == null || nodeB == null) {
            int[] n = g.world.roadGraph.nearestNode(pos.x, pos.z);
            nodeA = n;
            nodeB = nextNode(g, n[0], n[1], -1, -1);
        }
        float[] lane = g.world.roadGraph.lanePoint(nodeA[0], nodeA[1], nodeB[0], nodeB[1], 1f);
        float dToNode = Vec3.len(lane[0] - pos.x, 0, lane[1] - pos.z);
        boolean panicMode = brain == Brain.PANIC_DRIVE;
        float targetSpeed = panicMode ? 15f : (CityLayout.isMajor(nodeB[0]) || CityLayout.isMajor(nodeB[1]) ? 11f : 8f);
        if (dToNode < 7f) {
            // semáforo
            int phase = g.world.roadGraph.lightPhase(nodeB[0], nodeB[1], g.world.time.worldTime);
            if (phase >= 0 && !panicMode) {
                boolean movingNS = nodeA[0] == nodeB[0];
                boolean green = movingNS ? g.world.roadGraph.greenForNS(phase) : !g.world.roadGraph.greenForNS(phase) || phase == 3;
                if (!green && dToNode < 6f) {
                    targetSpeed = 0;
                }
            }
        }
        if (dToNode < 4.5f) {
            int[] prev = nodeA;
            nodeA = nodeB;
            nodeB = nextNode(g, nodeB[0], nodeB[1], prev[0], prev[1]);
            if (nodeB == null) {
                pullOutOfVehicle();
                return;
            }
        }
        driveTowards(g, v, lane[0], lane[1], targetSpeed, dt);
        // obstáculo à frente
        brakeForObstacles(g, v, targetSpeed);
        // pânico passa
        if (panicMode) {
            timer -= dt;
            if (timer <= 0) brain = Brain.WANDER;
        }
        // veículo danificado: abandona
        if (v.health < 45) {
            pullOutOfVehicle();
            panic(pos.x, pos.z);
        }
    }

    private int[] nextNode(Game g, int kx, int kz, int prevKx, int prevKz) {
        java.util.List<int[]> nbs = g.world.roadGraph.neighbors(kx, kz);
        java.util.List<int[]> options = new java.util.ArrayList<>();
        for (int[] nb : nbs) {
            if (nb[0] == prevKx && nb[1] == prevKz) continue;
            options.add(nb);
        }
        if (options.isEmpty()) return nbs.get(0);
        // viés reto
        if (prevKx >= 0) {
            int dx = Integer.compare(kx, prevKx), dz = Integer.compare(kz, prevKz);
            for (int[] o : options) {
                if (o[0] - kx == dx && o[1] - kz == dz && RND.nextFloat() < 0.65f) return o;
            }
        }
        return options.get(RND.nextInt(options.size()));
    }

    private void drivePursuit(Game g, float dt, Vehicle v) {
        ohkt.player.Player p = g.player;
        float d = pos.dst(p.pos);
        if (g.police.wantedSystem.stars <= 0) {
            // volta ao tráfego
            brain = Brain.WANDER;
            nodeA = null;
            return;
        }
        float tx = p.pos.x + p.vel.x * 0.5f;
        float tz = p.pos.z + p.vel.z * 0.5f;
        v.sirenOn = true;
        float targetSpeed = Math.min(20f, 8f + d * 0.15f);
        driveTowards(g, v, tx, tz, targetSpeed, dt);
        // se jogador está a pé e perto: desembarca
        if (p.state == ohkt.player.Player.State.ON_FOOT && d < 16f) {
            v.driverInput.clear();
            v.driver = null;
            v.sirenOn = false;
            vehicle = null;
            nodeA = null;
            pos.set(v.pos.x + 1.6f, 0, v.pos.z + 1.0f);
            brain = Brain.CHASE_PLAYER;
        }
    }

    private void driveTowards(Game g, Vehicle v, float tx, float tz, float targetSpeed, float dt) {
        float dx = tx - pos.x, dz = tz - pos.z;
        float wantYaw = (float) Math.atan2(dx, -dz);
        float diff = MathX.angleDiff(v.yaw, wantYaw);
        v.driverInput.steer = MathX.clamp(-diff * 2.2f, -1, 1);
        float sp = v.forwardSpeed();
        if (Math.abs(diff) > 1.2f && Math.abs(sp) > 6f) {
            v.driverInput.brake = 0.7f;
            v.driverInput.throttle = 0;
        } else if (sp < targetSpeed) {
            v.driverInput.throttle = 0.75f;
            v.driverInput.brake = 0;
        } else {
            v.driverInput.throttle = 0;
            v.driverInput.brake = sp > targetSpeed + 3 ? 0.4f : 0;
        }
        pos.set(v.pos);
    }

    /** Versão pública para corridas (oponentes seguem checkpoints). */
    public void driveTowardsPublic(Game g, Vehicle v, float tx, float tz, float targetSpeed, float dt) {
        driveTowards(g, v, tx, tz, targetSpeed, dt);
    }

    private void brakeForObstacles(Game g, Vehicle v, float targetSpeed) {
        float fx = (float) Math.sin(v.yaw), fz = (float) -Math.cos(v.yaw);
        float sp = Math.abs(v.forwardSpeed());
        float look = 4 + sp * 0.55f;
        float ax = pos.x + fx * look, az = pos.z + fz * look;
        // veículos
        for (Vehicle other : g.vehicles.list()) {
            if (other == v) continue;
            float d = other.pos.dst(ax, other.pos.y, az);
            if (d < 3.4f) {
                v.driverInput.throttle = 0;
                v.driverInput.brake = 1;
                return;
            }
        }
        // jogador
        if (g.player.pos.dst(ax, g.player.pos.y, az) < 2.6f) {
            v.driverInput.throttle = 0;
            v.driverInput.brake = 1;
            if (sp < 1) v.driverInput.throttle = 0.15f;
        }
        // pedestres
        for (NPC n : g.npcs.list()) {
            if (n.dead || n.vehicle != null || n == this) continue;
            if (n.pos.dst(ax, n.pos.y, az) < 2.2f) {
                v.driverInput.throttle = 0;
                v.driverInput.brake = 1;
                return;
            }
        }
    }

    /** Atropelamento: chamado pelo veículo quando colide com NPC. */
    public void hitByVehicle(Game g, Vehicle v) {
        float sp = Math.abs(v.forwardSpeed());
        takeDamage(sp * 9);
        knockback(v.vel.x, v.vel.z);
        g.particles.blood(pos.x, pos.y + 1, pos.z);
        if (dead && v.driver instanceof ohkt.player.Player) {
            g.bus.post(EventBus.Type.CRIME, "ATROPELO", sp > 8);
        }
        panic(pos.x - v.vel.x, pos.z - v.vel.z);
    }

    // ---------------- reação a eventos ----------------

    public void hearGunshot(Game g, float x, float z) {
        if (dead || type == Type.COP || type == Type.GANG) return;
        float d = pos.dst(x, pos.y, z);
        if (d > 45) return;
        if (vehicle != null) {
            brain = Brain.PANIC_DRIVE;
            timer = 6;
        } else if (d < 18 && RND.nextFloat() < 0.3f) {
            brain = Brain.REPORT;
            timer = 2.5f;
        } else {
            panic(x, z);
        }
    }

    // ---------------- render ----------------

    public void render(Game g, Renderer3D r, float lodDist) {
        if (dead) {
            if (deadTimer > 25) return;
            HumanoidRenderer.draw(r, pos.x, pos.y, pos.z, yaw, 0, 0, false, false, 1, false, 0, look);
            return;
        }
        float d = pos.dst(r.cam.pos.x, r.cam.pos.y, r.cam.pos.z);
        float speed01 = brain == Brain.FLEE || brain == Brain.CHASE_PLAYER || brain == Brain.FIGHT ? 0.9f : 0.35f;
        boolean aim = brain == Brain.CHASE_PLAYER && shootTimer > 0;
        boolean crouch = false;
        HumanoidRenderer.draw(r, pos.x, pos.y, pos.z, yaw, phase, d < lodDist ? speed01 : 0, aim, crouch, 0,
                vehicle != null, meleeTimer > 0.55f ? 1 : 0, look);
        if (d < 4) {
            r.drawShadowBlob(pos.x, pos.z, 0.5f, 0.6f, pos.y);
        }
        // balão de fala
        if (talkLineTimer > 0 && d < 20) {
            r.drawSprite(pos.x, pos.y + 2.3f, pos.z, 0.4f, 0xa0ffffff, 2);
        }
        // denúncia
        if (brain == Brain.REPORT) {
            r.drawSprite(pos.x, pos.y + 2.4f, pos.z, 0.5f, 0xc0ff4040, 2);
        }
    }

    public String chatLine() {
        return brain == Brain.FLEE ? PANIC[RND.nextInt(PANIC.length)] : CHAT[RND.nextInt(CHAT.length)];
    }

    public boolean isTraffic() {
        return vehicle != null && brain != Brain.CHASE_PLAYER;
    }
}
