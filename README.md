

HỆ THỐNG THẺ THÔNG MINH BỆNH VIỆN – MÔ HÌNH 1 THẺ (LUỒNG MỚI VỚI K\_MASTER)



I. Mô hình tổng quan hệ thống thẻ thông minh trong bệnh viện



Hệ thống sử dụng một loại thẻ duy nhất: Thẻ Bệnh nhân (Thẻ User). Toàn bộ mã hóa và xác thực dữ liệu bệnh nhân được thực hiện trực tiếp bên trong thẻ User. Hệ thống không dùng thẻ Admin riêng; quyền quản trị được kiểm soát bởi tài khoản Admin trên UI và cơ chế PIN\_admin\_reset được dẫn xuất từ khóa gốc của bệnh viện.



1\. Cấu trúc logic mỗi thẻ bệnh nhân:



\- PIN\_user: bệnh nhân dùng để đăng nhập, xem thông tin, thanh toán, nạp tiền.

\- PIN\_admin\_reset: mã “quản trị” riêng cho đúng thẻ đó, dùng để reset PIN\_user / mở khóa thẻ khi bệnh nhân quên PIN.

\- PIN\_admin\_reset của từng thẻ là khác nhau.

\- PIN\_admin\_reset không được lưu trong CSDL; mỗi khi cần, hệ thống backend sẽ tính lại từ:

PIN\_admin\_reset = F(K\_master, cardId\_user)

trong đó K\_master là master key của bệnh viện, chỉ backend/HSM biết.

\- MK\_user: khóa đối xứng (master key) do thẻ sinh ra, dùng để mã hóa/giải mã dữ liệu trên thẻ.



2\. Vai trò của các thành phần:



\- Thẻ User:

\- Lưu trữ và bảo vệ MK\_user, PIN\_user (dạng hash), PIN\_admin\_reset (dạng hash) và dữ liệu bệnh nhân đã mã hóa.

\- Thực hiện toàn bộ thao tác mã hóa/giải mã, reset PIN, khóa/mở khóa thẻ.



\- UI + Backend (Java Swing + server):

\- Điều khiển đầu đọc thẻ, gửi/nhận APDU.

\- Quản lý tài khoản Admin, mapping thẻ ↔ bệnh nhân, trạng thái thẻ.

\- Từ cardId\_user và K\_master, derive động PIN\_admin\_reset khi cần reset/mở khóa.



\- Supabase (CSDL hệ thống):

\- Lưu ánh xạ cardId\_user ↔ hồ sơ bệnh nhân.

\- Lưu trạng thái thẻ (ACTIVE, LOST, REVOKED, EXPIRED, …) và log hệ thống.

\- Tùy chọn lưu ciphertext backup (Enc\_patient) do thẻ trả ra.

\- Không lưu PIN\_user, PIN\_admin\_reset, MK\_user, hoặc K\_master ở dạng rõ.



II. Các thành phần chính



1\. Thẻ Bệnh nhân (Applet User – thẻ duy nhất)



1.1. Dữ liệu và khóa lưu trên thẻ User



Trên mỗi thẻ User lưu các dữ liệu bảo mật sau (trong EEPROM):



\- cardId\_user: định danh duy nhất của thẻ bệnh nhân (ID thẻ).

\- MK\_user: khóa đối xứng (master key) dùng để mã hóa/giải mã dữ liệu trên thẻ.

\- Chỉ tồn tại dạng rõ trong RAM khi xử lý.

\- Khi lưu trên EEPROM, MK\_user luôn được bọc/mã hóa.



Bọc MK\_user theo PIN\_user:

\- K\_user  = KDF(PIN\_user)

\- Enc\_user = AES\_Encrypt(K\_user, MK\_user)

→ Được dùng khi bệnh nhân đăng nhập bằng PIN\_user. Nếu PIN đúng, thẻ dùng K\_user để mở MK\_user.



Bọc MK\_user theo PIN\_admin\_reset (của chính thẻ này):

\- K\_admin  = KDF(PIN\_admin\_reset)

\- Enc\_admin = AES\_Encrypt(K\_admin, MK\_user)

