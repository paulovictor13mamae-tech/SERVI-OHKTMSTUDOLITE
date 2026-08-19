package ohkt.scene;

import ohkt.engine.Game;
import ohkt.engine.Settings;
import ohkt.graphics.Renderer3D;
import ohkt.mission.CutscenePlayer;
import ohkt.mission.Objective;
import ohkt.player.HumanoidRenderer;
import ohkt.player.Player;
import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;
import ohkt.world.TimeSystem;
import ohkt.world.WeatherSystem;

import java.awt.Graphics2D;

/**
 * Cena principal do mundo: atualização de todos os sistemas, render 3D
 * (céu, cidade, entidades, efeitos) e HUD.
 */
public final class GameScene implements ohkt.engine.Scene {

    private final Game g;
    private float sunMoonGlow;

    public GameScene(Game g) {
        this.g = g;
    }

    @Override
    public String name() { return "mundo"; }

    @Override
    public void enter() {
        if (!g.inMainMenu) {
            g.setMouseCapture(true);
        }
    }

    @Override
    public void exit() {
        g.setMouseCapture(false);
    }

    @Override
    public void update(float dt) {
        if (g.menus.inMenu()) {
            g.menus.update(g, dt);
            handleGlobalKeys();
            return;
        }
        handleGlobalKeys();
        boolean cutscene = g.missions.cutscene.isActive();

        // mundo/streaming
        g.world.update(dt, g.player.pos.x, g.player.pos.z, g.settings.quality > 0);

        if (cutscene) {
            g.missions.cutscene.update(g, dt);
        } else {
            g.player.update(g, dt);
            g.combat.update(g, dt);
            // estatísticas de distância
            if (g.player.state == Player.State.DRIVING && g.player.vehicle != null) {
                g.stats.add("distCarro", Math.abs(g.player.vehicle.forwardSpeed()) * dt);
            } else {
                g.stats.add("distPe", MathX.clamp(Vec3.len(g.player.vel.x, 0, g.player.vel.z), 0, 12) * dt);
            }
        }

        g.vehicles.update(g, dt);
        g.npcs.update(g, dt);
        g.police.update(g, dt);
        g.missions.update(g, dt);
        g.sideActivities.update(g, dt);
        g.randomEvents.update(g, dt);
        g.particles.update(dt);
        g.particles.updateRain(g.world.weather.rain * (g.world.weather.state() == WeatherSystem.State.FOG ? 0.3f : 1f),
                g.renderer.cam.pos.x, g.renderer.cam.pos.y, g.renderer.cam.pos.z,
                g.world.weather.windX, g.world.weather.windZ);
        if (g.world.weather.lightning > 0.7f) {
            g.hud.flashWhite = Math.max(g.hud.flashWhite, g.world.weather.consumeLightning());
        }
        if (g.world.weather.thunderPending()) {
            g.audio.play("EXPLOSION", g.player.pos.x + 40, 20, g.player.pos.z + 40, 0.5f, 0.4f);
        }
        g.hud.update(dt);

        // chuva no áudio
        g.audio.setRain(g.world.weather.rain);

        // rádio ligado dentro de veículo
        boolean inCar = g.player.state == Player.State.DRIVING;
        g.audio.music().setWantPlaying(inCar && g.audio.music().station() >= 0);

        if (!cutscene) {
            g.camera.update(g, dt);
        }
    }

    private void handleGlobalKeys() {
        // (fechar menu é tratado dentro de Menus.update para evitar duplo toggle)
        if (g.input.justPressed(Settings.Action.PAUSE) && !g.menus.inMenu()) {
            g.menus.open(g, ohkt.ui.Menus.Screen.PAUSE);
            g.setMouseCapture(false);
        }
        if (!g.menus.inMenu()) {
            if (g.input.justPressed(Settings.Action.MAP)) g.hud.mapOpen = !g.hud.mapOpen;
            if (g.input.justPressed(Settings.Action.CAMERA)) g.camera.toggleMode();
            if (g.input.justPressed(Settings.Action.DEBUG)) g.settings.debugInfo = !g.settings.debugInfo;
            if (g.input.justPressed(Settings.Action.RADIO_NEXT) && g.player.state == Player.State.DRIVING) {
                int next = (g.audio.music().station() + 2) % 5 - 1; // cicla com "desligado"
                g.audio.setRadio(next);
                g.hud.notify(g.audio.music().stationName());
            }
            if (g.player.state == Player.State.DRIVING && g.player.vehicle != null) {
                if (g.input.isDown(Settings.Action.HORN)) {
                    g.player.vehicle.hornOn = true;
                } else {
                    g.player.vehicle.hornOn = false;
                }
                if (g.input.justPressed(Settings.Action.LIGHTS)) {
                    g.player.vehicle.lightsOn = !g.player.vehicle.lightsOn;
                }
                if (g.player.vehicle.fuel <= 0) {
                    g.hud.setPrompt("Sem gasolina — abasteça num Posto Girassol (E)");
                }
            }
        }
    }

