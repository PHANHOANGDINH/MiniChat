package chat.client;

import chat.common.Message;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.GradientPaint;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Điểm khởi động của client. Mỗi lần chạy lớp này là một client độc lập:
 * hiện hộp thoại đăng nhập / đăng ký, sau khi thành công mở cửa sổ chat.
 *
 * Chạy: java -cp out chat.client.ChatClient
 */
public class ChatClient {

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) { }
        Theme.install();
        SwingUtilities.invokeLater(() -> new LoginDialog().setVisible(true));
    }

    /** Kết quả xác thực chạy nền. */
    private record AuthResult(ClientConnection conn, String error) { }

    /** Hộp thoại đăng nhập / đăng ký. */
    static class LoginDialog extends JDialog {
        final Widgets.HintField host = new Widgets.HintField("localhost", "Máy chủ");
        final Widgets.HintField port = new Widgets.HintField("6000", "Cổng");
        final Widgets.HintField user = new Widgets.HintField("", "Tên đăng nhập");
        final Widgets.HintPassword pass = new Widgets.HintPassword("Mật khẩu");
        final Widgets.RoundInput userBox = new Widgets.RoundInput(user, Icons.Kind.USER, 48);
        final Widgets.RoundInput passBox = new Widgets.RoundInput(pass, Icons.Kind.LOCK, 48);
        final Widgets.RoundInput hostBox = new Widgets.RoundInput(host, Icons.Kind.SERVER, 44);
        final Widgets.RoundInput portBox = new Widgets.RoundInput(port, Icons.Kind.SERVER, 44);
        final Banner banner = new Banner();
        final Widgets.GradientButton loginBtn = new Widgets.GradientButton("Đăng nhập", true);
        final Widgets.GradientButton registerBtn = new Widgets.GradientButton("Tạo tài khoản mới", false);
        final JPanel serverPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        final Card card = new Card();
        final Link serverLink = new Link("Cài đặt máy chủ");
        boolean busy;

        LoginDialog() {
            super((java.awt.Frame) null, "Mini Chat — Đăng nhập", true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setIconImages(java.util.List.of(Theme.appIcon(32), Theme.appIcon(64), Theme.appIcon(256)));

            Backdrop backdrop = new Backdrop();
            backdrop.add(card);
            setContentPane(backdrop);

            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(BorderFactory.createEmptyBorder(34, 34, 30, 34));

            Logo logo = new Logo();
            JLabel title = label("Mini Chat", Font.BOLD, 27f, Theme.TEXT);
            JLabel sub = label("Đăng nhập để bắt đầu trò chuyện", Font.PLAIN, 13.5f, Theme.TEXT_DIM);

            add(card, logo);
            card.add(Box.createVerticalStrut(14));
            add(card, title);
            card.add(Box.createVerticalStrut(4));
            add(card, sub);
            card.add(Box.createVerticalStrut(22));
            add(card, banner);
            card.add(Box.createVerticalStrut(0));
            add(card, userBox);
            card.add(Box.createVerticalStrut(12));
            add(card, passBox);
            card.add(Box.createVerticalStrut(12));

            serverPanel.setOpaque(false);
            serverPanel.add(hostBox);
            serverPanel.add(portBox);
            serverPanel.setVisible(false);
            serverLink.addActionListener(e -> toggleServer());
            serverLink.setAlignmentX(0f);
            card.add(serverLink);
            card.add(Box.createVerticalStrut(6));
            add(card, serverPanel);
            card.add(Box.createVerticalStrut(14));

            loginBtn.addActionListener(e -> submit(Message.Type.LOGIN));
            registerBtn.addActionListener(e -> submit(Message.Type.REGISTER));
            user.addActionListener(e -> pass.requestFocusInWindow());
            pass.addActionListener(e -> submit(Message.Type.LOGIN));
            getRootPane().setDefaultButton(loginBtn);
            loginBtn.setPreferredSize(new Dimension(100, 48));
            registerBtn.setPreferredSize(new Dimension(100, 48));
            add(card, loginBtn);
            card.add(Box.createVerticalStrut(10));
            add(card, registerBtn);

            card.setPreferredSize(new Dimension(420, card.getPreferredSize().height));
            pack();
            setResizable(false);
            setLocationRelativeTo(null);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowOpened(java.awt.event.WindowEvent e) { user.requestFocusInWindow(); }
            });
        }

        /** Thêm một thành phần giãn hết chiều ngang card. */
        private static void add(JPanel card, JComponent c) {
            c.setAlignmentX(0f);
            Dimension p = c.getPreferredSize();
            if (!(c instanceof Widgets.RoundInput) && !(c instanceof JButton))
                c.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.height));
            else if (c instanceof JButton) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.height));
            card.add(c);
        }

        private static JLabel label(String text, int style, float size, Color color) {
            JLabel l = new JLabel(text, JLabel.CENTER);
            l.setFont(Theme.font(style, size));
            l.setForeground(color);
            return l;
        }

        private void toggleServer() {
            boolean show = !serverPanel.isVisible();
            serverPanel.setVisible(show);
            serverLink.setOpen(show);
            card.setPreferredSize(null);
            card.setPreferredSize(new Dimension(420, card.getPreferredSize().height));
            pack();
            card.revalidate();
        }

        void showError(String msg) {
            banner.show(msg);
            card.setPreferredSize(null);
            card.setPreferredSize(new Dimension(420, card.getPreferredSize().height));
            pack();
            shake();
        }

        /** Hiệu ứng rung nhẹ cả hộp thoại khi có lỗi. */
        private void shake() {
            java.awt.Point origin = getLocation();
            int[] dx = { -10, 9, -7, 6, -3, 2, 0 };
            int[] i = { 0 };
            Timer t = new Timer(32, null);
            t.addActionListener(e -> {
                if (i[0] >= dx.length) { setLocation(origin); t.stop(); return; }
                setLocation(origin.x + dx[i[0]++], origin.y);
            });
            t.start();
        }

        private void setBusy(boolean b, Message.Type type) {
            busy = b;
            loginBtn.setEnabled(!b);
            registerBtn.setEnabled(!b);
            user.setEnabled(!b);
            pass.setEnabled(!b);
            loginBtn.setText(b && type == Message.Type.LOGIN ? "Đang kết nối…" : "Đăng nhập");
            registerBtn.setText(b && type == Message.Type.REGISTER ? "Đang đăng ký…" : "Tạo tài khoản mới");
        }

        private void submit(Message.Type type) {
            if (busy) return;
            String u = user.getText().trim();
            String pw = new String(pass.getPassword());
            banner.hideNow();
            userBox.setError(false);
            passBox.setError(false);
            if (u.isEmpty() || pw.isEmpty()) {
                userBox.setError(u.isEmpty());
                passBox.setError(pw.isEmpty());
                showError("Hãy nhập tên đăng nhập và mật khẩu");
                return;
            }
            int portNum;
            try {
                portNum = Integer.parseInt(port.getText().trim());
            } catch (NumberFormatException e) {
                if (!serverPanel.isVisible()) toggleServer();
                portBox.setError(true);
                showError("Cổng không hợp lệ");
                return;
            }
            final String hostName = host.getText().trim();
            setBusy(true, type);
            new SwingWorker<AuthResult, Void>() {
                @Override protected AuthResult doInBackground() throws Exception {
                    ClientConnection conn = null;
                    try {
                        conn = new ClientConnection(hostName, portNum);
                        String err = conn.authenticate(type, u, pw);
                        if (err != null) { conn.close(); return new AuthResult(null, err); }
                        return new AuthResult(conn, null);
                    } catch (IOException ex) {
                        if (conn != null) conn.close();
                        throw ex;
                    }
                }

                @Override protected void done() {
                    setBusy(false, type);
                    try {
                        AuthResult r = get();
                        if (r.error() != null) {
                            userBox.setError(true);
                            if (type == Message.Type.LOGIN) passBox.setError(true);
                            showError(r.error());
                            return;
                        }
                        dispose();
                        new ChatFrame(r.conn()).setVisible(true);
                    } catch (ExecutionException ex) {
                        Throwable c = ex.getCause();
                        showError("Không kết nối được tới server " + hostName + ":" + portNum
                                + (c != null && c.getMessage() != null ? " (" + c.getMessage() + ")" : ""));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }.execute();
        }

        // ---------------- Thành phần giao diện riêng của hộp thoại ----------------

        /** Nền tối có hai quầng sáng màu. */
        private static final class Backdrop extends JPanel {
            Backdrop() { super(new GridBagLayout()); setBorder(BorderFactory.createEmptyBorder(36, 50, 36, 50)); }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                int w = getWidth(), h = getHeight();
                g.setPaint(new GradientPaint(0, 0, new Color(0x0B0E16), w, h, new Color(0x141028)));
                g.fillRect(0, 0, w, h);
                glow(g, w, h, w * 0.12f, h * 0.10f, Math.max(w, h) * 0.75f, new Color(0x7C5CFF), 90);
                glow(g, w, h, w * 0.92f, h * 0.95f, Math.max(w, h) * 0.8f, new Color(0x3D8BFF), 80);
                g.dispose();
            }
            private static void glow(Graphics2D g, int w, int h, float cx, float cy, float r, Color c, int a) {
                g.setPaint(new RadialGradientPaint(cx, cy, r, new float[] { 0f, 1f },
                        new Color[] { Theme.alpha(c, a), Theme.alpha(c, 0) }));
                g.fillRect(0, 0, w, h);
            }
            @Override public boolean isOpaque() { return true; }
        }

        /** Thẻ đăng nhập bo tròn có bóng. */
        private static final class Card extends JPanel {
            Card() { setOpaque(false); }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                int w = getWidth(), h = getHeight();
                Theme.paintShadow(g, 6, 6, w - 12, h - 12, 24, 10);
                g.setColor(Theme.PANEL);
                g.fill(Theme.rr(6, 6, w - 12, h - 12, 24));
                g.setColor(Theme.BORDER);
                g.setStroke(new java.awt.BasicStroke(1f));
                g.draw(Theme.rr(6.5, 6.5, w - 13, h - 13, 24));
                g.dispose();
            }
            @Override public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(d.width, d.height);
            }
            @Override public java.awt.Insets getInsets() {
                java.awt.Insets i = super.getInsets();
                return new java.awt.Insets(i.top + 6, i.left + 6, i.bottom + 6, i.right + 6);
            }
        }

        private static final class Logo extends JComponent {
            Logo() { setPreferredSize(new Dimension(68, 68)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 68)); }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                int s = 64, x = (getWidth() - s) / 2, y = 2;
                g.setColor(Theme.alpha(Theme.ACCENT, 50));
                g.fill(Theme.rr(x - 4, y - 2, s + 8, s + 8, 22));
                g.setPaint(Theme.accentGradient(x, y, x + s, y + s));
                g.fill(Theme.rr(x, y, s, s, 20));
                Icons.draw(g, Icons.Kind.CHAT, x + 16, y + 16, 32, Color.WHITE);
                g.dispose();
            }
        }

        /** Khung báo lỗi đỏ, ẩn khi không có lỗi. */
        static final class Banner extends JComponent {
            private String text = "";
            Banner() { setVisible(false); setPreferredSize(new Dimension(100, 0)); }
            void show(String t) {
                text = t;
                setVisible(true);
                setPreferredSize(new Dimension(100, 44));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
                revalidate();
                repaint();
            }
            void hideNow() {
                if (!isVisible()) return;
                setVisible(false);
                revalidate();
            }
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, isVisible() ? 56 : 0);
            }
            @Override public Dimension getPreferredSize() {
                if (!isVisible()) return new Dimension(100, 0);
                FontMetrics fm = getFontMetrics(Theme.font(Font.PLAIN, 13f));
                int lines = Math.max(1, Theme.wrap(text, fm, 280).size());
                return new Dimension(100, 16 + lines * (fm.getHeight() + 1) + 12);
            }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                int w = getWidth(), h = getHeight() - 12;
                g.setColor(Theme.alpha(Theme.RED, 38));
                g.fill(Theme.rr(0, 0, w, h, 12));
                g.setColor(Theme.alpha(Theme.RED, 120));
                g.setStroke(new java.awt.BasicStroke(1f));
                g.draw(Theme.rr(0.5, 0.5, w - 1, h - 1, 12));
                g.setColor(Theme.RED);
                g.fill(new Ellipse2D.Double(12, h / 2.0 - 9, 18, 18));
                g.setColor(Color.WHITE);
                g.setFont(Theme.font(Font.BOLD, 13f));
                g.drawString("!", 12 + 9 - g.getFontMetrics().stringWidth("!") / 2f,
                        h / 2f - g.getFontMetrics().getHeight() / 2f + g.getFontMetrics().getAscent());
                g.setFont(Theme.font(Font.PLAIN, 13f));
                g.setColor(new Color(0xFFB4B7));
                FontMetrics fm = g.getFontMetrics();
                java.util.List<String> lines = Theme.wrap(text, fm, w - 52);
                int ty = (h - lines.size() * (fm.getHeight() + 1)) / 2 + fm.getAscent();
                for (String l : lines) { g.drawString(l, 40, ty); ty += fm.getHeight() + 1; }
                g.dispose();
            }
        }

        /** Nút dạng liên kết có mũi tên. */
        private static final class Link extends JButton {
            private boolean open;
            Link(String text) {
                super(text);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setOpaque(false);
                setRolloverEnabled(true);
                setFocusable(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFont(Theme.font(Font.PLAIN, 13f));
                FontMetrics fm = getFontMetrics(getFont());
                setPreferredSize(new Dimension(fm.stringWidth(text) + 40, 24));
                setMaximumSize(getPreferredSize());
            }
            void setOpen(boolean o) { open = o; repaint(); }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                Color c = getModel().isRollover() ? Theme.TEXT : Theme.TEXT_DIM;
                g.setFont(getFont());
                g.setColor(c);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(getText(), 2, (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
                Icons.draw(g, open ? Icons.Kind.CHEVRON_UP : Icons.Kind.CHEVRON_DOWN,
                        fm.stringWidth(getText()) + 6, (getHeight() - 16) / 2.0, 16, c);
                g.dispose();
            }
        }
    }
}
