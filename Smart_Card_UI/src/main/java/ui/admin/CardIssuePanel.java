package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import model.UserData;
import model.UserCardSnapshot;
import util.AdminPinDerivation;
import util.EnvFileLoader;
import util.UserDemoSnapshotManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * CardIssuePanel - Panel phát hành thẻ User mới
 * Theo đặc tả: Admin nhập thông tin bệnh nhân, gửi xuống thẻ User để sinh MK_user, PK_user
 */
public class CardIssuePanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    
    private JTextField txtHoTen, txtIdBenhNhan, txtNgaySinh, txtQueQuan, txtMaBHYT;
    private JPasswordField txtPinUserDefault;
    private JButton btnPhatHanh;

    public CardIssuePanel(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        // Load .env file on initialization
        EnvFileLoader.load();
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Phát hành thẻ User mới"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Họ tên
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Họ tên:"), gbc);
        txtHoTen = new JTextField(20);
        gbc.gridx = 1;
        formPanel.add(txtHoTen, gbc);

        // ID bệnh nhân (chỉ xem, tự động tăng từ database)
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("ID bệnh nhân:"), gbc);
        txtIdBenhNhan = new JTextField(15);
        txtIdBenhNhan.setEditable(false); // Chỉ đọc, không cho phép nhập
        txtIdBenhNhan.setBackground(new Color(240, 240, 240)); // Màu xám để thể hiện là chỉ đọc
        gbc.gridx = 1;
        formPanel.add(txtIdBenhNhan, gbc);

        // Ngày sinh
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Ngày sinh:"), gbc);
        txtNgaySinh = new JTextField(20);
        gbc.gridx = 1;
        formPanel.add(txtNgaySinh, gbc);

        // Quê quán
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Quê quán:"), gbc);
        txtQueQuan = new JTextField(20);
        gbc.gridx = 1;
        formPanel.add(txtQueQuan, gbc);

        // Mã BHYT
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Mã BHYT:"), gbc);
        txtMaBHYT = new JTextField(20);
        gbc.gridx = 1;
        formPanel.add(txtMaBHYT, gbc);

        // PIN User mặc định
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("PIN User mặc định:"), gbc);
        txtPinUserDefault = new JPasswordField(20);
        gbc.gridx = 1;
        formPanel.add(txtPinUserDefault, gbc);
        
        // Hướng dẫn (V3)
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.gridwidth = 2;
        JLabel lblNote = new JLabel("<html><small><i>Lưu ý (V3): PIN_admin_reset sẽ được derive tự động từ K_master và cardID. Không cần nhập PIN admin.</i></small></html>");
        lblNote.setForeground(new Color(100, 100, 100));
        formPanel.add(lblNote, gbc);
        gbc.gridwidth = 1;

        // Nút phát hành
        btnPhatHanh = new JButton("Phát hành thẻ");
        btnPhatHanh.setFont(new Font("Arial", Font.BOLD, 14));
        btnPhatHanh.setPreferredSize(new Dimension(200, 40));

        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnPhatHanh);

        // Event handlers
        btnPhatHanh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                issueCard();
            }
        });

        add(formPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
        
        // Tự động tạo ID bệnh nhân khi khởi tạo form
        autoGeneratePatientId();
    }

    /**
     * Tự động tạo ID bệnh nhân tiếp theo
     */
    private void autoGeneratePatientId() {
        try {
            System.out.println("[CardIssuePanel] autoGeneratePatientId: Lấy ID bệnh nhân tiếp theo...");
            String nextId = DatabaseConnection.getNextPatientId();
            txtIdBenhNhan.setText(nextId);
            System.out.println("[CardIssuePanel] autoGeneratePatientId: ID = " + nextId);
        } catch (Exception e) {
            System.err.println("[CardIssuePanel] autoGeneratePatientId: Lỗi - " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi khi lấy ID bệnh nhân tự động: " + e.getMessage(), 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Validate form input (V3)
     */
    private String validateForm() {
        String hoTen = txtHoTen.getText().trim();
        String idBenhNhan = txtIdBenhNhan.getText().trim();
        String ngaySinh = txtNgaySinh.getText().trim();
        String queQuan = txtQueQuan.getText().trim();
        String maBHYT = txtMaBHYT.getText().trim();
        String pinUserDefault = new String(txtPinUserDefault.getPassword());

        // Kiểm tra các trường bắt buộc
        if (hoTen.isEmpty()) {
            return "Vui lòng nhập họ tên!";
        }
        if (hoTen.length() < 2 || hoTen.length() > 100) {
            return "Họ tên phải từ 2 đến 100 ký tự!";
        }
        
        if (idBenhNhan.isEmpty()) {
            // Nếu ID bệnh nhân trống, tự động tạo lại
            autoGeneratePatientId();
            idBenhNhan = txtIdBenhNhan.getText().trim();
            if (idBenhNhan.isEmpty()) {
                return "Không thể tạo ID bệnh nhân tự động. Vui lòng thử lại!";
            }
        }
        if (!idBenhNhan.matches("^[0-9]+$")) {
            return "ID bệnh nhân chỉ được chứa số!";
        }
        
        if (ngaySinh.isEmpty()) {
            return "Vui lòng nhập ngày sinh!";
        }
        // Validate ngày sinh hợp lệ - chấp nhận cả D/M/YYYY và DD/MM/YYYY
        String ngaySinhTrimmed = ngaySinh.trim();
        Date date = null;
        
        // Thử parse với format linh hoạt d/M/yyyy (cho phép 1-2 chữ số cho ngày và tháng)
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy");
            sdf.setLenient(false);
            date = sdf.parse(ngaySinhTrimmed);
        } catch (ParseException e) {
            // Nếu không parse được với format linh hoạt, thử với format chuẩn dd/MM/yyyy
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("dd/MM/yyyy");
                sdf2.setLenient(false);
                date = sdf2.parse(ngaySinhTrimmed);
            } catch (ParseException e2) {
                return "Ngày sinh phải đúng định dạng DD/MM/YYYY (ví dụ: 01/01/2000 hoặc 1/1/2000)!";
            }
        }
        
        // Kiểm tra ngày không được là tương lai
        if (date != null) {
            Date now = new Date();
            if (date.after(now)) {
                return "Ngày sinh không thể là ngày trong tương lai!";
            }
            // Tự động format lại về chuẩn DD/MM/YYYY và cập nhật vào field
            SimpleDateFormat sdfFormat = new SimpleDateFormat("dd/MM/yyyy");
            String formatted = sdfFormat.format(date);
            if (!ngaySinhTrimmed.equals(formatted)) {
                txtNgaySinh.setText(formatted);
            }
        }
        
        if (queQuan.isEmpty()) {
            return "Vui lòng nhập quê quán!";
        }
        if (queQuan.length() > 200) {
            return "Quê quán không được vượt quá 200 ký tự!";
        }
        
        if (maBHYT.isEmpty()) {
            return "Vui lòng nhập mã BHYT!";
        }
        if (maBHYT.length() < 5 || maBHYT.length() > 50) {
            return "Mã BHYT phải từ 5 đến 50 ký tự!";
        }
        
        if (pinUserDefault.isEmpty()) {
            return "Vui lòng nhập PIN User mặc định!";
        }
        if (pinUserDefault.length() != 6) {
            return "PIN User phải là 6 chữ số!";
        }
        if (!pinUserDefault.matches("^[0-9]+$")) {
            return "PIN User chỉ được chứa số!";
        }
        
        return null; // Validation thành công
    }

    private void issueCard() {
        System.out.println("[CardIssuePanel] ========== BẮT ĐẦU PHÁT HÀNH THẺ ==========");
        
        try {
            // 1. Validate form
            String validationError = validateForm();
            if (validationError != null) {
                System.err.println("[CardIssuePanel] issueCard: Validation thất bại - " + validationError);
                JOptionPane.showMessageDialog(this, validationError, "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
                return;
            }
            System.out.println("[CardIssuePanel] issueCard: Validation thành công");

            // 2. Kiểm tra kết nối thẻ
            if (!cardManager.isConnected()) {
                System.err.println("[CardIssuePanel] issueCard: Chưa kết nối thẻ");
                if (!cardManager.connect()) {
                    JOptionPane.showMessageDialog(this, 
                        "Không thể kết nối với đầu đọc thẻ!\nVui lòng kiểm tra:\n" +
                        "1. Đầu đọc thẻ đã được cắm\n" +
                        "2. Thẻ đã được đặt vào đầu đọc\n" +
                        "3. Driver đầu đọc đã được cài đặt", 
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                System.out.println("[CardIssuePanel] issueCard: Đã kết nối thẻ thành công");
            }

            // 3. Select UserApplet
            System.out.println("[CardIssuePanel] issueCard: Đang select UserApplet...");
            if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
                System.err.println("[CardIssuePanel] issueCard: Không thể select UserApplet");
                JOptionPane.showMessageDialog(this, 
                    "Không tìm thấy UserApplet trên thẻ!\n\n" +
                    "Vui lòng kiểm tra:\n" +
                    "1. Thẻ User đã được cắm đúng cách\n" +
                    "2. UserApplet đã được cài đặt trên thẻ\n" +
                    "3. AID của applet đúng: " + bytesToHex(APDUCommands.AID_USER), 
                    "Lỗi UserApplet", JOptionPane.ERROR_MESSAGE);
                return;
            }
            System.out.println("[CardIssuePanel] issueCard: Select UserApplet thành công");
            
            // 3.1. Kiểm tra thẻ đã có cardId_user chưa (có thể là data cũ hoặc thẻ đã được issue)
            System.out.println("[CardIssuePanel] issueCard: Kiểm tra thẻ đã có cardId_user chưa...");
            byte[] existingCardId = apduCommands.getStatus(); // V3: Use getStatus() instead of getCardId()
            
            // Kiểm tra cardId có hợp lệ không (không null, không rỗng, và không phải toàn số 0)
            boolean hasCardId = false;
            if (existingCardId != null && existingCardId.length == 16) {
                // Kiểm tra xem có phải toàn số 0 không
                boolean allZeros = true;
                for (int i = 0; i < existingCardId.length; i++) {
                    if (existingCardId[i] != 0) {
                        allZeros = false;
                        break;
                    }
                }
                hasCardId = !allZeros;
            }
            
            if (hasCardId) {
                String existingCardIdHex = bytesToHex(existingCardId);
                System.out.println("[CardIssuePanel] issueCard: Thẻ đã có cardId_user = " + existingCardIdHex);
                System.out.println("[CardIssuePanel] issueCard: Lưu ý - AdminApplet và UserApplet lưu data riêng, cardId_user có thể là data cũ");
                
                int choice = JOptionPane.showConfirmDialog(this,
                    "Thẻ đã có cardId_user!\n\n" +
                    "Card ID hiện tại: " + existingCardIdHex + "\n\n" +
                    "Lưu ý: AdminApplet và UserApplet lưu data riêng.\n" +
                    "CardId này có thể là:\n" +
                    "- Data cũ từ lần test trước\n" +
                    "- Thẻ đã được phát hành trước đó\n\n" +
                    "Bạn có muốn tiếp tục phát hành? (Applet sẽ ghi đè cardId cũ)",
                    "Cảnh báo - Thẻ đã có CardId",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    System.out.println("[CardIssuePanel] issueCard: Người dùng hủy bỏ");
                    return;
                }
                System.out.println("[CardIssuePanel] issueCard: Người dùng chọn tiếp tục - sẽ ghi đè cardId cũ");
            } else {
                System.out.println("[CardIssuePanel] issueCard: Thẻ chưa có cardId_user (thẻ mới)");
            }

            // 4. Chuẩn bị dữ liệu
            UserData userData = new UserData();
            userData.setHoTen(txtHoTen.getText().trim());
            userData.setIdBenhNhan(txtIdBenhNhan.getText().trim());
            userData.setNgaySinh(txtNgaySinh.getText().trim());
            userData.setQueQuan(txtQueQuan.getText().trim());
            userData.setMaBHYT(txtMaBHYT.getText().trim());
            
            String pinUserDefault = new String(txtPinUserDefault.getPassword());
            
            System.out.println("[CardIssuePanel] issueCard: Thông tin bệnh nhân:");
            System.out.println("  - Họ tên: " + userData.getHoTen());
            System.out.println("  - ID bệnh nhân: " + userData.getIdBenhNhan());
            System.out.println("  - Ngày sinh: " + userData.getNgaySinh());
            System.out.println("  - Quê quán: " + userData.getQueQuan());
            System.out.println("  - Mã BHYT: " + userData.getMaBHYT());

            byte[] userDataBytes = userData.toBytes();
            byte[] pinUserBytes = pinUserDefault.getBytes();
            
            // V3: Backend sinh cardID trước, derive PIN admin, rồi gửi xuống thẻ
            System.out.println("[CardIssuePanel] issueCard: Sinh cardID và derive PIN_admin_reset...");
            
            // 5.1. Sinh cardID ngẫu nhiên (16 bytes)
            java.security.SecureRandom random = new java.security.SecureRandom();
            byte[] cardIdUser = new byte[16];
            random.nextBytes(cardIdUser);
            String cardIdHex = bytesToHex(cardIdUser);
            System.out.println("[CardIssuePanel] issueCard: Generated CardId = " + cardIdHex);
            
            // 5.2. Derive PIN_admin_reset từ K_master và cardID
            String pinAdminReset;
            try {
                EnvFileLoader.load();
                pinAdminReset = AdminPinDerivation.deriveAdminResetPIN(cardIdUser);
                System.out.println("[CardIssuePanel] issueCard: PIN_admin_reset = " + pinAdminReset);
            } catch (Exception e) {
                System.err.println("[CardIssuePanel] issueCard: Lỗi khi derive PIN_admin_reset: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "CẢNH BÁO: Không thể derive PIN_admin_reset!\n\n" +
                    "Lỗi: " + e.getMessage() + "\n\n" +
                    "Vui lòng kiểm tra K_MASTER environment variable!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            byte[] pinAdminBytes = pinAdminReset.getBytes();

            // 5.3. Gửi lệnh ISSUE_CARD xuống thẻ với cardID và PIN admin đã derive
            System.out.println("[CardIssuePanel] issueCard: Gửi lệnh ISSUE_CARD xuống thẻ với cardID và PIN admin...");
            byte[] result = apduCommands.issueCard(cardIdUser, userDataBytes, pinUserBytes, pinAdminBytes);
            
            // V3: Response chỉ là status byte (0x00 = success)
            if (result == null || result.length < 1 || result[0] != 0x00) {
                System.err.println("[CardIssuePanel] issueCard: Phát hành thẻ thất bại - status = " + 
                    (result != null && result.length > 0 ? String.format("0x%02X", result[0]) : "null"));
                String errorDetail = "Phát hành thẻ thất bại!\n\n" +
                    "Nguyên nhân có thể:\n" +
                    "1. Thẻ đã được phát hành trước đó (initialized = 1)\n" +
                    "2. Dữ liệu quá lớn hoặc format không đúng\n" +
                    "3. Lỗi applet trên thẻ\n" +
                    "4. Applet chưa được cài đặt đúng (V3)\n\n" +
                    "Vui lòng kiểm tra console để xem chi tiết lỗi.";
                JOptionPane.showMessageDialog(this, errorDetail, 
                    "Phát hành thẻ thất bại", JOptionPane.ERROR_MESSAGE);
                return;
            }
            System.out.println("[CardIssuePanel] issueCard: ISSUE_CARD thành công! Status = 0x00");
            
            // 6. Verify cardID trên thẻ khớp với cardID đã gửi
            System.out.println("[CardIssuePanel] issueCard: Verify cardID trên thẻ...");
            byte[] cardIdOnCard = apduCommands.getStatus();
            
            if (cardIdOnCard == null || cardIdOnCard.length != 16) {
                System.err.println("[CardIssuePanel] issueCard: Không thể đọc cardID từ thẻ!");
                JOptionPane.showMessageDialog(this,
                    "Phát hành thẻ thành công nhưng không thể đọc cardID!\n\n" +
                    "Vui lòng thử lại hoặc kiểm tra thẻ.",
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            String cardIdOnCardHex = bytesToHex(cardIdOnCard);
            if (!cardIdHex.equalsIgnoreCase(cardIdOnCardHex)) {
                System.err.println("[CardIssuePanel] issueCard: CardID không khớp! Expected: " + cardIdHex + ", Got: " + cardIdOnCardHex);
                JOptionPane.showMessageDialog(this,
                    "CẢNH BÁO: CardID trên thẻ không khớp với CardID đã gửi!\n\n" +
                    "CardID đã gửi: " + cardIdHex + "\n" +
                    "CardID trên thẻ: " + cardIdOnCardHex + "\n\n" +
                    "Có thể thẻ đã tự sinh cardID khác.",
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                // Update cardIdHex với giá trị từ thẻ
                cardIdHex = cardIdOnCardHex;
                System.arraycopy(cardIdOnCard, 0, cardIdUser, 0, 16);
            } else {
                System.out.println("[CardIssuePanel] issueCard: CardID khớp ✓");
            }

            // 7. Lưu vào Supabase
            System.out.println("[CardIssuePanel] issueCard: Lưu vào database...");
            
            // Lấy admin ID từ current logged-in user
            DatabaseConnection.AdminUserInfo adminUser = LoginFrame.getCurrentAdminUser();
            Integer adminId = (adminUser != null) ? adminUser.id : null;
            
            // 7.1. Lưu thông tin bệnh nhân vào bảng patients trước
            System.out.println("[CardIssuePanel] issueCard: Lưu thông tin bệnh nhân vào bảng patients...");
            if (!DatabaseConnection.savePatient(
                    userData.getIdBenhNhan(),
                    userData.getHoTen(),
                    userData.getNgaySinh(),
                    userData.getQueQuan(),
                    userData.getMaBHYT())) {
                System.err.println("[CardIssuePanel] issueCard: Lưu thông tin bệnh nhân thất bại");
                JOptionPane.showMessageDialog(this, 
                    "Phát hành thẻ thành công nhưng lưu thông tin bệnh nhân thất bại!\n" +
                    "Card ID: " + cardIdHex + "\n\n" +
                    "Vui lòng kiểm tra database connection.", 
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            } else {
                System.out.println("[CardIssuePanel] issueCard: Đã lưu thông tin bệnh nhân thành công");
            }
            
            // 7.2. Lưu thông tin thẻ vào bảng user_cards
            System.out.println("[CardIssuePanel] issueCard: Lưu thông tin thẻ vào bảng user_cards...");
            if (!DatabaseConnection.saveUserCard(cardIdUser, txtIdBenhNhan.getText(), adminId)) {
                System.err.println("[CardIssuePanel] issueCard: Lưu vào database thất bại");
                JOptionPane.showMessageDialog(this, 
                    "Phát hành thẻ thành công nhưng lưu vào database thất bại!\n" +
                    "Card ID: " + cardIdHex + "\n\n" +
                    "Vui lòng lưu thông tin này để tra cứu sau.", 
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            } else {
                System.out.println("[CardIssuePanel] issueCard: Đã lưu vào database thành công");
            }
            
            // V3: Không lưu PIN_admin_reset vào DB nữa, chỉ derive khi cần
            System.out.println("[CardIssuePanel] issueCard: V3 - PIN_admin_reset không lưu trong DB, chỉ derive động khi cần");

            // 8. Lưu snapshot demo vào file JSON
            System.out.println("[CardIssuePanel] issueCard: Lưu snapshot demo...");
            UserCardSnapshot snapshot = new UserCardSnapshot();
            snapshot.setCardIdHex(cardIdHex);
            snapshot.setHoTen(userData.getHoTen());
            snapshot.setIdBenhNhan(userData.getIdBenhNhan());
            snapshot.setNgaySinh(userData.getNgaySinh());
            snapshot.setQueQuan(userData.getQueQuan());
            snapshot.setMaBHYT(userData.getMaBHYT());
            snapshot.setPinUserDefault(pinUserDefault);
            snapshot.setPinAdminReset(pinAdminReset); // Lưu PIN admin reset để demo
            
            if (UserDemoSnapshotManager.saveSnapshot(snapshot)) {
                System.out.println("[CardIssuePanel] issueCard: Đã lưu snapshot demo cho cardId: " + cardIdHex);
            } else {
                System.err.println("[CardIssuePanel] issueCard: Lỗi khi lưu snapshot demo");
            }
            
            // 9. Lưu audit log
            if (adminUser != null) {
                String details = String.format("Issue card: cardId=%s, patientId=%s, hoTen=%s", 
                    cardIdHex, userData.getIdBenhNhan(), userData.getHoTen());
                DatabaseConnection.saveAdminAuditLog(adminUser.id, "ISSUE_CARD", cardIdUser, details, null);
            }

            System.out.println("[CardIssuePanel] ========== PHÁT HÀNH THẺ THÀNH CÔNG ==========");
            String successMsg = "Phát hành thẻ thành công!\n\n" +
                "Card ID: " + cardIdHex + "\n" +
                "ID bệnh nhân: " + userData.getIdBenhNhan() + "\n" +
                "Họ tên: " + userData.getHoTen() + "\n\n" +
                "PIN_admin_reset (V3 - derive từ K_master): " + pinAdminReset + "\n" +
                "LƯU Ý: PIN này được derive tự động, không lưu trong DB.";
            
            JOptionPane.showMessageDialog(this, successMsg, 
                "Thành công", JOptionPane.INFORMATION_MESSAGE);

            // Clear form
            clearForm();

        } catch (Exception e) {
            System.err.println("[CardIssuePanel] issueCard: Exception - " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Lỗi: " + e.getMessage() + "\n\n" +
                "Vui lòng kiểm tra console để xem chi tiết.", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearForm() {
        txtHoTen.setText("");
        txtNgaySinh.setText("");
        txtQueQuan.setText("");
        txtMaBHYT.setText("");
        txtPinUserDefault.setText("");
        // Tự động tạo ID bệnh nhân mới sau khi clear form
        autoGeneratePatientId();
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}

