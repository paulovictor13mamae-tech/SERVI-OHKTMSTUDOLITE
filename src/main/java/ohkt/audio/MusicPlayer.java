package ohkt.audio;

import java.util.Random;

/**
 * Rádio fictício de Porto Aurora: 4 estações com música 100% procedural
 * (sequenciador com baixo, acordes, melodia e bateria), faixas nomeadas.
 */
public final class MusicPlayer {

    public static final String[] STATIONS = {"Horizonte FM", "Choque FM", "Onda Suave", "Tritono"};

    public static final String[][] TRACKS = {
            {"Aurora em azul", "Jasmim de neon", "Vou de bonde", "Maré alta"},
            {"Ferro velho", "Grito do cais", "Bituca urbano", "Fúria rosa"},
            {"Chuva no píer", "Canção do farol", "Beco lento", "Fita cassete"},
            {"Circuito 07", "Sinal perdido", "Noite elétrica", "Modo drone"},
    };

    private int station = -1; // desligado
    private boolean wantPlaying;
    private float stepDur = 0.14f;
    private int step;
    private int bar;
    private float samplePos;
    private final Random rnd = new Random(404);

    // estado de síntese
    private float bassPhase, padPhase, leadPhase;
    private int bassNote, leadNote;
    private float leadEnv;

    // escalas (semitons)
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] PENTA = {0, 3, 5, 7, 10};
    private static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10};
    private static final int[] PHRYG = {0, 1, 3, 5, 7, 8, 10};

    public void setStation(int s) {
        station = s;
        step = 0;
        bar = 0;
        bassPhase = 0;
        padPhase = 0;
        leadPhase = 0;
        leadEnv = 0;
        if (s >= 0) {
            stepDur = s == 0 ? 0.135f : s == 1 ? 0.107f : s == 2 ? 0.18f : 0.117f;
        }
    }

    public int station() { return station; }

    public String stationName() {
        return station < 0 ? "Rádio desligado" : STATIONS[station];
    }

    public String trackName() {
        if (station < 0) return "";
        return TRACKS[station][Math.abs(bar / 8) % TRACKS[station].length];
    }

    public boolean playing() {
        return station >= 0 && wantPlaying;
    }

    public void setWantPlaying(boolean want) {
        this.wantPlaying = want;
    }

    public void updatePlaying() {
        // chamado por frame; nada a fazer (mix roda no thread de áudio)
    }

    private static float noteFreq(int semi) {
        return 220f * (float) Math.pow(2, semi / 12.0);
    }

    private static float osc(float phase, int type) {
        float p = phase - (float) Math.floor(phase);
        switch (type) {
            case 1: return 2 * p - 1;                    // dente de serra
            case 2: return p < 0.5f ? 1 : -1;            // quadrada
            default: return (float) Math.sin(p * 2 * Math.PI); // seno
        }
    }

    /** Mistura a música no buffer do mixer (chamado no thread de áudio). */
    public void mixInto(short[] mixL, short[] mixR, int frames, float vol) {
        if (!playing() || vol <= 0.001f) return;
        int st = station;
        int[] scale = st == 0 ? MAJOR : st == 1 ? PENTA : st == 2 ? DORIAN : PHRYG;
        int root = st == 0 ? 0 : st == 1 ? -5 : st == 2 ? 3 : -7;
        int leadWave = st == 0 ? 2 : st == 1 ? 1 : st == 2 ? 0 : 1;
        int padWave = st == 2 ? 0 : (st == 3 ? 1 : 0);

        float sr = SoundSynth.SR;
        for (int i = 0; i < frames; i++) {
            float t = samplePos / sr;
            samplePos++;
            float stepF = t / stepDur;
            int curStep = (int) stepF;
            if (curStep != step) {
                step = curStep;
                int stepInBar = step % 16;
                if (stepInBar == 0) {
                    bar++;
                    if (bar % 4 == 0) {
                        rnd.setSeed(bar * 31 + st); // variação determinística por compasso
                    }
                }
                // baixo: fundamental em colcheias
                if (stepInBar % 2 == 0) {
                    int degree = (bar % 4 == 3 && stepInBar % 4 == 0) ? 4 : 0;
                    if (st == 3 && stepInBar % 4 == 2) degree = 2;
                    bassNote = root + scale[degree % scale.length] - 12;
                }
                // melodia
                if (st != 2 ? stepInBar % 2 == 1 && rnd.nextFloat() < 0.55f : stepInBar % 4 == 2 && rnd.nextFloat() < 0.7f) {
                    leadNote = root + scale[rnd.nextInt(scale.length)] + (rnd.nextFloat() < 0.3f ? 12 : 0);
                    leadEnv = 1;
                }
                leadEnv *= 0.86f;
            }
            float stepT = (stepF - step);

            // ---- baixo ----
            float bf = noteFreq(bassNote);
            bassPhase += bf / sr;
            float bass = osc(bassPhase, st == 1 ? 1 : 2) * 0.30f;
            bass *= st == 2 ? 0.6f : 1f;

            // ---- pad (acorde) ----
            float chordRoot = noteFreq(root + scale[0]);
            padPhase += chordRoot / sr;
            float pad = (osc(padPhase, padWave) * 0.10f
                    + osc(padPhase * 1.26f, padWave) * 0.08f
                    + osc(padPhase * 1.5f, padWave) * 0.07f);
            if (st == 1) pad *= 0.5f;

            // ---- melodia ----
            float lf = noteFreq(leadNote + 12);
            leadPhase += lf / sr;
            float lead = osc(leadPhase, leadWave) * 0.16f * leadEnv;

            // ---- bateria ----
            float kick = 0, snare = 0, hat = 0;
            int sib = step % 16;
            if (sib % 4 == 0) {
                float kt = stepT * stepDur;
                kick = (float) Math.sin(kt * 2 * Math.PI * 60 * (1 - kt * 6)) * Math.max(0, 1 - kt * 22) * 0.5f;
            }
            if (sib == 4 || sib == 12 || (st == 1 && sib == 14)) {
                float st2 = stepT * stepDur;
                snare = ((rnd.nextInt() & 1) - 0.5f) * Math.max(0, 1 - st2 * 30) * 0.22f;
            }
            if (st != 2 ? sib % 2 == 0 : sib % 4 == 0) {
                float ht = stepT * stepDur;
                hat = ((rnd.nextInt() & 1) - 0.5f) * Math.max(0, 1 - ht * 90) * (st == 3 ? 0.16f : 0.08f);
            }

            float mixMono = (bass + pad + lead + kick + snare + hat) * vol;
            float widen = st == 3 ? 0.25f : 0.12f;
            float l = mixMono * (1 - widen) + lead * vol * widen;
            float r = mixMono * (1 - widen) - lead * vol * widen;
            mixL[i] = (short) Math.max(-32767, Math.min(32767, mixL[i] + l * 26000));
            mixR[i] = (short) Math.max(-32767, Math.min(32767, mixR[i] + r * 26000));
        }
    }
}
