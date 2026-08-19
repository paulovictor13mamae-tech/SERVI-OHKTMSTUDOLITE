package ohkt.player;

import ohkt.audio.AudioEngine;
import ohkt.combat.Weapon;
import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.engine.Settings;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleManager;
import ohkt.world.Door;
import ohkt.world.World;

/**
 * Personagem jogável: Dante Moraes. Movimento, salto, agachamento, natação,
 * armas, colete, interações, entrada/saída de veículos, morte e respawn.
 */
public final class Player {

    public enum State { ON_FOOT, DRIVING, DEAD, BUSTED }

    public final Vec3 pos = new Vec3(0, 0, 0);
    public final Vec3 vel = new Vec3();
    public float yaw;          // orientacao do corpo
    public boolean onGround = true;
    public boolean crouching, sprinting, aiming, inWater, swimming;
    public float phase;        // fase da animação de caminhada
    public float attackAnim;   // 0..1 soco

    public float health = 100, armor = 0;
    public State state = State.ON_FOOT;
    public float stateTimer;
    public Vehicle vehicle;

    // armas
    public final boolean[] ownedWeapons = new boolean[Weapon.CATALOG.length];
    public final int[] magAmmo = new int[Weapon.CATALOG.length];
    public final int[] reserveAmmo = new int[Weapon.CATALOG.length];
    public int currentWeapon = 0;
    public float fireCooldown, reloadTimer, switchTimer;
    public int pendingWeapon = -1;

    public int outfitIdx = 0;
    public final HumanoidRenderer.Look look = new HumanoidRenderer.Look();

    // efeitos
    public float hurtFlash, muzzleTimer, recoilPitch, recoilYaw;
    public float footstepAcc;
    public float lastCrimeT; // usado pela polícia (calculo de fuga)

    public static final float WALK = 4.0f, RUN = 7.2f, CROUCH = 1.7f;
    public static final float RADIUS = 0.38f, HEIGHT = 1.8f;

    public Player() {
        ownedWeapons[0] = true; // punhos
        look.set(0xffc8a080, 0xffd0d0d0, 0xff3a3a44, 0xff30241c, 0xff2a2018);
    }

    public float eyeHeight() {
        return crouching ? 1.15f : 1.62f;
    }