→ Được dùng khi thực hiện reset PIN\_user / mở khóa thẻ bằng quyền Admin trên hệ thống. Thẻ chỉ biết PIN\_admin\_reset dạng hash tại thời điểm triển khai, còn giá trị PIN\_admin\_reset thống nhất được derive từ K\_master + cardId\_user trên backend.



Dữ liệu bệnh nhân và giao dịch (mã hóa bằng MK\_user):

\- UserData\_enc = AES\_Encrypt(MK\_user, UserData\_plain)

Trong đó UserData\_plain gồm: họ tên, ID bệnh nhân, ngày sinh, quê quán, mã BHYT, ảnh đã resize, …

\- balance\_enc = AES\_Encrypt(MK\_user, balance) – số dư tài khoản thanh toán.

\- logs\_enc = AES\_Encrypt(MK\_user, logs) – lịch sử giao dịch.



Dữ liệu PIN và trạng thái:

\- PIN\_user\_hash        = Hash(PIN\_user)        – dùng để kiểm tra PIN\_user khi đăng nhập.

\- PIN\_admin\_reset\_hash = Hash(PIN\_admin\_reset) – dùng để kiểm tra quyền reset/mở khóa.

\- pin\_retry\_counter: bộ đếm số lần nhập sai PIN\_user.

\- blocked\_flag: cờ trạng thái thẻ bị khóa do nhập sai PIN\_user quá số lần cho phép.



Khóa công khai của User (tùy chọn, nếu dùng RSA cho challenge–response):

\- SK\_user, PK\_user: cặp khóa RSA của thẻ User.

\- PK\_user có thể lưu trên Supabase; SK\_user chỉ lưu trong thẻ.

\- Dùng để ký challenge từ UI, giúp hệ thống xác thực thẻ thật (tùy chọn, không liên quan tới K\_master).



1.2. Chức năng chính của Applet User



\- Xác thực bệnh nhân bằng PIN\_user (và tùy chọn thêm chữ ký RSA).

\- Quản lý trạng thái khóa/mở khóa thẻ bằng pin\_retry\_counter và blocked\_flag.

\- Xác thực quyền reset/mở khóa bằng PIN\_admin\_reset (được derive từ K\_master + cardId\_user ở phía hệ thống).

\- Giải mã và cung cấp dữ liệu cần thiết cho UI: thông tin bệnh nhân, mã BHYT, số dư, lịch sử giao dịch…

\- Thực hiện giao dịch: nạp tiền, thanh toán, cập nhật số dư và log.

\- Cho phép bệnh nhân tự đổi PIN\_user khi biết PIN cũ.

\- Cho phép reset PIN\_user và mở khóa thẻ bằng PIN\_admin\_reset, khi có yêu cầu từ hệ thống (role Admin trên phần mềm).



2\. UI và Supabase



2.1. UI (ứng dụng Java Swing + backend)



\- Giao tiếp với đầu đọc thẻ (PC/SC), gửi/nhận APDU tới Applet User.

\- Cung cấp các màn hình:

\- Đăng nhập Admin (tài khoản phần mềm),

\- Phát hành thẻ mới,

\- Quản lý/sửa thông tin thẻ,

\- Reset/mở khóa PIN\_user cho bệnh nhân,

\- Đăng nhập User, hiển thị thông tin, thanh toán, nạp tiền, lịch sử giao dịch, đổi PIN\_user.

\- Khi cần xác thực thẻ thật (nếu dùng RSA):

\- Lấy PK\_user từ Supabase để kiểm tra chữ ký RSA của thẻ (challenge–response).

\- Khi reset/mở khóa PIN\_user:

\- Không đọc PIN\_admin\_reset từ DB.

\- Thay vào đó, backend dùng K\_master và cardId\_user để derive động PIN\_admin\_reset cho đúng thẻ.



2.2. Supabase (CSDL hệ thống)



\- Lưu ánh xạ:

\- cardId\_user ↔ hồ sơ bệnh nhân (patient\_id, thông tin logic khác).

\- (Tùy chọn) cardId\_user ↔ PK\_user nếu dùng RSA.

\- Lưu dữ liệu bệnh viện:

\- Hồ sơ bệnh án, thông tin BHYT đầy đủ, log hệ thống.

