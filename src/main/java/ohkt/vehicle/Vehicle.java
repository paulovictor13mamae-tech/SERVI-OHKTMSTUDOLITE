package ohkt.vehicle;

import ohkt.engine.EventBus;
import ohkt.engine.Game;
import ohkt.graphics.Renderer3D;
import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;

/**
 * Veículo físico: aceleração, freio, direção com derrapagem, suspensão visual,
 * marchas, combustível, dano, destruição/explosão, luzes, buzina e sirene.
 */
public final class Vehicle {

    public final VehicleType type;
    public final Vec3 pos = new Vec3();
    public final Vec3 vel = new Vec3();
    public float yaw;
    public float steerAngle, steerVisual;

    public float health = 100;
    public float fuel;
    public boolean destroyed, burning;
    public float burnTimer, huskTimer;
    public boolean exploded;

    public boolean lightsOn, sirenOn, hornOn;
    public float wheelSpin;
    public float suspPitch, suspRoll;
    public int paint;
    public int engineLevel, tireLevel; // mods da oficina

    // gestão
    public int chunkI = -1, chunkJ = -1;
    public boolean parked, persist, mission; // persist = veículo do jogador
    public String missionTag;

    /** Motorista: Player, NPC ou null. */
    public Object driver;

    public static final class Controls {
        public float throttle, brake, steer;
        public boolean handbrake;

        public void clear() {
            throttle = 0;
            brake = 0;
            steer = 0;
            handbrake = false;
        }
    }

    public final Controls driverInput = new Controls();

    private float fireAcc, smokeAcc;
    public float sirenPhase;

    public Vehicle(VehicleType type, float x, float z, float yaw, int paint) {
        this.type = type;
        this.pos.set(x, ohkt.world.World.groundHeight(x, z), z);
        this.yaw = yaw;
        this.paint = paint;
        this.fuel = type.fuelCap * (0.35f + MathUtilsRandom.nextFloat() * 0.6f);
        this.lightsOn = false;
    }

    public boolean canEnter() {
        return !destroyed && !burning && !inWater();
    }

    public boolean inWater() {
        return ohkt.world.World.isWater(pos.x, pos.z);
    }

    public float forwardSpeed() {
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        return vel.x * fx + vel.z * fz;
    }

