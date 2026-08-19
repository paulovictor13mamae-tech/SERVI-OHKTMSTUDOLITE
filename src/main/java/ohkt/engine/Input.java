package ohkt.engine;

import java.awt.event.KeyEvent;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Entrada unificada: teclado + mouse + gamepad, com acoes configuraveis.
 * Suporta injecao de eventos (modo headless/testes automatizados).
 */
public final class Input {

    private final Settings settings;

    private final Map<String, Boolean> keyDown = new HashMap<>();
    private final Map<String, Boolean> keyJustPressed = new HashMap<>();

    private boolean mouseCaptured;
    public float mouseDX, mouseDY;
    private final BitSet mouseButtons = new BitSet(8);
    private final BitSet mouseJust = new BitSet(8);
    public int wheel;

    public int mouseX, mouseY;
    public boolean mouseInWindow = true;

    private final LinuxJoystick gamepad = new LinuxJoystick();
    private boolean gamepadSeen = false;

    public Input(Settings settings) {
        this.settings = settings;
        for (Settings.Action a : Settings.Action.values()) {
            keyDown.putIfAbsent(a.def, false);
        }
    }

    public void init() {
        if (settings.useGamepad) gamepad.start();
    }

    // ------------- estado bruto (Window chama) -------------

    public void setKeyDown(String name, boolean down) {
        Boolean was = keyDown.put(name, down);
        if (down && (was == null || !was)) {
            keyJustPressed.put(name, true);
            pendingKey = name;
        }
        if (!down) keyJustPressed.remove(name);
    }

    /** Última tecla pressionada (para rebinding de controles). */
    public String pendingKey;

    public void mouseMove(float dx, float dy) {
        if (mouseCaptured) {
            mouseDX += dx;
            mouseDY += dy;
        }
    }

    public void setMouseButton(int btn, boolean down) {
        if (btn < 0 || btn > 7) return;
        if (down && !mouseButtons.get(btn)) mouseJust.set(btn);
        mouseButtons.set(btn, down);
    }

    public void addWheel(int amount) { wheel += amount; }

    // ------------- consultas por acao -------------

    public boolean isDown(Settings.Action a) {
        boolean v = keyDown.getOrDefault(settings.bind(a), false);
        if (!v && gamepadSeen) v = padDown(a);
        return v;
    }

    public boolean justPressed(Settings.Action a) {
        boolean v = keyJustPressed.getOrDefault(settings.bind(a), false);
        if (!v && gamepadSeen) v = padJust(a);
        return v;
    }

    /** Chamado no fim do frame. */
    public void endFrame() {
        keyJustPressed.clear();
        mouseJust.clear();
        mouseDX = 0;
        mouseDY = 0;
        wheel = 0;
        gamepad.justClear();
    }

    public boolean isMouseCaptured() { return mouseCaptured; }

    public void setMouseCaptured(boolean c) { mouseCaptured = c; }

    public boolean isMouseDown(int btn) { return mouseButtons.get(btn); }

    public boolean isMouseJustPressed(int btn) { return mouseJust.get(btn); }

    /** Tecla crua recém-pressionada (menus). */
    public boolean justPressedRaw(String key) {
        return keyJustPressed.getOrDefault(key, false);
    }

    // ------------- gamepad -------------

    private boolean padDown(Settings.Action a) {
        switch (a) {
            case FIRE: return gamepad.button(LinuxJoystick.BTN_RB) || gamepad.axis(LinuxJoystick.AX_RTRIGGER) > 0.4f;
            case AIM: return gamepad.button(LinuxJoystick.BTN_LT_STICK) || gamepad.axis(LinuxJoystick.AX_RTRIGGER) > 0.9f;
            case SPRINT: return gamepad.axis(LinuxJoystick.AX_LTRIGGER) > 0.4f || gamepad.button(LinuxJoystick.BTN_RT_STICK);
            case JUMP: return gamepad.button(LinuxJoystick.BTN_A);
            case INTERACT: return gamepad.button(LinuxJoystick.BTN_A);
            case ENTER_EXIT: return gamepad.button(LinuxJoystick.BTN_Y);
            case RELOAD: return gamepad.button(LinuxJoystick.BTN_X);
            case HORN: return gamepad.button(LinuxJoystick.BTN_B);
            case HANDBRAKE: return gamepad.button(LinuxJoystick.BTN_LB);
            case WEAPON_NEXT: return gamepad.button(LinuxJoystick.BTN_RB);
            case CAMERA: return gamepad.button(LinuxJoystick.BTN_SELECT);
            case PAUSE: return gamepad.button(LinuxJoystick.BTN_START);
            default: return false;
        }
    }

