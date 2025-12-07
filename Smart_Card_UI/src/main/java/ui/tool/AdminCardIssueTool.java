package ui.tool;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import util.CryptoUtils;
import util.JsonFileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;

/**
 * AdminCardIssueTool - Tool cấp phát thẻ Admin (Đơn giản hóa)
 * JSON đã điền sẵn, chỉ cần 1 nút "Tạo" là xong
 */
public class AdminCardIssueTool extends JFrame {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    
    private JButton btnTao;
    private JButton btnChonFile;
    private JTextArea txtJsonConfig;
    private JTextArea txtLog;
    private JLabel lblStatus;
    private JLabel lblCardId;
    private JLabel lblPublicKey;
    private JLabel lblFilePath;
    
    // JSON config đã điền sẵn
    private AdminCardConfig config;
    private String currentJsonFile;

    public AdminCardIssueTool() {
        initUI();  // Khởi tạo UI trước
        initConfig();  // Sau đó mới load config
    }

    /**
     * Khởi tạo JSON config - Load từ file mặc định
     */
    private void initConfig() {
        // Load từ file mặc định
        String defaultFile = JsonFileManager.getAdminDir() + File.separator + "admin_config.json";
        loadConfigFromFile(defaultFile);
    }

    /**
     * Load config từ file JSON
     */
    private boolean loadConfigFromFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                // Nếu file không tồn tại, tạo file mẫu
                if (txtLog != null) {
                    log("File JSON không tồn tại, tạo file mẫu...");
                }
                config = new AdminCardConfig();
                config.pinAdmin = "123456";
                config.salt = bytesToHex(CryptoUtils.generateSalt());
                
