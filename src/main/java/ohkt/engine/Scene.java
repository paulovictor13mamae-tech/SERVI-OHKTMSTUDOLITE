package ohkt.engine;

import ohkt.graphics.Renderer3D;

import java.awt.Graphics2D;

/** Cena do jogo (menu, mundo, interior). */
public interface Scene {
    void enter();

    void exit();

    void update(float dt);

    /** Render 3D do mundo da cena. */
    void renderWorld(Renderer3D r);

    /** HUD / UI 2D por cima. */
    void render2d(Graphics2D g, int w, int h);

    /** Nome para debug. */
    String name();
}
