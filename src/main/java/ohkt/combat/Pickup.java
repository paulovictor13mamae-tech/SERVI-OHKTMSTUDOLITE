package ohkt.combat;

import ohkt.engine.Game;
import ohkt.graphics.Renderer3D;
import ohkt.utils.ColorUtil;
import ohkt.utils.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Itens coletáveis no mundo (dinheiro, vida, colete, munição). */
public final class Pickup {

    public enum Type { CASH, HEALTH, ARMOR, AMMO_LEVE, AMMO_PESADA, AMMO_RIFLE }

    public final Type type;
    public final int amount;
    public final float x, z;
    public float life = 45;
    public float spin;

    public Pickup(Type type, int amount, float x, float z) {
        this.type = type;
        this.amount = amount;
        this.x = x;
        this.z = z;
    }

    public static final class Manager {
        private final List<Pickup> list = new ArrayList<>();

        public void spawn(Type type, int amount, float x, float z) {
            if (list.size() > 60) list.remove(0);
            list.add(new Pickup(type, amount, x, z));
        }

        public void update(Game g, float dt) {
            Iterator<Pickup> it = list.iterator();
            while (it.hasNext()) {
                Pickup p = it.next();
                p.life -= dt;
                p.spin += dt * 3f;
                if (p.life <= 0) {
                    it.remove();
                    continue;
                }
                float dx = p.x - g.player.pos.x, dz = p.z - g.player.pos.z;
                if (dx * dx + dz * dz < 1.44f && Math.abs(g.player.pos.y - 0) < 3) {
                    apply(g, p);
                    it.remove();
                }
            }
        }

        private void apply(Game g, Pickup p) {
            switch (p.type) {
                case CASH:
                    g.economy.earn(p.amount, "achado");
                    g.particles.moneyPickupFx(p.x, 1, p.z);
                    g.audio.play("CASH", p.x, 1, p.z, 0.5f, 1f);
                    break;
                case HEALTH:
                    g.player.health = Math.min(100, g.player.health + p.amount);
                    g.audio.play("PICKUP", p.x, 1, p.z, 0.5f, 1.2f);
                    break;
                case ARMOR:
                    g.player.armor = Math.min(100, g.player.armor + p.amount);
                    g.audio.play("PICKUP", p.x, 1, p.z, 0.5f, 0.9f);
                    break;
                case AMMO_LEVE:
                    addAmmo(g, 2, p.amount);
                    addAmmo(g, 4, p.amount);
                    break;
                case AMMO_PESADA:
                    addAmmo(g, 3, p.amount);
                    addAmmo(g, 5, p.amount);
                    break;
                case AMMO_RIFLE:
                    addAmmo(g, 6, p.amount);
                    break;
            }
        }

        private void addAmmo(Game g, int weaponId, int amount) {
            if (g.player.ownedWeapons[weaponId]) {
                g.player.reserveAmmo[weaponId] += amount;
                g.audio.play("PICKUP", g.player.pos.x, 1, g.player.pos.z, 0.4f, 1.1f);
            }
        }

        public void render(Renderer3D r) {
            for (Pickup p : list) {
                if (!r.sphereVisible(p.x, 1, p.z, 1.5f)) continue;
                int col = colorOf(p.type);
                float bob = (float) Math.sin(p.spin) * 0.08f;
                r.drawBox(p.x, 0.5f + bob, p.z, 0.22f, 0.22f, 0.22f, p.spin, p.spin * 0.7f, 0, col, true);
                r.drawSprite(p.x, 0.5f + bob, p.z, 0.9f, ColorUtil.withAlpha(col, 70), 2);
            }
        }

        private static int colorOf(Type t) {
            switch (t) {
                case CASH: return 0xff40d060;
                case HEALTH: return 0xffff4050;
                case ARMOR: return 0xff4090ff;
                case AMMO_LEVE: return 0xffc8a840;
                case AMMO_PESADA: return 0xffc06030;
                default: return 0xffa0a0b0;
            }
        }
    }
}
