package ohkt.player;

/** Roupas/visual do jogador (compraveis no brecho). */
public final class Outfit {
    public final String name;
    public final int shirt, pants, shoes;
    public final int price;

    public Outfit(String name, int shirt, int pants, int shoes, int price) {
        this.name = name;
        this.shirt = shirt;
        this.pants = pants;
        this.shoes = shoes;
        this.price = price;
    }

    public static final Outfit[] CATALOG = {
            new Outfit("Básico", 0xffd0d0d0, 0xff3a3a44, 0xff30241c, 0),
            new Outfit("Operário", 0xffe8a020, 0xff4a5a6a, 0xff3a2a1a, 120),
            new Outfit("Praiano", 0xff30c0a0, 0xffe8d8b0, 0xfff0f0f0, 180),
            new Outfit("Noturno", 0xff28282e, 0xff181820, 0xff101014, 240),
            new Outfit("Farpado", 0xffa02828, 0xff2a2a30, 0xff1a1a1e, 320),
            new Outfit("Executivo", 0xff28324a, 0xff22262e, 0xff14100c, 600),
    };
}
