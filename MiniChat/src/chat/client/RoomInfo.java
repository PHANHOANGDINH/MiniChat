package chat.client;

import chat.common.Message;

import java.util.ArrayList;
import java.util.List;

/** Thông tin một phòng nhận từ ROOM_LIST: tên \t khoá \t số người \t trưởng nhóm \t thành viên,... */
public final class RoomInfo {
    public final String name;
    public final boolean locked;
    public final int count;
    public final String owner;
    /** Chỉ có khi mình là thành viên (server không gửi danh sách cho người ngoài). */
    public final List<String> members;

    private RoomInfo(String name, boolean locked, int count, String owner, List<String> members) {
        this.name = name;
        this.locked = locked;
        this.count = count;
        this.owner = owner;
        this.members = members;
    }

    public static RoomInfo parse(String item) {
        String[] p = item.split(Message.SEP, -1);
        List<String> mem = new ArrayList<>();
        if (p.length > 4 && !p[4].isEmpty()) for (String s : p[4].split(",")) mem.add(s);
        int count = 0;
        try { count = p.length > 2 ? Integer.parseInt(p[2]) : 0; } catch (NumberFormatException ignored) { }
        return new RoomInfo(p[0], p.length > 1 && "1".equals(p[1]), count, p.length > 3 ? p[3] : "", mem);
    }
}
