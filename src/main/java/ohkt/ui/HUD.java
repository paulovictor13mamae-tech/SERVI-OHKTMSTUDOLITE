package ohkt.ui;

import ohkt.combat.Weapon;
import ohkt.engine.Game;
import ohkt.mission.Mission;
import ohkt.mission.Objective;
import ohkt.player.Player;
import ohkt.utils.MathX;
import ohkt.vehicle.Vehicle;
import ohkt.world.CityLayout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/** HUD completo: minimapa rotativo, vida, colete, dinheiro, procurado, arma, velocímetro, missões. */
public final class HUD {

    private final List<String[]> notifications = new ArrayList<>(); // {texto, tempo}
    private String prompt;
    private String jobPrompt;
    private String[] dialogue;   // {speaker, text}
    private float dialogueTimer;
    public boolean mapOpen;
    public float flashWhite; // relâmpago
    private float moneyDisplay = -1;

    public void notify(String text) {
        notifications.add(new String[]{text, "4"});
        if (notifications.size() > 5) notifications.remove(0);
    }

    public void dialogue(String speaker, String text, float seconds) {
        dialogue = new String[]{speaker, text};
        dialogueTimer = seconds;
    }

    public void setPrompt(String p) { this.prompt = p; }

    public void setJobPrompt(String p) { this.jobPrompt = p; }

    public void update(float dt) {
        for (String[] n : notifications) {
            float t = Float.parseFloat(n[1]);
            n[1] = String.valueOf(t - dt);
        }
        notifications.removeIf(n -> Float.parseFloat(n[1]) <= 0);
        if (dialogueTimer > 0) {
            dialogueTimer -= dt;
            if (dialogueTimer <= 0) dialogue = null;
        }
        flashWhite = Math.max(0, flashWhite - dt * 3);
        prompt = null; // redefinido a cada frame pelos sistemas
    }

