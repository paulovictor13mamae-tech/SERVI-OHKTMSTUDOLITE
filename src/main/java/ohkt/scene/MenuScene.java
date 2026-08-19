package ohkt.scene;

import ohkt.engine.Game;
import ohkt.graphics.Renderer3D;
import ohkt.utils.ColorUtil;
import ohkt.utils.MathX;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Menu principal: câmera orbitando a cidade (se o mundo existir) com o
 * menu por cima; senão, painel escuro estilizado.
 */
public final class MenuScene implements ohkt.engine.Scene {

    private final Game g;
    private float orbit;

    public MenuScene(Game g) {
        this.g = g;
    }

    @Override
    public String name() { return "menu"; }

    @Override
    public void enter() {
        g.menus.open(g, ohkt.ui.Menus.Screen.MAIN);
        g.setMouseCapture(false);
    }

    @Override
    public void exit() {
    }

    @Override
    public void update(float dt) {
        orbit += dt * 0.06f;
        if (g.world != null && g.isWorldReady()) {
            g.world.update(dt, 0, 0, false); // mantém chunks perto do centro
            g.hud.update(dt);
        }
        g.menus.update(g, dt);
    }

    @Override
    public void renderWorld(Renderer3D r) {
        if (g.world != null && g.isWorldReady()) {
            ohkt.world.TimeSystem t = g.world.time;
            float[] sky = t.skyColors();
            float[] sun = t.sunDirection();
            float far = g.world.weather.farDistance(360);
            r.begin((int) sky[0], (int) sky[1], sun[0], sun[1], sun[2],
                    sky[2], sky[3], sky[4], sky[5], sky[6], sky[7],
                    t.nightFactor(), 0.4f + g.world.weather.fogDensity * 0.6f, far);
            // órbita sobre a Praça Central
            float cx = ohkt.world.CityLayout.roadCoord(13);
            float cz = ohkt.world.CityLayout.roadCoord(13);
            r.cam.pos.set(cx + (float) Math.cos(orbit) * 90, 46 + (float) Math.sin(orbit * 0.7f) * 10,
                    cz + (float) Math.sin(orbit) * 90);
            r.cam.yaw = (float) Math.atan2(cx - r.cam.pos.x, -(cz - r.cam.pos.z));
            r.cam.pitch = -0.30f;
            g.world.render(r, r.cam.pos.x, r.cam.pos.z, 120, far, 1);
            return;
        }
        // fundo estilizado sem mundo
        r.begin(0xff141826, 0xff2c3448, 0.3f, 0.7f, 0.2f, 0.6f, 0.6f, 0.7f, 0.4f, 0.42f, 0.5f, 0.3f, 0.5f, 200);
        for (int i = 0; i < 40; i++) {
            float x = -60 + i * 3.1f;
            float h = 8 + MathX.hash(i) % 30;
            r.drawBox(x, h / 2, -40 + (i % 5) * 12, 2.2f, h / 2, 2.2f, i * 0.13f, 0, 0,
                    ColorUtil.mix(0xff26304a, 0xff3c4868, (i % 7) / 7f), false);
        }
    }

    @Override
    public void render2d(Graphics2D gg, int w, int h) {
        gg.setColor(new Color(0f, 0f, 0f, 0.35f));
        gg.fillRect(0, 0, w, h);
        if (g.menus.inMenu()) {
            g.menus.render(g, gg, w, h);
        }
    }
}
