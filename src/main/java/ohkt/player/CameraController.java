package ohkt.player;

import ohkt.engine.Game;
import ohkt.engine.Settings;
import ohkt.graphics.Camera;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;

import ohkt.vehicle.Vehicle;

/**
 * Cameras: terceira pessoa, primeira pessoa e perseguição em veículos,
 * com colisão de câmera, mira sobre o ombro, sacudida e combate.
 */
public final class CameraController {

    public enum Mode { THIRD, FIRST }

    public Mode mode = Mode.THIRD;
    public float yaw, pitch;
    public float shake;
    public float fovKick;

    private final Vec3 desired = new Vec3();
    private final Vec3 target = new Vec3();
    private float curDist = 5.5f;
    private boolean vehicleCamAuto = true;

    public void addShake(float amount) {
        shake = Math.min(1.4f, shake + amount);
    }

    public void update(Game g, float dt) {
        Player p = g.player;
        float sens = g.settings.mouseSensitivity;
        // mouse / gamepad
        float dx = g.input.mouseDX, dy = g.input.mouseDY;
        float[] padLook = g.input.padLook();
        dx += padLook[0] * 380 * dt;
        dy += padLook[1] * 280 * dt;
        yaw -= dx * 0.0028f * sens;
        pitch += (g.settings.invertY ? -dy : dy) * 0.0022f * sens;
        pitch = MathX.clamp(pitch, -1.2f, 1.35f);
        if (p.state == Player.State.DEAD || p.state == Player.State.BUSTED) {
            pitch = MathX.clamp(pitch, -0.4f, 1.2f);
        }
        shake = Math.max(0, shake - dt * 2.2f);
        fovKick = MathX.approach(fovKick, 0, dt * 2f);

        Camera cam = g.renderer.cam;
        cam.fov = g.settings.fov + fovKick;

        if (p.state == Player.State.DRIVING && p.vehicle != null) {
            vehicleCamera(g, p.vehicle, dt);
            return;
        }

        float eyeH = p.eyeHeight();
        target.set(p.pos.x, p.pos.y + eyeH * 0.92f, p.pos.z);

        if (mode == Mode.FIRST || p.state == Player.State.DEAD) {
            cam.pos.set(target);
            if (p.state == Player.State.DEAD) cam.pos.y = p.pos.y + 0.7f;
            cam.yaw = yaw;
            cam.pitch = pitch;
        } else {
            boolean aiming = p.aiming;
            float wantDist = aiming ? 2.1f : (p.sprinting ? 6.2f : 5.2f);
            float height = aiming ? 0.12f : 0.55f;
            curDist = MathX.approach(curDist, wantDist, dt * 4f);
            float fwX = (float) Math.sin(yaw), fwZ = (float) -Math.cos(yaw);
            float rX = (float) Math.cos(yaw), rZ = (float) Math.sin(yaw);
            float shoulder = aiming ? 0.55f : 0.2f;
            desired.set(
                    target.x - fwX * curDist + rX * shoulder,
                    target.y + height + pitch * -curDist * 0.4f + 0.35f,
                    target.z - fwZ * curDist + rZ * shoulder);
            desired.y = Math.max(desired.y, ohkt.world.World.groundHeight(desired.x, desired.z) + 0.3f);
            // colisao da camera: encurta se atravessar parede
            float dirX = desired.x - target.x, dirY = desired.y - target.y, dirZ = desired.z - target.z;
            float len = Vec3.len(dirX, dirY, dirZ);
            if (len > 0.01f) {
                ohkt.physics.RaycastHit hit = g.world.physics.raycast(
                        target.x, target.y, target.z, dirX / len, dirY / len, dirZ / len, len);
                if (hit.hit) {
                    float d = Math.max(0.35f, hit.t - 0.3f);
                    desired.set(target.x + dirX / len * d, target.y + dirY / len * d, target.z + dirZ / len * d);
                }
            }
            cam.pos.lerp(desired, Math.min(1, dt * 22f));
            cam.yaw = yaw;
            cam.pitch = pitch;
        }
        applyShake(cam);
    }

    private void vehicleCamera(Game g, Vehicle v, float dt) {
        Camera cam = g.renderer.cam;
        if (vehicleCamAuto && g.input.isDown(Settings.Action.BACK)) {
            // olhar para tras mantendo B? nao: usa mouse normalmente
        }
        float speed = Math.abs(v.forwardSpeed());
        // acompanha o yaw do veiculo suavemente
        float vy = v.yaw;
        float diff = MathX.angleDiff(yaw, vy);
        float autoStrength = vehicleCamAuto ? MathX.clamp(speed / 6f, 0, 1) : 0;
        if (Math.abs(diff) > 2.6f) autoStrength = 1; // caso vire 180
        yaw += diff * Math.min(1, autoStrength * dt * 2.6f);
        float dist = 6.2f + v.type.hz * 1.4f + speed * 0.055f;
        float height = 2.2f + v.type.hz * 0.24f;
        float fwX = (float) Math.sin(yaw), fwZ = (float) -Math.cos(yaw);
        target.set(v.pos.x, v.pos.y + height, v.pos.z);
        desired.set(target.x - fwX * dist, target.y + 0.7f + pitch * -dist * 0.35f, target.z - fwZ * dist);
        desired.y = Math.max(desired.y, ohkt.world.World.groundHeight(desired.x, desired.z) + 0.4f);
        float dirX = desired.x - target.x, dirY = desired.y - target.y, dirZ = desired.z - target.z;
        float len = Vec3.len(dirX, dirY, dirZ);
        if (len > 0.01f) {
            ohkt.physics.RaycastHit hit = g.world.physics.raycast(target.x, target.y, target.z, dirX / len, dirY / len, dirZ / len, len);
            if (hit.hit) {
                float d = Math.max(0.5f, hit.t - 0.35f);
                desired.set(target.x + dirX / len * d, target.y + dirY / len * d, target.z + dirZ / len * d);
            }
        }
        cam.pos.lerp(desired, Math.min(1, dt * 10f));
        cam.yaw = yaw;
        cam.pitch = pitch * 0.5f;
        applyShake(cam);
    }

    private void applyShake(Camera cam) {
        if (shake > 0.001f) {
            float s = shake * 0.14f;
            cam.pos.x += (float) Math.sin(System.nanoTime() * 0.00013f) * s;
            cam.pos.y += (float) Math.sin(System.nanoTime() * 0.00017f) * s;
            cam.pos.z += (float) Math.cos(System.nanoTime() * 0.00011f) * s;
        }
    }

    public void toggleMode() {
        mode = mode == Mode.THIRD ? Mode.FIRST : Mode.THIRD;
    }
}