    private boolean padJust(Settings.Action a) {
        switch (a) {
            case JUMP: return gamepad.just(LinuxJoystick.BTN_A);
            case INTERACT: return gamepad.just(LinuxJoystick.BTN_A);
            case ENTER_EXIT: return gamepad.just(LinuxJoystick.BTN_Y);
            case RELOAD: return gamepad.just(LinuxJoystick.BTN_X);
            case HORN: return gamepad.just(LinuxJoystick.BTN_B);
            case HANDBRAKE: return gamepad.just(LinuxJoystick.BTN_LB);
            case WEAPON_NEXT: return gamepad.just(LinuxJoystick.BTN_RB);
            case CAMERA: return gamepad.just(LinuxJoystick.BTN_SELECT);
            case PAUSE: return gamepad.just(LinuxJoystick.BTN_START);
            default: return false;
        }
    }

    /** Analogico esquerdo: movimento (x,y) normalizado. */
    public float[] padMove() {
        float x = gamepad.axis(LinuxJoystick.AX_LX);
        float y = gamepad.axis(LinuxJoystick.AX_LY);
        return new float[]{x, y};
    }

    /** Analogico direito: camera (x,y) normalizado. */
    public float[] padLook() {
        float x = gamepad.axis(LinuxJoystick.AX_RX);
        float y = gamepad.axis(LinuxJoystick.AX_RY);
        return new float[]{x, y};
    }

    public boolean gamepadConnected() { return gamepadSeen; }

    void pollGamepad() {
        if (gamepadSeen != gamepad.seen()) gamepadSeen = gamepad.seen();
    }

    // ------------- normalizacao de teclas -------------

    public static String keyName(int keyCode) {
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) return String.valueOf((char) ('A' + keyCode - KeyEvent.VK_A));
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) return String.valueOf((char) ('0' + keyCode - KeyEvent.VK_0));
        if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) return "F" + (keyCode - KeyEvent.VK_F1 + 1);
        switch (keyCode) {
            case KeyEvent.VK_SPACE: return "SPACE";
            case KeyEvent.VK_SHIFT: return "SHIFT";
            case KeyEvent.VK_CONTROL: return "CTRL";
            case KeyEvent.VK_ALT: return "ALT";
            case KeyEvent.VK_ESCAPE: return "ESC";
            case KeyEvent.VK_ENTER: return "ENTER";
            case KeyEvent.VK_TAB: return "TAB";
            case KeyEvent.VK_UP: return "UP";
            case KeyEvent.VK_DOWN: return "DOWN";
            case KeyEvent.VK_LEFT: return "LEFTARROW";
            case KeyEvent.VK_RIGHT: return "RIGHTARROW";
            case KeyEvent.VK_COMMA: return ",";
            case KeyEvent.VK_PERIOD: return ".";
            case KeyEvent.VK_MINUS: return "-";
            case KeyEvent.VK_PLUS: return "+";
            case KeyEvent.VK_EQUALS: return "=";
            case KeyEvent.VK_SLASH: return "/";
            case KeyEvent.VK_SEMICOLON: return ";";
            case KeyEvent.VK_BACK_SPACE: return "BACKSPACE";
            case KeyEvent.VK_DELETE: return "DEL";
            case KeyEvent.VK_INSERT: return "INS";
            case KeyEvent.VK_HOME: return "HOME";
            case KeyEvent.VK_END: return "END";
            default: return "KEY" + keyCode;
        }
    }
}