    public void update(Game g, float dt) {
        hurtFlash = Math.max(0, hurtFlash - dt * 1.5f);
        muzzleTimer = Math.max(0, muzzleTimer - dt);
        attackAnim = Math.max(0, attackAnim - dt * 4f);
        updateWeapons(dt);

        switch (state) {
            case DEAD:
            case BUSTED:
                stateTimer += dt;
                if (stateTimer > 3.6f) respawn(g, state == State.DEAD);
                return;
            case DRIVING:
                updateDriving(g, dt);
                return;
            default:
                break;
        }

        boolean wantInteract = g.input.justPressed(Settings.Action.INTERACT);
        boolean wantEnterExit = g.input.justPressed(Settings.Action.ENTER_EXIT);

        // ---- interação com portas/serviços ----
        Door door = g.world.nearestDoor(pos.x, pos.z, 2.6f);
        if (door != null && (wantInteract || wantEnterExit)) {
            g.interactDoor(door);
            return;
        } else if (door != null) {
            g.hud.setPrompt("E — " + door.label + (door.interiorId != null || door.action != null ? "" : ""));
        }

        // ---- entrar/sair de veículo ----
        if (wantInteract || wantEnterExit) {
            Vehicle near = g.vehicles.nearest(pos.x, pos.z, 3.2f, false);
            if (near != null && near.canEnter()) {
                enterVehicle(g, near);
                return;
            }
        }

        // ---- movimento ----
        float ix = axis(g, Settings.Action.RIGHT, Settings.Action.LEFT);
        float iz = axis(g, Settings.Action.FORWARD, Settings.Action.BACK);
        float[] padMove = g.input.padMove();
        if (Math.abs(padMove[0]) > 0.1f || Math.abs(padMove[1]) > 0.1f) {
            ix = padMove[0];
            iz = -padMove[1];
        }
        sprinting = g.input.isDown(Settings.Action.SPRINT) && iz > 0.1f;
        boolean wantCrouch = g.input.isDown(Settings.Action.CROUCH);
        crouching = wantCrouch && onGround;
        aiming = g.input.isDown(Settings.Action.AIM) && weapon().kind != Weapon.Kind.MELEE && state == State.ON_FOOT;

        float camYaw = g.camera.yaw;
        float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
        float rx = (float) Math.cos(camYaw), rz = (float) Math.sin(camYaw);
        float speed = crouching ? CROUCH : (aiming ? WALK * 0.55f : (sprinting ? RUN : WALK));
        float moveX = fx * iz + rx * ix;
        float moveZ = fz * iz + rz * ix;
        float ml = Vec3.len(moveX, 0, moveZ);
        if (ml > 1) { moveX /= ml; moveZ /= ml; }

        inWater = World.isWater(pos.x, pos.z);
        swimming = inWater && pos.y < -0.4f;
        if (swimming) speed *= 0.55f;

        float accel = onGround ? 34f : 8f;
        vel.x = MathX.approach(vel.x, moveX * speed, accel * dt);
        vel.z = MathX.approach(vel.z, moveZ * speed, accel * dt);

        // salto
        if (g.input.justPressed(Settings.Action.JUMP) && onGround && !crouching && !inWater) {
            vel.y = 6.6f;
            onGround = false;
            g.audio.play("JUMP", pos.x, pos.y, pos.z, 0.25f, 1f);
        }
        if (swimming) {
            vel.y = MathX.approach(vel.y, -0.35f, 6f * dt);
            if (g.input.isDown(Settings.Action.JUMP)) vel.y = MathX.approach(vel.y, 1.4f, 8f * dt);
        } else {
            vel.y -= ohkt.physics.PhysicsWorld.GRAVITY * dt;
        }

        pos.x += vel.x * dt;
        pos.z += vel.z * dt;
        pos.y += vel.y * dt;

        // colisões estáticas
        ohkt.physics.PhysicsWorld.Position pp = new ohkt.physics.PhysicsWorld.Position(pos.x, pos.z);
        g.world.physics.resolveCircle(pp, RADIUS, Math.max(0.05f, pos.y), pos.y + (crouching ? 1.2f : 1.7f));
        pos.x = pp.x;
        pos.z = pp.z;

        // chão
        float gh = World.groundHeight(pos.x, pos.z);
        if (pos.y <= gh) {
            if (!onGround && vel.y < -9f) {
                float dmg = (-vel.y - 9f) * 6f;
                if (dmg > 0) takeDamage(g, dmg, "queda");
            }
            pos.y = gh;
            vel.y = 0;
            onGround = true;
        } else if (pos.y > gh + 0.05f) {
            onGround = false;
        }

        // orientação do corpo
        if (ml > 0.1f) {
            float moveYaw = (float) Math.atan2(moveX, -moveZ);
            yaw += MathX.angleDiff(yaw, moveYaw) * Math.min(1, dt * 12f);
        }
        if (aiming || fireCooldown > 0.2f) {
            yaw += MathX.angleDiff(yaw, camYaw) * Math.min(1, dt * 16f);
        }

        // animação de passos + som
        float hSpeed = Vec3.len(vel.x, 0, vel.z);
        if (onGround && hSpeed > 0.4f) {
            phase += hSpeed * dt * 2.4f;
            footstepAcc += hSpeed * dt;
            float stepLen = sprinting ? 2.4f : 1.8f;
            if (footstepAcc > stepLen) {
                footstepAcc = 0;
                g.audio.play("STEP", pos.x, pos.y, pos.z, 0.16f, 1f + 0.15f * hSpeed / RUN);
            }
        } else {
            phase = MathX.approach(phase, Math.round(phase / 3.1416f) * 3.1416f, dt * 8f);
        }
    }