\- Lưu trạng thái thẻ ở mức hệ thống:

\- status: ACTIVE, LOST, REVOKED, EXPIRED, …

\- Lịch sử thay đổi trạng thái thẻ (card\_status\_history) và log hành động admin (admin\_audit\_log).

\- Tùy chọn lưu ciphertext backup:

\- encrypted\_patient\_data\_backup = bản sao Enc\_patient do thẻ trả về (DB không có khả năng giải mã nếu không có MK\_user).

\- Không lưu:

\- PIN\_user (kể cả hash),

\- PIN\_admin\_reset,

\- MK\_user,

\- K\_master.



III. Luồng nghiệp vụ phía Admin (trên hệ thống, dùng duy nhất thẻ User)



1\. Đăng nhập Admin trên hệ thống



1.1. Trên UI



\- Quản trị viên mở ứng dụng Java Swing.

\- UI hiển thị form đăng nhập:

\- Tài khoản (username),

\- Mật khẩu (password).

\- UI gửi thông tin lên server/Supabase để kiểm tra:

\- Nếu hợp lệ và có role Admin:

\- Cho phép truy cập giao diện quản trị (phát hành thẻ, reset/mở khóa PIN, sửa thông tin).

\- Nếu không:

\- Từ chối truy cập các chức năng quản trị.



2\. Phát hành thẻ User (cấp thẻ cho bệnh nhân)



2.1. Trên UI / Backend



\- Admin đã đăng nhập hệ thống với role Admin.

\- Admin nhập thông tin bệnh nhân ở dạng plaintext:

\- Họ tên, ID bệnh nhân, ngày sinh, quê quán, mã BHYT, …

\- Ảnh bệnh nhân đã resize.

\- Admin nhập PIN\_user\_default (PIN mặc định cho bệnh nhân) hoặc để hệ thống sinh.

\- Admin cắm thẻ User trắng vào đầu đọc (Select sang Applet User).

\- UI/Backend thực hiện:

\- Đọc cardId\_user từ thẻ.

\- Từ K\_master và cardId\_user, derive:

PIN\_admin\_reset = F(K\_master, cardId\_user)



\- UI gửi toàn bộ dữ liệu plaintext xuống thẻ User:

\- Thông tin bệnh nhân,

\- Ảnh đã xử lý,

\- PIN\_user\_default,

\- PIN\_admin\_reset (vừa derive từ K\_master + cardId\_user).



\- Sau khi xử lý, Applet User trả về:

\- cardId\_user,

\- PK\_user (nếu dùng RSA).



\- UI lưu lên Supabase:

\- cardId\_user, patient\_id, trạng thái status = ACTIVE,

\- Thông tin mapping với hồ sơ bệnh nhân,

\- (Tùy chọn) PK\_user,

\- (Tùy chọn) encrypted\_patient\_data\_backup nếu thẻ trả ciphertext.



2.2. Trong thẻ User – sinh khóa và mã hóa dữ liệu



\- Sinh:

\- MK\_user = random\_AES\_key() – master key mã hóa dữ liệu trên thẻ.

\- (SK\_user, PK\_user) = RSA\_keypair\_generate() – nếu dùng RSA cho challenge–response.



\- Nhận UserData dạng plaintext từ UI:

\- { hoTen, ID\_BN, ngaySinh, queQuan, maBHYT, anh\_xu\_ly, … }



\- Mã hóa dữ liệu:

\- UserData\_enc = AES\_Encrypt(MK\_user, UserData)

\- balance\_enc = AES\_Encrypt(MK\_user, balance\_0)

\- logs\_enc = AES\_Encrypt(MK\_user, logs\_0 hoặc rỗng)



\- Thiết lập PIN:

\- Nhận PIN\_user\_default, PIN\_admin\_reset từ UI.

\- Tạo hash:

\- PIN\_user\_hash        = Hash(PIN\_user\_default)

\- PIN\_admin\_reset\_hash = Hash(PIN\_admin\_reset)

\- Bọc MK\_user theo PIN\_user:

\- K\_user  = KDF(PIN\_user\_default)

\- Enc\_user = AES\_Encrypt(K\_user, MK\_user)

