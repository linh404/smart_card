# HỆ THỐNG THẺ THÔNG MINH BỆNH VIỆN

## Tổng Quan

Hệ thống sử dụng **một loại thẻ duy nhất** - **Thẻ Bệnh nhân** (User Card) để quản lý thông tin và giao dịch trong bệnh viện. Toàn bộ dữ liệu được mã hóa và lưu trữ an toàn trên thẻ thông minh.

### Kiến Trúc Hệ Thống

```
┌─────────────────┐      ┌──────────────────┐      ┌─────────────┐
│   Thẻ User      │ ←──→ │  UI (Java Swing) │ ←──→ │  Supabase   │
│   (JavaCard)    │      │  + Backend       │      │  Database   │
└─────────────────┘      └──────────────────┘      └─────────────┘
```

### Các Thành Phần Chính

1. **Thẻ User (JavaCard Applet)**
   - Lưu trữ dữ liệu bệnh nhân đã mã hóa
   - Quản lý PIN và xác thực
   - Xử lý giao dịch (nạp tiền, thanh toán)
   - Mã hóa/giải mã dữ liệu bằng MK_user

2. **UI + Backend (Java Swing)**
   - Giao diện quản trị và người dùng
   - Giao tiếp với thẻ qua PC/SC
   - Quản lý tài khoản Admin
   - Tích hợp với database

3. **Supabase Database**
   - Lưu ánh xạ thẻ ↔ bệnh nhân
   - Quản lý trạng thái thẻ
   - Lưu log hệ thống
   - **KHÔNG** lưu dữ liệu nhạy cảm (PIN, MK_user, K_master)

---

## Cơ Chế Bảo Mật

### Dữ Liệu Trên Thẻ

| Dữ liệu | Mô tả | Trạng thái |
|---------|-------|------------|
| `cardId_user` | ID định danh thẻ | Plaintext |
| `MK_user` | Master key mã hóa dữ liệu | Encrypted |
| `PIN_user_hash` | Hash của PIN người dùng | Hashed |
| `PIN_admin_reset_hash` | Hash của PIN quản trị | Hashed |
| `UserData_enc` | Thông tin bệnh nhân | Encrypted |
| `balance_enc` | Số dư tài khoản | Encrypted |
| `logs_enc` | Lịch sử giao dịch | Encrypted |

### Cơ Chế Bọc Khóa (Key Wrapping)

**MK_user** được bọc bằng 2 cách:

1. **Enc_user** = AES_Encrypt(KDF(PIN_user), MK_user)
   - Dùng cho đăng nhập bệnh nhân

2. **Enc_admin** = AES_Encrypt(KDF(PIN_admin_reset), MK_user)
   - Dùng cho reset PIN bởi Admin

**PIN_admin_reset** được derive động:
```
PIN_admin_reset = HMAC-SHA256(K_master, cardId_user)
```
- K_master: Khóa bí mật để tạo PIN admin (lưu trong env)
- Mỗi thẻ có PIN_admin_reset riêng biệt
- KHÔNG lưu trong database

---

## CHỨC NĂNG CHÍNH

## 🔐 A. CHỨC NĂNG ADMIN

### A1. Đăng Nhập Admin

**Luồng xử lý:**
```
1. Admin nhập username/password trên UI
2. UI xác thực với Supabase
3. Kiểm tra role = Admin
4. Cho phép truy cập các chức năng quản trị
```

**Dữ liệu:** Tài khoản Admin lưu trong Supabase

---

### A2. Phát Hành Thẻ Mới

**Luồng xử lý:**

```
[UI/Admin]
  1. Nhập thông tin bệnh nhân (họ tên, ngày sinh, mã BHYT, ảnh...)
  2. Nhập PIN_user_default (hoặc tự động sinh)
  3. Cắm thẻ trắng vào đầu đọc
  4. Đọc cardId_user từ thẻ
  5. Derive PIN_admin_reset = HMAC(K_master, cardId_user)
  6. Gửi dữ liệu xuống thẻ
     
[Thẻ User]
  7. Sinh MK_user (AES key)
  8. Sinh cặp khóa RSA (SK_user, PK_user) [tùy chọn]
  9. Mã hóa dữ liệu:
     - UserData_enc = AES(MK_user, UserData)
     - balance_enc = AES(MK_user, 0)
     - logs_enc = AES(MK_user, [])
  10. Hash PIN:
     - PIN_user_hash = Hash(PIN_user_default)
     - PIN_admin_reset_hash = Hash(PIN_admin_reset)
  11. Bọc MK_user:
     - Enc_user = AES(KDF(PIN_user_default), MK_user)
     - Enc_admin = AES(KDF(PIN_admin_reset), MK_user)
  12. Lưu tất cả vào EEPROM
  13. Trả PK_user cho UI
     
[UI/Backend]
  14. Lưu vào Supabase:
      - cardId_user ↔ patient_id
      - PK_user (nếu dùng RSA)
      - status = ACTIVE
      - Thông tin mapping
```

