package ohkt.economy;

import ohkt.combat.Weapon;
import ohkt.engine.Game;
import ohkt.player.Outfit;
import ohkt.vehicle.Vehicle;
import ohkt.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lojas e serviços: roupas, armas, comida, hospital, concessionária,
 * oficina, posto e imobiliária. Os menus compram via Shop.buy().
 */
public final class Shop {

    public static final class Item {
        public final String id, label;
        public final int price;

        public Item(String id, String label, int price) {
            this.id = id;
            this.label = label;
            this.price = price;
        }
    }

    /** Título da loja pelo tipo. */
    public static String title(String type) {
        switch (type) {
            case "ROUPAS": return "Brechó da Duda";
            case "ARMERIA": return "Casa do Ferreiro";
            case "LANCHONETE": case "CAFE": return "Forno de Ouro";
            case "HOSPITAL": return "Hospital Santa Clara";
            case "CONCESSIONARIA": return "AutoVaurora";
            case "OFICINA": return "Oficina do Nino";
            case "POSTO": return "Posto Girassol";
            case "IMOBILIARIA": return "Chaves & Filhos";
            default: return "Loja";
        }
    }

    /** Itens disponíveis (preços podem depender do estado do jogador). */
    public static List<Item> itemsFor(Game g, String type) {
        List<Item> out = new ArrayList<>();
        switch (type) {
            case "ROUPAS":
                for (Outfit o : Outfit.CATALOG) {
                    out.add(new Item("outfit" + o.name, o.name, o.price));
                }
                break;
            case "ARMERIA":
                for (int i = 1; i < Weapon.CATALOG.length; i++) {
                    Weapon w = Weapon.CATALOG[i];
                    if (!g.player.ownedWeapons[i]) {
                        out.add(new Item("w" + w.id, w.name + " (arma)", w.price));
                    } else if (w.kind == Weapon.Kind.GUN) {
                        out.add(new Item("a" + w.id, "Munição " + w.name + " x" + w.ammoPack, w.ammoPrice));
                    }
                }
                out.add(new Item("armor", "Colete +50", 250));
                break;
            case "LANCHONETE":
            case "CAFE":
                out.add(new Item("food1", "Pão na chapa + café (vida+25)", 18));
                out.add(new Item("food2", "Prato do dia (vida+60)", 45));
                out.add(new Item("food3", "Refeição completa (vida+100)", 80));
                break;
            case "HOSPITAL":
                out.add(new Item("heal", "Tratamento completo", 120));
                out.add(new Item("armor", "Colete médico", 200));
                break;
            case "CONCESSIONARIA":
                for (VehicleType t : VehicleType.CATALOG) {
                    if (t.price > 0) {
                        out.add(new Item("v" + t.id, t.name + " — " + t.kind.toLowerCase(), t.price));
                    }
                }
                break;
            case "OFICINA": {
                Vehicle v = g.player.vehicle;
                if (v == null) {
                    out.add(new Item("none", "Estacione o veículo na oficina", 0));
                    break;
                }
                int dmg = (int) Math.max(0, 100 - v.health);
                out.add(new Item("repair", "Reparo (" + dmg + "% de dano)", 40 + dmg * 3));
                out.add(new Item("paint", "Nova pintura", 220));
                out.add(new Item("engine", "Motor nível " + (v.engineLevel + 1) + " (+15% força)", 400 * (v.engineLevel + 1)));
                out.add(new Item("tires", "Pneus nível " + (v.tireLevel + 1) + " (+aderência)", 300 * (v.tireLevel + 1)));
                break;
            }
            case "POSTO": {
                Vehicle v = g.player.vehicle;
                if (v == null) {
                    out.add(new Item("none", "Encoste um veículo na bomba", 0));
                    break;
                }
                int missing = Math.round(v.type.fuelCap - v.fuel);
                out.add(new Item("fuel", "Tanque cheio (" + missing + " L)", Math.max(0, missing * 3)));
                out.add(new Item("snack", "Conveniência (vida+20)", 12));
                break;
            }
            case "IMOBILIARIA":
                for (Properties.Def d : Properties.CATALOG) {
                    boolean owned = g.properties.own(d.id);
                    out.add(new Item("p" + d.id, d.name + " — renda R$" + d.dailyIncome + "/dia",
                            owned ? -1 : d.price));
                }
                break;
            case "DELEGACIA": {
                int stars = g.police.wantedSystem.stars;
                if (stars > 0) {
                    out.add(new Item("bribe", "Pagar fiança (" + stars + " estrelas)", stars * 250));
                } else {
                    out.add(new Item("none", "Você não é procurado. Boa noite.", 0));
                }
                break;
            }
            default:
                out.add(new Item("none", "Nada à venda", 0));
        }
        return out;
    }

