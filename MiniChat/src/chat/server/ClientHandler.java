package chat.server;

import chat.common.Message;
import chat.common.Message.Scope;
import chat.common.Message.Type;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * Luồng phục vụ một client. Vòng đời:
 *   1. Chờ gói REGISTER hoặc LOGIN cho tới khi xác thực thành công.
 *   2. Lặp: đọc gói tin -> xử lý -> chuyển tiếp cho client khác.
 *   3. Khi client ngắt kết nối: xoá khỏi danh sách online, báo cho mọi người.
 */
public class ClientHandler extends Thread {

    /**
     * Bộ lọc giải tuần tự hoá: chỉ chấp nhận các lớp của ứng dụng và vài lớp chuẩn,
     * giới hạn kích thước mảng và độ sâu đối tượng để tránh client gửi đối tượng độc hại / quá lớn.
     * (Không dùng maxbytes vì giới hạn đó tính dồn trên cả luồng, sẽ ngắt kết nối sau vài tệp.)
     */
    static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxarray=" + (Message.MAX_FILE_SIZE + 1)
            + ";maxdepth=10"
            + ";chat.common.*;java.lang.*;java.util.ArrayList;java.lang.Enum;!*");

    private final Socket socket;
    private final ChatServer server;
    private ObjectOutputStream out;
    private String name;   // tên user sau khi đăng nhập

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(FILTER);

            // ---- Giai đoạn 1: xác thực ----
            while (name == null) {
                Message m = (Message) in.readObject();
                handleAuth(m);
            }

            // ---- Giai đoạn 2: nhận và xử lý tin nhắn ----
            while (true) {
                Message m = (Message) in.readObject();
                m.from = name;                       // không tin "from" do client gửi lên
                m.time = System.currentTimeMillis(); // dùng giờ của server
                if (m.type == Type.LOGOUT) break;
                handle(m);
            }
        } catch (EOFException | SocketException e) {
            // client đóng kết nối
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            ChatServer.log("Lỗi với client " + (name != null ? name : socket.getRemoteSocketAddress())
                    + ": " + e);
        } finally {
            server.removeOnline(name);
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private void handleAuth(Message m) {
        String user = m.from == null ? "" : m.from.trim();
        if (m.type == Type.REGISTER) {
            String err = server.users().register(user, m.text);
            if (err != null) {
                send(Message.simple(Type.AUTH_FAIL, err));
                ChatServer.log("Đăng ký thất bại [" + user + "]: " + err);
                return;
            }
            ChatServer.log("Đăng ký tài khoản mới: " + user);
            loginOk(user);
        } else if (m.type == Type.LOGIN) {
            String realName = server.users().authenticate(user, m.text);
            if (realName == null) {
                send(Message.simple(Type.AUTH_FAIL, "Sai tên đăng nhập hoặc mật khẩu"));
                ChatServer.log("Đăng nhập thất bại: " + user);
                return;
            }
            loginOk(realName);
        } else {
            send(Message.simple(Type.AUTH_FAIL, "Bạn cần đăng nhập trước"));
        }
    }

    private void loginOk(String user) {
        if (!server.addOnline(user, this)) {
            send(Message.simple(Type.AUTH_FAIL, "Tài khoản \"" + user + "\" đang đăng nhập ở nơi khác"));
            return;
        }
        name = user;
        Message ok = Message.simple(Type.AUTH_OK, "Chào mừng " + user + "!");
        ok.to = user;
        send(ok);
        server.broadcast(Message.simple(Type.SYSTEM, user + " đã tham gia server"));
        server.broadcastUserList();
        server.broadcastRoomList();
        server.sendAllProfiles(this);
        Message mine = server.profileOf(user);
        if (mine.text != null || mine.data != null) server.broadcast(mine);
        ChatServer.log(user + " đăng nhập thành công");
    }

    private void handle(Message m) {
        switch (m.type) {
            case CHAT -> handleChat(m);
            case REACT -> handleReact(m);
            case ROOM_CREATE -> reply(server.createRoom(m.text, m.password, m.list, name));
            case ROOM_JOIN -> reply(server.joinRoom(m.text, m.password, name));
            case ROOM_LEAVE -> server.leaveRoom(m.text, name);
            case ROOM_INVITE -> reply(server.invite(m.to, m.list, name));
            case ROOM_KICK -> reply(server.kick(m.to, m.text, name));
            case ROOM_RENAME -> reply(server.rename(m.to, m.text, name));
            case PROFILE_UPDATE -> {
                if (m.data != null && m.data.length > ProfileStore.MAX_AVATAR) {
                    reply("Ảnh đại diện tối đa 512 KB");
                } else if (m.scope == Scope.ROOM) {
                    reply(server.updateRoomAvatar(m.to, name, m.data));
                } else {
                    reply(server.updateProfile(name, m.text, m.data));
                }
            }
            default -> send(Message.simple(Type.ERROR, "Yêu cầu không hợp lệ: " + m.type));
        }
    }

    /** Gửi lỗi về client nếu thao tác thất bại. */
    private void reply(String err) {
        if (err != null) send(Message.simple(Type.ERROR, err));
    }

    private void handleChat(Message m) {
        if (m.scope == null) return;
        if (m.content == Message.Content.STICKER) {
            if (m.text == null || m.text.isEmpty() || m.text.length() > 64) return;
            m.data = null;
        } else if (m.content != Message.Content.TEXT
                && (m.data == null || m.data.length > Message.MAX_FILE_SIZE)) {
            send(Message.simple(Type.ERROR, "Tệp rỗng hoặc vượt quá 10 MB"));
            return;
        }
        m.id = server.nextMessageId();
        ChatServer.log(m.toString());
        deliver(m);
    }

    /** Thả / gỡ cảm xúc: chuyển tới đúng những người thấy tin nhắn đó. */
    private void handleReact(Message m) {
        if (m.scope == null || m.id <= 0) return;
        if (m.text != null && (m.text.isEmpty() || m.text.length() > 16)) return;
        m.data = null;
        deliver(m);
    }

    /** Gửi một gói CHAT / REACT tới người nhận theo phạm vi. */
    private void deliver(Message m) {
        switch (m.scope) {
            case ALL -> server.broadcast(m);
            case PRIVATE -> {
                if (m.to == null || m.to.equals(name)) return;
                if (server.sendTo(m.to, m)) send(m); // gửi cho người nhận + gửi lại bản sao cho người gửi
                else send(Message.simple(Type.ERROR, m.to + " hiện không online"));
            }
            case ROOM -> {
                if (server.isMember(m.to, name)) server.sendToRoom(m.to, m);
                else send(Message.simple(Type.ERROR, "Bạn không ở trong phòng " + m.to));
            }
        }
    }

    /** Gửi gói tin cho client này. synchronized vì nhiều luồng có thể cùng gửi. */
    public synchronized void send(Message m) {
        if (out == null) return;
        try {
            out.writeObject(m);
            out.reset();   // xoá bộ nhớ đệm tham chiếu để tránh rò rỉ bộ nhớ
            out.flush();
        } catch (IOException e) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
