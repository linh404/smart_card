package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import ui.ModernUITheme;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * AdminFrame - Màn hình quản trị chính
 * Chứa các tab: Phát hành thẻ, Quản lý thẻ, Reset PIN
 * V3: Modern UI với light theme, gradient header, styled tabs
 */
public class AdminFrame extends JFrame {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private CardIssuePanel cardIssuePanel;
    private CardManagePanel cardManagePanel;
    private ResetPinPanel resetPinPanel;
    private JLabel lblConnectionStatus;

    public AdminFrame(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        ModernUITheme.applyTheme();
        initUI();
    }

    private void initUI() {
        setTitle("Quản Trị Hệ Thống");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 750);
        setExtendedState(JFrame.MAXIMIZED_BOTH); // Mặc định phóng to
        setLocationRelativeTo(null);
        setBackground(ModernUITheme.BG_PRIMARY);

        // Main container
        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setBackground(ModernUITheme.BG_PRIMARY);

        // ===== HEADER =====
        ModernUITheme.GradientHeader header = new ModernUITheme.GradientHeader(
                ModernUITheme.ADMIN_GRADIENT_START,
                ModernUITheme.ADMIN_GRADIENT_END);
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        header.setPreferredSize(new Dimension(0, 70));

        // Left: Title with icon
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftPanel.setOpaque(false);

        // System icon
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(0, 0, 38, 38, 10, 10);

