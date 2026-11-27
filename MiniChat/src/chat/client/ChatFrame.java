package chat.client;

import chat.common.Message;
import chat.common.Message.Content;
import chat.common.Message.Scope;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Cửa sổ chat chính, bố cục kiểu Zalo / Discord:
 *  - Thanh điều hướng dọc: tin nhắn, khám phá nhóm, tạo nhóm, hồ sơ
 *  - Danh sách: hồ sơ, ô tìm kiếm, hội thoại + nhóm có thể tham gia + người online
 *  - Khung chat: tiêu đề, tin nhắn, thanh soạn tin
 *  - Ngăn thông tin (bật/tắt): ảnh nhóm, tên, quyền riêng tư, thành viên
 */
public class ChatFrame extends JFrame {

    private static final String ALL_KEY = "ALL";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final ClientConnection conn;
    private final String me;

    private final Map<String, ChatPanel> panels = new LinkedHashMap<>();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Set<String> onlineUsers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, RoomInfo> rooms = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /** Ảnh nhóm chọn lúc tạo nhóm, gửi lên khi server xác nhận đã vào phòng. */
    private final Map<String, byte[]> pendingRoomAvatar = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private String currentKey = ALL_KEY;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(listModel);
    private int hoverIndex = -1;
    private final Widgets.HintField search = new Widgets.HintField("", "Tìm người, nhóm, tin nhắn");
    private JComponent profileCard;
    private final Rail rail = new Rail();

    private final ConvHeader header = new ConvHeader();
    private final InfoPanel info = new InfoPanel();

    // thanh soạn tin
    private final Widgets.HintField input = new Widgets.HintField("", "Nhập tin nhắn…");
    private final Widgets.IconButton imageBtn = new Widgets.IconButton(Icons.Kind.IMAGE, 40, 22, "Gửi ảnh");
    private final Widgets.IconButton fileBtn = new Widgets.IconButton(Icons.Kind.CLIP, 40, 22, "Gửi tệp");
    private final Widgets.IconButton micBtn = new Widgets.IconButton(Icons.Kind.MIC, 40, 22, "Ghi âm");
    private final Widgets.IconButton stickerBtn = new Widgets.IconButton(Icons.Kind.STICKER, 40, 22, "Gửi sticker");
    private final Widgets.IconButton sendBtn = new Widgets.IconButton(Icons.Kind.SEND, 40, 19, "Gửi");
    private final Widgets.IconButton cancelRecBtn = new Widgets.IconButton(Icons.Kind.CLOSE, 40, 20, "Hủy ghi âm");
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel centerCards = new JPanel(centerLayout);
    private final RecIndicator recIndicator = new RecIndicator();
    private final Widgets.RoundedPanel composerBox = new Widgets.RoundedPanel(Theme.INPUT, Theme.BORDER, 26);
    private final AudioRecorder recorder = new AudioRecorder();
    private boolean recording;
    private long recStart;
    private final Timer recTimer = new Timer(120, e -> recIndicator.repaint());
    private boolean connected = true;

    public ChatFrame(ClientConnection conn) {
        super("Mini Chat — " + conn.getUsername());
        this.conn = conn;
        this.me = conn.getUsername();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1280, 760);
        setMinimumSize(new Dimension(860, 520));
        setLocationByPlatform(true);
        setIconImages(List.of(Theme.appIcon(32), Theme.appIcon(64), Theme.appIcon(256)));

        cards.setBackground(Theme.BG);
        openPanel(ALL_KEY, Scope.ALL, null, Message.PUBLIC_ROOM);

