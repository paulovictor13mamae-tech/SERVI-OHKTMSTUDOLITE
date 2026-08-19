package ohkt.graphics;

import ohkt.utils.Mat4;
import ohkt.utils.Vec3;

/** Camera com yaw (0 = norte/-Z, cresce para leste) e pitch (positivo para cima). */
public final class Camera {
    public final Vec3 pos = new Vec3(0, 3, 8);
    public float yaw, pitch;
    public float fov = 70f;
    public float near = 0.25f;
    public float far = 420f;

    public final Mat4 view = new Mat4();
    public final Mat4 proj = new Mat4();
    public final Mat4 viewProj = new Mat4();
    public final Frustum frustum = new Frustum();

    private final float[] tmpA = new float[16];
    private final Vec3 target = new Vec3();
    private final Vec3 fwd = new Vec3();

    public void update(float aspect) {
        fwd.set((float) Math.sin(yaw), 0, (float) -Math.cos(yaw));
        float cp = (float) Math.cos(pitch);
        fwd.set(fwd.x * cp, (float) Math.sin(pitch), fwd.z * cp).norm();
        target.set(pos).add(fwd);
        view.lookAt(pos, target, Vec3Util.UP);
        proj.perspective(fov, aspect, near, far);
        Mat4.mul(tmpA, proj.m, view.m);
        System.arraycopy(tmpA, 0, viewProj.m, 0, 16);
        frustum.fromMatrix(viewProj.m);
    }

    public Vec3 forward(Vec3 out) {
        float cp = (float) Math.cos(pitch);
        return out.set((float) Math.sin(yaw) * cp, (float) Math.sin(pitch), (float) -Math.cos(yaw) * cp).norm();
    }

    public Vec3 right(Vec3 out) {
        return out.set((float) Math.cos(yaw), 0, (float) Math.sin(yaw));
    }

    static final class Vec3Util {
        static final Vec3 UP = new Vec3(0, 1, 0);
    }
}
