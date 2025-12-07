package ui.tool;

import db.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * DatabaseMigrationTool - Công cụ migration database
 * Tạo các bảng cần thiết cho hệ thống Hospital Card
 */
public class DatabaseMigrationTool extends JFrame {
    
    private JTextArea txtLog;
    private JButton btnTestConnection;
    private JButton btnCheckTables;
    private JButton btnShowTables;
    private JButton btnCreateTables;
    private JButton btnDropTables;
    private JLabel lblStatus;
    
    public DatabaseMigrationTool() {
        initUI();
    }
    
    private void initUI() {
        setTitle("Công Cụ Migration Database");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 102, 153));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("CÔNG CỤ MIGRATION DATABASE");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Info panel
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Thông tin"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        JLabel infoLabel = new JLabel("<html><b>Chức năng:</b><br>" +
            "1. Kiểm tra các bảng đã tồn tại<br>" +
            "2. Tạo các bảng nếu chưa có<br>" +
            "3. Xóa và tạo lại các bảng (reset)<br><br>" +
            "<b>Các bảng sẽ được tạo (V3):</b><br>" +
            "admin, patients, system_keys, user_cards (với status, expires_at),<br>" +
            "system_logs, admin_audit_log, card_status_history</html>");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        infoPanel.add(infoLabel, gbc);
        
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        
        // Buttons panel - 2 rows
        JPanel btnPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        btnPanel.setBorder(BorderFactory.createTitledBorder("Thao tác"));
        
        // Row 1
        btnTestConnection = new JButton("Test Connection");
        btnTestConnection.setPreferredSize(new Dimension(150, 40));
        btnTestConnection.setBackground(new Color(0, 153, 0));
        btnTestConnection.setForeground(Color.WHITE);
        btnTestConnection.setFont(new Font("Arial", Font.BOLD, 12));
        btnPanel.add(btnTestConnection);
        
        btnCheckTables = new JButton("Kiểm tra bảng");
        btnCheckTables.setPreferredSize(new Dimension(150, 40));
        btnCheckTables.setBackground(new Color(0, 102, 204));
        btnCheckTables.setForeground(Color.WHITE);
        btnCheckTables.setFont(new Font("Arial", Font.BOLD, 12));
        btnPanel.add(btnCheckTables);
        
        btnShowTables = new JButton("Xem dữ liệu");
        btnShowTables.setPreferredSize(new Dimension(150, 40));
        btnShowTables.setBackground(new Color(255, 153, 0));
        btnShowTables.setForeground(Color.WHITE);
        btnShowTables.setFont(new Font("Arial", Font.BOLD, 12));
        btnPanel.add(btnShowTables);
        
        // Row 2
        btnCreateTables = new JButton("Tạo bảng");
        btnCreateTables.setPreferredSize(new Dimension(150, 40));
        btnCreateTables.setBackground(new Color(51, 153, 255));
        btnCreateTables.setForeground(Color.WHITE);
        btnCreateTables.setFont(new Font("Arial", Font.BOLD, 12));
        btnPanel.add(btnCreateTables);
        
        btnDropTables = new JButton("Xóa và tạo lại");
        btnDropTables.setPreferredSize(new Dimension(150, 40));
        btnDropTables.setBackground(Color.RED);
        btnDropTables.setForeground(Color.WHITE);
        btnDropTables.setFont(new Font("Arial", Font.BOLD, 12));
        btnPanel.add(btnDropTables);
        
        JButton btnClearLog = new JButton("Xóa log");
        btnClearLog.setPreferredSize(new Dimension(150, 40));
        btnClearLog.addActionListener(e -> {
            txtLog.setText("");
            log("Log đã được xóa");
        });
        btnPanel.add(btnClearLog);
        
        // Status
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        lblStatus.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // Log area
        txtLog = new JTextArea(12, 80);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log"));
        scrollPane.setPreferredSize(new Dimension(750, 250));
        
        // Center panel: Buttons + Status
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(btnPanel, BorderLayout.CENTER);
        centerPanel.add(lblStatus, BorderLayout.SOUTH);
        
        // Bottom panel: Log
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Add to main panel
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Event handlers
        btnTestConnection.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testConnection();
            }
        });
        
        btnCheckTables.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkTables();
            }
        });
        
        btnShowTables.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showTablesData();
            }
        });
        
        btnCreateTables.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createTables();
            }
        });
        
        btnDropTables.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                    DatabaseMigrationTool.this,
                    "Bạn có chắc chắn muốn XÓA TẤT CẢ các bảng và tạo lại?\n" +
                    "Dữ liệu hiện tại sẽ bị mất!",
                    "Xác nhận",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    dropAndCreateTables();
                }
            }
        });
    }
    
    private void testConnection() {
        log("=== TEST CONNECTION ===");
        lblStatus.setText("Trạng thái: Đang test connection...");
        lblStatus.setForeground(Color.BLUE);
        
        try {
            long startTime = System.currentTimeMillis();
            Connection conn = DatabaseConnection.getConnection();
            long endTime = System.currentTimeMillis();
            
            if (conn != null && !conn.isClosed()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String dbName = metaData.getDatabaseProductName();
                String dbVersion = metaData.getDatabaseProductVersion();
                String driverName = metaData.getDriverName();
                String driverVersion = metaData.getDriverVersion();
                String url = metaData.getURL();
                
                log("✓ Kết nối thành công!");
                log("  Database: " + dbName + " " + dbVersion);
                log("  Driver: " + driverName + " " + driverVersion);
                log("  URL: " + url);
                log("  Thời gian kết nối: " + (endTime - startTime) + " ms");
                
                conn.close();
                
                lblStatus.setText("Trạng thái: Kết nối thành công");
                lblStatus.setForeground(new Color(0, 153, 0));
                
                JOptionPane.showMessageDialog(this,
                    "Kết nối database thành công!\n\n" +
                    "Database: " + dbName + "\n" +
                    "Version: " + dbVersion,
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE);
            }
            
        } catch (Exception e) {
            log("✗ LỖI KẾT NỐI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi kết nối");
            lblStatus.setForeground(Color.RED);
            
            JOptionPane.showMessageDialog(this,
                "Lỗi kết nối database:\n" + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void checkTables() {
        log("=== KIỂM TRA CÁC BẢNG ===");
        lblStatus.setText("Trạng thái: Đang kiểm tra...");
        lblStatus.setForeground(Color.BLUE);
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            
            String[] tables = {"admin", "patients", "system_keys", "user_cards", 
                               "system_logs", "admin_audit_log", "card_status_history"};
            boolean allExist = true;
            
            for (String tableName : tables) {
                ResultSet rs = metaData.getTables(null, null, tableName, null);
                if (rs.next()) {
                    log("✓ Bảng '" + tableName + "' đã tồn tại");
                    
                    // Đếm số dòng
                    Statement stmt = conn.createStatement();
                    ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
                    if (countRs.next()) {
                        int count = countRs.getInt(1);
                        log("  -> Số dòng: " + count);
                    }
                    countRs.close();
                    stmt.close();
                } else {
                    log("✗ Bảng '" + tableName + "' CHƯA TỒN TẠI");
                    allExist = false;
                }
                rs.close();
            }
            
            conn.close();
            
            if (allExist) {
                log("=== TẤT CẢ CÁC BẢNG ĐÃ TỒN TẠI ===");
                lblStatus.setText("Trạng thái: Tất cả bảng đã tồn tại");
                lblStatus.setForeground(new Color(0, 153, 0));
            } else {
                log("=== MỘT SỐ BẢNG CHƯA TỒN TẠI ===");
                log("Vui lòng nhấn 'Tạo bảng' để tạo các bảng còn thiếu");
                lblStatus.setText("Trạng thái: Một số bảng chưa tồn tại");
                lblStatus.setForeground(Color.ORANGE);
            }
            
        } catch (Exception e) {
            log("LỖI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
        }
    }
    
    private void createTables() {
        log("=== TẠO CÁC BẢNG (V3) ===");
        lblStatus.setText("Trạng thái: Đang tạo bảng...");
        lblStatus.setForeground(Color.BLUE);
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement stmt = conn.createStatement();
            
            // Bảng admin
            log("Đang tạo bảng admin...");
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
            log("✓ Đã tạo bảng admin");
            
            // Bảng patients (V3 - MỚI)
            log("Đang tạo bảng patients...");
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
            log("✓ Đã tạo bảng patients");
            
            // Bảng system_keys (V3 - chỉ lưu metadata, K_master lưu trong .env)
            log("Đang tạo bảng system_keys...");
            stmt.execute("CREATE TABLE IF NOT EXISTS system_keys (" +
                        "id SERIAL PRIMARY KEY, " +
                        "key_name VARCHAR(50) UNIQUE NOT NULL, " +
                        "key_version INTEGER DEFAULT 1, " +
                        "is_active BOOLEAN DEFAULT TRUE, " +
                        "created_at TIMESTAMP DEFAULT NOW(), " +
                        "description TEXT, " +
                        "rotation_policy TEXT, " +
                        "last_rotated_at TIMESTAMP)");
            log("✓ Đã tạo bảng system_keys");
            
            // Bảng user_cards (V3 - cập nhật lớn)
            log("Đang tạo bảng user_cards...");
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
            log("✓ Đã tạo bảng user_cards");
            
            // Bảng system_logs
            log("Đang tạo bảng system_logs...");
            stmt.execute("CREATE TABLE IF NOT EXISTS system_logs (" +
                        "id SERIAL PRIMARY KEY, " +
                        "action VARCHAR(100), " +
                        "card_id BYTEA, " +
                        "details TEXT, " +
                        "created_at TIMESTAMP DEFAULT NOW())");
            log("✓ Đã tạo bảng system_logs");
            
            // Bảng admin_audit_log
            log("Đang tạo bảng admin_audit_log...");
            stmt.execute("CREATE TABLE IF NOT EXISTS admin_audit_log (" +
                        "id SERIAL PRIMARY KEY, " +
                        "admin_user_id INTEGER REFERENCES admin(id), " +
                        "action VARCHAR(50) NOT NULL, " +
                        "card_id BYTEA, " +
                        "details JSONB, " +
                        "ip_address VARCHAR(45), " +
                        "created_at TIMESTAMP DEFAULT NOW())");
            log("✓ Đã tạo bảng admin_audit_log");
            
            // Bảng card_status_history (V3 - MỚI)
            log("Đang tạo bảng card_status_history...");
            stmt.execute("CREATE TABLE IF NOT EXISTS card_status_history (" +
                        "id SERIAL PRIMARY KEY, " +
                        "card_id BYTEA NOT NULL, " +
                        "old_status VARCHAR(20), " +
                        "new_status VARCHAR(20) NOT NULL, " +
                        "changed_by_admin_id INTEGER REFERENCES admin(id), " +
                        "reason TEXT, " +
                        "created_at TIMESTAMP DEFAULT NOW())");
            log("✓ Đã tạo bảng card_status_history");
            
            // Tạo indexes
            log("");
            log("Đang tạo indexes...");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_username ON admin(username)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_is_active ON admin(is_active)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_patients_full_name ON patients(full_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_patients_id_card_number ON patients(id_card_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_patients_insurance_number ON patients(insurance_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_patient_id ON user_cards(patient_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_status ON user_cards(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_expires_at ON user_cards(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_issued_at ON user_cards(issued_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_cards_created_at ON user_cards(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_user_id ON admin_audit_log(admin_user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action ON admin_audit_log(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_card_id ON admin_audit_log(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_log(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_card_id ON system_logs(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_action ON system_logs(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_system_logs_created_at ON system_logs(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_card_status_history_card_id ON card_status_history(card_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_card_status_history_created_at ON card_status_history(created_at)");
            log("✓ Đã tạo các indexes");
            
            // Tạo stored procedure
            log("");
            log("Đang tạo stored procedure update_card_status...");
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
            log("✓ Đã tạo stored procedure update_card_status");
            
            stmt.close();
            conn.close();
            
            log("=== TẠO BẢNG THÀNH CÔNG ===");
            lblStatus.setText("Trạng thái: Đã tạo bảng thành công");
            lblStatus.setForeground(new Color(0, 153, 0));
            
            JOptionPane.showMessageDialog(this, 
                "Đã tạo các bảng thành công!",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            log("LỖI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            
            JOptionPane.showMessageDialog(this, 
                "Lỗi khi tạo bảng: " + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void dropAndCreateTables() {
        log("=== XÓA VÀ TẠO LẠI CÁC BẢNG (V3) ===");
        lblStatus.setText("Trạng thái: Đang xóa và tạo lại...");
        lblStatus.setForeground(Color.BLUE);
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement stmt = conn.createStatement();
            
            // Xóa các bảng V2 cũ trước
            log("Đang xóa các bảng V2 cũ...");
            String[] oldV2Tables = {"admin_cards", "bhyt_info"};
            for (String tableName : oldV2Tables) {
                try {
                    stmt.execute("DROP TABLE IF EXISTS " + tableName + " CASCADE");
                    log("✓ Đã xóa bảng V2 cũ: " + tableName);
                } catch (Exception e) {
                    log("⚠ Không thể xóa bảng " + tableName + ": " + e.getMessage());
                }
            }
            
            log("");
            log("Đang xóa các bảng V3...");
            String[] tables = {"card_status_history", "admin_audit_log", "system_logs", 
                               "user_cards", "patients", "system_keys", "admin"};
            
            // Xóa các bảng (theo thứ tự ngược để tránh lỗi foreign key)
            for (String tableName : tables) {
                log("Đang xóa bảng " + tableName + "...");
                try {
                    stmt.execute("DROP TABLE IF EXISTS " + tableName + " CASCADE");
                    log("✓ Đã xóa bảng " + tableName);
                } catch (Exception e) {
                    log("⚠ Không thể xóa bảng " + tableName + ": " + e.getMessage());
                }
            }
            
            log("");
            log("Đang tạo lại các bảng (V3)...");
            
            // Bảng admin
            log("Đang tạo bảng admin...");
            stmt.execute("CREATE TABLE admin (" +
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
            log("✓ Đã tạo bảng admin");
            
            // Bảng patients (V3 - MỚI)
            log("Đang tạo bảng patients...");
            stmt.execute("CREATE TABLE patients (" +
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
            log("✓ Đã tạo bảng patients");
            
            // Bảng system_keys (V3 - chỉ lưu metadata)
            log("Đang tạo bảng system_keys...");
            stmt.execute("CREATE TABLE system_keys (" +
                        "id SERIAL PRIMARY KEY, " +
                        "key_name VARCHAR(50) UNIQUE NOT NULL, " +
                        "key_version INTEGER DEFAULT 1, " +
                        "is_active BOOLEAN DEFAULT TRUE, " +
                        "created_at TIMESTAMP DEFAULT NOW(), " +
                        "description TEXT, " +
                        "rotation_policy TEXT, " +
                        "last_rotated_at TIMESTAMP)");
            log("✓ Đã tạo bảng system_keys");
            
            // Bảng user_cards (V3 - cập nhật lớn)
            log("Đang tạo bảng user_cards...");
            stmt.execute("CREATE TABLE user_cards (" +
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
            log("✓ Đã tạo bảng user_cards");
            
            // Bảng system_logs
            log("Đang tạo bảng system_logs...");
            stmt.execute("CREATE TABLE system_logs (" +
                        "id SERIAL PRIMARY KEY, " +
                        "action VARCHAR(100), " +
                        "card_id BYTEA, " +
                        "details TEXT, " +
                        "created_at TIMESTAMP DEFAULT NOW())");
            log("✓ Đã tạo bảng system_logs");
            
            // Bảng admin_audit_log
            log("Đang tạo bảng admin_audit_log...");
            stmt.execute("CREATE TABLE admin_audit_log (" +
                        "id SERIAL PRIMARY KEY, " +
                        "admin_user_id INTEGER REFERENCES admin(id), " +
                        "action VARCHAR(50) NOT NULL, " +
                        "card_id BYTEA, " +
                        "details JSONB, " +
                        "ip_address VARCHAR(45), " +
                        "created_at TIMESTAMP DEFAULT NOW())");
            log("✓ Đã tạo bảng admin_audit_log");
            
            // Bảng card_status_history (V3 - MỚI)
            log("Đang tạo bảng card_status_history...");
            stmt.execute("CREATE TABLE card_status_history (" +
                        "id SERIAL PRIMARY KEY, " +
                        "card_id BYTEA NOT NULL, " +
                        "old_status VARCHAR(20), " +
                        "new_status VARCHAR(20) NOT NULL, " +
                        "changed_by_admin_id INTEGER REFERENCES admin(id), " +
                        "reason TEXT, " +
                        "created_at TIMESTAMP DEFAULT NOW())");
            log("✓ Đã tạo bảng card_status_history");
            
            // Tạo indexes
            log("");
            log("Đang tạo indexes...");
            stmt.execute("CREATE INDEX idx_admin_username ON admin(username)");
            stmt.execute("CREATE INDEX idx_admin_is_active ON admin(is_active)");
            stmt.execute("CREATE INDEX idx_patients_full_name ON patients(full_name)");
            stmt.execute("CREATE INDEX idx_patients_id_card_number ON patients(id_card_number)");
            stmt.execute("CREATE INDEX idx_patients_insurance_number ON patients(insurance_number)");
            stmt.execute("CREATE INDEX idx_user_cards_patient_id ON user_cards(patient_id)");
            stmt.execute("CREATE INDEX idx_user_cards_status ON user_cards(status)");
            stmt.execute("CREATE INDEX idx_user_cards_expires_at ON user_cards(expires_at)");
            stmt.execute("CREATE INDEX idx_user_cards_issued_at ON user_cards(issued_at)");
            stmt.execute("CREATE INDEX idx_user_cards_created_at ON user_cards(created_at)");
            stmt.execute("CREATE INDEX idx_admin_audit_log_admin_user_id ON admin_audit_log(admin_user_id)");
            stmt.execute("CREATE INDEX idx_admin_audit_log_action ON admin_audit_log(action)");
            stmt.execute("CREATE INDEX idx_admin_audit_log_card_id ON admin_audit_log(card_id)");
            stmt.execute("CREATE INDEX idx_admin_audit_log_created_at ON admin_audit_log(created_at)");
            stmt.execute("CREATE INDEX idx_system_logs_card_id ON system_logs(card_id)");
            stmt.execute("CREATE INDEX idx_system_logs_action ON system_logs(action)");
            stmt.execute("CREATE INDEX idx_system_logs_created_at ON system_logs(created_at)");
            stmt.execute("CREATE INDEX idx_card_status_history_card_id ON card_status_history(card_id)");
            stmt.execute("CREATE INDEX idx_card_status_history_created_at ON card_status_history(created_at)");
            log("✓ Đã tạo các indexes");
            
            // Tạo stored procedure
            log("");
            log("Đang tạo stored procedure update_card_status...");
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
            log("✓ Đã tạo stored procedure update_card_status");
            
            stmt.close();
            conn.close();
            
            log("=== XÓA VÀ TẠO LẠI THÀNH CÔNG ===");
            lblStatus.setText("Trạng thái: Đã xóa và tạo lại thành công");
            lblStatus.setForeground(new Color(0, 153, 0));
            
            JOptionPane.showMessageDialog(this, 
                "Đã xóa và tạo lại các bảng thành công!",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            log("LỖI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            
            JOptionPane.showMessageDialog(this, 
                "Lỗi khi xóa và tạo lại bảng: " + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void showTablesData() {
        log("=== XEM DỮ LIỆU CÁC BẢNG ===");
        lblStatus.setText("Trạng thái: Đang tải dữ liệu...");
        lblStatus.setForeground(Color.BLUE);
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement stmt = conn.createStatement();
            
            // Tạo dialog để hiển thị dữ liệu
            JDialog dataDialog = new JDialog(this, "Dữ liệu các bảng", true);
            dataDialog.setSize(1000, 700);
            dataDialog.setLocationRelativeTo(this);
            
            JTabbedPane tabbedPane = new JTabbedPane();
            
            // Tab cho từng bảng
            String[] tables = {"admin", "patients", "system_keys", "user_cards", 
                              "system_logs", "admin_audit_log", "card_status_history"};
            
            for (String tableName : tables) {
                try {
                    // Kiểm tra bảng có tồn tại không
                    ResultSet tableCheck = conn.getMetaData().getTables(null, null, tableName, null);
                    if (!tableCheck.next()) {
                        JTextArea noTableArea = new JTextArea("Bảng '" + tableName + "' chưa tồn tại!");
                        noTableArea.setEditable(false);
                        tabbedPane.addTab(tableName, new JScrollPane(noTableArea));
                        tableCheck.close();
                        continue;
                    }
                    tableCheck.close();
                    
                    // Đếm số dòng
                    ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
                    int totalRows = 0;
                    if (countRs.next()) {
                        totalRows = countRs.getInt(1);
                    }
                    countRs.close();
                    
                    // Lấy dữ liệu
                    ResultSet dataRs = stmt.executeQuery("SELECT * FROM " + tableName + " ORDER BY created_at DESC LIMIT 100");
                    
                    // Tạo table model
                    java.sql.ResultSetMetaData metaData = dataRs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    
                    // Tên cột
                    String[] columnNames = new String[columnCount];
                    for (int i = 1; i <= columnCount; i++) {
                        columnNames[i - 1] = metaData.getColumnName(i);
                    }
                    
                    // Dữ liệu
                    java.util.Vector<Object[]> dataVector = new java.util.Vector<>();
                    while (dataRs.next()) {
                        Object[] row = new Object[columnCount];
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = dataRs.getObject(i);
                            if (value instanceof byte[]) {
                                // Hiển thị byte[] dạng hex
                                byte[] bytes = (byte[]) value;
                                StringBuilder hex = new StringBuilder();
                                for (byte b : bytes) {
                                    hex.append(String.format("%02X", b));
                                }
                                row[i - 1] = hex.toString();
                            } else {
                                row[i - 1] = value;
                            }
                        }
                        dataVector.add(row);
                    }
                    dataRs.close();
                    
                    // Chuyển Vector thành Object[][]
                    Object[][] data = new Object[dataVector.size()][];
                    for (int i = 0; i < dataVector.size(); i++) {
                        data[i] = dataVector.get(i);
                    }
                    
                    // Tạo JTable
                    javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(data, columnNames) {
                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return false;
                        }
                    };
                    JTable table = new JTable(model);
                    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                    table.setFont(new Font("Courier New", Font.PLAIN, 11));
                    
                    JScrollPane scrollPane = new JScrollPane(table);
                    
                    // Panel với thông tin
                    JPanel panel = new JPanel(new BorderLayout());
                    JLabel infoLabel = new JLabel("<html><b>Tổng số dòng:</b> " + totalRows + 
                        (totalRows > 100 ? " (chỉ hiển thị 100 dòng đầu)" : "") + "</html>");
                    infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                    panel.add(infoLabel, BorderLayout.NORTH);
                    panel.add(scrollPane, BorderLayout.CENTER);
                    
                    tabbedPane.addTab(tableName + " (" + totalRows + ")", panel);
                    
                    log("✓ Đã tải dữ liệu bảng '" + tableName + "' (" + totalRows + " dòng)");
                    
                } catch (Exception e) {
                    JTextArea errorArea = new JTextArea("Lỗi khi tải dữ liệu: " + e.getMessage());
                    errorArea.setEditable(false);
                    tabbedPane.addTab(tableName, new JScrollPane(errorArea));
                    log("✗ Lỗi khi tải dữ liệu bảng '" + tableName + "': " + e.getMessage());
                }
            }
            
            stmt.close();
            conn.close();
            
            dataDialog.add(tabbedPane);
            dataDialog.setVisible(true);
            
            log("=== HOÀN TẤT ===");
            lblStatus.setText("Trạng thái: Đã tải dữ liệu");
            lblStatus.setForeground(new Color(0, 153, 0));
            
        } catch (Exception e) {
            log("LỖI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            
            JOptionPane.showMessageDialog(this,
                "Lỗi khi tải dữ liệu: " + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void log(String message) {
        String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
        txtLog.append("[" + timestamp + "] " + message + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new DatabaseMigrationTool().setVisible(true);
            }
        });
    }
}

