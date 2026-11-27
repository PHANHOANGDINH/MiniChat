package chat.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Gói tin (packet) trao đổi giữa Client và Server.
 * Được tuần tự hoá (serialize) bằng ObjectOutputStream và gửi qua TCP Socket.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Loại gói tin. */
    public enum Type {
        REGISTER,     // client -> server : đăng ký tài khoản mới
        LOGIN,        // client -> server : đăng nhập
        AUTH_OK,      // server -> client : đăng ký / đăng nhập thành công
        AUTH_FAIL,    // server -> client : thất bại (kèm lý do trong text)
        CHAT,         // hai chiều        : một tin nhắn chat (chữ, ảnh, file, ghi âm)
        USER_LIST,    // server -> client : danh sách người dùng đang online
        ROOM_LIST,    // server -> client : danh sách phòng chat đang tồn tại
        ROOM_CREATE,  // client -> server : tạo phòng mới
        ROOM_JOIN,    // hai chiều        : xin vào phòng / xác nhận đã vào phòng
        ROOM_LEAVE,   // hai chiều        : xin rời phòng / xác nhận đã rời phòng
        SYSTEM,       // server -> client : thông báo hệ thống (ai vào, ai ra...)
        ERROR,        // server -> client : báo lỗi
        LOGOUT,       // client -> server : đăng xuất
        PROFILE_UPDATE, // client -> server : đổi tên hiển thị / avatar (scope=ROOM, to=phòng: đổi ảnh nhóm)
        PROFILE,      // server -> client : hồ sơ của một người (from) hoặc ảnh của một phòng (scope=ROOM, to)
        ROOM_INVITE,  // client -> server : thêm các thành viên trong list vào phòng "to"
        ROOM_KICK,    // client -> server : trưởng nhóm mời "text" ra khỏi phòng "to"
        ROOM_RENAME,  // hai chiều        : đổi tên phòng "to" thành "text"
        REACT         // hai chiều        : thả cảm xúc "text" (null = gỡ) vào tin nhắn có mã "id" (scope/to như CHAT)
    }

    /** Phạm vi của một tin nhắn CHAT. */
    public enum Scope {
        ALL,      // phòng chung - gửi tới mọi client trên server
        PRIVATE,  // chat riêng 1-1, "to" là tên người nhận
        ROOM      // chat trong phòng, "to" là tên phòng
    }

    /** Kiểu nội dung của một tin nhắn CHAT. STICKER: "text" là mã sticker, không có dữ liệu tệp. */
    public enum Content { TEXT, IMAGE, FILE, AUDIO, STICKER }

    /** Giới hạn dung lượng tệp gửi qua mạng (10 MB). */
    public static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** Tên phòng chat chung mặc định. */
    public static final String PUBLIC_ROOM = "Phòng chung";

    public Type type;
    public Scope scope;
    public Content content = Content.TEXT;
    public String from;
    public String to;
    public String text;          // nội dung chữ / mật khẩu / lý do lỗi / tên phòng
    public String fileName;      // tên tệp (với IMAGE, FILE, AUDIO)
    public byte[] data;          // dữ liệu nhị phân của tệp
    public ArrayList<String> list; // danh sách user hoặc phòng
    public String password;      // mật khẩu phòng riêng tư (ROOM_CREATE / ROOM_JOIN)
    public long id;              // mã tin nhắn do server cấp (dùng để thả cảm xúc)
    public long time = System.currentTimeMillis();

    /** Phân cách các trường trong một mục ROOM_LIST: tên \t khoá(0/1) \t số người \t trưởng nhóm \t thành viên(,) */
    public static final String SEP = "\t";

    public Message() { }

    public Message(Type type) { this.type = type; }

    // ---------- Các hàm tạo nhanh ----------

    public static Message auth(Type type, String user, String password) {
        Message m = new Message(type);
        m.from = user;
        m.text = password;
        return m;
    }

    public static Message text(Scope scope, String to, String text) {
        Message m = new Message(Type.CHAT);
        m.scope = scope;
        m.to = to;
        m.content = Content.TEXT;
        m.text = text;
        return m;
    }

    public static Message file(Scope scope, String to, Content content, String fileName, byte[] data) {
        Message m = new Message(Type.CHAT);
        m.scope = scope;
        m.to = to;
        m.content = content;
        m.fileName = fileName;
        m.data = data;
        return m;
    }

    public static Message simple(Type type, String text) {
        Message m = new Message(type);
        m.text = text;
        return m;
    }

    public static Message list(Type type, List<String> items) {
        Message m = new Message(type);
        m.list = new ArrayList<>(items);
        return m;
    }

    @Override
    public String toString() {
        return type + (scope != null ? "/" + scope : "") + " from=" + from + " to=" + to
                + (content != Content.TEXT ? " " + content + "[" + fileName + ", "
                + (data == null ? 0 : data.length) + " bytes]" : "")
                + (text != null && type != Type.LOGIN && type != Type.REGISTER ? " text=" + text : "");
    }
}
