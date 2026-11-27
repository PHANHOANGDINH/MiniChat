package chat.client;

import chat.common.Message;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Các hộp thoại: tạo nhóm, chọn thành viên, mật khẩu nhóm, hồ sơ cá nhân, khám phá nhóm. */
public final class Dialogs {
    private Dialogs() { }

    private static final Cursor HAND = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
        return n.toLowerCase();
    }

    /**
     * Nhãn tự đo và tự vẽ với cùng cấu hình khử răng cưa (Theme.aa / Theme.fm), tránh bị cắt chữ
     * trong cửa sổ trong suốt — nơi JLabel thường vẽ chữ rộng hơn lúc đo.
     */
    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text) {
            @Override public Dimension getPreferredSize() {
                FontMetrics fm = Theme.fm(getFont());
                java.awt.Insets in = getInsets();
                String t = getText() == null ? "" : getText();
                return new Dimension(Theme.textWidth(fm, t) + 4 + in.left + in.right, fm.getHeight() + in.top + in.bottom);
            }
            @Override public Dimension getMaximumSize() { return getPreferredSize(); }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                g.setFont(getFont());
                g.setColor(getForeground());
                FontMetrics fm = g.getFontMetrics();
                java.awt.Insets in = getInsets();
                Theme.drawText(g, getText() == null ? "" : getText(), in.left,
                        in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2f + fm.getAscent());
                g.dispose();
            }
        };
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    static void onChange(JTextComponent f, Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { r.run(); }
            @Override public void removeUpdate(DocumentEvent e) { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    // =====================================================================
    //  Khung hộp thoại bo góc, kéo thả được
    // =====================================================================
    static class Sheet extends JDialog {
        final JPanel content = new JPanel();
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        private final int width;

        Sheet(Window owner, String title, String subtitle, int width) {
            super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
            this.width = width;
            setUndecorated(true);
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            boolean translucent = gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
            if (translucent) setBackground(new Color(0, 0, 0, 0));

            JPanel card = new JPanel(new BorderLayout()) {
                @Override protected void paintComponent(Graphics gr) {
                    Graphics2D g = Theme.aa(gr);
                    int w = getWidth(), h = getHeight();
                    double r = translucent ? 20 : 0;
                    g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.PANEL, Theme.ACCENT, 0.06), 0, 160, Theme.PANEL));
                    g.fill(Theme.rr(0, 0, w, h, r));
                    g.setColor(Theme.BORDER);
                    g.setStroke(new java.awt.BasicStroke(1.2f));
                    g.draw(Theme.rr(0.6, 0.6, w - 1.2, h - 1.2, r));
                    g.dispose();
                }
            };
            card.setOpaque(!translucent);
            card.setBackground(Theme.PANEL);
            card.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            JPanel titles = new JPanel();
            titles.setOpaque(false);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            titles.add(label(title, Theme.font(Font.BOLD, 19f), Theme.TEXT));
            if (subtitle != null) {
                titles.add(Box.createVerticalStrut(3));
                titles.add(label(subtitle, Theme.font(Font.PLAIN, 13f), Theme.TEXT_DIM));
            }
            head.add(titles, BorderLayout.CENTER);
            Widgets.IconButton close = new Widgets.IconButton(Icons.Kind.CLOSE, 34, 18, "Đóng");
            close.addActionListener(e -> dispose());
            JPanel cw = new JPanel(new BorderLayout());
            cw.setOpaque(false);
            cw.add(close, BorderLayout.NORTH);
            head.add(cw, BorderLayout.EAST);
            MouseAdapter drag = new MouseAdapter() {
                Point start;
                @Override public void mousePressed(MouseEvent e) { start = e.getLocationOnScreen(); }
                @Override public void mouseDragged(MouseEvent e) {
                    Point p = e.getLocationOnScreen();
                    setLocation(getX() + p.x - start.x, getY() + p.y - start.y);
                    start = p;
                }
            };
            head.addMouseListener(drag);
            head.addMouseMotionListener(drag);

            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(18, 0, 20, 0));
            footer.setOpaque(false);

            card.add(head, BorderLayout.NORTH);
            card.add(content, BorderLayout.CENTER);
            card.add(footer, BorderLayout.SOUTH);
            setContentPane(card);
            getRootPane().registerKeyboardAction(e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        <T extends JComponent> T add(T c) {
            c.setAlignmentX(0f);
            content.add(c);
            return c;
        }

        void gap(int h) { content.add(Box.createVerticalStrut(h)); }

        Widgets.GradientButton button(String text, boolean primary, Runnable action) {
            Widgets.GradientButton b = new Widgets.GradientButton(text, primary);
            FontMetrics fm = b.getFontMetrics(b.getFont());
            b.setPreferredSize(new Dimension(Math.max(primary ? 120 : 90, fm.stringWidth(text) + 40), 42));
            b.addActionListener(e -> action.run());
            footer.add(b);
            return b;
        }

        void refit() {
            content.revalidate();
            setSize(width, getPreferredSize().height);
        }

        void open(JComponent focus) {
            pack();
            setSize(width, getPreferredSize().height);
            setLocationRelativeTo(getOwner());
            if (focus != null) {
                addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowOpened(java.awt.event.WindowEvent e) { focus.requestFocusInWindow(); }
                });
            }
            setVisible(true);
        }
    }

    /** Dòng thông báo lỗi nhỏ màu đỏ trong hộp thoại. */
    static final class ErrorLine extends JLabel {
        ErrorLine() {
            setFont(Theme.font(Font.PLAIN, 12.5f));
            setForeground(new Color(0xFF8E92));
            setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 0));
            setVisible(false);
        }
        void show(String s) { setText(s); setVisible(true); }
        void clear() { setVisible(false); }
    }

    // =====================================================================
    //  Ảnh đại diện có thể bấm để chọn ảnh
    // =====================================================================
    static final class AvatarPicker extends JComponent {
        private final int size;
        private final Message.Scope scope;
        private final java.util.function.Supplier<String> name;
        private BufferedImage preview;
        private boolean hover;
        /** null = không đổi, rỗng = xoá ảnh, có dữ liệu = ảnh mới. */
        byte[] bytes;
        Runnable onChange = () -> { };

        AvatarPicker(int size, Message.Scope scope, java.util.function.Supplier<String> name, BufferedImage current) {
            this.size = size;
            this.scope = scope;
            this.name = name;
            this.preview = current;
            setPreferredSize(new Dimension(size + 4, size + 4));
            setMaximumSize(getPreferredSize());
            setCursor(HAND);
            setToolTipText("Chọn ảnh đại diện");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { choose(); }
            });
        }

        boolean hasImage() { return preview != null; }

        void clearImage() {
            preview = null;
            bytes = new byte[0];
            repaint();
            onChange.run();
        }

        private void choose() {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Ảnh (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
            if (fc.showOpenDialog(SwingUtilities.getWindowAncestor(this)) != JFileChooser.APPROVE_OPTION) return;
            try {
                bytes = Profiles.avatarBytes(fc.getSelectedFile());
                preview = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                repaint();
                onChange.run();
            } catch (IOException ex) {
                setToolTipText("Không đọc được ảnh: " + ex.getMessage());
            }
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int x = 2, y = 2;
            g.setPaint(Theme.accentGradient(0, 0, size + 4, size + 4));
            g.fill(new Ellipse2D.Double(0, 0, size + 4, size + 4));
            if (!Theme.paintImageAvatar(g, preview, x, y, size)) {
                String n = name.get();
                if (scope == Message.Scope.PRIVATE) {
                    Theme.paintAvatar(g, x, y, size, n == null || n.isBlank() ? "?" : n);
                } else {
                    g.setColor(Theme.SURFACE);
                    g.fill(new Ellipse2D.Double(x, y, size, size));
                    double s = size * 0.42;
                    Icons.draw(g, Icons.Kind.USERS, x + (size - s) / 2, y + (size - s) / 2, s, Theme.TEXT_DIM);
                }
            }
            if (hover) {
                g.setColor(new Color(0, 0, 0, 120));
                g.fill(new Ellipse2D.Double(x, y, size, size));
                double s = size * 0.34;
                Icons.draw(g, Icons.Kind.CAMERA, x + (size - s) / 2, y + (size - s) / 2, s, Color.WHITE);
            }
            // huy hiệu máy ảnh
            double r = Math.max(11, size * 0.15);
            double cx = x + size - r * 0.7, cy = y + size - r * 0.7;
            g.setColor(Theme.PANEL);
            g.fill(new Ellipse2D.Double(cx - r - 2, cy - r - 2, 2 * r + 4, 2 * r + 4));
            g.setPaint(Theme.accentGradient((float) (cx - r), (float) (cy - r), (float) (cx + r), (float) (cy + r)));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            Icons.draw(g, Icons.Kind.CAMERA, cx - r * 0.62, cy - r * 0.62, r * 1.24, Color.WHITE);
            g.dispose();
        }
    }

    // =====================================================================
    //  Nút chọn hai trạng thái (Cộng đồng / Riêng tư)
    // =====================================================================
    static final class Segmented extends JComponent {
        private final String[] labels;
        private final Icons.Kind[] icons;
        private int selected;
        private int hover = -1;
        Consumer<Integer> onSelect = i -> { };

        Segmented(String[] labels, Icons.Kind[] icons) {
            this.labels = labels;
            this.icons = icons;
            setPreferredSize(new Dimension(300, 46));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            setCursor(HAND);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int i = at(e); if (i != hover) { hover = i; repaint(); } }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { select(at(e)); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int at(MouseEvent e) {
            return Math.max(0, Math.min(labels.length - 1, e.getX() * labels.length / Math.max(1, getWidth())));
        }

        int selected() { return selected; }

        void select(int i) {
            if (i == selected) return;
            selected = i;
            repaint();
            onSelect.accept(i);
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.INPUT);
            g.fill(Theme.rr(0, 0, w, h, 14));
            g.setColor(Theme.BORDER);
            g.draw(Theme.rr(0.5, 0.5, w - 1, h - 1, 14));
            double cw = (w - 8) / (double) labels.length;
            for (int i = 0; i < labels.length; i++) {
                double x = 4 + i * cw;
                boolean sel = i == selected;
                if (sel) {
                    g.setPaint(new GradientPaint((float) x, 0, Theme.alpha(Theme.ACCENT, 90), (float) (x + cw), h, Theme.alpha(Theme.ACCENT2, 70)));
                    g.fill(Theme.rr(x, 4, cw, h - 8, 11));
                    g.setColor(Theme.alpha(Theme.ACCENT, 200));
                    g.draw(Theme.rr(x + 0.5, 4.5, cw - 1, h - 9, 11));
                } else if (i == hover) {
                    g.setColor(Theme.HOVER);
                    g.fill(Theme.rr(x, 4, cw, h - 8, 11));
                }
                g.setFont(Theme.font(Font.BOLD, 13.5f));
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(labels[i]);
                double total = 18 + 8 + tw;
                double sx = x + (cw - total) / 2;
                Color c = sel ? Color.WHITE : Theme.TEXT_DIM;
                Icons.draw(g, icons[i], sx, (h - 18) / 2.0, 18, c);
                g.setColor(c);
                g.drawString(labels[i], (float) (sx + 26), (h - fm.getHeight()) / 2f + fm.getAscent());
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  Danh sách chọn thành viên (có ô tìm kiếm, đánh dấu tròn)
    // =====================================================================
    static final class MemberPicker extends JPanel {
        private final List<String> all;
        final Set<String> chosen = new LinkedHashSet<>();
        private final JPanel rows = new JPanel();
        private final Widgets.HintField search = new Widgets.HintField("", "Tìm theo tên…");
        private final JLabel count = label("", Theme.font(Font.BOLD, 12f), Theme.TEXT_MUTED);
        Runnable onChange = () -> { };

        MemberPicker(Collection<String> candidates, int height) {
            super(new BorderLayout(0, 8));
            setOpaque(false);
            all = new ArrayList<>(candidates);
            all.sort((a, b) -> Profiles.display(a).compareToIgnoreCase(Profiles.display(b)));
            JPanel top = new JPanel(new BorderLayout(0, 8));
            top.setOpaque(false);
            top.add(count, BorderLayout.NORTH);
            top.add(new Widgets.RoundInput(search, Icons.Kind.SEARCH, 40), BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);
            rows.setOpaque(false);
            rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(rows, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(holder);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            Widgets.slim(sp);
            Widgets.RoundedPanel box = new Widgets.RoundedPanel(Theme.alpha(Color.BLACK, 40), Theme.BORDER, 14);
            box.setLayout(new BorderLayout());
            box.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            box.add(sp);
            box.setPreferredSize(new Dimension(10, height));
            add(box, BorderLayout.CENTER);
            onChange(search, this::rebuild);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height + 90));
            rebuild();
        }

        private void rebuild() {
            rows.removeAll();
            String q = norm(search.getText().trim());
            int shown = 0;
            for (String u : all) {
                if (!q.isEmpty() && !norm(Profiles.display(u)).contains(q) && !norm(u).contains(q)) continue;
                rows.add(new Row(u));
                shown++;
            }
            if (shown == 0) {
                JLabel empty = label(all.isEmpty() ? "Chưa có ai khác đang online" : "Không tìm thấy ai",
                        Theme.font(Font.PLAIN, 13f), Theme.TEXT_MUTED);
                empty.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
                rows.add(empty);
            }
            count.setText("ĐÃ CHỌN " + chosen.size() + " / " + all.size());
            rows.revalidate();
            rows.repaint();
        }

        private final class Row extends JComponent {
            final String user;
            boolean hover;
            Row(String user) {
                this.user = user;
                setPreferredSize(new Dimension(10, 52));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
                setCursor(HAND);
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        if (!chosen.remove(user)) chosen.add(user);
                        count.setText("ĐÃ CHỌN " + chosen.size() + " / " + all.size());
                        repaint();
                        onChange.run();
                    }
                });
            }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                int w = getWidth(), h = getHeight();
                boolean on = chosen.contains(user);
                if (on) { g.setColor(Theme.alpha(Theme.ACCENT, 34)); g.fill(Theme.rr(2, 2, w - 4, h - 4, 11)); }
                else if (hover) { g.setColor(Theme.HOVER); g.fill(Theme.rr(2, 2, w - 4, h - 4, 11)); }
                Theme.paintAvatar(g, 12, (h - 36) / 2, 36, user);
                Theme.paintOnlineDot(g, 12 + 36 - 5, (h - 36) / 2.0 + 36 - 5, 4, Theme.PANEL);
                g.setFont(Theme.font(Font.BOLD, 14f));
                g.setColor(Theme.TEXT);
                FontMetrics f1 = g.getFontMetrics();
                Theme.drawText(g, Theme.ellipsize(Profiles.display(user), f1, w - 120), 60, h / 2f - 2);
                g.setFont(Theme.font(Font.PLAIN, 12f));
                g.setColor(Theme.TEXT_MUTED);
                g.drawString("@" + user, 60, h / 2f + 14);
                double d = 22, cx = w - 16 - d, cy = (h - d) / 2;
                if (on) {
                    g.setPaint(Theme.accentGradient((float) cx, (float) cy, (float) (cx + d), (float) (cy + d)));
                    g.fill(new Ellipse2D.Double(cx, cy, d, d));
                    Icons.draw(g, Icons.Kind.CHECK, cx + 4, cy + 4, d - 8, Color.WHITE);
                } else {
                    g.setColor(hover ? Theme.TEXT_DIM : Theme.TEXT_MUTED);
                    g.setStroke(new java.awt.BasicStroke(1.6f));
                    g.draw(new Ellipse2D.Double(cx + 1, cy + 1, d - 2, d - 2));
                }
                g.dispose();
            }
        }
    }

    // =====================================================================
    //  Tạo nhóm
    // =====================================================================
    public record GroupSpec(String name, String password, List<String> members, byte[] avatar) { }

    public static GroupSpec createGroup(Window owner, Collection<String> candidates) {
        Sheet s = new Sheet(owner, "Tạo nhóm mới", "Nhóm cộng đồng ai cũng vào được, nhóm riêng tư cần mật khẩu", 470);
        GroupSpec[] result = { null };

        Widgets.HintField name = new Widgets.HintField("", "Tên nhóm");
        Widgets.RoundInput nameBox = new Widgets.RoundInput(name, Icons.Kind.HASH, 46);
        AvatarPicker avatar = new AvatarPicker(64, Message.Scope.ROOM, name::getText, null);
        onChange(name, avatar::repaint);

        JPanel top = new JPanel(new BorderLayout(14, 0));
        top.setOpaque(false);
        top.add(avatar, BorderLayout.WEST);
        JPanel nameCol = new JPanel(new BorderLayout(0, 6));
        nameCol.setOpaque(false);
        nameCol.add(label("TÊN NHÓM", Theme.font(Font.BOLD, 11.5f), Theme.TEXT_MUTED), BorderLayout.NORTH);
        nameCol.add(nameBox, BorderLayout.CENTER);
        top.add(nameCol, BorderLayout.CENTER);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        s.add(top);
        s.gap(16);

        s.add(label("QUYỀN RIÊNG TƯ", Theme.font(Font.BOLD, 11.5f), Theme.TEXT_MUTED));
        s.gap(6);
        Segmented privacy = s.add(new Segmented(new String[] { "Cộng đồng", "Riêng tư" },
                new Icons.Kind[] { Icons.Kind.GLOBE, Icons.Kind.LOCK }));
        s.gap(6);
        JLabel hint = s.add(label("Mọi người đều thấy và tự tham gia nhóm.", Theme.font(Font.PLAIN, 12.5f), Theme.TEXT_DIM));
        Widgets.HintPassword pass = new Widgets.HintPassword("Mật khẩu nhóm (ít nhất 3 ký tự)");
        Widgets.RoundInput passBox = new Widgets.RoundInput(pass, Icons.Kind.LOCK, 44);
        JPanel passWrap = new JPanel(new BorderLayout());
        passWrap.setOpaque(false);
        passWrap.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        passWrap.add(passBox);
        passWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        passWrap.setVisible(false);
        s.add(passWrap);
        privacy.onSelect = i -> {
            passWrap.setVisible(i == 1);
            hint.setText(i == 1 ? "Người ngoài cần mật khẩu; thành viên bạn thêm vào thì không cần."
                                : "Mọi người đều thấy và tự tham gia nhóm.");
            s.refit();
            if (i == 1) pass.requestFocusInWindow();
        };
        s.gap(16);

        s.add(label("THÊM THÀNH VIÊN", Theme.font(Font.BOLD, 11.5f), Theme.TEXT_MUTED));
        s.gap(6);
        MemberPicker picker = s.add(new MemberPicker(candidates, 200));
        ErrorLine err = s.add(new ErrorLine());

        Runnable submit = () -> {
            String n = name.getText().trim();
            String p = new String(pass.getPassword());
            if (n.length() < 2 || n.length() > 30) { nameBox.setError(true); err.show("Tên nhóm dài 2-30 ký tự"); s.refit(); return; }
            if (privacy.selected() == 1 && p.length() < 3) { passBox.setError(true); err.show("Mật khẩu nhóm cần ít nhất 3 ký tự"); s.refit(); return; }
            result[0] = new GroupSpec(n, privacy.selected() == 1 ? p : null, new ArrayList<>(picker.chosen),
                    avatar.bytes != null && avatar.bytes.length > 0 ? avatar.bytes : null);
            s.dispose();
        };
        name.addActionListener(e -> submit.run());
        pass.addActionListener(e -> submit.run());
        s.button("Hủy", false, s::dispose);
        Widgets.GradientButton ok = s.button("Tạo nhóm", true, submit);
        picker.onChange = () -> ok.setText(picker.chosen.isEmpty() ? "Tạo nhóm" : "Tạo nhóm (" + (picker.chosen.size() + 1) + ")");
        s.open(name);
        return result[0];
    }

    // =====================================================================
    //  Thêm thành viên vào nhóm có sẵn
    // =====================================================================
    public static List<String> pickMembers(Window owner, String room, Collection<String> candidates) {
        Sheet s = new Sheet(owner, "Thêm thành viên", "Thêm người đang online vào nhóm \"" + room + "\"", 440);
        List<List<String>> result = new ArrayList<>();
        result.add(null);
        MemberPicker picker = s.add(new MemberPicker(candidates, 280));
        s.button("Hủy", false, s::dispose);
        Widgets.GradientButton ok = s.button("Thêm", true, () -> {
            if (picker.chosen.isEmpty()) return;
            result.set(0, new ArrayList<>(picker.chosen));
            s.dispose();
        });
        ok.setEnabled(false);
        picker.onChange = () -> {
            ok.setEnabled(!picker.chosen.isEmpty());
            ok.setText(picker.chosen.isEmpty() ? "Thêm" : "Thêm " + picker.chosen.size() + " người");
        };
        s.open(null);
        return result.get(0);
    }

    // =====================================================================
    //  Mật khẩu nhóm riêng tư
    // =====================================================================
    public static String askPassword(Window owner, String room) {
        Sheet s = new Sheet(owner, "Nhóm riêng tư", "Nhập mật khẩu để vào \"" + room + "\"", 400);
        String[] result = { null };
        Widgets.HintPassword pass = new Widgets.HintPassword("Mật khẩu nhóm");
        Widgets.RoundInput box = s.add(new Widgets.RoundInput(pass, Icons.Kind.LOCK, 46));
        Runnable submit = () -> {
            String p = new String(pass.getPassword());
            if (p.isEmpty()) { box.setError(true); return; }
            result[0] = p;
            s.dispose();
        };
        pass.addActionListener(e -> submit.run());
        s.button("Hủy", false, s::dispose);
        s.button("Vào nhóm", true, submit);
        s.open(pass);
        return result[0];
    }

    // =====================================================================
    //  Hồ sơ cá nhân: đổi tên hiển thị + avatar
    // =====================================================================
    public record ProfileSpec(String displayName, byte[] avatar) { }

    public static ProfileSpec editProfile(Window owner, String me) {
        Sheet s = new Sheet(owner, "Hồ sơ của bạn", "Mọi người sẽ thấy tên và ảnh này", 420);
        ProfileSpec[] result = { null };
        Widgets.HintField name = new Widgets.HintField(Profiles.hasDisplayName(me) ? Profiles.display(me) : "", me);
        AvatarPicker avatar = new AvatarPicker(104, Message.Scope.PRIVATE,
                () -> name.getText().isBlank() ? me : name.getText(), Profiles.avatar(me));
        onChange(name, avatar::repaint);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        center.setOpaque(false);
        center.add(avatar);
        center.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        s.add(center);
        s.gap(8);
        JPanel linkRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        linkRow.setOpaque(false);
        JLabel change = link("Đổi ảnh", () -> avatar.dispatchEvent(new MouseEvent(avatar, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 1, 1, 1, false)));
        JLabel remove = link("Xóa ảnh", avatar::clearImage);
        remove.setVisible(avatar.hasImage());
        avatar.onChange = () -> { remove.setVisible(avatar.hasImage()); linkRow.revalidate(); };
        linkRow.add(change);
        linkRow.add(remove);
        linkRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        s.add(linkRow);
        s.gap(14);
        JLabel id = label("@" + me, Theme.font(Font.PLAIN, 13f), Theme.TEXT_MUTED);
        JPanel idRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        idRow.setOpaque(false);
        idRow.add(id);
        idRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        s.add(idRow);
        s.gap(16);
        s.add(label("TÊN HIỂN THỊ", Theme.font(Font.BOLD, 11.5f), Theme.TEXT_MUTED));
        s.gap(6);
        Widgets.RoundInput box = s.add(new Widgets.RoundInput(name, Icons.Kind.EDIT, 46));
        s.gap(4);
        s.add(label("Để trống để dùng tên đăng nhập. Tối đa 30 ký tự.", Theme.font(Font.PLAIN, 12f), Theme.TEXT_MUTED));
        ErrorLine err = s.add(new ErrorLine());

        Runnable submit = () -> {
            String n = name.getText().strip();
            if (n.length() > 30) { box.setError(true); err.show("Tên hiển thị tối đa 30 ký tự"); s.refit(); return; }
            result[0] = new ProfileSpec(n, avatar.bytes);
            s.dispose();
        };
        name.addActionListener(e -> submit.run());
        s.button("Hủy", false, s::dispose);
        s.button("Lưu thay đổi", true, submit);
        s.open(name);
        return result[0];
    }

    static JLabel link(String text, Runnable action) {
        JLabel l = label(text, Theme.font(Font.BOLD, 13f), new Color(0xA594FF));
        l.setCursor(HAND);
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { l.setForeground(Color.WHITE); }
            @Override public void mouseExited(MouseEvent e) { l.setForeground(new Color(0xA594FF)); }
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
        });
        return l;
    }

    // =====================================================================
    //  Khám phá & tìm kiếm nhóm
    // =====================================================================
    public static void discover(Window owner, List<RoomInfo> rooms, Predicate<String> joined, Consumer<RoomInfo> pick) {
        Sheet s = new Sheet(owner, "Khám phá nhóm", rooms.size() + " nhóm đang hoạt động trên server", 480);
        Widgets.HintField search = new Widgets.HintField("", "Tìm nhóm theo tên…");
        s.add(new Widgets.RoundInput(search, Icons.Kind.SEARCH, 44));
        s.gap(12);
        Segmented filter = s.add(new Segmented(new String[] { "Tất cả", "Cộng đồng", "Riêng tư" },
                new Icons.Kind[] { Icons.Kind.COMPASS, Icons.Kind.GLOBE, Icons.Kind.LOCK }));
        s.gap(12);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(rows, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        Widgets.slim(sp);
        sp.setPreferredSize(new Dimension(10, 340));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        s.add(sp);

        Runnable rebuild = () -> {
            rows.removeAll();
            String q = norm(search.getText().trim());
            int f = filter.selected();
            for (RoomInfo r : rooms) {
                if (!q.isEmpty() && !norm(r.name).contains(q)) continue;
                if (f == 1 && r.locked || f == 2 && !r.locked) continue;
                rows.add(new RoomRow(r, joined.test(r.name), () -> { s.dispose(); pick.accept(r); }));
                rows.add(Box.createVerticalStrut(6));
            }
            if (rows.getComponentCount() == 0) {
                JPanel empty = new JPanel();
                empty.setOpaque(false);
                empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
                JLabel a = label(rooms.isEmpty() ? "Chưa có nhóm nào" : "Không tìm thấy nhóm phù hợp",
                        Theme.font(Font.BOLD, 14.5f), Theme.TEXT);
                JLabel b = label("Hãy tạo một nhóm mới và mời bạn bè!", Theme.font(Font.PLAIN, 13f), Theme.TEXT_MUTED);
                a.setAlignmentX(0.5f);
                b.setAlignmentX(0.5f);
                empty.add(Box.createVerticalStrut(60));
                empty.add(a);
                empty.add(Box.createVerticalStrut(4));
                empty.add(b);
                rows.add(empty);
            }
            rows.revalidate();
            rows.repaint();
        };
        onChange(search, rebuild);
        filter.onSelect = i -> rebuild.run();
        rebuild.run();
        s.button("Đóng", false, s::dispose);
        s.open(search);
    }

    private static final class RoomRow extends JComponent {
        final RoomInfo r;
        final boolean joined;
        boolean hover;
        RoomRow(RoomInfo r, boolean joined, Runnable action) {
            this.r = r;
            this.joined = joined;
            setPreferredSize(new Dimension(10, 66));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            setCursor(HAND);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
            });
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            g.setColor(hover ? Theme.mix(Theme.SURFACE, Color.WHITE, 0.04) : Theme.alpha(Theme.SURFACE, 150));
            g.fill(Theme.rr(0, 0, w - 2, h, 14));
            int d = 44, ax = 12, ay = (h - d) / 2;
            Theme.paintConvAvatar(g, Message.Scope.ROOM, r.name, ax, ay, d);
            if (r.locked) Theme.paintLockBadge(g, ax + d - 5, ay + d - 5, 8, Theme.SURFACE);
            String btn = joined ? "Mở" : r.locked ? "Nhập mật khẩu" : "Tham gia";
            g.setFont(Theme.font(Font.BOLD, 12.5f));
            FontMetrics bf = g.getFontMetrics();
            int bw = bf.stringWidth(btn) + 26, bh = 30, bx = w - 14 - bw, by = (h - bh) / 2;
            if (joined) g.setColor(hover ? Theme.alpha(Color.WHITE, 30) : Theme.alpha(Color.WHITE, 14));
            else g.setPaint(hover ? Theme.accentGradient(bx, by, bx + bw, by + bh) : new GradientPaint(bx, by, Theme.alpha(Theme.ACCENT, 70), bx + bw, by, Theme.alpha(Theme.ACCENT2, 60)));
            g.fill(Theme.rr(bx, by, bw, bh, bh / 2.0));
            g.setColor(joined ? Theme.TEXT_DIM : Color.WHITE);
            g.drawString(btn, bx + 13, by + (bh - bf.getHeight()) / 2f + bf.getAscent());
            int tx = ax + d + 14;
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.setColor(Theme.TEXT);
            FontMetrics f1 = g.getFontMetrics();
            Theme.drawText(g, Theme.ellipsize(r.name, f1, bx - tx - 10), tx, h / 2f - 3);
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            g.setColor(r.locked ? new Color(0xFDC830) : Theme.GREEN);
            String kind = r.locked ? "Riêng tư" : "Cộng đồng";
            g.drawString(kind, tx, h / 2f + 15);
            int kw = g.getFontMetrics().stringWidth(kind);
            g.setColor(Theme.TEXT_MUTED);
            g.drawString(" · " + r.count + " thành viên" + (joined ? " · đã tham gia" : ""), tx + kw, h / 2f + 15);
            g.dispose();
        }
    }
}