**Kết quả:** Thẻ sẵn sàng sử dụng với dữ liệu đã mã hóa

---

### A3. Sửa Thông Tin Thẻ

**Luồng xử lý:**

```
[UI/Admin]
  1. Chọn bệnh nhân cần sửa (từ DB)
  2. Yêu cầu bệnh nhân cắm thẻ
  3. Kiểm tra cardId_user khớp
  4. Yêu cầu bệnh nhân nhập PIN_user
     
[Thẻ User]
  5. Xác thực PIN_user
  6. Mở MK_user = AES_Decrypt(KDF(PIN_user), Enc_user)
  7. Giải mã UserData_enc → UserData (plaintext)
  8. Trả UserData cho UI
     
[UI/Admin]
  9. Hiển thị form chỉnh sửa
  10. Admin cập nhật thông tin
  11. Gửi UserData_new xuống thẻ
     
[Thẻ User]
  12. Mã hóa: UserData_enc_new = AES(MK_user, UserData_new)
  13. Ghi đè UserData_enc
  14. Trả status OK
     
[UI/Backend]
  15. Đồng bộ thông tin lên Supabase (nếu cần)
```

**Yêu cầu:** Bệnh nhân phải biết PIN_user

---

### A4. Reset PIN / Mở Khóa Thẻ

**Luồng xử lý:**

```
[UI/Admin]
  1. Chọn bệnh nhân cần reset PIN
  2. Lấy cardId_user từ DB
  3. Yêu cầu bệnh nhân cắm thẻ
  4. Kiểm tra cardId_user khớp
  5. Backend derive: PIN_admin_reset = HMAC(K_master, cardId_user)
  6. Admin nhập PIN_user_new
  7. Gửi APDU: {PIN_admin_reset, PIN_user_new}
     
[Thẻ User]
  8. Xác thực PIN_admin_reset:
     - Hash(PIN_admin_reset) == PIN_admin_reset_hash?
     - Nếu SAI → Trả lỗi, DỪNG
  9. Mở MK_user = AES_Decrypt(KDF(PIN_admin_reset), Enc_admin)
  10. Tính toán dữ liệu mới:
     - PIN_user_hash_new = Hash(PIN_user_new)
     - Enc_user_new = AES(KDF(PIN_user_new), MK_user)
  11. Cập nhật:
     - PIN_user_hash ← PIN_user_hash_new
     - Enc_user ← Enc_user_new
     - pin_retry_counter ← MAX_RETRY (3)
     - blocked_flag ← 0 (mở khóa)
  12. Xóa MK_user khỏi RAM
  13. Trả status SUCCESS
```

**Kết quả:** 
- PIN_user được đặt lại
- Thẻ được mở khóa (nếu bị khóa)
- Bệnh nhân có thể đăng nhập bằng PIN mới

**Bảo mật:** Chỉ Admin có K_master mới derive được PIN_admin_reset đúng

---

## 👤 B. CHỨC NĂNG USER (BỆNH NHÂN)

### B1. Đăng Nhập User

**Luồng xử lý:**

```
[UI/User]
  1. Bệnh nhân cắm thẻ
  2. Đọc cardId_user
  3. Nhập ID bệnh nhân + PIN_user
  4. Gửi PIN_user xuống thẻ
     
[Thẻ User]
  5. Xác thực PIN:
     - Hash(PIN_user) == PIN_user_hash?
     - Nếu SAI:
       → Giảm pin_retry_counter
       → Nếu = 0: blocked_flag = 1 (khóa thẻ)
       → Trả lỗi + số lần còn lại
     - Nếu ĐÚNG:
       → Đặt cờ "authenticated" trong RAM
       → Reset pin_retry_counter = MAX_RETRY
  6. [Tùy chọn] Xác thực RSA:
     - Sinh signature = RSA_Sign(SK_user, challenge)
     - Trả signature cho UI
     
[UI]
  7. Verify signature bằng PK_user (từ DB)
  8. Nếu hợp lệ → Cho phép truy cập giao diện User
```

**Bảo mật:**
- Sau 3 lần nhập sai → Thẻ bị khóa
- Cần Admin reset để mở khóa

