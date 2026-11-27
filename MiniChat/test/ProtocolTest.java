import chat.common.Message;
import chat.common.Message.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class ProtocolTest {
    static int pass = 0, fail = 0;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (ok) pass++; else fail++;
    }

    static class C {
        Socket s; ObjectOutputStream out; ObjectInputStream in;
        BlockingQueue<Message> q = new LinkedBlockingQueue<>();
        C() throws IOException {
            s = new Socket("localhost", 5055);
            out = new ObjectOutputStream(s.getOutputStream()); out.flush();
            in = new ObjectInputStream(s.getInputStream());
            Thread t = new Thread(() -> { try { while (true) q.add((Message) in.readObject()); } catch (Exception e) {} });
            t.setDaemon(true); t.start();
        }
        void send(Message m) throws IOException { out.writeObject(m); out.reset(); out.flush(); }
        Message auth(Type t, String u, String p) throws Exception {
            send(Message.auth(t, u, p));
            return waitFor(m -> m.type == Type.AUTH_OK || m.type == Type.AUTH_FAIL);
        }
        Message waitFor(java.util.function.Predicate<Message> p) throws InterruptedException {
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end) {
                Message m = q.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && p.test(m)) return m;
            }
            return null;
        }
        boolean gets(java.util.function.Predicate<Message> p) throws InterruptedException { return waitFor(p) != null; }
    }

    public static void main(String[] a) throws Exception {
        C alice = new C();
        check("Đăng ký alice", alice.auth(Type.REGISTER, "alice", "123").type == Type.AUTH_OK);
        C x = new C();
        Message dup = x.auth(Type.REGISTER, "ALICE", "999");
        check("Đăng ký trùng tên (ALICE) bị từ chối: " + dup.text, dup.type == Type.AUTH_FAIL);
        Message bad = x.auth(Type.REGISTER, "a!", "999");
        check("Tên không hợp lệ bị từ chối: " + bad.text, bad.type == Type.AUTH_FAIL);
        C bob = new C();
        check("Đăng ký bob", bob.auth(Type.REGISTER, "bob", "456").type == Type.AUTH_OK);
        Message wrong = x.auth(Type.LOGIN, "bob", "000");
        check("Sai mật khẩu bị từ chối: " + wrong.text, wrong.type == Type.AUTH_FAIL);
        Message twice = x.auth(Type.LOGIN, "bob", "456");
        check("Đăng nhập trùng phiên bị từ chối: " + twice.text, twice.type == Type.AUTH_FAIL);
        C carol = x;
        check("Đăng ký carol", carol.auth(Type.REGISTER, "carol", "789").type == Type.AUTH_OK);

        Message ul = alice.waitFor(m -> m.type == Type.USER_LIST && m.list.size() == 3);
        check("Danh sách online có 3 người: " + (ul == null ? null : ul.list), ul != null);

        alice.send(Message.text(Scope.ALL, null, "Xin chào cả nhà"));
        check("Bob nhận tin phòng chung", bob.gets(m -> m.type == Type.CHAT && m.scope == Scope.ALL && "alice".equals(m.from)));
        check("Carol nhận tin phòng chung", carol.gets(m -> m.type == Type.CHAT && m.scope == Scope.ALL));

        alice.send(Message.text(Scope.PRIVATE, "bob", "bí mật"));
        check("Bob nhận tin riêng", bob.gets(m -> m.scope == Scope.PRIVATE && "bí mật".equals(m.text)));
        check("Alice nhận bản sao tin riêng", alice.gets(m -> m.scope == Scope.PRIVATE && "bob".equals(m.to)));
        check("Carol KHÔNG nhận tin riêng", !carol.gets(m -> m.scope == Scope.PRIVATE));

        Message spoof = Message.text(Scope.ALL, null, "giả mạo"); spoof.from = "bob";
        carol.send(spoof);
        Message got = alice.waitFor(m -> "giả mạo".equals(m.text));
        check("Server ghi đè trường from (chống giả mạo): from=" + got.from, "carol".equals(got.from));

        alice.send(Message.simple(Type.ROOM_CREATE, "Nhóm 1"));
        check("Alice tạo phòng và được vào", alice.gets(m -> m.type == Type.ROOM_JOIN && "Nhóm 1".equals(m.text)));
        Message rl = carol.waitFor(m -> m.type == Type.ROOM_LIST && !m.list.isEmpty());
        check("Mọi người thấy danh sách phòng: " + (rl == null ? null : rl.list), rl != null);
        bob.send(Message.simple(Type.ROOM_CREATE, "nhóm 1"));
        check("Tạo phòng trùng tên bị báo lỗi", bob.gets(m -> m.type == Type.ERROR));
        bob.send(Message.simple(Type.ROOM_JOIN, "Nhóm 1"));
        check("Bob vào phòng", bob.gets(m -> m.type == Type.ROOM_JOIN));
        alice.send(Message.text(Scope.ROOM, "Nhóm 1", "họp nhóm"));
        check("Bob nhận tin trong phòng", bob.gets(m -> m.scope == Scope.ROOM && "họp nhóm".equals(m.text)));
        check("Carol KHÔNG nhận tin phòng", !carol.gets(m -> m.type == Type.CHAT && m.scope == Scope.ROOM));
        carol.send(Message.text(Scope.ROOM, "Nhóm 1", "chen vào"));
        check("Người ngoài gửi vào phòng bị báo lỗi", carol.gets(m -> m.type == Type.ERROR));

        // ---- Nhóm riêng tư (mật khẩu), mời thành viên, đổi tên, mời ra ----
        Message priv = Message.simple(Type.ROOM_CREATE, "Bí mật");
        priv.password = "abc";
        carol.send(priv);
        check("Carol tạo nhóm riêng tư", carol.gets(m -> m.type == Type.ROOM_JOIN && "Bí mật".equals(m.text)));
        Message rlp = bob.waitFor(m -> m.type == Type.ROOM_LIST && m.list.stream().anyMatch(s -> s.startsWith("Bí mật\t")));
        check("Người ngoài thấy nhóm bị khoá nhưng không thấy thành viên",
                rlp != null && rlp.list.stream().anyMatch(s -> s.equals("Bí mật\t1\t1\tcarol\t")));
        bob.send(Message.simple(Type.ROOM_JOIN, "Bí mật"));
        check("Vào nhóm riêng tư không có mật khẩu bị từ chối", bob.gets(m -> m.type == Type.ERROR && m.text.contains("mật khẩu")));
        Message jw = Message.simple(Type.ROOM_JOIN, "Bí mật"); jw.password = "sai";
        bob.send(jw);
        check("Sai mật khẩu nhóm bị từ chối", bob.gets(m -> m.type == Type.ERROR && m.text.contains("mật khẩu")));
        Message jr = Message.simple(Type.ROOM_JOIN, "Bí mật"); jr.password = "abc";
        bob.send(jr);
        check("Đúng mật khẩu thì vào được", bob.gets(m -> m.type == Type.ROOM_JOIN && "Bí mật".equals(m.text)));
        Message inv = new Message(Type.ROOM_INVITE); inv.to = "Bí mật"; inv.list = new ArrayList<>(List.of("ALICE"));
        carol.send(inv);
        check("Thành viên thêm alice không cần mật khẩu", alice.gets(m -> m.type == Type.ROOM_JOIN && "Bí mật".equals(m.text)));
        Message rnBad = Message.simple(Type.ROOM_RENAME, "Của bob"); rnBad.to = "Bí mật";
        bob.send(rnBad);
        check("Không phải trưởng nhóm thì không đổi tên được", bob.gets(m -> m.type == Type.ERROR && m.text.contains("trưởng nhóm")));
        Message rn = Message.simple(Type.ROOM_RENAME, "Bí mật – v2"); rn.to = "Bí mật";
        carol.send(rn);
        check("Trưởng nhóm đổi tên, mọi người nhận ROOM_RENAME",
                alice.gets(m -> m.type == Type.ROOM_RENAME && "Bí mật".equals(m.to) && "Bí mật – v2".equals(m.text)));
        Message kick = Message.simple(Type.ROOM_KICK, "bob"); kick.to = "Bí mật – v2";
        carol.send(kick);
        check("Trưởng nhóm mời bob ra", bob.gets(m -> m.type == Type.ROOM_LEAVE && "Bí mật – v2".equals(m.text)));
        Message jr2 = Message.simple(Type.ROOM_JOIN, "Bí mật – v2"); jr2.password = "abc";
        bob.send(jr2);
        check("Mật khẩu vẫn đúng sau khi đổi tên", bob.gets(m -> m.type == Type.ROOM_JOIN && "Bí mật – v2".equals(m.text)));

        // ---- Hồ sơ: tên hiển thị ----
        Message prof = new Message(Type.PROFILE_UPDATE); prof.text = "Alice Nguyễn ✿";
        alice.send(prof);
        check("Mọi người nhận hồ sơ mới của alice",
                bob.gets(m -> m.type == Type.PROFILE && "alice".equals(m.from) && "Alice Nguyễn ✿".equals(m.text)));

        // ---- Sticker & thả cảm xúc ----
        Message st = Message.text(Scope.ALL, null, "e:🥳"); st.content = Content.STICKER;
        alice.send(st);
        Message gotSt = bob.waitFor(m -> m.type == Type.CHAT && m.content == Content.STICKER);
        check("Bob nhận sticker, có mã tin nhắn: id=" + (gotSt == null ? null : gotSt.id),
                gotSt != null && "e:🥳".equals(gotSt.text) && gotSt.id > 0);
        long sid = gotSt == null ? 0 : gotSt.id;
        Message re = new Message(Type.REACT); re.scope = Scope.ALL; re.id = sid; re.text = "❤️";
        bob.send(re);
        check("Alice nhận cảm xúc ❤️ của bob",
                alice.gets(m -> m.type == Type.REACT && m.id == sid && "bob".equals(m.from) && "❤️".equals(m.text)));
        Message un = new Message(Type.REACT); un.scope = Scope.ALL; un.id = sid; un.text = null;
        bob.send(un);
        check("Gỡ cảm xúc được chuyển tới mọi người",
                carol.gets(m -> m.type == Type.REACT && m.id == sid && m.text == null));
        Message rp = new Message(Type.REACT); rp.scope = Scope.ROOM; rp.to = "Nhóm 1"; rp.id = sid; rp.text = "👍";
        carol.send(rp);
        check("Người ngoài phòng không thả cảm xúc vào phòng được", carol.gets(m -> m.type == Type.ERROR));

        byte[] img = new byte[2_000_000]; new Random(1).nextBytes(img);
        alice.send(Message.file(Scope.PRIVATE, "bob", Content.FILE, "data.bin", img));
        Message f = bob.waitFor(m -> m.content == Content.FILE);
        check("Bob nhận tệp 2 MB nguyên vẹn", f != null && Arrays.equals(f.data, img));
        int okCount = 0;
        for (int i = 0; i < 6; i++) {
            alice.send(Message.file(Scope.PRIVATE, "bob", Content.IMAGE, "anh" + i + ".png", img));
            if (bob.gets(m -> m.content == Content.IMAGE)) okCount++;
        }
        check("Gửi liên tiếp 6 tệp (tổng 12 MB) trên cùng kết nối: " + okCount + "/6", okCount == 6);
        byte[] big =new byte[Message.MAX_FILE_SIZE + 1];
        alice.send(Message.file(Scope.ALL, null, Content.FILE, "big.bin", big));
        check("Tệp > 10 MB bị từ chối", alice.gets(m -> m.type == Type.ERROR && m.text.contains("10 MB")));

        bob.send(new Message(Type.LOGOUT));
        check("Alice thấy bob rời server", alice.gets(m -> m.type == Type.SYSTEM && m.text.startsWith("bob")));
        check("Phòng còn 1 thành viên", alice.gets(m -> m.type == Type.ROOM_LIST && m.list.stream().anyMatch(s -> s.startsWith("Nhóm 1\t0\t1\t"))));

        C bob2 = new C();
        check("Bob đăng nhập lại (tên viết hoa BOB)", "bob".equals(bob2.auth(Type.LOGIN, "BOB", "456").type == Type.AUTH_OK ? "bob" : ""));

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
