package chat.client;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sticker và cảm xúc.
 * Mã sticker: "e:&lt;emoji&gt;" (emoji cỡ lớn) hoặc "t:&lt;số thứ tự&gt;" (sticker chữ vẽ bằng Java2D).
 */
public final class Stickers {
    private Stickers() { }

    /** Cảm xúc nhanh khi thả vào tin nhắn. */
    public static final String[] REACTIONS = { "👍", "❤️", "😆", "😮", "😢", "😡" };

    public record Pack(String name, String icon, List<String> codes, int cols) { }

    private record Word(String text, String emoji, int c1, int c2) { }

    private static final Word[] WORDS = {
            new Word("Cảm ơn", "🙏", 0xFF9966, 0xFF5E62),
            new Word("Haha", "😂", 0xFDC830, 0xF37335),
            new Word("OK!", "👌", 0x43CEA2, 0x185A9D),
            new Word("Yêu quá", "💖", 0xF093FB, 0xF5576C),
            new Word("Chào nha", "👋", 0x56CCF2, 0x2F80ED),
            new Word("Ngủ ngon", "🌙", 0x8E9EFF, 0x6A4CFF),
            new Word("Tuyệt vời", "✨", 0x11C8A0, 0x2BB673),
            new Word("Xin lỗi", "🥺", 0xFF7EB3, 0xC850C0),
            new Word("Đợi xíu", "⏳", 0x7F7FD5, 0x86A8E7),
            new Word("Chốt!", "🔥", 0xF12711, 0xF5AF19),
    };

    public static final List<Pack> PACKS = List.of(
            emojiPack("Cảm xúc", "😀", "😀 😂 🥰 😍 😎 🤩 😭 😡 😱 🥳 😴 🤔 🙄 😇 🤗 🤭 😏 🥺 🤯 😤"),
            new Pack("Chữ", "💬", wordCodes(), 2),
            emojiPack("Cử chỉ", "👍", "👍 👏 🙏 👌 ✌️ 🤝 💪 👋 🙌 🫶 ❤️ 💔 🔥 💯 ✨ 🎉"),
            emojiPack("Động vật", "🐱", "🐶 🐱 🐼 🦊 🐸 🐧 🐵 🦄 🐻 🐰 🐯 🐨 🐷 🐮 🐙 🦋"),
            emojiPack("Đồ ăn", "🍕", "🍕 🍔 🍟 🌭 🍩 🍰 🧋 ☕ 🍜 🍣 🍉 🍓 🍦 🍿 🥤 🍻"));

    private static Pack emojiPack(String name, String icon, String list) {
        List<String> codes = new ArrayList<>();
        for (String e : list.split(" ")) codes.add("e:" + e);
        return new Pack(name, icon, codes, 4);
    }

