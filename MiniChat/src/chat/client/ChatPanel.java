package chat.client;

import chat.common.Message;

import javax.imageio.ImageIO;
import javax.sound.sampled.Clip;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Một khung hội thoại (phòng chung, một phòng, hoặc chat riêng với một người).
 * Toàn bộ tin nhắn được vẽ tay (bong bóng chat, ảnh bo góc, thẻ tệp, thanh ghi âm)
 * trên một canvas cuộn mượt, tự xuống cuối khi có tin mới.
 */
public class ChatPanel extends JPanel {

    public final Message.Scope scope;
    public String target;          // tên phòng / tên người nhận (null với phòng chung)
    public String title;

    /** Đổi tên phòng (khi trưởng nhóm đổi tên). */
    public void rename(String newName) { target = newName; title = newName; }

    /** Vẽ lại khi hồ sơ (tên hiển thị / avatar) thay đổi. */
    public void refreshProfiles() { canvas.dirty = true; canvas.revalidate(); canvas.repaint(); }

    private static final int SIDE = 20;          // lề trái/phải
    private static final int AVATAR = 34;
    private static final Font BODY = Theme.font(Font.PLAIN, 14.5f);
    private static final Font TIME = Theme.font(Font.PLAIN, 11f);
    private static final Font NAME = Theme.font(Font.BOLD, 12.5f);
    private static final Font PILL = Theme.font(Font.PLAIN, 12.5f);
    private static final Font REACT_FONT = Theme.font(Font.PLAIN, 14f);
    private static final Font REACT_COUNT = Theme.font(Font.BOLD, 12f);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final String me;
    private final List<Item> items = new ArrayList<>();
    private final Canvas canvas = new Canvas();
    private final JScrollPane scroll = new JScrollPane(canvas);
    private final Timer scrollTimer;
    private final Timer animTimer;

    private MsgItem lastMsg;
    private LocalDate lastDay;
    private boolean atBottom = true;
    private int prevScrollValue;
    private String preview = "";
    private long lastTime;

