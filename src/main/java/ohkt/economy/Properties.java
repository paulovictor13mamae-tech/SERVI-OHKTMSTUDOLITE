package ohkt.economy;

import ohkt.world.CityLayout;

import java.util.LinkedHashSet;
import java.util.Set;

/** Propriedades compráveis (safehouses com renda). */
public final class Properties {

    private final Set<String> owned = new LinkedHashSet<>();

    public static final class Def {
        public final String id, name, interiorId;
        public final int price, dailyIncome;
        public final CityLayout.Special block;

        Def(String id, String name, String interiorId, int price, int income, CityLayout.Special block) {
            this.id = id;
            this.name = name;
            this.interiorId = interiorId;
            this.price = price;
            this.dailyIncome = income;
            this.block = block;
        }
    }

    public static final Def[] CATALOG = {
            new Def("apartamento", "Apartamento Beira-Mar", "APARTAMENTO", 8000, 100, CityLayout.Special.APARTAMENTO),
            new Def("cobertura", "Cobertura Vaurora", "COBERTURA", 25000, 300, CityLayout.Special.COBERTURA),
            new Def("galpao", "Galpão do Porto", "GALPAO_CASA", 15000, 200, CityLayout.Special.GALPAO_CASA),
    };

    public static Def defOf(String id) {
        for (Def d : CATALOG) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }

    public static int incomeOf(String id) {
        Def d = defOf(id);
        return d == null ? 0 : d.dailyIncome;
    }

    public boolean own(String id) { return owned.contains(id); }

    public void buy(String id) { owned.add(id); }

    public Set<String> owned() { return owned; }

    public void clear() { owned.clear(); }
}