    public void render(Game g, Graphics2D gg, int w, int h) {
        Player p = g.player;
        Font small = new Font("SansSerif", Font.BOLD, Math.max(12, h / 48));
        Font med = new Font("SansSerif", Font.BOLD, Math.max(14, h / 36));
        Font big = new Font("SansSerif", Font.BOLD, Math.max(20, h / 22));

        if (p.state == Player.State.DEAD || p.state == Player.State.BUSTED) {
            String t = p.state == Player.State.DEAD ? "VOCÊ MORREU" : "PRESO!";
            gg.setColor(new Color(0, 0, 0, (int) Math.min(200, p.stateTimer * 120)));
            gg.fillRect(0, 0, w, h);
            Widgets.text(gg, t, w / 2, h / 2, new Color(0xff3020), true, big);
            String sub = p.state == Player.State.DEAD ? "Reanimando no Hospital Santa Clara..." : "Os covardes te levaram à 12ª Delegacia...";
            Widgets.text(gg, sub, w / 2, h / 2 + h / 20, Color.LIGHT_GRAY, true, small);
            return;
        }

        // topo esquerdo: vida/colete/dinheiro
        int bx = 14, by = 12, bw = Math.min(230, w / 5);
        Widgets.panel(gg, bx - 6, by - 6, bw + 12, 62, 0x90060610, 0xff303040);
        Widgets.bar(gg, bx, by, bw, 12, p.health / 100f, new Color(0x30d050), new Color(0x283028));
        Widgets.text(gg, "VIDA", bx + 2, by + 11, new Color(0xc8f0c8), false, small);
        Widgets.bar(gg, bx, by + 16, bw, 10, p.armor / 100f, new Color(0x4098f0), new Color(0x283040));
        if (moneyDisplay < 0) moneyDisplay = g.economy.money();
        moneyDisplay = MathX.approach(moneyDisplay, g.economy.money(), 8);
        Widgets.text(gg, Widgets.money((int) moneyDisplay), bx, by + 46, new Color(0x70e880), false, med);

        // topo direito: relógio/clima/procurado
        String phase = g.world.time.phaseName();
        String clock = g.world.time.clockString() + "  " + phase;
        String weather = g.world.weather.label();
        int rx = w - 14;
        Widgets.panel(gg, rx - 190, by - 6, 196, 62, 0x90060610, 0xff303040);
        Widgets.text(gg, clock, rx - 92, by + 12, Color.WHITE, true, small);
        Widgets.text(gg, weather + "  Dia " + g.world.time.day, rx - 92, by + 30, Color.LIGHT_GRAY, true, small);
        int stars = g.police.wantedSystem.stars;
        if (stars > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(i < stars ? "★" : "·");
            float blink = stars >= 3 ? (float) Math.abs(Math.sin(g.world.time.worldTime * 6)) : 1;
            Widgets.text(gg, sb.toString(), rx - 92, by + 54,
                    new Color(1f, 0.85f, 0.2f, 0.4f + 0.6f * blink), true, med);
        }

        // objetivo da missão
        Mission m = g.missions.current;
        if (m != null && !g.missions.cutscene.isActive()) {
            Objective o = m.currentObjective();
            if (o != null) {
                Widgets.panel(gg, w / 2 - 220, 4, 440, 26, 0x90060610, 0xff403820);
                Widgets.text(gg, "◈ " + o.text, w / 2, 22, new Color(0xffe090), true, small);
            }
        }

        // arma/munição
        Weapon wp = p.weapon();
        int wy = h - 60;
        Widgets.panel(gg, w - 200, wy - 20, 186, 52, 0x90060610, 0xff303040);
        Widgets.text(gg, wp.name, w - 192, wy, Color.WHITE, false, small);
        if (wp.kind == Weapon.Kind.GUN) {
            String ammo = p.magAmmo[wp.id] + " / " + p.reserveAmmo[wp.id];
            Widgets.text(gg, ammo, w - 192, wy + 22, p.magAmmo[wp.id] == 0 ? Color.RED : Color.LIGHT_GRAY, false, med);
        } else {
            Widgets.text(gg, "corpo a corpo", w - 192, wy + 22, Color.GRAY, false, small);
        }

        // veículo
        if (p.state == Player.State.DRIVING && p.vehicle != null) {
            Vehicle v = p.vehicle;
            Widgets.panel(gg, w / 2 - 150, h - 46, 300, 36, 0x90060610, 0xff303040);
            Widgets.text(gg, String.format("%3d km/h   %s", (int) v.kmh(), v.gearLabel()), w / 2 - 140, h - 20, Color.WHITE, false, med);
            Widgets.bar(gg, w / 2 + 30, h - 32, 100, 8, v.fuel / v.type.fuelCap, new Color(0xffc040), new Color(0x302820));
            Widgets.text(gg, "gasolina", w / 2 + 30, h - 34, Color.GRAY, false, new Font("SansSerif", Font.PLAIN, 10));
            if (v.health < 60) {
                Widgets.text(gg, v.health < 25 ? "! MOTOR CRÍTICO !" : "veículo danificado",
                        w / 2, h - 52, v.health < 25 ? Color.RED : Color.ORANGE, true, small);
            }
        }

        // rádio
        if (g.audio.music().playing()) {
            Widgets.text(gg, g.audio.music().stationName() + " — " + g.audio.music().trackName(),
                    w / 2, h - 8, new Color(0.9f, 0.9f, 0.9f, 0.7f), true, small);
        }

        if (g.settings.showMinimap) {
            drawMinimap(g, gg, w, h, small);
        }

        if (p.aiming || (p.weapon().kind == Weapon.Kind.GUN && p.state == Player.State.ON_FOOT)) {
            drawCrosshair(g, gg, w, h);
        }

        if (prompt != null && prompt.length() > 4) {
            Widgets.panel(gg, w / 2 - 160, h / 2 + h / 8, 320, 28, 0x90060610, 0xff504830);
            Widgets.text(gg, prompt, w / 2, h / 2 + h / 8 + 19, Color.WHITE, true, small);
        }
        if (jobPrompt != null) {
            Widgets.panel(gg, w / 2 - 220, 60, 440, 26, 0x90161018, 0xff205838);
            Widgets.text(gg, jobPrompt, w / 2, 78, new Color(0x60e8a0), true, small);
        }

        if (g.police.arrestProgress > 0.1f) {
            Widgets.text(gg, "A POLÍCIA ESTÁ TE ALGEMANDO!", w / 2, h / 3, Color.RED, true, med);
            Widgets.bar(gg, w / 2 - 120, h / 3 + 10, 240, 10, g.police.arrestProgress / 1.7f, Color.RED, Color.DARK_GRAY);
        }

        int ny = h / 3;
        for (String[] n : notifications) {
            float t = Float.parseFloat(n[1]);
            int alpha = (int) (Math.min(1, t) * 230);
            Widgets.panel(gg, w - 330, ny, 316, 26, (alpha << 24) | 0x060610, 0xff303040);
            Widgets.text(gg, n[0], w - 324, ny + 19, new Color(1f, 1f, 1f, Math.min(1, t + 0.3f)), false, small);
            ny += 30;
        }

        if (dialogue != null) {
            Widgets.panel(gg, w / 2 - w * 2 / 5, h - 120, w * 4 / 5, 64, 0xb000000a, 0xff605830);
            gg.setFont(med);
            Widgets.drawWrappedText(gg, dialogue[0] + ": " + dialogue[1], w / 2, h - 92, w * 7 / 10, new Color(0xffe8b0), true);
        }

        if (p.hurtFlash > 0) {
            gg.setColor(new Color(1f, 0.1f, 0.1f, p.hurtFlash * 0.28f));
            gg.fillRect(0, 0, w, h);
        }
        if (flashWhite > 0) {
            gg.setColor(new Color(1f, 1f, 1f, flashWhite * 0.5f));
            gg.fillRect(0, 0, w, h);
        }
        float night = g.world.time.nightFactor();
        if (night > 0.05f) {
            gg.setColor(new Color(0f, 0f, 0.08f, night * 0.12f));
            gg.fillRect(0, 0, w, h);
        }

        if (g.settings.showFps || g.settings.debugInfo) {
            debugOverlay(g, gg, w, small);
        }

        if (mapOpen) {
            drawBigMap(g, gg, w, h);
        }
    }