    public void update(Game g, float dt) {
        if (huskTimer > 0) huskTimer -= dt;
        if (inWater()) {
            // afunda
            pos.y = MathX.approach(pos.y, -1.2f, dt * 0.5f);
            vel.x *= 1 - dt * 0.8f;
            vel.z *= 1 - dt * 0.8f;
            if (driver instanceof ohkt.player.Player) {
                ((ohkt.player.Player) driver).exitVehicle(g, true);
            } else if (driver instanceof ohkt.npc.NPC) {
                ((ohkt.npc.NPC) driver).pullOutOfVehicle();
            }
            if (pos.y < -1f && !exploded) {
                destroy(g, false);
            }
            return;
        }
        if (destroyed) {
            vel.x *= 1 - dt * 2f;
            vel.z *= 1 - dt * 2f;
            return;
        }
        if (burning) {
            fireAcc += dt;
            if (fireAcc > 0.05f) {
                fireAcc = 0;
                g.particles.fire(pos.x, pos.y + 0.8f, pos.z, 2);
            }
            burnTimer -= dt;
            if (burnTimer <= 0) {
                destroy(g, true);
                return;
            }
        } else if (health < 45) {
            smokeAcc += dt;
            if (smokeAcc > 0.16f) {
                smokeAcc = 0;
                g.particles.smoke(pos.x, pos.y + 1f, pos.z, 1);
            }
        }

        Controls c = driverInput;
        float speedF = forwardSpeed();
        boolean hasFuel = fuel > 0;

        // ---- motor ----
        float accel = type.accel * (1f + engineLevel * 0.15f);
        if (hasFuel && c.throttle > 0.01f) {
            float power = c.throttle * accel * Math.max(0f, 1f - Math.abs(speedF) / type.topSpeed);
            float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
            vel.x += fx * power * dt;
            vel.z += fz * power * dt;
            fuel -= c.throttle * dt * type.fuelUse * 3f;
        }
        // ré / freio
        if (c.brake > 0.01f) {
            if (speedF > 0.4f) {
                float bx = -(float) Math.sin(yaw) * c.brake * type.brakeForce * dt;
                float bz = (float) Math.cos(yaw) * c.brake * type.brakeForce * dt;
                vel.x += bx;
                vel.z += bz;
            } else if (hasFuel) {
                // ré
                float power = c.brake * accel * 0.55f * Math.max(0f, 1f - Math.abs(speedF) / (type.topSpeed * 0.3f));
                vel.x -= (float) Math.sin(yaw) * power * dt;
                vel.z -= (float) -Math.cos(yaw) * power * dt;
                fuel -= c.brake * dt * type.fuelUse * 2f;
            }
        }
        // consumo por movimento
        fuel -= Math.abs(speedF) * dt * type.fuelUse * 0.4f;
        if (fuel < 0) fuel = 0;

        // ---- direção + atrito ----
        float wetGrip = g.world.weather.gripFactor();
        float grip = type.grip * (1f + tireLevel * 0.1f) * wetGrip;
        float targetSteer = c.steer * (type.isMotorcycle() ? 0.55f : 0.7f);
        steerAngle = MathX.approach(steerAngle, targetSteer, dt * 5f);
        float speedFactor = MathX.clamp(Math.abs(speedF) / 4.5f, 0, 1) * Math.signum(speedF == 0 ? 1 : speedF);
        // subesterço em alta velocidade
        float highSpeedDamp = 1f / (1f + Math.abs(speedF) * 0.045f);
        yaw += steerAngle * type.turnRate * speedFactor * highSpeedDamp * dt * 2.2f;

        // reavalia a velocidade longitudinal após motor/freio
        speedF = forwardSpeed();
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        float latX = vel.x - fx * speedF, latZ = vel.z - fz * speedF;
        float gripLat = c.handbrake ? grip * 0.22f : grip;
        float latDamp = (float) Math.pow(1 - Math.min(0.95f, gripLat * 3.2f * (c.handbrake ? 0.5f : 1f) * 0.5f), dt * 60f);
        latX *= latDamp;
        latZ *= latDamp;
        // resistencia de rolagem
        float roll = (c.handbrake ? 3f : 0.55f) * dt;
        speedF *= 1 - Math.min(0.5f, roll);
        vel.x = fx * speedF + latX;
        vel.z = fz * speedF + latZ;

        float slip = Vec3.len(latX, 0, latZ);
        if (slip > 4f && Math.abs(speedF) > 4f) {
            g.particles.tireSmoke(pos.x - fx * type.hz, pos.y + 0.15f, pos.z - fz * type.hz);
        }

        // ---- integra ----
        pos.x += vel.x * dt;
        pos.z += vel.z * dt;
        float gh = ohkt.world.World.groundHeight(pos.x, pos.z);
        pos.y = MathX.approach(pos.y, gh, dt * 8f);

        // ---- colisões estáticas (2 círculos) ----
        collideWorld(g, dt, fx, fz);

        // visual
        wheelSpin += speedF * dt / type.wheelR;
        steerVisual = MathX.approach(steerVisual, steerAngle, dt * 8f);
        float targetPitch = MathX.clamp((c.throttle - c.brake) * -0.03f * (speedF >= 0 ? 1 : -1), -0.05f, 0.05f);
        suspPitch = MathX.approach(suspPitch, targetPitch, dt * 6f);
        float targetRoll = MathX.clamp(-steerAngle * Math.abs(speedF) * 0.006f, -0.08f, 0.08f);
        suspRoll = MathX.approach(suspRoll, targetRoll, dt * 6f);
        sirenPhase += dt * 8f;

        // buzina
        if (hornOn && driver != null) {
            g.audio.play("HORN", pos.x, pos.y, pos.z, 0.5f, type.isMotorcycle() ? 1.4f : 1f);
        }
        // motor (som continuo)
        if (driver != null) {
            g.audio.engineTick(this, Math.abs(speedF));
        }
    }

