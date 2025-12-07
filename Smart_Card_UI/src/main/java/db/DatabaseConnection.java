package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.postgresql.util.PGobject;
import com.google.gson.Gson;

/**
 * DatabaseConnection - Quản lý kết nối Supabase
 * Tham khảo từ SmartCard_Old, giữ nguyên pattern connection pooler
 */
public class DatabaseConnection {

    // Connection Pooler - Transaction Mode
    // Sử dụng pooler thay vì direct connection để tránh lỗi UnknownHostException
    // Host: aws-1-ap-southeast-1.pooler.supabase.com
    // Port: 6543 (Pooler port)
    // User: postgres.rnvqcxqbripfkcckhgkl
    // Database: postgres
    private static final String URL = "jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:6543/postgres?user=postgres.rnvqcxqbripfkcckhgkl&password=a4fVpu2p0YGto65G&sslmode=require";

    /**
     * Lấy connection từ pool
     */
    public static Connection getConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        // User và password đã được đưa vào URL, không cần truyền riêng
        return DriverManager.getConnection(URL);
    }

    /**
     * Helper method để chuyển byte[] sang hex string
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // ================== DEPRECATED METHODS (V2 - Đã xóa bảng admin_cards) ==================
    // Các methods sau đây đã không còn dùng vì bảng admin_cards đã bị xóa trong V3
    // Giữ lại để tránh lỗi compile, nhưng sẽ throw exception nếu được gọi
    
    /**
     * @deprecated V2 method - Bảng admin_cards đã bị xóa trong V3
     * Admin không cần thẻ nữa, sử dụng admin với username/password
     */
    @Deprecated
    public static byte[] getAdminPublicKey(byte[] cardIdAdmin) {
        throw new UnsupportedOperationException("getAdminPublicKey: Bảng admin_cards đã bị xóa trong V3. Sử dụng admin thay thế.");
    }

    /**
     * @deprecated V2 method - Bảng admin_cards đã bị xóa trong V3
     * Admin không cần thẻ nữa, sử dụng admin với username/password
     */
    @Deprecated
    public static boolean saveAdminCard(byte[] cardId, byte[] pkAdmin) {
        throw new UnsupportedOperationException("saveAdminCard: Bảng admin_cards đã bị xóa trong V3. Sử dụng admin thay thế.");
    }

    // ================== DEPRECATED METHODS (V2 - Đã xóa cột pk_user) ==================
    // Cột pk_user đã bị xóa trong V3, không còn lưu public key trong DB
    
    /**
     * @deprecated V2 method - Cột pk_user đã bị xóa trong V3
     * Public key không còn lưu trong DB, chỉ có trong thẻ
     */
    @Deprecated
    public static byte[] getUserPublicKey(byte[] cardIdUser) {
        throw new UnsupportedOperationException("getUserPublicKey: Cột pk_user đã bị xóa trong V3. Public key chỉ có trong thẻ.");
    }

    /**
     * Lưu hoặc cập nhật thông tin bệnh nhân (V3)
     * @param patientId Mã bệnh nhân
     * @param fullName Họ tên
     * @param dateOfBirth Ngày sinh (format: DD/MM/YYYY)
     * @param address Địa chỉ/Quê quán
     * @param insuranceNumber Mã BHYT
     * @return true nếu thành công
     */
    public static boolean savePatient(String patientId, String fullName, String dateOfBirth, String address, String insuranceNumber) {
        String sql = "INSERT INTO patients (patient_id, full_name, date_of_birth, address, insurance_number, created_at, updated_at) " +
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
     * @param cardId Card ID
     * @param patientId Mã bệnh nhân
     * @param adminId Admin phát hành
     * @return true nếu thành công
     */
    public static boolean saveUserCard(byte[] cardId, String patientId, Integer adminId) {
        // First, ensure patient exists (check if patient_id exists in patients table)
        String checkSql = "SELECT COUNT(*) FROM patients WHERE patient_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement checkPst = conn.prepareStatement(checkSql)) {
            
            checkPst.setString(1, patientId);
            try (ResultSet rs = checkPst.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // Patient does not exist - this should not happen if savePatient was called first
                    System.err.println("[DatabaseConnection] Warning: Patient " + patientId + " does not exist in patients table!");
                    System.err.println("[DatabaseConnection] Please create patient record first using savePatient()");
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error checking patient existence: " + e.getMessage());
            return false;
        }
        
        // Now insert/update user_cards
        String sql = "INSERT INTO user_cards (card_id, patient_id, status, issued_at, issued_by_admin_id, created_at) " +
                     "VALUES (?, ?, 'ACTIVE', NOW(), ?, NOW()) " +
                     "ON CONFLICT (card_id) DO UPDATE SET patient_id = EXCLUDED.patient_id, " +
                     "status = 'ACTIVE', issued_at = NOW(), issued_by_admin_id = EXCLUDED.issued_by_admin_id, " +
                     "last_updated_at = NOW()";
        try (Connection conn = getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setBytes(1, cardId);
            pst.setString(2, patientId);
            pst.setObject(3, adminId);
            
            return pst.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error in saveUserCard: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== DEPRECATED METHODS (V2 - Đã xóa bảng bhyt_info) ==================
    // Bảng bhyt_info đã bị xóa trong V3, thông tin BHYT giờ lưu trong bảng patients
    
    /**
     * @deprecated V2 method - Bảng bhyt_info đã bị xóa trong V3
     * Thông tin BHYT giờ lưu trong bảng patients (insurance_number)
     */
    @Deprecated
    public static BHYTInfo getBHYTInfo(String maBHYT) {
        throw new UnsupportedOperationException("getBHYTInfo: Bảng bhyt_info đã bị xóa trong V3. Sử dụng bảng patients (insurance_number) thay thế.");
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
            
            // Bảng admin_audit_log (giữ nguyên)
            stmt.execute("CREATE TABLE IF NOT EXISTS admin_audit_log (" +
                        "id SERIAL PRIMARY KEY, " +
                        "admin_user_id INTEGER REFERENCES admin(id), " +
                        "action VARCHAR(50) NOT NULL, " +
                        "card_id BYTEA, " +
                        "details JSONB, " +
                        "ip_address VARCHAR(45), " +
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
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_user_id ON admin_audit_log(admin_user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action ON admin_audit_log(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_card_id ON admin_audit_log(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_log(created_at)");
            
            // Indexes cho system_logs
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_card_id ON system_logs(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_action ON system_logs(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON system_logs(created_at)");
            
            // Indexes cho card_status_history
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_card_status_history_card_id ON card_status_history(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_card_status_history_created_at ON card_status_history(created_at)");
            
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
                        "    UPDATE user_cards SET status = p_new_status, last_updated_at = NOW() WHERE card_id = p_card_id; " +
                        "    INSERT INTO card_status_history (card_id, old_status, new_status, changed_by_admin_id, reason) " +
                        "    VALUES (p_card_id, v_old_status, p_new_status, p_admin_id, p_reason); " +
                        "END; " +
                        "$$ LANGUAGE plpgsql");
            
            System.out.println("Đã khởi tạo các bảng thành công (V3)!");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================== ADMIN USERS METHODS (V2 - Admin không cần thẻ) ==================

    /**
     * Xác thực admin user bằng username và password
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
     * @param username Username
     * @param password Plaintext password (sẽ được hash)
     * @param fullName Full name
     * @param email Email (optional)
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

    // ================== ADMIN PIN ENCRYPTION METHODS (V2 - DEPRECATED) ==================
    // V3: PIN_admin_reset được derive động từ K_master và cardID, không lưu trong DB

    /**
     * @deprecated V2 method - V3 không lưu admin PIN trong DB nữa
     * PIN_admin_reset được derive động từ K_master và cardID bằng AdminPinDerivation.deriveAdminResetPIN()
     * 
     * @param cardId Card ID (byte[])
     * @param adminPinEncrypted Admin PIN đã được encrypt (Base64 string)
     * @param keyVersion Version của encryption key
     * @return false (always fails in V3)
     */
    @Deprecated
    public static boolean saveAdminPinEncrypted(byte[] cardId, String adminPinEncrypted, int keyVersion) {
        System.err.println("[DatabaseConnection] WARNING: saveAdminPinEncrypted() is deprecated in V3!");
        System.err.println("[DatabaseConnection] PIN_admin_reset is now derived dynamically from K_master and cardID.");
        System.err.println("[DatabaseConnection] Use AdminPinDerivation.deriveAdminResetPIN(cardId) instead.");
        return false;
    }
    
    /**
     * @deprecated V2 method - V3 không lưu admin PIN trong DB nữa
     * PIN_admin_reset được derive động từ K_master và cardID bằng AdminPinDerivation.deriveAdminResetPIN()
     * 
     * @param cardId Card ID (byte[])
     * @return null (always returns null in V3)
     */
    @Deprecated
    public static String getAdminPinEncrypted(byte[] cardId) {
        System.err.println("[DatabaseConnection] WARNING: getAdminPinEncrypted() is deprecated in V3!");
        System.err.println("[DatabaseConnection] PIN_admin_reset is now derived dynamically from K_master and cardID.");
        System.err.println("[DatabaseConnection] Use AdminPinDerivation.deriveAdminResetPIN(cardId) instead.");
        return null;
    }

    // ================== AUDIT LOG METHODS ==================

    /**
     * Lưu audit log cho admin actions
     * @param adminUserId Admin user ID
     * @param action Action name (e.g., "ISSUE_CARD", "RESET_PIN")
     * @param cardId Card ID (có thể null)
     * @param details JSON details (có thể null)
     * @param ipAddress IP address (có thể null)
     * @return true nếu thành công
     */
    public static boolean saveAdminAuditLog(Integer adminUserId, String action, byte[] cardId, 
                                             String details, String ipAddress) {
        String sql = "INSERT INTO admin_audit_log (admin_user_id, action, card_id, details, ip_address, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, NOW())";
        try (Connection conn = getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setObject(1, adminUserId);
            pst.setString(2, action);
            pst.setBytes(3, cardId);
            
            // Set details as JSONB using PGobject
            // If details is not a valid JSON, wrap it in a JSON object
            if (details != null) {
                String jsonValue = details.trim();
                // Check if it's already a valid JSON (starts with { or [)
                if (!jsonValue.startsWith("{") && !jsonValue.startsWith("[")) {
                    // Wrap string in JSON object using Gson
                    Gson gson = new Gson();
                    Map<String, String> jsonMap = new HashMap<>();
                    jsonMap.put("message", details);
                    jsonValue = gson.toJson(jsonMap);
                }
                
                PGobject jsonObject = new PGobject();
                jsonObject.setType("jsonb");
                jsonObject.setValue(jsonValue);
                pst.setObject(4, jsonObject);
            } else {
                pst.setObject(4, null);
            }
            
            pst.setString(5, ipAddress);
            
            return pst.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DatabaseConnection] Error saving admin audit log: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== V3 METHODS - NEW ==================

    /**
     * Lưu backup ciphertext từ thẻ (V3)
     * Backup dữ liệu đã mã hóa từ thẻ vào database
     * 
     * @param cardId Card ID (byte[])
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
     * Stored procedure tự động cập nhật user_cards.status và ghi vào card_status_history
     * 
     * @param cardId Card ID (byte[])
     * @param newStatus Trạng thái mới (ACTIVE, LOST, REVOKED, EXPIRED, PENDING)
     * @param adminId Admin ID thực hiện thao tác
     * @param reason Lý do thay đổi trạng thái (có thể null)
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
     * @param status Trạng thái thẻ (ACTIVE, LOST, REVOKED, EXPIRED, PENDING) hoặc null để lấy tất cả
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