    private void updateDriving(Game g, float dt) {
        if (vehicle == null || vehicle.destroyed) {
            exitVehicle(g, true);
            return;
        }
        pos.set(vehicle.pos);
        yaw = vehicle.yaw;
        // controles do veículo
        float throttle = axis(g, Settings.Action.FORWARD, Settings.Action.BACK) > 0 ? axis(g, Settings.Action.FORWARD, Settings.Action.BACK) : 0;
        float brake = axis(g, Settings.Action.BACK, Settings.Action.FORWARD) > 0 ? axis(g, Settings.Action.BACK, Settings.Action.FORWARD) : 0;
        float[] padMove = g.input.padMove();
        if (Math.abs(padMove[1]) > 0.1f) {
            if (padMove[1] < 0) throttle = -padMove[1];
            else brake = padMove[1];
        }
        float steer = axis(g, Settings.Action.RIGHT, Settings.Action.LEFT);
        if (Math.abs(padMove[0]) > 0.08f) steer = padMove[0];
        vehicle.driverInput.throttle = throttle;
        vehicle.driverInput.brake = brake;
        vehicle.driverInput.steer = steer;
        vehicle.driverInput.handbrake = g.input.isDown(Settings.Action.HANDBRAKE);
        if (g.input.justPressed(Settings.Action.ENTER_EXIT) || g.input.justPressed(Settings.Action.INTERACT)) {
            exitVehicle(g, false);
        }
    }

    private float axis(Game g, Settings.Action posA, Settings.Action negA) {
        float v = 0;
        if (g.input.isDown(posA)) v += 1;
        if (g.input.isDown(negA)) v -= 1;
        return MathX.clamp(v, -1, 1);
    }

    // ---------------- armas ----------------