\- Bọc MK\_user theo PIN\_admin\_reset:

\- K\_admin  = KDF(PIN\_admin\_reset)

\- Enc\_admin = AES\_Encrypt(K\_admin, MK\_user)



\- Lưu trên EEPROM thẻ:

\- UserData\_enc, balance\_enc, logs\_enc,

\- PIN\_user\_hash, PIN\_admin\_reset\_hash,

\- Enc\_user, Enc\_admin,

\- PK\_user (nếu dùng), cardId\_user,

\- pin\_retry\_counter (MAX\_RETRY), blocked\_flag = 0.



3\. Quản lý / sửa thông tin thẻ User



3.1. Trên UI



\- Admin đăng nhập hệ thống (role Admin).

\- Admin chọn bệnh nhân/thẻ cần chỉnh sửa theo cardId\_user (tra từ CSDL).

\- Yêu cầu bệnh nhân cắm thẻ User tương ứng vào đầu đọc.

\- UI:

\- Đọc cardId\_user từ thẻ và kiểm tra khớp với card đã chọn.

\- Yêu cầu bệnh nhân đăng nhập bằng PIN\_user.



\- UI gửi PIN\_user xuống thẻ để xác thực:

\- Nếu thẻ báo PIN sai, hiển thị số lần còn lại (nếu có) hoặc báo thẻ đã bị khóa.

\- Nếu PIN đúng và thẻ chưa bị blocked\_flag, thẻ sẽ giải mã MK\_user và cho phép đọc/ghi dữ liệu.



\- Sau khi thẻ cho phép truy cập dữ liệu:

\- UI gửi APDU yêu cầu thẻ trả về dữ liệu bệnh nhân dạng plaintext (họ tên, mã BHYT, …).

\- UI hiển thị form chỉnh sửa dữ liệu plaintext.

\- Admin sửa thông tin và gửi dữ liệu mới xuống thẻ qua APDU UPDATE\_PATIENT\_DATA.



\- Thẻ trả về status OK/FAIL.

\- Sau khi cập nhật thành công trên thẻ:

\- UI cập nhật bản sao thông tin logic trên Supabase (nếu cần đồng bộ).



3.2. Trong thẻ User



\- Kiểm tra:

\- Người dùng đã được xác thực qua PIN\_user (pin\_retry\_counter > 0, blocked\_flag == 0).



\- Mở MK\_user bằng PIN\_user:

\- K\_user  = KDF(PIN\_user)

\- MK\_user = AES\_Decrypt(K\_user, Enc\_user)



\- Giải mã dữ liệu cũ:

\- UserData = AES\_Decrypt(MK\_user, UserData\_enc)



\- Áp dụng thay đổi từ UI → tạo UserData\_moi.



\- Mã hóa lại:

\- UserData\_enc\_new = AES\_Encrypt(MK\_user, UserData\_moi)



\- Ghi đè:

\- UserData\_enc ← UserData\_enc\_new.



\- Xóa MK\_user, K\_user khỏi RAM khi hoàn thành.



4\. Reset PIN\_user và mở khóa thẻ bằng quyền Admin (dùng K\_master)



4.1. Trên UI / Backend (Admin hệ thống)



\- Admin đăng nhập hệ thống (role Admin).

\- Chọn bệnh nhân cần reset/mở khóa PIN\_user, xác định cardId\_user (từ DB).

\- Yêu cầu bệnh nhân cắm đúng thẻ User; UI đọc cardId\_user trên thẻ và kiểm tra khớp.



\- Backend:

\- Lấy cardId\_user,

\- Từ K\_master và cardId\_user derive:

PIN\_admin\_reset = F(K\_master, cardId\_user)



\- Admin nhập PIN\_user\_new (PIN mới cho bệnh nhân) trên UI.



\- UI gửi APDU RESET\_PIN\_BY\_ADMIN xuống thẻ User, kèm:

\- PIN\_admin\_reset,

\- PIN\_user\_new.



4.2. Trong thẻ User



\- Nhận PIN\_admin\_reset và PIN\_user\_new qua APDU RESET\_PIN\_BY\_ADMIN.



\- Xác thực PIN\_admin\_reset:

