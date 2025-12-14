# 🚨 HƯỚNG DẪN SỬA LỖI "MAX CLIENT CONNECTIONS REACHED"

## ✅ Vấn Đề Đã Xác Định

**Lỗi:** `FATAL: Max client connections reached`

**Nguyên Nhân:** 
- Code cũ (trước khi có HikariCP) đã tạo hàng chục connection đến Supabase
- Các connection này CHƯA được đóng và vẫn đang "treo" trên database
- Supabase free tier chỉ cho phép 15-20 connections
- Database đã FULL → Không thể tạo connection mới

**Giải Pháp:** Kill các connection cũ hoặc restart database

---

## 🔥 PHƯƠNG ÁN 1: RESTART DATABASE (NHANH NHẤT - 2 PHÚT)

### Bước 1: Vào Supabase Dashboard

1. Mở trình duyệt, vào: **https://supabase.com**
2. Đăng nhập
3. Chọn project của bạn

### Bước 2: Restart Database

**Cách 1: Pause/Resume (Khuyến nghị)**
1. Vào **Settings** (sidebar bên trái)
2. Chọn **Database**
3. Kéo xuống phần **Connection pooling**
4. Click nút **"Pause database"**
5. Đợi 10 giây
6. Click nút **"Resume database"**
7. Đợi 30-60 giây để database khởi động

**Cách 2: Restart**
1. Vào **Settings** → **Database**
2. Tìm nút **"Restart"** hoặc **"Reboot"**
3. Click và xác nhận
4. Đợi 1-2 phút

### Bước 3: Test Lại

Chạy lại ứng dụng của bạn:
```
(Trong IDE NetBeans/IntelliJ: Run → Run Project)
```

**KẾT QUẢ:** Tất cả connection cũ đã bị đóng! ✅

---

## 🛠️ PHƯƠNG ÁN 2: KILL CONNECTIONS QUA SQL (3 PHÚT)

### Bước 1: Vào SQL Editor

1. Mở Supabase Dashboard
2. Vào **SQL Editor** (sidebar bên trái)
3. Click **"New query"**

### Bước 2: Chạy Query Kill Connections

Copy và paste query này:

```sql
-- Xem tất cả connections hiện tại
SELECT 
    pid,
    usename,
    application_name,
    client_addr,
    state,
    query_start
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY query_start DESC;
```

Click **"Run"** để xem danh sách connections.

### Bước 3: Kill Tất Cả Connections (Trừ Connection Hiện Tại)

```sql
-- Kill TẤT CẢ connections (trừ connection này)
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE pid <> pg_backend_pid()
  AND datname = current_database();
```

Click **"Run"**

**KẾT QUẢ:** Sẽ hiển thị số lượng connections đã kill

### Bước 4: Kiểm Tra Lại

```sql
-- Xem còn bao nhiêu connections
SELECT count(*) as total_connections
FROM pg_stat_activity
WHERE datname = current_database();
```

**Mong đợi:** Chỉ còn 1-2 connections (là connection của bạn)

### Bước 5: Test Ứng Dụng

Chạy lại ứng dụng Java của bạn.

---

## 🎯 PHƯƠNG ÁN 3: CHẠY TOOL JAVA (TỪ CODE)

Tôi đã tạo tool `KillAllConnections.java` cho bạn.

### Chạy Tool:

**Trong NetBeans/IntelliJ:**
1. Mở file: `Smart_Card_UI/src/main/java/db/KillAllConnections.java`
2. Right-click → **Run File** (hoặc Shift+F6)

**Từ Command Line (nếu có Maven):**
```bash
cd Smart_Card_UI
mvn exec:java -Dexec.mainClass="db.KillAllConnections"
```

Tool này sẽ:
- Thử kết nối đến Supabase
- Hiển thị số lượng connections hiện tại
- Tự động kill các idle connections
- Hướng dẫn bạn nếu không thành công

---

## 🔮 SAU KHI SỬA - KIỂM TRA

### Test Kết Nối Mới

Chạy tool test:

**Trong NetBeans/IntelliJ:**
1. Mở file: `Smart_Card_UI/src/main/java/db/TestSupabaseConnection.java`
2. Right-click → **Run File**

**Kết quả mong đợi:**
```
✓✓✓ KẾT NỐI THÀNH CÔNG! ✓✓✓
Thời gian kết nối: 1234 ms
✓ Query thành công
✓ Tổng số connection hiện tại: 2-3
```

### Chạy Ứng Dụng Chính

Bây giờ chạy ứng dụng chính (`MainFrame`):

