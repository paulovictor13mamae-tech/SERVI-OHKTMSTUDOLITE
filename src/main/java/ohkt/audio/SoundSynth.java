package ohkt.audio;

import java.util.Random;

/**
 * Sintetizador de efeitos sonoros: tudo gerado em tempo real (sem assets).
 * Buffers mono 16-bit 44100Hz.
 */
public final class SoundSynth {

    public static final float SR = 44100f;

    private SoundSynth() {}

    private static short[] alloc(float seconds) {
        return new short[(int) (seconds * SR)];
    }

    private static float noise(Random r) { return r.nextFloat() * 2 - 1; }

    /** Envelope exponencial. */
    private static float env(float t, float dur, float k) {
        return (float) Math.exp(-t * k / dur);
    }

    private static void put(short[] buf, int i, float v) {
        if (i < 0 || i >= buf.length) return;
        int s = (int) (v * 32000);
        if (s > 32000) s = 32000;
        if (s < -32000) s = -32000;
        buf[i] = (short) (buf[i] / 4 + s); // leve mix com o que já tem
    }

    public static short[] gunshot(float dur, float boom, Random r) {
        short[] b = alloc(dur);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float e = env(t, dur, 5);
            float v = noise(r) * e * 0.9f;
            v += Math.sin(t * 90 * (1 + boom)) * e * boom * 0.8f;
            b[i] = (short) (v * 31000);
        }
        return b;
    }

    public static short[] explosion() {
        short[] b = alloc(1.6f);
        Random r = new Random(1);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float e = env(t, 1.6f, 3.5f);
            lp += (noise(r) - lp) * 0.08f;
            float v = lp * e * 1.1f + (float) (Math.sin(t * 45 * Math.exp(-t * 1.1f)) * e * 0.9f);
            b[i] = (short) (v * 30000);
        }
        return b;
    }

    public static short[] crash() {
        short[] b = alloc(0.45f);
        Random r = new Random(2);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float e = env(t, 0.45f, 4);
            float v = noise(r) * e * 0.7f + (float) (Math.sin(t * 700) * e * 0.25f + Math.sin(t * 340) * e * 0.2f);
            b[i] = (short) (v * 30000);
        }
        return b;
    }

    public static short[] metalPing() {
        short[] b = alloc(0.12f);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float e = env(t, 0.12f, 5);
            float v = (float) ((Math.sin(t * 1900) + Math.sin(t * 2740) * 0.6f) * e);
            b[i] = (short) (v * 20000);
        }
        return b;
    }

    public static short[] thud(float dur, float freq) {
        short[] b = alloc(dur);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float v = (float) Math.sin(t * freq * (1 - t * 2)) * env(t, dur, 6);
            b[i] = (short) (v * 28000);
        }
        return b;
    }

    public static short[] whoosh() {
        short[] b = alloc(0.16f);
        Random r = new Random(3);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float sweep = 0.02f + t * 0.4f;
            lp += (noise(r) - lp) * sweep;
            b[i] = (short) (lp * env(t, 0.16f, 3) * 20000);
        }
        return b;
    }

    public static short[] step() {
        short[] b = alloc(0.07f);
        Random r = new Random(4);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            lp += (noise(r) - lp) * 0.25f;
            b[i] = (short) (lp * env(t, 0.07f, 5) * 14000);
        }
        return b;
    }

    public static short[] horn() {
        short[] b = alloc(0.4f);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float sq1 = (float) (Math.signum(Math.sin(t * 2 * Math.PI * 400)) * 0.3f);
            float sq2 = (float) (Math.signum(Math.sin(t * 2 * Math.PI * 505)) * 0.3f);
            float e = Math.min(1, t * 40) * Math.min(1, (0.4f - t) * 20);
            b[i] = (short) ((sq1 + sq2) * e * 16000);
        }
        return b;
    }

    public static short[] siren() {
        short[] b = alloc(1.0f);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float f = 700 + 380 * (float) Math.sin(t * 2 * Math.PI * 1.1f);
            float v = (float) Math.sin(t * 2 * Math.PI * f) * 0.5f;
            b[i] = (short) (v * 22000);
        }
        return b;
    }

    public static short[] heli() {
        short[] b = alloc(0.5f);
        Random r = new Random(5);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float chop = (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI * 13));
            lp += (noise(r) - lp) * 0.05f;
            b[i] = (short) (lp * chop * 0.35f * 24000);
        }
        return b;
    }

    public static short[] rainLoop() {
        short[] b = alloc(2f);
        Random r = new Random(6);
        float lp = 0;
        for (int i = 0; i < b.length; i++) {
            lp += (noise(r) - lp) * 0.12f;
            b[i] = (short) (lp * 9000);
        }
        return b;
    }

    public static short[] blip(float f0, float f1, float dur) {
        short[] b = alloc(dur);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float f = f0 + (f1 - f0) * t / dur;
            b[i] = (short) (Math.sin(t * 2 * Math.PI * f) * env(t, dur, 3) * 24000);
        }
        return b;
    }

    public static short[] jingle(float[] freqs, float noteDur) {
        short[] b = alloc(noteDur * freqs.length + 0.1f);
        int idx = 0;
        for (float f : freqs) {
            for (int i = 0; i < noteDur * SR; i++) {
                float t = i / SR;
                float v = (float) (Math.sin(t * 2 * Math.PI * f) * env(t, noteDur, 2.2f));
                put(b, idx + i, (float) (v * 0.7f + Math.sin(t * 4 * Math.PI * f) * env(t, noteDur, 3) * 0.2f));
            }
            idx += (int) (noteDur * SR * 0.8f);
        }
        return b;
    }

    public static short[] doorThunk() {
        return thud(0.09f, 130);
    }

    public static short[] reload() {
        short[] b = alloc(0.3f);
        short[] c1 = metalPing();
        short[] c2 = thud(0.08f, 220);
        for (int i = 0; i < c1.length / 3 && i < b.length; i++) b[i * 3] = c1[i * 3];
        int off = (int) (0.2f * SR);
        for (int i = 0; i < c2.length && off + i < b.length; i++) b[off + i] = c2[i];
        return b;
    }

    public static short[] deathTone() {
        short[] b = alloc(1.2f);
        for (int i = 0; i < b.length; i++) {
            float t = i / SR;
            float f = 220 * (float) Math.pow(0.5, t);
            b[i] = (short) (Math.sin(t * 2 * Math.PI * f) * env(t, 1.2f, 1.6f) * 26000);
        }
        return b;
    }
}