    private void collideWorld(Game g, float dt, float fx, float fz) {
        float speed = Vec3.len(vel.x, 0, vel.z);
        float[][] probes = {
                {pos.x + fx * type.hz * 0.62f, pos.z + fz * type.hz * 0.62f},
                {pos.x - fx * type.hz * 0.62f, pos.z - fz * type.hz * 0.62f}
        };
        float r = type.hx * 1.05f + 0.15f;
        for (float[] probe : probes) {
            ohkt.physics.PhysicsWorld.Position pp = new ohkt.physics.PhysicsWorld.Position(probe[0], probe[1]);
            float pushed = g.world.physics.resolveCircle(pp, r, pos.y + 0.2f, pos.y + type.bodyH);
            if (pushed > 0.001f) {
                float dx = pp.x - probe[0], dz = pp.z - probe[1];
                pos.x += dx;
                pos.z += dz;
                // mata componente na direção do empurrão
                float pl = Vec3.len(dx, 0, dz);
                if (pl > 1e-5f) {
                    float nx = dx / pl, nz = dz / pl;
                    float vn = vel.x * nx + vel.z * nz;
                    if (vn < 0) {
                        vel.x -= vn * nx * 1.3f;
                        vel.z -= vn * nz * 1.3f;
                    }
                }
                if (speed > 6f) {
                    damage(g, (speed - 6f) * 2.4f);
                    g.particles.impactSparks(probe[0], pos.y + 0.5f, probe[1], -dx, 0.4f, -dz);
                    g.audio.play("CRASH", pos.x, pos.y, pos.z, Math.min(1f, speed * 0.06f), 0.9f);
                    if (driver instanceof ohkt.player.Player) {
                        ((ohkt.player.Player) driver).takeDamage(g, Math.max(0, (speed - 9f)) * 3.5f, "colisão");
                        g.camera.addShake(Math.min(1f, speed * 0.05f));
                    }
                }
            }
        }
    }

    public void damage(Game g, float amount) {
        if (destroyed) return;
        health -= amount;
        if (health < 20 && !burning) {
            burning = true;
            burnTimer = 6f;
        }
        if (health <= 0) {
            health = 0;
            destroy(g, true);
        }
    }

    public void destroy(Game g, boolean explosion) {
        if (destroyed) return;
        destroyed = true;
        burning = false;
        if (driver instanceof ohkt.player.Player) {
            ohkt.player.Player p = (ohkt.player.Player) driver;
            p.exitVehicle(g, true);
            if (explosion) p.takeDamage(g, 65, "explosão");
        } else if (driver instanceof ohkt.npc.NPC) {
            ohkt.npc.NPC n = (ohkt.npc.NPC) driver;
            n.pullOutOfVehicle();
            if (explosion) n.takeDamage(200);
        }
        if (explosion) {
            exploded = true;
            g.particles.explosion(pos.x, pos.y + 0.8f, pos.z);
            g.audio.play("EXPLOSION", pos.x, pos.y, pos.z, 1.2f, 1f);
            g.camera.addShake(Math.max(0.3f, 1.2f - pos.dst(g.player.pos) * 0.02f));
            g.bus.post(EventBus.Type.EXPLOSION, pos.x, pos.y, pos.z, "VEICULO");
            g.combat.explosionDamage(g, pos.x, pos.y + 0.8f, pos.z, 90, 7f, this);
        }
        g.bus.post(EventBus.Type.VEHICLE_DESTROYED, this);
        huskTimer = 22f;
    }

    public boolean wantsDelete() {
        return destroyed && huskTimer <= 0;
    }

    // ---------------- render ----------------

