package ohkt.save;

import ohkt.engine.Game;
import ohkt.vehicle.VehicleManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Save system real em disco: 3 slots manuais + autosave.
 * Salva posição, dinheiro, armas, veículos, missões, propriedades,
 * hora/clima do mundo, estatísticas e sinaliza autosave para o menu.
 */
public final class SaveSystem {

    public static final int SLOTS = 3;

    public static final class Meta {
        public String label, timestamp;
        public int money, missionCount, day;
        public float hour;

        public String brief() {
            return "R$" + money + " • " + missionCount + " missões • dia " + day + " " + String.format("%02dh", (int) hour);
        }
    }

    private final String dir;

    public SaveSystem(String dataDir) {
        this.dir = dataDir + "/saves";
        new File(dir).mkdirs();
    }

    public File slotFile(int slot) {
        return new File(dir, "save_" + slot + ".sav");
    }

    public boolean anySlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (slotFile(i).exists()) return true;
        }
        return hasAutosave();
    }

    public boolean hasAutosave() {
        return slotFile(99).exists();
    }

    public Meta meta(int slot) {
        File f = slotFile(slot);
        if (!f.exists()) return null;
        Map<String, List<String>> data = parse(f);
        Meta m = new Meta();
        List<String> meta = data.get("meta");
        if (meta != null) {
            for (String line : meta) {
                if (line.startsWith("label=")) m.label = line.substring(6);
                if (line.startsWith("timestamp=")) m.timestamp = line.substring(10);
            }
        }
        List<String> player = data.get("player");
        if (player != null) {
            for (String line : player) {
                if (line.startsWith("money=")) m.money = Integer.parseInt(line.substring(6));
            }
        }
        List<String> world = data.get("world");
        if (world != null) {
            for (String line : world) {
                if (line.startsWith("hour=")) m.hour = Float.parseFloat(line.substring(5));
                if (line.startsWith("day=")) m.day = Integer.parseInt(line.substring(4));
            }
        }
        List<String> missions = data.get("missions");
        if (missions != null) {
            for (String line : missions) {
                if (line.startsWith("completed=")) {
                    String c = line.substring("completed=".length());
                    m.missionCount = c.isEmpty() ? 0 : c.split(",").length;
                }
            }
        }
        return m;
    }

    // ---------------- salvar ----------------

    public void save(Game g, int slot, String label) {
        File f = slotFile(slot);
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            w.println("[meta]");
            w.println("label=" + label);
            w.println("timestamp=" + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date()));
            w.println("[player]");
            ohkt.player.Player p = g.player;
            w.println("x=" + p.pos.x);
            w.println("y=" + p.pos.y);
            w.println("z=" + p.pos.z);
            w.println("yaw=" + p.yaw);
            w.println("health=" + p.health);
            w.println("armor=" + p.armor);
            w.println("money=" + g.economy.money());
            w.println("outfit=" + p.outfitIdx);
            w.println("earned=" + g.economy.totalEarned());
            w.println("spent=" + g.economy.totalSpent());
            StringBuilder owned = new StringBuilder();
            for (int i = 0; i < p.ownedWeapons.length; i++) {
                if (p.ownedWeapons[i]) {
                    if (owned.length() > 0) owned.append(",");
                    owned.append(i).append(":").append(p.magAmmo[i]).append(":").append(p.reserveAmmo[i]);
                }
            }
            w.println("weapons=" + owned);
            w.println("currentWeapon=" + p.currentWeapon);
            w.println("[world]");
            w.println("seed=" + g.world.seed);
            w.println("hour=" + g.world.time.hour);
            w.println("day=" + g.world.time.day);
            w.println("weather=" + g.world.weather.state().name());
            w.println("[missions]");
            w.println("campaignIdx=" + g.missions.campaignIdx);
            w.println("completed=" + String.join(",", g.missions.completed));
            w.println("[properties]");
            w.println("owned=" + String.join(",", g.properties.owned()));
            w.println("[vehicles]");
            for (VehicleManager.Spec spec : g.vehicles.ownedSpecs()) {
                w.println("owned=" + spec.typeId + "," + spec.paint + "," + spec.engineLevel + "," + spec.tireLevel
                        + "," + spec.x + "," + spec.z + "," + spec.yaw);
            }
            w.println("[stats]");
            for (Map.Entry<String, Float> e : g.stats.all().entrySet()) {
                w.println(e.getKey() + "=" + e.getValue());
            }
        } catch (IOException e) {
            g.hud.notify("Falha ao salvar: " + e.getMessage());
        }
    }

    public void autosave(Game g, String label) {
        save(g, 99, label);
    }

    // ---------------- carregar ----------------

    public boolean loadInto(Game g, int slot) {
        File f = slotFile(slot);
        if (!f.exists()) return false;
        Map<String, List<String>> data = parse(f);
        long seed = 1337L;
        for (String line : data.getOrDefault("world", new ArrayList<>())) {
            if (line.startsWith("seed=")) seed = Long.parseLong(afterEq(line));
        }
        g.resetWorldState();
        g.setupWorld(seed);
        ohkt.player.Player p = g.player;
        for (String line : data.getOrDefault("player", new ArrayList<>())) {
            String v = afterEq(line);
            if (line.startsWith("x=")) p.pos.x = Float.parseFloat(v);
            else if (line.startsWith("y=")) p.pos.y = Float.parseFloat(v);
            else if (line.startsWith("z=")) p.pos.z = Float.parseFloat(v);
            else if (line.startsWith("yaw=")) p.yaw = Float.parseFloat(v);
            else if (line.startsWith("health=")) p.health = Float.parseFloat(v);
            else if (line.startsWith("armor=")) p.armor = Float.parseFloat(v);
            else if (line.startsWith("money=")) g.economy.setMoney(Integer.parseInt(v));
            else if (line.startsWith("outfit=")) {
                p.outfitIdx = Integer.parseInt(v);
                p.applyOutfit(ohkt.player.Outfit.CATALOG[Math.min(p.outfitIdx, ohkt.player.Outfit.CATALOG.length - 1)]);
            } else if (line.startsWith("earned=")) g.economy.setTotals(Integer.parseInt(v), 0);
            else if (line.startsWith("spent=")) g.economy.setTotals(g.economy.totalEarned(), Integer.parseInt(v));
            else if (line.startsWith("weapons=")) {
                for (int i = 0; i < p.ownedWeapons.length; i++) {
                    p.ownedWeapons[i] = false;
                    p.magAmmo[i] = 0;
                    p.reserveAmmo[i] = 0;
                }
                p.ownedWeapons[0] = true;
                if (!v.isEmpty()) {
                    for (String part : v.split(",")) {
                        String[] seg = part.split(":");
                        int id = Integer.parseInt(seg[0]);
                        p.ownedWeapons[id] = true;
                        p.magAmmo[id] = Integer.parseInt(seg[1]);
                        p.reserveAmmo[id] = Integer.parseInt(seg[2]);
                    }
                }
            } else if (line.startsWith("currentWeapon=")) p.currentWeapon = Integer.parseInt(v);
        }
        for (String line : data.getOrDefault("world", new ArrayList<>())) {
            String v = afterEq(line);
            if (line.startsWith("hour=")) g.world.time.hour = Float.parseFloat(v);
            else if (line.startsWith("day=")) g.world.time.day = Integer.parseInt(v);
            else if (line.startsWith("weather=")) {
                try {
                    g.world.weather.forceState(ohkt.world.WeatherSystem.State.valueOf(v));
                } catch (Exception ignored) {
                }
            }
        }
        for (String line : data.getOrDefault("missions", new ArrayList<>())) {
            String v = afterEq(line);
            if (line.startsWith("campaignIdx=")) g.missions.campaignIdx = Integer.parseInt(v);
            else if (line.startsWith("completed=")) {
                g.missions.completed.clear();
                if (!v.isEmpty()) {
                    for (String id : v.split(",")) g.missions.completed.add(id);
                }
            }
        }
        for (String line : data.getOrDefault("properties", new ArrayList<>())) {
            if (line.startsWith("owned=")) {
                String v = afterEq(line);
                if (!v.isEmpty()) {
                    for (String id : v.split(",")) g.properties.buy(id);
                }
            }
        }
        List<VehicleManager.Spec> specs = new ArrayList<>();
        for (String line : data.getOrDefault("vehicles", new ArrayList<>())) {
            if (line.startsWith("owned=")) {
                String[] seg = afterEq(line).split(",");
                if (seg.length >= 7) {
                    specs.add(new VehicleManager.Spec(seg[0], Integer.parseInt(seg[1]),
                            Integer.parseInt(seg[2]), Integer.parseInt(seg[3]),
                            Float.parseFloat(seg[4]), Float.parseFloat(seg[5]), Float.parseFloat(seg[6])));
                }
            }
        }
        if (!specs.isEmpty()) g.vehicles.restoreOwned(specs);
        Map<String, Float> stats = new LinkedHashMap<>();
        for (String line : data.getOrDefault("stats", new ArrayList<>())) {
            if (line.contains("=")) {
                stats.put(line.substring(0, line.indexOf('=')), Float.parseFloat(afterEq(line)));
            }
        }
        g.stats.load(stats);
        p.state = ohkt.player.Player.State.ON_FOOT;
        return true;
    }

    private static String afterEq(String line) {
        int i = line.indexOf('=');
        return i >= 0 ? line.substring(i + 1) : "";
    }

    private Map<String, List<String>> parse(File f) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (Scanner sc = new Scanner(f, "UTF-8")) {
            String section = "";
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1);
                    out.computeIfAbsent(section, k -> new ArrayList<>());
                    continue;
                }
                out.computeIfAbsent(section, k -> new ArrayList<>()).add(line);
            }
        } catch (IOException e) {
            // arquivo corrompido: ignora
        }
        return out;
    }
}
