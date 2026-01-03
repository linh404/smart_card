package ui.admin;

import card.CardManager;
import card.APDUCommands;
import util.AdminPinDerivation;
import util.EnvFileLoader;
import ui.ModernUITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;

/**
 * ResetPinPanel - Panel reset PIN User
 * V3: Tự động derive Admin PIN từ K_master và cardID, không lưu trong DB
 */
public class ResetPinPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;

    private ModernUITheme.RoundedTextField txtCardIdUser;
    private ModernUITheme.RoundedTextField txtPinUserNew; // V5: Đổi từ JPasswordField sang JTextField
    private ModernUITheme.RoundedButton btnResetPin;
    private ModernUITheme.RoundedButton btnLoadCard;
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
        setBackground(ModernUITheme.BG_PRIMARY);

        // Center wrapper
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);

        // Card content
        JPanel card = new ModernUITheme.CardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(500, 350));

        addHeader(card, "🔐 RESET CLOUD PIN"); // Title

        // Card ID Section
        addLabel(card, "Card ID User:");
        JPanel cardIdPanel = new JPanel(new BorderLayout(10, 0));
        cardIdPanel.setOpaque(false);
        cardIdPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cardIdPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtCardIdUser = new ModernUITheme.RoundedTextField(20);
        txtCardIdUser.setEditable(false);
        txtCardIdUser.setBackground(new Color(245, 245, 245));
        cardIdPanel.add(txtCardIdUser, BorderLayout.CENTER);

        btnLoadCard = new ModernUITheme.RoundedButton("Đọc thẻ", ModernUITheme.INFO,
                ModernUITheme.darken(ModernUITheme.INFO, 0.1f), Color.WHITE);
        btnLoadCard.setPreferredSize(new Dimension(100, 40));
        btnLoadCard.setFont(ModernUITheme.FONT_SMALL);
        btnLoadCard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadCardInfo();
            }
        });
        cardIdPanel.add(btnLoadCard, BorderLayout.EAST);

        card.add(cardIdPanel);
        card.add(Box.createVerticalStrut(20));

        // PIN User mới
        addLabel(card, "PIN User mới (cố định):");
        txtPinUserNew = new ModernUITheme.RoundedTextField(20);
        txtPinUserNew.setText("123456"); // Cố định
        txtPinUserNew.setEditable(false); // Không cho sửa
        txtPinUserNew.setBackground(new Color(245, 245, 245)); // Màu xám
        txtPinUserNew.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        txtPinUserNew.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(txtPinUserNew);
        card.add(Box.createVerticalStrut(30));

        // Reset Button
        btnResetPin = new ModernUITheme.RoundedButton("Xác nhận Reset PIN",
                new Color(220, 38, 38), // Red color for critical action
                new Color(185, 28, 28),
                Color.WHITE);
        btnResetPin.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnResetPin.setPreferredSize(new Dimension(200, 45));
        btnResetPin.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrapper.setOpaque(false);
        btnWrapper.add(btnResetPin);

        card.add(btnWrapper);

        centerPanel.add(card);

        // Wrap centerPanel in ScrollPane
        JScrollPane mainScroll = new JScrollPane(centerPanel);
        mainScroll.setBorder(BorderFactory.createEmptyBorder());
        mainScroll.getViewport().setBackground(ModernUITheme.BG_PRIMARY);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);

        // Log area at bottom
        txtLog = new JTextArea(8, 50);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtLog);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Log Process"));
        scrollLog.setPreferredSize(new Dimension(0, 150));

        add(mainScroll, BorderLayout.CENTER);
        add(scrollLog, BorderLayout.SOUTH);

        // Event handlers
        btnResetPin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetPin();
            }
        });
    }

    private void addHeader(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(ModernUITheme.FONT_HEADING);
        label.setForeground(ModernUITheme.ADMIN_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(20));
    }

    private void addLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(ModernUITheme.FONT_SUBHEADING);
        label.setForeground(ModernUITheme.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(5));
    }

    /**
     * Load thông tin thẻ (Card ID)
     */
    private void loadCardInfo() {
        try {
            txtLog.setText("");
            log("=== ĐỌC THÔNG TIN THẺ ===\n");

            // Đảm bảo kết nối thẻ, channel sẵn sàng, và applet đã được select
            log("Bước 1: Kiểm tra kết nối thẻ và select UserApplet...");
            if (!CardConnectionHelper.ensureCardAndAppletReady(
                    cardManager, apduCommands, this, true, APDUCommands.AID_USER)) {
                log("LỖI: Không thể đảm bảo kết nối thẻ và applet");
                return;
            }
            log("✓ Kết nối thẻ và select UserApplet thành công");

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

            String pinUserNew = txtPinUserNew.getText().trim();
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

            // --- Xác nhận Reset PIN ---
            int confirm = JOptionPane.showConfirmDialog(this,
                    String.format("Bạn có chắc chắn muốn Reset PIN cho thẻ này không?\n\nCard ID: %s\nPIN Mới: %s",
                            cardIdHex, pinUserNew),
                    "Xác nhận Reset PIN",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                log("Đã hủy Reset PIN.");
                return;
            }

            // 1. Đảm bảo kết nối thẻ, channel sẵn sàng, và applet đã được select
            log("Bước 1: Kiểm tra kết nối thẻ và select UserApplet...");
            if (!CardConnectionHelper.ensureCardAndAppletReady(
                    cardManager, apduCommands, this, true, APDUCommands.AID_USER)) {
                log("LỖI: Không thể đảm bảo kết nối thẻ và applet");
                return;
            }
            log("✓ Kết nối thẻ và select UserApplet thành công");

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

            // Sử dụng UTF-8 để đảm bảo encoding nhất quán
            byte[] adminPinBytes = adminPin.getBytes(StandardCharsets.UTF_8);
            byte[] pinUserNewBytes = pinUserNew.getBytes(StandardCharsets.UTF_8);

            // Đảm bảo PIN bytes đúng 6 bytes
            if (adminPinBytes.length != 6) {
                log("LỖI: Admin PIN không đúng định dạng (phải là 6 bytes)!");
                JOptionPane.showMessageDialog(this,
                        "Lỗi: Admin PIN không đúng định dạng!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (pinUserNewBytes.length != 6) {
                log("LỖI: PIN User mới không đúng định dạng (phải là 6 bytes)!");
                JOptionPane.showMessageDialog(this,
                        "Lỗi: PIN User mới không đúng định dạng!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✨ NEW: Nhận kết quả với public key
            card.APDUCommands.ResetPinResult result = apduCommands.resetPinByAdmin(
                    adminPinBytes, pinUserNewBytes);

            if (result.success) {
                log("\n✓✓✓ RESET PIN THÀNH CÔNG! ✓✓✓");
                log("PIN User mới: " + pinUserNew);

                // ✨ NEW: Cập nhật public key nếu có
                if (result.hasNewKey()) {
                    log("\nBước 5: Cập nhật RSA Public Key mới vào database...");
                    log("  - Public key length: " + result.newPublicKey.length + " bytes");

                    boolean updateSuccess = db.DatabaseConnection.updateUserPublicKey(
                            cardIdOnCard, result.newPublicKey);

                    if (updateSuccess) {
                        log("  - ✓✓ RSA Public Key đã được cập nhật trong database!");
                    } else {
                        log("  - ✗✗ CẢNH BÁO: Không thể cập nhật public key vào database!");

                        JOptionPane.showMessageDialog(this,
                                "⚠️ CẢNH BÁO NGHIÊM TRỌNG ⚠️\n\n" +
                                        "Reset PIN thành công NHƯNG cập nhật RSA key thất bại!\n\n" +
                                        "Card ID: " + bytesToHex(cardIdOnCard) + "\n\n" +
                                        "Hậu quả:\n" +
                                        "- User KHÔNG THỂ đăng nhập được\n" +
                                        "- Cần PHÁT HÀNH LẠI THẺ ngay\n\n" +
                                        "Vui lòng liên hệ IT support!",
                                "Lỗi Nghiêm Trọng", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    log("\nℹ️ Lưu ý: Không nhận được RSA public key mới từ thẻ");
                    log("  - Applet có thể chưa được cập nhật lên V4");
                    log("  - Hoặc JavaCard không cho phép export public key");
                    log("  - RSA keys KHÔNG được đổi, chỉ đổi PIN");
                }

                // Refresh channel
                log("\nBước 6: Refresh channel...");
                try {
                    // Cập nhật lại channel cho APDUCommands
                    apduCommands.setChannel(cardManager.getChannel());
                    log("✓ Channel đã được refresh");
                } catch (Exception e) {
                    log("⚠️ Cảnh báo: Không refresh được channel - " + e.getMessage());
                    // Không fail vì reset PIN đã thành công
                }

                // Thông báo thành công
                String successMsg = "✓ Reset PIN thành công!\n\n" +
                        "PIN User mới: " + pinUserNew + "\n";

                if (result.hasNewKey()) {
                    successMsg += "\n🔐 Bảo mật đã được tăng cường:\n" +
                            "✓ Cặp khóa RSA đã được tạo mới\n" +
                            "✓ Public Key mới đã lưu vào database\n" +
                            "✓ Private Key cũ đã bị xóa khỏi thẻ\n\n" +
                            "⚠️ Lưu ý: User cần đăng nhập lại với PIN mới";
                } else {
                    successMsg += "\n⚠️ Lưu ý:\n" +
                            "- RSA keys KHÔNG được đổi\n" +
                            "- Chỉ đổi PIN thành công\n" +
                            "- Applet có thể chưa hỗ trợ V4\n\n" +
                            "User vẫn đăng nhập được với PIN mới";
                }

                JOptionPane.showMessageDialog(this, successMsg,
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);

            } else {
                log("\n✗✗✗ RESET PIN THẤT BẠI! ✗✗✗");
                log("Có thể do Admin PIN không đúng hoặc lỗi trên thẻ.");

                JOptionPane.showMessageDialog(this,
                        "Reset PIN thất bại!\n\n" +
                                "Nguyên nhân có thể:\n" +
                                "- Admin PIN không đúng\n" +
                                "- Lỗi tạo RSA key trên thẻ\n" +
                                "- Thẻ bị lỗi hoặc corrupt\n\n" +
                                "Vui lòng kiểm tra log để biết chi tiết.",
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
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