    /** Aplica compra. Retorna mensagem de resultado. */
    public static String buy(Game g, String type, Item item) {
        Economy eco = g.economy;
        if (item.price < 0) return "Já é sua propriedade.";
        if (item.price == 0 && !item.id.equals("none")) return "Grátis!";
        switch (item.id) {
            case "none":
                return "—";
            case "food1":
                if (!eco.spend(g, item.price, "comida")) return "Sem dinheiro.";
                g.player.health = Math.min(100, g.player.health + 25);
                return "Delícia! Vida restaurada.";
            case "food2":
                if (!eco.spend(g, item.price, "comida")) return "Sem dinheiro.";
                g.player.health = Math.min(100, g.player.health + 60);
                return "Prato do dia! Ótimo.";
            case "food3":
                if (!eco.spend(g, item.price, "comida")) return "Sem dinheiro.";
                g.player.health = 100;
                return "Barriga cheia, vida cheia.";
            case "snack":
                if (!eco.spend(g, item.price, "snack")) return "Sem dinheiro.";
                g.player.health = Math.min(100, g.player.health + 20);
                return "Salgadinho da hora.";
            case "heal":
                if (!eco.spend(g, item.price, "hospital")) return "Sem dinheiro.";
                g.player.health = 100;
                return "Alta hospitalar imediata!";
            case "armor":
                if (!eco.spend(g, item.price, "colete")) return "Sem dinheiro.";
                g.player.armor = Math.min(100, g.player.armor + 50);
                return "Colete equipado.";
            case "repair": {
                Vehicle v = g.player.vehicle;
                if (v == null) return "Sem veículo.";
                if (!eco.spend(g, item.price, "reparo")) return "Sem dinheiro.";
                v.health = 100;
                v.burning = false;
                return "Como novo!";
            }
            case "paint": {
                Vehicle v = g.player.vehicle;
                if (v == null) return "Sem veículo.";
                if (!eco.spend(g, item.price, "pintura")) return "Sem dinheiro.";
                int[] pal = {0xffc02020, 0xff2050c0, 0xffe8e8e8, 0xff18181c, 0xff30a040, 0xffd8b028, 0xff7030a0, 0xff20c0c0};
                java.util.Random r = new java.util.Random();
                v.paint = pal[r.nextInt(pal.length)];
                return "Pintura nova aplicada!";
            }
            case "engine": {
                Vehicle v = g.player.vehicle;
                if (v == null || v.engineLevel >= 3) return "Não é possível.";
                if (!eco.spend(g, item.price, "motor")) return "Sem dinheiro.";
                v.engineLevel++;
                return "Motor turbinado (nível " + v.engineLevel + ")!";
            }
            case "tires": {
                Vehicle v = g.player.vehicle;
                if (v == null || v.tireLevel >= 3) return "Não é possível.";
                if (!eco.spend(g, item.price, "pneus")) return "Sem dinheiro.";
                v.tireLevel++;
                return "Pneus esportivos (nível " + v.tireLevel + ")!";
            }
            case "fuel": {
                Vehicle v = g.player.vehicle;
                if (v == null) return "Sem veículo.";
                if (item.price <= 0) {
                    v.fuel = v.type.fuelCap;
                    return "Tanque já está cheio.";
                }
                if (!eco.spend(g, item.price, "gasolina")) return "Sem dinheiro.";
                v.fuel = v.type.fuelCap;
                return "Tanque cheio!";
            }
            default:
                break;
        }
        // roupas
        if (item.id.startsWith("outfit")) {
            String name = item.id.substring("outfit".length());
            for (Outfit o : Outfit.CATALOG) {
                if (o.name.equals(name)) {
                    if (!eco.spend(g, item.price, "roupa")) return "Sem dinheiro.";
                    g.player.applyOutfit(o);
                    g.player.outfitIdx = java.util.Arrays.asList(Outfit.CATALOG).indexOf(o);
                    return "Novo visual: " + o.name;
                }
            }
        }
        // armas
        if (item.id.startsWith("w")) {
            int id = Integer.parseInt(item.id.substring(1));
            Weapon w = Weapon.byId(id);
            if (!eco.spend(g, item.price, "arma")) return "Sem dinheiro.";
            g.player.giveWeapon(id, w.ammoPack * 2);
            g.player.requestWeapon(id);
            return w.name + " adquirida!";
        }
        if (item.id.startsWith("a")) {
            int id = Integer.parseInt(item.id.substring(1));
            Weapon w = Weapon.byId(id);
            if (!eco.spend(g, item.price, "munição")) return "Sem dinheiro.";
            g.player.reserveAmmo[id] += w.ammoPack;
            return "+" + w.ammoPack + " munição de " + w.name;
        }
        // veículos
        if (item.id.startsWith("v")) {
            String vid = item.id.substring(1);
            VehicleType t = VehicleType.byId(vid);
            if (!eco.spend(g, t.price, "veículo")) return "Sem dinheiro.";
            g.vehicles.deliverPurchased(g, t, t.defaultPaint);
            return t.name + " esperando na concessionária!";
        }
        // propriedades
        if (item.id.startsWith("p")) {
            String pid = item.id.substring(1);
            Properties.Def d = Properties.defOf(pid);
            if (d == null || g.properties.own(pid)) return "Indisponível.";
            if (!eco.spend(g, d.price, "propriedade")) return "Sem dinheiro.";
            g.properties.buy(pid);
            g.hud.notify("Propriedade adquirida: " + d.name + " (renda diária R$" + d.dailyIncome + ")");
            return "Parabéns! " + d.name + " é sua.";
        }
        return "Algo deu errado.";
    }
}