\- PIN\_admin\_reset\_hash' = Hash(PIN\_admin\_reset)

\- So sánh với PIN\_admin\_reset\_hash.

\- Nếu sai:

\- Trả lỗi “Admin PIN sai”, không mở MK\_user, không thay đổi gì.



\- Nếu đúng:

\- K\_admin  = KDF(PIN\_admin\_reset)

\- MK\_user  = AES\_Decrypt(K\_admin, Enc\_admin)



\- Tính toán dữ liệu mới cho PIN\_user:

\- PIN\_user\_hash\_new = Hash(PIN\_user\_new)

\- K\_user\_new        = KDF(PIN\_user\_new)

\- Enc\_user\_new      = AES\_Encrypt(K\_user\_new, MK\_user)



\- Cập nhật trên thẻ:

\- PIN\_user\_hash ← PIN\_user\_hash\_new

\- Enc\_user      ← Enc\_user\_new

\- pin\_retry\_counter ← MAX\_RETRY

\- blocked\_flag      ← 0  (mở khóa thẻ nếu đang bị khóa)



\- Xóa MK\_user, K\_admin, K\_user\_new khỏi RAM.

\- Trả status “Reset PIN thành công / thẻ đã được mở khóa” cho UI.



IV. Luồng nghiệp vụ phía User (bệnh nhân dùng thẻ User)



1\. Đăng nhập User và hiển thị thông tin thẻ



1.1. Trên UI



\- Bệnh nhân cắm thẻ User (Select Applet User).

\- UI đọc cardId\_user, có thể truy vấn Supabase để lấy thông tin logic nếu cần.

\- UI hiển thị form:

\- Nhập ID bệnh nhân (hoặc mã định danh),

\- Nhập PIN\_user.



\- UI gửi PIN\_user xuống thẻ User.

\- Nếu thẻ báo PIN đúng và chưa bị khóa:

\- (Tùy chọn) UI sinh challenge\_user và gửi xuống thẻ.

\- Thẻ ký: signature\_user = RSA\_Sign(SK\_user, challenge\_user) và trả về.

\- UI dùng PK\_user để verify signature\_user.

\- Nếu hợp lệ → cho phép vào giao diện User.



\- Sau khi đăng nhập thành công:

\- UI gửi lệnh yêu cầu thẻ trả dữ liệu hiển thị (họ tên, ID BN, ảnh, số dư…).

\- UI hiển thị dữ liệu plaintext nhận được từ thẻ.



1.2. Trong thẻ User – xác thực và giải mã



a) Xác thực PIN\_user



\- Nhận PIN\_user từ UI.

\- Tính PIN\_user\_hash' = Hash(PIN\_user) và so sánh với PIN\_user\_hash.

\- Nếu sai:

\- Giảm pin\_retry\_counter.

\- Nếu pin\_retry\_counter == 0 → blocked\_flag = 1 (thẻ bị khóa).

\- Trả status lỗi, cho UI biết số lần còn lại (nếu còn).

\- Nếu đúng:

\- Đặt cờ “User đã xác thực” trong RAM.

\- Reset pin\_retry\_counter = MAX\_RETRY.

\- Khi cần, mở MK\_user:

\- K\_user  = KDF(PIN\_user)

\- MK\_user = AES\_Decrypt(K\_user, Enc\_user)



b) Xác thực RSA User (tùy chọn)



\- Nhận challenge\_user từ UI.

\- Ký: signature\_user = RSA\_Sign(SK\_user, challenge\_user).

\- Trả signature\_user cho UI để kiểm tra bằng PK\_user.



c) Giải mã dữ liệu để hiển thị



\- Khi UI yêu cầu:

\- UserData = AES\_Decrypt(MK\_user, UserData\_enc)

\- balance  = AES\_Decrypt(MK\_user, balance\_enc) (nếu cần)



\- Trả họ tên, ID BN, ảnh, số dư… dạng plaintext cho UI.



2\. Nạp tiền / Thanh toán



2.1. Trên UI



\- Chỉ cho phép truy cập màn hình nạp/chi sau khi User đã đăng nhập thành công.

\- User nhập số tiền và chọn loại giao dịch (nạp hoặc thanh toán).

