package ohkt.engine;

import ohkt.graphics.Renderer3D;

import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

/** Gerenciador de cenas com troca e pilha (pause sobrepoe jogo). */
public final class SceneManager {

    private final Map<String, Scene> scenes = new HashMap<>();
    private Scene current;

    public void register(String id, Scene s) {
        scenes.put(id, s);
    }

    public void switchTo(String id) {
        if (current != null) current.exit();
        current = scenes.get(id);
        if (current != null) current.enter();
    }

    public Scene current() { return current; }

    public Scene byId(String id) { return scenes.get(id); }

    public void update(float dt) {
        if (current != null) current.update(dt);
    }

    public void renderWorld(Renderer3D r) {
        if (current != null) current.renderWorld(r);
    }

    public void render2d(Graphics2D g, int w, int h) {
        if (current != null) current.render2d(g, w, h);
    }
}
