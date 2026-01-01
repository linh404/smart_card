package db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

/**
 * DatabaseConnection - Quản lý kết nối Supabase với HikariCP
 * V3: Sử dụng connection pooling để tránh cạn kiệt kết nối
 */
public class DatabaseConnection {

    // Connection Pooler - Transaction Mode
    // Sử dụng pooler thay vì direct connection để tránh lỗi UnknownHostException
    // Host: aws-1-ap-southeast-1.pooler.supabase.com
    // Port: 6543 (Pooler port)
    // User: postgres.rnvqcxqbripfkcckhgkl
    // Database: postgres
    private static final String JDBC_URL = "jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:6543/postgres";
    private static final String USERNAME = "postgres.rnvqcxqbripfkcckhgkl";
    private static final String PASSWORD = "a4fVpu2p0YGto65G";

    // HikariCP DataSource (singleton)
    private static HikariDataSource dataSource;

    /**
     * Khởi tạo HikariCP connection pool (chỉ gọi một lần khi startup)
     */
    private static synchronized void initializePool() {
        if (dataSource != null) {
            return; // Đã được khởi tạo rồi
        }

        try {
            System.out.println("[DatabaseConnection] Đang khởi tạo HikariCP pool...");
            System.out.println("[DatabaseConnection] JDBC URL: " + JDBC_URL);
            System.out.println("[DatabaseConnection] Username: " + USERNAME);

            HikariConfig config = new HikariConfig();

            // Cấu hình kết nối
            config.setJdbcUrl(JDBC_URL);
            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);

            // Cấu hình SSL cho Supabase
            config.addDataSourceProperty("sslmode", "require");

            // ⚡ QUAN TRỌNG: Kích thước pool cho Supabase free tier
            config.setMaximumPoolSize(2); // Tối đa 2 kết nối (an toàn cho free tier)
            config.setMinimumIdle(0); // Không giữ idle connection (tiết kiệm tài nguyên)

            // Cấu hình timeout - TĂNG TIMEOUT ĐỂ CHỜ SUPABASE WAKE UP
            config.setConnectionTimeout(60000); // 60 giây để lấy connection (tăng từ 30s)
            config.setIdleTimeout(600000); // 10 phút idle timeout
            config.setMaxLifetime(1800000); // 30 phút max lifetime
            config.setKeepaliveTime(300000); // 5 phút keepalive

            // Cấu hình retry (tránh storm)
            config.setInitializationFailTimeout(-1); // Không fail fast khi startup

            // Test kết nối
            config.setConnectionTestQuery("SELECT 1");

            // Tên pool
            config.setPoolName("SupabaseHikariPool");

            // Phát hiện leak (debug)
            config.setLeakDetectionThreshold(60000); // 60 giây

            System.out.println("[DatabaseConnection] Đang tạo HikariDataSource...");
            dataSource = new HikariDataSource(config);

            // Test kết nối ngay
            System.out.println("[DatabaseConnection] Đang test kết nối đến Supabase...");
            try (Connection testConn = dataSource.getConnection()) {
                System.out.println("[DatabaseConnection] ✓ Test kết nối thành công!");
            }

            System.out.println("[DatabaseConnection] ✓ Đã khởi tạo HikariCP pool");
            System.out.println("[DatabaseConnection]   Max pool size: 2");
            System.out.println("[DatabaseConnection]   Min idle: 0");

        } catch (Exception e) {
            System.err.println("[DatabaseConnection] ✗ Lỗi khởi tạo HikariCP pool: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Không thể khởi tạo database connection pool", e);
        }
    }

    /**
     * Lấy connection từ pool
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initializePool();
        }
        return dataSource.getConnection();
    }

    /**
     * Đóng connection pool (gọi khi thoát ứng dụng)
     */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DatabaseConnection] Đã đóng HikariCP pool");
        }
    }

    /**
     * Helper method để chuyển byte[] sang hex string
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // ================== RSA PUBLIC KEY METHODS (V3 - RESTORED) ==================
    // Cột pk_user được restore lại để lưu RSA public key cho challenge-response

    /**
     * Lưu RSA public key của user vào database
     * 
     * @param cardId      Card ID (16 bytes)
     * @param pkUserBytes Public key bytes (X.509 encoded)
     * @return true nếu thành công
     */
    public static boolean saveUserPublicKey(byte[] cardId, byte[] pkUserBytes) {
        System.out.println("[DatabaseConnection] saveUserPublicKey: BẮT ĐẦU");
        System.out.println("[DatabaseConnection]   cardId length: " + (cardId != null ? cardId.length : "null"));
        System.out.println("[DatabaseConnection]   cardId hex: " + (cardId != null ? bytesToHex(cardId) : "null"));
        System.out.println(
                "[DatabaseConnection]   pkUserBytes length: " + (pkUserBytes != null ? pkUserBytes.length : "null"));

        // Kiểm tra cardId đã tồn tại trong bảng user_cards chưa
        String checkSql = "SELECT card_id, patient_id, status FROM user_cards WHERE card_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement checkPst = conn.prepareStatement(checkSql)) {
            checkPst.setBytes(1, cardId);
            try (ResultSet rs = checkPst.executeQuery()) {
                if (rs.next()) {
                    System.out.println("[DatabaseConnection]   ✓ CardId TỒN TẠI trong user_cards");
                    System.out.println("[DatabaseConnection]     - patient_id: " + rs.getString("patient_id"));
                    System.out.println("[DatabaseConnection]     - status: " + rs.getString("status"));
                } else {
                    System.err.println("[DatabaseConnection]   ✗ CardId KHÔNG TỒN TẠI trong user_cards!");
                    System.err.println(
                            "[DatabaseConnection]     → Nguyên nhân: Card chưa được insert vào bảng user_cards");
                    System.err.println(
                            "[DatabaseConnection]     → Giải pháp: Phải insert record vào user_cards TRƯỚC KHI lưu PK_user");
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Lỗi kiểm tra cardId tồn tại: " + e.getMessage());
            e.printStackTrace();
        }

        // Thực hiện UPDATE pk_user
        String sql = "UPDATE user_cards SET pk_user = ? WHERE card_id = ?";
        System.out.println("[DatabaseConnection] SQL: " + sql);

        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            System.out.println("[DatabaseConnection] Chuẩn bị execute UPDATE...");
            pst.setBytes(1, pkUserBytes);
            pst.setBytes(2, cardId);

            int rows = pst.executeUpdate();
            System.out.println("[DatabaseConnection] UPDATE affected rows: " + rows);

            if (rows > 0) {
                System.out
                        .println("[DatabaseConnection] ✓ ĐÃ LƯU PK_user thành công cho cardId: " + bytesToHex(cardId));

                // Verify: Đọc lại để xác nhận
                String verifySql = "SELECT pk_user FROM user_cards WHERE card_id = ?";
                try (PreparedStatement verifyPst = conn.prepareStatement(verifySql)) {
                    verifyPst.setBytes(1, cardId);
                    try (ResultSet rs = verifyPst.executeQuery()) {
                        if (rs.next()) {
                            byte[] savedPk = rs.getBytes("pk_user");
                            if (savedPk != null) {
                                System.out.println("[DatabaseConnection]   Verify: pk_user length = " + savedPk.length);
                                System.out.println("[DatabaseConnection]   Verify: ✓ Dữ liệu đã được lưu vào database");
                            } else {
                                System.err.println("[DatabaseConnection]   Verify: ✗ pk_user = NULL (không lưu được)");
                            }
                        }
                    }
                }

                return true;
            } else {
                System.err.println("[DatabaseConnection] ✗ UPDATE KHÔNG TÁC ĐỘNG row nào (rows = 0)");
                System.err.println("[DatabaseConnection]   → Nguyên nhân: WHERE card_id = ? không tìm thấy row nào");
                System.err.println(
                        "[DatabaseConnection]   → Kiểm tra lại: CardId có đúng? Card đã được insert vào user_cards chưa?");
                return false;
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] ✗ EXCEPTION khi lưu PK_user:");
            System.err.println("[DatabaseConnection]   Exception type: " + e.getClass().getName());
            System.err.println("[DatabaseConnection]   Message: " + e.getMessage());
            System.err.println("[DatabaseConnection]   Stack trace:");
            e.printStackTrace();

            // Kiểm tra exception cụ thể
            if (e.getMessage() != null) {
                if (e.getMessage().contains("column") && e.getMessage().contains("does not exist")) {
                    System.err.println("[DatabaseConnection]   → Lỗi: Cột pk_user KHÔNG TỒN TẠI trong bảng user_cards");
                    System.err.println(
                            "[DatabaseConnection]   → Giải pháp: Chạy ALTER TABLE user_cards ADD COLUMN pk_user BYTEA;");
                } else if (e.getMessage().contains("connection")) {
                    System.err.println("[DatabaseConnection]   → Lỗi: Vấn đề kết nối database");
                } else if (e.getMessage().contains("syntax")) {
                    System.err.println("[DatabaseConnection]   → Lỗi: Lỗi cú pháp SQL");
                }
            }

            return false;
        }
    }

    /**
     * Lấy RSA public key của user từ database
     * 
     * @param cardId Card ID (16 bytes)
     * @return Public key bytes (X.509 encoded) hoặc null nếu không có
     */
    public static byte[] getUserPublicKey(byte[] cardId) {
        String sql = "SELECT pk_user FROM user_cards WHERE card_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setBytes(1, cardId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    byte[] pkUser = rs.getBytes("pk_user");
                    if (pkUser != null) {
                        System.out.println("[DatabaseConnection] Đã lấy PK_user, length: " + pkUser.length);
                    } else {
                        System.out.println("[DatabaseConnection] PK_user = null cho cardId: " + bytesToHex(cardId));
                    }
                    return pkUser;
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Lỗi lấy PK_user: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Lưu hoặc cập nhật thông tin bệnh nhân (V3)
     * 
     * @param patientId       Mã bệnh nhân
     * @param fullName        Họ tên
     * @param dateOfBirth     Ngày sinh (format: DD/MM/YYYY)
     * @param address         Địa chỉ/Quê quán
     * @param insuranceNumber Mã BHYT
     * @return true nếu thành công
     */
    public static boolean savePatient(String patientId, String fullName, String dateOfBirth, String address,
            String insuranceNumber) {
        String sql = "INSERT INTO patients (patient_id, full_name, date_of_birth, address, insurance_number, created_at, updated_at) "
                +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (patient_id) DO UPDATE SET " +
                "full_name = EXCLUDED.full_name, " +
                "date_of_birth = EXCLUDED.date_of_birth, " +
                "address = EXCLUDED.address, " +
                "insurance_number = EXCLUDED.insurance_number, " +
                "updated_at = NOW()";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, patientId);
            pst.setString(2, fullName);

            // Parse date of birth (DD/MM/YYYY)
            java.sql.Date dob = null;
            if (dateOfBirth != null && !dateOfBirth.isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy");
                    java.util.Date date = sdf.parse(dateOfBirth);
                    dob = new java.sql.Date(date.getTime());
                } catch (Exception e) {
                    System.err.println("[DatabaseConnection] Error parsing date of birth: " + dateOfBirth);
                }
            }
            pst.setDate(3, dob);

            pst.setString(4, address);
            pst.setString(5, insuranceNumber);

            return pst.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error in savePatient: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lưu thông tin thẻ User mới sau khi phát hành (V3)
     * 
     * @param cardId    Card ID
     * @param patientId Mã bệnh nhân
     * @param adminId   Admin phát hành
     * @return true nếu thành công
     */
    public static boolean saveUserCard(byte[] cardId, String patientId, Integer adminId) {
        System.out.println("[DatabaseConnection] saveUserCard: BẮT ĐẦU");
        System.out.println("[DatabaseConnection]   cardId length: " + (cardId != null ? cardId.length : "null"));
        System.out.println("[DatabaseConnection]   cardId hex: " + (cardId != null ? bytesToHex(cardId) : "null"));
        System.out.println("[DatabaseConnection]   patientId: " + patientId);
        System.out.println("[DatabaseConnection]   adminId: " + adminId);

        // First, ensure patient exists (check if patient_id exists in patients table)
        String checkSql = "SELECT COUNT(*) FROM patients WHERE patient_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement checkPst = conn.prepareStatement(checkSql)) {

            checkPst.setString(1, patientId);
            try (ResultSet rs = checkPst.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // Patient does not exist - this should not happen if savePatient was called
                    // first
                    System.err.println(
                            "[DatabaseConnection] ✗ Patient " + patientId + " KHÔNG TỒN TẠI trong bảng patients!");
                    System.err.println(
                            "[DatabaseConnection]   → Giải pháp: Phải gọi savePatient() TRƯỚC KHI gọi saveUserCard()");
                    return false;
                } else {
                    System.out
                            .println("[DatabaseConnection]   ✓ Patient " + patientId + " tồn tại trong bảng patients");
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] ✗ Lỗi kiểm tra patient existence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        // Now insert/update user_cards
        String sql = "INSERT INTO user_cards (card_id, patient_id, status, issued_at, issued_by_admin_id, created_at) "
                +
                "VALUES (?, ?, 'ACTIVE', NOW(), ?, NOW()) " +
                "ON CONFLICT (card_id) DO UPDATE SET patient_id = EXCLUDED.patient_id, " +
                "status = 'ACTIVE', issued_at = NOW(), issued_by_admin_id = EXCLUDED.issued_by_admin_id, " +
                "last_updated_at = NOW()";
        System.out.println("[DatabaseConnection] SQL: " + sql);

        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            System.out.println("[DatabaseConnection] Chuẩn bị execute INSERT/UPDATE...");
            pst.setBytes(1, cardId);
            pst.setString(2, patientId);
            pst.setObject(3, adminId);

            int rows = pst.executeUpdate();
            System.out.println("[DatabaseConnection] INSERT/UPDATE affected rows: " + rows);

            if (rows > 0) {
                System.out.println("[DatabaseConnection] ✓ ĐÃ LƯU user_cards thành công");

                // Verify: Đọc lại để xác nhận
                String verifySql = "SELECT card_id, patient_id, status, issued_at FROM user_cards WHERE card_id = ?";
                try (PreparedStatement verifyPst = conn.prepareStatement(verifySql)) {
                    verifyPst.setBytes(1, cardId);
                    try (ResultSet rs = verifyPst.executeQuery()) {
                        if (rs.next()) {
                            System.out.println("[DatabaseConnection]   Verify:");
                            System.out.println(
                                    "[DatabaseConnection]     - card_id: " + bytesToHex(rs.getBytes("card_id")));
                            System.out.println("[DatabaseConnection]     - patient_id: " + rs.getString("patient_id"));
                            System.out.println("[DatabaseConnection]     - status: " + rs.getString("status"));
                            System.out.println("[DatabaseConnection]     - issued_at: " + rs.getTimestamp("issued_at"));
                        } else {
                            System.err.println(
                                    "[DatabaseConnection]   Verify: ✗ Không tìm thấy row vừa insert (lỗi bất thường)");
                        }
                    }
                }

                return true;
            } else {
                System.err.println("[DatabaseConnection] ✗ INSERT/UPDATE KHÔNG TÁC ĐỘNG row nào (rows = 0)");
                return false;
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] ✗ EXCEPTION khi saveUserCard:");
            System.err.println("[DatabaseConnection]   Exception type: " + e.getClass().getName());
            System.err.println("[DatabaseConnection]   Message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lưu log hệ thống
     */
    public static boolean saveSystemLog(String action, byte[] cardId, String details) {
        String sql = "INSERT INTO system_logs (action, card_id, details, created_at) VALUES (?, ?, ?, NOW())";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, action);
            pst.setBytes(2, cardId);
            pst.setString(3, details);

            return pst.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy ID bệnh nhân tiếp theo (tự động tăng)
     * Lấy ID lớn nhất từ database, nếu không có thì bắt đầu từ 1
     * 
     * @return ID bệnh nhân tiếp theo dạng String
     */
    public static String getNextPatientId() {
        String sql = "SELECT MAX(CAST(patient_id AS INTEGER)) as max_id FROM user_cards WHERE patient_id ~ '^[0-9]+$'";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql);
                ResultSet rs = pst.executeQuery()) {

            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                if (rs.wasNull()) {
                    return "1";
                }
                return String.valueOf(maxId + 1);
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Lỗi khi lấy next patient ID: " + e.getMessage());
            e.printStackTrace();
        }
        // Nếu có lỗi hoặc không có dữ liệu, trả về 1
        return "1";
    }

    /**
     * Khởi tạo các bảng cần thiết (chạy một lần)
     * Sử dụng V3 schema - Luồng mới với K_master và PIN_admin_reset derive động
     */
    public static void initializeTables() {
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // ========== XÓA CÁC BẢNG V2 CŨ ==========

            // Xóa các bảng V2 cũ không còn dùng
            try {
                stmt.execute("DROP TABLE IF EXISTS admin_cards CASCADE");
                System.out.println("Đã xóa bảng admin_cards (V2 cũ)");
            } catch (Exception e) {
                System.out.println("Không thể xóa admin_cards (có thể chưa tồn tại): " + e.getMessage());
            }

            try {
                stmt.execute("DROP TABLE IF EXISTS bhyt_info CASCADE");
                System.out.println("Đã xóa bảng bhyt_info (V2 cũ)");
            } catch (Exception e) {
                System.out.println("Không thể xóa bhyt_info (có thể chưa tồn tại): " + e.getMessage());
            }

            // ========== V3 TABLES ==========

            // Bảng admin (đổi tên từ admin_users)
            stmt.execute("CREATE TABLE IF NOT EXISTS admin (" +
                    "id SERIAL PRIMARY KEY, " +
                    "username VARCHAR(50) UNIQUE NOT NULL, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "salt VARCHAR(255), " +
                    "role VARCHAR(20) DEFAULT 'admin', " +
                    "full_name VARCHAR(100), " +
                    "email VARCHAR(100), " +
                    "is_active BOOLEAN DEFAULT TRUE, " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "last_login TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT NOW())");

            // Bảng patients (V3 - MỚI)
            stmt.execute("CREATE TABLE IF NOT EXISTS patients (" +
                    "patient_id VARCHAR(50) PRIMARY KEY, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "date_of_birth DATE, " +
                    "gender VARCHAR(10), " +
                    "phone VARCHAR(20), " +
                    "email VARCHAR(100), " +
                    "address TEXT, " +
                    "id_card_number VARCHAR(20), " +
                    "insurance_number VARCHAR(50), " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "updated_at TIMESTAMP DEFAULT NOW(), " +
                    "notes TEXT)");

            // Bảng system_keys (V3 - chỉ lưu metadata, K_master lưu trong .env)
            stmt.execute("CREATE TABLE IF NOT EXISTS system_keys (" +
                    "id SERIAL PRIMARY KEY, " +
                    "key_name VARCHAR(50) UNIQUE NOT NULL, " +
                    "key_version INTEGER DEFAULT 1, " +
                    "is_active BOOLEAN DEFAULT TRUE, " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "description TEXT, " +
                    "rotation_policy TEXT, " +
                    "last_rotated_at TIMESTAMP)");

            // Bảng user_cards (V3 - cập nhật lớn)
            stmt.execute("CREATE TABLE IF NOT EXISTS user_cards (" +
                    "card_id BYTEA PRIMARY KEY, " +
                    "patient_id VARCHAR(50) REFERENCES patients(patient_id) ON DELETE SET NULL, " +
                    "status VARCHAR(20) DEFAULT 'PENDING', " +
                    "expires_at TIMESTAMP, " +
                    "encrypted_patient_data_backup TEXT, " +
                    "backup_created_at TIMESTAMP, " +
                    "issued_at TIMESTAMP, " +
                    "issued_by_admin_id INTEGER REFERENCES admin(id), " +
                    "last_updated_at TIMESTAMP DEFAULT NOW(), " +
                    "created_at TIMESTAMP DEFAULT NOW(), " +
                    "notes TEXT)");

            // Bảng system_logs (giữ nguyên)
            stmt.execute("CREATE TABLE IF NOT EXISTS system_logs (" +
                    "id SERIAL PRIMARY KEY, " +
                    "action VARCHAR(100), " +
                    "card_id BYTEA, " +
                    "details TEXT, " +
                    "created_at TIMESTAMP DEFAULT NOW())");

            // Bảng card_status_history (V3 - MỚI)
            stmt.execute("CREATE TABLE IF NOT EXISTS card_status_history (" +
                    "id SERIAL PRIMARY KEY, " +
                    "card_id BYTEA NOT NULL, " +
                    "old_status VARCHAR(20), " +
                    "new_status VARCHAR(20) NOT NULL, " +
                    "changed_by_admin_id INTEGER REFERENCES admin(id), " +
                    "reason TEXT, " +
                    "created_at TIMESTAMP DEFAULT NOW())");

            // ========== INDEXES ==========

            // Indexes cho admin
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_username ON admin(username)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_is_active ON admin(is_active)");

            // Indexes cho patients
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_patients_full_name ON patients(full_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_patients_id_card_number ON patients(id_card_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_patients_insurance_number ON patients(insurance_number)");

            // Indexes cho user_cards
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_patient_id ON user_cards(patient_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_status ON user_cards(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_expires_at ON user_cards(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_issued_at ON user_cards(issued_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_created_at ON user_cards(created_at)");

            // Indexes cho admin_audit_log
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_user_id ON admin_audit_log(admin_user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action ON admin_audit_log(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_card_id ON admin_audit_log(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_log(created_at)");

            // Indexes cho system_logs
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_card_id ON system_logs(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_action ON system_logs(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON system_logs(created_at)");

            // Indexes cho card_status_history
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_card_status_history_card_id ON card_status_history(card_id)");
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_card_status_history_created_at ON card_status_history(created_at)");

            // ========== STORED PROCEDURE ==========

            // Stored Procedure: Cập nhật trạng thái thẻ (có audit trail)
            stmt.execute("CREATE OR REPLACE FUNCTION update_card_status(" +
                    "p_card_id BYTEA, " +
                    "p_new_status VARCHAR(20), " +
                    "p_admin_id INTEGER, " +
                    "p_reason TEXT DEFAULT NULL" +
                    ") RETURNS VOID AS $$ " +
                    "DECLARE " +
                    "    v_old_status VARCHAR(20); " +
                    "BEGIN " +
                    "    SELECT status INTO v_old_status FROM user_cards WHERE card_id = p_card_id; " +
                    "    UPDATE user_cards SET status = p_new_status, last_updated_at = NOW() WHERE card_id = p_card_id; "
                    +
                    "    INSERT INTO card_status_history (card_id, old_status, new_status, changed_by_admin_id, reason) "
                    +
                    "    VALUES (p_card_id, v_old_status, p_new_status, p_admin_id, p_reason); " +
                    "END; " +
                    "$$ LANGUAGE plpgsql");

            System.out.println("Đã khởi tạo các bảng thành công (V3)!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================== ADMIN USERS METHODS (V2 - Admin không cần thẻ)
    // ==================

    /**
     * Xác thực admin user bằng username và password
     * 
     * @param username Username
     * @param password Plaintext password
     * @return AdminUserInfo nếu đúng, null nếu sai
     */
    public static AdminUserInfo authenticateAdmin(String username, String password) {
        String sql = "SELECT id, username, password_hash, role, full_name, email, is_active " +
                "FROM admin WHERE username = ? AND is_active = TRUE";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, username);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    String passwordHash = rs.getString("password_hash");

                    // Verify password bằng BCrypt
                    if (org.mindrot.jbcrypt.BCrypt.checkpw(password, passwordHash)) {
                        AdminUserInfo user = new AdminUserInfo();
                        user.id = rs.getInt("id");
                        user.username = rs.getString("username");
                        user.role = rs.getString("role");
                        user.fullName = rs.getString("full_name");
                        user.email = rs.getString("email");

                        // Update last_login
                        updateLastLogin(user.id);

                        return user;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error in authenticateAdmin: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Cập nhật last_login timestamp
     */
    private static void updateLastLogin(int userId) {
        String sql = "UPDATE admin SET last_login = NOW() WHERE id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, userId);
            pst.executeUpdate();
        } catch (Exception e) {
            // Log nhưng không throw
            System.err.println("[DatabaseConnection] Error updating last_login: " + e.getMessage());
        }
    }

    /**
     * Tạo admin user mới
     * 
     * @param username Username
     * @param password Plaintext password (sẽ được hash)
     * @param fullName Full name
     * @param email    Email (optional)
     * @return true nếu thành công
     */
    public static boolean createAdminUser(String username, String password, String fullName, String email) {
        String sql = "INSERT INTO admin (username, password_hash, full_name, email, role) " +
                "VALUES (?, ?, ?, ?, 'admin')";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            // Hash password bằng BCrypt
            String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());

            pst.setString(1, username);
            pst.setString(2, passwordHash);
            pst.setString(3, fullName);
            pst.setString(4, email);

            return pst.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error creating admin user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Inner class để lưu thông tin admin user
     */
    public static class AdminUserInfo {
        public int id;
        public String username;
        public String role;
        public String fullName;
        public String email;
    }

    // ================== V3 METHODS - NEW ==================

    /**
     * Lưu backup ciphertext từ thẻ (V3)
     * Backup dữ liệu đã mã hóa từ thẻ vào database
     * 
     * @param cardId        Card ID (byte[])
     * @param encryptedData Encrypted patient data từ thẻ (byte[])
     * @return true nếu thành công
     */
    public static boolean saveEncryptedPatientDataBackup(byte[] cardId, byte[] encryptedData) {
        String sql = "UPDATE user_cards SET encrypted_patient_data_backup = ?, backup_created_at = NOW() " +
                "WHERE card_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            // Convert byte[] to Base64 string for storage
            String encryptedDataBase64 = Base64.getEncoder().encodeToString(encryptedData);

            pst.setString(1, encryptedDataBase64);
            pst.setBytes(2, cardId);

            int rows = pst.executeUpdate();

            if (rows > 0) {
                System.out.println("[DatabaseConnection] Saved encrypted patient data backup for card: " +
                        bytesToHex(cardId));
                return true;
            } else {
                System.err.println("[DatabaseConnection] No card found with ID: " + bytesToHex(cardId));
                return false;
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error saving encrypted patient data backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cập nhật trạng thái thẻ (gọi stored procedure) (V3)
     * Stored procedure tự động cập nhật user_cards.status và ghi vào
     * card_status_history
     * 
     * @param cardId    Card ID (byte[])
     * @param newStatus Trạng thái mới (ACTIVE, LOST, REVOKED, EXPIRED, PENDING)
     * @param adminId   Admin ID thực hiện thao tác
     * @param reason    Lý do thay đổi trạng thái (có thể null)
     * @return true nếu thành công
     */
    public static boolean updateCardStatus(byte[] cardId, String newStatus, Integer adminId, String reason) {
        String sql = "SELECT update_card_status(?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setBytes(1, cardId);
            pst.setString(2, newStatus);
            pst.setObject(3, adminId);
            pst.setString(4, reason);

            pst.execute();

            System.out.println("[DatabaseConnection] Updated card status to " + newStatus +
                    " for card: " + bytesToHex(cardId));
            return true;
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error updating card status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy danh sách giao dịch từ database
     * 
     * @param cardId Card ID (byte[])
     * @return List of Transaction objects
     */
    public static java.util.List<model.Transaction> getTransactions(byte[] cardId) {
        java.util.List<model.Transaction> transactions = new java.util.ArrayList<>();
        String sql = "SELECT seq, type, amount, balance_after, txn_hash, created_at " +
                "FROM transactions WHERE card_id = ? ORDER BY seq ASC";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setBytes(1, cardId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    model.Transaction txn = new model.Transaction();
                    txn.setSeq(rs.getShort("seq"));
                    txn.setLoai(rs.getString("type"));
                    txn.setSoTien(rs.getInt("amount"));
                    txn.setSoDuSau(rs.getInt("balance_after"));
                    txn.setTxnHash(rs.getBytes("txn_hash"));
                    txn.setThoiGian(new java.util.Date(rs.getTimestamp("created_at").getTime()));
                    transactions.add(txn);
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error getting transactions: " + e.getMessage());
            e.printStackTrace();
        }
        return transactions;
    }

    /**
     * Lưu giao dịch vào database
     * 
     * @param cardId Card ID (byte[])
     * @param txn    Transaction object
     * @return true nếu thành công
     */
    public static boolean saveTransaction(byte[] cardId, model.Transaction txn) {
        String sql = "INSERT INTO transactions (card_id, seq, type, amount, balance_after, txn_hash, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setBytes(1, cardId);
            pst.setShort(2, txn.getSeq());
            pst.setString(3, txn.getLoai());
            pst.setInt(4, txn.getSoTien());
            pst.setInt(5, txn.getSoDuSau());
            pst.setBytes(6, txn.getTxnHash());
            pst.setTimestamp(7, new java.sql.Timestamp(txn.getThoiGian().getTime()));

            int rows = pst.executeUpdate();
            System.out.println("[DatabaseConnection] Saved transaction: seq=" + txn.getSeq() +
                    ", type=" + txn.getLoai() + ", amount=" + txn.getSoTien());
            return rows > 0;
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error saving transaction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy thông tin thẻ từ cardID (V3)
     * Join với bảng patients để lấy thông tin bệnh nhân
     * 
     * @param cardId Card ID (byte[])
     * @return CardInfo object hoặc null nếu không tìm thấy
     */
    public static CardInfo getCardInfo(byte[] cardId) {
        String sql = "SELECT uc.*, p.full_name, p.date_of_birth, p.gender, p.phone, " +
                "p.address, p.id_card_number, p.insurance_number " +
                "FROM user_cards uc " +
                "LEFT JOIN patients p ON uc.patient_id = p.patient_id " +
                "WHERE uc.card_id = ?";
        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setBytes(1, cardId);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    CardInfo info = new CardInfo();
                    info.cardId = rs.getBytes("card_id");
                    info.patientId = rs.getString("patient_id");
                    info.status = rs.getString("status");
                    info.expiresAt = rs.getTimestamp("expires_at");
                    info.issuedAt = rs.getTimestamp("issued_at");
                    info.issuedByAdminId = (Integer) rs.getObject("issued_by_admin_id");
                    info.lastUpdatedAt = rs.getTimestamp("last_updated_at");
                    info.createdAt = rs.getTimestamp("created_at");
                    info.notes = rs.getString("notes");
                    info.encryptedPatientDataBackup = rs.getString("encrypted_patient_data_backup");
                    info.backupCreatedAt = rs.getTimestamp("backup_created_at");

                    // Patient info
                    info.patientFullName = rs.getString("full_name");
                    info.patientDateOfBirth = rs.getDate("date_of_birth");
                    info.patientGender = rs.getString("gender");
                    info.patientPhone = rs.getString("phone");
                    info.patientAddress = rs.getString("address");
                    info.patientIdCardNumber = rs.getString("id_card_number");
                    info.patientInsuranceNumber = rs.getString("insurance_number");

                    return info;
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error getting card info: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Lấy danh sách thẻ theo trạng thái (V3)
     * 
     * @param status Trạng thái thẻ (ACTIVE, LOST, REVOKED, EXPIRED, PENDING) hoặc
     *               null để lấy tất cả
     * @return Danh sách CardInfo
     */
    public static java.util.List<CardInfo> getCardsByStatus(String status) {
        java.util.List<CardInfo> cards = new java.util.ArrayList<>();

        String sql;
        if (status == null || status.isEmpty()) {
            sql = "SELECT uc.*, p.full_name, p.date_of_birth, p.gender, p.phone, " +
                    "p.address, p.id_card_number, p.insurance_number " +
                    "FROM user_cards uc " +
                    "LEFT JOIN patients p ON uc.patient_id = p.patient_id " +
                    "ORDER BY uc.created_at DESC";
        } else {
            sql = "SELECT uc.*, p.full_name, p.date_of_birth, p.gender, p.phone, " +
                    "p.address, p.id_card_number, p.insurance_number " +
                    "FROM user_cards uc " +
                    "LEFT JOIN patients p ON uc.patient_id = p.patient_id " +
                    "WHERE uc.status = ? " +
                    "ORDER BY uc.created_at DESC";
        }

        try (Connection conn = getConnection();
                PreparedStatement pst = conn.prepareStatement(sql)) {

            if (status != null && !status.isEmpty()) {
                pst.setString(1, status);
            }

            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    CardInfo info = new CardInfo();
                    info.cardId = rs.getBytes("card_id");
                    info.patientId = rs.getString("patient_id");
                    info.status = rs.getString("status");
                    info.expiresAt = rs.getTimestamp("expires_at");
                    info.issuedAt = rs.getTimestamp("issued_at");
                    info.issuedByAdminId = (Integer) rs.getObject("issued_by_admin_id");
                    info.lastUpdatedAt = rs.getTimestamp("last_updated_at");
                    info.createdAt = rs.getTimestamp("created_at");
                    info.notes = rs.getString("notes");
                    info.encryptedPatientDataBackup = rs.getString("encrypted_patient_data_backup");
                    info.backupCreatedAt = rs.getTimestamp("backup_created_at");

                    // Patient info
                    info.patientFullName = rs.getString("full_name");
                    info.patientDateOfBirth = rs.getDate("date_of_birth");
                    info.patientGender = rs.getString("gender");
                    info.patientPhone = rs.getString("phone");
                    info.patientAddress = rs.getString("address");
                    info.patientIdCardNumber = rs.getString("id_card_number");
                    info.patientInsuranceNumber = rs.getString("insurance_number");

                    cards.add(info);
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error getting cards by status: " + e.getMessage());
            e.printStackTrace();
        }

        return cards;
    }

    /**
     * Inner class để lưu thông tin thẻ (V3)
     */
    public static class CardInfo {
        // Card info
        public byte[] cardId;
        public String patientId;
        public String status;
        public java.sql.Timestamp expiresAt;
        public java.sql.Timestamp issuedAt;
        public Integer issuedByAdminId;
        public java.sql.Timestamp lastUpdatedAt;
        public java.sql.Timestamp createdAt;
        public String notes;
        public String encryptedPatientDataBackup;
        public java.sql.Timestamp backupCreatedAt;

        // Patient info (joined from patients table)
        public String patientFullName;
        public java.sql.Date patientDateOfBirth;
        public String patientGender;
        public String patientPhone;
        public String patientAddress;
        public String patientIdCardNumber;
        public String patientInsuranceNumber;

        @Override
        public String toString() {
            return "CardInfo{" +
                    "cardId=" + DatabaseConnection.bytesToHex(cardId) +
                    ", patientId='" + patientId + '\'' +
                    ", patientFullName='" + patientFullName + '\'' +
                    ", status='" + status + '\'' +
                    ", issuedAt=" + issuedAt +
                    '}';
        }
    }
}
