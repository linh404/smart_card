Đề xuất bổ sung RSA vào các chức năng
1. Xác thực thẻ thật khi đăng nhập User (ưu tiên cao)
Mục đích: Chống thẻ giả/clone
Luồng đề xuất:
1. User nhập PIN → verify PIN thành công2. UI sinh challenge ngẫu nhiên (32 bytes)3. UI gửi challenge xuống thẻ → thẻ ký bằng SK_user4. UI verify signature bằng PK_user (lấy từ DB)5. Nếu verify thành công → cho phép đăng nhập
Lợi ích:
Xác thực thẻ thật (có SK_user)
Chống replay attack (challenge ngẫu nhiên)
Phù hợp với README.md (đã đề cập)
Vị trí implement:
UserApplet: thêm RSA key pair + method SIGN_CHALLENGE_USER (INS 0x06)
UserLoginFrame: thêm challenge-response sau khi verify PIN
Database: lưu PK_user khi phát hành thẻ
2. Xác thực khi phát hành thẻ mới (ưu tiên cao)
Mục đích: Xác thực thẻ mới là thật, chống thẻ giả
Luồng đề xuất:
1. Admin phát hành thẻ → ISSUE_CARD2. Thẻ sinh RSA key pair (SK_user, PK_user)3. Thẻ trả về PK_user cùng với cardId4. UI lưu PK_user vào DB5. (Tùy chọn) UI gửi challenge → thẻ ký → verify để xác nhận thẻ thật
Lợi ích:
Đảm bảo thẻ có khả năng ký số
PK_user được lưu để dùng cho challenge-response sau này
Tạo cơ sở cho các chức năng RSA khác
Vị trí implement:
UserApplet.issueCard(): sinh RSA key pair, trả về PK_user
CardIssuePanel: lưu PK_user vào DB
Database: thêm cột pk_user vào bảng user_cards
3. Xác thực khi Admin reset PIN (ưu tiên trung bình)
Mục đích: Thêm lớp bảo mật cho thao tác nhạy cảm
Luồng đề xuất:
1. Admin đăng nhập hệ thống (username/password)2. Admin chọn reset PIN → UI yêu cầu thẻ ký challenge3. UI sinh challenge → gửi xuống thẻ4. Thẻ ký challenge bằng SK_user (sau khi verify PIN_admin_reset)5. UI verify signature → nếu đúng mới cho phép reset PIN
Lợi ích:
Xác nhận thẻ thật trước khi reset
Chống tấn công từ xa
Tăng độ tin cậy cho thao tác quan trọng
Vị trí implement:
UserApplet.adminResetPin(): thêm bước ký challenge (sau verify PIN_admin_reset)
ResetPinPanel: thêm challenge-response trước khi reset
4. Ký số giao dịch (nếu có nạp tiền/thanh toán) (ưu tiên trung bình)
Mục đích: Đảm bảo tính toàn vẹn và không thể chối bỏ
Luồng đề xuất:
1. User thực hiện giao dịch (nạp/chi)2. Thẻ tạo transaction record: {type, amount, timestamp, cardId}3. Thẻ ký transaction record bằng SK_user4. Thẻ lưu transaction + signature vào logs_enc5. UI có thể verify signature khi đọc logs
Lợi ích:
Chống giả mạo giao dịch
Non-repudiation
Audit trail có chữ ký số
Vị trí implement:
UserApplet: thêm method ký transaction (khi implement credit/debit)
Transaction model: thêm trường signature
5. Xác thực khi cập nhật dữ liệu bệnh nhân (ưu tiên thấp)
Mục đích: Đảm bảo tính toàn vẹn dữ liệu
Luồng đề xuất:
1. Admin cập nhật patient data2. Thẻ tạo hash của dữ liệu mới3. Thẻ ký hash bằng SK_user4. Thẻ lưu signature cùng với dữ liệu5. Khi đọc dữ liệu, UI có thể verify signature
Lợi ích:
Phát hiện dữ liệu bị sửa đổi
Đảm bảo tính toàn vẹn
Vị trí implement:
UserApplet.updatePatientData(): thêm bước ký hash
UserData model: thêm trường signature (optional)