---

### B2. Xem Thông Tin Thẻ

**Luồng xử lý:**

```
[UI] Gửi lệnh GET_USER_DATA
     ↓
[Thẻ User]
  1. Kiểm tra đã authenticated?
  2. Mở MK_user = AES_Decrypt(KDF(PIN_user), Enc_user)
  3. Giải mã:
     - UserData = AES_Decrypt(MK_user, UserData_enc)
     - balance = AES_Decrypt(MK_user, balance_enc)
  4. Trả plaintext: {họ tên, ngày sinh, mã BHYT, ảnh, số dư...}
     ↓
[UI] Hiển thị thông tin cho bệnh nhân
```

**Dữ liệu hiển thị:**
- Họ tên, ID bệnh nhân
- Ngày sinh, quê quán
- Mã BHYT
- Ảnh bệnh nhân
- Số dư tài khoản

---

### B3. Nạp Tiền / Thanh Toán

**Luồng xử lý:**

```
[UI/User]
  1. Chọn loại giao dịch: NAP_TIEN / THANH_TOAN
  2. Nhập số tiền
  3. Gửi {type, amount} xuống thẻ
     
[Thẻ User]
  4. Kiểm tra authenticated?
  5. Giải mã số dư: balance = AES_Decrypt(MK_user, balance_enc)
  6. Tính toán:
     - Nếu NAP_TIEN: balance_new = balance + amount
     - Nếu THANH_TOAN: balance_new = balance - amount
  7. Kiểm tra điều kiện:
     - balance_new >= 0?
     - Nằm trong hạn mức?
  8. Nếu hợp lệ:
     - Mã hóa: balance_enc_new = AES(MK_user, balance_new)
     - Cập nhật log:
       logs = AES_Decrypt(MK_user, logs_enc)
       logs_new = logs + [{timestamp, type, amount, balance_new}]
       logs_enc_new = AES(MK_user, logs_new)
     - Ghi vào EEPROM
  9. Trả {status, balance_new}
     
[UI] Hiển thị kết quả + số dư mới
```

**Kiểm tra:**
- Không cho phép số dư âm
- Kiểm tra hạn mức giao dịch
- Log đầy đủ lịch sử

---

### B4. Xem Lịch Sử Giao Dịch

**Luồng xử lý:**

```
[UI] Gửi lệnh GET_TRANSACTION_LOGS
     ↓
[Thẻ User]
  1. Kiểm tra authenticated?
  2. Giải mã: logs = AES_Decrypt(MK_user, logs_enc)
  3. Trả danh sách giao dịch
     ↓
[UI] Hiển thị bảng lịch sử:
     - Thời gian
     - Loại giao dịch
     - Số tiền
     - Số dư sau giao dịch
```

---

### B5. Tra Cứu Thông Tin BHYT

**Luồng xử lý:**

```
[UI] Gửi lệnh GET_BHYT_CODE
     ↓
[Thẻ User]
  1. Giải mã: UserData = AES_Decrypt(MK_user, UserData_enc)
  2. Trích xuất maBHYT
  3. Trả maBHYT
     ↓
[UI/Backend]
  4. Truy vấn API/Supabase với maBHYT
  5. Lấy thông tin BHYT đầy đủ
     ↓
[UI] Hiển thị:
     - Thời hạn thẻ BHYT
     - Nơi đăng ký KCB
     - Mức hưởng
     - ...
```

---

### B6. Đổi PIN (Tự Thực Hiện)

**Luồng xử lý:**

```
[UI/User]
  1. Nhập PIN_cu (PIN cũ)
  2. Nhập PIN_moi (PIN mới)
  3. Gửi {PIN_cu, PIN_moi} xuống thẻ
     
[Thẻ User]
  4. Xác thực PIN cũ:
     - Hash(PIN_cu) == PIN_user_hash?
     - Nếu SAI → Giảm retry_counter, trả lỗi
  5. Kiểm tra PIN_moi ≠ PIN_cu (không cho trùng)
  6. Mở MK_user = AES_Decrypt(KDF(PIN_cu), Enc_user)
  7. Tính toán dữ liệu mới:
     - PIN_user_hash_new = Hash(PIN_moi)
     - Enc_user_new = AES(KDF(PIN_moi), MK_user)
  8. Cập nhật:
     - PIN_user_hash ← PIN_user_hash_new
     - Enc_user ← Enc_user_new
     - Reset pin_retry_counter = MAX_RETRY
  9. Xóa MK_user khỏi RAM
  10. Trả status SUCCESS
     
[UI] Hiển thị "Đổi PIN thành công"
```

