package chat.client;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ nhớ đệm hồ sơ phía client: tên hiển thị + ảnh đại diện của người dùng, ảnh của các nhóm.
 * Theme.paintAvatar / paintConvAvatar tra cứu ở đây nên mọi nơi vẽ avatar đều tự cập nhật.
 */
public final class Profiles {
    private Profiles() { }

    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> AVATARS = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> ROOM_AVATARS = new ConcurrentHashMap<>();

    private static String key(String s) { return s == null ? "" : s.toLowerCase(); }

    /** Tên hiển thị (nếu đã đặt), ngược lại là tên đăng nhập. */
    public static String display(String user) {
        if (user == null) return "";
        String n = NAMES.get(key(user));
        return n != null ? n : user;
    }

    public static boolean hasDisplayName(String user) { return NAMES.containsKey(key(user)); }

    public static BufferedImage avatar(String user) { return AVATARS.get(key(user)); }

    public static BufferedImage roomAvatar(String room) { return ROOM_AVATARS.get(key(room)); }

    public static void setUser(String user, String displayName, byte[] avatar) {
        if (displayName == null || displayName.isBlank()) NAMES.remove(key(user));
        else NAMES.put(key(user), displayName);
        BufferedImage img = decode(avatar);
        if (img == null) AVATARS.remove(key(user));
        else AVATARS.put(key(user), img);
    }

    public static void setRoom(String room, byte[] avatar) {
        BufferedImage img = decode(avatar);
        if (img == null) ROOM_AVATARS.remove(key(room));
        else ROOM_AVATARS.put(key(room), img);
    }

    public static void renameRoom(String oldName, String newName) {
        BufferedImage img = ROOM_AVATARS.remove(key(oldName));
        if (img != null) ROOM_AVATARS.put(key(newName), img);
    }

    private static BufferedImage decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Đọc ảnh từ tệp, cắt vuông ở giữa và thu về 192x192 PNG để gửi lên server. */
    public static byte[] avatarBytes(File f) throws IOException {
        BufferedImage src = ImageIO.read(f);
        if (src == null) throw new IOException("Tệp không phải ảnh hợp lệ");
        int s = Math.min(src.getWidth(), src.getHeight());
        BufferedImage sq = src.getSubimage((src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
        int target = Math.min(192, s);
        BufferedImage cur = sq;
        while (cur.getWidth() / 2 >= target) {
            BufferedImage n = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = n.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, n.getWidth(), n.getHeight(), null);
            g.dispose();
            cur = n;
        }
        BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(cur, 0, 0, target, target, null);
        g.dispose();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bo);
        return bo.toByteArray();
    }
}
