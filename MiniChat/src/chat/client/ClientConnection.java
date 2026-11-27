package chat.client;

import chat.common.Message;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Lớp kết nối mạng phía client: mở Socket tới server, gửi gói tin,
 * và chạy một luồng nền để nhận gói tin từ server.
 */
public class ClientConnection {

    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxarray=" + (Message.MAX_FILE_SIZE + 1)
            + ";chat.common.*;java.lang.*;java.util.ArrayList;!*");

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private String username;

    /** Mở kết nối TCP tới server. */
    public ClientConnection(String host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        in.setObjectInputFilter(FILTER);
    }

    /**
     * Gửi REGISTER hoặc LOGIN rồi chờ phản hồi (đồng bộ).
     * @return null nếu thành công, ngược lại là lý do thất bại.
     */
    public String authenticate(Message.Type type, String user, String password) throws IOException {
        send(Message.auth(type, user, password));
        socket.setSoTimeout(10_000);
        try {
            while (true) {
                Message m = (Message) in.readObject();
                if (m.type == Message.Type.AUTH_OK) {
                    username = m.to;          // tên chuẩn do server trả về
                    return null;
                }
                if (m.type == Message.Type.AUTH_FAIL) return m.text;
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        } finally {
            socket.setSoTimeout(0);
        }
    }

    public String getUsername() { return username; }

    /** Khởi động luồng nhận tin; mọi callback được chạy trên luồng giao diện Swing (EDT). */
    public void startListening(Consumer<Message> onMessage, Runnable onDisconnect) {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Message m = (Message) in.readObject();
                    SwingUtilities.invokeLater(() -> onMessage.accept(m));
                }
            } catch (IOException | ClassNotFoundException e) {
                SwingUtilities.invokeLater(onDisconnect);
            }
        }, "receiver");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void send(Message m) throws IOException {
        out.writeObject(m);
        out.reset();
        out.flush();
    }

    public void close() {
        try { send(new Message(Message.Type.LOGOUT)); } catch (IOException ignored) { }
        try { socket.close(); } catch (IOException ignored) { }
    }
}
