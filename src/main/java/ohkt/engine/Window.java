package ohkt.engine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Janela AWT com renderizacao ativa via BufferStrategy.
 * Faz blit sem copia do buffer do renderer (DataBufferInt).
 */
public final class Window {

    private final Game game;
    private Frame frame;
    private Canvas canvas;
    private boolean shown = true;
    private BufferedImage screenImage; // tamanho do canvas
    private Graphics2D screenG;
    private int screenW = 1280, screenH = 720;

    private Robot robot;
    private boolean captureMouse;
    private final Cursor blankCursor = makeBlankCursor();
    private final List<Runnable> resizeListeners = new ArrayList<>();

    public Window(Game game) throws HeadlessException {
        this.game = game;
    }

    public void create(String title, int w, int h) {
        screenW = w;
        screenH = h;
        frame = new Frame(title);
        frame.setUndecorated(false);
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(w, h));
        canvas.setBackground(Color.BLACK);
        frame.add(canvas, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                game.input.setKeyDown(Input.keyName(e.getKeyCode()), true);
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) game.input.setKeyDown("ESC", true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                game.input.setKeyDown(Input.keyName(e.getKeyCode()), false);
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                game.input.setMouseButton(e.getButton(), true);
                if (captureMouse && robot != null) recenter(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                game.input.setMouseButton(e.getButton(), false);
            }
        });

        canvas.addMouseWheelListener(e -> game.input.addWheel(e.getWheelRotation()));

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouse(e);
            }
        });

        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension d = canvas.getSize();
                if (d.width > 0 && d.height > 0) {
                    screenW = d.width;
                    screenH = d.height;
                    allocateScreen();
                    for (Runnable r : resizeListeners) r.run();
                }
            }
        });

        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                setMouseCapture(false);
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                game.requestExit();
            }
        });

        try {
            robot = new Robot();
        } catch (AWTException ignored) {
        }

        allocateScreen();
        frame.setVisible(true);
        canvas.setIgnoreRepaint(true);
        canvas.requestFocus();
    }

    private void handleMouse(MouseEvent e) {
        Point p = canvas.getLocationOnScreen();
        game.input.mouseX = e.getXOnScreen() - p.x;
        game.input.mouseY = e.getYOnScreen() - p.y;
        if (captureMouse && robot != null) {
            recenter(e);
        }
    }

    private int centerX, centerY;

    private void recenter(MouseEvent e) {
        Point loc = canvas.getLocationOnScreen();
        int cx = loc.x + canvas.getWidth() / 2;
        int cy = loc.y + canvas.getHeight() / 2;
        int dx = e.getXOnScreen() - cx;
        int dy = e.getYOnScreen() - cy;
        if (dx != 0 || dy != 0) {
            game.input.mouseMove(dx, dy);
            robot.mouseMove(cx, cy);
        }
        centerX = cx;
        centerY = cy;
    }

    public void setMouseCapture(boolean capture) {
        if (this.captureMouse == capture) return;
        this.captureMouse = capture;
        if (canvas != null) {
            canvas.setCursor(capture ? blankCursor : Cursor.getDefaultCursor());
        }
        if (capture && robot != null && canvas != null) {
            try {
                Point loc = canvas.getLocationOnScreen();
                robot.mouseMove(loc.x + canvas.getWidth() / 2, loc.y + canvas.getHeight() / 2);
            } catch (Exception ignored) {
            }
        }
    }

    private static Cursor makeBlankCursor() {
        byte[] png = blankPng();
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            return Toolkit.getDefaultToolkit().createCustomCursor(img, new Point(0, 0), "blank");
        } catch (Exception e) {
            return Cursor.getDefaultCursor();
        }
    }

    private static byte[] blankPng() {
        // PNG 1x1 transparente
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 0x49,
                0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0, 0, 0, 0x0D, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x62, 0, 1,
                0, 0, 5, 0, 1, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }

    private void allocateScreen() {
        screenImage = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_RGB);
        screenG = screenImage.createGraphics();
        screenG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public void onResize(Runnable r) { resizeListeners.add(r); }

    /** Blita o buffer 3D escalado e devolve o Graphics2D para HUD 2D. */
    public Graphics2D beginFrame(BufferedImage rendered) {
        if (screenImage == null) allocateScreen();
        if (screenG != null) screenG.dispose();
        screenG = screenImage.createGraphics();
        screenG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        screenG.drawImage(rendered, 0, 0, screenW, screenH, null);
        return screenG;
    }

    public void endFrame() {
        if (screenG != null) {
            screenG.dispose();
            screenG = null;
        }
        java.awt.Graphics cg = canvas.getGraphics();
        if (cg != null) {
            cg.drawImage(screenImage, 0, 0, null);
            cg.dispose();
        }
        Toolkit.getDefaultToolkit().sync();
    }

    public int screenWidth() { return screenW; }

    public int screenHeight() { return screenH; }

    public void setTitle(String t) {
        if (frame != null) frame.setTitle(t);
    }

    public void setFullScreen(boolean fs) {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (fs && gd.isFullScreenSupported()) {
            frame.setUndecorated(true);
            gd.setFullScreenWindow(frame);
        } else {
            gd.setFullScreenWindow(null);
            frame.setUndecorated(false);
            frame.pack();
            frame.setLocationRelativeTo(null);
        }

    }

    public boolean isFocused() {
        return frame != null && frame.isFocused();
    }

    /** Para o reflection de metodos de janela em modo headless (nao usado). */
    @SuppressWarnings("unused")
    private static void noop(Method m) {
    }
}
