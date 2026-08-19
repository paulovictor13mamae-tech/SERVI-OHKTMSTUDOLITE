package ohkt.mission;

import ohkt.engine.Game;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;

/**
 * Cutscenes: letterbox, câmera em travelling e diálogos sequenciais.
 * Pode ser pulada com a tecla de interação.
 */
public final class CutscenePlayer {

    public static final class Line {
        public final String speaker, text;
        public final float dur;

        public Line(String speaker, String text, float dur) {
            this.speaker = speaker;
            this.text = text;
            this.dur = dur;
        }
    }

    private Line[] lines;
    private int lineIdx;
    private float lineTimer;
    private boolean active;
    private float progress01;

    // câmera
    private final Vec3 camFrom = new Vec3(), camTo = new Vec3(), target = new Vec3();
    private Runnable onEnd;
    private float duration;

    public boolean isActive() { return active; }

    public void start(Game g, Line[] lines, float fromX, float fromY, float fromZ,
                      float toX, float toY, float toZ, float targetX, float targetY, float targetZ, Runnable onEnd) {
        this.lines = lines;
        this.lineIdx = 0;
        this.lineTimer = lines.length > 0 ? lines[0].dur : 0;
        this.active = true;
        this.camFrom.set(fromX, fromY, fromZ);
        this.camTo.set(toX, toY, toZ);
        this.target.set(targetX, targetY, targetZ);
        this.onEnd = onEnd;
        this.duration = 0;
        for (Line l : lines) duration += l.dur;
        progress01 = 0;
        g.audio.play("CUTSCENE", 0, 0, 0, 0.3f, 1f);
    }

    public void update(Game g, float dt) {
        if (!active) return;
        progress01 = Math.min(1, progress01 + dt / Math.max(0.1f, duration));
        lineTimer -= dt;
        if (lineTimer <= 0) {
            lineIdx++;
            if (lineIdx < lines.length) {
                lineTimer = lines[lineIdx].dur;
            } else {
                end(g);
                return;
            }
        }
        // câmera travelling
        float t = MathX.smooth(progress01);
        Vec3 camPos = g.renderer.cam.pos;
        camPos.set(MathX.lerp(camFrom.x, camTo.x, t),
                MathX.lerp(camFrom.y, camTo.y, t),
                MathX.lerp(camFrom.z, camTo.z, t));
        g.renderer.cam.yaw = (float) Math.atan2(target.x - camPos.x, -(target.z - camPos.z));
        float dy = target.y - camPos.y;
        float dh = Vec3.len(target.x - camPos.x, 0, target.z - camPos.z);
        g.renderer.cam.pitch = (float) Math.atan2(dy, Math.max(0.1f, dh));
        // pular
        if (g.input.justPressed(ohkt.engine.Settings.Action.INTERACT)
                || g.input.justPressed(ohkt.engine.Settings.Action.PAUSE)) {
            end(g);
        }
    }

    private void end(Game g) {
        active = false;
        if (onEnd != null) onEnd.run();
    }

    /** Render 2D (letterbox + legenda). */
    public void render(java.awt.Graphics2D g2, int w, int h) {
        if (!active) return;
        int bar = h / 8;
        g2.setColor(java.awt.Color.BLACK);
        g2.fillRect(0, 0, w, bar);
        g2.fillRect(0, h - bar, w, bar);
        if (lineIdx < lines.length) {
            Line l = lines[lineIdx];
            g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, Math.max(14, h / 34)));
            String full = l.speaker + ": " + l.text;
            ohkt.ui.Widgets.drawWrappedText(g2, full, w / 2, h - bar - 26, w * 4 / 5,
                    java.awt.Color.WHITE, true);
        }
    }
}