    private void drawCrosshair(Game g, Graphics2D gg, int w, int h) {
        int x = w / 2, y = h / 2;
        int spread = 6 + (int) (g.player.recoilPitch * 260);
        gg.setColor(new Color(1f, 1f, 1f, 0.85f));
        gg.fillRect(x - 1, y - spread - 6, 2, 6);
        gg.fillRect(x - 1, y + spread, 2, 6);
        gg.fillRect(x - spread - 6, y - 1, 6, 2);
        gg.fillRect(x + spread, y - 1, 6, 2);
        gg.setColor(new Color(1f, 0.2f, 0.2f, 0.9f));
        gg.fillRect(x - 1, y - 1, 2, 2);
    }

    // ---------------- minimapa ----------------

    private float[] toMap(float wx, float wz, Player p, float cos, float sin, float scale) {
        float rx = wx - p.pos.x, rz = wz - p.pos.z;
        return new float[]{(rx * cos + rz * sin) * scale, (-rx * sin + rz * cos) * scale};
    }

    private void plotBlip(Graphics2D gg, float wx, float wz, Player p, float cos, float sin,
                          float scale, int cx, int cy, float limit, Color c, boolean clampEdge, int sizePx) {
        float[] m = toMap(wx, wz, p, cos, sin, scale);
        float d = (float) Math.sqrt(m[0] * m[0] + m[1] * m[1]);
        if (d > limit && !clampEdge) return;
        float k = d > limit ? limit / d : 1;
        gg.setColor(c);
        gg.fillOval(cx + (int) (m[0] * k) - sizePx / 2, cy + (int) (m[1] * k) - sizePx / 2, sizePx, sizePx);
    }

    private void drawMinimap(Game g, Graphics2D gg, int w, int h, Font small) {
        int size = Math.min(170, h / 4);
        int cx = 20 + size / 2, cy = h - 20 - size / 2;
        float scale = size / 95f;
        Player p = g.player;
        float yaw = g.camera.yaw;
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);

