package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import ui.ModernUITheme;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.charset.StandardCharsets;

/**
 * UserFrame - Màn hình chính của User
 * Chứa các tab: Thông tin thẻ, Nạp tiền/Thanh toán, BHYT, Lịch sử, Đổi PIN
 * V3: Modern UI với light theme, gradient header, styled tabs
 */
public class UserFrame extends JFrame {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private String userPin;
    private UserData userData;

    private UserInfoPanel userInfoPanel;
    private EditInfoPanel editInfoPanel;
    private TransactionPanel transactionPanel;
    private BHYTPanel bhytPanel;
    private HistoryPanel historyPanel;
    private ChangePinPanel changePinPanel;

    public UserFrame(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null, null);
    }

    public UserFrame(CardManager cardManager, APDUCommands apduCommands, String userPin, UserData userData) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userPin = userPin;
        this.userData = userData;
        ModernUITheme.applyTheme();
        initUI();
    }

    public String getUserPin() {
        return userPin;
    }

    public UserData getUserData() {
        return userData;
    }

    public boolean refreshUserData() {
        if (userPin == null || userPin.isEmpty()) {
            return false;
        }
        try {
            byte[] pinBytes = userPin.getBytes(StandardCharsets.UTF_8);
            byte[] userDataBytes = apduCommands.verifyPinAndReadData(pinBytes);
            if (userDataBytes != null && userDataBytes.length > 0) {
                userData = UserData.fromBytes(userDataBytes);
                return userData != null;
            }
        } catch (Exception e) {
            System.err.println("[UserFrame] Error refreshing userData: " + e.getMessage());
        }
        return false;
    }

    private void initUI() {
        setTitle("Hệ Thống Thẻ Thông Minh - User");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        setBackground(ModernUITheme.BG_PRIMARY);

        // Main container
        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setBackground(ModernUITheme.BG_PRIMARY);

        // ===== HEADER =====
        ModernUITheme.GradientHeader header = new ModernUITheme.GradientHeader(
                ModernUITheme.USER_GRADIENT_START,
                ModernUITheme.USER_GRADIENT_END);
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        header.setPreferredSize(new Dimension(0, 70));

        // Left: Title with icon
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftPanel.setOpaque(false);

        // Card icon
        JPanel iconPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(0, 0, 38, 38, 10, 10);

                // Card icon
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(8, 10, 22, 16, 3, 3);
                // Chip
                g2.fillRect(12, 14, 6, 4);

                g2.dispose();
            }
        };
        iconPanel.setOpaque(false);
        iconPanel.setPreferredSize(new Dimension(38, 38));
        leftPanel.add(iconPanel);

        JLabel title = new JLabel("HỆ THỐNG THẺ THÔNG MINH BỆNH VIỆN");
        title.setFont(ModernUITheme.FONT_HEADING);
        title.setForeground(Color.WHITE);
        leftPanel.add(title);

        header.add(leftPanel, BorderLayout.WEST);

        // Right: User info
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        rightPanel.setOpaque(false);

        if (userData != null) {
            String displayName = userData.getHoTen() != null ? userData.getHoTen() : "User";

            // Avatar
            JPanel avatar = ModernUITheme.createAvatarIcon(displayName, new Color(255, 255, 255, 40));
            rightPanel.add(avatar);

            JLabel userLabel = new JLabel(displayName);
            userLabel.setForeground(Color.WHITE);
            userLabel.setFont(ModernUITheme.FONT_BODY);
            rightPanel.add(userLabel);
        }

        // Logout button
        ModernUITheme.OutlineButton btnLogout = new ModernUITheme.OutlineButton(
                "Đăng xuất",
                Color.WHITE,
                new Color(255, 255, 255, 40),
                Color.WHITE);
        btnLogout.setPreferredSize(new Dimension(100, 34));
        btnLogout.setBackground(new Color(0, 0, 0, 0)); // Transparent background for gradient header
        btnLogout.addActionListener(e -> {
            if (cardManager != null && cardManager.isConnected()) {
                cardManager.disconnect();
            }
            dispose();
            new UserLoginFrame().setVisible(true);
        });
        rightPanel.add(btnLogout);

        header.add(rightPanel, BorderLayout.EAST);

        // ===== TABS =====
        JTabbedPane tabs = createModernUserTabbedPane();

        userInfoPanel = new UserInfoPanel(cardManager, apduCommands, this);
        editInfoPanel = new EditInfoPanel(cardManager, apduCommands, this);
        transactionPanel = new TransactionPanel(cardManager, apduCommands, this);
        bhytPanel = new BHYTPanel(cardManager, apduCommands, this);
        historyPanel = new HistoryPanel(cardManager, apduCommands, this);
        changePinPanel = new ChangePinPanel(cardManager, apduCommands, this);

        tabs.addTab("Thông tin thẻ", wrapInScrollPane(userInfoPanel));
        tabs.addTab("Đổi thông tin", wrapInScrollPane(editInfoPanel));
        tabs.addTab("Nạp tiền/Thanh toán", wrapInScrollPane(transactionPanel));
        tabs.addTab("Thông tin BHYT", wrapInScrollPane(bhytPanel));
        tabs.addTab("Lịch sử giao dịch", wrapInScrollPane(historyPanel));
        tabs.addTab("Đổi PIN", wrapInScrollPane(changePinPanel));

        // Auto-refresh data on tab change
        tabs.addChangeListener(e -> {
            int selectedIndex = tabs.getSelectedIndex();
            switch (selectedIndex) {
                case 0: // Thông tin thẻ
                    if (userInfoPanel != null)
                        userInfoPanel.loadInfo();
                    break;
                case 1: // Nạp tiền/Thanh toán
                    if (transactionPanel != null)
                        transactionPanel.updateBalance();
                    break;
                case 2: // BHYT
                    if (bhytPanel != null)
                        bhytPanel.loadBHYTInfo();
                    break;
                case 3: // Lịch sử
                    if (historyPanel != null)
                        historyPanel.loadHistory();
                    break;
                default:
                    break;
            }
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
    }

    private JScrollPane wrapInScrollPane(JPanel panel) {
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(ModernUITheme.BG_PRIMARY);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    private JTabbedPane createModernUserTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(ModernUITheme.FONT_BUTTON);
        tabbedPane.setBackground(ModernUITheme.BG_PRIMARY);
        tabbedPane.setForeground(ModernUITheme.TEXT_PRIMARY);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder());

        tabbedPane.setUI(new BasicTabbedPaneUI() {
            private final Insets tabInsets = new Insets(12, 18, 12, 18);

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
                    GradientPaint gp = new GradientPaint(
                            x, y, ModernUITheme.USER_PRIMARY,
                            x + w, y, ModernUITheme.USER_GRADIENT_END);
                    g2.setPaint(gp);
                    g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 10, 10));
                } else {
                    g2.setColor(ModernUITheme.BG_CARD);
                    g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 10, 10));
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
}
