package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * AdminFrame - Màn hình quản trị chính
 * Chứa các tab: Phát hành thẻ, Quản lý thẻ, Reset PIN
 */
public class AdminFrame extends JFrame {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private CardIssuePanel cardIssuePanel;
    private CardManagePanel cardManagePanel;
    private ResetPinPanel resetPinPanel;
    private JLabel lblConnectionStatus; // Status indicator cho kết nối thẻ

    public AdminFrame(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        initUI();
    }

    private void initUI() {
        setTitle("Quản Trị Hệ Thống");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 102, 153));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("HỆ THỐNG QUẢN TRỊ THẺ THÔNG MINH");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        
        // Right side: Connection status + User info + Logout button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBackground(new Color(0, 102, 153));
        
        // Connection status indicator
        lblConnectionStatus = new JLabel("● Chưa kết nối");
        lblConnectionStatus.setForeground(new Color(255, 200, 200)); // Màu đỏ nhạt
        lblConnectionStatus.setFont(new Font("Arial", Font.PLAIN, 11));
        lblConnectionStatus.setToolTipText("Trạng thái kết nối với đầu đọc thẻ");
        rightPanel.add(lblConnectionStatus);
        
        // Separator
        JLabel separator = new JLabel(" | ");
        separator.setForeground(Color.WHITE);
        rightPanel.add(separator);
        
        DatabaseConnection.AdminUserInfo currentUser = LoginFrame.getCurrentAdminUser();
        if (currentUser != null) {
            String displayName = currentUser.fullName != null && !currentUser.fullName.isEmpty() 
                ? currentUser.fullName 
                : currentUser.username;
            JLabel userLabel = new JLabel("Xin chào: " + displayName);
            userLabel.setForeground(Color.WHITE);
            userLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            rightPanel.add(userLabel);
        }
        
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setPreferredSize(new Dimension(100, 30));
        btnLogout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                    AdminFrame.this,
                    "Bạn có chắc chắn muốn đăng xuất?",
                    "Xác nhận",
                    JOptionPane.YES_NO_OPTION
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    LoginFrame.clearCurrentAdminUser();
                    if (cardManager != null && cardManager.isConnected()) {
                        cardManager.disconnect();
                    }
                    dispose();
                    new LoginFrame().setVisible(true);
                }
            }
        });
        rightPanel.add(btnLogout);
        header.add(rightPanel, BorderLayout.EAST);
        
        // Khởi tạo status indicator
        updateConnectionStatusIndicator(false);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        
        cardIssuePanel = new CardIssuePanel(cardManager, apduCommands);
        cardManagePanel = new CardManagePanel(cardManager, apduCommands);
        resetPinPanel = new ResetPinPanel(cardManager, apduCommands);

        tabs.addTab("Phát hành thẻ User", cardIssuePanel);
        tabs.addTab("Quản lý thông tin thẻ", cardManagePanel);
        tabs.addTab("Reset PIN User", resetPinPanel);
        
        // Thêm listener để auto-check connection khi chuyển tab
        tabs.addChangeListener(e -> {
            int selectedIndex = tabs.getSelectedIndex();
            System.out.println("[AdminFrame] Tab changed to index: " + selectedIndex);
            
            // Kiểm tra connection status (không hiển thị dialog, chỉ log)
            boolean isReady = CardConnectionHelper.checkConnectionStatus(cardManager, apduCommands);
            String statusText = CardConnectionHelper.getConnectionStatusText(cardManager, apduCommands);
            System.out.println("[AdminFrame] Connection status: " + statusText + " (ready: " + isReady + ")");
            
            // Cập nhật status indicator nếu có
            updateConnectionStatusIndicator(isReady);
        });

        // Layout
        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }
    
    /**
     * Cập nhật status indicator cho kết nối thẻ
     * @param isConnected true nếu đã kết nối và channel sẵn sàng
     */
    private void updateConnectionStatusIndicator(boolean isConnected) {
        if (lblConnectionStatus == null) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            if (isConnected) {
                lblConnectionStatus.setText("● Đã kết nối");
                lblConnectionStatus.setForeground(new Color(200, 255, 200)); // Màu xanh nhạt
                lblConnectionStatus.setToolTipText("Đã kết nối với đầu đọc thẻ và sẵn sàng sử dụng");
            } else {
                lblConnectionStatus.setText("● Chưa kết nối");
                lblConnectionStatus.setForeground(new Color(255, 200, 200)); // Màu đỏ nhạt
                lblConnectionStatus.setToolTipText("Chưa kết nối với đầu đọc thẻ. Kết nối sẽ được thiết lập tự động khi cần.");
            }
        });
    }
}

