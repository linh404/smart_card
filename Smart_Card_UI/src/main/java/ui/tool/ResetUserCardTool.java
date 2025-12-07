package ui.tool;

import card.CardManager;
import card.APDUCommands;
import card.UserAppletHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * ResetUserCardTool - Tool reset thẻ User
 * UI riêng biệt để reset toàn bộ dữ liệu trên thẻ User
 */
public class ResetUserCardTool extends JFrame {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserAppletHelper userHelper;
    
    private JTextArea txtCardInfo;
    private JPasswordField txtPinAdmin;
    private JButton btnCheckCard;
    private JButton btnResetCard;
    private JTextArea txtLog;
    private JLabel lblStatus;
    
    public ResetUserCardTool() {
        initUI();
        cardManager = CardManager.getInstance();
    }
    
    private void initUI() {
        setTitle("Công Cụ Reset Thẻ User");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(153, 0, 0));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("CÔNG CỤ RESET THẺ USER");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Info panel - Hiển thị thông tin thẻ
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Thông tin thẻ hiện tại"));
        
        txtCardInfo = new JTextArea(8, 60);
        txtCardInfo.setEditable(false);
        txtCardInfo.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtCardInfo.setBackground(new Color(245, 245, 245));
        txtCardInfo.setText("Chưa kiểm tra thẻ. Nhấn 'Kiểm tra thẻ' để xem thông tin.");
        JScrollPane infoScroll = new JScrollPane(txtCardInfo);
        infoPanel.add(infoScroll, BorderLayout.CENTER);
        
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        
        // Control panel
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Điều khiển"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Hướng dẫn
        JLabel instructionLabel = new JLabel("<html><b>Cảnh báo:</b> Reset thẻ sẽ <b>XÓA TẤT CẢ</b> dữ liệu trên thẻ User!<br>" +
            "Bao gồm: cardId_user, PK_user, UserData, số dư, lịch sử giao dịch, PIN, v.v.<br><br>" +
            "<b>Hướng dẫn:</b><br>" +
            "1. Cắm thẻ User vào đầu đọc<br>" +
            "2. Nhấn 'Kiểm tra thẻ' để xem thông tin hiện tại<br>" +
            "3. Nhập PIN Admin<br>" +
            "4. Nhấn 'Reset thẻ' để xóa tất cả dữ liệu</html>");
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        instructionLabel.setForeground(new Color(153, 0, 0));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        controlPanel.add(instructionLabel, gbc);
        
        // PIN Admin
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        controlPanel.add(new JLabel("PIN Admin:"), gbc);
        
        txtPinAdmin = new JPasswordField(20);
        txtPinAdmin.setFont(new Font("Courier New", Font.PLAIN, 14));
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        controlPanel.add(txtPinAdmin, gbc);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        
        btnCheckCard = new JButton("Kiểm tra thẻ");
        btnCheckCard.setFont(new Font("Arial", Font.BOLD, 12));
        btnCheckCard.setPreferredSize(new Dimension(150, 35));
        btnCheckCard.setBackground(new Color(0, 102, 204));
        btnCheckCard.setForeground(Color.WHITE);
        btnPanel.add(btnCheckCard);
        
        btnResetCard = new JButton("RESET THẺ");
        btnResetCard.setFont(new Font("Arial", Font.BOLD, 14));
        btnResetCard.setPreferredSize(new Dimension(200, 40));
        btnResetCard.setBackground(Color.RED);
        btnResetCard.setForeground(Color.WHITE);
        btnResetCard.setEnabled(false);
        btnPanel.add(btnResetCard);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        controlPanel.add(btnPanel, gbc);
        
        // Status
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        gbc.gridy = 3;
        controlPanel.add(lblStatus, gbc);
        