        JPanel chat = new JPanel(new BorderLayout());
        chat.setBackground(Theme.BG);
        chat.add(header, BorderLayout.NORTH);
        chat.add(cards, BorderLayout.CENTER);
        chat.add(buildComposer(), BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Theme.BG);
        right.add(chat, BorderLayout.CENTER);
        right.add(info, BorderLayout.EAST);
        info.setVisible(false);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(rail, BorderLayout.WEST);
        left.add(buildSidebar(), BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(left, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);

        select(ALL_KEY);

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { if (connected) input.requestFocusInWindow(); }
            @Override public void windowClosed(WindowEvent e) {
                conn.close();
                System.exit(0);
            }
        });

        conn.startListening(this::onMessage, () -> {
            connected = false;
            current().appendError("Mất kết nối tới server");
            input.setEnabled(false);
            input.setHint("Mất kết nối tới server");
            for (Widgets.IconButton b : new Widgets.IconButton[] { imageBtn, fileBtn, stickerBtn, micBtn, sendBtn })
                b.setEnabled(false);
        });
    }

    // =====================================================================
    //  Thanh điều hướng dọc (rail)
    // =====================================================================

    private final class Rail extends JPanel {
        private final String[] tips = { "Tin nhắn", "Khám phá nhóm", "Tạo nhóm mới" };
        private final Icons.Kind[] kinds = { Icons.Kind.CHAT, Icons.Kind.COMPASS, Icons.Kind.USERS };
        private int hover = -1;          // 0..2 = nút, 3 = avatar
        private static final int W = 72, TOP = 18, STEP = 58, BTN = 46;

        Rail() {
            setPreferredSize(new Dimension(W, 0));
            setOpaque(true);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int h = hit(e.getPoint());
                    if (h != hover) {
                        hover = h;
                        setCursor(h >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                        setToolTipText(h < 0 ? null : h == 3 ? "Hồ sơ của bạn" : tips[h]);
                        repaint();
                    }
                }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    switch (hit(e.getPoint())) {
                        case 0 -> { search.setText(""); select(ALL_KEY); }
                        case 1 -> discoverGroups();
                        case 2 -> createGroup();
                        case 3 -> editProfile();
                        default -> { }
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int hit(Point p) {
            if (p.x < (W - BTN) / 2 || p.x > (W + BTN) / 2) return -1;
            for (int i = 0; i < kinds.length; i++) {
                int y = TOP + i * STEP;
                if (p.y >= y && p.y < y + BTN) return i;
            }
            int ay = getHeight() - 20 - BTN;
            if (p.y >= ay && p.y < ay + BTN) return 3;
            return -1;
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int h = getHeight();
            g.setPaint(new GradientPaint(0, 0, new Color(0x0F1220), 0, h, new Color(0x0B0D16)));
            g.fillRect(0, 0, W, h);
            g.setColor(Theme.BORDER);
            g.fillRect(W - 1, 0, 1, h);
            int totalUnread = unread.values().stream().mapToInt(Integer::intValue).sum();
            for (int i = 0; i < kinds.length; i++) {
                int x = (W - BTN) / 2, y = TOP + i * STEP;
                boolean active = i == 0;
                if (active) {
                    g.setPaint(Theme.accentGradient(x, y, x + BTN, y + BTN));
                    g.fill(Theme.rr(x, y, BTN, BTN, 15));
                    g.setColor(Color.WHITE);
                    g.fill(Theme.rr(0, y + 10, 4, BTN - 20, 2));
                } else if (hover == i) {
                    g.setColor(Theme.alpha(Color.WHITE, 22));
                    g.fill(Theme.rr(x, y, BTN, BTN, 15));
                }
                Icons.draw(g, kinds[i], x + 12, y + 12, 22, active || hover == i ? Color.WHITE : Theme.TEXT_DIM);
                if (i == 0 && totalUnread > 0) {
                    String s = totalUnread > 99 ? "99+" : String.valueOf(totalUnread);
                    g.setFont(Theme.font(Font.BOLD, 10.5f));
                    FontMetrics fm = g.getFontMetrics();
                    int bw = Math.max(18, fm.stringWidth(s) + 10), bx = x + BTN - bw + 6, by = y - 5;
                    g.setColor(new Color(0x0F1220));
                    g.fill(Theme.rr(bx - 2, by - 2, bw + 4, 22, 11));
                    g.setColor(Theme.RED);
                    g.fill(Theme.rr(bx, by, bw, 18, 9));
                    g.setColor(Color.WHITE);
                    g.drawString(s, bx + (bw - fm.stringWidth(s)) / 2f, by + (18 - fm.getHeight()) / 2f + fm.getAscent());
                }
            }
            int ax = (W - BTN) / 2, ay = h - 20 - BTN;
            if (hover == 3) {
                g.setPaint(Theme.accentGradient(ax - 3, ay - 3, ax + BTN + 3, ay + BTN + 3));
                g.fill(new Ellipse2D.Double(ax - 3, ay - 3, BTN + 6, BTN + 6));
            }
            Theme.paintAvatar(g, ax, ay, BTN, me);
            Theme.paintOnlineDot(g, ax + BTN - 6, ay + BTN - 6, 5, new Color(0x0B0D16));
            g.dispose();
        }
    }

    // =====================================================================
    //  Sidebar
    // =====================================================================

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                g.setColor(Theme.SIDEBAR);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
                g.dispose();
            }
        };
        side.setPreferredSize(new Dimension(320, 0));
        side.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 1));

        // Hồ sơ (bấm để sửa) + nút tạo nhóm
        JPanel profile = new JPanel(new BorderLayout()) {
            boolean hover;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) { editProfile(); }
                });
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Sửa hồ sơ");
            }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g = Theme.aa(gr);
                if (hover) {
                    g.setColor(Theme.HOVER);
                    g.fill(Theme.rr(8, 8, getWidth() - 70, 62, 14));
                }
                Theme.paintAvatar(g, 16, 16, 46, me);
                Theme.paintOnlineDot(g, 16 + 46 - 6, 16 + 46 - 6, 5, Theme.SIDEBAR);
                g.setFont(Theme.font(Font.BOLD, 15.5f));
                g.setColor(Theme.TEXT);
                FontMetrics fm = g.getFontMetrics();
                String dn = Profiles.display(me);
                Theme.drawText(g, Theme.ellipsize(dn, fm, getWidth() - 150), 74, 16 + 20);
                int nw = Math.min(fm.stringWidth(dn), getWidth() - 150);
                if (hover) Icons.draw(g, Icons.Kind.EDIT, 74 + nw + 8, 16 + 7, 14, Theme.TEXT_DIM);
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                g.setColor(Theme.GREEN);
                g.drawString("● Đang hoạt động", 74, 16 + 40);
                g.dispose();
            }
        };
        profileCard = profile;
        profile.setOpaque(false);
        profile.setPreferredSize(new Dimension(0, 78));
        Widgets.IconButton create = new Widgets.IconButton(Icons.Kind.PLUS, 40, 22, "Tạo nhóm mới");
        create.setFill(Theme.alpha(Theme.ACCENT, 60));
        create.addActionListener(e -> createGroup());
        JPanel createWrap = new JPanel(new GridBagLayout());
        createWrap.setOpaque(false);
        createWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        createWrap.add(create);
        profile.add(createWrap, BorderLayout.EAST);

        // Ô tìm kiếm
        Widgets.RoundInput searchBox = new Widgets.RoundInput(search, Icons.Kind.SEARCH, 40);
        Dialogs.onChange(search, this::rebuildList);
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setOpaque(false);
        searchWrap.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 14));
        searchWrap.add(searchBox);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(profile, BorderLayout.NORTH);
        top.add(searchWrap, BorderLayout.SOUTH);
        side.add(top, BorderLayout.NORTH);

        // Danh sách
        list.setBackground(Theme.SIDEBAR);
        list.setOpaque(true);
        list.setCellRenderer(new RowView());
        list.setFocusable(false);
        list.setFixedCellHeight(-1);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getPoint());
                if (i >= 0 && SwingUtilities.isLeftMouseButton(e)) activate(listModel.get(i));
            }
            @Override public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getPoint());
                if (i != hoverIndex) {
                    hoverIndex = i;
                    list.setCursor(i >= 0 && listModel.get(i).kind != Kind.HEADER
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    list.repaint();
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (hoverIndex != -1) { hoverIndex = -1; list.repaint(); }
            }
        };
        list.addMouseListener(ma);
        list.addMouseMotionListener(ma);
        JScrollPane sp = new JScrollPane(list);
        sp.getViewport().setBackground(Theme.SIDEBAR);
        sp.setBackground(Theme.SIDEBAR);
        Widgets.slim(sp);
        side.add(sp, BorderLayout.CENTER);
        return side;
    }

    private int indexAt(Point p) {
        int i = list.locationToIndex(p);
        if (i < 0) return -1;
        java.awt.Rectangle r = list.getCellBounds(i, i);
        return r != null && r.contains(p) ? i : -1;
    }

    private void activate(Entry e) {
        switch (e.kind) {
            case CONV -> select(e.key);
            case USER -> {
                if (!e.target.equals(me)) openPrivate(e.target);
            }
            case ROOM_OPEN -> joinRoom(rooms.get(e.target));
            case ACTION -> discoverGroups();
            default -> { }
        }
    }

    private void openPrivate(String user) {
        String key = "PM:" + user;
        openPanel(key, Scope.PRIVATE, user, user);
        select(key);
    }

    // ---- Dữ liệu danh sách ----
    private enum Kind { HEADER, CONV, USER, ROOM_OPEN, ACTION }

    private static final class Entry {
        Kind kind;
        String key;          // khoá panel (CONV)
        String title;
        String sub = "";
        String target;       // tên người / tên phòng
        Scope scope;
        boolean online;
        boolean locked;
        int unread;
        long time;
        Entry(Kind k) { kind = k; }
    }

    private void rebuildList() {
        String q = Dialogs.norm(search.getText().trim());
        listModel.clear();
        int added = 0;

        List<Entry> convs = new ArrayList<>();
        for (Map.Entry<String, ChatPanel> me2 : panels.entrySet()) {
            ChatPanel p = me2.getValue();
            String title = convTitle(p);
            if (!q.isEmpty() && !Dialogs.norm(title).contains(q)
                    && !(p.target != null && Dialogs.norm(p.target).contains(q))) continue;
            Entry en = new Entry(Kind.CONV);
            en.key = me2.getKey();
            en.title = title;
            en.scope = p.scope;
            en.target = p.scope == Scope.PRIVATE ? p.target : p.title;
            en.unread = unread.getOrDefault(en.key, 0);
            en.time = p.getLastTime();
            en.online = p.scope == Scope.PRIVATE && onlineUsers.contains(p.target);
            en.locked = p.scope == Scope.ROOM && rooms.containsKey(p.target) && rooms.get(p.target).locked;
            en.sub = p.getPreview().isEmpty() ? defaultSub(p) : p.getPreview();
            convs.add(en);
        }
        convs.sort((a, b) -> a.key.equals(ALL_KEY) ? -1 : b.key.equals(ALL_KEY) ? 1 : Long.compare(b.time, a.time));
        if (!convs.isEmpty()) {
            listModel.addElement(headerEntry("CUỘC TRÒ CHUYỆN"));
            convs.forEach(listModel::addElement);
            added += convs.size();
        }

        List<Entry> open = new ArrayList<>();
        for (RoomInfo r : rooms.values()) {
            if (panels.containsKey("ROOM:" + r.name)) continue;
            if (!q.isEmpty() && !Dialogs.norm(r.name).contains(q)) continue;
            Entry en = new Entry(Kind.ROOM_OPEN);
            en.title = r.name;
            en.target = r.name;
            en.scope = Scope.ROOM;
            en.locked = r.locked;
            en.sub = (r.locked ? "Riêng tư" : "Cộng đồng") + " · " + r.count + " thành viên";
            open.add(en);
        }
        if (!open.isEmpty()) {
            listModel.addElement(headerEntry("NHÓM CÓ THỂ THAM GIA"));
            open.stream().limit(q.isEmpty() ? 5 : Long.MAX_VALUE).forEach(listModel::addElement);
            added += open.size();
            if (q.isEmpty() && open.size() > 5) {
                Entry more = new Entry(Kind.ACTION);
                more.title = "Xem tất cả " + open.size() + " nhóm";
                listModel.addElement(more);
            }
        }

        List<Entry> users = new ArrayList<>();
        for (String u : onlineUsers) {
            if (u.equals(me)) continue;
            if (!q.isEmpty() && !Dialogs.norm(Profiles.display(u)).contains(q) && !Dialogs.norm(u).contains(q)) continue;
            Entry en = new Entry(Kind.USER);
            en.title = Profiles.display(u);
            en.target = u;
            en.scope = Scope.PRIVATE;
            en.online = true;
            en.sub = Profiles.hasDisplayName(u) ? "@" + u : "Nhấn để nhắn tin riêng";
            users.add(en);
        }
        if (!users.isEmpty()) {
            listModel.addElement(headerEntry("ĐANG ONLINE — " + users.size()));
            users.forEach(listModel::addElement);
            added += users.size();
        }
        if (added == 0) listModel.addElement(headerEntry("KHÔNG TÌM THẤY KẾT QUẢ"));
        hoverIndex = -1;
        list.repaint();
        rail.repaint();
    }

    private static Entry headerEntry(String t) {
        Entry e = new Entry(Kind.HEADER);
        e.title = t;
        return e;
    }

    private static String convTitle(ChatPanel p) {
        return p.scope == Scope.PRIVATE ? Profiles.display(p.target) : p.title;
    }

    private String defaultSub(ChatPanel p) {
        return switch (p.scope) {
            case ALL -> onlineUsers.size() + " người đang online";
            case ROOM -> {
                RoomInfo r = rooms.get(p.target);
                yield r == null ? "Nhóm chat" : (r.locked ? "Nhóm riêng tư" : "Nhóm cộng đồng") + " · " + r.count + " thành viên";
            }
            case PRIVATE -> onlineUsers.contains(p.target) ? "Đang hoạt động" : "Ngoại tuyến";
        };
    }

    /** Một hàng trong danh sách bên trái (vẽ tay). */
    private final class RowView extends JComponent implements ListCellRenderer<Entry> {
        private Entry e;
        private boolean hover;

        RowView() { setOpaque(false); }

        @Override public Component getListCellRendererComponent(JList<? extends Entry> l, Entry v,
                int index, boolean sel, boolean focus) {
            e = v;
            hover = index == hoverIndex;
            return this;
        }

        @Override public Dimension getPreferredSize() {
            if (e == null) return new Dimension(10, 40);
            return new Dimension(10, switch (e.kind) {
                case HEADER -> 36;
                case CONV -> 66;
                case ACTION -> 40;
                default -> 58;
            });
        }

        @Override protected void paintComponent(Graphics gr) {
            if (e == null) return;
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            if (e.kind == Kind.HEADER) {
                g.setFont(Theme.font(Font.BOLD, 11.5f));
                g.setColor(Theme.TEXT_MUTED);
                g.drawString(e.title, 20, h - 9);
                g.dispose();
                return;
            }
            if (e.kind == Kind.ACTION) {
                g.setFont(Theme.font(Font.BOLD, 13f));
                g.setColor(hover ? Color.WHITE : new Color(0xA594FF));
                Icons.draw(g, Icons.Kind.COMPASS, 26, (h - 18) / 2.0, 18, hover ? Color.WHITE : new Color(0xA594FF));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(e.title, 54, (h - fm.getHeight()) / 2f + fm.getAscent());
                g.dispose();
                return;
            }
            boolean selected = e.kind == Kind.CONV && e.key.equals(currentKey);
            if (selected) {
                g.setPaint(new GradientPaint(8, 0, Theme.alpha(Theme.ACCENT, 80), w - 8, 0, Theme.alpha(Theme.ACCENT2, 36)));
                g.fill(Theme.rr(8, 2, w - 16, h - 4, 14));
                g.setPaint(Theme.accentGradient(0, 0, 0, h));
                g.fill(Theme.rr(8, 14, 3.5, h - 28, 2));
            } else if (hover) {
                g.setColor(Theme.HOVER);
                g.fill(Theme.rr(8, 2, w - 16, h - 4, 14));
            }
            Color rowBg = selected ? Theme.mix(Theme.SIDEBAR, Theme.ACCENT, 0.26)
                    : hover ? Theme.mix(Theme.SIDEBAR, Color.WHITE, 0.055) : Theme.SIDEBAR;

            int d = e.kind == Kind.CONV ? 46 : 40;
            int ax = 20, ay = (h - d) / 2;
            Theme.paintConvAvatar(g, e.scope, e.target, ax, ay, d);
            if (e.online) Theme.paintOnlineDot(g, ax + d - 6, ay + d - 6, 5, rowBg);
            if (e.locked) Theme.paintLockBadge(g, ax + d - 6, ay + d - 6, 7, rowBg);

            int tx = ax + d + 12;
            int right = w - 20;
            boolean hasUnread = e.unread > 0;

            int reserveTop = 0, reserveBottom = 0;
            if (e.kind == Kind.CONV && e.time > 0) {
                g.setFont(Theme.font(Font.PLAIN, 11.5f));
                String t = Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).format(HM);
                int tw = g.getFontMetrics().stringWidth(t);
                reserveTop = tw + 8;
                g.setColor(hasUnread ? Theme.ACCENT2 : Theme.TEXT_MUTED);
                g.drawString(t, right - tw, h / 2f - 7);
            }
            if (hasUnread) {
                String s = e.unread > 99 ? "99+" : String.valueOf(e.unread);
                g.setFont(Theme.font(Font.BOLD, 11.5f));
                FontMetrics bf = g.getFontMetrics();
                int bw = Math.max(20, bf.stringWidth(s) + 12), bh = 20;
                int bx = right - bw, by = h / 2 + 3;
                g.setPaint(new GradientPaint(bx, by, Theme.ACCENT, bx + bw, by + bh, Theme.ACCENT2));
                g.fill(Theme.rr(bx, by, bw, bh, bh / 2.0));
                g.setColor(Color.WHITE);
                g.drawString(s, bx + (bw - bf.stringWidth(s)) / 2f, by + (bh - bf.getHeight()) / 2f + bf.getAscent());
                reserveBottom = bw + 8;
            }
            if (e.kind == Kind.ROOM_OPEN) {
                String s = e.locked ? "Mở khóa" : "Vào";
                g.setFont(Theme.font(Font.BOLD, 12.5f));
                FontMetrics bf = g.getFontMetrics();
                int bw = bf.stringWidth(s) + 24, bh = 26;
                int bx = right - bw, by = (h - bh) / 2;
                if (hover) g.setPaint(Theme.accentGradient(bx, by, bx + bw, by + bh));
                else g.setColor(Theme.alpha(Theme.ACCENT, 50));
                g.fill(Theme.rr(bx, by, bw, bh, bh / 2.0));
                g.setColor(hover ? Color.WHITE : new Color(0xB9A9FF));
                g.drawString(s, bx + 12, by + (bh - bf.getHeight()) / 2f + bf.getAscent());
                reserveTop = reserveBottom = bw + 8;
            }

            Font tf = Theme.font(Font.BOLD, 15f);
            Font sf = Theme.font(Font.PLAIN, 13f);
            FontMetrics f1 = g.getFontMetrics(tf), f2 = g.getFontMetrics(sf);
            int total = f1.getHeight() + f2.getHeight() + 2;
            int top = (h - total) / 2;
            g.setFont(tf);
            g.setColor(Theme.TEXT);
            Theme.drawText(g, Theme.ellipsize(e.title, f1, right - tx - reserveTop), tx, top + f1.getAscent());
            g.setFont(sf);
            g.setColor(hasUnread ? Theme.TEXT : Theme.TEXT_DIM);
            Theme.drawText(g, Theme.ellipsize(e.sub, f2, right - tx - reserveBottom),
                    tx, top + f1.getHeight() + 2 + f2.getAscent());
            g.dispose();
        }
    }

    // =====================================================================
    //  Tiêu đề hội thoại
    // =====================================================================

    private final class ConvHeader extends JPanel {
        private final Widgets.IconButton addBtn = new Widgets.IconButton(Icons.Kind.USER_PLUS, 42, 21, "Thêm thành viên");
        private final Widgets.IconButton infoBtn = new Widgets.IconButton(Icons.Kind.INFO, 42, 22, "Thông tin hội thoại");
        private final Widgets.IconButton leaveBtn = new Widgets.IconButton(Icons.Kind.LEAVE, 42, 22, "Rời nhóm");
        private String title = "";
        private String target;
        private String sub = "";
        private Scope scope = Scope.ALL;
        private boolean online, locked;

        ConvHeader() {
            super(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(0, 70));
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(false);
            wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(0, 2, 0, 2);
            wrap.add(addBtn, c);
            wrap.add(infoBtn, c);
            wrap.add(leaveBtn, c);
            add(wrap, BorderLayout.EAST);
            addBtn.addActionListener(e -> addMembers(current().target));
            infoBtn.addActionListener(e -> toggleInfo());
            leaveBtn.addActionListener(e -> {
                ChatPanel p = current();
                if (p.scope == Scope.ROOM) send(Message.simple(Message.Type.ROOM_LEAVE, p.target));
                else if (p.scope == Scope.PRIVATE) closePanel("PM:" + p.target);
            });
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (e.getX() < getWidth() - 160) toggleInfo(); }
            };
            addMouseListener(ma);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void update() {
            ChatPanel p = panels.get(currentKey);
            if (p == null) return;
            title = convTitle(p);
            target = p.scope == Scope.PRIVATE ? p.target : p.title;
            scope = p.scope;
            online = p.scope == Scope.PRIVATE && onlineUsers.contains(p.target);
            locked = p.scope == Scope.ROOM && rooms.containsKey(p.target) && rooms.get(p.target).locked;
            sub = defaultSub(p);
            addBtn.setVisible(p.scope == Scope.ROOM);
            leaveBtn.setVisible(p.scope != Scope.ALL);
            leaveBtn.setToolTipText(p.scope == Scope.ROOM ? "Rời nhóm" : "Đóng cuộc trò chuyện");
            infoBtn.setFill(info.isVisible() ? Theme.alpha(Theme.ACCENT, 90) : null);
            repaint();
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.PANEL);
            g.fillRect(0, 0, w, h);
            g.setColor(Theme.BORDER);
            g.fillRect(0, h - 1, w, 1);
            int d = 44, ax = 22, ay = (h - 1 - d) / 2;
            Theme.paintConvAvatar(g, scope, target, ax, ay, d);
            if (online) Theme.paintOnlineDot(g, ax + d - 6, ay + d - 6, 5, Theme.PANEL);
            if (locked) Theme.paintLockBadge(g, ax + d - 6, ay + d - 6, 7, Theme.PANEL);
            Font tf = Theme.font(Font.BOLD, 16.5f), sf = Theme.font(Font.PLAIN, 13f);
            FontMetrics f1 = g.getFontMetrics(tf), f2 = g.getFontMetrics(sf);
            int total = f1.getHeight() + f2.getHeight() + 1;
            int top = (h - 1 - total) / 2;
            int tx = ax + d + 14;
            g.setFont(tf);
            g.setColor(Theme.TEXT);
            Theme.drawText(g, Theme.ellipsize(title, f1, w - tx - 180), tx, top + f1.getAscent());
            g.setFont(sf);
            g.setColor(online ? Theme.GREEN : locked ? new Color(0xFDC830) : Theme.TEXT_DIM);
            Theme.drawText(g, Theme.ellipsize(sub, f2, w - tx - 180), tx, top + f1.getHeight() + 1 + f2.getAscent());
            g.dispose();
        }
    }

    // =====================================================================
    //  Ngăn thông tin bên phải
    // =====================================================================

    private void toggleInfo() {
        info.setVisible(!info.isVisible());
        info.rebuild();
        header.update();
        getContentPane().revalidate();
    }

    private final class InfoPanel extends JPanel {
        private final JPanel body = new JPanel();

        InfoPanel() {
            super(new BorderLayout());
            setPreferredSize(new Dimension(300, 0));
            setBackground(Theme.SIDEBAR);
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(BorderFactory.createEmptyBorder(22, 18, 22, 18));
            JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(body, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(holder);
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            Widgets.slim(sp);
            add(sp);
        }

        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.SIDEBAR, Theme.ACCENT, 0.10), 0, 260, Theme.SIDEBAR));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Theme.BORDER);
            g.fillRect(0, 0, 1, getHeight());
            g.dispose();
        }

        private void center(JComponent c) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            row.setOpaque(false);
            row.add(c);
            row.setAlignmentX(0f);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            body.add(row);
        }

        private void gap(int h) { body.add(Box.createVerticalStrut(h)); }

        private void section(String text) {
            JLabel l = Dialogs.label(text, Theme.font(Font.BOLD, 11.5f), Theme.TEXT_MUTED);
            l.setAlignmentX(0f);
            body.add(l);
            gap(8);
        }

        void rebuild() {
            if (!isVisible()) return;
            body.removeAll();
            ChatPanel p = current();
            switch (p.scope) {
                case ALL -> buildPublic();
                case PRIVATE -> buildPrivate(p.target);
                case ROOM -> buildRoom(p.target);
            }
            body.revalidate();
            body.repaint();
        }

        private void buildPublic() {
            center(new BigAvatar(Scope.ALL, Message.PUBLIC_ROOM, null));
            gap(12);
            center(Dialogs.label(Message.PUBLIC_ROOM, Theme.font(Font.BOLD, 19f), Theme.TEXT));
            gap(4);
            center(Dialogs.label("Tin nhắn gửi tới mọi người trên server", Theme.font(Font.PLAIN, 12.5f), Theme.TEXT_DIM));
            gap(22);
            section("ĐANG ONLINE — " + onlineUsers.size());
            for (String u : onlineUsers) body.add(new MemberRow(u, false, false, null));
        }

        private void buildPrivate(String user) {
            center(new BigAvatar(Scope.PRIVATE, user, null));
            gap(12);
            center(Dialogs.label(Profiles.display(user), Theme.font(Font.BOLD, 19f), Theme.TEXT));
            gap(4);
            center(Dialogs.label("@" + user, Theme.font(Font.PLAIN, 13f), Theme.TEXT_MUTED));
            gap(8);
            boolean on = onlineUsers.contains(user);
            center(new Pill(on ? "Đang hoạt động" : "Ngoại tuyến", on ? Theme.GREEN : Theme.TEXT_MUTED, null));
            gap(22);
            List<RoomInfo> common = new ArrayList<>();
            for (RoomInfo r : rooms.values()) if (r.members.contains(user) && r.members.contains(me)) common.add(r);
            section("NHÓM CHUNG — " + common.size());
            if (common.isEmpty()) {
                JLabel l = Dialogs.label("Chưa có nhóm chung", Theme.font(Font.PLAIN, 13f), Theme.TEXT_MUTED);
                l.setAlignmentX(0f);
                body.add(l);
            }
            for (RoomInfo r : common) body.add(new GroupRow(r));
        }

        private void buildRoom(String roomName) {
            RoomInfo r = rooms.get(roomName);
            boolean owner = r != null && me.equals(r.owner);
            center(new BigAvatar(Scope.ROOM, roomName, () -> changeRoomAvatar(roomName)));
            gap(12);
            JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
            nameRow.setOpaque(false);
            nameRow.add(Dialogs.label(roomName, Theme.font(Font.BOLD, 19f), Theme.TEXT));
            if (owner) {
                Widgets.IconButton edit = new Widgets.IconButton(Icons.Kind.EDIT, 30, 16, "Đổi tên nhóm");
                edit.addActionListener(e -> renameRoom(roomName));
                nameRow.add(edit);
            }
            nameRow.setAlignmentX(0f);
            nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            body.add(nameRow);
            gap(8);
            boolean locked = r != null && r.locked;
            center(new Pill(locked ? "Nhóm riêng tư" : "Nhóm cộng đồng", locked ? new Color(0xFDC830) : Theme.GREEN,
                    locked ? Icons.Kind.LOCK : Icons.Kind.GLOBE));
            gap(18);

            JPanel actions = new JPanel(new java.awt.GridLayout(1, 3, 8, 0));
            actions.setOpaque(false);
            actions.add(new ActionTile(Icons.Kind.USER_PLUS, "Thêm", () -> addMembers(roomName)));
            actions.add(new ActionTile(Icons.Kind.CAMERA, "Ảnh nhóm", () -> changeRoomAvatar(roomName)));
            actions.add(new ActionTile(Icons.Kind.LEAVE, "Rời nhóm", () -> send(Message.simple(Message.Type.ROOM_LEAVE, roomName))));
            actions.setAlignmentX(0f);
            actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
            actions.setPreferredSize(new Dimension(260, 72));
            body.add(actions);
            gap(22);

            List<String> mem = r == null ? List.of() : r.members;
            section("THÀNH VIÊN — " + mem.size());
            for (String u : mem) {
                boolean isOwner = r != null && u.equals(r.owner);
                Runnable kick = owner && !u.equals(me) ? () -> kickMember(roomName, u) : null;
                body.add(new MemberRow(u, isOwner, true, kick));
            }
        }
    }

    /** Avatar lớn trong ngăn thông tin; bấm để đổi ảnh (với nhóm). */
    private final class BigAvatar extends JComponent {
        final Scope scope;
        final String name;
        final Runnable onClick;
        boolean hover;
        BigAvatar(Scope scope, String name, Runnable onClick) {
            this.scope = scope;
            this.name = name;
            this.onClick = onClick;
            setPreferredSize(new Dimension(104, 104));
            if (onClick != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Đổi ảnh nhóm");
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
                });
            }
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            g.setPaint(Theme.accentGradient(0, 0, 104, 104));
            g.fill(new Ellipse2D.Double(0, 0, 104, 104));
            g.setColor(Theme.SIDEBAR);
            g.fill(new Ellipse2D.Double(3, 3, 98, 98));
            Theme.paintConvAvatar(g, scope, name, 6, 6, 92);
            if (hover) {
                g.setColor(new Color(0, 0, 0, 120));
                g.fill(new Ellipse2D.Double(6, 6, 92, 92));
                Icons.draw(g, Icons.Kind.CAMERA, 36, 36, 32, Color.WHITE);
            }
            g.dispose();
        }
    }

    /** Nhãn viên thuốc nhỏ (trạng thái / loại nhóm). */
    private static final class Pill extends JComponent {
        final String text;
        final Color color;
        final Icons.Kind icon;
        Pill(String text, Color color, Icons.Kind icon) {
            this.text = text;
            this.color = color;
            this.icon = icon;
            FontMetrics fm = getFontMetrics(Theme.font(Font.BOLD, 12f));
            setPreferredSize(new Dimension(fm.stringWidth(text) + (icon != null ? 44 : 28), 26));
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.alpha(color, 36));
            g.fill(Theme.rr(0, 0, w, h, h / 2.0));
            int x = 14;
            if (icon != null) { Icons.draw(g, icon, 11, (h - 14) / 2.0, 14, color); x = 30; }
            g.setFont(Theme.font(Font.BOLD, 12f));
            g.setColor(color);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x, (h - fm.getHeight()) / 2f + fm.getAscent());
            g.dispose();
        }
    }

    /** Ô thao tác nhanh: biểu tượng + chữ. */
    private static final class ActionTile extends JComponent {
        final Icons.Kind icon;
        final String text;
        boolean hover;
        ActionTile(Icons.Kind icon, String text, Runnable action) {
            this.icon = icon;
            this.text = text;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
            });
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            g.setColor(hover ? Theme.alpha(Theme.ACCENT, 60) : Theme.alpha(Color.WHITE, 10));
            g.fill(Theme.rr(0, 0, w, h, 14));
            Icons.draw(g, icon, (w - 22) / 2.0, 12, 22, hover ? Color.WHITE : Theme.TEXT_DIM);
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.setColor(hover ? Color.WHITE : Theme.TEXT_DIM);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (w - fm.stringWidth(text)) / 2f, h - 14);
            g.dispose();
        }
    }

    /** Một thành viên trong ngăn thông tin: avatar, tên, vai trò, nút mời ra (trưởng nhóm). */
    private final class MemberRow extends JComponent {
        final String user;
        final boolean owner, showRole;
        final Runnable kick;
        boolean hover, hoverKick;
        MemberRow(String user, boolean owner, boolean showRole, Runnable kick) {
            this.user = user;
            this.owner = owner;
            this.showRole = showRole;
            this.kick = kick;
            setAlignmentX(0f);
            setPreferredSize(new Dimension(10, 52));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            if (!user.equals(me)) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; hoverKick = false; repaint(); }
                @Override public void mouseMoved(MouseEvent e) {
                    boolean k = kick != null && e.getX() > getWidth() - 40;
                    if (k != hoverKick) { hoverKick = k; setToolTipText(k ? "Mời ra khỏi nhóm" : null); repaint(); }
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (kick != null && e.getX() > getWidth() - 40) kick.run();
                    else if (!user.equals(me)) openPrivate(user);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            if (hover) { g.setColor(Theme.HOVER); g.fill(Theme.rr(0, 2, w, h - 4, 12)); }
            int d = 36, ay = (h - d) / 2;
            Theme.paintAvatar(g, 8, ay, d, user);
            if (onlineUsers.contains(user)) Theme.paintOnlineDot(g, 8 + d - 5, ay + d - 5, 4, Theme.SIDEBAR);
            int tx = 8 + d + 12;
            g.setFont(Theme.font(Font.BOLD, 14f));
            g.setColor(Theme.TEXT);
            FontMetrics f1 = g.getFontMetrics();
            String n = Profiles.display(user) + (user.equals(me) ? " (bạn)" : "");
            int maxW = w - tx - (kick != null ? 44 : 10);
            Theme.drawText(g, Theme.ellipsize(n, f1, maxW), tx, h / 2f - 2);
            g.setFont(Theme.font(Font.PLAIN, 12f));
            if (showRole && owner) {
                Icons.draw(g, Icons.Kind.CROWN, tx, h / 2f + 4, 13, new Color(0xFDC830));
                g.setColor(new Color(0xFDC830));
                g.drawString("Trưởng nhóm", tx + 17, h / 2f + 14);
            } else {
                g.setColor(Theme.TEXT_MUTED);
                g.drawString("@" + user, tx, h / 2f + 14);
            }
            if (kick != null && hover) {
                double cx = w - 22, cy = h / 2.0;
                if (hoverKick) { g.setColor(Theme.alpha(Theme.RED, 60)); g.fill(new Ellipse2D.Double(cx - 15, cy - 15, 30, 30)); }
                Icons.draw(g, Icons.Kind.CLOSE, cx - 8, cy - 8, 16, hoverKick ? Theme.RED : Theme.TEXT_MUTED);
            }
            g.dispose();
        }
    }

    /** Một nhóm chung trong ngăn thông tin chat riêng. */
    private final class GroupRow extends JComponent {
        final RoomInfo r;
        boolean hover;
        GroupRow(RoomInfo r) {
            this.r = r;
            setAlignmentX(0f);
            setPreferredSize(new Dimension(10, 50));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { select("ROOM:" + r.name); }
            });
        }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            int w = getWidth(), h = getHeight();
            if (hover) { g.setColor(Theme.HOVER); g.fill(Theme.rr(0, 2, w, h - 4, 12)); }
            Theme.paintConvAvatar(g, Scope.ROOM, r.name, 8, (h - 34) / 2, 34);
            g.setFont(Theme.font(Font.BOLD, 14f));
            g.setColor(Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            Theme.drawText(g, Theme.ellipsize(r.name, fm, w - 70), 54, h / 2f - 2);
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.setColor(Theme.TEXT_MUTED);
            g.drawString(r.count + " thành viên", 54, h / 2f + 14);
            g.dispose();
        }
    }

    // =====================================================================
    //  Nhóm & hồ sơ: các thao tác
    // =====================================================================

    private List<String> othersOnline() {
        List<String> l = new ArrayList<>();
        for (String u : onlineUsers) if (!u.equals(me)) l.add(u);
        return l;
    }

    private void createGroup() {
        if (!connected) return;
        Dialogs.GroupSpec g = Dialogs.createGroup(this, othersOnline());
        if (g == null) return;
        if (g.avatar() != null) pendingRoomAvatar.put(g.name(), g.avatar());
        Message m = Message.simple(Message.Type.ROOM_CREATE, g.name());
        m.password = g.password();
        m.list = new ArrayList<>(g.members());
        send(m);
    }

    private void discoverGroups() {
        Dialogs.discover(this, new ArrayList<>(rooms.values()),
                name -> panels.containsKey("ROOM:" + name), this::joinRoom);
    }

    /** Vào một nhóm: đã là thành viên thì mở, nhóm riêng tư thì hỏi mật khẩu. */
    private void joinRoom(RoomInfo r) {
        if (r == null) return;
        if (panels.containsKey("ROOM:" + r.name)) { select("ROOM:" + r.name); return; }
        Message m = Message.simple(Message.Type.ROOM_JOIN, r.name);
        if (r.locked) {
            String pass = Dialogs.askPassword(this, r.name);
            if (pass == null) return;
            m.password = pass;
        }
        send(m);
    }

    private void addMembers(String room) {
        RoomInfo r = rooms.get(room);
        List<String> cands = othersOnline();
        if (r != null) cands.removeIf(u -> r.members.stream().anyMatch(u::equalsIgnoreCase));
        List<String> chosen = Dialogs.pickMembers(this, room, cands);
        if (chosen == null || chosen.isEmpty()) return;
        Message m = new Message(Message.Type.ROOM_INVITE);
        m.to = room;
        m.list = new ArrayList<>(chosen);
        send(m);
    }

    private void renameRoom(String room) {
        String n = Widgets.prompt(this, "Đổi tên nhóm", "Tên mới cho nhóm \"" + room + "\"", "Tên nhóm", "Đổi tên");
        if (n == null || n.isBlank() || n.trim().equals(room)) return;
        Message m = Message.simple(Message.Type.ROOM_RENAME, n.trim());
        m.to = room;
        send(m);
    }

    private void changeRoomAvatar(String room) {
        byte[] data = chooseAvatar();
        if (data == null) return;
        Message m = new Message(Message.Type.PROFILE_UPDATE);
        m.scope = Scope.ROOM;
        m.to = room;
        m.data = data;
        send(m);
    }

    private void kickMember(String room, String user) {
        Message m = Message.simple(Message.Type.ROOM_KICK, user);
        m.to = room;
        send(m);
    }

    private byte[] chooseAvatar() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Ảnh (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        try {
            return Profiles.avatarBytes(fc.getSelectedFile());
        } catch (IOException ex) {
            current().appendError("Không đọc được ảnh: " + ex.getMessage());
            return null;
        }
    }

    private void editProfile() {
        if (!connected) return;
        Dialogs.ProfileSpec p = Dialogs.editProfile(this, me);
        if (p == null) return;
        Message m = new Message(Message.Type.PROFILE_UPDATE);
        m.text = p.displayName();
        m.data = p.avatar();
        send(m);
    }

    /** Hồ sơ thay đổi: vẽ lại mọi nơi có tên / avatar. */
    private void profilesChanged() {
        for (ChatPanel p : panels.values()) p.refreshProfiles();
        header.update();
        info.rebuild();
        rebuildList();
        if (profileCard != null) profileCard.repaint();
        rail.repaint();
        setTitle("Mini Chat — " + Profiles.display(me));
    }

    // =====================================================================
    //  Thanh soạn tin
    // =====================================================================

    private JComponent buildComposer() {
        sendBtn.setGradient(true);
        sendBtn.setEnabled(false);
        sendBtn.addActionListener(e -> sendText());
        input.addActionListener(e -> sendText());          // nhấn Enter để gửi
        input.getDocument().addDocumentListener(new DocumentListener() {
            void ch() { sendBtn.setEnabled(connected && !input.getText().trim().isEmpty()); }
            @Override public void insertUpdate(DocumentEvent e) { ch(); }
            @Override public void removeUpdate(DocumentEvent e) { ch(); }
            @Override public void changedUpdate(DocumentEvent e) { ch(); }
        });
        input.setFont(Theme.font(Font.PLAIN, 15f));
        input.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { composerBox.setBorderColor(Theme.ACCENT); }
            @Override public void focusLost(FocusEvent e) { composerBox.setBorderColor(Theme.BORDER); }
        });
        imageBtn.addActionListener(e -> chooseAndSend(Content.IMAGE));
        fileBtn.addActionListener(e -> chooseAndSend(Content.FILE));
        micBtn.addActionListener(e -> toggleRecord());
        stickerBtn.addActionListener(e -> showStickers());
        cancelRecBtn.fg = Theme.RED;
        cancelRecBtn.hoverFg = Theme.RED;
        cancelRecBtn.addActionListener(e -> stopRecording(false));

        JPanel inputWrap = new JPanel(new GridBagLayout());
        inputWrap.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 6, 0, 6);
        inputWrap.add(input, c);

        JPanel recWrap = new JPanel(new BorderLayout());
        recWrap.setOpaque(false);
        recWrap.add(cancelRecBtn, BorderLayout.WEST);
        recWrap.add(recIndicator, BorderLayout.CENTER);

        centerCards.setOpaque(false);
        centerCards.add(inputWrap, "text");
        centerCards.add(recWrap, "rec");

        composerBox.setLayout(new BorderLayout());
        composerBox.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 6));
        composerBox.add(rowOf(imageBtn, fileBtn), BorderLayout.WEST);
        composerBox.add(centerCards, BorderLayout.CENTER);
        composerBox.add(rowOf(stickerBtn, micBtn, sendBtn), BorderLayout.EAST);
        composerBox.setPreferredSize(new Dimension(0, 56));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createEmptyBorder(12, 18, 14, 18));
        bar.add(composerBox);
        return bar;
    }

    private static JPanel rowOf(JComponent... cs) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 1, 0, 1);
        for (JComponent x : cs) p.add(x, c);
        return p;
    }

    /** Chỉ báo đang ghi âm: chấm đỏ nhấp nháy + thời gian. */
    private final class RecIndicator extends JComponent {
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g = Theme.aa(gr);
            double pulse = 0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 220.0);
            int cy = getHeight() / 2;
            g.setColor(Theme.alpha(Theme.RED, (int) (255 * pulse)));
            g.fill(new Ellipse2D.Double(8, cy - 6, 12, 12));
            long sec = recording ? (System.currentTimeMillis() - recStart) / 1000 : 0;
            String t = "Đang ghi âm  " + (sec / 60) + ":" + String.format("%02d", sec % 60);
            g.setFont(Theme.font(Font.BOLD, 14.5f));
            g.setColor(Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            int tx = 30;
            g.drawString(t, tx, (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            g.setColor(Theme.TEXT_MUTED);
            g.drawString("Nhấn ■ để dừng và gửi", tx + fm.stringWidth(t) + 14,
                    (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g.dispose();
        }
    }

    // ===================== Gửi =====================

    private void sendText() {
        String text = input.getText().trim();
        if (text.isEmpty() || !connected) return;
        ChatPanel p = current();
        send(Message.text(p.scope, p.target, text));
        input.setText("");
    }

    private void chooseAndSend(Content kind) {
        JFileChooser fc = new JFileChooser();
        if (kind == Content.IMAGE)
            fc.setFileFilter(new FileNameExtensionFilter("Ảnh (png, jpg, gif, bmp)",
                    "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (f.length() > Message.MAX_FILE_SIZE) {
            current().appendError("Tệp lớn hơn 10 MB, không thể gửi");
            return;
        }
        try {
            sendFile(kind, f.getName(), Files.readAllBytes(f.toPath()));
        } catch (IOException ex) {
            current().appendError("Không đọc được tệp: " + ex.getMessage());
        }
    }

    void sendFile(Content kind, String name, byte[] data) {
        ChatPanel p = current();
        send(Message.file(p.scope, p.target, kind, name, data));
    }

    private void toggleRecord() {
        if (!recording) {
            try {
                recorder.start();
            } catch (LineUnavailableException | RuntimeException ex) {
                current().appendError("Không mở được micro: " + ex.getMessage());
                return;
            }
            recording = true;
            recStart = System.currentTimeMillis();
            micBtn.setFill(Theme.RED);
            micBtn.setKind(Icons.Kind.STOP);
            micBtn.setToolTipText("Dừng & gửi");
            imageBtn.setEnabled(false);
            fileBtn.setEnabled(false);
            stickerBtn.setEnabled(false);
            centerLayout.show(centerCards, "rec");
            recTimer.start();
        } else {
            stopRecording(true);
        }
    }

    private void stopRecording(boolean sendIt) {
        if (!recording) return;
        recording = false;
        recTimer.stop();
        micBtn.setFill(null);
        micBtn.setKind(Icons.Kind.MIC);
        micBtn.setToolTipText("Ghi âm");
        imageBtn.setEnabled(connected);
        fileBtn.setEnabled(connected);
        stickerBtn.setEnabled(connected);
        centerLayout.show(centerCards, "text");
        input.requestFocusInWindow();
        try {
            byte[] wav = recorder.stop();
            if (sendIt) sendFile(Content.AUDIO, "ghiam_" + System.currentTimeMillis() + ".wav", wav);
        } catch (IOException | RuntimeException ex) {
            current().appendError("Lỗi ghi âm: " + ex.getMessage());
        }
    }

    private void send(Message m) {
        try {
            conn.send(m);
        } catch (IOException e) {
            current().appendError("Gửi thất bại: " + e.getMessage());
        }
    }

    // ===================== Nhận =====================

    void onMessage(Message m) {
        switch (m.type) {
            case CHAT -> {
                ChatPanel p = panelFor(m);
                p.appendMessage(m);
                String key = keyOf(p);
                if (!key.equals(currentKey)) unread.merge(key, 1, Integer::sum);
                rebuildList();
            }
            case SYSTEM -> {
                ChatPanel p = (m.scope == Scope.ROOM) ? panels.get("ROOM:" + m.to) : panels.get(ALL_KEY);
                if (p != null) p.appendSystem(m.text);
            }
            case ERROR -> current().appendError(m.text);
            case USER_LIST -> {
                onlineUsers.clear();
                if (m.list != null) onlineUsers.addAll(m.list);
                header.update();
                info.rebuild();
                rebuildList();
            }
            case ROOM_LIST -> {
                rooms.clear();
                if (m.list != null) for (String item : m.list) {
                    RoomInfo r = RoomInfo.parse(item);
                    rooms.put(r.name, r);
                }
                header.update();
                info.rebuild();
                rebuildList();
            }
            case ROOM_JOIN -> {
                ChatPanel p = openPanel("ROOM:" + m.text, Scope.ROOM, m.text, m.text);
                select(keyOf(p));
                p.appendSystem("Bạn đã vào nhóm " + m.text);
                byte[] av = pendingRoomAvatar.remove(m.text);
                if (av != null) {
                    Message up = new Message(Message.Type.PROFILE_UPDATE);
                    up.scope = Scope.ROOM;
                    up.to = m.text;
                    up.data = av;
                    send(up);
                }
            }
            case ROOM_LEAVE -> closePanel("ROOM:" + m.text);
            case ROOM_RENAME -> renamePanel(m.to, m.text);
            case REACT -> {
                ChatPanel p = findPanel(m);
                if (p != null) p.applyReaction(m.id, m.from, m.text);
            }
            case PROFILE -> {
                if (m.scope == Scope.ROOM) Profiles.setRoom(m.to, m.data);
                else Profiles.setUser(m.from, m.text, m.data);
                profilesChanged();
            }
            default -> { }
        }
    }

    /** Hội thoại đang mở ứng với một gói (không tự mở hội thoại mới). */
    private ChatPanel findPanel(Message m) {
        if (m.scope == null) return null;
        return switch (m.scope) {
            case ALL -> panels.get(ALL_KEY);
            case ROOM -> panels.get("ROOM:" + m.to);
            case PRIVATE -> panels.get("PM:" + (me.equals(m.from) ? m.to : m.from));
        };
    }

    /** Mở bảng sticker phía trên thanh soạn tin; chọn là gửi ngay. */
    private void showStickers() {
        if (!connected) return;
        javax.swing.JPopupMenu pop = Stickers.picker(code -> {
            ChatPanel p = current();
            Message m = Message.text(p.scope, p.target, code);
            m.content = Content.STICKER;
            send(m);
        });
        Dimension ps = pop.getPreferredSize();
        pop.show(composerBox, Math.max(0, composerBox.getWidth() - ps.width), -ps.height - 10);
    }

    /** Xác định hội thoại sẽ hiển thị một tin CHAT. */
    private ChatPanel panelFor(Message m) {
        return switch (m.scope) {
            case ALL -> panels.get(ALL_KEY);
            case ROOM -> openPanel("ROOM:" + m.to, Scope.ROOM, m.to, m.to);
            case PRIVATE -> {
                String other = me.equals(m.from) ? m.to : m.from;
                yield openPanel("PM:" + other, Scope.PRIVATE, other, other);
            }
        };
    }

    // ===================== Quản lý hội thoại =====================

    private String keyOf(ChatPanel p) {
        for (Map.Entry<String, ChatPanel> e : panels.entrySet()) if (e.getValue() == p) return e.getKey();
        return ALL_KEY;
    }

    private ChatPanel openPanel(String key, Scope scope, String target, String title) {
        ChatPanel p = panels.get(key);
        if (p == null) {
            p = new ChatPanel(scope, target, title, me);
            ChatPanel panel = p;
            p.setOnReact((id, emoji) -> {
                Message r = new Message(Message.Type.REACT);
                r.scope = panel.scope;
                r.to = panel.target;
                r.id = id;
                r.text = emoji;
                send(r);
            });
            panels.put(key, p);
            cards.add(p, key);
            rebuildList();
        }
        return p;
    }

    /** Trưởng nhóm đổi tên: chuyển hội thoại sang khoá mới, giữ nguyên lịch sử. */
    private void renamePanel(String oldName, String newName) {
        Profiles.renameRoom(oldName, newName);
        String oldKey = "ROOM:" + oldName, newKey = "ROOM:" + newName;
        ChatPanel p = panels.remove(oldKey);
        if (p != null) {
            Map<String, ChatPanel> copy = new LinkedHashMap<>(panels);
            panels.clear();
            panels.put(newKey, p);
            panels.putAll(copy);
            p.rename(newName);
            cards.remove(p);
            cards.add(p, newKey);
            Integer u = unread.remove(oldKey);
            if (u != null) unread.put(newKey, u);
            if (oldKey.equals(currentKey)) { currentKey = newKey; cardLayout.show(cards, newKey); }
        }
        header.update();
        info.rebuild();
        rebuildList();
    }

    private void select(String key) {
        ChatPanel p = panels.get(key);
        if (p == null) return;
        currentKey = key;
        cardLayout.show(cards, key);
        unread.remove(key);
        header.update();
        info.rebuild();
        rebuildList();
        SwingUtilities.invokeLater(p::jumpToBottom);
        if (connected && !recording) input.requestFocusInWindow();
    }

    private void closePanel(String key) {
        ChatPanel p = panels.remove(key);
        if (p == null) return;
        cards.remove(p);
        unread.remove(key);
        if (key.equals(currentKey)) select(ALL_KEY);
        else rebuildList();
        cards.revalidate();
        cards.repaint();
    }

    private ChatPanel current() {
        ChatPanel p = panels.get(currentKey);
        return p != null ? p : panels.get(ALL_KEY);
    }
}
