package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

/**
 * UserFrame - Màn hình chính của User
 * Chứa các tab: Thông tin thẻ, Nạp tiền/Thanh toán, BHYT, Lịch sử, Đổi PIN
 */
public class UserFrame extends JFrame {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private String userPin; // V3: Lưu PIN để dùng lại khi cần
    private UserData userData; // V3: Lưu userData từ lúc login
    
    private UserInfoPanel userInfoPanel;
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
        initUI();
    }
    
    // Getter methods
    public String getUserPin() {
        return userPin;
    }
    
    public UserData getUserData() {
        return userData;
    }
    
    // Refresh userData từ thẻ (cần PIN)
    public boolean refreshUserData() {
        if (userPin == null || userPin.isEmpty()) {
            return false;
        }
        try {
            // Sử dụng UTF-8 để đảm bảo encoding nhất quán với changePin
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
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 153, 102));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("HỆ THỐNG THẺ THÔNG MINH BỆNH VIỆN");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        
        userInfoPanel = new UserInfoPanel(cardManager, apduCommands, this);
        transactionPanel = new TransactionPanel(cardManager, apduCommands, this);
        bhytPanel = new BHYTPanel(cardManager, apduCommands, this);
        historyPanel = new HistoryPanel(cardManager, apduCommands, this);
        changePinPanel = new ChangePinPanel(cardManager, apduCommands, this);

        tabs.addTab("Thông tin thẻ", userInfoPanel);
        tabs.addTab("Nạp tiền/Thanh toán", transactionPanel);
        tabs.addTab("Thông tin BHYT", bhytPanel);
        tabs.addTab("Lịch sử giao dịch", historyPanel);
        tabs.addTab("Đổi PIN", changePinPanel);

        // Layout
        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }
}

