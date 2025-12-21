package ui;

import db.DatabaseConnection;
import ui.admin.LoginFrame;
import ui.user.UserLoginFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * MainFrame - Màn hình chính của ứng dụng
 * Cho phép chọn đăng nhập Admin hoặc User
 * 
 * V3: Modern UI với light theme, rounded buttons, smooth animations
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        ModernUITheme.applyTheme();
        setTitle("Hệ Thống Thẻ Thông Minh Bệnh Viện");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 450);
        setLocationRelativeTo(null);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
    }

    private void initUI() {
        // Main container with gradient background
        JPanel mainContainer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // Subtle gradient background
                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.BG_PRIMARY,
                        getWidth(), getHeight(), ModernUITheme.BG_SECONDARY);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };

        // Center card
        ModernUITheme.CardPanel centerCard = new ModernUITheme.CardPanel(new GridBagLayout());
        centerCard.setPreferredSize(new Dimension(420, 320));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 10, 0);

        // Hospital icon/logo placeholder
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Circle background with gradient
                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.ADMIN_PRIMARY,
                        getWidth(), getHeight(), ModernUITheme.ADMIN_GRADIENT_END);
                g2.setPaint(gp);
                g2.fillOval(5, 5, 60, 60);

                // Hospital cross icon
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(35, 20, 35, 50); // Vertical
                g2.drawLine(20, 35, 50, 35); // Horizontal

                g2.dispose();
            }
        };
        logoPanel.setOpaque(false);
        logoPanel.setPreferredSize(new Dimension(70, 70));
        centerCard.add(logoPanel, gbc);

        // Title
        gbc.gridy = 1;
        gbc.insets = new Insets(5, 0, 5, 0);
        JLabel titleLabel = new JLabel("HỆ THỐNG THẺ THÔNG MINH");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        centerCard.add(titleLabel, gbc);

        // Subtitle
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 30, 0);
        JLabel subtitleLabel = new JLabel("Bệnh Viện - Hospital Smart Card System");
        subtitleLabel.setFont(ModernUITheme.FONT_BODY);
        subtitleLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        centerCard.add(subtitleLabel, gbc);

        // Admin button
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.insets = new Insets(5, 10, 5, 10);

        ModernUITheme.RoundedButton adminButton = new ModernUITheme.RoundedButton(
                "🔒 Đăng nhập Admin",
                ModernUITheme.ADMIN_PRIMARY,
                ModernUITheme.ADMIN_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        adminButton.setPreferredSize(new Dimension(180, 48));
        adminButton.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
        centerCard.add(adminButton, gbc);

        // User button
        gbc.gridx = 1;
        ModernUITheme.RoundedButton userButton = new ModernUITheme.RoundedButton(
                "👤 Đăng nhập User",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        userButton.setPreferredSize(new Dimension(180, 48));
        userButton.addActionListener(e -> {
            dispose();
            new UserLoginFrame().setVisible(true);
        });
        centerCard.add(userButton, gbc);

        // Footer text
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(25, 0, 0, 0);
        JLabel footerLabel = new JLabel("Phiên bản 3.0 - Bảo mật RSA");
        footerLabel.setFont(ModernUITheme.FONT_SMALL);
        footerLabel.setForeground(ModernUITheme.TEXT_MUTED);
        centerCard.add(footerLabel, gbc);

        // Center the card
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(centerCard);

        mainContainer.add(centerWrapper, BorderLayout.CENTER);

        setContentPane(mainContainer);
    }

    public static void main(String[] args) {
        // Thiết lập encoding UTF-8 để hiển thị tiếng Việt trong console
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");

        // Thiết lập default charset cho JVM
        try {
            java.lang.reflect.Field charsetField = java.nio.charset.Charset.class.getDeclaredField("defaultCharset");
            charsetField.setAccessible(true);
            charsetField.set(null, java.nio.charset.Charset.forName("UTF-8"));
        } catch (Exception e) {
            // Ignore nếu không thể set
        }

        // Thiết lập encoding cho System.out và System.err
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (Exception e) {
            System.err.println("Warning: Could not set UTF-8 encoding for console output");
        }

        // Thêm shutdown hook để đóng HikariCP pool khi thoát ứng dụng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[MainFrame] Đang đóng database connection pool...");
            DatabaseConnection.shutdown();
        }));

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