                // Settings gear icon
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(12, 12, 14, 14);
                // Gear teeth (simplified)
                for (int i = 0; i < 6; i++) {
                    double angle = i * Math.PI / 3;
                    int x1 = (int) (19 + 9 * Math.cos(angle));
                    int y1 = (int) (19 + 9 * Math.sin(angle));
                    int x2 = (int) (19 + 12 * Math.cos(angle));
                    int y2 = (int) (19 + 12 * Math.sin(angle));
                    g2.drawLine(x1, y1, x2, y2);
                }

                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(38, 38));
        leftPanel.add(iconPanel);

        JLabel title = new JLabel("HỆ THỐNG QUẢN TRỊ THẺ THÔNG MINH");
        title.setFont(ModernUITheme.FONT_HEADING);
        title.setForeground(Color.WHITE);
        leftPanel.add(title);

        header.add(leftPanel, BorderLayout.WEST);

        // Right: Status, user info, logout
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        rightPanel.setOpaque(false);

        // Connection status badge
        lblConnectionStatus = createStatusBadge("● Chưa kết nối", new Color(255, 255, 255, 30), Color.WHITE);
        rightPanel.add(lblConnectionStatus);

        // Separator
        JLabel sep = new JLabel("|");
        sep.setForeground(new Color(255, 255, 255, 60));
        rightPanel.add(sep);

        // User info
        DatabaseConnection.AdminUserInfo currentUser = LoginFrame.getCurrentAdminUser();
        if (currentUser != null) {
            String displayName = currentUser.fullName != null && !currentUser.fullName.isEmpty()
                    ? currentUser.fullName
                    : currentUser.username;

            // Avatar
            JPanel avatar = ModernUITheme.createAvatarIcon(displayName, new Color(255, 255, 255, 40));
            rightPanel.add(avatar);

            JLabel userLabel = new JLabel(displayName);
            userLabel.setForeground(Color.WHITE);
            userLabel.setFont(ModernUITheme.FONT_BODY);
            rightPanel.add(userLabel);
        }

        // Logout button
        ModernUITheme.RoundedButton btnLogout = new ModernUITheme.RoundedButton(
                "Đăng xuất",
                Color.WHITE,
                new Color(240, 240, 240),
                new Color(220, 38, 38)); // Red text
        btnLogout.setPreferredSize(new Dimension(100, 34));
        btnLogout.setFont(ModernUITheme.FONT_SMALL);
        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    AdminFrame.this,
                    "Bạn có chắc chắn muốn đăng xuất?",
                    "Xác nhận",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                LoginFrame.clearCurrentAdminUser();
                dispose();
                new LoginFrame().setVisible(true);
            }
        });
        rightPanel.add(btnLogout);

        header.add(rightPanel, BorderLayout.EAST);

        // ===== TABS =====
        JTabbedPane tabs = createModernAdminTabbedPane();

        cardIssuePanel = new CardIssuePanel(cardManager, apduCommands);
        cardManagePanel = new CardManagePanel(cardManager, apduCommands);
        resetPinPanel = new ResetPinPanel(cardManager, apduCommands);

        // Add tabs directly (panels handle their own scrolling)
        tabs.addTab("📝 Phát hành thẻ User", cardIssuePanel);
        tabs.addTab("🔧 Quản lý thông tin thẻ", cardManagePanel);
        tabs.addTab("🔑 Reset PIN User", resetPinPanel);

        // Tab change listener
        tabs.addChangeListener(e -> {
            int selectedIndex = tabs.getSelectedIndex();
            System.out.println("[AdminFrame] Tab changed to index: " + selectedIndex);

            boolean isReady = CardConnectionHelper.checkConnectionStatus(cardManager, apduCommands);
            String statusText = CardConnectionHelper.getConnectionStatusText(cardManager, apduCommands);
            System.out.println("[AdminFrame] Connection status: " + statusText + " (ready: " + isReady + ")");

            updateConnectionStatusIndicator(isReady);
        });

        // Tab panel container
        JPanel tabContainer = new JPanel(new BorderLayout());
        tabContainer.setBackground(ModernUITheme.BG_PRIMARY);
        tabContainer.setBorder(BorderFactory.createEmptyBorder(16, 20, 20, 20));
        tabContainer.add(tabs, BorderLayout.CENTER);

        // Layout
        mainContainer.add(header, BorderLayout.NORTH);
        mainContainer.add(tabContainer, BorderLayout.CENTER);

        setContentPane(mainContainer);

        // Initial status check
        updateConnectionStatusIndicator(false);
    }

    private JScrollPane wrapInScrollPane(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(ModernUITheme.BG_PRIMARY);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    private JTabbedPane createModernAdminTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(ModernUITheme.FONT_BUTTON);
        tabbedPane.setBackground(ModernUITheme.BG_PRIMARY);
        tabbedPane.setForeground(ModernUITheme.TEXT_PRIMARY);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder());

        tabbedPane.setUI(new BasicTabbedPaneUI() {
            private final Insets tabInsets = new Insets(12, 20, 12, 20);

            @Override
            protected void installDefaults() {
                super.installDefaults();
                tabAreaInsets = new Insets(0, 0, 8, 0);
                contentBorderInsets = new Insets(0, 0, 0, 0);
            }

            @Override
            protected Insets getTabInsets(int tabPlacement, int tabIndex) {
                return tabInsets;
            }

            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                    int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isSelected) {
                    // Gradient for selected tab
                    GradientPaint gp = new GradientPaint(
                            x, y, ModernUITheme.ADMIN_PRIMARY,
                            x + w, y, ModernUITheme.ADMIN_GRADIENT_END);
                    g2.setPaint(gp);
                    g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 10, 10));
                } else {
                    g2.setColor(ModernUITheme.BG_CARD);
                    g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 10, 10));
                    // Border
                    g2.setColor(ModernUITheme.BORDER_LIGHT);
                    g2.draw(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 10, 10));
                }

                g2.dispose();
            }

            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                    int x, int y, int w, int h, boolean isSelected) {
                // No border
            }

            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                // Content border
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Rectangle bounds = tabPane.getBounds();
                Insets insets = tabPane.getInsets();
                int x = insets.left;
                int y = insets.top + calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
                int w = bounds.width - insets.left - insets.right;
                int h = bounds.height - insets.top - insets.bottom - y + insets.top;

                g2.setColor(ModernUITheme.BG_CARD);
                g2.fill(new RoundRectangle2D.Float(x, y, w, h, 12, 12));

                g2.dispose();
            }

            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                    int tabIndex, Rectangle iconRect, Rectangle textRect,
                    boolean isSelected) {
                // No focus indicator
            }

            @Override
            protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                    int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(font);
                g2.setColor(isSelected ? Color.WHITE : ModernUITheme.TEXT_SECONDARY);
                g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
                g2.dispose();
            }
        });

        return tabbedPane;
    }

    private JLabel createStatusBadge(String text, Color bgColor, Color textColor) {
        JLabel badge = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(textColor);
        badge.setFont(ModernUITheme.FONT_SMALL);
        badge.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return badge;
    }

    private void updateConnectionStatusIndicator(boolean isConnected) {
        if (lblConnectionStatus == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            if (isConnected) {
                lblConnectionStatus.setText("● Đã kết nối");
                lblConnectionStatus.setToolTipText("Đã kết nối với đầu đọc thẻ và sẵn sàng sử dụng");
            } else {
                lblConnectionStatus.setText("● Chưa kết nối");
                lblConnectionStatus
                        .setToolTipText("Chưa kết nối với đầu đọc thẻ. Kết nối sẽ được thiết lập tự động khi cần.");
            }
        });
    }
}
