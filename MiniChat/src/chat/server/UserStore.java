package chat.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lưu danh sách tài khoản vào một tệp văn bản (mặc định users.txt).
 * Mỗi dòng: tênđăngnhập;muối;băm_SHA-256(muối + mật khẩu)
 * Mật khẩu không bao giờ được lưu dạng rõ.
 */
public class UserStore {

    /** Tên hợp lệ: 3-20 ký tự gồm chữ (có dấu), số, dấu gạch dưới. */
    private static final String NAME_PATTERN = "[\\p{L}\\p{N}_]{3,20}";

    private final Path file;
    /** key = tên viết thường (để so sánh trùng tên không phân biệt hoa/thường). */
    private final Map<String, String[]> users = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public UserStore(Path file) throws IOException {
        this.file = file;
        load();
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            Files.createFile(file);
            return;
        }
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split(";");
                if (p.length == 3) users.put(p[0].toLowerCase(), p);
            }
        }
    }

    public synchronized int size() { return users.size(); }

    /** Kiểm tra tên đã tồn tại chưa (so sánh không phân biệt hoa thường). */
    public synchronized boolean exists(String name) {
        return users.containsKey(name.toLowerCase());
    }

    /**
     * Đăng ký tài khoản mới.
     * @return null nếu thành công, ngược lại là thông báo lỗi.
     */
    public synchronized String register(String name, String password) {
        if (name == null || !name.matches(NAME_PATTERN))
            return "Tên phải dài 3-20 ký tự, chỉ gồm chữ, số hoặc dấu _";
        if (password == null || password.length() < 3)
            return "Mật khẩu phải có ít nhất 3 ký tự";
        if (exists(name))
            return "Tên \"" + name + "\" đã có người sử dụng, hãy chọn tên khác";

        byte[] saltBytes = new byte[8];
        random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        String[] record = { name, salt, hash(salt, password) };

        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(String.join(";", record));
            bw.newLine();
        } catch (IOException e) {
            return "Lỗi ghi tệp người dùng: " + e.getMessage();
        }
        users.put(name.toLowerCase(), record);
        return null;
    }

    /**
     * Kiểm tra đăng nhập.
     * @return tên chuẩn (đúng hoa/thường như lúc đăng ký) nếu hợp lệ, null nếu sai.
     */
    public synchronized String authenticate(String name, String password) {
        if (name == null || password == null) return null;
        String[] r = users.get(name.toLowerCase());
        if (r == null) return null;
        return MessageDigest.isEqual(r[2].getBytes(), hash(r[1], password).getBytes()) ? r[0] : null;
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
