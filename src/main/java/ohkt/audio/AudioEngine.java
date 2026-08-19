package ohkt.audio;

import ohkt.engine.Settings;
import ohkt.utils.Vec3;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Áudio 3D: mixer com SourceDataLine, vozes sintetizadas com pan/atenuação
 * pela posição, motor contínuo por veículo, chuva, sirenes e rádio.
 * Degrada graciosamente em ambientes sem placa de som (CI/headless).
 */
public final class AudioEngine {

    private final Settings settings;
    private SourceDataLine line;
    private volatile boolean enabled;
    private final MusicPlayer music = new MusicPlayer();

    private static final int VOICES = 20;
    private static final int BUF_FRAMES = 1024;

    private final Voice[] voices = new Voice[VOICES];
    private final short[] mixL = new short[BUF_FRAMES];
    private final short[] mixR = new short[BUF_FRAMES];
    private final byte[] outBytes = new byte[BUF_FRAMES * 4];
    private final Map<String, short[]> cache = new HashMap<>();

    // listener (camera)
    private float lx, ly, lz, lyaw;
    private float masterVol = 0.9f;

    private Thread thread;
    private volatile boolean running;

    private final Map<Object, Voice> engineVoices = new HashMap<>();
    private Voice rainVoice;
    private float rainTarget;

    private static final class Voice {
        boolean active;
        short[] buffer;
        int pos;          // posicao em samples
        float step = 1;   // passo de playback (pitch)
        float vol, pan;
        boolean loop;
        float kind;       // 0 oneshot, 1 engine
        float engineFreq; // base do motor
        float engineVol;
        long lastTick;
    }

