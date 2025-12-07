package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import util.AdminPinDerivation;
import util.EnvFileLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ResetPinPanel - Panel reset PIN User
 * V3: Tự động derive Admin PIN từ K_master và cardID, không lưu trong DB
 */
public class ResetPinPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    
    private JTextField txtCardIdUser;
    private JPasswordField txtPinUserNew;
    private JButton btnResetPin;
    private JButton btnLoadCard;
    private JTextArea txtLog;

    public ResetPinPanel(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        // Load .env file on initialization
        EnvFileLoader.load();
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Reset PIN User (đơn giản hóa)"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Thông tin hướng dẫn
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JLabel lblInfo = new JLabel("<html><b>Hướng dẫn (V3):</b><br>" +
            "1. Cắm thẻ User cần reset PIN vào đầu đọc<br>" +
            "2. Nhấn 'Đọc thông tin thẻ' để load Card ID<br>" +
            "3. Hệ thống sẽ tự động derive Admin PIN từ K_master và Card ID<br>" +
            "4. Nhập PIN User mới (mặc định: <b>123456</b>)<br>" +
            "5. Nhấn 'Reset PIN' để thực hiện</html>");
        lblInfo.setForeground(new Color(0, 102, 153));
        formPanel.add(lblInfo, gbc);
        
        row++;
        gbc.gridwidth = 1;

        // Card ID User
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Card ID User:"), gbc);
        JPanel cardIdPanel = new JPanel(new BorderLayout());
        txtCardIdUser = new JTextField(35);
        txtCardIdUser.setEditable(false);
        txtCardIdUser.setBackground(new Color(240, 240, 240));
        cardIdPanel.add(txtCardIdUser, BorderLayout.CENTER);
        
        btnLoadCard = new JButton("Đọc thông tin thẻ");
        btnLoadCard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadCardInfo();
            }
        });
        cardIdPanel.add(btnLoadCard, BorderLayout.EAST);
        gbc.gridx = 1;
        formPanel.add(cardIdPanel, gbc);

        // PIN User mới (mặc định = 123456)
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("PIN User mới (mặc định: 123456):"), gbc);
        txtPinUserNew = new JPasswordField(20);
        txtPinUserNew.setText("123456"); // Đặt mặc định
        gbc.gridx = 1;
        formPanel.add(txtPinUserNew, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        btnResetPin = new JButton("Reset PIN");
        btnResetPin.setBackground(new Color(0, 153, 102));
        btnResetPin.setForeground(Color.WHITE);
        btnResetPin.setFont(new Font("Arial", Font.BOLD, 16));
        btnResetPin.setPreferredSize(new Dimension(150, 40));
        btnPanel.add(btnResetPin);

        // Log area
        txtLog = new JTextArea(10, 50);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log"));

        // Event handlers
        btnResetPin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetPin();
            }
        });

        add(formPanel, BorderLayout.NORTH);
        add(btnPanel, BorderLayout.CENTER);
        add(scrollLog, BorderLayout.SOUTH);
    }

    /**
     * Load thông tin thẻ (Card ID)
     */
    private void loadCardInfo() {
        try {
            txtLog.setText("");
            log("=== ĐỌC THÔNG TIN THẺ ===\n");
            
            // Kiểm tra kết nối thẻ
            if (!cardManager.isConnected()) {
                if (!cardManager.connect()) {
                    JOptionPane.showMessageDialog(this,
                        "Không thể kết nối với đầu đọc thẻ!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            
            // Cập nhật channel cho APDUCommands
            apduCommands.setChannel(cardManager.getChannel());
            
            log("Bước 1: Select UserApplet...");
            if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
                JOptionPane.showMessageDialog(this,
                    "Vui lòng cắm thẻ User vào đầu đọc!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                log("LỖI: Không thể select UserApplet");
                return;
            }
            log("✓ Select UserApplet thành công");
            
            log("\nBước 2: Đọc Card ID từ thẻ...");
            byte[] cardIdOnCard = apduCommands.getCardId();
            if (cardIdOnCard == null || isCardIdEmpty(cardIdOnCard)) {
                JOptionPane.showMessageDialog(this,
                    "Thẻ chưa được phát hành!\n\n" +
                    "CardId hiện tại là rỗng (toàn số 0).\n" +
                    "Vui lòng phát hành thẻ trước khi reset PIN.",
                    "Thẻ chưa được phát hành", JOptionPane.WARNING_MESSAGE);
                log("LỖI: Thẻ chưa được phát hành");
                txtCardIdUser.setText("");
                return;
            }
            
            String cardIdHex = bytesToHex(cardIdOnCard);
            log("✓ Card ID: " + cardIdHex);
            txtCardIdUser.setText(cardIdHex);
            
            // V3: Derive Admin PIN từ K_master và cardID
            log("\nBước 3: Derive Admin PIN từ K_master và cardID...");
            try {
                String adminPin = AdminPinDerivation.deriveAdminResetPIN(cardIdOnCard);
                log("✓ Admin PIN đã được derive: " + adminPin);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Lỗi khi derive Admin PIN!\n\n" +
                    "Card ID: " + cardIdHex + "\n\n" +
                    "Vui lòng kiểm tra K_MASTER environment variable.\n\n" +
                    "Lỗi: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                log("LỖI: Không thể derive Admin PIN - " + e.getMessage());
                return;
            }
            
            JOptionPane.showMessageDialog(this,
                "Đã đọc thông tin thẻ thành công!\n\n" +
                "Card ID: " + cardIdHex + "\n" +
                "Admin PIN đã được derive từ K_master.",
                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            log("\nEXCEPTION: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Lỗi: " + e.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Reset PIN User - Tự động lấy Admin PIN từ database
     */
    private void resetPin() {
        try {
            txtLog.setText("");
            log("=== BẮT ĐẦU RESET PIN USER (INS 0xA2) ===\n");
            
            // Validate Card ID
            String cardIdHex = txtCardIdUser.getText().trim();
            if (cardIdHex.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Vui lòng đọc thông tin thẻ trước!",
                    "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            byte[] cardId = hexStringToBytes(cardIdHex);
            if (cardId == null || cardId.length != 16) {
                JOptionPane.showMessageDialog(this,
                    "Card ID không hợp lệ!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            String pinUserNew = new String(txtPinUserNew.getPassword());
            if (pinUserNew.isEmpty()) {
                pinUserNew = "123456";
                txtPinUserNew.setText("123456");
                log("PIN User mới trống, sử dụng mặc định: 123456");
            }
            
            // Validate PIN User New (must be 6 digits)
            if (pinUserNew.length() != 6 || !pinUserNew.matches("^[0-9]+$")) {
                JOptionPane.showMessageDialog(this,
                    "PIN User mới phải là 6 chữ số!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 1. Select UserApplet
            log("Bước 1: Select UserApplet...");
            if (!cardManager.isConnected()) {
                if (!cardManager.connect()) {
                    JOptionPane.showMessageDialog(this,
                        "Không thể kết nối với đầu đọc thẻ!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            
            // Cập nhật channel cho APDUCommands
            apduCommands.setChannel(cardManager.getChannel());
            
            if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
                JOptionPane.showMessageDialog(this,
                    "Vui lòng cắm thẻ User vào đầu đọc!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                log("LỖI: Không thể select UserApplet");
                return;
            }
            log("✓ Select UserApplet thành công");
            
            // 2. Đọc Card ID từ thẻ (V3: dùng getStatus())
            log("\nBước 2: Đọc Card ID từ thẻ...");
            byte[] cardIdOnCard = apduCommands.getStatus(); // V3: Use getStatus() instead of getCardId()
            if (cardIdOnCard == null || cardIdOnCard.length != 16) {
                JOptionPane.showMessageDialog(this,
                    "Thẻ chưa được phát hành hoặc không thể đọc Card ID!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                log("LỖI: Không thể đọc Card ID từ thẻ");
                return;
            }
            
            String cardIdOnCardHex = bytesToHex(cardIdOnCard);
            log("✓ Card ID trên thẻ: " + cardIdOnCardHex);
            
            // Verify Card ID trên thẻ khớp với Card ID trong form (nếu có)
            if (!cardIdHex.isEmpty() && !cardIdHex.equalsIgnoreCase(cardIdOnCardHex)) {
                int choice = JOptionPane.showConfirmDialog(this,
                    "Card ID không khớp!\n\n" +
                    "Card ID trên form: " + cardIdHex + "\n" +
                    "Card ID trên thẻ: " + cardIdOnCardHex + "\n\n" +
                    "Bạn có muốn tiếp tục với Card ID trên thẻ?",
                    "Cảnh báo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
                // Update form với cardID từ thẻ
                txtCardIdUser.setText(cardIdOnCardHex);
                cardIdHex = cardIdOnCardHex;
            }
            
            // 3. Derive Admin PIN từ K_master và cardID trên thẻ (V3)
            log("\nBước 3: Derive Admin PIN từ K_master và cardID trên thẻ...");
            String adminPin;
            try {
                EnvFileLoader.load();
                adminPin = AdminPinDerivation.deriveAdminResetPIN(cardIdOnCard); // Dùng cardID từ thẻ!
                log("✓ Admin PIN đã được derive: " + adminPin);
                log("  - Card ID dùng để derive: " + cardIdOnCardHex);
            } catch (Exception e) {
                log("LỖI: Không thể derive Admin PIN - " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Lỗi khi derive Admin PIN!\n\n" +
                    "Card ID trên thẻ: " + cardIdOnCardHex + "\n\n" +
                    "Vui lòng kiểm tra K_MASTER environment variable.\n\n" +
                    "Lỗi: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 4. Gửi lệnh ADMIN_RESET_PIN (V3 - INS 0x05)
            log("\nBước 4: Gửi lệnh ADMIN_RESET_PIN (0x05)...");
            log("  - PIN Admin length: " + adminPin.length());
            log("  - PIN User New length: " + pinUserNew.length());
            
            if (apduCommands.resetPinByAdmin(adminPin.getBytes(), pinUserNew.getBytes())) {
                log("\n✓✓✓ RESET PIN THÀNH CÔNG! ✓✓✓");
                log("PIN User mới đã được đặt: " + pinUserNew);
                
                // Lưu audit log
                DatabaseConnection.AdminUserInfo adminUser = LoginFrame.getCurrentAdminUser();
                if (adminUser != null) {
                    String details = "Reset PIN User: cardId=" + cardIdHex;
                    DatabaseConnection.saveAdminAuditLog(adminUser.id, "RESET_PIN", cardId, details, null);
                }
                
                JOptionPane.showMessageDialog(this,
                    "Reset PIN thành công!\n\nPIN User mới: " + pinUserNew,
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                log("\n✗✗✗ RESET PIN THẤT BẠI! ✗✗✗");
                log("Có thể do Admin PIN không đúng hoặc lỗi giải mã trên thẻ.");
                JOptionPane.showMessageDialog(this,
                    "Reset PIN thất bại!\n\n" +
                    "Có thể do:\n" +
                    "- Admin PIN không đúng\n" +
                    "- Lỗi trên thẻ\n" +
                    "- Thẻ bị corrupt",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            log("\nEXCEPTION: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Lỗi: " + e.getMessage(),
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Log message to text area
     */
    private void log(String msg) {
        txtLog.append(msg + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }
    
    /**
     * Kiểm tra cardId có rỗng không (toàn số 0)
     */
    private boolean isCardIdEmpty(byte[] cardId) {
        if (cardId == null || cardId.length == 0) {
            return true;
        }
        for (int i = 0; i < cardId.length; i++) {
            if (cardId[i] != 0) {
                return false;
            }
        }
        return true;
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    private byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}

