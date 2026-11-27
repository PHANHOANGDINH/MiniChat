package chat.client;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;

/** Các thành phần giao diện tự vẽ: nút biểu tượng, nút gradient, ô nhập bo tròn, thanh cuộn mảnh... */
public final class Widgets {
    private Widgets() { }

    private static final Cursor HAND = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    // =============== Nút biểu tượng tròn ===============
    public static class IconButton extends JButton {
        private Icons.Kind kind;
        private final int iconSize;
        private final int box;
        private boolean gradient;
        private Color fill;
        public Color fg = Theme.TEXT_DIM;
        public Color hoverFg = Theme.TEXT;

        public IconButton(Icons.Kind kind, int box, int iconSize, String tip) {
            this.kind = kind;
            this.box = box;
            this.iconSize = iconSize;
            setToolTipText(tip);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFocusable(false);
            setCursor(HAND);
            setPreferredSize(new Dimension(box, box));
            setMinimumSize(new Dimension(box, box));
        }

        public void setKind(Icons.Kind k) { this.kind = k; repaint(); }
        public void setGradient(boolean b) { this.gradient = b; repaint(); }
        public void setFill(Color c) { this.fill = c; repaint(); }

        @Override public Dimension getMaximumSize() { return new Dimension(box, box); }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            boolean hover = getModel().isRollover() && isEnabled();
            boolean press = getModel().isArmed() && getModel().isPressed();
            double d = Math.min(w, h);
            double ox = (w - d) / 2, oy = (h - d) / 2;
            Color icon;
            if (gradient) {
                float a = isEnabled() ? 1f : 0.4f;
                Color c1 = Theme.alpha(press ? Theme.ACCENT.darker() : Theme.ACCENT, (int) (255 * a));
                Color c2 = Theme.alpha(press ? Theme.ACCENT2.darker() : Theme.ACCENT2, (int) (255 * a));
                g.setPaint(new java.awt.GradientPaint((float) ox, (float) oy, c1, (float) (ox + d), (float) (oy + d), c2));
                g.fill(new Ellipse2D.Double(ox, oy, d, d));
                icon = Theme.alpha(Color.WHITE, (int) (255 * a));
            } else if (fill != null) {
                g.setColor(press ? fill.darker() : hover ? Theme.mix(fill, Color.WHITE, 0.15) : fill);
                g.fill(new Ellipse2D.Double(ox, oy, d, d));
                icon = Color.WHITE;
            } else {
                if (press) { g.setColor(Theme.PRESSED); g.fill(new Ellipse2D.Double(ox, oy, d, d)); }
                else if (hover) { g.setColor(Theme.HOVER); g.fill(new Ellipse2D.Double(ox, oy, d, d)); }
                icon = !isEnabled() ? Theme.TEXT_MUTED : hover ? hoverFg : fg;
            }
            double s = press ? iconSize - 1 : iconSize;
            Icons.draw(g, kind, (w - s) / 2.0, (h - s) / 2.0, s, icon);
            g.dispose();
        }
    }

    // =============== Nút chữ (gradient / viền) ===============
    public static class GradientButton extends JButton {
        private final boolean primary;

        public GradientButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(HAND);
            setFont(Theme.font(Font.BOLD, 14.5f));
            setPreferredSize(new Dimension(120, 46));
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            boolean hover = getModel().isRollover() && isEnabled();
            boolean press = getModel().isArmed() && getModel().isPressed();
            double r = 14;
            Color textColor;
            if (primary) {
                Color c1 = Theme.ACCENT, c2 = Theme.ACCENT2;
                if (hover) { c1 = Theme.mix(c1, Color.WHITE, 0.12); c2 = Theme.mix(c2, Color.WHITE, 0.12); }
                if (press) { c1 = c1.darker(); c2 = c2.darker(); }
                if (!isEnabled()) { c1 = Theme.alpha(c1, 110); c2 = Theme.alpha(c2, 110); }
                g.setPaint(new java.awt.GradientPaint(0, 0, c1, w, h, c2));
                g.fill(Theme.rr(0, 0, w, h, r));
                textColor = isEnabled() ? Color.WHITE : Theme.alpha(Color.WHITE, 170);
            } else {
                g.setColor(press ? Theme.PRESSED : hover ? Theme.HOVER : Theme.alpha(Color.WHITE, 6));
                g.fill(Theme.rr(0, 0, w, h, r));
                g.setColor(hover ? Theme.alpha(Theme.ACCENT, 160) : Theme.BORDER);
                g.setStroke(new java.awt.BasicStroke(1.2f));
                g.draw(Theme.rr(0.6, 0.6, w - 1.2, h - 1.2, r));
                textColor = isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED;
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String t = getText();
            g.setColor(textColor);
            g.drawString(t, (w - fm.stringWidth(t)) / 2f, (h - fm.getHeight()) / 2f + fm.getAscent());
            g.dispose();
        }
    }

    // =============== Ô nhập có gợi ý (placeholder) ===============
    private static void paintHint(JTextComponent c, Graphics gr, String hint) {
        if (hint == null || !c.getText().isEmpty()) return;
        Graphics2D g = Theme.aa(gr);
        g.setFont(c.getFont());
        g.setColor(Theme.TEXT_MUTED);
        FontMetrics fm = g.getFontMetrics();
        Insets in = c.getInsets();
        g.drawString(hint, in.left, (c.getHeight() - fm.getHeight()) / 2f + fm.getAscent());
        g.dispose();
    }

    private static void styleField(JTextComponent f) {
        f.setOpaque(false);
        f.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.TEXT);
        f.setSelectionColor(Theme.alpha(Theme.ACCENT, 140));
        f.setSelectedTextColor(Color.WHITE);
        f.setDisabledTextColor(Theme.TEXT_MUTED);
        f.setFont(Theme.font(Font.PLAIN, 14.5f));
    }

    public static class HintField extends JTextField {
        private String hint;
        public HintField(String text, String hint) {
            super(text);
            this.hint = hint;
            styleField(this);
        }
        public void setHint(String h) { hint = h; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintHint(this, g, hint);
        }
    }

    public static class HintPassword extends JPasswordField {
        private final String hint;
        public HintPassword(String hint) {
            this.hint = hint;
            styleField(this);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            paintHint(this, g, hint);
        }
    }

    // =============== Khung bo tròn chứa ô nhập ===============
    public static class RoundInput extends JPanel {
        private final JTextComponent field;
        private final Icons.Kind icon;
        private final int height;
        private boolean focused;
        private boolean error;

        public RoundInput(JTextComponent field, Icons.Kind icon, int height) {
            super(new BorderLayout());
            this.field = field;
            this.icon = icon;
            this.height = height;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, icon != null ? height - 2 : 16, 0, 14));
            add(field, BorderLayout.CENTER);
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
            });
            field.getDocument().addDocumentListener(new DocumentListener() {
                void clear() { if (error) setError(false); }
                @Override public void insertUpdate(DocumentEvent e) { clear(); }
                @Override public void removeUpdate(DocumentEvent e) { clear(); }
                @Override public void changedUpdate(DocumentEvent e) { clear(); }
            });
        }

        public JTextComponent field() { return field; }
        public void setError(boolean b) { error = b; repaint(); }

        @Override public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, height);
        }
        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, height);
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            double r = Math.min(14, h / 2.0);
            g.setColor(Theme.INPUT);
            g.fill(Theme.rr(0, 0, w, h, r));
            Color border = error ? Theme.RED : focused ? Theme.ACCENT : Theme.BORDER;
            g.setColor(border);
            g.setStroke(new java.awt.BasicStroke(focused || error ? 1.6f : 1.1f));
            g.draw(Theme.rr(0.8, 0.8, w - 1.6, h - 1.6, r));
            if (icon != null) {
                double s = Math.min(20, h * 0.46);
                Icons.draw(g, icon, (h - s) / 2.0 + 2, (h - s) / 2.0, s,
                        error ? Theme.RED : focused ? Theme.ACCENT : Theme.TEXT_MUTED);
            }
            g.dispose();
        }
    }

    // =============== Panel bo tròn ===============
    public static class RoundedPanel extends JPanel {
        private Color fill;
        private Color border;
        private final double radius;

        public RoundedPanel(Color fill, Color border, double radius) {
            this.fill = fill;
            this.border = border;
            this.radius = radius;
            setOpaque(false);
        }

        public void setBorderColor(Color c) { border = c; repaint(); }
        public void setFill(Color c) { fill = c; repaint(); }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            if (fill != null) { g.setColor(fill); g.fill(Theme.rr(0, 0, w, h, radius)); }
            if (border != null) {
                g.setColor(border);
                g.setStroke(new java.awt.BasicStroke(1.2f));
                g.draw(Theme.rr(0.6, 0.6, w - 1.2, h - 1.2, radius));
            }
            g.dispose();
        }
    }

    // =============== Thanh cuộn mảnh ===============
    private static class SlimBarUI extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics gr, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = Theme.aa(gr);
            boolean active = isThumbRollover() || isDragging;
            g.setColor(new Color(255, 255, 255, active ? 90 : 42));
            int bw = active ? 7 : 5;
            g.fill(Theme.rr(r.x + (r.width - bw) / 2.0, r.y + 2, bw, Math.max(0, r.height - 4), bw / 2.0));
            g.dispose();
        }
    }

    public static void slim(JScrollPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setViewportBorder(null);
        JScrollBar v = sp.getVerticalScrollBar();
        v.setUI(new SlimBarUI());
        v.setPreferredSize(new Dimension(10, 0));
        v.setOpaque(false);
        v.setUnitIncrement(28);
        JScrollBar h = sp.getHorizontalScrollBar();
        h.setPreferredSize(new Dimension(0, 0));
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setCorner(JScrollPane.UPPER_RIGHT_CORNER, new JPanel());
    }

    // =============== Menu ngữ cảnh ===============
    public static JPopupMenu popup() {
        JPopupMenu m = new JPopupMenu() {
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                g.setColor(Theme.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        m.setOpaque(true);
        m.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return m;
    }

    public static void menuItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem it = new JMenuItem(text) {
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                if (getModel().isArmed() || getModel().isRollover()) {
                    g.setColor(Theme.alpha(Theme.ACCENT, 150));
                    g.fill(Theme.rr(0, 0, getWidth(), getHeight(), 8));
                }
                g.setFont(getFont());
                g.setColor(Theme.TEXT);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(getText(), 12, (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
                g.dispose();
            }
            @Override public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                return new Dimension(fm.stringWidth(getText()) + 36, 32);
            }
        };
        it.setOpaque(false);
        it.setFont(Theme.font(Font.PLAIN, 13.5f));
        it.setCursor(HAND);
        it.addActionListener(e -> action.run());
        menu.add(it);
    }

    // =============== Hộp thoại nhập một dòng ===============
    /** @return chuỗi đã nhập, hoặc null nếu huỷ. */
    public static String prompt(Window owner, String title, String message, String hint, String okText) {
        JDialog d = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        String[] result = { null };

        HintField tf = new HintField("", hint);
        RoundInput input = new RoundInput(tf, Icons.Kind.HASH, 44);

        JLabel t = new JLabel(title);
        t.setFont(Theme.font(Font.BOLD, 18f));
        t.setForeground(Theme.TEXT);
        JLabel msg = new JLabel(message);
        msg.setFont(Theme.font(Font.PLAIN, 13.5f));
        msg.setForeground(Theme.TEXT_DIM);

        GradientButton ok = new GradientButton(okText, true);
        GradientButton cancel = new GradientButton("Hủy", false);
        ok.setPreferredSize(new Dimension(110, 40));
        cancel.setPreferredSize(new Dimension(90, 40));
        Runnable submit = () -> {
            String s = tf.getText().trim();
            if (s.isEmpty()) { input.setError(true); return; }
            result[0] = s;
            d.dispose();
        };
        ok.addActionListener(e -> submit.run());
        tf.addActionListener(e -> submit.run());
        cancel.addActionListener(e -> d.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(ok);

        JPanel body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setBackground(Theme.PANEL);
        body.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(22, 24, 20, 24)));
        for (JComponent c : new JComponent[] { t, msg, input, buttons }) c.setAlignmentX(0f);
        body.add(t);
        body.add(javax.swing.Box.createVerticalStrut(4));
        body.add(msg);
        body.add(javax.swing.Box.createVerticalStrut(16));
        body.add(input);
        body.add(javax.swing.Box.createVerticalStrut(18));
        body.add(buttons);
        body.setPreferredSize(new Dimension(380, body.getPreferredSize().height));

        d.setContentPane(body);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) { tf.requestFocusInWindow(); }
        });
        d.setVisible(true);
        return result[0];
    }

    @SuppressWarnings("unused")
    private static AbstractAction unused() { return null; }
}
