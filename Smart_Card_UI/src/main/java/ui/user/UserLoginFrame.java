package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import util.CryptoUtils;
import db.DatabaseConnection;
import ui.ModernUITheme;

import javax.swing.*;
import javax.smartcardio.CardException;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.charset.StandardCharsets;

/**
 * UserLoginFrame - Màn hình đăng nhập User bằng thẻ User
 * V3: Modern UI với light theme, card layout, smooth animations
 */
public class UserLoginFrame extends JFrame {

    private ModernUITheme.RoundedPasswordField txtPin;
    private ModernUITheme.RoundedButton btnLogin;
    private ModernUITheme.OutlineButton btnCancel;
    private CardManager cardManager;
    private APDUCommands apduCommands;

    public UserLoginFrame() {
        ModernUITheme.applyTheme();
        initUI();
    }

    private void initUI() {
        setTitle("Đăng nhập User");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);

        // Main container with gradient background
        JPanel mainContainer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.BG_PRIMARY,
                        getWidth(), getHeight(), ModernUITheme.BG_SECONDARY);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };

        // Center card
        ModernUITheme.CardPanel card = new ModernUITheme.CardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(480, 480));

        // Header with icon
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // User icon
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Circle background with gradient
                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.USER_PRIMARY,
                        50, 50, ModernUITheme.USER_GRADIENT_END);
                g2.setPaint(gp);
                g2.fillOval(5, 5, 50, 50);

                // Card icon
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.fillRoundRect(17, 22, 26, 18, 4, 4);
                // Chip on card
                g2.setColor(new Color(255, 215, 0)); // Gold chip
                g2.fillRect(21, 27, 8, 6);

                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(60, 60));
        iconPanel.setMaximumSize(new Dimension(60, 60));
        iconPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(iconPanel);

        headerPanel.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel("ĐĂNG NHẬP USER");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Vui lòng nhập mã PIN để đăng nhập");
        subtitleLabel.setFont(ModernUITheme.FONT_SMALL);
        subtitleLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(subtitleLabel);

        card.add(headerPanel);
        card.add(Box.createVerticalStrut(25));

        // Card status indicator
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusPanel.setOpaque(false);
        JLabel statusLabel = new JLabel("✓ Thẻ đã được kết nối");
        statusLabel.setFont(ModernUITheme.FONT_SMALL);
        statusLabel.setForeground(ModernUITheme.SUCCESS);
        statusPanel.add(statusLabel);
        card.add(statusPanel);
        card.add(Box.createVerticalStrut(15));

        // Form fields
        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // PIN field
        JLabel lblPin = new JLabel("Mã PIN (6 chữ số)");
        lblPin.setFont(ModernUITheme.FONT_SUBHEADING);
        lblPin.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblPin);
        formPanel.add(Box.createVerticalStrut(6));

        txtPin = new ModernUITheme.RoundedPasswordField(20);
        txtPin.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtPin.setMaximumSize(new Dimension(320, 50));
        txtPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(txtPin);

        // Wrapper to center form
        JPanel formWrapper = new JPanel();
        formWrapper.setOpaque(false);
        formWrapper.add(formPanel);
        card.add(formWrapper);

        card.add(Box.createVerticalStrut(25));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);

        btnLogin = new ModernUITheme.RoundedButton(
                "Đăng nhập",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnLogin.setPreferredSize(new Dimension(140, 48));
        btnPanel.add(btnLogin);

        btnCancel = new ModernUITheme.OutlineButton(
                "Hủy",
                ModernUITheme.TEXT_SECONDARY,
                ModernUITheme.BG_SECONDARY,
                ModernUITheme.TEXT_SECONDARY);
        btnCancel.setPreferredSize(new Dimension(100, 48));
        btnPanel.add(btnCancel);

        card.add(btnPanel);

        // Center the card
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(card);

        mainContainer.add(centerWrapper, BorderLayout.CENTER);
        setContentPane(mainContainer);

        // Event handlers
        btnLogin.addActionListener(e -> handleLogin());
        btnCancel.addActionListener(e -> dispose());
        txtPin.addActionListener(e -> handleLogin());

        // Kết nối thẻ
        cardManager = CardManager.getInstance();

        if (cardManager.isConnected()) {
            cardManager.disconnect();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        if (!cardManager.connect()) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối với đầu đọc thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!cardManager.selectApplet(APDUCommands.AID_USER, true)) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy UserApplet trên thẻ!\n\n" +
                            "Có thể thẻ đã bị reset hoặc applet chưa được cài đặt.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            cardManager.disconnect();
            return;
        }

        apduCommands = new APDUCommands(cardManager.getChannel());
    }

    private void handleLogin() {
        try {
            String pin = new String(txtPin.getPassword());

            if (pin.isEmpty()) {
                showError("Vui lòng nhập mã PIN!");
                txtPin.requestFocus();
                return;
            }

            if (pin.length() != 6 || !pin.matches("^[0-9]+$")) {
                showError("PIN phải là 6 chữ số!");
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            // 1. Kiểm tra cardId_user từ thẻ
            byte[] cardIdUser = apduCommands.getStatus();
            if (cardIdUser == null || cardIdUser.length != 16) {
                showError("Không thể đọc cardId từ thẻ!\n\nCó thể thẻ chưa được phát hành.");
                return;
            }

            boolean cardIdEmpty = true;
            for (int i = 0; i < cardIdUser.length; i++) {
                if (cardIdUser[i] != 0) {
                    cardIdEmpty = false;
                    break;
                }
            }
            if (cardIdEmpty) {
                showWarning("Thẻ chưa được phát hành!\n\nVui lòng phát hành thẻ trước khi đăng nhập.");
                return;
            }

            // 2. Xác thực PIN và đọc data (sử dụng method mới với kết quả chi tiết)
            byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);

            if (pinBytes.length != 6) {
                showError("Lỗi: PIN không đúng định dạng (phải là 6 bytes)!");
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            APDUCommands.VerifyPinResult verifyResult = apduCommands.verifyPinWithResult(pinBytes);

            if (!verifyResult.isSuccess()) {
                // Hiển thị thông báo lỗi chi tiết từ kết quả
                if (verifyResult.isWrongPin()) {
                    // PIN sai - hiển thị số lần còn lại
                    showError(verifyResult.errorMessage);
                } else if (verifyResult.isBlocked()) {
                    // Thẻ bị khóa
                    showError(verifyResult.errorMessage);
                } else {
                    // Lỗi khác
                    showError(verifyResult.errorMessage != null ? verifyResult.errorMessage
                            : "Lỗi xác thực! Vui lòng thử lại.");
                }
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            byte[] userDataBytes = verifyResult.patientData;
            if (userDataBytes == null || userDataBytes.length == 0) {
                showError("Không thể đọc dữ liệu từ thẻ!");
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            // 3. RSA challenge-response
            System.out.println("[User Login] ✓ PIN đúng, bắt đầu xác thực RSA...");

            byte[] pkUserBytes = DatabaseConnection.getUserPublicKey(cardIdUser);

            if (pkUserBytes == null) {
                System.err.println("[User Login] ✗ Thẻ chưa có PK_user trong database");
                showError("THẺ CHƯA CÓ RSA KEY!\n\nVui lòng liên hệ admin để phát hành lại thẻ.");
                return;
            }

            System.out.println("[User Login] ✓ Đã lấy PK_user từ database, length: " + pkUserBytes.length);

            byte[] challenge = CryptoUtils.generateChallenge();
            System.out.println("[User Login] Challenge (32 bytes): " + bytesToHex(challenge));

            byte[] signature = null;
            try {
                signature = apduCommands.signChallenge(challenge);
            } catch (CardException e) {
                System.err.println("[User Login] CardException: " + e.getMessage());
                e.printStackTrace();

                int retry = JOptionPane.showConfirmDialog(this,
                        "LỖI GIAO TIẾP VỚI THẺ!\n\nBạn có muốn thử lại không?",
                        "Lỗi giao tiếp",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (retry == JOptionPane.YES_OPTION) {
                    handleLogin();
                }
                return;
            }

            if (signature == null || signature.length == 0) {
                System.err.println("[User Login] ✗ Thẻ không trả về signature");

                int retry = JOptionPane.showConfirmDialog(this,
                        "LỖI XÁC THỰC!\n\nBạn có muốn thử lại không?",
                        "Lỗi",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (retry == JOptionPane.YES_OPTION) {
                    handleLogin();
                }
                return;
            }

            System.out.println("[User Login] ✓ Nhận được signature, length: " + signature.length);

            java.security.PublicKey pkUser = CryptoUtils.bytesToPublicKey(pkUserBytes);

            if (pkUser == null) {
                System.err.println("[User Login] ✗ Không thể parse PK_user");
                showError("LỖI HỆ THỐNG!\n\nVui lòng liên hệ admin để kiểm tra.");
                return;
            }

            // IMPORTANT: Applet signs HASH of challenge (SHA-1), not raw challenge
            // Java Card uses MessageDigest.ALG_SHA which is SHA-1 (20 bytes)
            // So we must hash the challenge with SHA-1 before verifying signature
            byte[] challengeHash = CryptoUtils.sha1(challenge);
            System.out.println("[User Login] Challenge hash SHA-1 (20 bytes): " + bytesToHex(challengeHash));

            // Verify signature against HASH, not raw challenge
            boolean isValid = CryptoUtils.verifyRSASignature(challengeHash, signature, pkUser);

            if (!isValid) {
                System.err.println("[User Login] ✗✗✗ SIGNATURE KHÔNG HỢP LỆ! ✗✗✗");
                showError("XÁC THỰC THẺ THẤT BẠI!\n\nCHỮ KÝ SỐ KHÔNG HỢP LỆ.\nTHẺ NÀY CÓ THỂ LÀ GIẢ!");
                return;
            }

            System.out.println("[User Login] ✓✓✓ XÁC THỰC RSA THÀNH CÔNG! ✓✓✓");

            // 4. Parse userData
            UserData userData = UserData.fromBytes(userDataBytes);
            if (userData == null) {
                showError("Không thể parse dữ liệu từ thẻ!");
                return;
            }

            // 5. V4: Kiểm tra user đã đổi PIN mặc định chưa
            boolean pinChanged = apduCommands.isPinChanged();
            System.out.println("[User Login] PIN changed status: " + pinChanged);

            if (!pinChanged) {
                System.out.println("[User Login] ⚠️ User chưa đổi PIN mặc định, yêu cầu đổi PIN");

                // Hiển thị dialog bắt buộc đổi PIN
                ForceChangePinDialog dialog = new ForceChangePinDialog(
                        this, cardManager, apduCommands, pin);
                dialog.setVisible(true);

                if (!dialog.isPinChanged()) {
                    // User không đổi PIN -> không cho đăng nhập
                    JOptionPane.showMessageDialog(this,
                            "Bạn PHẢI đổi mã PIN mặc định trước khi sử dụng!\n\n" +
                                    "Đây là yêu cầu bảo mật bắt buộc.",
                            "Yêu cầu bảo mật", JOptionPane.WARNING_MESSAGE);
                    txtPin.setText("");
                    txtPin.requestFocus();
                    return;
                }

                // Cập nhật PIN mới cho session hiện tại
                pin = dialog.getNewPin();
                System.out.println("[User Login] ✓ Đã đổi PIN thành công, tiếp tục đăng nhập");
            }

            // 6. Đăng nhập thành công
            System.out.println("[User Login] UserData loaded: " + userData.getHoTen());

            dispose();
            new UserFrame(cardManager, apduCommands, pin, userData).setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
            showError("LỖI KHÔNG XÁC ĐỊNH!\n\nLỗi: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Cảnh báo", JOptionPane.WARNING_MESSAGE);
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
