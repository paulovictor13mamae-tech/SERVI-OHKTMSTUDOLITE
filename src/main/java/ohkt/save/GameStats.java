package ohkt.save;

import java.util.HashMap;
import java.util.Map;

/** Estatísticas persistentes da carreira do jogador. */
public final class GameStats {

    private final Map<String, Float> values = new HashMap<>();

    public static final String[] KEYS = {
            "tempoJogo", "distPe", "distCarro", "abates", "policiaisMortos",
            "mortes", "preso", "veiculosComprados", "finalCompletado", "corridasVencidas"
    };

    public void add(String key, float amount) {
        values.merge(key, amount, Float::sum);
    }

    public void set(String key, float value) {
        values.put(key, value);
    }

    public float getFloat(String key) {
        return values.getOrDefault(key, 0f);
    }

    public int getInt(String key) {
        return (int) getFloat(key);
    }

    public Map<String, Float> all() {
        return values;
    }

    public String timeString() {
        int total = (int) getFloat("tempoJogo");
        int h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    public void load(Map<String, Float> data) {
        values.clear();
        values.putAll(data);
    }
}
