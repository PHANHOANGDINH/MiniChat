# Mini Chat — Java Socket (TCP)

Ứng dụng chat nhiều người dùng gồm 1 server và nhiều client (giao diện Swing), chỉ dùng thư viện chuẩn của JDK (JDK 17 trở lên).

## Tính năng
- Đăng ký / đăng nhập; kiểm tra trùng tên (không phân biệt hoa thường); tài khoản lưu trong `users.txt` (mật khẩu được băm SHA-256 có muối).
- Phòng chung: gửi tin cho tất cả client trên server.
- Chat riêng 1-1: nháy đúp tên người trong danh sách online.
- Phòng chat nhóm: tạo phòng, vào phòng, rời phòng; chỉ thành viên nhận được tin.
  - Nhóm **cộng đồng** (ai cũng vào được) hoặc **riêng tư** (đặt mật khẩu khi tạo; mật khẩu băm SHA-256 có muối).
  - Tạo nhóm kiểu Zalo: chọn thành viên đang online ngay khi tạo; thành viên trong nhóm có thể thêm người khác (không cần mật khẩu).
  - Trưởng nhóm đổi tên nhóm và mời thành viên ra; trưởng nhóm rời thì quyền chuyển cho người kế tiếp. Thành viên đổi được ảnh nhóm.
  - "Khám phá nhóm": tìm nhóm theo tên (không phân biệt dấu), lọc cộng đồng / riêng tư.
- Sticker: 5 gói (Cảm xúc, Chữ, Cử chỉ, Động vật, Đồ ăn) — nút sticker trên thanh soạn tin.
- Thả cảm xúc 👍 ❤️ 😆 😮 😢 😡 vào tin nhắn: rê chuột → nút mặt cười, chuột phải, hoặc nháy đúp để thả ❤️;
  bấm vào ô cảm xúc để thả/gỡ nhanh, rê chuột lên để xem ai đã thả.
- Hồ sơ: đổi tên hiển thị và ảnh đại diện; lưu trong thư mục `profiles/` cạnh `users.txt`.
- Gửi ảnh (hiển thị thu nhỏ), gửi tệp bất kỳ, ghi âm và gửi đoạn ghi âm (≤ 10 MB mỗi lần).
- Mỗi lần chạy `ChatClient` là một client độc lập.

## Cấu trúc
```
src/chat/common/Message.java        Gói tin trao đổi (Serializable)
src/chat/server/ChatServer.java     Server: chấp nhận kết nối, quản lý online & phòng
src/chat/server/ClientHandler.java  Luồng phục vụ từng client
src/chat/server/UserStore.java      Đọc/ghi users.txt, đăng ký, xác thực
src/chat/server/ProfileStore.java   Tên hiển thị + ảnh đại diện (thư mục profiles/)
src/chat/client/Dialogs.java        Hộp thoại tạo nhóm, thêm thành viên, mật khẩu, hồ sơ, khám phá nhóm
src/chat/client/Profiles.java       Bộ nhớ đệm hồ sơ / ảnh nhóm phía client
src/chat/client/RoomInfo.java       Thông tin một phòng trong ROOM_LIST
src/chat/client/Stickers.java       Gói sticker, bảng chọn sticker, thanh thả cảm xúc
src/chat/client/ChatClient.java     Điểm khởi động client + hộp thoại đăng nhập
src/chat/client/ClientConnection.java  Kết nối socket phía client
src/chat/client/ChatFrame.java      Cửa sổ chat chính
src/chat/client/ChatPanel.java      Khung hiển thị một hội thoại
src/chat/client/AudioRecorder.java  Ghi âm / phát âm thanh WAV
```

## Biên dịch và chạy
Windows: nháy đúp `build.bat`, sau đó `run-server.bat` (1 lần) và `run-client.bat` (mỗi lần mở thêm 1 client).

Linux / macOS:
```
./build.sh
./run-server.sh          # cửa sổ 1
./run-client.sh          # cửa sổ 2, 3, ... mỗi lần là 1 client
```

Chạy thủ công:
```
javac -encoding UTF-8 -d out src/chat/common/*.java src/chat/server/*.java src/chat/client/*.java
java -cp out chat.server.ChatServer 5000 users.txt
java -cp out chat.client.ChatClient
```

Trong IntelliJ / NetBeans / Eclipse: mở thư mục `src` làm source root, chạy `chat.server.ChatServer` trước, rồi chạy `chat.client.ChatClient` nhiều lần (bật "Allow multiple instances").