    @Override
    public void renderWorld(Renderer3D r) {
        TimeSystem t = g.world.time;
        WeatherSystem w = g.world.weather;
        float[] sky = t.skyColors();
        int top = (int) sky[0], horizon = (int) sky[1];
        // clima escurece/clareia
        top = ColorUtil.mix(top, 0xff4a4e58, w.cloud * 0.55f);
        horizon = ColorUtil.mix(horizon, 0xff5a5e66, w.cloud * 0.5f);
        if (w.fogDensity > 0.5f) {
            top = ColorUtil.mix(top, 0xff8a8e96, (w.fogDensity - 0.5f) * 0.8f);
            horizon = ColorUtil.mix(horizon, 0xffa0a4aa, (w.fogDensity - 0.5f) * 0.8f);
        }
        float night = t.nightFactor();
        float[] sun = t.sunDirection();
        float far = w.farDistance(g.settings.quality == 0 ? 260 : g.settings.quality == 1 ? 340 : 400);

        r.begin(top, horizon, sun[0], sun[1], sun[2],
                sky[2] * (1 - w.cloud * 0.5f), sky[3] * (1 - w.cloud * 0.5f), sky[4] * (1 - w.cloud * 0.5f),
                sky[5] * (1 - w.cloud * 0.3f), sky[6] * (1 - w.cloud * 0.3f), sky[7] * (1 - w.cloud * 0.3f),
                night, 0.25f + w.fogDensity * 0.75f, far);
        r.cam.near = 0.25f;
        r.cam.far = far;

        // sol / lua
        Vec3 bodyDir = new Vec3(sun[0], sun[1], sun[2]);
        boolean nightB = night > 0.7f;
        Vec3 dir = nightB ? new Vec3(0.35f, 0.75f, -0.4f).norm() : bodyDir.norm();
        r.drawCelestialBody(dir, nightB ? 0xffe8ecf0 : 0xffffe8b0, nightB ? 9 : 12);

        // cidade e entidades
        float detail = g.settings.quality == 0 ? 70 : g.settings.quality == 1 ? 110 : 150;
        g.world.render(r, r.cam.pos.x, r.cam.pos.z, detail, far, g.settings.quality);
        g.vehicles.render(g, r);
        g.npcs.render(g, r);
        g.police.render(g, r);
        g.combat.render(r);
        g.combat.pickups.render(r);

        // jogador
        Player p = g.player;
        if (p.state != Player.State.DRIVING) {
            if (r.sphereVisible(p.pos.x, p.pos.y + 1, p.pos.z, 2)) {
                r.drawShadowBlob(p.pos.x, p.pos.z, 0.55f, 0.7f, ohkt.world.World.groundHeight(p.pos.x, p.pos.z));
                boolean muzzle = p.muzzleTimer > 0;
                HumanoidRenderer.draw(r, p.pos.x, p.pos.y, p.pos.z, p.yaw, p.phase,
                        p.sprinting ? 1f : 0.45f, p.aiming || muzzle, p.crouching,
                        p.state == Player.State.DEAD || p.state == Player.State.BUSTED ? 1 : 0,
                        false, p.attackAnim, p.look);
            }
        }

        // marcadores e partículas
        g.missions.render(g, r);
        g.sideActivities.render(g, r);
        g.randomEvents.render(g, r);
        g.particles.render(r);
    }

    @Override
    public void render2d(Graphics2D gg, int w, int h) {
        g.hud.render(g, gg, w, h);
        g.missions.cutscene.render(gg, w, h);
        if (g.menus.inMenu()) {
            g.menus.render(g, gg, w, h);
        }
    }
}
