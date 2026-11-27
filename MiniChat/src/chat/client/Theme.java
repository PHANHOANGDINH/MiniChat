package chat.client;

import chat.common.Message;

import javax.swing.BorderFactory;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hệ thống thiết kế (design system) dùng chung cho toàn bộ giao diện client:
 * màu sắc, phông chữ, bo góc, hàm vẽ avatar / đường bo và các thiết lập UIManager.
 */
public final class Theme {
    private Theme() { }

    // ---------- Màu sắc (giao diện tối) ----------
    public static final Color SIDEBAR = new Color(0x131722);
    public static final Color BG = new Color(0x0D1019);
    public static final Color PANEL = new Color(0x171B27);
    public static final Color SURFACE = new Color(0x232A3B);
    public static final Color INPUT = new Color(0x1E2432);
    public static final Color BORDER = new Color(0x2A3145);
    public static final Color TEXT = new Color(0xEAEDF5);
    public static final Color TEXT_DIM = new Color(0xA7B0C6);
    public static final Color TEXT_MUTED = new Color(0x6F7992);
    public static final Color ACCENT = new Color(0x7C5CFF);
    public static final Color ACCENT2 = new Color(0x3D8BFF);
    public static final Color GREEN = new Color(0x3DD68C);
    public static final Color RED = new Color(0xF2555A);
    public static final Color HOVER = new Color(255, 255, 255, 14);
    public static final Color PRESSED = new Color(255, 255, 255, 28);
    public static final Color SELECTED = new Color(0x7C, 0x5C, 0xFF, 64);

    // ---------- Kích thước ----------
    public static final int RADIUS = 14;
    public static final int BUBBLE_RADIUS = 18;
    public static final int BUBBLE_SMALL = 5;

    // ---------- Phông chữ (Segoe UI hỗ trợ đầy đủ tiếng Việt) ----------
    public static final String FAMILY = pickFamily();
    private static final Map<Integer, Font> FONTS = new HashMap<>();

