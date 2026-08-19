package ohkt.engine;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Configuracoes persistentes (video, audio, controles).
 * Formato INI simples em gamedata/settings.ini.
 */
public final class Settings {

    // video
    public float renderScale = 0.66f;
    public int quality = 1;          // 0=baixo 1=medio 2=alto
    public float fov = 70f;
    public int fpsCap = 60;          // 0 = sem limite
    public boolean showFps = false;
    public boolean fullScreen = false;
    public boolean debugInfo = false;

    // audio
    public float volMaster = 0.9f;
    public float volMusic = 0.7f;
    public float volSfx = 0.9f;

    // gameplay
    public float mouseSensitivity = 1f;
    public boolean invertY = false;
    public boolean useGamepad = true;
    public boolean showMinimap = true;

    // rede (servidor local de estatisticas)
    public boolean localStatsServer = false;
    public int statsServerPort = 8123;

    public String dataDir = "gamedata";
    private final Map<String, String> keyBinds = new LinkedHashMap<>();

    /** Acoes de entrada com teclas padrao (reconfiguraveis). */
    public enum Action {
        FORWARD("W"), BACK("S"), LEFT("A"), RIGHT("D"),
        SPRINT("SHIFT"), JUMP("SPACE"), CROUCH("CTRL"),
        INTERACT("E"), ENTER_EXIT("F"), FIRE("MOUSE1"), AIM("MOUSE2"),
        RELOAD("R"), WEAPON_NEXT("WHEEL_UP"), WEAPON_PREV("WHEEL_DOWN"),
        WEAPON_1("1"), WEAPON_2("2"), WEAPON_3("3"), WEAPON_4("4"), WEAPON_5("5"), WEAPON_6("6"), WEAPON_7("7"),
        CAMERA("C"), HORN("H"), LIGHTS("L"), HANDBRAKE("SPACE"),
        PAUSE("ESC"), MAP("M"), RADIO_NEXT("B"), DEBUG("F3");

        public String def;
        Action(String def) { this.def = def; }
    }

    public Settings() {
        for (Action a : Action.values()) keyBinds.put(a.name(), a.def);
    }

    public String bind(Action a) { return keyBinds.getOrDefault(a.name(), a.def); }

    public void setBind(Action a, String key) { keyBinds.put(a.name(), key); }

    public Map<String, String> binds() { return keyBinds; }

    public File file() { return new File(dataDir, "settings.ini"); }

    public void load() {
        File f = file();
        if (!f.exists()) return;
        try (Scanner sc = new Scanner(f, "UTF-8")) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                String k = line.substring(0, line.indexOf('=')).trim();
                String v = line.substring(line.indexOf('=') + 1).trim();
                apply(k, v);
            }
        } catch (IOException ignored) {
        }
    }

    private void apply(String k, String v) {
        switch (k) {
            case "renderScale": renderScale = parseFloat(v, renderScale); break;
            case "quality": quality = (int) parseFloat(v, quality); break;
            case "fov": fov = parseFloat(v, fov); break;
            case "fpsCap": fpsCap = (int) parseFloat(v, fpsCap); break;
            case "showFps": showFps = parseBool(v, showFps); break;
            case "fullScreen": fullScreen = parseBool(v, fullScreen); break;
            case "debugInfo": debugInfo = parseBool(v, debugInfo); break;
            case "volMaster": volMaster = parseFloat(v, volMaster); break;
            case "volMusic": volMusic = parseFloat(v, volMusic); break;
            case "volSfx": volSfx = parseFloat(v, volSfx); break;
            case "mouseSensitivity": mouseSensitivity = parseFloat(v, mouseSensitivity); break;
            case "invertY": invertY = parseBool(v, invertY); break;
            case "useGamepad": useGamepad = parseBool(v, useGamepad); break;
            case "showMinimap": showMinimap = parseBool(v, showMinimap); break;
            case "localStatsServer": localStatsServer = parseBool(v, localStatsServer); break;
            case "statsServerPort": statsServerPort = (int) parseFloat(v, statsServerPort); break;
            default:
                if (k.startsWith("key.")) {
                    keyBinds.put(k.substring(4), v);
                }
        }
    }

    public void save() {
        new File(dataDir).mkdirs();
        try (PrintWriter w = new PrintWriter(file(), "UTF-8")) {
            w.println("# Porto Aurora - configuracoes");
            w.println("renderScale=" + renderScale);
            w.println("quality=" + quality);
            w.println("fov=" + fov);
            w.println("fpsCap=" + fpsCap);
            w.println("showFps=" + showFps);
            w.println("fullScreen=" + fullScreen);
            w.println("debugInfo=" + debugInfo);
            w.println("volMaster=" + volMaster);
            w.println("volMusic=" + volMusic);
            w.println("volSfx=" + volSfx);
            w.println("mouseSensitivity=" + mouseSensitivity);
            w.println("invertY=" + invertY);
            w.println("useGamepad=" + useGamepad);
            w.println("showMinimap=" + showMinimap);
            w.println("localStatsServer=" + localStatsServer);
            w.println("statsServerPort=" + statsServerPort);
            for (Map.Entry<String, String> e : keyBinds.entrySet()) {
                w.println("key." + e.getKey() + "=" + e.getValue());
            }
        } catch (IOException ignored) {
        }
    }

    private static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private static boolean parseBool(String s, boolean def) {
        if (s.equalsIgnoreCase("true")) return true;
        if (s.equalsIgnoreCase("false")) return false;
        return def;
    }
}