                // Lưu file mẫu
                JsonFileManager.saveJson(filePath, config);
                if (txtLog != null) {
                    log("Đã tạo file mẫu: " + filePath);
                }
            } else {
                // Load từ file
                config = JsonFileManager.loadAdminConfig(filePath, AdminCardConfig.class);
                if (config == null) {
                    if (txtLog != null) {
                        log("LỖI: Không thể đọc file JSON!");
                    }
                    return false;
                }
                if (txtLog != null) {
                    log("Đã load config từ: " + filePath);
                }
            }
            
            currentJsonFile = filePath;
            if (txtJsonConfig != null) {
                updateJsonDisplay();
            }
            if (lblFilePath != null) {
                lblFilePath.setText("File: " + filePath);
            }
            return true;
        } catch (Exception e) {
            if (txtLog != null) {
                log("LỖI khi load config: " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
    }

    private void initUI() {
        setTitle("Công Cụ Cấp Phát Thẻ Admin");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 800);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 102, 153));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("CÔNG CỤ CẤP PHÁT THẺ ADMIN");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel trên: JSON Config
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Cấu hình JSON (load từ file)"));
        
        // Panel chọn file
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnChonFile = new JButton("Chọn file JSON...");
        btnChonFile.setPreferredSize(new Dimension(150, 30));
        filePanel.add(btnChonFile);
        lblFilePath = new JLabel("File: " + (currentJsonFile != null ? currentJsonFile : "Chưa chọn"));
        lblFilePath.setFont(new Font("Arial", Font.PLAIN, 11));
        filePanel.add(lblFilePath);
        configPanel.add(filePanel, BorderLayout.NORTH);
        
        txtJsonConfig = new JTextArea(8, 80);
        txtJsonConfig.setEditable(false);
        txtJsonConfig.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtJsonConfig.setBackground(new Color(245, 245, 245));
        updateJsonDisplay();
        JScrollPane jsonScroll = new JScrollPane(txtJsonConfig);
        configPanel.add(jsonScroll, BorderLayout.CENTER);
        
        mainPanel.add(configPanel, BorderLayout.NORTH);

        // Panel giữa: Nút Tạo và Status
        JPanel actionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Hướng dẫn
        JLabel instructionLabel = new JLabel("<html><b>Hướng dẫn:</b> Cắm thẻ Admin vào đầu đọc, sau đó nhấn nút <b>Tạo</b> để khởi tạo thẻ</html>");
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        actionPanel.add(instructionLabel, gbc);

        // Nút Tạo
        btnTao = new JButton("TẠO THẺ ADMIN");
        btnTao.setFont(new Font("Arial", Font.BOLD, 16));
        btnTao.setPreferredSize(new Dimension(250, 50));
        btnTao.setBackground(new Color(0, 153, 0));
        btnTao.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        actionPanel.add(btnTao, gbc);

        // Status
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        actionPanel.add(lblStatus, gbc);

        // Card ID
        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcInfo = new GridBagConstraints();
        gbcInfo.insets = new Insets(5, 5, 5, 5);
        gbcInfo.anchor = GridBagConstraints.WEST;
        
        gbcInfo.gridx = 0;
        gbcInfo.gridy = 0;
        infoPanel.add(new JLabel("Card ID:"), gbcInfo);
        lblCardId = new JLabel("-");
        lblCardId.setFont(new Font("Courier New", Font.PLAIN, 12));
        lblCardId.setBorder(BorderFactory.createLoweredBevelBorder());
        gbcInfo.gridx = 1;
        gbcInfo.fill = GridBagConstraints.HORIZONTAL;
        gbcInfo.weightx = 1.0;
        infoPanel.add(lblCardId, gbcInfo);

        gbcInfo.gridx = 0;
        gbcInfo.gridy = 1;
        gbcInfo.fill = GridBagConstraints.NONE;
        gbcInfo.weightx = 0;
        infoPanel.add(new JLabel("Public Key:"), gbcInfo);
        lblPublicKey = new JLabel("-");
        lblPublicKey.setFont(new Font("Courier New", Font.PLAIN, 11));
        lblPublicKey.setBorder(BorderFactory.createLoweredBevelBorder());
        gbcInfo.gridx = 1;
        gbcInfo.fill = GridBagConstraints.HORIZONTAL;
        gbcInfo.weightx = 1.0;
        infoPanel.add(lblPublicKey, gbcInfo);

        actionPanel.add(infoPanel, gbc);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        actionPanel.add(infoPanel, gbc);

        mainPanel.add(actionPanel, BorderLayout.CENTER);

        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Nhật ký"));
        txtLog = new JTextArea(10, 80);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Courier New", Font.PLAIN, 11));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(txtLog);
        logPanel.add(logScroll, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        // Event handlers
        btnTao.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                taoTheAdmin();
            }
        });

        btnChonFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chonFileJson();
            }
        });

        // Initialize
        cardManager = CardManager.getInstance();
    }

    /**
     * Chọn file JSON để load config
     */
    private void chonFileJson() {
        JFileChooser fileChooser = new JFileChooser(JsonFileManager.getAdminDir());
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
            }

            @Override
            public String getDescription() {
                return "JSON Files (*.json)";
            }
        });
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (loadConfigFromFile(selectedFile.getAbsolutePath())) {
                JOptionPane.showMessageDialog(this, 
                    "Đã load config từ file thành công!", 
                    "Thành công", 
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Lỗi khi load config từ file!", 
                    "Lỗi", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Hàm chính: Tạo thẻ Admin với JSON config đã điền sẵn
     */
    private void taoTheAdmin() {
        try {
            btnTao.setEnabled(false);
            lblStatus.setText("Trạng thái: Đang xử lý...");
            lblStatus.setForeground(Color.BLUE);
            log("=== BẮT ĐẦU TẠO THẺ ADMIN ===");
            
            // Đọc config từ JSON
            if (config == null) {
                log("LỖI: Chưa load config từ JSON!");
                lblStatus.setText("Trạng thái: Chưa load config");
                lblStatus.setForeground(Color.RED);
                btnTao.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Vui lòng chọn file JSON config trước!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String pin = config.pinAdmin;
            if (pin == null || pin.isEmpty()) {
                log("LỖI: PIN Admin không hợp lệ trong JSON!");
                lblStatus.setText("Trạng thái: PIN không hợp lệ");
                lblStatus.setForeground(Color.RED);
                btnTao.setEnabled(true);
                JOptionPane.showMessageDialog(this, "PIN Admin không hợp lệ trong file JSON!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Đọc salt từ JSON (nếu có), nếu không thì sinh mới
            byte[] salt;
            if (config.salt != null && !config.salt.isEmpty()) {
                try {
                    salt = hexToBytes(config.salt);
                    if (salt.length != 16) {
                        log("Salt trong JSON không đúng độ dài (16 bytes), sinh salt mới...");
                        salt = CryptoUtils.generateSalt();
                        config.salt = bytesToHex(salt);
                    }
                } catch (Exception e) {
                    log("Salt trong JSON không hợp lệ, sinh salt mới...");
                    salt = CryptoUtils.generateSalt();
                    config.salt = bytesToHex(salt);
                }
            } else {
                log("Không có salt trong JSON, sinh salt mới...");
                salt = CryptoUtils.generateSalt();
                config.salt = bytesToHex(salt);
            }
            
            log("Cấu hình từ JSON:");
            log("  - File: " + currentJsonFile);
            log("  - PIN Admin: " + pin.length() + " ký tự");
            log("  - Salt: " + bytesToHex(salt));

            // 1. Kết nối với đầu đọc thẻ
            log("Bước 1: Đang kết nối với đầu đọc thẻ...");
            if (!cardManager.connect()) {
                log("LỖI: Không thể kết nối với đầu đọc thẻ!");
                lblStatus.setText("Trạng thái: Lỗi kết nối");
                lblStatus.setForeground(Color.RED);
                btnTao.setEnabled(true);
                return;
            }
            log("✓ Đã kết nối với đầu đọc thẻ");

            // 2. Select AdminApplet
            log("Bước 2: Đang select AdminApplet...");
            String aidHex = bytesToHex(APDUCommands.AID_ADMIN);
            log("  AID: " + aidHex);
            
            if (!cardManager.selectApplet(APDUCommands.AID_ADMIN)) {
                log("LỖI: Không tìm thấy AdminApplet trên thẻ!");
                log("  Có thể applet chưa được cài đặt hoặc AID không khớp!");
                lblStatus.setText("Trạng thái: Không tìm thấy applet");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnTao.setEnabled(true);
                return;
            }
            log("✓ Đã chọn AdminApplet thành công");
            apduCommands = new APDUCommands(cardManager.getChannel());

            // 3. Gửi lệnh INIT_ADMIN_CARD để set PIN hash và salt
            log("Bước 3: Đang khởi tạo PIN trên thẻ...");
            byte[] pinBytes = pin.getBytes();
            log("  INS code: 0x14 (INIT_ADMIN_CARD)");
            log("  PIN length: " + pinBytes.length + " bytes");
            log("  Salt length: " + salt.length + " bytes");
            
            if (!apduCommands.initAdminCard(pinBytes, salt, (msg) -> log("  " + msg))) {
                log("LỖI: Không thể khởi tạo PIN trên thẻ!");
                lblStatus.setText("Trạng thái: Lỗi khởi tạo PIN");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnTao.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Không thể khởi tạo PIN trên thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log("✓ Đã set PIN hash và salt trên thẻ thành công");

            // 4. Đọc Card ID
            log("Bước 4: Đang đọc Card ID...");
            byte[] cardId = apduCommands.getAdminCardId();
            if (cardId == null) {
                log("LỖI: Không thể đọc Card ID!");
                lblStatus.setText("Trạng thái: Lỗi đọc Card ID");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnTao.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Không thể đọc Card ID từ thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String cardIdHex = bytesToHex(cardId);
            log("✓ Card ID: " + cardIdHex);
            lblCardId.setText(cardIdHex);

            // 5. Lấy Public Key từ thẻ
            log("Bước 5: Đang đọc Public Key...");
            byte[] pkRaw = apduCommands.getAdminPublicKey();
            if (pkRaw == null) {
                log("LỖI: Không thể đọc Public Key từ thẻ!");
                lblStatus.setText("Trạng thái: Lỗi đọc Public Key");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnTao.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Không thể đọc Public Key từ thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log("✓ Đã đọc Public Key từ thẻ (" + pkRaw.length + " bytes)");

            // 6. Chuyển đổi Public Key sang DER format
            log("Bước 6: Đang chuyển đổi Public Key sang DER format...");
            byte[] pkDer = convertRSAPublicKeyToDER(pkRaw);
            if (pkDer == null) {
                log("LỖI: Không thể chuyển đổi Public Key!");
                lblStatus.setText("Trạng thái: Lỗi chuyển đổi Public Key");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnTao.setEnabled(true);
                JOptionPane.showMessageDialog(this, "Không thể chuyển đổi Public Key!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log("✓ Đã chuyển đổi Public Key sang DER format (" + pkDer.length + " bytes)");
            String pkHex = bytesToHex(pkDer);
            lblPublicKey.setText(pkHex.substring(0, Math.min(50, pkHex.length())) + "...");

            // 7. Lưu vào Supabase
            log("Bước 7: Đang lưu vào database...");
            if (!DatabaseConnection.saveAdminCard(cardId, pkDer)) {
                log("CẢNH BÁO: Không thể lưu vào database!");
                JOptionPane.showMessageDialog(this, 
                    "Cấp phát thẻ thành công nhưng không thể lưu vào database!\n" +
                    "Vui lòng kiểm tra kết nối database.", 
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            } else {
                log("✓ Đã lưu thông tin vào database thành công");
            }

            // Ngắt kết nối
            cardManager.disconnect();
            log("✓ Đã ngắt kết nối với thẻ");

            log("=== TẠO THẺ ADMIN THÀNH CÔNG ===");
            log("Card ID: " + cardIdHex);
            log("PIN: " + pin.length() + " ký tự");
            log("Public Key đã được lưu vào database");
            log("Config được load từ: " + currentJsonFile);

            lblStatus.setText("Trạng thái: Thành công!");
            lblStatus.setForeground(new Color(0, 153, 0));

            JOptionPane.showMessageDialog(this, 
                "Tạo thẻ Admin thành công!\n\n" +
                "Card ID: " + cardIdHex + "\n" +
                "PIN: " + pin + "\n\n" +
                "Config được load từ:\n" + currentJsonFile + "\n\n" +
                "Vui lòng ghi nhớ PIN này để sử dụng sau này.",
                "Thành công", JOptionPane.INFORMATION_MESSAGE);

            btnTao.setEnabled(true);

        } catch (Exception e) {
            e.printStackTrace();
            log("LỖI: " + e.getMessage());
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            btnTao.setEnabled(true);
            try {
                cardManager.disconnect();
            } catch (Exception ex) {
                // Ignore
            }
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Cập nhật hiển thị JSON config
     */
    private void updateJsonDisplay() {
        if (txtJsonConfig != null && config != null) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(config);
            txtJsonConfig.setText(json);
        }
    }

    /**
     * Chuyển đổi RSA Public Key từ raw format (modLen + modulus + expLen + exponent)
     * sang DER format (X.509)
     */
    private byte[] convertRSAPublicKeyToDER(byte[] raw) {
        try {
            // Parse raw format: modLen(2) + modulus + expLen(2) + exponent
            int offset = 0;
            int modLen = ((raw[offset] & 0xFF) << 8) | (raw[offset + 1] & 0xFF);
            offset += 2;
            byte[] modulus = new byte[modLen];
            System.arraycopy(raw, offset, modulus, 0, modLen);
            offset += modLen;
            
            int expLen = ((raw[offset] & 0xFF) << 8) | (raw[offset + 1] & 0xFF);
            offset += 2;
            byte[] exponent = new byte[expLen];
            System.arraycopy(raw, offset, exponent, 0, expLen);

            // Tạo RSAPublicKeySpec
            java.math.BigInteger mod = new java.math.BigInteger(1, modulus);
            java.math.BigInteger exp = new java.math.BigInteger(1, exponent);
            RSAPublicKeySpec spec = new RSAPublicKeySpec(mod, exp);

            // Tạo PublicKey và export sang DER
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey pk = kf.generatePublic(spec);
            return pk.getEncoded(); // X.509 DER format

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void log(String message) {
        if (txtLog != null) {
            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
            txtLog.append("[" + timestamp + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        } else {
            System.out.println("[LOG] " + message);
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Class chứa cấu hình JSON cho thẻ Admin
     * Được load từ file JSON để nạp vào thẻ
     */
    public static class AdminCardConfig {
        public String pinAdmin;  // PIN Admin (6 chữ số)
        public String salt;       // Salt 16 bytes (hex string, 32 ký tự)
    }

    /**
     * Chuyển đổi hex string sang byte array
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new AdminCardIssueTool().setVisible(true);
            }
        });
    }
}

