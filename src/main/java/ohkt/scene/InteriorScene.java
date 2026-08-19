package ohkt.scene;

import ohkt.engine.Game;
import ohkt.engine.Settings;
import ohkt.graphics.Renderer3D;
import ohkt.player.HumanoidRenderer;
import ohkt.player.Player;
import ohkt.utils.MathX;
import ohkt.utils.Vec3;
import ohkt.world.InteriorManager;

import java.awt.Graphics2D;

/**
 * Interior de lojas/casas: movimento confinado ao cômodo, balconista,
 * compra/salvamento e retorno ao mundo sem tela de carregamento.
 */
public final class InteriorScene implements ohkt.engine.Scene {

    private final Game g;
    private float camYaw, camPitch;

    public InteriorScene(Game g) {
        this.g = g;
    }

    @Override
    public String name() { return "interior"; }

    @Override
    public void enter() {
        g.setMouseCapture(true);
        if (g.player != null) {
            // entra no cômodo (coordenadas locais, porta ao sul)
            g.player.pos.set(0, 0.02f, 3.4f);
            g.player.vel.set(0, 0, 0);
            g.player.yaw = (float) Math.PI; // olhando para dentro
            camYaw = (float) Math.PI;
        }
        camPitch = 0;
    }

    @Override
    public void exit() {
        g.setMouseCapture(false);
    }

    @Override
    public void update(float dt) {
        InteriorManager.Active act = g.interior.active;
        Player p = g.player;
        if (act == null) {
            exitToWorld();
            return;
        }

        if (g.menus.inMenu()) {
            g.menus.update(g, dt);
            return;
        }
        if (g.input.justPressed(Settings.Action.PAUSE)) {
            g.menus.open(g, ohkt.ui.Menus.Screen.PAUSE);
            return;
        }

        // câmera (primeira/terceira pessoa simples)
        camYaw -= g.input.mouseDX * 0.0028f * g.settings.mouseSensitivity;
        camPitch += (g.settings.invertY ? -g.input.mouseDY : g.input.mouseDY) * 0.0022f * g.settings.mouseSensitivity;
        camPitch = MathX.clamp(camPitch, -1.1f, 1.3f);

        // movimento confinado
        float ix = axis(Settings.Action.RIGHT, Settings.Action.LEFT);
        float iz = axis(Settings.Action.FORWARD, Settings.Action.BACK);
        float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
        float rx = (float) Math.cos(camYaw), rz = (float) Math.sin(camYaw);
        float speed = 4f;
        float mvx = (fx * iz + rx * ix) * speed;
        float mvz = (fz * iz + rz * ix) * speed;
        p.pos.x += mvx * dt;
        p.pos.z += mvz * dt;
        p.pos.y = 0.02f;
        float ml = Vec3.len(mvx, 0, mvz);
        if (ml > 0.3f) {
            p.yaw += MathX.angleDiff(p.yaw, (float) Math.atan2(mvx, -mvz)) * Math.min(1, dt * 12f);
            p.phase += ml * dt * 2.4f;
        }
        // colisão com paredes/balcão
        ohkt.physics.PhysicsWorld.Position pp = new ohkt.physics.PhysicsWorld.Position(p.pos.x, p.pos.z);
        act.physics.resolveCircle(pp, 0.38f, 0.1f, 1.7f);
        p.pos.x = pp.x;
        p.pos.z = pp.z;

        // saída
        float dExit = Vec3.len(p.pos.x - act.exitPos[0], 0, p.pos.z - act.exitPos[1]);
        String prompt = dExit < 2.5f ? "E — Sair" : null;
        boolean safehouse = act.def.safehouse;
        float dKeepper = Vec3.len(p.pos.x - act.shopkeeper[0], 0, p.pos.z - act.shopkeeper[1]);
        if (dKeepper < 2.2f) {
            if (act.def.heals && !act.def.safehouse && !act.def.type.equals("CASA")) {
                prompt = "E — Falar com o balconista";
            } else if (safehouse) {
                prompt = "E — Dormir e salvar";
            }
        }
        if (prompt != null) {
            g.hud.setPrompt(prompt);
        }
        if (g.input.justPressed(Settings.Action.INTERACT) || g.input.justPressed(Settings.Action.ENTER_EXIT)) {
            if (dExit < 2.5f) {
                exitToWorld();
            } else if (dKeepper < 2.2f) {
                if (safehouse) {
                    // dormir: salva, cura, avança para 8h
                    g.player.health = 100;
                    g.world.time.hour = 8f;
                    g.world.time.day++;
                    g.saveSystem.autosave(g, "dormiu");
                    g.hud.notify("Você dormiu. Jogo salvo.");
                    exitToWorld();
                } else if (act.def.type.equals("HOSPITAL")) {
                    g.openShop("HOSPITAL");
                } else if (act.def.type.equals("DELEGACIA")) {
                    g.openShop("DELEGACIA");
                } else {
                    g.openShop(act.def.type);
                }
            }
        }
        g.hud.update(dt);
        g.world.time.update(dt);
        g.camera.yaw = camYaw;
        g.camera.pitch = camPitch;
    }