    public AudioEngine(Settings settings) {
        this.settings = settings;
        for (int i = 0; i < VOICES; i++) voices[i] = new Voice();
        try {
            AudioFormat fmt = new AudioFormat(44100f, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, BUF_FRAMES * 4);
            line.start();
            enabled = true;
            running = true;
            thread = new Thread(this::loop, "audio-mixer");
            thread.setDaemon(true);
            thread.start();
        } catch (LineUnavailableException | IllegalArgumentException | UnsupportedOperationException e) {
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    public MusicPlayer music() { return music; }

    public void setListener(float x, float y, float z, float yaw) {
        lx = x;
        ly = y;
        lz = z;
        lyaw = yaw;
    }

    // ---------------- cache de sons ----------------

    private short[] sound(String name) {
        short[] s = cache.get(name);
        if (s != null) return s;
        Random r = new Random(name.hashCode());
        switch (name) {
            case "SHOT_GP9": s = SoundSynth.gunshot(0.13f, 0.4f, r); break;
            case "SHOT_VESPA": s = SoundSynth.gunshot(0.08f, 0.3f, r); break;
            case "SHOT_TUFAO": s = SoundSynth.gunshot(0.28f, 1f, r); break;
            case "SHOT_BRUTA": s = SoundSynth.gunshot(0.32f, 0.9f, r); break;
            case "SHOT_CONDOR": s = SoundSynth.gunshot(0.16f, 0.6f, r); break;
            case "EXPLOSION": s = SoundSynth.explosion(); break;
            case "CRASH": s = SoundSynth.crash(); break;
            case "METAL_HIT": s = SoundSynth.metalPing(); break;
            case "PUNCH": s = SoundSynth.thud(0.09f, 95); break;
            case "SWING": s = SoundSynth.whoosh(); break;
            case "STEP": s = SoundSynth.step(); break;
            case "JUMP": s = SoundSynth.blip(300, 500, 0.12f); break;
            case "HORN": s = SoundSynth.horn(); break;
            case "SIREN": s = SoundSynth.siren(); break;
            case "HELI": s = SoundSynth.heli(); break;
            case "RAIN": s = SoundSynth.rainLoop(); break;
            case "ENTER_CAR": case "EXIT_CAR": case "DOOR": s = SoundSynth.doorThunk(); break;
            case "RELOAD": s = SoundSynth.reload(); break;
            case "CASH": s = SoundSynth.jingle(new float[]{1200, 1600}, 0.08f); break;
            case "PICKUP": s = SoundSynth.blip(500, 900, 0.14f); break;
            case "CHECKPOINT": s = SoundSynth.jingle(new float[]{660, 880}, 0.1f); break;
            case "MISSION_START": s = SoundSynth.jingle(new float[]{440, 554, 659}, 0.14f); break;
            case "MISSION_OK": s = SoundSynth.jingle(new float[]{523, 659, 784, 1046}, 0.13f); break;
            case "MISSION_FAIL": s = SoundSynth.jingle(new float[]{392, 311, 233}, 0.18f); break;
            case "OBJECTIVE_OK": s = SoundSynth.blip(700, 1050, 0.12f); break;
            case "CUTSCENE": s = SoundSynth.blip(200, 600, 0.5f); break;
            case "UI": s = SoundSynth.blip(800, 800, 0.05f); break;
            case "DEATH": s = SoundSynth.deathTone(); break;
            default: s = SoundSynth.blip(440, 440, 0.08f); break;
        }
        cache.put(name, s);
        return s;
    }

    // ---------------- API pública ----------------

    public void play(String name, float x, float y, float z, float vol, float pitch) {
        if (!enabled) return;
        float dx = x - lx, dz = z - lz;
        float d = Vec3.len(dx, y - ly, dz);
        if (d > 95) return;
        float atten = 1f / (1f + d * d * 0.0012f);
        float rx = (float) Math.cos(lyaw), rz = (float) Math.sin(lyaw);
        float pan = d < 0.5f ? 0 : MathXu.clamp((dx * rx + dz * rz) / Math.max(1, d), -1, 1) * 0.7f;
        startVoice(name, vol * atten, pan, pitch, false);
    }

    public void playUI(String name, float vol) {
        if (!enabled) return;
        startVoice(name, vol, 0, 1f, false);
    }

    private void startVoice(String name, float vol, float pan, float pitch, boolean loop) {
        synchronized (voices) {
            Voice v = null;
            for (Voice cand : voices) {
                if (!cand.active) {
                    v = cand;
                    break;
                }
            }
            if (v == null) return; // sem vozes livres
            v.active = true;
            v.buffer = sound(name);
            v.pos = 0;
            v.step = pitch;
            v.vol = vol;
            v.pan = pan;
            v.loop = loop;
        }
    }

    /** Motor contínuo por veículo (chamado por frame enquanto dirigido). */
    public void engineTick(ohkt.vehicle.Vehicle veh, float speed01) {
        if (!enabled) return;
        Voice v = engineVoices.get(veh);
        if (v == null || !v.active) {
            v = null;
            synchronized (voices) {
                for (Voice cand : voices) {
                    if (!cand.active) {
                        cand.active = true;
                        cand.kind = 1;
                        cand.buffer = null;
                        cand.pos = 0;
                        cand.step = 1;
                        v = cand;
                        break;
                    }
                }
            }
            if (v == null) return;
            engineVoices.put(veh, v);
        }
        v.lastTick = System.nanoTime();
        float dx = veh.pos.x - lx, dz = veh.pos.z - lz;
        float d = Vec3.len(dx, 0, dz);
        float rpm = veh.rpm01();
        v.engineFreq = 42 + rpm * 130 + speed01 * 20;
        float atten = 1f / (1f + d * d * 0.0009f);
        v.engineVol = (0.32f + veh.driverInput.throttle * 0.4f) * atten;
        v.vol = v.engineVol;
        float rx = (float) Math.cos(lyaw), rz = (float) Math.sin(lyaw);
        v.pan = d < 1 ? 0 : MathXu.clamp((dx * rx + dz * rz) / Math.max(1, d), -1, 1) * 0.7f;
    }

    public void setRain(float intensity) {
        rainTarget = intensity;
        if (!enabled) return;
        if (intensity > 0.02f && (rainVoice == null || !rainVoice.active)) {
            synchronized (voices) {
                for (Voice cand : voices) {
                    if (!cand.active) {
                        cand.active = true;
                        cand.buffer = sound("RAIN");
                        cand.pos = 0;
                        cand.step = 1;
                        cand.loop = true;
                        cand.vol = 0;
                        cand.pan = 0;
                        rainVoice = cand;
                        break;
                    }
                }
            }
        }
    }

    public void setRadio(int station) {
        music.setStation(station);
    }

    public void frame() {
        // limpa motores parados
        Iterator<Map.Entry<Object, Voice>> it = engineVoices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Voice> e = it.next();
            Voice v = e.getValue();
            if (System.nanoTime() - v.lastTick > 300_000_000L) {
                v.active = false;
                v.kind = 0;
                it.remove();
            }
        }
        // chuva
        if (rainVoice != null) {
            rainVoice.vol += ((rainTarget * 0.5f) - rainVoice.vol) * 0.05f;
            if (rainTarget < 0.02f && rainVoice.vol < 0.01f) {
                rainVoice.active = false;
                rainVoice = null;
            }
        }
        music.updatePlaying();
    }

    public void dispose() {
        running = false;
        if (thread != null) thread.interrupt();
        if (line != null) {
            line.drain();
            line.close();
        }
    }

    // ---------------- mixer ----------------

    private void loop() {
        while (running) {
            mixFrame();
            if (line != null) {
                line.write(outBytes, 0, outBytes.length);
            }
        }
    }

    private void mixFrame() {
        masterVol = settings.volMaster;
        java.util.Arrays.fill(mixL, (short) 0);
        java.util.Arrays.fill(mixR, (short) 0);

        synchronized (voices) {
            for (Voice v : voices) {
                if (!v.active) continue;
                if (v.kind == 1) {
                    mixEngine(v);
                    continue;
                }
                if (v.buffer == null) {
                    v.active = false;
                    continue;
                }
                float lgain = v.vol * (1 - Math.max(0, v.pan)) * settings.volSfx;
                float rgain = v.vol * (1 + Math.min(0, v.pan)) * settings.volSfx;
                for (int i = 0; i < BUF_FRAMES; i++) {
                    int idx = (int) v.pos;
                    if (idx >= v.buffer.length) {
                        if (v.loop) {
                            v.pos -= v.buffer.length;
                            idx = 0;
                        } else {
                            v.active = false;
                            break;
                        }
                    }
                    short s = v.buffer[idx];
                    mixL[i] = (short) MathXu.clamp(mixL[i] + s * lgain, -32767, 32767);
                    mixR[i] = (short) MathXu.clamp(mixR[i] + s * rgain, -32767, 32767);
                    v.pos += v.step;
                }
            }
        }

        // rádio ( música )
        if (music.playing()) {
            music.mixInto(mixL, mixR, BUF_FRAMES, settings.volMusic * 0.6f);
        }

        // master + saída intercalada
        for (int i = 0; i < BUF_FRAMES; i++) {
            float l = mixL[i] * masterVol;
            float r = mixR[i] * masterVol;
            outBytes[i * 4] = (byte) ((int) l & 0xff);
            outBytes[i * 4 + 1] = (byte) (((int) l >> 8) & 0xff);
            outBytes[i * 4 + 2] = (byte) ((int) r & 0xff);
            outBytes[i * 4 + 3] = (byte) (((int) r >> 8) & 0xff);
        }
    }

    private void mixEngine(Voice v) {
        float f = v.engineFreq;
        float lgain = v.engineVol * (1 - Math.max(0, v.pan)) * settings.volSfx;
        float rgain = v.engineVol * (1 + Math.min(0, v.pan)) * settings.volSfx;
        for (int i = 0; i < BUF_FRAMES; i++) {
            float t = (v.pos++) / SoundSynth.SR;
            float ph = t * f;
            float saw = 2 * (ph - (float) Math.floor(ph + 0.5f));
            float sub = (float) Math.sin(ph * 2 * Math.PI);
            float grit = (v.pos % 7 < 3) ? 0.35f : -0.2f;
            float s = (saw * 0.5f + sub * 0.3f + grit * 0.2f) * 20000;
            mixL[i] = (short) MathXu.clamp(mixL[i] + s * lgain, -32767, 32767);
            mixR[i] = (short) MathXu.clamp(mixR[i] + s * rgain, -32767, 32767);
        }
    }

    /** utilitario interno de clamp */
    static final class MathXu {
        static float clamp(float v, float a, float b) {
            return v < a ? a : (v > b ? b : v);
        }
    }
}
