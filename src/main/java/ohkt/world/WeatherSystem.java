package ohkt.world;

import ohkt.utils.MathX;

import java.util.Random;

/**
 * Clima dinamico: claro, nublado, chuva, tempestade e neblina.
 * Afeta visibilidade, aderencia dos veiculos e audio ambiente.
 */
public final class WeatherSystem {

    public enum State { CLEAR, CLOUDY, RAIN, STORM, FOG }

    private State state = State.CLEAR;
    private State target = State.CLEAR;
    private float transition;     // 0..1 lerp entre params antigos e novos
    private float timer;
    private float stateTimer;

    // parametros atuais (suavizados)
    public float rain;          // 0..1
    public float cloud;         // 0..1
    public float fogDensity;    // 0..1 (multiplicador)
    public float windX, windZ;  // vento
    public float wetness;       // acumulado na pista
    public float lightning;     // flash 0..1
    private float thunderDelay = -1;

    private final Random rnd = new Random(31);

    public void update(float dt) {
        timer -= dt;
        stateTimer += dt;
        if (timer <= 0) {
            pickNext();
        }
        if (transition < 1f) {
            transition = Math.min(1f, transition + dt / 12f);
        }
        // lerp para os parametros alvo
        float k = 1f - (float) Math.pow(0.92f, dt * 60f / 8f);
        rain += (targetRain() - rain) * k;
        cloud += (targetCloud() - cloud) * k;
        fogDensity += (targetFog() - fogDensity) * k;

        // vento oscila
        float t = stateTimer * 0.05f;
        float str = 0.3f + cloud * 0.7f;
        windX = (ohkt.utils.MathX.perlin(t, 3.7f)) * str * 3f;
        windZ = (ohkt.utils.MathX.perlin(1.3f, t)) * str * 3f;

        // molhado acumula/seca
        if (rain > 0.15f) wetness = Math.min(1, wetness + dt * 0.05f * rain);
        else wetness = Math.max(0, wetness - dt * 0.008f);

        // relampagos
        if (lightning > 0) lightning = Math.max(0, lightning - dt * 2.5f);
        if (thunderDelay > 0) {
            thunderDelay -= dt;
        } else if (state == State.STORM && rain > 0.7f && rnd.nextFloat() < dt * 0.14f) {
            lightning = 1f;
            thunderDelay = 0.5f + rnd.nextFloat() * 1.5f;
            thunderFired = false;
        }
    }

    public float consumeLightning() {
        float v = lightning;
        lightning = 0;
        return v;
    }

    private boolean thunderFired;

    /** Deve tocar trovejada agora (consome). */
    public boolean thunderPending() {
        if (!thunderFired && thunderDelay <= 0 && state == State.STORM) {
            thunderFired = true;
            return true;
        }
        return false;
    }

    public State state() { return state; }

    public String label() {
        switch (state) {
            case CLEAR: return "Céu limpo";
            case CLOUDY: return "Nublado";
            case RAIN: return "Chuva";
            case STORM: return "Tempestade";
            default: return "Neblina";
        }
    }

    private void pickNext() {
        State next;
        switch (state) {
            case CLEAR: next = rnd.nextFloat() < 0.55f ? State.CLOUDY : (rnd.nextFloat() < 0.15f ? State.FOG : State.CLEAR); break;
            case CLOUDY: next = rnd.nextFloat() < 0.45f ? State.RAIN : (rnd.nextFloat() < 0.5f ? State.CLEAR : State.CLOUDY); break;
            case RAIN: next = rnd.nextFloat() < 0.3f ? State.STORM : (rnd.nextFloat() < 0.6f ? State.CLOUDY : State.RAIN); break;
            case STORM: next = State.RAIN; break;
            default: next = State.CLEAR; break;
        }
        if (next == state && rnd.nextFloat() < 0.6f) {
            timer = nextDuration();
            return;
        }
        state = next;
        target = next;
        transition = 0;
        stateTimer = 0;
        timer = nextDuration();
    }

    private float nextDuration() {
        return 70f + rnd.nextFloat() * 150f;
    }

    private float targetRain() {
        switch (state) {
            case RAIN: return 0.65f;
            case STORM: return 1f;
            default: return 0f;
        }
    }

    private float targetCloud() {
        switch (state) {
            case CLEAR: return 0.08f;
            case CLOUDY: return 0.7f;
            case FOG: return 0.5f;
            default: return 1f;
        }
    }

    private float targetFog() {
        switch (state) {
            case FOG: return 1f;
            case STORM: return 0.55f;
            case RAIN: return 0.4f;
            case CLOUDY: return 0.18f;
            default: return 0.1f;
        }
    }

    /** Aderencia dos pneus (1 = seco). */
    public float gripFactor() {
        return Math.max(0.45f, 1f - rain * 0.28f - wetness * 0.22f);
    }

    /** Distancia de visao base para o renderer. */
    public float farDistance(float base) {
        float f = 1f - fogDensity * 0.62f;
        return MathX.clamp(base * f, 120f, base);
    }

    public void forceState(State s) {
        state = s;
        target = s;
        rain = targetRain();
        cloud = targetCloud();
        fogDensity = targetFog();
    }
}