    private static List<String> wordCodes() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < WORDS.length; i++) l.add("t:" + i);
        return l;
    }

    private static Word word(String code) {
        try {
            int i = Integer.parseInt(code.substring(2));
            return i >= 0 && i < WORDS.length ? WORDS[i] : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Mô tả ngắn để hiện ở dòng xem trước hội thoại. */
    public static String label(String code) {
        if (code == null) return "Sticker";
        if (code.startsWith("e:")) return "Sticker " + code.substring(2);
        Word w = word(code);
        return w != null ? "Sticker \"" + w.text + "\" " + w.emoji : "Sticker";
    }

    // ---------------- Vẽ ----------------
    private static final Font WORD_FONT = Theme.font(Font.BOLD, 26f);
    private static final int WORD_H = 58;

    private static int wordBoxW(Word w) {
        return Theme.textWidth(Theme.fm(WORD_FONT), w.text) + 44;
    }

    /** Kích thước sticker khi vẽ với cỡ gốc "base" (emoji: base x base). */
    public static Dimension size(String code, int base) {
        if (code != null && code.startsWith("t:")) {
            Word w = word(code);
            if (w != null) {
                double k = base / 100.0;
                return new Dimension((int) Math.ceil((wordBoxW(w) + 30) * k), (int) Math.ceil((WORD_H + 34) * k));
            }
        }
        return new Dimension(base, base);
    }

    public static void paint(Graphics2D g0, String code, int x, int y, int base) {
        Graphics2D g = Theme.aa(g0);
        if (code != null && code.startsWith("t:") && word(code) != null) {
            paintWord(g, word(code), x, y, base / 100.0);
        } else {
            String e = code != null && code.startsWith("e:") ? code.substring(2) : "❓";
            Font f = Theme.font(Font.PLAIN, base * 0.78f);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            int tw = Theme.textWidth(fm, e);
            // bóng mềm dưới sticker
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new Ellipse2D.Double(x + base * 0.2, y + base * 0.86, base * 0.6, base * 0.1));
            Theme.drawText(g, e, x + (base - tw) / 2f, y + (base - fm.getHeight()) / 2f + fm.getAscent());
        }
        g.dispose();
    }

    private static void paintWord(Graphics2D g, Word w, int x, int y, double k) {
        g.translate(x, y);
        g.scale(k, k);
        int bw = wordBoxW(w), bh = WORD_H;
        double ox = 12, oy = 22;
        AffineTransform keep = g.getTransform();
        g.rotate(Math.toRadians(-5), ox + bw / 2.0, oy + bh / 2.0);
        // bóng
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(Theme.rr(ox + 2, oy + 6, bw, bh, 20));
        // viền trắng + nền gradient
        g.setColor(Color.WHITE);
        g.fill(Theme.rr(ox - 4, oy - 4, bw + 8, bh + 8, 24));
        g.setPaint(new GradientPaint((float) ox, (float) oy, new Color(w.c1), (float) (ox + bw), (float) (oy + bh), new Color(w.c2)));
        g.fill(Theme.rr(ox, oy, bw, bh, 20));
        // ánh sáng phía trên
        g.setPaint(new GradientPaint(0, (float) oy, new Color(255, 255, 255, 90), 0, (float) (oy + bh * 0.55), new Color(255, 255, 255, 0)));
        g.fill(Theme.rr(ox + 3, oy + 3, bw - 6, bh * 0.5, 17));
        // chữ có viền
        g.setFont(WORD_FONT);
        FontMetrics fm = g.getFontMetrics();
        float tx = (float) (ox + 22), ty = (float) (oy + (bh - fm.getHeight()) / 2.0 + fm.getAscent());
        java.awt.font.TextLayout tl = new java.awt.font.TextLayout(w.text, WORD_FONT, g.getFontRenderContext());
        java.awt.Shape outline = tl.getOutline(AffineTransform.getTranslateInstance(tx, ty));
        g.setColor(new Color(0, 0, 0, 60));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setColor(Color.WHITE);
        g.fill(outline);
        g.setTransform(keep);
        // emoji trang trí ở góc
        Font ef = Theme.font(Font.PLAIN, 30f);
        g.setFont(ef);
        FontMetrics efm = g.getFontMetrics();
        Theme.drawText(g, w.emoji, (float) (ox + bw - 22), (float) (oy - 18 + efm.getAscent()));
    }

    // =====================================================================
    //  Bảng chọn sticker (hiện trong popup phía trên thanh soạn tin)
    // =====================================================================
    public static JPopupMenu picker(Consumer<String> onPick) {
        JPopupMenu pop = new JPopupMenu() {
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                g.setColor(Theme.PANEL);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        pop.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        pop.setLayout(new BorderLayout());

        JPanel grid = new JPanel();
        grid.setOpaque(false);
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        gridHolder.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        gridHolder.add(grid, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(gridHolder);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        Widgets.slim(sp);
        sp.setPreferredSize(new Dimension(372, 300));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 6));
        tabs.setOpaque(false);
        tabs.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 6, 0, 6)));
        int[] current = { 0 };
        List<Tab> tabList = new ArrayList<>();
        Runnable[] show = new Runnable[1];
        show[0] = () -> {
            Pack p = PACKS.get(current[0]);
            grid.removeAll();
            grid.setLayout(new GridLayout(0, p.cols(), 6, 6));
            for (String code : p.codes()) grid.add(new Cell(code, p.cols() == 2 ? 70 : 64, c -> { pop.setVisible(false); onPick.accept(c); }));
            grid.revalidate();
            grid.repaint();
            sp.getVerticalScrollBar().setValue(0);
            for (Tab t : tabList) t.repaint();
        };
        for (int i = 0; i < PACKS.size(); i++) {
            int idx = i;
            Tab t = new Tab(PACKS.get(i), () -> current[0] == idx, () -> { current[0] = idx; show[0].run(); });
            tabList.add(t);
            tabs.add(t);
        }
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        javax.swing.JLabel title = Dialogs.label("Sticker", Theme.font(Font.BOLD, 14f), Theme.TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(10, 14, 0, 0));
        head.add(title, BorderLayout.NORTH);
        head.add(tabs, BorderLayout.CENTER);
        pop.add(head, BorderLayout.NORTH);
        pop.add(sp, BorderLayout.CENTER);
        show[0].run();
        return pop;
    }

    private static final class Tab extends JComponent {
        final Pack p;
        final java.util.function.BooleanSupplier active;
        boolean hover;
        Tab(Pack p, java.util.function.BooleanSupplier active, Runnable onClick) {
            this.p = p;
            this.active = active;
            setPreferredSize(new Dimension(40, 36));
            setToolTipText(p.name());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            if (active.getAsBoolean()) {
                g.setColor(Theme.alpha(Theme.ACCENT, 70));
                g.fill(Theme.rr(0, 0, w, h, 11));
                g.setPaint(Theme.accentGradient(8, 0, w - 8, 0));
                g.fill(Theme.rr(8, h - 3, w - 16, 3, 1.5));
            } else if (hover) {
                g.setColor(Theme.HOVER);
                g.fill(Theme.rr(0, 0, w, h, 11));
            }
            g.setFont(Theme.font(Font.PLAIN, 19f));
            FontMetrics fm = g.getFontMetrics();
            Theme.drawText(g, p.icon(), (w - Theme.textWidth(fm, p.icon())) / 2f, (h - fm.getHeight()) / 2f + fm.getAscent());
            g.dispose();
        }
    }

    private static final class Cell extends JComponent {
        final String code;
        final int base;
        boolean hover;
        Cell(String code, int base, Consumer<String> onPick) {
            this.code = code;
            this.base = base;
            Dimension d = Stickers.size(code, base);
            setPreferredSize(new Dimension(Math.max(78, d.width + 8), Math.max(78, d.height + 8)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { onPick.accept(code); }
            });
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            if (hover) {
                g.setColor(Theme.alpha(Theme.ACCENT, 46));
                g.fill(Theme.rr(0, 0, w, h, 14));
            }
            int b = hover ? base + 6 : base;
            Dimension d = Stickers.size(code, b);
            Stickers.paint(g, code, (w - d.width) / 2, (h - d.height) / 2, b);
            g.dispose();
        }
    }

    // =====================================================================
    //  Thanh chọn cảm xúc (popup khi bấm nút cảm xúc hoặc chuột phải)
    // =====================================================================
    public static final class ReactBar extends JComponent {
        private final String mine;
        private int hover = -1;
        private static final int CELL = 46;

        public ReactBar(String mine, Consumer<String> onPick, Runnable close) {
            this.mine = mine;
            setPreferredSize(new Dimension(CELL * REACTIONS.length + 12, CELL + 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int i = at(e); if (i != hover) { hover = i; repaint(); } }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    int i = at(e);
                    if (i < 0) return;
                    close.run();
                    onPick.accept(REACTIONS[i]);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int at(MouseEvent e) {
            int i = (e.getX() - 6) / CELL;
            return e.getX() >= 6 && i >= 0 && i < REACTIONS.length ? i : -1;
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.SURFACE);
            g.fill(Theme.rr(0, 0, w, h, h / 2.0));
            for (int i = 0; i < REACTIONS.length; i++) {
                String e = REACTIONS[i];
                double cx = 6 + i * CELL + CELL / 2.0, cy = h / 2.0;
                if (e.equals(mine)) {
                    g.setColor(Theme.alpha(Theme.ACCENT, 90));
                    g.fill(new Ellipse2D.Double(cx - 21, cy - 21, 42, 42));
                }
                float size = hover == i ? 32f : 25f;
                g.setFont(Theme.font(Font.PLAIN, size));
                FontMetrics fm = g.getFontMetrics();
                Theme.drawText(g, e, (float) (cx - Theme.textWidth(fm, e) / 2.0),
                        (float) (cy - (hover == i ? 3 : 0) - fm.getHeight() / 2.0 + fm.getAscent()));
            }
            g.dispose();
        }
    }

    /** Popup chỉ chứa thanh cảm xúc. */
    public static JPopupMenu reactPopup(String mine, Consumer<String> onPick) {
        JPopupMenu pop = new JPopupMenu();
        pop.setOpaque(false);
        pop.setBorder(BorderFactory.createEmptyBorder());
        pop.setBackground(Theme.BG);
        ReactBar bar = new ReactBar(mine, onPick, () -> pop.setVisible(false));
        pop.add(bar);
        return pop;
    }

}