    private static String pickFamily() {
        Set<String> names = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[] { "Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue",
                "Noto Sans", "Ubuntu", "DejaVu Sans" })
            if (names.contains(f)) return f;
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        int key = style * 100000 + Math.round(size * 10);
        synchronized (FONTS) {
            return FONTS.computeIfAbsent(key, k -> new Font(FAMILY, style, 12).deriveFont(style, size));
        }
    }

    private static final Graphics2D MEASURE = aa(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics());

    /** FontMetrics dùng để đo chữ (cùng cấu hình khử răng cưa với lúc vẽ). */
    public static FontMetrics fm(Font f) {
        return MEASURE.getFontMetrics(f);
    }

    // ---------- Vẽ ----------

    /** Tạo bản sao Graphics2D đã bật khử răng cưa. Người gọi nhớ dispose(). */
    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static GradientPaint accentGradient(float x1, float y1, float x2, float y2) {
        return new GradientPaint(x1, y1, ACCENT, x2, y2, ACCENT2);
    }

    /** Hình chữ nhật bo góc với bán kính riêng cho từng góc. */
    public static Path2D roundedPath(double x, double y, double w, double h,
                                     double tl, double tr, double br, double bl) {
        final double k = 0.5523;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x + tl, y);
        p.lineTo(x + w - tr, y);
        p.curveTo(x + w - tr + tr * k, y, x + w, y + tr - tr * k, x + w, y + tr);
        p.lineTo(x + w, y + h - br);
        p.curveTo(x + w, y + h - br + br * k, x + w - br + br * k, y + h, x + w - br, y + h);
        p.lineTo(x + bl, y + h);
        p.curveTo(x + bl - bl * k, y + h, x, y + h - bl + bl * k, x, y + h - bl);
        p.lineTo(x, y + tl);
        p.curveTo(x, y + tl - tl * k, x + tl - tl * k, y, x + tl, y);
        p.closePath();
        return p;
    }

    public static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    /** Vẽ bóng mềm phía sau một hình chữ nhật bo góc. */
    public static void paintShadow(Graphics2D g, double x, double y, double w, double h, double r, int layers) {
        for (int i = layers; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 10));
            g.fill(rr(x - i, y - i + layers * 0.5, w + 2 * i, h + 2 * i, r + i));
        }
    }

    // ---------- Chữ có emoji / ký hiệu: tự chuyển sang phông dự phòng ----------
    private static final String[] FALLBACKS = pickFallbacks();

    private static String[] pickFallbacks() {
        Set<String> names = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        List<String> l = new ArrayList<>();
        for (String f : new String[] { "Segoe UI Emoji", "Segoe UI Symbol", "Apple Color Emoji",
                "Noto Color Emoji", "Noto Emoji", "Symbola", "DejaVu Sans" })
            if (names.contains(f)) l.add(f);
        l.add(Font.DIALOG);
        return l.toArray(new String[0]);
    }

    private static final Map<String, Font> FALLBACK_FONTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Font fallbackFor(Font base, int cp) {
        for (String f : FALLBACKS) {
            Font c = FALLBACK_FONTS.computeIfAbsent(f + "|" + base.getStyle() + "|" + base.getSize2D(),
                    k -> new Font(f, base.getStyle(), 12).deriveFont(base.getStyle(), base.getSize2D()));
            if (c.canDisplay(cp)) return c;
        }
        return base;
    }

    /** Chia chuỗi thành các đoạn liền nhau cùng phông (phông chính hoặc phông dự phòng). */
    private static void runs(String s, Font base, java.util.function.BiConsumer<String, Font> out) {
        int i = 0, n = s.length();
        while (i < n) {
            int cp = s.codePointAt(i);
            boolean ok = base.canDisplay(cp) || Character.isWhitespace(cp);
            Font f = ok ? base : fallbackFor(base, cp);
            int j = i + Character.charCount(cp);
            while (j < n) {
                int c2 = s.codePointAt(j);
                // ký tự nối (ZWJ, biến thể emoji) đi kèm ký tự trước
                boolean joiner = c2 == 0x200D || (c2 >= 0xFE00 && c2 <= 0xFE0F) || (c2 >= 0x1F3FB && c2 <= 0x1F3FF);
                boolean ok2 = base.canDisplay(c2) || Character.isWhitespace(c2);
                if (!joiner && (ok2 != ok || (!ok2 && !fallbackFor(base, c2).equals(f)))) break;
                j += Character.charCount(c2);
            }
            out.accept(s.substring(i, j), f);
            i = j;
        }
    }

    /** Độ rộng chuỗi, tính cả các ký tự vẽ bằng phông dự phòng. */
    public static int textWidth(FontMetrics fm, String s) {
        if (s == null || s.isEmpty()) return 0;
        Font base = fm.getFont();
        if (base.canDisplayUpTo(s) == -1) return fm.stringWidth(s);
        int[] w = { 0 };
        runs(s, base, (t, f) -> w[0] += (f == base ? fm : fm(f)).stringWidth(t));
        return w[0];
    }

    /** Vẽ chuỗi với phông hiện tại của g; ký tự phông không có (emoji, ký hiệu) dùng phông dự phòng. */
    public static void drawText(Graphics2D g, String s, float x, float y) {
        if (s == null || s.isEmpty()) return;
        Font base = g.getFont();
        if (base.canDisplayUpTo(s) == -1) { g.drawString(s, x, y); return; }
        float[] cx = { x };
        java.awt.Paint paint = g.getPaint();
        // Emoji màu bị nhân với alpha / không vẽ được với GradientPaint: dùng màu đặc cho phông dự phòng.
        Color solid = paint instanceof Color c ? new Color(c.getRGB() | 0xFF000000) : TEXT;
        runs(s, base, (t, f) -> {
            g.setFont(f);
            g.setPaint(f == base ? paint : solid);
            g.drawString(t, cx[0], y);
            cx[0] += g.getFontMetrics().stringWidth(t);
        });
        g.setFont(base);
        g.setPaint(paint);
    }

    public static String ellipsize(String s, FontMetrics fm, int maxW) {
        if (s == null) return "";
        if (textWidth(fm, s) <= maxW) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && textWidth(fm, s.substring(0, end)) + fm.stringWidth(dots) > maxW) end--;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end).stripTrailing() + dots;
    }

    /** Ngắt dòng theo độ rộng; từ quá dài được cắt theo ký tự. */
    public static List<String> wrap(String text, FontMetrics fm, int maxW) {
        List<String> out = new ArrayList<>();
        if (text == null) text = "";
        for (String para : text.split("\r?\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (textWidth(fm, candidate) <= maxW) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (line.length() > 0) { out.add(line.toString()); line = new StringBuilder(); }
                // từ dài hơn một dòng: cắt theo ký tự
                String w = word;
                while (textWidth(fm, w) > maxW && w.length() > 1) {
                    int n = w.length();
                    while (n > 1 && textWidth(fm, w.substring(0, n)) > maxW) n--;
                    if (n < w.length() && Character.isHighSurrogate(w.charAt(n - 1)) && n > 1) n--;
                    out.add(w.substring(0, n));
                    w = w.substring(n);
                }
                line = new StringBuilder(w);
            }
            out.add(line.toString());
        }
        return out;
    }

    // ---------- Avatar ----------
    private static final int[][] AVATARS = {
            { 0xFF9966, 0xFF5E62 }, { 0x56CCF2, 0x2F80ED }, { 0x43CEA2, 0x185A9D },
            { 0xF093FB, 0xF5576C }, { 0xFDC830, 0xF37335 }, { 0x8E9EFF, 0x6A4CFF },
            { 0x11C8A0, 0x2BB673 }, { 0xFF7EB3, 0xC850C0 }
    };

    private static int[] avatarPair(String name) {
        int h = name == null ? 0 : name.toLowerCase().hashCode() & 0x7fffffff;
        return AVATARS[h % AVATARS.length];
    }

    public static Color avatarColor(String name) {
        return new Color(avatarPair(name)[1]);
    }

    public static Color avatarColorLight(String name) {
        return new Color(avatarPair(name)[0]);
    }

    public static String initial(String name) {
        if (name == null || name.isBlank()) return "?";
        String n = name.strip();
        return new String(Character.toChars(Character.toUpperCase(n.codePointAt(0))));
    }

    /** Avatar tròn: gradient theo băm tên và chữ cái đầu. */
    public static void paintAvatar(Graphics2D g0, int x, int y, int d, String name) {
        if (paintImageAvatar(g0, Profiles.avatar(name), x, y, d)) return;
        Graphics2D g = aa(g0);
        int[] pair = avatarPair(name);
        g.setPaint(new GradientPaint(x, y, new Color(pair[0]), x + d, y + d, new Color(pair[1])));
        g.fill(new Ellipse2D.Double(x, y, d, d));
        g.setColor(Color.WHITE);
        Font f = font(Font.BOLD, d * 0.42f);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String s = initial(name);
        drawText(g, s, x + (d - textWidth(fm, s)) / 2f, y + (d - fm.getHeight()) / 2f + fm.getAscent());
        g.dispose();
    }

    /** Avatar của một cuộc hội thoại: phòng chung = cầu, phòng = #, chat riêng = chữ cái. */
    public static void paintConvAvatar(Graphics2D g0, Message.Scope scope, String name, int x, int y, int d) {
        if (scope == Message.Scope.PRIVATE) { paintAvatar(g0, x, y, d, name); return; }
        if (scope == Message.Scope.ROOM && paintImageAvatar(g0, Profiles.roomAvatar(name), x, y, d)) return;
        Graphics2D g = aa(g0);
        if (scope == Message.Scope.ALL) {
            g.setPaint(accentGradient(x, y, x + d, y + d));
        } else {
            int[] pair = avatarPair(name);
            g.setPaint(new GradientPaint(x, y, new Color(pair[0]), x + d, y + d, new Color(pair[1])));
        }
        g.fill(new Ellipse2D.Double(x, y, d, d));
        double s = d * 0.5;
        Icons.draw(g, scope == Message.Scope.ALL ? Icons.Kind.GLOBE : Icons.Kind.HASH,
                x + (d - s) / 2, y + (d - s) / 2, s, Color.WHITE);
        g.dispose();
    }

    /** Vẽ ảnh tròn (cắt theo hình tròn). @return false nếu không có ảnh. */
    public static boolean paintImageAvatar(Graphics2D g0, BufferedImage img, int x, int y, int d) {
        if (img == null) return false;
        Graphics2D g = aa(g0);
        Ellipse2D circle = new Ellipse2D.Double(x, y, d, d);
        g.setColor(SURFACE);
        g.fill(circle);
        g.clip(circle);
        double k = Math.max(d / (double) img.getWidth(), d / (double) img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * k), dh = (int) Math.ceil(img.getHeight() * k);
        g.drawImage(img, x + (d - dw) / 2, y + (d - dh) / 2, dw, dh, null);
        g.dispose();
        return true;
    }

    /** Huy hiệu ổ khoá nhỏ ở góc avatar của nhóm riêng tư. */
    public static void paintLockBadge(Graphics2D g0, double cx, double cy, double r, Color ring) {
        Graphics2D g = aa(g0);
        g.setColor(ring);
        g.fill(new Ellipse2D.Double(cx - r - 2.5, cy - r - 2.5, 2 * r + 5, 2 * r + 5));
        g.setPaint(new GradientPaint((float) (cx - r), (float) (cy - r), new Color(0xFDC830),
                (float) (cx + r), (float) (cy + r), new Color(0xF37335)));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        double s = r * 1.25;
        Icons.draw(g, Icons.Kind.LOCK, cx - s / 2, cy - s / 2, s, Color.WHITE);
        g.dispose();
    }

    public static void paintOnlineDot(Graphics2D g0, double cx, double cy, double r, Color ring) {
        Graphics2D g = aa(g0);
        g.setColor(ring);
        g.fill(new Ellipse2D.Double(cx - r - 2.5, cy - r - 2.5, 2 * r + 5, 2 * r + 5));
        g.setColor(GREEN);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g.dispose();
    }

    /** Biểu tượng ứng dụng (vẽ bằng Java2D). */
    public static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img.createGraphics());
        g.setPaint(accentGradient(0, 0, size, size));
        g.fill(rr(0, 0, size, size, size * 0.26));
        double s = size * 0.56;
        Icons.draw(g, Icons.Kind.CHAT, (size - s) / 2, (size - s) / 2, s, Color.WHITE);
        g.dispose();
        return img;
    }

    // ---------- UIManager ----------
    public static void install() {
        System.setProperty("awt.useSystemAAFonts", "on");
        System.setProperty("swing.aatext", "true");
        FontUIResource base = new FontUIResource(font(Font.PLAIN, 14));
        for (Object k : Collections.list(UIManager.getDefaults().keys())) {
            if (k instanceof String s && s.endsWith(".font")) UIManager.put(s, base);
        }
        UIManager.put("ToolTip.background", new ColorUIResource(0x0A0D14));
        UIManager.put("ToolTip.foreground", new ColorUIResource(TEXT));
        UIManager.put("ToolTip.font", new FontUIResource(font(Font.PLAIN, 12.5f)));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(4, 9, 4, 9)));
        ToolTipManager.sharedInstance().setInitialDelay(350);
    }
}
