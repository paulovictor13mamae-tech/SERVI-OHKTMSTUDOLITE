package ohkt.engine;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Leitor de joystick Linux (/dev/input/js*) — gamepad real sem dependencias.
 * Em outros sistemas simplesmente fica inativo e o teclado assume.
 */
final class LinuxJoystick implements Runnable {

    // indices de eixos (layout comum de joysticks USB)
    public static final int AX_LX = 0, AX_LY = 1, AX_LTRIGGER = 2, AX_RX = 3, AX_RY = 4, AX_RTRIGGER = 5;
    // botoes comuns
    public static final int BTN_A = 0, BTN_B = 1, BTN_X = 2, BTN_Y = 3,
            BTN_LB = 4, BTN_RB = 5, BTN_SELECT = 6, BTN_START = 7,
            BTN_LT_STICK = 9, BTN_RT_STICK = 10;

    private final float[] axes = new float[12];
    private final boolean[] buttons = new boolean[20];
    private final boolean[] justSet = new boolean[20];
    private volatile boolean seen;
    private volatile boolean running;
    private Thread thread;

    void start() {
        running = true;
        thread = new Thread(this, "joystick");
        thread.setDaemon(true);
        thread.start();
    }

    boolean seen() { return seen; }

    float axis(int i) { return i >= 0 && i < axes.length ? dead(axes[i]) : 0; }

    boolean button(int i) { return i >= 0 && i < buttons.length && buttons[i]; }

    boolean just(int i) {
        boolean v = i >= 0 && i < justSet.length && justSet[i];
        return v;
    }

    void justClear() {
        for (int i = 0; i < justSet.length; i++) justSet[i] = false;
    }

    private static float dead(float v) {
        return Math.abs(v) < 0.18f ? 0 : v;
    }

    @Override
    public void run() {
        while (running) {
            File f = findDevice();
            if (f == null) {
                seen = false;
                sleep(2000);
                continue;
            }
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                seen = true;
                byte[] buf = new byte[8];
                while (running) {
                    int n = raf.read(buf);
                    if (n < 8) break;
                    int value = (buf[4] & 0xff) | (buf[5] << 8);
                    int type = buf[6] & 0xff;
                    int number = buf[7] & 0xff;
                    if ((type & 0x80) != 0) continue; // init/sync
                    if ((type & 0x01) != 0 && number < axes.length) {
                        axes[number] = value / 32767f;
                    } else if ((type & 0x02) != 0 && number < buttons.length) {
                        boolean down = value != 0;
                        if (down && !buttons[number]) justSet[number] = true;
                        buttons[number] = down;
                    }
                }
            } catch (IOException e) {
                seen = false;
                sleep(1000);
            }
        }
    }

    private static File findDevice() {
        for (int i = 0; i < 4; i++) {
            File f = new File("/dev/input/js" + i);
            if (f.exists() && f.canRead()) return f;
        }
        return null;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