    private void updateWeapons(float dt) {
        fireCooldown = Math.max(0, fireCooldown - dt);
        switchTimer = Math.max(0, switchTimer - dt);
        if (switchTimer <= 0 && pendingWeapon >= 0 && pendingWeapon != currentWeapon) {
            currentWeapon = pendingWeapon;
            reloadTimer = 0;
        }
        pendingWeapon = -1;
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) finishReload();
        }
        recoilPitch = MathX.approach(recoilPitch, 0, dt * 6f);
        recoilYaw = MathX.approach(recoilYaw, 0, dt * 6f);
    }

    public Weapon weapon() { return Weapon.CATALOG[currentWeapon]; }

    public void requestWeapon(int idx) {
        if (idx < 0 || idx >= Weapon.CATALOG.length || !ownedWeapons[idx] || idx == currentWeapon) return;
        pendingWeapon = idx;
        switchTimer = 0.25f;
        reloadTimer = 0;
    }

    public void cycleWeapon(int dir) {
        int idx = currentWeapon;
        for (int i = 0; i < Weapon.CATALOG.length; i++) {
            idx = (idx + dir + Weapon.CATALOG.length) % Weapon.CATALOG.length;
            if (ownedWeapons[idx]) {
                requestWeapon(idx);
                return;
            }
        }
    }

    public void startReload(Game g) {
        Weapon w = weapon();
        if (w.kind != Weapon.Kind.GUN || reloadTimer > 0) return;
        if (magAmmo[w.id] >= w.magSize || reserveAmmo[w.id] <= 0) return;
        reloadTimer = w.reloadTime;
        g.audio.play("RELOAD", pos.x, pos.y, pos.z, 0.4f, 1f);
    }

    private void finishReload() {
        Weapon w = weapon();
        int need = w.magSize - magAmmo[w.id];
        int take = Math.min(need, reserveAmmo[w.id]);
        magAmmo[w.id] += take;
        reserveAmmo[w.id] -= take;
    }

    public void giveWeapon(int id, int ammo) {
        Weapon w = Weapon.CATALOG[id];
        ownedWeapons[id] = true;
        if (w.kind == Weapon.Kind.GUN) {
            magAmmo[id] = w.magSize;
            reserveAmmo[id] += ammo;
        }
    }

    public boolean consumeMag() {
        Weapon w = weapon();
        if (magAmmo[w.id] <= 0) return false;
        magAmmo[w.id]--;
        return true;
    }

    // ---------------- dano / morte ----------------

    public void takeDamage(Game g, float amount, String cause) {
        if (state == State.DEAD || state == State.BUSTED) return;
        if (armor > 0) {
            float absorbed = Math.min(armor, amount * 0.6f);
            armor -= absorbed;
            amount -= absorbed;
        }
        health -= amount;
        hurtFlash = 1;
        g.camera.addShake(Math.min(0.6f, amount * 0.02f));
        if (health <= 0) {
            health = 0;
            die(g, cause);
        }
    }

    private void die(Game g, String cause) {
        if (state == State.DEAD) return;
        state = State.DEAD;
        stateTimer = 0;
        vel.set(0, 0, 0);
        g.bus.post(EventBus.Type.PLAYER_DIED, cause);
        g.audio.play("DEATH", pos.x, pos.y, pos.z, 0.8f, 1f);
    }

    public void busted(Game g) {
        if (state == State.BUSTED || state == State.DEAD) return;
        state = State.BUSTED;
        stateTimer = 0;
        if (vehicle != null) exitVehicle(g, true);
        g.bus.post(EventBus.Type.PLAYER_BUSTED);
    }

    private void respawn(Game g, boolean hospital) {
        float[] p;
        if (hospital) {
            p = ohkt.world.CityLayout.specialPos(ohkt.world.CityLayout.Special.HOSPITAL);
            g.economy.spend(g, Math.round(g.economy.money() * 0.1f), "taxa hospitalar");
        } else {
            p = ohkt.world.CityLayout.specialPos(ohkt.world.CityLayout.Special.DELEGACIA);
            for (int i = 1; i < ownedWeapons.length; i++) {
                ownedWeapons[i] = false;
                magAmmo[i] = 0;
                reserveAmmo[i] = 0;
            }
            currentWeapon = 0;
        }
        pos.set(p[0] + 2, 0.2f, p[1] + 8);
        vel.set(0, 0, 0);
        health = hospital ? 100 : 80;
        armor = 0;
        state = State.ON_FOOT;
        g.police.clearWanted(true);
        g.bus.post(EventBus.Type.PLAYER_RESPAWN, hospital);
    }

    // ---------------- veículos ----------------

    public void enterVehicle(Game g, Vehicle v) {
        if (v.destroyed) return;
        // carjacking
        if (v.driver instanceof ohkt.npc.NPC) {
            ohkt.npc.NPC n = (ohkt.npc.NPC) v.driver;
            n.pullOutOfVehicle();
            n.panic(pos.x, pos.z);
            g.bus.post(EventBus.Type.CRIME, "ROUBO_VEICULO", true);
        }
        vehicle = v;
        v.driver = this;
        state = State.DRIVING;
        g.bus.post(EventBus.Type.VEHICLE_ENTERED, v);
        g.audio.play("ENTER_CAR", pos.x, pos.y, pos.z, 0.4f, 1f);
    }

    public void exitVehicle(Game g, boolean forced) {
        if (vehicle == null) return;
        Vehicle v = vehicle;
        vehicle = null;
        v.driver = null;
        v.driverInput.clear();
        state = State.ON_FOOT;
        float rx = (float) Math.cos(v.yaw), rz = (float) Math.sin(v.yaw);
        pos.set(v.pos.x + rx * (v.type.hx + 0.7f), v.pos.y, v.pos.z + rz * (v.type.hx + 0.7f));
        vel.set(v.vel.x * 0.2f, 0, v.vel.z * 0.2f);
        g.bus.post(EventBus.Type.VEHICLE_EXITED, v);
        if (!forced) g.audio.play("EXIT_CAR", pos.x, pos.y, pos.z, 0.3f, 1f);
    }

    public void applyOutfit(Outfit o) {
        look.shirt = o.shirt;
        look.pants = o.pants;
        look.shoes = o.shoes;
    }
}
