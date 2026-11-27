package chat.server;

import chat.common.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server chat chạy trên máy cá nhân (mặc định cổng 5000).
 * Mỗi client kết nối được phục vụ bởi một luồng ClientHandler riêng.
 *
 * Chạy: java -cp out chat.server.ChatServer [cổng] [tệp_người_dùng]
 */
public class ChatServer {

    public static final int DEFAULT_PORT = 6000;

    private final int port;
    private final UserStore userStore;
    private final ProfileStore profiles;
    /** Các client đã đăng nhập: tên -> luồng xử lý. */
    private final Map<String, ClientHandler> online = new ConcurrentHashMap<>();
    /** Các phòng chat: tên phòng -> phòng. */
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /** Một phòng / nhóm chat. Phòng có mật khẩu là nhóm riêng tư. */
    static final class Room {
        String name;
        String owner;
        final String salt = Long.toHexString(new java.security.SecureRandom().nextLong());
        String passHash;         // null = nhóm cộng đồng
        final Set<String> members = Collections.synchronizedSet(new LinkedHashSet<>());
        volatile byte[] avatar;

        Room(String name, String owner) {
            this.name = name;
            this.owner = owner;

        }

        boolean locked() { return passHash != null; }
    }

    public ChatServer(int port, Path userFile) throws IOException {
        this.port = port;
        this.userStore = new UserStore(userFile);
        this.profiles = new ProfileStore(userFile);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        Path userFile = Path.of(args.length > 1 ? args[1] : "users.txt");
        new ChatServer(port, userFile).start();
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            log("Server đang lắng nghe tại cổng " + port + " — " + userStore.size()
                    + " tài khoản đã đăng ký");
            while (true) {
                Socket socket = server.accept();           // chờ client kết nối
                log("Kết nối mới từ " + socket.getRemoteSocketAddress());
                new ClientHandler(socket, this).start();   // mỗi client một luồng
            }
        }
    }

    // ================== Đăng ký / đăng nhập ==================

    UserStore users() { return userStore; }

    /** Đưa client vào danh sách online. Trả về false nếu tên đang được dùng ở phiên khác. */
    boolean addOnline(String name, ClientHandler handler) {
        return online.putIfAbsent(name, handler) == null;
    }

    boolean isOnline(String name) { return online.containsKey(name); }

    void removeOnline(String name) {
        if (name == null || online.remove(name) == null) return;
        boolean roomsChanged = false;
        for (Room r : new ArrayList<>(rooms.values())) {
            if (leaveRoomInternal(r, name, name + " đã rời phòng")) roomsChanged = true;
        }
        broadcast(Message.simple(Message.Type.SYSTEM, name + " đã rời khỏi server"));
        broadcastUserList();
        if (roomsChanged) broadcastRoomList();
        log(name + " đã ngắt kết nối (" + online.size() + " đang online)");
    }

    /** Tên online theo đúng hoa/thường, hoặc null. */
    private String onlineName(String name) {
        if (name == null) return null;
        for (String u : online.keySet()) if (u.equalsIgnoreCase(name.trim())) return u;
        return null;
    }

    // ================== Hồ sơ ==================

    /** Gói PROFILE mô tả một người dùng. */
    Message profileOf(String user) {
        Message m = new Message(Message.Type.PROFILE);
        m.from = user;
        m.text = profiles.displayName(user);
        m.data = profiles.avatar(user);
        return m;
    }

    private Message roomProfile(Room r) {
        Message m = new Message(Message.Type.PROFILE);
        m.scope = Message.Scope.ROOM;
        m.to = r.name;
        m.data = r.avatar;
        return m;
    }

    /** Gửi cho người mới đăng nhập toàn bộ hồ sơ đang có (người online + ảnh phòng). */
    void sendAllProfiles(ClientHandler h) {
        for (String u : online.keySet()) {
            if (profiles.displayName(u) != null || profiles.avatar(u) != null) h.send(profileOf(u));
        }
        for (Room r : rooms.values()) if (r.avatar != null) h.send(roomProfile(r));
    }

    /** text = tên hiển thị mới (null = giữ nguyên), data = ảnh mới (null = giữ nguyên, rỗng = xoá). */
    String updateProfile(String user, String displayName, byte[] avatar) {
        if (displayName != null) {
            String err = profiles.setDisplayName(user, displayName);
            if (err != null) return err;
        }
        if (avatar != null) {
            String err = profiles.setAvatar(user, avatar);
            if (err != null) return err;
        }
        broadcast(profileOf(user));
        log(user + " cập nhật hồ sơ");
        return null;
    }

    String updateRoomAvatar(String roomName, String user, byte[] avatar) {
        Room r = rooms.get(roomName);
        if (r == null) return "Phòng \"" + roomName + "\" không tồn tại";
        if (!r.members.contains(user)) return "Bạn không ở trong phòng này";
        String err = ProfileStore.validateImage(avatar);
        if (err != null) return err;
        r.avatar = avatar.length == 0 ? null : avatar;
        broadcast(roomProfile(r));
        sendToRoom(r, systemIn(r, display(user) + " đã đổi ảnh nhóm"));
        return null;
    }

    private String display(String user) {
        String d = profiles.displayName(user);
        return d != null ? d : user;
    }

    // ================== Chuyển tiếp tin nhắn ==================

    private final java.util.concurrent.atomic.AtomicLong messageIds = new java.util.concurrent.atomic.AtomicLong();

    /** Mã duy nhất cho mỗi tin CHAT (để thả cảm xúc). */
    long nextMessageId() { return messageIds.incrementAndGet(); }

    /** Gửi cho tất cả client đang online. */
    void broadcast(Message m) {
        for (ClientHandler h : online.values()) h.send(m);
    }

    /** Gửi cho một client theo tên. Trả về false nếu người đó không online. */
    boolean sendTo(String name, Message m) {
        ClientHandler h = online.get(name);
        if (h == null) return false;
        h.send(m);
        return true;
    }

    void broadcastUserList() {
        ArrayList<String> names = new ArrayList<>(online.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        broadcast(Message.list(Message.Type.USER_LIST, names));
    }

    // ================== Phòng chat ==================

    private static String validRoomName(String room) {
        String t = room == null ? "" : room.trim();
        int len = t.codePointCount(0, t.length());
        if (len < 2 || len > 30 || !t.matches("[^\\p{Cntrl},]+"))
            return "Tên nhóm dài 2-30 ký tự và không chứa dấu phẩy";
        return null;
    }

    private Room findRoom(String name) {
        for (Room r : rooms.values()) if (r.name.equalsIgnoreCase(name)) return r;
        return null;
    }

    /**
     * Tạo phòng. password rỗng/null = nhóm cộng đồng; invited = những người được thêm ngay khi tạo.
     * @return null nếu thành công, ngược lại là thông báo lỗi.
     */
    synchronized String createRoom(String room, String password, List<String> invited, String creator) {
        String err = validRoomName(room);
        if (err != null) return err;
        room = room.trim().replaceAll("\\s+", " ");
        if (room.equalsIgnoreCase(Message.PUBLIC_ROOM)) return "Tên phòng bị trùng với phòng chung";
        Room exist = findRoom(room);
        if (exist != null) return "Phòng \"" + exist.name + "\" đã tồn tại";
        String pass = password == null ? "" : password;
        if (!pass.isEmpty() && pass.length() < 3) return "Mật khẩu nhóm phải có ít nhất 3 ký tự";

        Room r = new Room(room, creator);
        if (!pass.isEmpty()) r.passHash = hash(r.salt, pass);
        r.members.add(creator);
        rooms.put(room, r);
        sendTo(creator, Message.simple(Message.Type.ROOM_JOIN, room));
        log(creator + " tạo " + (r.locked() ? "nhóm riêng tư" : "nhóm cộng đồng") + " [" + room + "]");
        addMembers(r, invited, creator);
        broadcastRoomList();
        return null;
    }

    synchronized String joinRoom(String room, String password, String user) {
        Room r = rooms.get(room);
        if (r == null) return "Phòng \"" + room + "\" không tồn tại";
        if (r.members.contains(user)) return null;
        if (r.locked() && (password == null || !MessageDigest.isEqual(
                r.passHash.getBytes(StandardCharsets.UTF_8),
                hash(r.salt, password).getBytes(StandardCharsets.UTF_8))))
            return "Sai mật khẩu nhóm \"" + r.name + "\"";
        r.members.add(user);
        sendTo(user, Message.simple(Message.Type.ROOM_JOIN, r.name));
        sendToRoom(r, systemIn(r, display(user) + " đã vào nhóm"));
        broadcastRoomList();
        log(user + " vào phòng [" + r.name + "]");
        return null;
    }

    /** Thành viên trong phòng thêm người khác (giống Zalo: không cần mật khẩu). */
    synchronized String invite(String room, List<String> users, String by) {
        Room r = rooms.get(room);
        if (r == null) return "Phòng \"" + room + "\" không tồn tại";
        if (!r.members.contains(by)) return "Bạn không ở trong phòng này";
        if (addMembers(r, users, by) == 0) return "Không có ai mới để thêm (chỉ thêm được người đang online)";
        broadcastRoomList();
        return null;
    }

    private int addMembers(Room r, List<String> users, String by) {
        if (users == null) return 0;
        int n = 0;
        for (String u0 : users) {
            String u = onlineName(u0);
            if (u == null || !r.members.add(u)) continue;
            n++;
            sendTo(u, Message.simple(Message.Type.ROOM_JOIN, r.name));
            sendToRoom(r, systemIn(r, display(by) + " đã thêm " + display(u) + " vào nhóm"));
            log(by + " thêm " + u + " vào phòng [" + r.name + "]");
        }
        return n;
    }

    synchronized String kick(String room, String target, String by) {
        Room r = rooms.get(room);
        if (r == null) return "Phòng \"" + room + "\" không tồn tại";
        if (!by.equals(r.owner)) return "Chỉ trưởng nhóm mới được mời thành viên ra";
        if (target == null || target.equals(by)) return "Không thể mời chính mình ra";
        if (!r.members.contains(target)) return target + " không ở trong nhóm";
        leaveRoomInternal(r, target, display(by) + " đã mời " + display(target) + " ra khỏi nhóm");
        sendTo(target, Message.simple(Message.Type.ROOM_LEAVE, r.name));
        sendTo(target, Message.simple(Message.Type.ERROR, "Bạn đã bị mời ra khỏi nhóm " + r.name));
        broadcastRoomList();
        return null;
    }

    synchronized String rename(String room, String newName, String by) {
        Room r = rooms.get(room);
        if (r == null) return "Phòng \"" + room + "\" không tồn tại";
        if (!by.equals(r.owner)) return "Chỉ trưởng nhóm mới được đổi tên nhóm";
        String err = validRoomName(newName);
        if (err != null) return err;
        newName = newName.trim().replaceAll("\\s+", " ");
        if (newName.equals(r.name)) return null;
        if (newName.equalsIgnoreCase(Message.PUBLIC_ROOM)) return "Tên phòng bị trùng với phòng chung";
        Room exist = findRoom(newName);
        if (exist != null && exist != r) return "Phòng \"" + exist.name + "\" đã tồn tại";

        String old = r.name;
        rooms.remove(old);
        r.name = newName;
        rooms.put(newName, r);
        Message m = Message.simple(Message.Type.ROOM_RENAME, newName);
        m.to = old;
        broadcast(m);
        sendToRoom(r, systemIn(r, display(by) + " đã đổi tên nhóm thành \"" + newName + "\""));
        broadcastRoomList();
        log(by + " đổi tên phòng [" + old + "] -> [" + newName + "]");
        return null;
    }

    void leaveRoom(String room, String user) {
        Room r = rooms.get(room);
        if (r != null && leaveRoomInternal(r, user, display(user) + " đã rời nhóm")) {
            sendTo(user, Message.simple(Message.Type.ROOM_LEAVE, room));
            broadcastRoomList();
            log(user + " rời phòng [" + room + "]");
        }
    }

    /** Xoá user khỏi phòng; phòng trống sẽ bị huỷ; trưởng nhóm rời thì chuyển quyền. */
    private synchronized boolean leaveRoomInternal(Room r, String user, String notice) {
        if (!r.members.remove(user)) return false;
        if (r.members.isEmpty()) {
            rooms.remove(r.name);
            return true;
        }
        sendToRoom(r, systemIn(r, notice));
        if (user.equals(r.owner)) {
            synchronized (r.members) { r.owner = r.members.iterator().next(); }
            sendToRoom(r, systemIn(r, display(r.owner) + " trở thành trưởng nhóm"));
        }
        return true;
    }

    boolean isMember(String room, String user) {
        Room r = rooms.get(room);
        return r != null && r.members.contains(user);
    }

    void sendToRoom(String room, Message m) {
        Room r = rooms.get(room);
        if (r != null) sendToRoom(r, m);
    }

    private void sendToRoom(Room r, Message m) {
        List<String> copy;
        synchronized (r.members) { copy = new ArrayList<>(r.members); }
        for (String u : copy) sendTo(u, m);
    }

    /**
     * Mỗi người nhận một danh sách riêng: danh sách thành viên chỉ gửi cho người ở trong phòng.
     * Mục: tên \t khoá \t số người \t trưởng nhóm \t thành viên,...
     */
    void broadcastRoomList() {
        Map<String, Room> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(rooms);
        for (Map.Entry<String, ClientHandler> e : online.entrySet()) {
            String viewer = e.getKey();
            ArrayList<String> items = new ArrayList<>();
            for (Room r : sorted.values()) {
                List<String> mem;
                synchronized (r.members) { mem = new ArrayList<>(r.members); }
                boolean inside = mem.contains(viewer);
                items.add(r.name + Message.SEP + (r.locked() ? 1 : 0) + Message.SEP + mem.size()
                        + Message.SEP + r.owner + Message.SEP + (inside ? String.join(",", mem) : ""));
            }
            e.getValue().send(Message.list(Message.Type.ROOM_LIST, items));
        }
    }

    /** Thông báo hệ thống hiển thị bên trong một phòng. */
    private static Message systemIn(Room r, String text) {
        Message m = Message.simple(Message.Type.SYSTEM, text);
        m.scope = Message.Scope.ROOM;
        m.to = r.name;
        return m;
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================== Tiện ích ==================

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    static void log(String s) {
        System.out.println("[" + LocalTime.now().format(TF) + "] " + s);
    }
}