**Kết quả mong đợi:**
```
[DatabaseConnection] ✓ Đã khởi tạo HikariCP pool
[DatabaseConnection]   Max pool size: 2
[DatabaseConnection]   Min idle: 0
[DatabaseConnection] ✓ Test kết nối thành công!
```

**KHÔNG CÒN LỖI "Max client connections reached"!** ✅

---

## 📊 TẠI SAO HIKARICP SẼ NGĂN CHẶN VẤN ĐỀ NÀY?

### Trước (Code Cũ - BROKEN):
```java
// MỖI thao tác tạo connection mới
public void saveData() {
    Connection conn = DriverManager.getConnection(URL);  // ← Connection mới #1
    // ... làm gì đó
    conn.close();  // ← Có thể không chạy nếu có lỗi
}

public void loadData() {
    Connection conn = DriverManager.getConnection(URL);  // ← Connection mới #2
    // ...
}
```

**Vấn đề:**
- Phát hành 1 thẻ = 5+ connections mới
- 10 thao tác = 50 connections → **VƯỢT GIỚI HẠN!**

### Sau (Với HikariCP - FIXED):
```java
// HikariCP tạo SẴN 2 connections và TÁI SỬ DỤNG
private static HikariDataSource dataSource;  // ← Pool có 2 connections

public void saveData() {
    Connection conn = dataSource.getConnection();  // ← Lấy từ pool
    // ... làm gì đó
    conn.close();  // ← Trả connection về pool (KHÔNG đóng thật)
}

public void loadData() {
    Connection conn = dataSource.getConnection();  // ← Lấy lại connection CŨ từ pool
    // ...
}
```

**Ưu điểm:**
- CHỈ CÓ 2 connections trên Supabase
- Tái sử dụng → Nhanh hơn
- KHÔNG BAO GIỜ vượt giới hạn
- Tự động đóng đúng cách

---

## 🎓 BÀI HỌC RÚT RA

1. **Supabase Connection Pooler ≠ Application Connection Pool**
   - Supabase pooler là **server-side** (giữa Supabase và database)
   - Ứng dụng của bạn VẪN CẦN **client-side pool** (HikariCP)

2. **Free Tier Limits**
   - Max 15-20 connections
   - Auto-pause sau 7 ngày không hoạt động
   - Cần design cho ít connections

3. **Best Practices**
   - LUÔN dùng connection pooling (HikariCP, DBCP, C3P0)
   - `maximumPoolSize` phải <= 1/3 giới hạn của database
   - `minimumIdle` = 0 hoặc 1 cho free tier

---

## ✅ CHECKLIST HOÀN THÀNH

- [ ] Restart Supabase database (Phương án 1)
  - HOẶC Kill connections qua SQL (Phương án 2)
  - HOẶC Chạy tool KillAllConnections (Phương án 3)

- [ ] Chạy `TestSupabaseConnection.java` → Kết nối thành công

- [ ] Chạy ứng dụng chính → Thấy "HikariCP pool initialized"

- [ ] Test phát hành thẻ → Không còn lỗi connection

- [ ] Kiểm tra log → Chỉ còn 2-3 connections trên Supabase

---

## 🆘 NẾU VẪN GẶP VẤN ĐỀ

### Lỗi: "Max connections" ngay sau khi restart

**Nguyên nhân:** Có ứng dụng/process KHÁC đang giữ connections

**Giải pháp:**
1. Kiểm tra xem có đang chạy nhiều instance của ứng dụng không
2. Kiểm tra Supabase Studio (web dashboard) có đang mở nhiều tab không
3. Đợi 5-10 phút để các connection timeout tự nhiên

### Lỗi: "Connection timeout" sau khi restart

**Nguyên nhân:** Database đang wake up

**Giải pháp:**
- Đợi 30-60 giây
- Thử lại

### Lỗi: Credentials không đúng

**Giải pháp:**
1. Vào Supabase Dashboard → Settings → Database
2. Copy lại:
   - Host
   - Port (phải là **6543** cho pooler)
   - Database name
   - User
   - Password
3. Cập nhật trong `DatabaseConnection.java`

---

## 📞 SUPPORT

Nếu sau khi làm theo hướng dẫn vẫn gặp lỗi, cung cấp thông tin:

1. Output của `TestSupabaseConnection.java`
2. Output của query kiểm tra connections trong SQL Editor
3. Screenshot Supabase Dashboard → Settings → Database
4. Log đầy đủ của ứng dụng khi chạy

---

**🎉 CHÚC MỪNG! Sau khi hoàn thành, ứng dụng của bạn sẽ KHÔNG BAO GIỜ gặp lỗi "Max connections" nữa!**