    public void render(Game g, Renderer3D r) {
        boolean night = g.world.time.isNight();
        int bodyPaint = destroyed ? 0xff1c1c1e : paint;
        float fwX = (float) Math.sin(yaw), fwZ = (float) -Math.cos(yaw);
        float rX = (float) Math.cos(yaw), rZ = (float) Math.sin(yaw);
        float roll = type.isMotorcycle() ? MathX.clamp(-steerAngle * Math.abs(forwardSpeed()) * 0.05f, -0.5f, 0.5f) : suspRoll;
        float bodyY = pos.y + type.wheelR + type.bodyH / 2;

        // sombra
        r.drawShadowBlob(pos.x, pos.z, type.hz * 0.85f, 0.75f, ohkt.world.World.groundHeight(pos.x, pos.z));

        if (type.isMotorcycle()) {
            r.drawBox(pos.x, bodyY - 0.1f, pos.z, type.hx, type.bodyH * 0.7f, type.hz * 0.8f, yaw, suspPitch, roll, bodyPaint, false);
            // guidao + banco
            r.drawBox(pos.x + fwX * type.hz * 0.5f, bodyY + 0.25f, pos.z + fwZ * type.hz * 0.5f, 0.3f, 0.08f, 0.08f, yaw, 0, 0, 0xff3a3a3e, false);
            wheel(g, r, type.hz * 0.75f, 0, 0);
            wheel(g, r, -type.hz * 0.75f, 0, 0);
        } else {
            // corpo
            r.drawBox(pos.x, bodyY, pos.z, type.hx, type.bodyH / 2, type.hz, yaw, suspPitch, roll, bodyPaint, false);
            // cabine
            float cabH = type.kind.equals("VAN") || type.kind.equals("TRUCK") ? type.bodyH * 0.9f : type.bodyH * 0.62f;
            float cabOff = type.kind.equals("VAN") ? 0 : (type.kind.equals("TRUCK") ? -type.hz * 0.45f : -type.hz * 0.06f);
            int glass = destroyed ? 0xff222226 : 0xff1e2c3c;
            if (type.kind.equals("TRUCK")) {
                // cabine à frente + baú
                r.drawBox(pos.x + fwX * type.hz * 0.55f, bodyY + type.bodyH * 0.75f, pos.z + fwZ * type.hz * 0.55f,
                        type.hx * 0.95f, type.bodyH * 0.65f, type.hz * 0.4f, yaw, suspPitch, roll, bodyPaint, false);
                r.drawBox(pos.x - fwX * type.hz * 0.25f, bodyY + type.bodyH * 0.9f, pos.z - fwZ * type.hz * 0.25f,
                        type.hx, type.bodyH * 1.05f, type.hz * 0.72f, yaw, suspPitch, roll, 0xffc8c4b8, false);
            } else {
                r.drawBox(pos.x + fwX * cabOff, bodyY + type.bodyH * 0.85f, pos.z + fwZ * cabOff,
                        type.hx * 0.88f, cabH / 2, type.hz * 0.48f, yaw, suspPitch, roll, glass, false);
            }
            // rodas
            float wx = type.hx * 0.92f, wz = type.hz * 0.66f;
            wheel(g, r, wz, wx, steerVisual);
            wheel(g, r, wz, -wx, steerVisual);
            wheel(g, r, -wz, wx, 0);
            wheel(g, r, -wz, -wx, 0);
            // faróis
            if (!destroyed) {
                int headCol = lightsOn ? 0xfffff0c0 : 0xffc8c8b8;
                float hy = bodyY;
                r.drawBox(pos.x + fwX * type.hz - rX * type.hx * 0.7f, hy, pos.z + fwZ * type.hz - rZ * type.hx * 0.7f, 0.14f, 0.12f, 0.06f, yaw, 0, 0, headCol, lightsOn);
                r.drawBox(pos.x + fwX * type.hz + rX * type.hx * 0.7f, hy, pos.z + fwZ * type.hz + rZ * type.hx * 0.7f, 0.14f, 0.12f, 0.06f, yaw, 0, 0, headCol, lightsOn);
                boolean braking = driverInput.brake > 0.1f;
                int tailCol = braking ? 0xffff2020 : (lightsOn ? 0xffa02020 : 0xff601414);
                r.drawBox(pos.x - fwX * type.hz - rX * type.hx * 0.7f, hy, pos.z - fwZ * type.hz - rZ * type.hx * 0.7f, 0.12f, 0.1f, 0.05f, yaw, 0, 0, tailCol, braking || lightsOn);
                r.drawBox(pos.x - fwX * type.hz + rX * type.hx * 0.7f, hy, pos.z - fwZ * type.hz + rZ * type.hx * 0.7f, 0.12f, 0.1f, 0.05f, yaw, 0, 0, tailCol, braking || lightsOn);
            }
            // taxi / policia
            if (type.kind.equals("TAXI") && !destroyed) {
                r.drawBox(pos.x, bodyY + type.bodyH * 1.25f, pos.z, 0.34f, 0.12f, 0.14f, yaw, 0, 0, 0xffffe040, true);
            }
            if (type.kind.equals("POLICE") && !destroyed) {
                boolean flip = ((int) (sirenPhase * 2)) % 2 == 0;
                r.drawBox(pos.x, bodyY + type.bodyH * 1.25f, pos.z, 0.4f, 0.1f, 0.12f, yaw, 0, 0, 0xff28282c, false);
                if (sirenOn) {
                    r.drawBox(pos.x - rX * 0.25f, bodyY + type.bodyH * 1.38f, pos.z - rZ * 0.25f, 0.14f, 0.08f, 0.1f, yaw, 0, 0, flip ? 0xffff3040 : 0xff701010, true);
                    r.drawBox(pos.x + rX * 0.25f, bodyY + type.bodyH * 1.38f, pos.z + rZ * 0.25f, 0.14f, 0.08f, 0.1f, yaw, 0, 0, !flip ? 0xff3060ff : 0xff101840, true);
                }
            }
        }

        // luzes no chão à noite
        if (lightsOn && !destroyed && (night || g.world.time.hour < 6.5f)) {
            float ax = pos.x + fwX * (type.hz + 3.5f), az = pos.z + fwZ * (type.hz + 3.5f);
            r.drawGroundDisk(ax - rX * 0.6f, az - rZ * 0.6f, 2.4f, 0.06f, ColorUtil.rgba(255, 240, 190, 46), 2);
            r.drawGroundDisk(ax + rX * 0.6f, az + rZ * 0.6f, 2.4f, 0.06f, ColorUtil.rgba(255, 240, 190, 46), 2);
            r.drawSprite(pos.x + fwX * type.hz, bodyY, pos.z + fwZ * type.hz, 0.4f, ColorUtil.rgba(255, 245, 200, 160), 2);
        }
        if (sirenOn && !destroyed) {
            boolean flip = ((int) (sirenPhase * 2)) % 2 == 0;
            r.drawSprite(pos.x, bodyY + 0.9f, pos.z, 1.5f,
                    ColorUtil.rgba(flip ? 255 : 60, 40, flip ? 40 : 255, 110), 2);
        }
    }

