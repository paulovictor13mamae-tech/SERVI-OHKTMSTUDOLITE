package ohkt.combat;

import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.engine.Settings;
import ohkt.npc.NPC;
import ohkt.player.Player;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleType;

import java.util.List;
import java.util.Random;

/**
 * Sistema de combate: disparos com raycast (headshot, recuo, dispersão),
 * corpo a corpo, explosões em área, tracer/particles e munição.
 */
public final class CombatSystem {

    private final Random rnd = new Random(99);
    public final Pickup.Manager pickups = new Pickup.Manager();
    private float bloom = 0;

    public void update(Game g, float dt) {
        bloom = Math.max(0, bloom - dt * 0.8f);
        pickups.update(g, dt);

        Player p = g.player;
        if (p.state != Player.State.ON_FOOT && p.state != Player.State.DRIVING) return;

        Weapon w = p.weapon();
        boolean fireDown = g.input.isDown(Settings.Action.FIRE);
        boolean fireJust = g.input.isDown(Settings.Action.FIRE) && (w.auto || justFirePressed(g));
        if (p.state == Player.State.DRIVING) fireJust = justFirePressed(g); // drive-by semi

        if (g.input.justPressed(Settings.Action.RELOAD)) p.startReload(g);

        // troca de armas
        if (g.input.justPressed(Settings.Action.WEAPON_NEXT) || g.input.wheel < 0) p.cycleWeapon(1);
        if (g.input.justPressed(Settings.Action.WEAPON_PREV) || g.input.wheel > 0) p.cycleWeapon(-1);
        checkNumberKeys(g);

        if (fireJust && p.fireCooldown <= 0 && p.reloadTimer <= 0) {
            if (w.kind == Weapon.Kind.MELEE) {
                meleeAttack(g);
            } else {
                if (p.magAmmo[w.id] <= 0) {
                    p.startReload(g);
                } else {
                    fireGun(g, w);
                }
            }
        }
    }

    private boolean justFirePressed(Game g) {
        return g.input.isMouseJustPressed(1) || g.input.justPressed(Settings.Action.FIRE);
    }