        java.awt.geom.Area clip = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(cx - size / 2f, cy - size / 2f, size, size));
        gg.setClip(clip);
        gg.setColor(new Color(0x101418));
        gg.fillRect(cx - size / 2, cy - size / 2, size, size);

        float[] waterRel = toMap(p.pos.x, CityLayout.WATER_Z + 40, p, cos, sin, scale);
        gg.setColor(new Color(0x18344a));
        gg.fillRect(cx - size, cy - size + (int) waterRel[1], size * 2, size * 2);

        gg.setColor(new Color(0x50545c));
        for (int k = 0; k <= CityLayout.NB; k++) {
            float rk = CityLayout.roadCoord(k);
            if (Math.abs(rk - p.pos.x) < 110) {
                float[] a = toMap(rk, p.pos.z - 110, p, cos, sin, scale);
                float[] b = toMap(rk, p.pos.z + 110, p, cos, sin, scale);
                gg.setStroke(new java.awt.BasicStroke(CityLayout.isMajor(k) ? 3 : 2));
                gg.drawLine(cx + (int) a[0], cy + (int) a[1], cx + (int) b[0], cy + (int) b[1]);
            }
            if (Math.abs(rk - p.pos.z) < 110) {
                float[] a = toMap(p.pos.x - 110, rk, p, cos, sin, scale);
                float[] b = toMap(p.pos.x + 110, rk, p, cos, sin, scale);
                gg.setStroke(new java.awt.BasicStroke(CityLayout.isMajor(k) ? 3 : 2));
                gg.drawLine(cx + (int) a[0], cy + (int) a[1], cx + (int) b[0], cy + (int) b[1]);
            }
        }
        gg.setStroke(new java.awt.BasicStroke(1));

        // blips: missão, trabalho, eventos, polícia, propriedades
        Mission m = g.missions.current;
        if (m != null) {
            Objective o = m.currentObjective();
            if (o != null) {
                if (o.type == Objective.Type.RACE && o.checkpoints != null && g.missions.raceCheckpoint < o.checkpoints.size()) {
                    float[] cp = o.checkpoints.get(g.missions.raceCheckpoint);
                    plotBlip(gg, cp[0], cp[1], p, cos, sin, scale, cx, cy, size / 2f - 4, new Color(0x30c0ff), false, 7);
                } else {
                    plotBlip(gg, o.x, o.z, p, cos, sin, scale, cx, cy, size / 2f - 4, new Color(0xffd020), true, 7);
                }
            }
        }
        float[] job = g.sideActivities.marker();
        if (job != null) plotBlip(gg, job[0], job[1], p, cos, sin, scale, cx, cy, size / 2f - 4, new Color(0x40e0a0), true, 6);
        float[] ev = g.randomEvents.marker();
        if (ev != null) plotBlip(gg, ev[0], ev[1], p, cos, sin, scale, cx, cy, size / 2f - 4, new Color(0xe08030), true, 6);
        if (g.police.wantedSystem.stars > 0) {
            for (Vehicle v : g.policeUnitsForMap()) {
                plotBlip(gg, v.pos.x, v.pos.z, p, cos, sin, scale, cx, cy, size / 2f - 4, new Color(0x4080ff), false, 5);
            }
            if (g.police.heliActive()) {
                plotBlip(gg, g.player.pos.x, g.player.pos.z, p, cos, sin, scale, cx, cy, 14, new Color(0xff5050), true, 5);
            }
        }
        gg.setClip(null);
        gg.setColor(new Color(0xd0d0d8, true));
        gg.drawOval(cx - size / 2, cy - size / 2, size, size);
        gg.setColor(Color.WHITE);
        java.awt.Polygon arrow = new java.awt.Polygon(
                new int[]{cx, cx - 5, cx + 5}, new int[]{cy - 6, cy + 4, cy + 4}, 3);
        gg.fill(arrow);
        float[] north = toMap(p.pos.x, p.pos.z - 50, p, cos, sin, scale);
        Widgets.text(gg, "N", cx + (int) north[0], cy + (int) north[1] + 4, Color.LIGHT_GRAY, true, small);
    }

    // ---------------- mapa grande ----------------

    private static float mapLin(float v, int o, float s, float span) {
        return o + (v + span / 2) * s;
    }

    private void drawBigMap(Game g, Graphics2D gg, int w, int h) {
        gg.setColor(new Color(0f, 0f, 0f, 0.78f));
        gg.fillRect(0, 0, w, h);
        int size = Math.min(w, h) - 80;
        int ox = (w - size) / 2, oy = (h - size) / 2 + 10;
        float span = CityLayout.NB * CityLayout.BLOCK + 320;
        float s = size / span;
        Player p = g.player;

        gg.setColor(new Color(0x14181e));
        gg.fillRect(ox, oy, size, size);
        gg.setColor(new Color(0x18344a));
        gg.fillRect(ox, (int) mapLin(CityLayout.WATER_Z, oy, s, span), size, size);

        gg.setColor(new Color(0x50545c));
        for (int k = 0; k <= CityLayout.NB; k++) {
            float rk = CityLayout.roadCoord(k);
            gg.setStroke(new java.awt.BasicStroke(CityLayout.isMajor(k) ? 2 : 1));
            gg.drawLine((int) mapLin(rk, ox, s, span), (int) mapLin(CityLayout.ORIGIN, oy, s, span),
                    (int) mapLin(rk, ox, s, span), (int) mapLin(CityLayout.ORIGIN + CityLayout.NB * CityLayout.BLOCK, oy, s, span));
            gg.drawLine((int) mapLin(CityLayout.ORIGIN, ox, s, span), (int) mapLin(rk, oy, s, span),
                    (int) mapLin(CityLayout.ORIGIN + CityLayout.NB * CityLayout.BLOCK, ox, s, span), (int) mapLin(rk, oy, s, span));
        }
        gg.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, h / 60)));
        label(gg, "CENTRO", 12, 12, ox, oy, s, span);
        label(gg, "COMÉRCIO", 13, 9, ox, oy, s, span);
        label(gg, "JARDIM DAS ACÁCIAS", 4, 12, ox, oy, s, span);
        label(gg, "VILA DO METAL", 22, 13, ox, oy, s, span);
        label(gg, "CAIS DO SUL", 12, 23, ox, oy, s, span);
        label(gg, "PARQUE AURORA", 3, 3, ox, oy, s, span);
        label(gg, "PERIFERIA", 12, 2, ox, oy, s, span);
        label(gg, "ILHA DO FAROL", 26, 26, ox, oy, s, span);

        // marcadores
        Mission m = g.missions.current;
        if (m != null) {
            Objective o = m.currentObjective();
            if (o != null) {
                gg.setColor(new Color(0xffd020));
                gg.fillOval((int) mapLin(o.x, ox, s, span) - 5, (int) mapLin(o.z, oy, s, span) - 5, 10, 10);
            }
        }
        float[] job = g.sideActivities.marker();
        if (job == null) job = g.randomEvents.marker();
        if (job != null) {
            gg.setColor(new Color(0x40e0a0));
            gg.fillOval((int) mapLin(job[0], ox, s, span) - 4, (int) mapLin(job[1], oy, s, span) - 4, 8, 8);
        }
        for (String id : g.properties.owned()) {
            ohkt.economy.Properties.Def d = ohkt.economy.Properties.defOf(id);
            float[] pp = CityLayout.specialPos(d.block);
            gg.setColor(new Color(0x30a0ff));
            gg.fillRect((int) mapLin(pp[0], ox, s, span) - 4, (int) mapLin(pp[1], oy, s, span) - 4, 8, 8);
        }
        if (g.police.wantedSystem.stars > 0) {
            gg.setColor(new Color(0x4080ff));
            for (Vehicle v : g.policeUnitsForMap()) {
                gg.fillOval((int) mapLin(v.pos.x, ox, s, span) - 3, (int) mapLin(v.pos.z, oy, s, span) - 3, 6, 6);
            }
        }
        // jogador
        gg.setColor(Color.WHITE);
        java.awt.Polygon arrow = new java.awt.Polygon();
        float px = mapLin(p.pos.x, ox, s, span), pz = mapLin(p.pos.z, oy, s, span);
        double a = p.yaw;
        arrow.addPoint((int) (px + Math.sin(a) * 9), (int) (pz - Math.cos(a) * 9));
        arrow.addPoint((int) (px + Math.sin(a + 2.5) * 7), (int) (pz - Math.cos(a + 2.5) * 7));
        arrow.addPoint((int) (px + Math.sin(a - 2.5) * 7), (int) (pz - Math.cos(a - 2.5) * 7));
        gg.fill(arrow);

        Widgets.text(gg, "PORTO AURORA — mapa da cidade  (M para fechar)", w / 2, oy - 12, Color.LIGHT_GRAY, true,
                new Font("SansSerif", Font.BOLD, 16));
        gg.setStroke(new java.awt.BasicStroke(1));
    }

    private void label(Graphics2D gg, String name, int bi, int bj, int ox, int oy, float s, float span) {
        float x = CityLayout.roadCoord(bi), z = CityLayout.roadCoord(bj);
        gg.setColor(new Color(0.55f, 0.6f, 0.65f, 0.85f));
        gg.drawString(name, mapLin(x, ox, s, span), mapLin(z, oy, s, span));
    }

    private void debugOverlay(Game g, Graphics2D gg, int w, Font small) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        String info = String.format("%s fps | tris %d/%d | chunks %d | NPCs %d | veíc %d | mem %dMB",
                g.fpsDisplay, g.renderer.trisDrawn, g.renderer.trisCulled,
                g.world.loadedChunks().size(), g.npcs.aliveCount(), g.vehicles.count(), used);
        Widgets.text(gg, info, 10, 20, Color.CYAN, false, small);
        if (g.settings.debugInfo) {
            Player p = g.player;
            Widgets.text(gg, String.format("pos %.1f, %.1f, %.1f | distrito %s | quer %d | %s",
                    p.pos.x, p.pos.y, p.pos.z, CityLayout.districtAt(p.pos.x, p.pos.z).label,
                    g.police.wantedSystem.stars,
                    g.input.gamepadConnected() ? "gamepad OK" : "teclado/mouse"), 10, 38, Color.CYAN, false, small);
        }
    }

    public boolean hasDialogue() {
        return dialogue != null;
    }
}