    private void wheel(Game g, Renderer3D r, float fwdOff, float rightOff, float steer) {
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        float rX = (float) Math.cos(yaw), rZ = (float) Math.sin(yaw);
        float wx = pos.x + fx * fwdOff + rX * rightOff;
        float wz = pos.z + fz * fwdOff + rZ * rightOff;
        float wy = pos.y + type.wheelR;
        r.drawBox(wx, wy, wz, type.wheelHalf, type.wheelR, type.wheelR, yaw + steer, wheelSpin, 0, 0xff18181a, false);
    }

    /** Ponto de entrada/saída (lado direito). */
    public float[] doorPoint() {
        float rX = (float) Math.cos(yaw), rZ = (float) Math.sin(yaw);
        return new float[]{pos.x + rX * (type.hx + 0.8f), pos.z + rZ * (type.hx + 0.8f)};
    }

    public float kmh() {
        return Math.abs(forwardSpeed()) * 3.6f;
    }

    /** Marcha atual (1..6, R). */
    public String gearLabel() {
        float s = forwardSpeed();
        if (s < -0.5f) return "R";
        float abs = Math.abs(s);
        if (abs < 0.3f) return "N";
        float[] th = {7f, 13f, 20f, 28f, 38f};
        int gear = 1;
        for (int i = 0; i < th.length; i++) {
            if (abs > th[i]) gear = i + 2;
        }
        return String.valueOf(Math.min(gear, 6));
    }

    /** RPM 0..1 para áudio. */
    public float rpm01() {
        float abs = Math.abs(forwardSpeed());
        float[] th = {7f, 13f, 20f, 28f, 38f};
        float low = 0, high = th[0];
        for (int i = 0; i < th.length; i++) {
            if (abs > th[i]) {
                low = th[i];
                high = i + 1 < th.length ? th[i + 1] : th[i] + 12f;
            }
        }
        float f = (abs - low) / Math.max(1f, high - low);
        return MathX.clamp(0.25f + f * 0.75f + driverInput.throttle * 0.12f, 0, 1);
    }

    static final class MathUtilsRandom {
        static final java.util.Random R = new java.util.Random(1234);

        static float nextFloat() {
            return R.nextFloat();
        }
    }
}
