package chat.server;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hồ sơ người dùng: tên hiển thị và ảnh đại diện.
 * Lưu trong thư mục "profiles" cạnh tệp người dùng:
 *   profiles/&lt;tên viết thường&gt;.txt  (tên hiển thị, UTF-8)
 *   profiles/&lt;tên viết thường&gt;.png  (ảnh đại diện)
 */
public class ProfileStore {

    /** Ảnh đại diện tối đa 512 KB (client đã thu nhỏ trước khi gửi). */
    public static final int MAX_AVATAR = 512 * 1024;

    private final Path dir;
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final Map<String, byte[]> avatars = new ConcurrentHashMap<>();

    public ProfileStore(Path userFile) {
        Path parent = userFile.toAbsolutePath().getParent();
        dir = (parent != null ? parent : Path.of(".")).resolve("profiles");
        try {
            Files.createDirectories(dir);
            try (var files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    String f = p.getFileName().toString();
                    if (f.endsWith(".txt"))
                        names.put(f.substring(0, f.length() - 4), Files.readString(p, StandardCharsets.UTF_8).strip());
                    else if (f.endsWith(".png"))
                        avatars.put(f.substring(0, f.length() - 4), Files.readAllBytes(p));
                }
            }
        } catch (IOException e) {
            ChatServer.log("Không đọc được thư mục hồ sơ: " + e.getMessage());
        }
    }

    public String displayName(String user) { return names.get(key(user)); }

    public byte[] avatar(String user) { return avatars.get(key(user)); }

    /** @return null nếu thành công, ngược lại là thông báo lỗi. */
    public String setDisplayName(String user, String name) {
        String n = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (n.codePointCount(0, n.length()) > 30) return "Tên hiển thị tối đa 30 ký tự";
        if (!n.isEmpty() && !n.matches("[^\\p{Cntrl}]+"))
            return "Tên hiển thị không được chứa ký tự điều khiển";
        Path p = dir.resolve(key(user) + ".txt");
        try {
            if (n.isEmpty()) { names.remove(key(user)); Files.deleteIfExists(p); }
            else { names.put(key(user), n); Files.writeString(p, n, StandardCharsets.UTF_8); }
        } catch (IOException e) {
            return "Lỗi lưu hồ sơ: " + e.getMessage();
        }
        return null;
    }

    /** data rỗng = xoá ảnh. @return null nếu thành công, ngược lại là thông báo lỗi. */
    public String setAvatar(String user, byte[] data) {
        String err = validateImage(data);
        if (err != null) return err;
        Path p = dir.resolve(key(user) + ".png");
        try {
            if (data.length == 0) { avatars.remove(key(user)); Files.deleteIfExists(p); }
            else { avatars.put(key(user), data); Files.write(p, data); }
        } catch (IOException e) {
            return "Lỗi lưu ảnh: " + e.getMessage();
        }
        return null;
    }

    /** Kiểm tra dữ liệu ảnh hợp lệ (mảng rỗng được coi là "xoá ảnh"). */
    static String validateImage(byte[] data) {
        if (data == null) return "Thiếu dữ liệu ảnh";
        if (data.length == 0) return null;
        if (data.length > MAX_AVATAR) return "Ảnh đại diện tối đa 512 KB";
        try {
            if (ImageIO.read(new ByteArrayInputStream(data)) == null) return "Tệp không phải ảnh hợp lệ";
        } catch (IOException | RuntimeException e) {
            return "Tệp không phải ảnh hợp lệ";
        }
        return null;
    }

    private static String key(String user) { return user.toLowerCase(); }
}