**Yêu cầu:**
- Phải biết PIN cũ
- PIN mới phải khác PIN cũ

---

## 📊 CẤU TRÚC DỮ LIỆU

### Thông Tin Bệnh Nhân (UserData)

```json
{
  "hoTen": "Nguyễn Văn A",
  "idBenhNhan": "BN123456",
  "ngaySinh": "1990-01-01",
  "queQuan": "Hà Nội",
  "maBHYT": "DN123456789012345",
  "anhDaiDien": "<base64_encoded_image>",
  "gioiTinh": "Nam",
  "soDienThoai": "0123456789"
}
```

### Log Giao Dịch (Transaction Log)

```json
[
  {
    "timestamp": "2026-01-01T10:30:00",
    "type": "NAP_TIEN",
    "amount": 500000,
    "balance_after": 500000,
    "location": "Quầy thu ngân 1"
  },
  {
    "timestamp": "2026-01-01T14:15:00",
    "type": "THANH_TOAN",
    "amount": 150000,
    "balance_after": 350000,
    "service": "Khám nội khoa"
  }
]
```

### Trạng Thái Thẻ (Card Status)

| Status | Ý nghĩa | Có thể sử dụng? |
|--------|---------|-----------------|
| ACTIVE | Thẻ đang hoạt động | ✅ Có |
| BLOCKED | Thẻ bị khóa do sai PIN | ❌ Cần Admin mở khóa |
| LOST | Thẻ bị mất | ❌ Cần cấp thẻ mới |
| REVOKED | Thẻ bị thu hồi | ❌ Vĩnh viễn vô hiệu |
| EXPIRED | Thẻ hết hạn | ❌ Cần gia hạn |

---

## 🔒 CƠ CHẾ BẢO MẬT

### Nguyên Tắc Bảo Mật

1. **Zero Trust Database**
   - Database KHÔNG thể đọc dữ liệu bệnh nhân
   - Không có PIN, MK_user trong DB
   - Chỉ lưu metadata và ciphertext

2. **PIN Phân Tầng**
   - `PIN_user`: Bệnh nhân tự quản lý, có thể đổi
   - `PIN_admin_reset`: Derive từ K_master, dùng để reset

3. **Key Rotation**
   - MK_user không đổi (trừ khi cấp thẻ mới)
   - Enc_user thay đổi khi đổi PIN_user
   - Enc_admin cố định (trừ khi đổi K_master toàn hệ thống)

4. **Defense in Depth**
   - Layer 1: PIN authentication
   - Layer 2: RSA signature (tùy chọn)
   - Layer 3: Encrypted data storage
   - Layer 4: Card status check
   - Layer 5: Audit logging

### Xử Lý Khi Mất Thẻ

```
1. Bệnh nhân báo mất thẻ
2. Admin đánh dấu status = LOST trong DB
3. UI từ chối mọi giao dịch với thẻ đó
4. Cấp thẻ mới:
   - Sinh MK_user mới
   - Sinh PIN_admin_reset mới (dựa vào cardId_user mới)
   - Copy dữ liệu bệnh nhân từ DB hoặc nhập lại
5. Thẻ cũ vô hiệu hóa vĩnh viễn
```

---

## 🛠️ CÔNG NGHỆ SỬ DỤNG

### JavaCard Applet (Smart_Card_JCIDE)

- **Platform:** JavaCard 3.0.4
- **Crypto:** 
  - AES-128/256 (mã hóa dữ liệu)
  - SHA-256 (hash PIN)
  - HMAC-SHA256 (derive PIN_admin_reset)
  - RSA-2048 (xác thực thẻ - tùy chọn)
- **Storage:** EEPROM persistent

**Cấu trúc modules:**
- `UserApplet.java`: Xử lý APDU, điều phối
- `CryptoHelper.java`: Mã hóa/giải mã, KDF
- `PINHelper.java`: Quản lý PIN, retry counter
- `DataHelper.java`: Serialize/deserialize
- `RSAHelper.java`: Chữ ký số RSA

### Java Swing UI (Smart_Card_UI)

- **Framework:** Java Swing + Maven
- **Java Version:** 1.8
- **Dependencies:**
  - `javax.smartcardio`: Giao tiếp PC/SC
  - `postgresql`: Kết nối Supabase
  - `bcprov-jdk18on`: BouncyCastle crypto
  - `gson`: JSON parsing
  - `jbcrypt`: Password hashing
  - `HikariCP`: Connection pooling