\- UI gửi yêu cầu {loai\_giao\_dich, so\_tien} xuống thẻ User.

\- Nhận kết quả:

\- Thành công / thất bại,

\- Số dư mới balance\_new.

\- UI hiển thị kết quả và số dư mới.



2.2. Trong thẻ User



\- Kiểm tra cờ “User đã xác thực” và trạng thái thẻ (blocked\_flag == 0).

\- Dùng MK\_user giải mã số dư hiện tại:

\- balance = AES\_Decrypt(MK\_user, balance\_enc)

\- Tính toán:

\- balance\_new = balance + delta

\- delta > 0: nạp tiền.

\- delta < 0: thanh toán.

\- Kiểm tra điều kiện (không âm, hạn mức…).

\- Mã hóa lại:

\- balance\_enc\_new = AES\_Encrypt(MK\_user, balance\_new)

\- Cập nhật log giao dịch:

\- logs     = AES\_Decrypt(MK\_user, logs\_enc)

\- logs\_new = logs + \[giao\_dich\_moi]

\- logs\_enc\_new = AES\_Encrypt(MK\_user, logs\_new)

\- Lưu balance\_enc\_new, logs\_enc\_new và trả balance\_new (plaintext) cho UI.



3\. Kiểm tra thông tin BHYT



3.1. Trên UI



\- Sau khi user đăng nhập, chọn chức năng “Thông tin BHYT”.

\- UI gửi lệnh yêu cầu mã BHYT xuống thẻ User.

\- UI nhận maBHYT từ thẻ, sử dụng Supabase/API server để truy vấn thông tin BHYT đầy đủ.

\- Hiển thị kết quả tra cứu cho bệnh nhân.



3.2. Trong thẻ User



\- Dùng MK\_user giải mã:

\- UserData = AES\_Decrypt(MK\_user, UserData\_enc)

\- Lấy maBHYT từ UserData và gửi ra cho UI.



4\. Kiểm tra lịch sử giao dịch



4.1. Trên UI



\- Sau khi đăng nhập, user chọn “Lịch sử giao dịch”.

\- UI gửi lệnh yêu cầu log xuống thẻ.

\- Nhận danh sách log (plaintext) và hiển thị.



4.2. Trong thẻ User



\- Dùng MK\_user giải mã:

\- logs = AES\_Decrypt(MK\_user, logs\_enc)

\- Gửi logs (đã giải mã) ra UI.



5\. Đổi PIN\_user (User tự thực hiện)



5.1. Trên UI



\- Yêu cầu người dùng nhập:

\- PIN\_cu,

\- PIN\_moi.

\- Gửi cả hai xuống thẻ User.

\- Nhận kết quả:

\- Đổi PIN thành công,

\- Sai PIN cũ,

\- PIN mới trùng PIN cũ,

\- Các mã lỗi khác (nếu có).

\- Hiển thị thông báo tương ứng.



5.2. Trong thẻ User



a) Xác thực và kiểm tra



\- Nhận PIN\_cu, PIN\_moi.

\- Kiểm tra PIN\_cu:

\- PIN\_hash\_cu' = Hash(PIN\_cu) so sánh với PIN\_user\_hash.

\- Nếu sai → từ chối, giảm pin\_retry\_counter, có thể khóa thẻ nếu vượt ngưỡng.



\- Kiểm tra PIN\_moi ≠ PIN\_cu:

\- Nếu trùng → từ chối (không cho sử dụng lại PIN cũ).



b) Mã hóa lại MK\_user với PIN mới



\- Mở MK\_user bằng PIN\_cu:

\- K\_user\_old = KDF(PIN\_cu)

\- MK\_user    = AES\_Decrypt(K\_user\_old, Enc\_user)



\- Tạo hash và khóa mới:

\- PIN\_user\_hash\_new = Hash(PIN\_moi)

\- K\_user\_new        = KDF(PIN\_moi)

\- Enc\_user\_new      = AES\_Encrypt(K\_user\_new, MK\_user)



\- Ghi đè:

\- PIN\_user\_hash ← PIN\_user\_hash\_new

\- Enc\_user      ← Enc\_user\_new

\- Reset pin\_retry\_counter, giữ blocked\_flag = 0.