        mainPanel.add(controlPanel, BorderLayout.CENTER);
        
        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Nhật ký"));
        txtLog = new JTextArea(8, 80);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Courier New", Font.PLAIN, 11));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(txtLog);
        logPanel.add(logScroll, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Event handlers
        btnCheckCard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkCard();
            }
        });
        
        btnResetCard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetCard();
            }
        });
    }
    
    /**
     * Kiểm tra thẻ User và hiển thị thông tin
     */
    private void checkCard() {
        try {
            btnCheckCard.setEnabled(false);
            lblStatus.setText("Trạng thái: Đang kiểm tra thẻ...");
            lblStatus.setForeground(Color.BLUE);
            log("=== KIỂM TRA THẺ USER ===");
            
            // 1. Kết nối với đầu đọc thẻ
            log("Bước 1: Đang kết nối với đầu đọc thẻ...");
            if (!cardManager.isConnected()) {
                if (!cardManager.connect()) {
                    log("✗ LỖI: Không thể kết nối với đầu đọc thẻ!");
                    lblStatus.setText("Trạng thái: Lỗi kết nối");
                    lblStatus.setForeground(Color.RED);
                    btnCheckCard.setEnabled(true);
                    return;
                }
            }
            log("✓ Đã kết nối với đầu đọc thẻ");
            
            // 2. Select UserApplet
            log("Bước 2: Đang select UserApplet...");
            if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
                log("✗ LỖI: Không tìm thấy UserApplet trên thẻ!");
                lblStatus.setText("Trạng thái: Không tìm thấy applet");
                lblStatus.setForeground(Color.RED);
                btnCheckCard.setEnabled(true);
                return;
            }
            log("✓ Đã chọn UserApplet thành công");
            apduCommands = new APDUCommands(cardManager.getChannel());
            userHelper = new UserAppletHelper(apduCommands);
            
            // 3. Đọc thông tin thẻ
            log("Bước 3: Đang đọc thông tin thẻ...");
            String cardInfo = userHelper.getCardInfo();
            txtCardInfo.setText(cardInfo);
            log("✓ Đã đọc thông tin thẻ thành công");
            log("\n" + cardInfo);
            
            // 4. Kiểm tra trạng thái thẻ
            boolean isIssued = userHelper.isCardIssued();
            if (isIssued) {
                log("Thẻ đã được phát hành (có cardId_user)");
                btnResetCard.setEnabled(true);
            } else {
                log("Thẻ chưa được phát hành (chưa có cardId_user)");
                btnResetCard.setEnabled(false);
                JOptionPane.showMessageDialog(this,
                    "Thẻ chưa được phát hành, không cần reset!",
                    "Thông báo",
                    JOptionPane.INFORMATION_MESSAGE);
            }
            
            log("=== KIỂM TRA HOÀN TẤT ===");
            lblStatus.setText("Trạng thái: Kiểm tra thành công");
            lblStatus.setForeground(new Color(0, 153, 0));
            btnCheckCard.setEnabled(true);
            
        } catch (Exception e) {
            log("✗ LỖI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            btnCheckCard.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                "Lỗi khi kiểm tra thẻ: " + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Reset thẻ User (xóa tất cả dữ liệu)
     */
    private void resetCard() {
        String pinAdmin = new String(txtPinAdmin.getPassword());
        if (pinAdmin.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Vui lòng nhập PIN Admin!",
                "Lỗi",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Xác nhận reset
        int confirm = JOptionPane.showConfirmDialog(this,
            "BẠN CÓ CHẮC CHẮN MUỐN RESET THẺ USER?\n\n" +
            "Hành động này sẽ XÓA TẤT CẢ dữ liệu trên thẻ:\n" +
            "- cardId_user\n" +
            "- PK_user, SK_user\n" +
            "- UserData (họ tên, ID bệnh nhân, v.v.)\n" +
            "- Số dư\n" +
            "- Lịch sử giao dịch\n" +
            "- PIN User\n" +
            "- Tất cả dữ liệu khác\n\n" +
            "Hành động này KHÔNG THỂ HOÀN TÁC!\n\n" +
            "Bạn có chắc chắn muốn tiếp tục?",
            "XÁC NHẬN RESET THẺ",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            log("Người dùng hủy bỏ reset");
            return;
        }
        
        try {
            btnResetCard.setEnabled(false);
            btnCheckCard.setEnabled(false);
            lblStatus.setText("Trạng thái: Đang reset thẻ...");
            lblStatus.setForeground(Color.BLUE);
            log("=== BẮT ĐẦU RESET THẺ USER ===");
            
            // Kiểm tra kết nối
            if (!cardManager.isConnected()) {
                if (!cardManager.connect()) {
                    log("✗ LỖI: Không thể kết nối với đầu đọc thẻ!");
                    lblStatus.setText("Trạng thái: Lỗi kết nối");
                    lblStatus.setForeground(Color.RED);
                    btnResetCard.setEnabled(true);
                    btnCheckCard.setEnabled(true);
                    return;
                }
            }
            
            // Select UserApplet
            if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
                log("✗ LỖI: Không tìm thấy UserApplet trên thẻ!");
                lblStatus.setText("Trạng thái: Không tìm thấy applet");
                lblStatus.setForeground(Color.RED);
                btnResetCard.setEnabled(true);
                btnCheckCard.setEnabled(true);
                return;
            }
            
            if (apduCommands == null) {
                apduCommands = new APDUCommands(cardManager.getChannel());
            }
            if (userHelper == null) {
                userHelper = new UserAppletHelper(apduCommands);
            }
            
            // Reset thẻ
            log("Đang reset thẻ với PIN Admin...");
            byte[] pinAdminBytes = pinAdmin.getBytes();
            boolean success = userHelper.resetCard(pinAdminBytes);
            
            if (success) {
                log("✓ Reset thẻ thành công!");
                log("Tất cả dữ liệu đã được xóa khỏi thẻ");
                
                // Kiểm tra lại thẻ sau khi reset
                log("Đang kiểm tra lại thẻ sau khi reset...");
                String cardInfo = userHelper.getCardInfo();
                txtCardInfo.setText(cardInfo);
                log(cardInfo);
                
                log("=== RESET THẺ THÀNH CÔNG ===");
                lblStatus.setText("Trạng thái: Reset thành công!");
                lblStatus.setForeground(new Color(0, 153, 0));
                
                JOptionPane.showMessageDialog(this,
                    "Reset thẻ User thành công!\n\n" +
                    "Tất cả dữ liệu đã được xóa khỏi thẻ.\n" +
                    "Bạn có thể phát hành thẻ mới ngay bây giờ.",
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE);
                
                btnResetCard.setEnabled(false);
            } else {
                log("✗ Reset thẻ thất bại!");
                log("Có thể PIN Admin không đúng hoặc applet không hỗ trợ reset");
                lblStatus.setText("Trạng thái: Reset thất bại");
                lblStatus.setForeground(Color.RED);
                
                JOptionPane.showMessageDialog(this,
                    "Reset thẻ thất bại!\n\n" +
                    "Nguyên nhân có thể:\n" +
                    "- PIN Admin không đúng\n" +
                    "- Applet không hỗ trợ lệnh reset\n" +
                    "- Lỗi kết nối với thẻ\n\n" +
                    "Vui lòng kiểm tra console để xem chi tiết.",
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            }
            
            btnCheckCard.setEnabled(true);
            
        } catch (Exception e) {
            log("✗ LỖI: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            btnResetCard.setEnabled(true);
            btnCheckCard.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                "Lỗi khi reset thẻ: " + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
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
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ResetUserCardTool().setVisible(true);
            }
        });
    }
}