    public ChatPanel(Message.Scope scope, String target, String title, String me) {
        super(new BorderLayout());
        this.scope = scope;
        this.target = target;
        this.title = title;
        this.me = me;
        setBackground(Theme.BG);

        scroll.setBackground(Theme.BG);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getViewport().setOpaque(true);
        Widgets.slim(scroll);
        add(scroll, BorderLayout.CENTER);

        JScrollBar bar = scroll.getVerticalScrollBar();
        bar.addAdjustmentListener(e -> {
            int v = bar.getValue();
            if (v != prevScrollValue) {
                prevScrollValue = v;
                atBottom = bar.getMaximum() - bar.getVisibleAmount() - v < 48;
            }
        });
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (atBottom) SwingUtilities.invokeLater(ChatPanel.this::jumpToBottom);
            }
        });

        scrollTimer = new Timer(12, e -> {
            int target1 = bar.getMaximum() - bar.getVisibleAmount();
            int cur = bar.getValue();
            int d = target1 - cur;
            if (Math.abs(d) <= 1) { bar.setValue(target1); ((Timer) e.getSource()).stop(); return; }
            int step = (int) Math.ceil(Math.abs(d) * 0.22);
            bar.setValue(cur + (d > 0 ? step : -step));
        });
        animTimer = new Timer(40, e -> {
            boolean any = false;
            for (Item it : items) {
                if (it instanceof AudioMsg a && a.playing) {
                    any = true;
                    if (System.nanoTime() - a.startedAt > (long) (a.seconds * 1e9) + 250_000_000L) a.playing = false;
                    canvas.repaint(0, a.y, canvas.getWidth(), a.h);
                }
            }
            if (!any) ((Timer) e.getSource()).stop();
        });
    }

    // ---------------- API công khai ----------------

    public void appendSystem(String text) {
        ensureDay(System.currentTimeMillis());
        lastMsg = null;
        addItem(new PillItem(text, 0), false);
    }

    public void appendError(String text) {
        lastMsg = null;
        addItem(new PillItem("Lỗi: " + text, 1), true);
    }

    public String getPreview() { return preview; }
    public long getLastTime() { return lastTime; }

    public void jumpToBottom() {
        JScrollBar bar = scroll.getVerticalScrollBar();
        scrollTimer.stop();
        bar.setValue(bar.getMaximum() - bar.getVisibleAmount());
        atBottom = true;
    }

    /** Chèn nhãn ngày ("Hôm nay", "Hôm qua", dd/MM/yyyy) khi sang ngày mới. */
    private void ensureDay(long time) {
        LocalDate day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
        if (day.equals(lastDay)) return;
        lastDay = day;
        lastMsg = null;
        LocalDate today = LocalDate.now();
        String label = day.equals(today) ? "Hôm nay"
                : day.equals(today.minusDays(1)) ? "Hôm qua"
                : day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        addItem(new PillItem(label, 0), false);
    }

    // ---------------- Cảm xúc ----------------

    private final Map<Long, MsgItem> byId = new HashMap<>();
    private BiConsumer<Long, String> onReact = (id, emoji) -> { };

    /** ChatFrame gắn hàm gửi gói REACT (mã tin, cảm xúc hoặc null để gỡ). */
    public void setOnReact(BiConsumer<Long, String> f) { onReact = f; }

    /** Áp dụng một cảm xúc nhận từ server. emoji null = người đó gỡ cảm xúc. */
    public void applyReaction(long id, String user, String emoji) {
        MsgItem it = byId.get(id);
        if (it == null || user == null) return;
        if (emoji == null) it.reactions.remove(user);
        else { it.reactions.remove(user); it.reactions.put(user, emoji); }
        boolean stick = atBottom;
        canvas.dirty = true;
        canvas.revalidate();
        canvas.repaint();
        if (stick) SwingUtilities.invokeLater(this::jumpToBottom);
    }

    /** Thả cảm xúc; thả lại đúng cảm xúc đang có thì gỡ. */
    private void react(MsgItem mi, String emoji) {
        if (!mi.canReact()) return;
        onReact.accept(mi.m.id, emoji.equals(mi.reactions.get(me)) ? null : emoji);
    }

    /** Bấm vào pill cảm xúc: gỡ của mình, hoặc thả theo cảm xúc phổ biến nhất. */
    private void quickToggle(MsgItem mi) {
        if (mi.reactions.containsKey(me)) onReact.accept(mi.m.id, null);
        else onReact.accept(mi.m.id, mi.groups().get(0).getKey());
    }

    private void showReactPicker(MsgItem mi) {
        JPopupMenu pop = Stickers.reactPopup(mi.reactions.get(me), em -> react(mi, em));
        Dimension ps = pop.getPreferredSize();
        Rectangle b = mi.reactButton();
        int x = Math.max(4, Math.min(canvas.getWidth() - ps.width - 4, b.x + b.width / 2 - ps.width / 2));
        pop.show(canvas, x, mi.y + b.y - ps.height - 6);
    }

    public void appendMessage(Message m) {
        ensureDay(m.time);
        boolean mine = me.equals(m.from);
        MsgItem it = switch (m.content) {
            case TEXT -> new TextMsg(m, mine);
            case STICKER -> new StickerMsg(m, mine);
            case IMAGE -> {
                MsgItem im = ImageMsg.tryCreate(this, m, mine);
                yield im != null ? im : new FileMsg(m, mine);
            }
            case FILE -> new FileMsg(m, mine);
            case AUDIO -> new AudioMsg(m, mine);
        };
        boolean same = lastMsg != null && Objects.equals(lastMsg.m.from, m.from)
                && m.time - lastMsg.m.time < 5 * 60_000L;
        it.first = !same;
        if (same) lastMsg.last = false;
        lastMsg = it;

        String body = switch (m.content) {
            case TEXT -> m.text == null ? "" : m.text.replace('\n', ' ');
            case IMAGE -> "Đã gửi một ảnh";
            case FILE -> "Đã gửi tệp: " + (m.fileName == null ? "" : m.fileName);
            case AUDIO -> "Đã gửi tin nhắn thoại";
            case STICKER -> Stickers.label(m.text);
        };
        if (m.id > 0) byId.put(m.id, it);
        preview = mine ? "Bạn: " + body : (scope == Message.Scope.PRIVATE ? body : Profiles.display(m.from) + ": " + body);
        lastTime = m.time;
        addItem(it, mine);
    }

    // ---------------- Nội bộ ----------------

    private void addItem(Item it, boolean force) {
        boolean stick = atBottom || force;
        items.add(it);
        canvas.dirty = true;
        canvas.revalidate();
        canvas.repaint();
        if (stick) {
            atBottom = true;
            SwingUtilities.invokeLater(() -> { if (!scrollTimer.isRunning()) scrollTimer.start(); });
        }
    }

    private void saveMessage(Message m) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(safeName(m)));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(fc.getSelectedFile().toPath(), m.data);
                appendSystem("Đã lưu " + fc.getSelectedFile().getName());
            } catch (IOException ex) {
                appendError("Không lưu được tệp: " + ex.getMessage());
            }
        }
    }

    private void openMessage(Message m) {
        try {
            File dir = Files.createTempDirectory("minichat").toFile();
            File f = new File(dir, safeName(m));
            Files.write(f.toPath(), m.data);
            f.deleteOnExit();
            dir.deleteOnExit();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                Desktop.getDesktop().open(f);
            else appendError("Hệ thống không hỗ trợ mở tệp, hãy dùng Lưu");
        } catch (Exception ex) {
            appendError("Không mở được tệp: " + ex.getMessage());
        }
    }

    private static String safeName(Message m) {
        String n = m.fileName == null || m.fileName.isBlank() ? "tep" : new File(m.fileName).getName();
        return n.isEmpty() ? "tep" : n;
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    static String mmss(double sec) {
        int s = (int) Math.round(sec);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private static BufferedImage thumb(BufferedImage src, int maxW, int maxH) {
        double k = Math.min(1.0, Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight()));
        int tw = Math.max(1, (int) Math.round(src.getWidth() * k));
        int th = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage cur = src;
        // thu nhỏ từng bước (chia đôi) cho ảnh mượt
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            BufferedImage n = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = n.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, n.getWidth(), n.getHeight(), null);
            g.dispose();
            cur = n;
        }
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cur, 0, 0, tw, th, null);
        g.dispose();
        return out;
    }

    // =====================================================================
    //  Canvas
    // =====================================================================
    private final class Canvas extends JComponent implements Scrollable {
        boolean dirty = true;
        int laidW = -1;
        int contentH = 0;
        Item hover;

        Canvas() {
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { updateHover(e); }
                @Override public void mouseExited(MouseEvent e) {
                    if (hover != null) { hover.hoverPoint = null; hover = null; repaint(); }
                    setCursor(Cursor.getDefaultCursor());
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    Item it = itemAt(e.getY());
                    if (it == null) return;
                    Point p = new Point(e.getX(), e.getY() - it.y);
                    if (it instanceof MsgItem mi && mi.canReact()) {
                        if (mi.reactButton().contains(p)) { showReactPicker(mi); return; }
                        Rectangle pill = mi.pillRect();
                        if (pill != null && pill.contains(p)) { quickToggle(mi); return; }
                        // nháy đúp vào tin nhắn (không phải ảnh/tệp/ghi âm) để thả tim
                        if (e.getClickCount() == 2 && mi.inBubble(p) && it.clickAt(p) == null) { react(mi, "❤️"); return; }
                    }
                    Runnable r = it.clickAt(p);
                    if (r != null) r.run();
                }
                @Override public void mousePressed(MouseEvent e) { popup(e); }
                @Override public void mouseReleased(MouseEvent e) { popup(e); }
                private void popup(MouseEvent e) {
                    if (!e.isPopupTrigger()) return;
                    Item it = itemAt(e.getY());
                    if (it == null) return;
                    JPopupMenu menu = Widgets.popup();
                    if (it instanceof MsgItem mi && mi.canReact()) {
                        menu.add(new Stickers.ReactBar(mi.reactions.get(me), em -> react(mi, em), () -> menu.setVisible(false)));
                    }
                    it.fillMenu(menu);
                    if (menu.getComponentCount() > 0) menu.show(Canvas.this, e.getX(), e.getY());
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            setToolTipText(null);
        }

        private void updateHover(MouseEvent e) {
            Item it = itemAt(e.getY());
            if (it != hover) {
                if (hover != null) hover.hoverPoint = null;
                hover = it;
            }
            if (it != null) {
                Point p = new Point(e.getX(), e.getY() - it.y);
                it.hoverPoint = p;
                boolean hand = it.clickAt(p) != null;
                if (it instanceof MsgItem mi && mi.canReact()) {
                    Rectangle pill = mi.pillRect();
                    hand |= mi.reactButton().contains(p) || (pill != null && pill.contains(p));
                }
                setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
            // vẽ lại cả vùng nhìn thấy: thẻ "ai đã thả cảm xúc" có thể tràn ra ngoài phần tử
            repaint();
        }

        Item itemAt(int y) {
            relayout(getWidth());
            for (Item it : items) if (y >= it.y && y < it.y + it.h) return it;
            return null;
        }

        void relayout(int w) {
            if (w <= 0) w = 600;
            if (!dirty && w == laidW) return;
            laidW = w;
            dirty = false;
            int y = 10;
            for (Item it : items) {
                it.y = y;
                it.h = it.layout(w);
                y += it.h;
            }
            contentH = y + 14;
        }

        @Override public Dimension getPreferredSize() {
            int w = getParent() != null ? getParent().getWidth() : 600;
            relayout(w);
            return new Dimension(Math.max(w, 100), contentH);
        }

        @Override protected void paintComponent(Graphics gr) {
            relayout(getWidth());
            if (contentH != getHeight() && getParent() != null
                    && getHeight() != Math.max(contentH, getParent().getHeight()))
                SwingUtilities.invokeLater(this::revalidate);
            Graphics2D g = Theme.aa(gr);
            Rectangle clip = gr.getClipBounds();
            if (clip == null) clip = new Rectangle(0, 0, getWidth(), getHeight());
            if (items.isEmpty()) paintEmpty(g);
            for (Item it : items) {
                if (it.y + it.h < clip.y) continue;
                if (it.y > clip.y + clip.height) break;
                Graphics2D ig = (Graphics2D) g.create();
                ig.translate(0, it.y);
                it.paint(ig, getWidth(), it == hover);
                ig.dispose();
            }
            if (hover instanceof MsgItem mi && mi.hoverPoint != null) {
                Rectangle pill = mi.pillRect();
                if (pill != null && pill.contains(mi.hoverPoint)) {
                    Graphics2D og = (Graphics2D) g.create();
                    og.translate(0, mi.y);
                    mi.paintWhoReacted(og, getWidth(), pill, getVisibleRect().y - mi.y);
                    og.dispose();
                }
            }
            g.dispose();
        }

        private void paintEmpty(Graphics2D g) {
            int cx = getWidth() / 2;
            int cy = Math.max(120, (getParent() != null ? getParent().getHeight() : 400) / 2 - 20);
            g.setColor(Theme.alpha(Theme.ACCENT, 40));
            g.fill(new Ellipse2D.Double(cx - 36, cy - 60, 72, 72));
            Icons.draw(g, Icons.Kind.CHAT, cx - 18, cy - 42, 36, Theme.alpha(Theme.ACCENT, 200));
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.setColor(Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            String a = "Chưa có tin nhắn nào";
            g.drawString(a, cx - fm.stringWidth(a) / 2f, cy + 38);
            g.setFont(Theme.font(Font.PLAIN, 13f));
            g.setColor(Theme.TEXT_MUTED);
            fm = g.getFontMetrics();
            String b = "Hãy gửi lời chào đầu tiên!";
            g.drawString(b, cx - fm.stringWidth(b) / 2f, cy + 62);
        }

        // Scrollable
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 36; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(60, r.height - 60); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport vp && vp.getHeight() > contentH;
        }
    }

    // =====================================================================
    //  Các phần tử trong canvas
    // =====================================================================
    private abstract class Item {
        int y, h;
        Point hoverPoint;
        abstract int layout(int w);
        abstract void paint(Graphics2D g, int w, boolean hover);
        Runnable clickAt(Point p) { return null; }
        void fillMenu(JPopupMenu m) { }
    }

    /** Thông báo hệ thống / lỗi / ngày: viên thuốc ở giữa. kind 0 = hệ thống, 1 = lỗi. */
    private final class PillItem extends Item {
        final String text;
        final int kind;
        PillItem(String text, int kind) { this.text = text; this.kind = kind; }
        @Override int layout(int w) { return 38; }
        @Override void paint(Graphics2D g, int w, boolean hover) {
            FontMetrics fm = Theme.fm(PILL);
            String s = Theme.ellipsize(text, fm, Math.max(60, w - 100));
            int tw = Theme.textWidth(fm, s);
            int pw = tw + 28, ph = 24;
            int x = (w - pw) / 2, py = 10;
            g.setColor(kind == 1 ? Theme.alpha(Theme.RED, 46) : new Color(255, 255, 255, 16));
            g.fill(Theme.rr(x, py, pw, ph, ph / 2.0));
            g.setFont(PILL);
            g.setColor(kind == 1 ? new Color(0xFF8E92) : Theme.TEXT_DIM);
            Theme.drawText(g, s, x + 14, py + (ph - fm.getHeight()) / 2f + fm.getAscent());
        }
    }

    /** Tin nhắn có bong bóng. */
    private abstract class MsgItem extends Item {
        final Message m;
        final boolean mine;
        final String time;
        boolean first = true, last = true;
        int bx, by, bw, bh;

        MsgItem(Message m, boolean mine) {
            this.m = m;
            this.mine = mine;
            this.time = Instant.ofEpochMilli(m.time).atZone(ZoneId.systemDefault()).format(HM);
        }

        abstract void measure(int maxW);
        abstract void paintBody(Graphics2D g, boolean hover);
        boolean paintsOwnBackground() { return false; }
        boolean showName() { return !mine && first && scope != Message.Scope.PRIVATE; }

        Shape shape(double x, double y, double w, double h) {
            double R = Theme.BUBBLE_RADIUS, r = Theme.BUBBLE_SMALL;
            double top = first ? R : r, bot = last ? R : r;
            return mine ? Theme.roundedPath(x, y, w, h, R, top, bot, R)
                        : Theme.roundedPath(x, y, w, h, top, R, R, bot);
        }

        @Override int layout(int w) {
            int avatarSpace = mine ? 0 : AVATAR + 10;
            int avail = w - 2 * SIDE - avatarSpace;
            int maxW = (int) Math.max(140, Math.min(avail * 0.8, 520));
            maxW = Math.min(maxW, Math.max(60, avail));
            // chừa chỗ cho nút thả cảm xúc bên cạnh bong bóng
            maxW = Math.min(maxW, Math.max(60, avail - 44));
            measure(maxW);
            int top = first ? 12 : 2;
            by = top + (showName() ? 20 : 0);
            bx = mine ? w - SIDE - bw : SIDE + avatarSpace;
            return by + bh + 1 + (reactions.isEmpty() ? 0 : 18);
        }

        // ---------- Cảm xúc ----------
        /** người thả -> cảm xúc (mỗi người một cảm xúc, thả lại cùng loại để gỡ). */
        final Map<String, String> reactions = new LinkedHashMap<>();

        boolean canReact() { return m.id > 0; }

        boolean inBubble(Point p) {
            return p.x >= bx && p.x < bx + bw && p.y >= by && p.y < by + bh;
        }

        /** Nút mặt cười hiện bên cạnh bong bóng khi rê chuột. */
        Rectangle reactButton() {
            int d = 30;
            int x = mine ? bx - d - 8 : bx + bw + 8;
            return new Rectangle(x, by + Math.max(0, Math.min(bh - d, (bh - d) / 2)), d, d);
        }

        /** Cảm xúc sắp theo số lượt (nhiều trước), cùng số thì theo thứ tự thả. */
        List<Map.Entry<String, List<String>>> groups() {
            Map<String, List<String>> g = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : reactions.entrySet())
                g.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            List<Map.Entry<String, List<String>>> l = new ArrayList<>(g.entrySet());
            l.sort((a, b) -> b.getValue().size() - a.getValue().size());
            return l;
        }

        private String pillEmojis() {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Map.Entry<String, List<String>> e : groups()) { if (n++ == 3) break; sb.append(e.getKey()); }
            return sb.toString();
        }

        Rectangle pillRect() {
            if (reactions.isEmpty()) return null;
            FontMetrics fe = Theme.fm(REACT_FONT), fc = Theme.fm(REACT_COUNT);
            int w = Theme.textWidth(fe, pillEmojis()) + 16;
            if (reactions.size() > 1) w += fc.stringWidth(String.valueOf(reactions.size())) + 5;
            int x = mine ? bx - 4 : bx + bw - w + 4;
            if (!mine && x < bx + 8) x = bx + 8;
            if (mine && x + w > bx + bw - 8) x = Math.max(bx - 4, bx + bw - 8 - w);
            return new Rectangle(x, by + bh - 6, w, 24);
        }

        private void paintReactions(Graphics2D g, boolean hover) {
            Rectangle r = pillRect();
            if (r != null) {
                boolean mineIn = reactions.containsKey(me);
                g.setColor(Theme.BG);
                g.fill(Theme.rr(r.x - 2, r.y - 2, r.width + 4, r.height + 4, (r.height + 4) / 2.0));
                g.setColor(mineIn ? Theme.mix(Theme.SURFACE, Theme.ACCENT, 0.35) : Theme.SURFACE);
                g.fill(Theme.rr(r.x, r.y, r.width, r.height, r.height / 2.0));
                if (mineIn) {
                    g.setColor(Theme.alpha(Theme.ACCENT, 170));
                    g.setStroke(new BasicStroke(1.1f));
                    g.draw(Theme.rr(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, r.height / 2.0));
                }
                g.setFont(REACT_FONT);
                FontMetrics fe = g.getFontMetrics();
                String em = pillEmojis();
                Theme.drawText(g, em, r.x + 8, r.y + (r.height - fe.getHeight()) / 2f + fe.getAscent());
                if (reactions.size() > 1) {
                    g.setFont(REACT_COUNT);
                    FontMetrics fc = g.getFontMetrics();
                    g.setColor(Theme.TEXT_DIM);
                    g.drawString(String.valueOf(reactions.size()), r.x + 8 + Theme.textWidth(fe, em) + 5,
                            r.y + (r.height - fc.getHeight()) / 2f + fc.getAscent());
                }
            }
            if (hover && canReact()) {
                Rectangle b = reactButton();
                boolean over = hoverPoint != null && b.contains(hoverPoint);
                g.setColor(over ? Theme.alpha(Theme.ACCENT, 120) : new Color(255, 255, 255, 16));
                g.fill(new Ellipse2D.Double(b.x, b.y, b.width, b.height));
                Icons.draw(g, Icons.Kind.SMILE, b.x + 6, b.y + 6, b.width - 12, over ? Color.WHITE : Theme.TEXT_DIM);
            }
        }

        /** Thẻ nổi liệt kê ai đã thả cảm xúc gì (vẽ đè lên trên các phần tử khác). */
        void paintWhoReacted(Graphics2D g, int w, Rectangle pill, int visibleTop) {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : groups()) {
                StringBuilder sb = new StringBuilder(e.getKey()).append("  ");
                for (int i = 0; i < e.getValue().size(); i++) {
                    String u = e.getValue().get(i);
                    if (i > 0) sb.append(", ");
                    sb.append(u.equals(me) ? "Bạn" : Profiles.display(u));
                }
                lines.add(sb.toString());
            }
            Font f = Theme.font(Font.PLAIN, 13f);
            FontMetrics fm = Theme.fm(f);
            int cw = 0;
            for (String l : lines) cw = Math.max(cw, Theme.textWidth(fm, l));
            cw = Math.min(cw + 24, w - 16);
            int lh = fm.getHeight() + 4, ch = lines.size() * lh + 14;
            int cx = Math.max(8, Math.min(w - cw - 8, pill.x + pill.width / 2 - cw / 2));
            int cy = pill.y - ch - 6;
            if (cy < visibleTop + 4) cy = pill.y + pill.height + 6;   // không đủ chỗ phía trên: lật xuống dưới
            Theme.paintShadow(g, cx, cy, cw, ch, 12, 6);
            g.setColor(new Color(0x0A0D14));
            g.fill(Theme.rr(cx, cy, cw, ch, 12));
            g.setColor(Theme.BORDER);
            g.draw(Theme.rr(cx + 0.5, cy + 0.5, cw - 1, ch - 1, 12));
            g.setFont(f);
            g.setColor(Theme.TEXT);
            for (int i = 0; i < lines.size(); i++)
                Theme.drawText(g, Theme.ellipsize(lines.get(i), fm, cw - 24), cx + 12, cy + 7 + i * lh + 2 + fm.getAscent());
        }

        @Override void paint(Graphics2D g, int w, boolean hover) {
            if (!mine && first) {
                Theme.paintAvatar(g, SIDE, 12, AVATAR, m.from);
                if (showName()) {
                    g.setFont(NAME);
                    g.setColor(Theme.avatarColorLight(m.from));
                    FontMetrics fm = g.getFontMetrics();
                    Theme.drawText(g, Theme.ellipsize(Profiles.display(m.from), fm, Math.max(40, w - bx - SIDE)),
                            bx + 4, 12 + fm.getAscent());
                }
            }
            if (!paintsOwnBackground()) {
                Shape s = shape(bx, by, bw, bh);
                if (mine) g.setPaint(new GradientPaint(bx, by, Theme.ACCENT, bx + bw, by + bh, Theme.ACCENT2));
                else g.setColor(Theme.SURFACE);
                g.fill(s);
            }
            Graphics2D bg = (Graphics2D) g.create();
            bg.translate(bx, by);
            paintBody(bg, hover);
            bg.dispose();
            paintReactions(g, hover);
        }

        Color timeColor() { return mine ? new Color(255, 255, 255, 175) : Theme.TEXT_MUTED; }
    }

    private final class TextMsg extends MsgItem {
        List<String> lines;
        boolean inline;
        int lineH, timeW;
        static final int PX = 13, PY = 8;

        TextMsg(Message m, boolean mine) { super(m, mine); }

        @Override void measure(int maxW) {
            FontMetrics fm = Theme.fm(BODY);
            lineH = (int) Math.ceil(fm.getHeight() * 1.1);
            int inner = maxW - 2 * PX;
            lines = Theme.wrap(m.text, fm, inner);
            int widest = 0;
            for (String l : lines) widest = Math.max(widest, Theme.textWidth(fm, l));
            int lastW = Theme.textWidth(fm, lines.get(lines.size() - 1));
            timeW = Theme.fm(TIME).stringWidth(time);
            inline = lastW + 10 + timeW <= inner;
            int cw = Math.max(widest, inline ? lastW + 10 + timeW : timeW);
            bw = cw + 2 * PX;
            bh = lines.size() * lineH + (inline ? 0 : 15) + 2 * PY;
        }

        @Override void paintBody(Graphics2D g, boolean hover) {
            g.setFont(BODY);
            g.setColor(mine ? Color.WHITE : Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < lines.size(); i++)
                Theme.drawText(g, lines.get(i), PX, PY + i * lineH + (lineH - fm.getHeight()) / 2f + fm.getAscent());
            g.setFont(TIME);
            g.setColor(timeColor());
            g.drawString(time, bw - PX - timeW, bh - PY - 3);
        }

        @Override void fillMenu(JPopupMenu menu) {
            Widgets.menuItem(menu, "Sao chép", () ->
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(m.text == null ? "" : m.text), null));
        }
    }

    private static final class ImageMsg {
        static MsgItem tryCreate(ChatPanel p, Message m, boolean mine) {
            if (m.data == null) return null;
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(m.data));
                if (img == null) return null;
                return p.new ImageItem(m, mine, thumb(img, 640, 680), img.getWidth(), img.getHeight());
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
    }

    private final class ImageItem extends MsgItem {
        final BufferedImage thumb;
        final int iw, ih;

        ImageItem(Message m, boolean mine, BufferedImage thumb, int iw, int ih) {
            super(m, mine);
            this.thumb = thumb;
            this.iw = iw;
            this.ih = ih;
        }

        @Override boolean paintsOwnBackground() { return true; }

        @Override void measure(int maxW) {
            double k = Math.min(1.0, Math.min(Math.min(maxW, 320) / (double) iw, 340.0 / ih));
            bw = Math.max(80, (int) Math.round(iw * k));
            bh = Math.max(50, (int) Math.round(ih * k));
        }

        @Override void paintBody(Graphics2D g, boolean hover) {
            Shape s = shape(0, 0, bw, bh);
            g.setColor(Theme.SURFACE);
            g.fill(s);
            Graphics2D c = (Graphics2D) g.create();
            c.clip(s);
            // vẽ ảnh phủ kín (cover) nếu tỉ lệ bị làm tròn
            double k = Math.max(bw / (double) thumb.getWidth(), bh / (double) thumb.getHeight());
            int dw = (int) Math.round(thumb.getWidth() * k), dh = (int) Math.round(thumb.getHeight() * k);
            c.drawImage(thumb, (bw - dw) / 2, (bh - dh) / 2, dw, dh, null);
            if (hover) {
                c.setColor(new Color(0, 0, 0, 90));
                c.fillRect(0, 0, bw, bh);
                c.setColor(new Color(0, 0, 0, 150));
                c.fill(new Ellipse2D.Double(bw / 2.0 - 22, bh / 2.0 - 22, 44, 44));
                Icons.draw(c, Icons.Kind.DOWNLOAD, bw / 2.0 - 12, bh / 2.0 - 12, 24, Color.WHITE);
            }
            c.dispose();
            Graphics2D t = (Graphics2D) g.create();
            t.setFont(TIME);
            FontMetrics fm = t.getFontMetrics();
            int tw = fm.stringWidth(time);
            t.setColor(new Color(0, 0, 0, 120));
            t.fill(Theme.rr(bw - tw - 20, bh - 24, tw + 14, 18, 9));
            t.setColor(Color.WHITE);
            t.drawString(time, bw - tw - 13, bh - 24 + (18 - fm.getHeight()) / 2f + fm.getAscent());
            t.dispose();
        }

        @Override Runnable clickAt(Point p) {
            return inBubble(p) ? () -> saveMessage(m) : null;
        }

        boolean inBubble(Point p) {
            return p.x >= bx && p.x < bx + bw && p.y >= by && p.y < by + bh;
        }

        @Override void fillMenu(JPopupMenu menu) {
            Widgets.menuItem(menu, "Lưu ảnh về máy…", () -> saveMessage(m));
            Widgets.menuItem(menu, "Mở ảnh", () -> openMessage(m));
        }
    }

    /** Sticker: vẽ to, không có bong bóng; giờ gửi nằm trong viên thuốc nhỏ bên dưới. */
    private final class StickerMsg extends MsgItem {
        static final int BASE = 128;
        int base = BASE;

        StickerMsg(Message m, boolean mine) { super(m, mine); }

        @Override boolean paintsOwnBackground() { return true; }

        @Override void measure(int maxW) {
            Dimension d = Stickers.size(m.text, BASE);
            base = d.width > maxW ? Math.max(48, BASE * maxW / d.width) : BASE;
            d = Stickers.size(m.text, base);
            bw = d.width;
            bh = d.height + 24;
        }

        @Override void paintBody(Graphics2D g, boolean hover) {
            int b = hover ? base + 6 : base;
            Dimension d = Stickers.size(m.text, b);
            Dimension d0 = Stickers.size(m.text, base);
            Stickers.paint(g, m.text, (bw - d.width) / 2, (d0.height - d.height) / 2, b);
            g.setFont(TIME);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(time), pw = tw + 14, ph = 18;
            int px = mine ? bw - pw : 0, py = bh - ph;
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(Theme.rr(px, py, pw, ph, ph / 2.0));
            g.setColor(new Color(255, 255, 255, 210));
            g.drawString(time, px + 7, py + (ph - fm.getHeight()) / 2f + fm.getAscent());
        }
    }

    private final class FileMsg extends MsgItem {
        FileMsg(Message m, boolean mine) { super(m, mine); }

        @Override void measure(int maxW) {
            bw = Math.min(maxW, 290);
            bh = 68;
        }

        boolean inBubble(Point p) {
            return p.x >= bx && p.x < bx + bw && p.y >= by && p.y < by + bh;
        }

        @Override void paintBody(Graphics2D g, boolean hover) {
            if (hover) {
                g.setColor(new Color(255, 255, 255, 22));
                g.fill(shape(0, 0, bw, bh));
            }
            // ô biểu tượng
            if (mine) g.setColor(new Color(255, 255, 255, 48));
            else g.setPaint(new GradientPaint(12, 12, Theme.ACCENT, 56, 56, Theme.ACCENT2));
            g.fill(Theme.rr(12, 12, 44, 44, 12));
            Icons.draw(g, hover ? Icons.Kind.DOWNLOAD : Icons.Kind.FILE, 23, 23, 22, Color.WHITE);

            int tx = 66;
            int maxText = bw - tx - 14;
            g.setFont(Theme.font(Font.BOLD, 14f));
            g.setColor(mine ? Color.WHITE : Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            Theme.drawText(g, Theme.ellipsize(safeName(m), fm, maxText), tx, 12 + fm.getAscent() + 2);

            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.setColor(timeColor());
            fm = g.getFontMetrics();
            String size = human(m.data == null ? 0 : m.data.length);
            g.drawString(size, tx, 44 + fm.getAscent() - 8);
            g.setFont(TIME);
            int tw = g.getFontMetrics().stringWidth(time);
            g.drawString(time, bw - 12 - tw, bh - 12);
        }

        @Override Runnable clickAt(Point p) {
            return inBubble(p) ? () -> saveMessage(m) : null;
        }

        @Override void fillMenu(JPopupMenu menu) {
            Widgets.menuItem(menu, "Lưu về máy…", () -> saveMessage(m));
            Widgets.menuItem(menu, "Mở tệp", () -> openMessage(m));
        }
    }

    private final class AudioMsg extends MsgItem {
        final double seconds;
        final float[] bars;
        Clip clip;
        boolean playing;
        long startedAt;

        AudioMsg(Message m, boolean mine) {
            super(m, mine);
            this.seconds = m.data == null ? 0 : AudioRecorder.durationOf(m.data);
            Random rnd = new Random(m.data == null ? 1 : java.util.Arrays.hashCode(m.data) * 31L + m.data.length);
            bars = new float[48];
            float prev = 0.5f;
            for (int i = 0; i < bars.length; i++) {
                prev = Math.max(0.18f, Math.min(1f, prev * 0.45f + rnd.nextFloat() * 0.65f));
                bars[i] = prev;
            }
        }

        @Override void measure(int maxW) {
            bw = Math.max(Math.min(maxW, 280), Math.min(maxW, 210));
            bh = 62;
        }

        private Rectangle downloadRect() { return new Rectangle(bw - 34, 8, 26, 26); }

        @Override void paintBody(Graphics2D g, boolean hover) {
            // nút phát
            double d = 38;
            if (mine) g.setColor(new Color(255, 255, 255, 235));
            else g.setPaint(new GradientPaint(10, 8, Theme.ACCENT, 48, 46, Theme.ACCENT2));
            g.fill(new Ellipse2D.Double(10, 9, d, d));
            if (playing) Icons.draw(g, Icons.Kind.STOP, 19, 18, 20, mine ? Theme.ACCENT : Color.WHITE);
            else Icons.draw(g, Icons.Kind.PLAY, 19.5, 18.5, 20, mine ? Theme.ACCENT : Color.WHITE);

            // dạng sóng
            int x0 = 58, x1 = bw - 42;
            int n = Math.max(8, (x1 - x0) / 5);
            double progress = 0;
            if (playing) progress = Math.min(1.0, (System.nanoTime() - startedAt) / 1e9 / Math.max(0.1, seconds));
            int cy = 28;
            for (int i = 0; i < n; i++) {
                float hh = bars[i * bars.length / n] * 26f;
                boolean played = playing && i < progress * n;
                Color c = mine ? (played ? Color.WHITE : new Color(255, 255, 255, 110))
                               : (played ? Theme.ACCENT2 : Theme.alpha(Theme.TEXT_DIM, 120));
                g.setColor(c);
                g.fill(Theme.rr(x0 + i * 5, cy - hh / 2, 3, hh, 1.5));
            }
            // tải xuống
            Rectangle dr = downloadRect();
            boolean overDl = hoverPoint != null && dr.contains(hoverPoint.x - bx, hoverPoint.y - by);
            Icons.draw(g, Icons.Kind.DOWNLOAD, dr.x + 3, dr.y + 3, 20, overDl
                    ? (mine ? Color.WHITE : Theme.TEXT) : timeColor());

            // thời lượng + giờ
            g.setFont(TIME);
            g.setColor(timeColor());
            String dur = playing ? mmss(progress * seconds) + " / " + mmss(seconds) : mmss(seconds);
            g.drawString(dur, x0, bh - 9);
            int tw = g.getFontMetrics().stringWidth(time);
            g.drawString(time, bw - 12 - tw, bh - 9);
        }

        @Override Runnable clickAt(Point p) {
            if (p.x < bx || p.x >= bx + bw || p.y < by || p.y >= by + bh) return null;
            Rectangle dr = downloadRect();
            if (dr.contains(p.x - bx, p.y - by)) return () -> saveMessage(m);
            return this::toggle;
        }

        void toggle() {
            if (playing) {
                if (clip != null) clip.stop();
                playing = false;
                canvas.repaint(0, y, canvas.getWidth(), h);
                return;
            }
            try {
                clip = AudioRecorder.play(m.data);
                startedAt = System.nanoTime();
                playing = true;
                clip.addLineListener(ev -> {
                    if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP)
                        SwingUtilities.invokeLater(() -> { playing = false; canvas.repaint(0, y, canvas.getWidth(), h); });
                });
                if (!animTimer.isRunning()) animTimer.start();
            } catch (Exception ex) {
                playing = false;
                appendError("Không phát được âm thanh: " + ex.getMessage());
            }
        }

        @Override void fillMenu(JPopupMenu menu) {
            Widgets.menuItem(menu, playing ? "Dừng phát" : "Nghe", this::toggle);
            Widgets.menuItem(menu, "Lưu về máy…", () -> saveMessage(m));
        }
    }
}
