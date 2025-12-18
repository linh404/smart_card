package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import model.UserData;
import model.UserCardSnapshot;
import util.AdminPinDerivation;
import util.EnvFileLoader;
import util.UserDemoSnapshotManager;

import javax.smartcardio.CardException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * CardManagePanel - Panel quản lý/sửa thông tin thẻ User
 */
public class CardManagePanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    
    private JTextField txtCardId, txtHoTen, txtIdBenhNhan, txtNgaySinh, txtQueQuan, txtMaBHYT, txtBalance;
    private JPasswordField txtPinUserDefault;
    private JPasswordField txtPinUserForLoad; // PIN User để load data từ thẻ
    private JButton btnLoadFromCard, btnUpdate, btnLoadToCard;
    private JLabel lblAdminPinStatus; // Hiển thị trạng thái Admin PIN

    public CardManagePanel(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Quản lý thông tin thẻ"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Card ID (hiển thị từ thẻ)
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Card ID (hex):"), gbc);
        txtCardId = new JTextField(40);
        txtCardId.setEditable(false);
        txtCardId.setBackground(new Color(240, 240, 240));
        gbc.gridx = 1;
        formPanel.add(txtCardId, gbc);

        // Button để load thông tin từ thẻ
        row++;
        btnLoadFromCard = new JButton("Load từ thẻ User");
        btnLoadFromCard.setFont(new Font("Arial", Font.BOLD, 12));
        btnLoadFromCard.setPreferredSize(new Dimension(200, 35));
        gbc.gridx = 0; gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        formPanel.add(btnLoadFromCard, gbc);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;

        // Các trường thông tin
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Họ tên:"), gbc);
        txtHoTen = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(txtHoTen, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("ID bệnh nhân:"), gbc);
        txtIdBenhNhan = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(txtIdBenhNhan, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Ngày sinh:"), gbc);
        txtNgaySinh = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(txtNgaySinh, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Quê quán:"), gbc);
        txtQueQuan = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(txtQueQuan, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Mã BHYT:"), gbc);
        txtMaBHYT = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(txtMaBHYT, gbc);

        // Số dư
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Số dư (VNĐ):"), gbc);
        txtBalance = new JTextField(30);
        gbc.gridx = 1;
        formPanel.add(txtBalance, gbc);

        // PIN User để load data từ thẻ
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("PIN User (để load data):"), gbc);
        txtPinUserForLoad = new JPasswordField(20);
        txtPinUserForLoad.setToolTipText("Nhập PIN User để xác thực và load dữ liệu từ thẻ");
        gbc.gridx = 1;
        formPanel.add(txtPinUserForLoad, gbc);

        // PIN User Default (để demo)
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("PIN User mặc định:"), gbc);
        txtPinUserDefault = new JPasswordField(20);
        txtPinUserDefault.setToolTipText("PIN User mặc định dùng khi phát hành thẻ mới");
        gbc.gridx = 1;
        formPanel.add(txtPinUserDefault, gbc);

        // Admin PIN Status (tự động lấy từ database)
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Admin PIN:"), gbc);
        lblAdminPinStatus = new JLabel("Chưa load thẻ");
        lblAdminPinStatus.setForeground(new Color(100, 100, 100));
        lblAdminPinStatus.setFont(new Font("Arial", Font.ITALIC, 11));
        gbc.gridx = 1;
        formPanel.add(lblAdminPinStatus, gbc);

        // Buttons
        btnLoadToCard = new JButton("Nạp vào thẻ (ISSUE_CARD/UPDATE)");
        btnUpdate = new JButton("Lưu Snapshot");
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnLoadToCard);
        btnPanel.add(btnUpdate);

        // Event handlers
        btnLoadFromCard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadCardInfo();
            }
        });

        btnLoadToCard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadToCard();
            }
        });

        btnUpdate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveSnapshotOnly();
            }
        });

        add(formPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    /**
     * Load thông tin từ thẻ User (đọc trực tiếp từ applet)
     * Chỉ load từ thẻ, nếu thẻ trắng thì không hiển thị gì
     */
    private void loadCardInfo() {
        try {
            // Đảm bảo kết nối thẻ, channel sẵn sàng, và applet đã được select
            if (!CardConnectionHelper.ensureCardAndAppletReady(
                    cardManager, apduCommands, this, true, APDUCommands.AID_USER)) {
                clearAllFields();
                return;
            }

            // Đọc cardId
            byte[] cardId = apduCommands.getCardId();
            boolean hasCardId = (cardId != null && !isCardIdEmpty(cardId));
            
            // Nếu thẻ trắng (không có cardId), không hiển thị gì
            if (!hasCardId) {
                clearAllFields();
                JOptionPane.showMessageDialog(this, 
                    "Thẻ trắng (chưa được phát hành)!\n\n" +
                    "CardId hiện tại là rỗng.\n" +
                    "Vui lòng phát hành thẻ trước.", 
                    "Thẻ trắng", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Hiển thị Card ID
            String cardIdHex = bytesToHex(cardId);
            txtCardId.setText(cardIdHex);

            // V3: PIN_admin_reset được derive động từ K_master và cardID
            try {
                EnvFileLoader.load();
                String pinAdminReset = AdminPinDerivation.deriveAdminResetPIN(cardId);
                lblAdminPinStatus.setText("✓ PIN được derive động (V3): " + pinAdminReset);
                lblAdminPinStatus.setForeground(new Color(0, 153, 0));
            } catch (Exception e) {
                lblAdminPinStatus.setText("⚠ Không thể derive PIN (kiểm tra K_MASTER)");
                lblAdminPinStatus.setForeground(new Color(255, 0, 0));
                System.err.println("[CardManagePanel] Error deriving PIN: " + e.getMessage());
            }

            // V3: Đọc UserData từ thẻ bằng VERIFY_PIN_AND_READ_DATA
            byte[] userDataBytes = null;
            UserData userData = null;
            boolean loadedFromCard = false;
            
            // Yêu cầu nhập PIN User (V3: cần PIN để verify và đọc data)
            String pinUser = new String(txtPinUserForLoad.getPassword());
            if (pinUser.isEmpty() || pinUser.length() != 6) {
                // Hiển thị dialog để nhập PIN User
                JPasswordField pinField = new JPasswordField(20);
                int option = JOptionPane.showConfirmDialog(this,
                    new Object[]{"V3: Cần nhập PIN User (6 chữ số) để đọc dữ liệu từ thẻ.\nVui lòng nhập PIN User:", pinField},
                    "Nhập PIN User",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
                
                if (option == JOptionPane.OK_OPTION) {
                    pinUser = new String(pinField.getPassword());
                    // Validate PIN length (must be 6 digits)
                    if (pinUser.length() != 6 || !pinUser.matches("^[0-9]+$")) {
                        JOptionPane.showMessageDialog(this,
                            "PIN User phải là 6 chữ số!",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                        clearAllFields();
                        txtCardId.setText(cardIdHex);
                        return;
                    }
                    // Lưu PIN vào field để lần sau không cần nhập lại
                    txtPinUserForLoad.setText(pinUser);
                } else {
                    // Người dùng hủy
                    clearAllFields();
                    txtCardId.setText(cardIdHex); // Giữ lại Card ID
                    return;
                }
            }
            
            // V3: Gọi verifyPinAndReadData() - verify PIN và đọc data cùng lúc
            try {
                System.out.println("[CardManagePanel] V3: Đang verify PIN và đọc data từ thẻ...");
                // Sử dụng UTF-8 để đảm bảo encoding nhất quán với changePin
                byte[] pinBytes = pinUser.getBytes(StandardCharsets.UTF_8);
                userDataBytes = apduCommands.verifyPinAndReadData(pinBytes);
                
                if (userDataBytes != null && userDataBytes.length > 0) {
                    userData = UserData.fromBytes(userDataBytes);
                    loadedFromCard = (userData != null);
                    if (loadedFromCard) {
                        System.out.println("[CardManagePanel] Đã load UserData từ thẻ thành công (V3)");
                    }
                } else {
                    // PIN sai hoặc thẻ bị khóa
                    txtPinUserForLoad.setText(""); // Xóa PIN
                    clearAllFields();
                    txtCardId.setText(cardIdHex);
                    JOptionPane.showMessageDialog(this, 
                        "Không thể đọc dữ liệu từ thẻ!\n\n" +
                        "Nguyên nhân có thể:\n" +
                        "1. PIN User không đúng\n" +
                        "2. Thẻ bị khóa (blocked)\n" +
                        "3. Thẻ chưa được phát hành\n\n" +
                        "Vui lòng kiểm tra lại PIN User và thử lại.", 
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception e) {
                System.err.println("[CardManagePanel] Lỗi khi đọc UserData (V3): " + e.getMessage());
                e.printStackTrace();
                txtPinUserForLoad.setText(""); // Xóa PIN
                clearAllFields();
                txtCardId.setText(cardIdHex);
                JOptionPane.showMessageDialog(this, 
                    "Lỗi khi đọc dữ liệu từ thẻ!\n\n" +
                    "Lỗi: " + e.getMessage() + "\n\n" +
                    "Vui lòng kiểm tra:\n" +
                    "- PIN User có đúng không\n" +
                    "- Thẻ có bị khóa không\n" +
                    "- Kết nối thẻ có ổn định không", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Hiển thị dữ liệu nếu có
            if (userData != null && loadedFromCard) {
                // Hiển thị các thông tin cần thiết
                txtHoTen.setText(userData.getHoTen() != null ? userData.getHoTen() : "");
                txtIdBenhNhan.setText(userData.getIdBenhNhan() != null ? userData.getIdBenhNhan() : "");
                txtNgaySinh.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "");
                txtQueQuan.setText(userData.getQueQuan() != null ? userData.getQueQuan() : "");
                txtMaBHYT.setText(userData.getMaBHYT() != null ? userData.getMaBHYT() : "");
                txtBalance.setText(String.valueOf(userData.getBalance()));
                
                JOptionPane.showMessageDialog(this, 
                    "Đã load thông tin từ thẻ User thành công!", 
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                // Không đọc được UserData từ thẻ
                clearAllFields();
                txtCardId.setText(cardIdHex); // Giữ lại Card ID
                JOptionPane.showMessageDialog(this, 
                    "Không thể load UserData từ thẻ!\n\n" +
                    "Nguyên nhân có thể:\n" +
                    "1. Cần xác thực PIN User để đọc từ thẻ (SW: 0x6982)\n" +
                    "2. PIN User không đúng\n" +
                    "3. Thẻ chưa được phát hành đầy đủ\n" +
                    "4. Lỗi kết nối với thẻ\n\n" +
                    "Giải pháp:\n" +
                    "- Nhập đúng PIN User và thử lại\n" +
                    "- Kiểm tra kết nối thẻ\n" +
                    "- Phát hành lại thẻ nếu cần", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            clearAllFields();
            JOptionPane.showMessageDialog(this, 
                "Lỗi khi đọc thẻ: " + e.getMessage(), 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Xóa tất cả các trường thông tin (trừ Card ID nếu cần)
     */
    private void clearAllFields() {
        txtCardId.setText("");
        txtHoTen.setText("");
        txtIdBenhNhan.setText("");
        txtNgaySinh.setText("");
        txtQueQuan.setText("");
        txtMaBHYT.setText("");
        txtBalance.setText("0");
        txtPinUserDefault.setText("");
        // Không xóa txtPinUserForLoad để giữ PIN cho lần load sau
        lblAdminPinStatus.setText("Chưa load thẻ");
        lblAdminPinStatus.setForeground(new Color(100, 100, 100));
    }

    /**
     * Nạp thông tin vào thẻ User (dùng ISSUE_CARD hoặc UPDATE_USER_DATA)
     * V2: Tự động lấy Admin PIN từ database
     */
    private void loadToCard() {
        try {
            // Kiểm tra dữ liệu đầu vào
            if (txtCardId.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Card ID không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String cardIdHex = txtCardId.getText().trim();
            byte[] cardId = hexStringToBytes(cardIdHex);
            if (cardId == null || cardId.length != 16) {
                JOptionPane.showMessageDialog(this, "Card ID không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // V3: Derive Admin PIN từ K_master và cardID
            String pinAdmin = null;
            try {
                EnvFileLoader.load();
                pinAdmin = AdminPinDerivation.deriveAdminResetPIN(cardId);
                System.out.println("[CardManagePanel] Derived Admin PIN: " + pinAdmin);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Lỗi khi derive Admin PIN!\n\n" +
                    "Vui lòng kiểm tra K_MASTER environment variable.\n\n" +
                    "Lỗi: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String pinUserDefault = new String(txtPinUserDefault.getPassword());
            if (pinUserDefault.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập PIN User mặc định!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Đảm bảo kết nối thẻ, channel sẵn sàng, và applet đã được select
            if (!CardConnectionHelper.ensureCardAndAppletReady(
                    cardManager, apduCommands, this, true, APDUCommands.AID_USER)) {
                return;
            }

            // Tạo UserData
            UserData userData = new UserData();
            userData.setHoTen(txtHoTen.getText());
            userData.setIdBenhNhan(txtIdBenhNhan.getText());
            userData.setNgaySinh(txtNgaySinh.getText());
            userData.setQueQuan(txtQueQuan.getText());
            userData.setMaBHYT(txtMaBHYT.getText());
            
            // Parse balance
            try {
                long balance = Long.parseLong(txtBalance.getText().trim());
                userData.setBalance(balance);
            } catch (NumberFormatException ex) {
                userData.setBalance(0);
            }

            // Kiểm tra thẻ đã được phát hành chưa (thử đọc cardId)
            byte[] existingCardId = apduCommands.getCardId();
            boolean hasExistingCardId = existingCardId != null && !isCardIdEmpty(existingCardId);
            String existingCardIdHex = hasExistingCardId ? bytesToHex(existingCardId) : "";
            String targetCardIdHex = txtCardId.getText().trim();

            boolean success = false;

            if (!hasExistingCardId || existingCardIdHex.isEmpty() || 
                !existingCardIdHex.equalsIgnoreCase(targetCardIdHex)) {
                // Thẻ chưa được phát hành hoặc cardId khác -> dùng ISSUE_CARD
                // V3: Sinh cardID trước, derive PIN admin, rồi gửi xuống thẻ
                java.security.SecureRandom random = new java.security.SecureRandom();
                byte[] cardIdToIssue = new byte[16];
                random.nextBytes(cardIdToIssue);
                
                // Derive PIN admin từ cardID
                String pinAdminReset;
                try {
                    EnvFileLoader.load();
                    pinAdminReset = AdminPinDerivation.deriveAdminResetPIN(cardIdToIssue);
                    System.out.println("[CardManagePanel] Derived Admin PIN for new card: " + pinAdminReset);
                } catch (Exception e) {
                    System.err.println("[CardManagePanel] Lỗi khi derive Admin PIN: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "Lỗi khi derive Admin PIN!\n\n" +
                        "Vui lòng kiểm tra K_MASTER environment variable.\n\n" +
                        "Lỗi: " + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                byte[] result = apduCommands.issueCard(
                    cardIdToIssue,  // V3: Gửi cardID xuống thẻ
                    userData.toBytes(), 
                    pinUserDefault.getBytes(StandardCharsets.UTF_8), 
                    pinAdminReset.getBytes(StandardCharsets.UTF_8)  // V3: PIN admin đã derive
                );
                
                if (result != null && result.length >= 1 && result[0] == 0x00) {
                    // V3: Response chỉ là status byte, đọc cardID từ GET_STATUS
                    byte[] newCardId = apduCommands.getStatus();
                    if (newCardId == null || newCardId.length != 16) {
                        JOptionPane.showMessageDialog(this, 
                            "Phát hành thẻ thành công nhưng không thể đọc cardID!", 
                            "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    
                    String newCardIdHex = bytesToHex(newCardId);
                    txtCardId.setText(newCardIdHex);
                    
                    // V3: Không lưu Admin PIN vào database nữa, chỉ derive động
                    // Derive PIN cho card mới
                    try {
                        EnvFileLoader.load();
                        String pinAdminResetNew = AdminPinDerivation.deriveAdminResetPIN(newCardId);
                        System.out.println("[CardManagePanel] Derived Admin PIN for new card: " + pinAdminResetNew);
                        lblAdminPinStatus.setText("✓ PIN được derive động (V3): " + pinAdminResetNew);
                        lblAdminPinStatus.setForeground(new Color(0, 153, 0));
                    } catch (Exception e) {
                        System.err.println("[CardManagePanel] Lỗi khi derive Admin PIN: " + e.getMessage());
                        JOptionPane.showMessageDialog(this,
                            "CẢNH BÁO: Không thể derive Admin PIN!\n\n" +
                            "Vui lòng kiểm tra K_MASTER environment variable.\n\n" +
                            "Lỗi: " + e.getMessage(),
                            "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                    }
                    
                    JOptionPane.showMessageDialog(this, 
                        "Đã phát hành thẻ mới thành công!\nCard ID: " + newCardIdHex, 
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    success = true;
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Phát hành thẻ thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                // Thẻ đã được phát hành -> dùng UPDATE_USER_DATA
                if (apduCommands.updateUserData(userData.toBytes())) {
                    JOptionPane.showMessageDialog(this, 
                        "Đã cập nhật thông tin vào thẻ thành công!", 
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    success = true;
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Cập nhật thông tin vào thẻ thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }

            // Sau khi nạp vào thẻ thành công, tự động lưu snapshot
            if (success) {
                saveSnapshotOnly();
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Chỉ lưu snapshot vào JSON (không nạp vào thẻ)
     */
    private void saveSnapshotOnly() {
        try {
            // Kiểm tra dữ liệu đầu vào
            if (txtCardId.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Card ID không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String cardIdHex = txtCardId.getText().trim();
            String pinUserDefault = new String(txtPinUserDefault.getPassword());

            // Tạo UserData mới
            UserData userData = new UserData();
            userData.setHoTen(txtHoTen.getText());
            userData.setIdBenhNhan(txtIdBenhNhan.getText());
            userData.setNgaySinh(txtNgaySinh.getText());
            userData.setQueQuan(txtQueQuan.getText());
            userData.setMaBHYT(txtMaBHYT.getText());
            
            // Parse balance
            try {
                long balance = Long.parseLong(txtBalance.getText().trim());
                userData.setBalance(balance);
            } catch (NumberFormatException ex) {
                userData.setBalance(0);
            }

            // Lưu snapshot vào JSON
            UserCardSnapshot snapshot = new UserCardSnapshot();
            snapshot.setCardIdHex(cardIdHex);
            snapshot.setHoTen(userData.getHoTen());
            snapshot.setIdBenhNhan(userData.getIdBenhNhan());
            snapshot.setNgaySinh(userData.getNgaySinh());
            snapshot.setQueQuan(userData.getQueQuan());
            snapshot.setMaBHYT(userData.getMaBHYT());
            snapshot.setBalance(userData.getBalance());
            snapshot.setPinUserDefault(pinUserDefault);
            
            // Derive và lưu PIN admin reset nếu có thể
            try {
                byte[] cardIdBytes = UserDemoSnapshotManager.hexToBytes(cardIdHex);
                if (cardIdBytes != null && cardIdBytes.length == 16) {
                    String pinAdminReset = AdminPinDerivation.deriveAdminResetPIN(cardIdBytes);
                    snapshot.setPinAdminReset(pinAdminReset);
                    System.out.println("[CardManagePanel] saveSnapshotOnly: Đã derive và lưu PIN admin reset: " + pinAdminReset);
                }
            } catch (Exception e) {
                System.err.println("[CardManagePanel] saveSnapshotOnly: Không thể derive PIN admin reset: " + e.getMessage());
                // Không báo lỗi, chỉ log vì có thể snapshot cũ không có PIN admin
            }

            if (UserDemoSnapshotManager.saveSnapshot(snapshot)) {
                JOptionPane.showMessageDialog(this, 
                    "Đã lưu snapshot demo thành công!", 
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Lỗi khi lưu snapshot!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
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
    
    /**
     * Generate random Admin PIN (6-8 chữ số)
     */
    private String generateRandomAdminPin() {
        SecureRandom random = new SecureRandom();
        int length = 6 + random.nextInt(3); // 6, 7, hoặc 8 chữ số
        StringBuilder pin = new StringBuilder();
        for (int i = 0; i < length; i++) {
            pin.append(random.nextInt(10));
        }
        return pin.toString();
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

