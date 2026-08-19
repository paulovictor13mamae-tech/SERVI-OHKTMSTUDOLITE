package ohkt.world;

import ohkt.utils.MathX;

/**
 * Ciclo dia/noite: relogio, sol/lua, cores de ceu e ambiente,
 * horario de funcionamento de lojas e densidade de NPCs.
 */
public final class TimeSystem {

    /** Duracao do dia em segundos reais (24 min). */
    public static final float DAY_LENGTH = 24f * 60f;

    public float hour = 9f; // 0..24
    public float worldTime; // segundos acumulados (semaforos etc)
    public int day = 1;

    public void update(float dt) {
        worldTime += dt;
        float prev = hour;
        hour += dt * 24f / DAY_LENGTH;
        if (hour >= 24f) {
            hour -= 24f;
            day++;
            onNewDay();
        }
        if (Math.floor(prev) != Math.floor(hour)) {
            onHourTick();
        }
    }

    private java.util.List<Runnable> dayListeners = new java.util.ArrayList<>();
    private java.util.List<Runnable> hourListeners = new java.util.ArrayList<>();

    public void onNewDay(Runnable r) { dayListeners.add(r); }

    public void onHourTick(Runnable r) { hourListeners.add(r); }

    private void onNewDay() {
        for (Runnable r : dayListeners) r.run();
    }

    private void onHourTick() {
        for (Runnable r : hourListeners) r.run();
    }

    public String phaseName() {
        if (hour < 5) return "Madrugada";
        if (hour < 12) return "Manhã";
        if (hour < 18) return "Tarde";
        return "Noite";
    }

    public boolean isNight() { return hour >= 18.5f || hour < 5.5f; }

    public boolean isDark() { return hour >= 19.5f || hour < 5f; }

    /** Fator 0 dia -> 1 noite plena. */
    public float nightFactor() {
        float t;
        if (hour >= 20f || hour < 4.5f) return 1f;
        if (hour < 5.5f) t = 1f - (hour - 4.5f);
        else if (hour < 18f) return 0f;
        else if (hour < 19f) t = (hour - 18f) * 0.55f;
        else t = 0.55f + (hour - 19f) * 0.45f;
        return MathX.clamp(t, 0, 1);
    }

    /** Direcao PARA o sol (ou lua a noite). */
    public float[] sunDirection() {
        if (nightFactor() > 0.85f) {
            return new float[]{0.35f, 0.75f, -0.4f}; // luz da lua
        }
        float t = MathX.clamp((hour - 5.5f) / 13f, 0, 1); // 5h30..18h30
        float az = (float) Math.PI * (1f - t);
        float x = (float) Math.cos(az);
        float y = (float) (Math.sin(az) * 0.9f + 0.08f);
        float z = 0.25f;
        float l = MathX.len(x, y, z);
        return new float[]{x / l, y / l, z / l};
    }

    /** Cores do ceu e luz por hora (com nuvens/fator clima aplicado depois). */
    public float[] skyColors() {
        float h = hour;
        int top, horizon;
        float sunR, sunG, sunB, ambR, ambG, ambB;
        // keyframes
        if (h < 4.5f) {
            top = 0xff050810; horizon = 0xff0d1424;
            sunR = 0.10f; sunG = 0.12f; sunB = 0.20f;
            ambR = 0.15f; ambG = 0.17f; ambB = 0.26f;
        } else if (h < 6.5f) { // amanhecer
            float t = (h - 4.5f) / 2f;
            top = ohkt.utils.ColorUtil.mix(0xff050810, 0xff3a4a7c, t);
            horizon = ohkt.utils.ColorUtil.mix(0xff0d1424, 0xffff9a5a, t * t);
            sunR = MathX.lerp(0.10f, 1.05f, t); sunG = MathX.lerp(0.12f, 0.7f, t); sunB = MathX.lerp(0.20f, 0.5f, t);
            ambR = MathX.lerp(0.15f, 0.34f, t); ambG = MathX.lerp(0.17f, 0.33f, t); ambB = MathX.lerp(0.26f, 0.42f, t);
        } else if (h < 9f) {
            top = 0xff3a4a7c; horizon = 0xffc8b0a0;
            sunR = 1.05f; sunG = 0.85f; sunB = 0.7f;
            ambR = 0.34f; ambG = 0.36f; ambB = 0.44f;
        } else if (h < 15.5f) { // dia pleno
            top = 0xff4a7ac8; horizon = 0xffb8d4ec;
            sunR = 1.12f; sunG = 1.08f; sunB = 1.0f;
            ambR = 0.46f; ambG = 0.48f; ambB = 0.54f;
        } else if (h < 18f) {
            float t = (h - 15.5f) / 2.5f;
            top = ohkt.utils.ColorUtil.mix(0xff4a7ac8, 0xff3a3a6c, t);
            horizon = ohkt.utils.ColorUtil.mix(0xffb8d4ec, 0xffe8a878, t);
            sunR = MathX.lerp(1.12f, 1.0f, t); sunG = MathX.lerp(1.08f, 0.72f, t); sunB = MathX.lerp(1.0f, 0.45f, t);
            ambR = MathX.lerp(0.46f, 0.34f, t); ambG = MathX.lerp(0.48f, 0.33f, t); ambB = MathX.lerp(0.54f, 0.4f, t);
        } else if (h < 20f) { // poente
            float t = (h - 18f) / 2f;
            top = ohkt.utils.ColorUtil.mix(0xff3a3a6c, 0xff080a18, t * t);
            horizon = ohkt.utils.ColorUtil.mix(0xffff7c48, 0xff141c30, t);
            sunR = MathX.lerp(1.0f, 0.12f, t); sunG = MathX.lerp(0.6f, 0.13f, t); sunB = MathX.lerp(0.35f, 0.22f, t);
            ambR = MathX.lerp(0.32f, 0.16f, t); ambG = MathX.lerp(0.3f, 0.17f, t); ambB = MathX.lerp(0.38f, 0.27f, t);
        } else {
            top = 0xff050810; horizon = 0xff0d1424;
            sunR = 0.10f; sunG = 0.12f; sunB = 0.20f;
            ambR = 0.15f; ambG = 0.17f; ambB = 0.26f;
        }
        return new float[]{top, horizon, sunR, sunG, sunB, ambR, ambG, ambB};
    }

    /** Lojas abertas (8h-22h; armaria 10h-20h). */
    public boolean shopOpen(String shopType) {
        if (shopType.equals("ARMERIA")) return hour >= 10 && hour < 20;
        return hour >= 8 && hour < 22;
    }

    public String clockString() {
        int h = (int) hour;
        int m = (int) ((hour - h) * 60);
        return String.format("%02d:%02d", h, m);
    }
}
