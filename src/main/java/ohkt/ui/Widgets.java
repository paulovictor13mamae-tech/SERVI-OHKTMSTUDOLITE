package ohkt.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Widgets 2D compartilhados (texto, painéis, barras, texto quebrado). */
public final class Widgets {

    private Widgets() {}

    public static void panel(Graphics2D g, int x, int y, int w, int h, int fill, int border) {
        g.setColor(new Color(fill, true));
        g.fillRoundRect(x, y, w, h, 10, 10);
        if (border != 0) {
            g.setColor(new Color(border, true));
            g.drawRoundRect(x, y, w, h, 10, 10);
        }
    }

    public static void text(Graphics2D g, String s, int x, int y, Color c, boolean center, Font font) {
        g.setFont(font);
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        int tx = center ? x - fm.stringWidth(s) / 2 : x;
        g.drawString(s, tx + 1, y + 1);
        g.setColor(c);
        g.drawString(s, tx, y);
    }

    public static void drawWrappedText(Graphics2D g, String s, int cx, int y, int maxWidth, Color c, boolean center) {
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        // quebra por palavras
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(test) > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        int lineH = fm.getHeight();
        int i = 0;
        for (String line : lines) {
            int tx = center ? cx - fm.stringWidth(line) / 2 : cx;
            g.setColor(Color.BLACK);
            g.drawString(line, tx + 1, y + i * lineH + 1);
            g.setColor(c);
            g.drawString(line, tx, y + i * lineH);
            i++;
        }
    }

    public static void bar(Graphics2D g, int x, int y, int w, int h, float pct, Color c, Color bg) {
        g.setColor(bg);
        g.fillRect(x, y, w, h);
        g.setColor(c);
        int fw = (int) (w * Math.max(0, Math.min(1, pct)));
        g.fillRect(x, y, fw, h);
        g.setColor(new Color(0, 0, 0, 120));
        g.drawRect(x, y, w, h);
    }

    public static String money(int v) {
        return "R$ " + String.format("%,d", v).replace(',', '.');
    }
}