**Cấu trúc packages:**
- `ui.*`: Giao diện Swing
- `card.*`: APDU commands
- `db.*`: Database access
- `model.*`: Data models
- `util.*`: Utilities

### Database (Supabase/PostgreSQL)

**Tables chính:**
- `users`: Tài khoản Admin
- `cards`: Thông tin thẻ (cardId, status, patient_id)
- `patients`: Hồ sơ bệnh nhân
- `card_status_history`: Lịch sử thay đổi trạng thái
- `admin_audit_log`: Log hành động Admin

---

## 📋 SƠ ĐỒ TỔNG QUAN

### Vòng Đời Thẻ

```
┌─────────────┐
│ Phát hành   │ → Admin cấp thẻ, sinh khóa, mã hóa dữ liệu
└──────┬──────┘
       ↓
┌─────────────┐
│   ACTIVE    │ → Bệnh nhân sử dụng bình thường
└──────┬──────┘
       ↓
  ┌────┴────┐
  │         │
  ↓         ↓
┌──────┐  ┌──────────┐
│BLOCKED│  │  LOST    │ → Admin đánh dấu
└───┬───┘  └────┬─────┘
    │           │
    ↓           ↓
┌─────────┐  ┌──────────┐
│ Mở khóa │  │ Cấp mới  │
└────┬────┘  └────┬─────┘
     │            │
     └────→←──────┘
          ↓
      ┌──────────┐
      │ REVOKED  │ → Vĩnh viễn vô hiệu
      └──────────┘
```

### Luồng Dữ Liệu Mã Hóa

```
[Bệnh nhân nhập PIN_user]
         ↓
    ┌────────────┐
    │ Thẻ hash   │ → Hash(PIN_user) == PIN_user_hash?
    └─────┬──────┘
          ↓ (ĐÚNG)
    ┌─────────────┐
    │ KDF(PIN)    │ → Sinh K_user
    └─────┬───────┘
          ↓
    ┌──────────────────┐
    │ AES_Decrypt      │ → MK_user = AES_Dec(K_user, Enc_user)
    │ (Enc_user)       │
    └─────┬────────────┘
          ↓
    ┌──────────────────┐
    │ AES_Decrypt      │ → UserData = AES_Dec(MK_user, UserData_enc)
    │ (UserData_enc)   │
    └─────┬────────────┘
          ↓
    [Dữ liệu plaintext]
```

---

## 🚀 HƯỚNG DẪN SỬ DỤNG

### Khởi Động Hệ Thống

1. **Cài đặt JavaCard Applet**
   ```bash
   cd Smart_Card_JCIDE
   # Build .cap file
   # Upload lên thẻ bằng JCIDE hoặc GPShell
   ```

2. **Khởi động UI**
   ```bash
   cd Smart_Card_UI
   mvn clean install
   mvn exec:java -Dexec.mainClass="ui.MainFrame"
   ```

3. **Cấu hình Database**
   - Tạo file `.env`:
     ```
     SUPABASE_URL=https://xxx.supabase.co
     SUPABASE_KEY=your_key_here
     K_MASTER=your_master_key_hex
     ```

### Quy Trình Cấp Thẻ Mới

1. Admin đăng nhập UI
2. Chọn "Phát hành thẻ mới"
3. Nhập thông tin bệnh nhân
4. Cắm thẻ trắng
5. Hệ thống tự động:
   - Đọc cardId_user
   - Sinh PIN_user_default
   - Derive PIN_admin_reset
   - Ghi dữ liệu lên thẻ
   - Lưu metadata vào DB
6. In thẻ và giao cho bệnh nhân

### Xử Lý Sự Cố

**Thẻ bị khóa (nhập sai PIN 3 lần)**
```
1. Admin đăng nhập
2. Chọn "Reset PIN / Mở khóa thẻ"
3. Tìm bệnh nhân theo ID
4. Yêu cầu cắm thẻ
5. Nhập PIN mới
6. Hệ thống tự động mở khóa
```

**Thẻ bị mất**
```
1. Admin đánh dấu status = LOST
2. Cấp thẻ mới với cardId khác
3. Copy dữ liệu từ DB (nếu có backup)
```

---

## 📞 HỖ TRỢ KỸ THUẬT

- **Repository:** d:\Workspace\Smart_Card
- **JavaCard Version:** 3.0.4
- **Java Version:** 1.8
- **Database:** Supabase (PostgreSQL 14+)

**Tài liệu tham khảo:**
- JavaCard API Specification
- PC/SC Specification
- NIST SP 800-108 (KDF)
- FIPS 197 (AES)
