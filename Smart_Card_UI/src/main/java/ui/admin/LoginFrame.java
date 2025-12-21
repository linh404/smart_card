package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import ui.ModernUITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.net.InetAddress;

/**
 * LoginFrame - Màn hình đăng nhập Admin bằng username/password
 * V3: Modern UI với light theme, rounded inputs, smooth animations
 */
public class LoginFrame extends JFrame {

    private ModernUITheme.RoundedTextField txtUsername;
    private ModernUITheme.RoundedPasswordField txtPassword;
    private ModernUITheme.RoundedButton btnLogin;
    private ModernUITheme.OutlineButton btnCancel;

    // Lưu current admin user (static để các panel khác có thể truy cập)
    private static DatabaseConnection.AdminUserInfo currentAdminUser = null;

    public LoginFrame() {
        ModernUITheme.applyTheme();
        initUI();
    }

    /**
     * Get current logged-in admin user
     */
    public static DatabaseConnection.AdminUserInfo getCurrentAdminUser() {
        return currentAdminUser;
    }

    /**
     * Clear current admin user (khi logout)
     */
    public static void clearCurrentAdminUser() {
        currentAdminUser = null;
    }

    private void initUI() {
        setTitle("Đăng nhập Admin");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(480, 420);
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
        card.setPreferredSize(new Dimension(380, 340));

        // Header with icon
        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Admin icon
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Circle background
                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.ADMIN_PRIMARY,
                        50, 50, ModernUITheme.ADMIN_GRADIENT_END);
                g2.setPaint(gp);
                g2.fillOval(5, 5, 50, 50);

                // Lock icon
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                // Lock body
                g2.fillRoundRect(20, 30, 20, 18, 4, 4);
                // Lock shackle
                g2.drawArc(22, 20, 16, 16, 0, 180);

                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(60, 60));
        iconPanel.setMaximumSize(new Dimension(60, 60));
        iconPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(iconPanel);

        headerPanel.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel("ĐĂNG NHẬP ADMIN");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Nhập thông tin đăng nhập của bạn");
        subtitleLabel.setFont(ModernUITheme.FONT_SMALL);
        subtitleLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(subtitleLabel);

        card.add(headerPanel);
        card.add(Box.createVerticalStrut(25));

        // Form fields
        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Username field
        JLabel lblUsername = new JLabel("Username");
        lblUsername.setFont(ModernUITheme.FONT_SUBHEADING);
        lblUsername.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblUsername.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblUsername);
        formPanel.add(Box.createVerticalStrut(6));

        txtUsername = new ModernUITheme.RoundedTextField(25);
        txtUsername.setMaximumSize(new Dimension(320, 42));
        txtUsername.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(txtUsername);
        formPanel.add(Box.createVerticalStrut(15));

        // Password field
        JLabel lblPassword = new JLabel("Password");
        lblPassword.setFont(ModernUITheme.FONT_SUBHEADING);
        lblPassword.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblPassword);
        formPanel.add(Box.createVerticalStrut(6));

        txtPassword = new ModernUITheme.RoundedPasswordField(25);
        txtPassword.setMaximumSize(new Dimension(320, 42));
        txtPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(txtPassword);

        // Wrapper to center form
        JPanel formWrapper = new JPanel();
        formWrapper.setOpaque(false);
        formWrapper.add(formPanel);
        card.add(formWrapper);

        card.add(Box.createVerticalStrut(20));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);

        btnLogin = new ModernUITheme.RoundedButton(
                "Đăng nhập",
                ModernUITheme.ADMIN_PRIMARY,
                ModernUITheme.ADMIN_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnLogin.setPreferredSize(new Dimension(140, 44));
        btnPanel.add(btnLogin);

        btnCancel = new ModernUITheme.OutlineButton(
                "Hủy",
                ModernUITheme.TEXT_SECONDARY,
                ModernUITheme.BG_SECONDARY,
                ModernUITheme.TEXT_SECONDARY);
        btnCancel.setPreferredSize(new Dimension(100, 44));
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
        txtPassword.addActionListener(e -> handleLogin());
    }

    private void handleLogin() {
        try {
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());

            // Validate input
            if (username.isEmpty()) {
                showError("Vui lòng nhập username!");
                txtUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                showError("Vui lòng nhập password!");
                txtPassword.requestFocus();
                return;
            }

            // Xác thực admin user
            System.out.println("[Admin Login] Đang xác thực: username = " + username);
            DatabaseConnection.AdminUserInfo adminUser = DatabaseConnection.authenticateAdmin(username, password);

            if (adminUser == null) {
                showError("Username hoặc password không đúng!");
                txtPassword.setText("");
                txtPassword.requestFocus();
                return;
            }

            // Lưu current admin user
            currentAdminUser = adminUser;

            // Lấy IP address để log
            String ipAddress = null;
            try {
                ipAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                // Ignore
            }

            // Lưu audit log
            DatabaseConnection.saveAdminAuditLog(
                    adminUser.id,
                    "LOGIN",
                    null,
                    "Login successful from " + username,
                    ipAddress);

            System.out.println("[Admin Login] Đăng nhập thành công: " + adminUser.username);

            // Đăng nhập thành công
            showSuccess("Đăng nhập thành công!\nChào mừng: " +
                    (adminUser.fullName != null ? adminUser.fullName : adminUser.username));

            // Khởi tạo CardManager (cần cho các panel khác)
            CardManager cardManager = CardManager.getInstance();
            APDUCommands apduCommands = new APDUCommands(cardManager.getChannel());

            dispose();
            new AdminFrame(cardManager, apduCommands).setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi khi đăng nhập: " + e.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "Thành công", JOptionPane.INFORMATION_MESSAGE);
    }
}