    private void exitToWorld() {
        InteriorManager.Active act = g.interior.active;
        if (act != null) {
            g.player.pos.set(act.returnPos[0], 0.2f, act.returnPos[1]);
        }
        g.interior.exit();
        g.scenes.switchTo("game");
    }

    private float axis(Settings.Action posA, Settings.Action negA) {
        float v = 0;
        if (g.input.isDown(posA)) v += 1;
        if (g.input.isDown(negA)) v -= 1;
        return MathX.clamp(v, -1, 1);
    }

    @Override
    public void renderWorld(Renderer3D r) {
        InteriorManager.Active act = g.interior.active;
        if (act == null) return;
        TimeSystemLight light = new TimeSystemLight();
        r.begin(0xff2a2c34, 0xff2a2c34, 0.2f, 0.8f, 0.2f, 0.85f, 0.82f, 0.78f, 0.5f, 0.5f, 0.55f, 0f, 0.1f, 60f);
        // câmera atrás do jogador
        float fx = (float) Math.sin(camYaw), fz = (float) -Math.cos(camYaw);
        float dist = 4f;
        float cy = 1.9f + camPitch * -dist * 0.3f;
        r.cam.pos.set(g.player.pos.x - fx * dist, cy, g.player.pos.z - fz * dist);
        r.cam.yaw = camYaw;
        r.cam.pitch = camPitch * 0.4f;
        r.drawMesh(act.mesh, 0, 0, 0, 0, 1);
        // balconista
        HumanoidRenderer.draw(r, act.shopkeeper[0], 0, act.shopkeeper[1], (float) Math.PI, 0, 0, false, false, 0, false, 0, merchantLook(act));
        // jogador
        HumanoidRenderer.draw(r, g.player.pos.x, g.player.pos.y, g.player.pos.z, g.player.yaw, g.player.phase,
                0.4f, false, false, 0, false, 0, g.player.look);
    }

    private HumanoidRenderer.Look merchantLook(InteriorManager.Active act) {
        HumanoidRenderer.Look look = new HumanoidRenderer.Look();
        look.set(0xffc8a080, 0xffe8e0d0, 0xff3a3a44, 0xff30241c, 0xff2a2018);
        switch (act.def.type) {
            case "ARMERIA": look.shirt = 0xff4a4a52; break;
            case "HOSPITAL": look.shirt = 0xffffffff; break;
            case "DELEGACIA": look.shirt = 0xff2a4a8a; break;
            case "CONCESSIONARIA": look.shirt = 0xff2080a0; break;
            case "IMOBILIARIA": look.shirt = 0xff20a080; break;
            default: look.shirt = 0xffc8a040;
        }
        return look;
    }

    @Override
    public void render2d(Graphics2D gg, int w, int h) {
        g.hud.render(g, gg, w, h);
        if (g.menus.inMenu()) {
            g.menus.render(g, gg, w, h);
        }
    }

    private static final class TimeSystemLight {
    }
}