    private void checkNumberKeys(Game g) {
        for (int i = 0; i < 7; i++) {
            try {
                Settings.Action a = Settings.Action.valueOf("WEAPON_" + (i + 1));
                if (g.input.justPressed(a)) g.player.requestWeapon(i);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // ---------------- armas de fogo ----------------

    private void fireGun(Game g, Weapon w) {
        Player p = g.player;
        if (!p.consumeMag()) return;
        p.fireCooldown = w.rate;
        p.muzzleTimer = 0.05f;

        // origem: olho (a pe) ou janela do carro
        float ox, oy, oz;
        if (p.state == Player.State.DRIVING && p.vehicle != null) {
            Vehicle v = p.vehicle;
            ox = v.pos.x;
            oy = v.pos.y + v.type.bodyH + 0.5f;
            oz = v.pos.z;
        } else {
            ox = p.pos.x;
            oy = p.pos.y + p.eyeHeight() - 0.1f;
            oz = p.pos.z;
        }

        float spread = w.spread * (p.aiming ? 0.45f : 1.6f) * (1 + bloom);
        bloom = Math.min(1.6f, bloom + w.recoil * 0.3f);

        ohkt.graphics.Camera cam = g.renderer.cam;
        Vec3 fwd = cam.forward(new Vec3());
        for (int i = 0; i < w.pellets; i++) {
            float dx = fwd.x + (rnd.nextFloat() - 0.5f) * 2 * spread;
            float dy = fwd.y + (rnd.nextFloat() - 0.5f) * 2 * spread;
            float dz = fwd.z + (rnd.nextFloat() - 0.5f) * 2 * spread;
            float l = Vec3.len(dx, dy, dz);
            raycastShot(g, ox, oy, oz, dx / l, dy / l, dz / l, w.range, w.damage, true, w.id);
        }

        // recuo
        p.recoilPitch += w.recoil * 0.045f;
        p.recoilYaw += (rnd.nextFloat() - 0.5f) * w.recoil * 0.02f;
        g.camera.addShake(w.recoil * 0.06f);
        g.particles.muzzleFlash(ox + fwd.x * 0.6f, oy - 0.12f, oz + fwd.z * 0.6f, fwd.x, fwd.y, fwd.z);
        g.particles.casing(ox, oy - 0.2f, oz, (float) Math.cos(p.yaw), (float) Math.sin(p.yaw));
        g.audio.play(shotSound(w.id), ox, oy, oz, 0.7f, 1f + (rnd.nextFloat() - 0.5f) * 0.1f);
        g.bus.post(EventBus.Type.GUNSHOT, ox, oy, oz, w.id);
    }

    public static String shotSound(int weaponId) {
        switch (weaponId) {
            case 3: return "SHOT_TUFAO";
            case 4: return "SHOT_VESPA";
            case 5: return "SHOT_BRUTA";
            case 6: return "SHOT_CONDOR";
            default: return "SHOT_GP9";
        }
    }

    /** Raycast completo de um tiro. Retorna distancia do hit (ou range). */
    public float raycastShot(Game g, float ox, float oy, float oz, float dx, float dy, float dz,
                             float range, float damage, boolean fromPlayer, int weaponId) {
        // mundo estático
        ohkt.physics.RaycastHit worldHit = g.world.physics.raycast(ox, oy, oz, dx, dy, dz, range);
        float bestT = worldHit.hit ? worldHit.t : range;

        // alvo do tiro: NPCs (se atirador é o jogador) ou jogador (se NPC)
        if (fromPlayer) {
            for (NPC n : g.npcs.list()) {
                if (n.dead) continue;
                float t = raySegmentPoint(ox, oy, oz, dx, dy, dz, n.pos.x, n.pos.y + 0.9f, n.pos.z, range);
                if (t < 0) continue;
                float hy = oy + dy * t;
                boolean head = hy > n.pos.y + 1.42f && hy < n.pos.y + 1.95f;
                float hitR = head ? 0.22f : 0.42f;
                float[] closest = closestPoint(ox, oy, oz, dx, dy, dz, n.pos.x, n.pos.y + 0.9f, n.pos.z);
                float dist = Vec3.len(closest[0] - n.pos.x, closest[1] - (n.pos.y + 0.9f), closest[2] - n.pos.z);
                if (dist < hitR && t < bestT) {
                    bestT = t;
                    float dmg = damage * (head ? 2.3f : 1f);
                    hitNPC(g, n, dmg, head, closest[0], closest[1], closest[2]);
                }
            }
            // veículos
            for (Vehicle v : g.vehicles.list()) {
                float t = rayVehicle(ox, oy, oz, dx, dy, dz, v, range);
                if (t > 0 && t < bestT) {
                    bestT = t;
                    float px = ox + dx * t, py = oy + dy * t, pz = oz + dz * t;
                    v.damage(g, damage * 0.55f);
                    g.particles.impactSparks(px, py, pz, -dx, -dy, -dz);
                    g.audio.play("METAL_HIT", px, py, pz, 0.3f, 1f);
                }
            }
        } else {
            Player p = g.player;
            float[] closest = closestPoint(ox, oy, oz, dx, dy, dz, p.pos.x, p.pos.y + 0.95f, p.pos.z);
            float dist = Vec3.len(closest[0] - p.pos.x, closest[1] - (p.pos.y + 0.95f), closest[2] - p.pos.z);
            float hitR = p.crouching ? 0.5f : 0.55f;
            if (dist < hitR) {
                float t = closest[3];
                if (t < bestT) {
                    bestT = t;
                    p.takeDamage(g, damage, "tiro");
                    g.particles.blood(p.pos.x, p.pos.y + 1.2f, p.pos.z);
                    return bestT;
                }
            }
        }

        // efeito final no ponto
        if (worldHit.hit && bestT >= worldHit.t) {
            g.particles.impactSparks(worldHit.px, worldHit.py, worldHit.pz, worldHit.nx, worldHit.ny, worldHit.nz);
        }
        addTracer(ox + dx * 0.7f, oy - 0.1f, oz + dz * 0.7f,
                ox + dx * bestT, oy + dy * bestT, oz + dz * bestT);
        return bestT;
    }

    // ---------------- tracers ----------------

    private static final class Tracer {
        float x0, y0, z0, x1, y1, z1, life;

        Tracer(float x0, float y0, float z0, float x1, float y1, float z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.life = 0.06f;
        }
    }

    private final java.util.List<Tracer> tracers = new java.util.ArrayList<>();

    private void addTracer(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (tracers.size() > 40) tracers.remove(0);
        tracers.add(new Tracer(x0, y0, z0, x1, y1, z1));
    }

    public void render(ohkt.graphics.Renderer3D r) {
        java.util.Iterator<Tracer> it = tracers.iterator();
        while (it.hasNext()) {
            Tracer t = it.next();
            t.life -= 0.016f;
            if (t.life <= 0) {
                it.remove();
                continue;
            }
            r.drawTracer(t.x0, t.y0, t.z0, t.x1, t.y1, t.z1, 0.05f, 0x90ffe8b0);
        }
    }

    private void hitNPC(Game g, NPC n, float dmg, boolean head, float hx, float hy, float hz) {
        n.takeDamage(dmg);
        g.particles.blood(hx, hy, hz);
        if (n.dead) {
            if (n.type == NPC.Type.COP) {
                g.bus.post(EventBus.Type.COP_KILLED, n);
            } else {
                g.bus.post(EventBus.Type.PED_KILLED, n);
            }
            // criminosos derrubam dinheiro
            if (n.type == NPC.Type.CRIMINAL || n.type == NPC.Type.GANG) {
                pickups.spawn(Pickup.Type.CASH, 40 + rnd.nextInt(120), n.pos.x + rnd.nextFloat() - 0.5f, n.pos.z + rnd.nextFloat() - 0.5f);
            }
        }
    }

    /** t no segmento mais próximo do ponto; -1 se fora. */
    private float raySegmentPoint(float ox, float oy, float oz, float dx, float dy, float dz,
                                  float px, float py, float pz, float range) {
        float[] c = closestPoint(ox, oy, oz, dx, dy, dz, px, py, pz);
        return c[3];
    }

    /** Ponto mais próximo na reta; retorna [x,y,z,t]. */
    public static float[] closestPoint(float ox, float oy, float oz, float dx, float dy, float dz,
                                       float px, float py, float pz) {
        float vx = px - ox, vy = py - oy, vz = pz - oz;
        float t = vx * dx + vy * dy + vz * dz;
        if (t < 0) t = 0;
        return new float[]{ox + dx * t, oy + dy * t, oz + dz * t, t};
    }

    private float rayVehicle(float ox, float oy, float oz, float dx, float dy, float dz, Vehicle v, float range) {
        // aproximação: esfera no centro + eixo
        float[] c = closestPoint(ox, oy, oz, dx, dy, dz, v.pos.x, v.pos.y + 0.6f, v.pos.z);
        float dist = Vec3.len(c[0] - v.pos.x, c[1] - (v.pos.y + 0.6f), c[2] - v.pos.z);
        if (dist < v.type.hz * 0.8f) {
            return c[3];
        }
        return -1;
    }

    // ---------------- corpo a corpo ----------------

    private void meleeAttack(Game g) {
        Player p = g.player;
        Weapon w = p.weapon();
        p.fireCooldown = w.rate;
        p.attackAnim = 1f;
        g.audio.play("SWING", p.pos.x, p.pos.y + 1, p.pos.z, 0.35f, 1f);
        float fwX = (float) Math.sin(p.yaw), fwZ = (float) -Math.cos(p.yaw);
        boolean hitSomething = false;
        for (NPC n : g.npcs.list()) {
            if (n.dead) continue;
            float dx = n.pos.x - p.pos.x, dz = n.pos.z - p.pos.z;
            float d = Vec3.len(dx, 0, dz);
            if (d > w.range) continue;
            float dirYaw = (float) Math.atan2(dx, -dz);
            if (Math.abs(MathX.angleDiff(p.yaw, dirYaw)) > 1.15f) continue;
            n.takeDamage(w.damage);
            n.knockback(dx / d * 5, dz / d * 5);
            g.particles.blood(n.pos.x, n.pos.y + 1.2f, n.pos.z);
            g.audio.play("PUNCH", n.pos.x, n.pos.y + 1, n.pos.z, 0.5f, 0.9f);
            hitSomething = true;
            if (n.dead) g.bus.post(EventBus.Type.PED_KILLED, n);
        }
        if (!hitSomething) {
            // soco no veiculo
            Vehicle v = g.vehicles.nearest(p.pos.x + fwX, p.pos.z + fwZ, 1.6f, false);
            if (v != null) {
                v.damage(g, w.damage * 0.4f);
                g.particles.impactSparks(p.pos.x + fwX, p.pos.y + 1, p.pos.z + fwZ, -fwX, 0, -fwZ);
            }
        }
    }

    // ---------------- explosões ----------------

    public void explosionDamage(Game g, float x, float y, float z, float damage, float radius, Object source) {
        // NPCs
        for (NPC n : g.npcs.list()) {
            if (n.dead) continue;
            float d = n.pos.dst(x, y, z);
            if (d < radius) {
                float dmg = damage * (1 - d / radius);
                n.takeDamage(dmg);
                n.knockback((n.pos.x - x) * 2f, (n.pos.z - z) * 2f);
                if (n.dead) g.bus.post(n.type == NPC.Type.COP ? EventBus.Type.COP_KILLED : EventBus.Type.PED_KILLED, n);
            }
        }
        // jogador
        Player p = g.player;
        float pd = p.pos.dst(x, y + 0.9f, z);
        if (pd < radius && source != p) {
            p.takeDamage(g, damage * 0.8f * (1 - pd / radius), "explosão");
        }
        // veículos
        for (Vehicle v : g.vehicles.list()) {
            if (v.destroyed || v == source) continue;
            float d = v.pos.dst(x, y, z);
            if (d < radius) {
                v.damage(g, damage * 0.8f * (1 - d / radius));
            }
        }
    }

    // ---------------- NPC atirando ----------------

    /** NPC atira no jogador com precisão dada. */
    public void npcShootAtPlayer(Game g, NPC shooter, float accuracy, float damage) {
        Player p = g.player;
        float ox = shooter.pos.x, oy = shooter.pos.y + 1.4f, oz = shooter.pos.z;
        float tx = p.pos.x, ty = p.pos.y + (p.crouching ? 0.8f : 1.1f), tz = p.pos.z;
        float dx = tx - ox, dy = ty - oy, dz = tz - oz;
        float len = Vec3.len(dx, dy, dz);
        if (len < 0.5f) return;
        dx /= len;
        dy /= len;
        dz /= len;
        // erro de mira
        float err = (1 - accuracy) * 0.09f;
        dx += (rnd.nextFloat() - 0.5f) * err;
        dy += (rnd.nextFloat() - 0.5f) * err;
        dz += (rnd.nextFloat() - 0.5f) * err;
        float l2 = Vec3.len(dx, dy, dz);
        dx /= l2;
        dy /= l2;
        dz /= l2;
        g.particles.muzzleFlash(ox + dx * 0.5f, oy, oz + dz * 0.5f, dx, dy, dz);
        g.audio.play("SHOT_GP9", ox, oy, oz, 0.6f, 1.05f);
        g.bus.post(EventBus.Type.GUNSHOT, ox, oy, oz, 2);
        raycastShot(g, ox, oy, oz, dx, dy, dz, 90f, damage, false, 2);
    }
}
