package ohkt.combat;

/** Catálogo de armas fictícias de Porto Aurora. */
public final class Weapon {

    public enum Kind { MELEE, GUN }

    public final int id;
    public final String name;
    public final Kind kind;
    public final float damage;
    public final float rate;          // segundos entre tiros/golpes
    public final int magSize;
    public final float range;
    public final float spread;        // radianos
    public final float recoil;
    public final boolean auto;
    public final int pellets;
    public final float reloadTime;
    public final String ammoType;     // null / LEVE / PESADA / RIFLE
    public final int price;           // preço de compra da arma
    public final int ammoPrice;       // preço por pacote de munição
    public final int ammoPack;        // munição por pacote

    Weapon(int id, String name, Kind kind, float damage, float rate, int magSize, float range,
           float spread, float recoil, boolean auto, int pellets, float reloadTime,
           String ammoType, int price, int ammoPrice, int ammoPack) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.damage = damage;
        this.rate = rate;
        this.magSize = magSize;
        this.range = range;
        this.spread = spread;
        this.recoil = recoil;
        this.auto = auto;
        this.pellets = pellets;
        this.reloadTime = reloadTime;
        this.ammoType = ammoType;
        this.price = price;
        this.ammoPrice = ammoPrice;
        this.ammoPack = ammoPack;
    }

    public static final Weapon[] CATALOG = {
            new Weapon(0, "Punhos", Kind.MELEE, 8, 0.42f, 0, 1.9f, 0, 0, false, 1, 0, null, 0, 0, 0),
            new Weapon(1, "Punhal Treste", Kind.MELEE, 22, 0.45f, 0, 2.1f, 0, 0, false, 1, 0, null, 180, 0, 0),
            new Weapon(2, "GP-9", Kind.GUN, 16, 0.24f, 15, 90f, 0.012f, 0.35f, false, 1, 1.5f, "LEVE", 380, 30, 40),
            new Weapon(3, "Tufão .44", Kind.GUN, 38, 0.68f, 6, 110f, 0.014f, 0.9f, false, 1, 2.6f, "PESADA", 950, 60, 18),
            new Weapon(4, "Vespa K", Kind.GUN, 11, 0.082f, 30, 80f, 0.035f, 0.16f, true, 1, 1.9f, "LEVE", 1350, 40, 90),
            new Weapon(5, "Bruta-12", Kind.GUN, 9, 0.85f, 6, 32f, 0.075f, 1.3f, false, 8, 2.8f, "PESADA", 1750, 80, 16),
            new Weapon(6, "Condor AR", Kind.GUN, 24, 0.115f, 30, 130f, 0.02f, 0.45f, true, 1, 2.1f, "RIFLE", 2700, 90, 60),
    };

    public static Weapon byId(int id) {
        return CATALOG[Math.max(0, Math.min(id, CATALOG.length - 1))];
    }
}
