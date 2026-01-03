package ui.admin;

import card.CardManager;
import card.APDUCommands;
import ui.ModernUITheme;
import db.DatabaseConnection;
import model.UserData;
import model.UserCardSnapshot;
import util.AdminPinDerivation;
import util.EnvFileLoader;
import util.MessageHelper;
import util.UserDemoSnapshotManager;
import util.ImageHelper; // V6: Import ImageHelper cho upload ảnh

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Base64;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.io.File; // V6: Import cho upload ảnh

/**
 * CardIssuePanel - Panel phát hành thẻ User mới
 * Theo đặc tả: Admin nhập thông tin bệnh nhân, gửi xuống thẻ User để sinh
 * MK_user, PK_user
 */
public class CardIssuePanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;

    private ModernUITheme.RoundedTextField txtHoTen, txtIdBenhNhan, txtNgaySinh, txtQueQuan, txtMaBHYT, txtBalance;
    private JComboBox<String> cboGioiTinh;
    private ModernUITheme.RoundedTextField txtPinUserDefault; // V5: Đổi từ JPasswordField sang JTextField
    private ModernUITheme.RoundedButton btnPhatHanh;

    // V4: Thông tin y tế khẩn cấp
    private JComboBox<String> cboNhomMau;
    private JTextArea txtDiUng;
    private JTextArea txtBenhNen;

    // V6: Ảnh đại diện
    private JLabel lblPhotoPreview;
    private ModernUITheme.RoundedButton btnUploadPhoto;
    private String photoBase64; // Lưu ảnh dạng Base64

    public CardIssuePanel(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        // Load .env file on initialization
        EnvFileLoader.load();
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ModernUITheme.BG_PRIMARY);
        // setBorder(BorderFactory.createTitledBorder("Phát hành thẻ User mới")); // Bỏ
        // border cũ

        // Main content wrapper with padding
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.5;

        // --- LEFT COLUMN: PERSONAL INFO ---
        JPanel pnlPersonal = new ModernUITheme.CardPanel();
        pnlPersonal.setLayout(new BoxLayout(pnlPersonal, BoxLayout.Y_AXIS));
        pnlPersonal.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        addHeader(pnlPersonal, "Thông tin cá nhân");

        txtHoTen = addLabeledField(pnlPersonal, "Họ tên:", 25);

        // ID Bệnh nhân (Hidden but kept for logic)
        txtIdBenhNhan = new ModernUITheme.RoundedTextField(25);
        txtIdBenhNhan.setVisible(false);

        txtNgaySinh = addLabeledField(pnlPersonal, "Ngày sinh (DD/MM/YYYY):", 25);
        txtQueQuan = addLabeledField(pnlPersonal, "Quê quán:", 25);

        // Giới tính
        addLabel(pnlPersonal, "Giới tính:");
        cboGioiTinh = new JComboBox<>(new String[] { "Nam", "Nữ", "Khác" });
        cboGioiTinh.setFont(ModernUITheme.FONT_BODY);
        cboGioiTinh.setPreferredSize(new Dimension(200, 40));
        cboGioiTinh.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        alignLeft(cboGioiTinh);
        pnlPersonal.add(cboGioiTinh);
        pnlPersonal.add(Box.createVerticalStrut(15));

        // V6: Ảnh đại diện
        addLabel(pnlPersonal, "Ảnh đại diện:");

        // Photo panel with preview and upload button
        JPanel photoPanel = new JPanel();
        photoPanel.setLayout(new BoxLayout(photoPanel, BoxLayout.X_AXIS));
        photoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        photoPanel.setOpaque(false);

        // Preview label
        lblPhotoPreview = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        lblPhotoPreview.setPreferredSize(new Dimension(120, 120));
        lblPhotoPreview.setMaximumSize(new Dimension(120, 120));
        lblPhotoPreview.setMinimumSize(new Dimension(120, 120));
        lblPhotoPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT, 2),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        lblPhotoPreview.setBackground(new Color(250, 250, 250));
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblPhotoPreview.setForeground(Color.GRAY);
        photoPanel.add(lblPhotoPreview);

        photoPanel.add(Box.createHorizontalStrut(10));

        // Upload button
        JPanel btnPanelPhoto = new JPanel();
        btnPanelPhoto.setLayout(new BoxLayout(btnPanelPhoto, BoxLayout.Y_AXIS));
        btnPanelPhoto.setOpaque(false);

        btnUploadPhoto = new ModernUITheme.RoundedButton(
                "Chọn ảnh",
                ModernUITheme.ADMIN_PRIMARY,
                ModernUITheme.ADMIN_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnUploadPhoto.setPreferredSize(new Dimension(100, 35));
        btnUploadPhoto.setMaximumSize(new Dimension(100, 35));
        btnUploadPhoto.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnUploadPhoto.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanelPhoto.add(btnUploadPhoto);

        btnPanelPhoto.add(Box.createVerticalStrut(5));

        JLabel lblPhotoHint = new JLabel("<html><i>Ảnh sẽ được<br/>resize xuống<br/>≤ 20KB</i></html>");
        lblPhotoHint.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        lblPhotoHint.setForeground(Color.GRAY);
        lblPhotoHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanelPhoto.add(lblPhotoHint);

        photoPanel.add(btnPanelPhoto);

        pnlPersonal.add(photoPanel);
        pnlPersonal.add(Box.createVerticalStrut(15));

        txtMaBHYT = addLabeledField(pnlPersonal, "Mã BHYT:", 25);

        // --- RIGHT COLUMN: MEDICAL & ACCOUNT INFO ---
        JPanel pnlMedical = new ModernUITheme.CardPanel();
        pnlMedical.setLayout(new BoxLayout(pnlMedical, BoxLayout.Y_AXIS));
        pnlMedical.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        addHeader(pnlMedical, "Thông tin y tế & Tài khoản");

        // Nhóm máu
        addLabel(pnlMedical, "Nhóm máu:");
        cboNhomMau = new JComboBox<>(UserData.BLOOD_TYPE_LABELS);
        cboNhomMau.setFont(ModernUITheme.FONT_BODY);
        cboNhomMau.setPreferredSize(new Dimension(200, 40));
        cboNhomMau.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        alignLeft(cboNhomMau);
        pnlMedical.add(cboNhomMau);
        pnlMedical.add(Box.createVerticalStrut(15));

        // Dị ứng
        addLabel(pnlMedical, "Dị ứng:");
        txtDiUng = new JTextArea(3, 20);
        txtDiUng.setLineWrap(true);
        txtDiUng.setWrapStyleWord(true);
        txtDiUng.setFont(ModernUITheme.FONT_BODY);
        JScrollPane scrollDiUng = new JScrollPane(txtDiUng);
        scrollDiUng.setBorder(BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT));
        scrollDiUng.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnlMedical.add(scrollDiUng);
        pnlMedical.add(Box.createVerticalStrut(15));

        // Bệnh nền
        addLabel(pnlMedical, "Bệnh nền:");
        txtBenhNen = new JTextArea(3, 20);
        txtBenhNen.setLineWrap(true);
        txtBenhNen.setWrapStyleWord(true);
        txtBenhNen.setFont(ModernUITheme.FONT_BODY);
        JScrollPane scrollBenhNen = new JScrollPane(txtBenhNen);
        scrollBenhNen.setBorder(BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT));
        scrollBenhNen.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnlMedical.add(scrollBenhNen);
        pnlMedical.add(Box.createVerticalStrut(15));

        // Tài khoản
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        pnlMedical.add(sep);
        pnlMedical.add(Box.createVerticalStrut(15));

        txtBalance = addLabeledField(pnlMedical, "Số dư ban đầu (VNĐ):", 25);
        txtBalance.setText("0");

        addLabel(pnlMedical, "PIN User mặc định:");
        txtPinUserDefault = new ModernUITheme.RoundedTextField(25);
        txtPinUserDefault.setText("123456");
        txtPinUserDefault.setEditable(false);
        txtPinUserDefault.setBackground(new Color(245, 245, 245));
        txtPinUserDefault.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        alignLeft(txtPinUserDefault);
        pnlMedical.add(txtPinUserDefault);

        // Add columns to GridBag
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0; // Fill vertical space
        contentPanel.add(pnlPersonal, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        contentPanel.add(pnlMedical, gbc);

        // --- BUTTON SECTION ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        btnPhatHanh = new ModernUITheme.RoundedButton(
                "Phát hành thẻ",
                ModernUITheme.ADMIN_PRIMARY,
                ModernUITheme.ADMIN_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnPhatHanh.setPreferredSize(new Dimension(220, 50));
        btnPhatHanh.setFont(new Font("Segoe UI", Font.BOLD, 16));

        btnPanel.add(btnPhatHanh);

        // Add Main Scroll Pane
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        // Event handlers
        btnPhatHanh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                issueCard();
            }
        });

        // V6: Event handler cho upload ảnh
        btnUploadPhoto.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uploadPhoto();
            }
        });

        // Tự động tạo ID bệnh nhân khi khởi tạo form
        autoGeneratePatientId();
    }

    // Helper methods for UI construction
    private void addHeader(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(ModernUITheme.FONT_HEADING);
        label.setForeground(ModernUITheme.ADMIN_PRIMARY != null ? ModernUITheme.ADMIN_PRIMARY : new Color(79, 70, 229));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(20));
    }

    private void addLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(ModernUITheme.FONT_SUBHEADING);
        label.setForeground(ModernUITheme.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(5));
    }

    private ModernUITheme.RoundedTextField addLabeledField(JPanel panel, String labelText, int columns) {
        addLabel(panel, labelText);
        ModernUITheme.RoundedTextField field = new ModernUITheme.RoundedTextField(columns);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        alignLeft(field);
        panel.add(field);
        panel.add(Box.createVerticalStrut(15));
        return field;
    }

    private void alignLeft(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        String pinUserDefault = txtPinUserDefault.getText().trim();

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

        // Thử parse với format linh hoạt d/M/yyyy (cho phép 1-2 chữ số cho ngày và
        // tháng)
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

        String balanceStr = txtBalance.getText().trim();
        if (balanceStr.isEmpty()) {
            return "Vui lòng nhập số dư ban đầu (có thể nhập 0)!";
        }
        try {
            long balance = Long.parseLong(balanceStr);
            if (balance < 0) {
                return "Số dư ban đầu không thể âm!";
            }
        } catch (NumberFormatException e) {
            return "Số dư ban đầu phải là số nguyên hợp lệ!";
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

            // 2. Đảm bảo kết nối thẻ, channel sẵn sàng, và applet đã được select
            // Sử dụng CardConnectionHelper để đảm bảo tất cả đã sẵn sàng
            System.out.println("[CardIssuePanel] issueCard: Đang kiểm tra kết nối thẻ và applet...");
            if (!CardConnectionHelper.ensureCardAndAppletReady(
                    cardManager, apduCommands, this, true, APDUCommands.AID_USER)) {
                System.err.println("[CardIssuePanel] issueCard: Không thể đảm bảo kết nối thẻ và applet");
                return;
            }
            System.out.println("[CardIssuePanel] issueCard: ✓ Kết nối thẻ và applet đã sẵn sàng");

            // 3.2. Kiểm tra thẻ đã có cardId_user chưa (có thể là data cũ hoặc thẻ đã được
            // issue)
            System.out.println("[CardIssuePanel] issueCard: Kiểm tra thẻ đã có cardId_user chưa...");
            byte[] existingCardId = apduCommands.getStatus(); // V3: Use getStatus() instead of getCardId()

            // Kiểm tra cardId có hợp lệ không (không null, không rỗng, và không phải toàn
            // số 0)
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
                System.out.println("[CardIssuePanel] issueCard: Lưu ý - cardId_user có thể là data cũ");

                int choice = JOptionPane.showConfirmDialog(this,
                        "Thẻ đã có cardId_user!\n\n" +
                                "Card ID hiện tại: " + existingCardIdHex + "\n\n" +
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

            // V5: Set gender trước khi gửi xuống thẻ
            String gioiTinhSelected = (String) cboGioiTinh.getSelectedItem();
            int genderValue = 1; // Mặc định: Nam
            if ("Nữ".equals(gioiTinhSelected)) {
                genderValue = 2;
            } else if ("Khác".equals(gioiTinhSelected)) {
                genderValue = 3;
            }
            userData.setGender(genderValue);

            // V4: Thông tin y tế khẩn cấp
            userData.setNhomMau(cboNhomMau.getSelectedIndex());
            userData.setDiUng(txtDiUng.getText().trim());
            userData.setBenhNen(txtBenhNen.getText().trim());

            // V6: KHÔNG gửi ảnh trong ISSUE_CARD (vượt APDU limit 254 bytes)
            // Lưu ảnh vào biến tạm, gửi riêng sau
            String photoBase64Temp = photoBase64;
            userData.setAnhDaiDien(""); // Xóa ảnh tạm thời

            // V7: Set default BHYT info
            userData.setBhytCoverageRate(60); // Hardcode 60%
            userData.setBhytExpiryDate("31/12/2027"); // Hardcode expiry date

            long initialBalance = Long.parseLong(txtBalance.getText().trim());
            userData.setBalance(initialBalance);

            String pinUserDefault = txtPinUserDefault.getText().trim();

            System.out.println("[CardIssuePanel] issueCard: Thông tin bệnh nhân:");
            System.out.println("  - Họ tên: " + userData.getHoTen());
            System.out.println("  - ID bệnh nhân: " + userData.getIdBenhNhan());
            System.out.println("  - Ngày sinh: " + userData.getNgaySinh());
            System.out.println("  - Quê quán: " + userData.getQueQuan());
            System.out.println("  - Mã BHYT: " + userData.getMaBHYT());
            System.out.println("  - Số dư ban đầu: " + initialBalance + " VNĐ");
            System.out.println("  - Nhóm máu: " + userData.getNhomMauLabel());
            System.out.println("  - Dị ứng: " + userData.getDiUng());
            System.out.println("  - Bệnh nền: " + userData.getBenhNen());

            // Remove balance from userData before sending to card (balance is stored
            // separately)
            // But we keep balance in userData object for display/snapshot
            byte[] userDataBytes = userData.toBytes();
            // Note: toBytes() might include balance, but we'll send patient data only
            // Actually, UserData.toBytes() should not include balance since it's stored
            // separately now
            // But for backward compatibility, let's check if we need to remove balance from
            // bytes

            // Sử dụng UTF-8 để đảm bảo encoding nhất quán với changePin
            byte[] pinUserBytes = pinUserDefault.getBytes(StandardCharsets.UTF_8);

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

            // Sử dụng UTF-8 để đảm bảo encoding nhất quán
            byte[] pinAdminBytes = pinAdminReset.getBytes(StandardCharsets.UTF_8);

            // 5.3. Gửi lệnh ISSUE_CARD xuống thẻ với cardID, PIN admin, và initial balance
            System.out.println(
                    "[CardIssuePanel] issueCard: Gửi lệnh ISSUE_CARD xuống thẻ với cardID, PIN admin, và balance ban đầu = "
                            + initialBalance);
            byte[] result = apduCommands.issueCard(cardIdUser, userDataBytes, pinUserBytes, pinAdminBytes,
                    (int) initialBalance);

            // V3: Response chỉ là status byte (0x00 = success)
            if (result == null || result.length < 1 || result[0] != 0x00) {
                System.err.println("[CardIssuePanel] issueCard: Phát hành thẻ thất bại - status = " +
                        (result != null && result.length > 0 ? String.format("0x%02X", result[0]) : "null"));
                MessageHelper.showCardIssueFailure(this);
                return;
            }
            System.out.println("[CardIssuePanel] issueCard: ISSUE_CARD thành công! Status = 0x00");

            // 5.5. Parse RSA keys từ response (V3)
            System.out.println("[CardIssuePanel] issueCard: Parse RSA keys từ response...");

            // Biến tạm để lưu RSA keys (Base64) cho snapshot
            String pkUserBase64 = null;
            String skUserBase64 = null;
            byte[] pkUserEncoded = null; // Di chuyển ra ngoài để truy cập sau khi saveUserCard()
            byte[] skUserEncoded = null;

            try {

                if (result != null && result.length > 3) {
                    // Format: [status (1)] [pk_mod_len (2)] [pk_modulus] [pk_exp_len (2)]
                    // [pk_exponent]
                    // [sk_mod_len (2)] [sk_modulus] [sk_exp_len (2)] [sk_exponent] (optional)
                    // NOTE: SK may not be present if JavaCard doesn't allow private key export

                    short offset = 1; // Skip status byte

                    // Parse PUBLIC KEY
                    short pkModLen = getShort(result, offset);
                    offset += 2;
                    byte[] pkModulus = new byte[pkModLen];
                    System.arraycopy(result, offset, pkModulus, 0, pkModLen);
                    offset += pkModLen;

                    short pkExpLen = getShort(result, offset);
                    offset += 2;
                    byte[] pkExponent = new byte[pkExpLen];
                    System.arraycopy(result, offset, pkExponent, 0, pkExpLen);
                    offset += pkExpLen;

                    System.out.println("[CardIssuePanel] PK_user parsed: modLen=" + pkModLen + ", expLen=" + pkExpLen);

                    // Parse PRIVATE KEY (if available - may not exist due to JavaCard security)
                    byte[] skModulus = null;
                    byte[] skExponent = null;

                    if (result.length > offset + 4) {
                        System.out.println("[CardIssuePanel] Attempting to parse SK_user from response...");
                        short skModLen = getShort(result, offset);
                        offset += 2;
                        skModulus = new byte[skModLen];
                        System.arraycopy(result, offset, skModulus, 0, skModLen);
                        offset += skModLen;

                        short skExpLen = getShort(result, offset);
                        offset += 2;
                        skExponent = new byte[skExpLen];
                        System.arraycopy(result, offset, skExponent, 0, skExpLen);

                        System.out.println(
                                "[CardIssuePanel] ✓ SK_user parsed: modLen=" + skModLen + ", expLen=" + skExpLen);
                    } else {
                        System.out.println("[CardIssuePanel] ⚠️ SK_user KHÔNG CÓ trong response");
                        System.out
                                .println("[CardIssuePanel]    → JavaCard không cho phép export private key (bảo mật)");
                        System.out.println("[CardIssuePanel]    → Snapshot sẽ không có SK_user (OK cho production)");
                    }

                    // Convert RAW bytes → Java standard format
                    BigInteger n = new BigInteger(1, pkModulus);
                    BigInteger e = new BigInteger(1, pkExponent);

                    RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(n, e);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    java.security.PublicKey javaPublicKey = kf.generatePublic(pubSpec);

                    // Encode sang X.509 format (standard)
                    pkUserEncoded = javaPublicKey.getEncoded();
                    System.out.println("[CardIssuePanel] PK_user encoded (X.509): " + pkUserEncoded.length + " bytes");
                    System.out.println(
                            "[CardIssuePanel] Lưu ý: PK_user sẽ được lưu vào database SAU KHI saveUserCard() thành công");

                    // Convert PRIVATE KEY (if available - usually NOT on real JavaCards)
                    if (skModulus != null && skExponent != null) {
                        try {
                            BigInteger d = new BigInteger(1, skExponent);
                            RSAPrivateKeySpec privSpec = new RSAPrivateKeySpec(n, d);
                            java.security.PrivateKey javaPrivateKey = kf.generatePrivate(privSpec);

                            // Encode sang PKCS#8 format
                            skUserEncoded = javaPrivateKey.getEncoded();
                            System.out.println(
                                    "[CardIssuePanel] ✓ SK_user encoded (PKCS#8): " + skUserEncoded.length + " bytes");
                            System.out.println(
                                    "[CardIssuePanel]   ⚠️ SK_user chỉ lưu vào SNAPSHOT (demo), KHÔNG lưu database");
                        } catch (Exception skEx) {
                            System.err.println("[CardIssuePanel] ✗ Lỗi convert SK_user: " + skEx.getMessage());
                            // Continue without SK
                        }
                    } else {
                        System.out.println("[CardIssuePanel] ℹ️ SK_user không có → Snapshot không có private key (OK)");
                    }

                } else {
                    System.err.println("[CardIssuePanel] ✗ Response quá ngắn, không parse được RSA keys");
                }

                // Lưu Base64 vào biến tạm (sẽ set vào snapshot sau)
                if (pkUserEncoded != null) {
                    pkUserBase64 = Base64.getEncoder().encodeToString(pkUserEncoded);
                    System.out.println("[CardIssuePanel] ✓ PK_user ready for snapshot (Base64)");
                }

                if (skUserEncoded != null) {
                    skUserBase64 = Base64.getEncoder().encodeToString(skUserEncoded);
                    System.out.println("[CardIssuePanel] ⚠️ SK_user ready for snapshot (Base64, DEMO only)");
                }

            } catch (Exception e) {
                System.err.println("[CardIssuePanel] Lỗi parse RSA keys: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Cảnh báo: Không thể parse RSA keys!\n\n" +
                                "Lỗi: " + e.getMessage() + "\n\n" +
                                "Thẻ vẫn được phát hành nhưng không có RSA.",
                        "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            }

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
                System.err.println("[CardIssuePanel] issueCard: CardID không khớp! Expected: " + cardIdHex + ", Got: "
                        + cardIdOnCardHex);
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

            // V6: Upload ảnh đại diện (nếu có)
            if (photoBase64Temp != null && !photoBase64Temp.isEmpty()) {
                System.out.println("[CardIssuePanel] issueCard: Uploading photo to card...");

                // MUST verify PIN first to load MK_user (needed for setPhoto encryption)
                System.out.println("[CardIssuePanel] Verifying PIN user before photo upload...");
                try {
                    // Use pinUserDefault that was already read above
                    byte[] pinUserBytesForPhoto = pinUserDefault.getBytes(StandardCharsets.UTF_8);
                    byte[] verifyResult = apduCommands.verifyPinAndReadData(pinUserBytesForPhoto);
                    if (verifyResult == null || verifyResult.length == 0) {
                        System.err.println("[CardIssuePanel] ✗ PIN verification failed before photo upload!");
                        JOptionPane.showMessageDialog(this,
                                "Thẻ đã phát hành thành công nhưng không thể upload ảnh.\n\n" +
                                        "Nguyên nhân: Không thể verify PIN user.\n" +
                                        "Vui lòng thử upload ảnh lại sau.",
                                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                    } else {
                        System.out.println("[CardIssuePanel] ✓ PIN verified, MK_user loaded");

                        // Now upload photo
                        boolean photoUploaded = apduCommands.setPhotoChunked(photoBase64Temp);
                        if (photoUploaded) {
                            System.out.println("[CardIssuePanel] ✓ Photo uploaded successfully!");
                        } else {
                            System.err.println("[CardIssuePanel] ✗ Photo upload failed!");
                            JOptionPane.showMessageDialog(this,
                                    "Thẻ đã phát hành thành công nhưng upload ảnh thất bại.\n\n" +
                                            "Bạn có thể thử upload lại sau.",
                                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                } catch (Exception photoEx) {
                    System.err.println("[CardIssuePanel] Exception during photo upload: " + photoEx.getMessage());
                    photoEx.printStackTrace();
                }
            } else {
                System.out.println("[CardIssuePanel] issueCard: No photo to upload");
            }

            // 7. Lưu vào Supabase
            System.out.println("[CardIssuePanel] issueCard: Lưu vào database...");

            // Lấy admin ID từ current logged-in user
            DatabaseConnection.AdminUserInfo adminUser = LoginFrame.getCurrentAdminUser();
            Integer adminId = (adminUser != null) ? adminUser.id : null;

            // 7.1. Lưu thông tin bệnh nhân vào bảng patients trước
            System.out.println("[CardIssuePanel] issueCard: Lưu thông tin bệnh nhân vào bảng patients...");

            // Lấy gender đã set vào userData
            int gender = userData.getGender();

            if (!DatabaseConnection.savePatient(
                    userData.getIdBenhNhan(),
                    userData.getHoTen(),
                    userData.getNgaySinh(),
                    userData.getQueQuan(),
                    userData.getMaBHYT(),
                    gender)) {
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

                // 7.3. SAU KHI insert user_cards thành công, MỚI lưu PK_user (V3)
                if (pkUserEncoded != null) {
                    System.out.println("[CardIssuePanel] ========================================");
                    System.out.println("[CardIssuePanel] LƯU PK_user VÀO DATABASE (SAU KHI INSERT user_cards)");
                    System.out.println("[CardIssuePanel] cardIdUser hex: " + bytesToHex(cardIdUser));
                    System.out.println("[CardIssuePanel] pkUserEncoded length: " + pkUserEncoded.length);
                    System.out.println("[CardIssuePanel] ========================================");

                    if (DatabaseConnection.saveUserPublicKey(cardIdUser, pkUserEncoded)) {
                        System.out.println("[CardIssuePanel] ✓ Đã lưu PK_user vào database thành công");
                    } else {
                        System.err.println("[CardIssuePanel] ✗ Lưu PK_user vào database thất bại");
                        JOptionPane.showMessageDialog(this,
                                "Cảnh báo: Không thể lưu RSA public key vào database!\n\n" +
                                        "Thẻ đã được phát hành nhưng sẽ không thể xác thực RSA.\n\n" +
                                        "Vui lòng kiểm tra:\n" +
                                        "1. Cột pk_user đã tồn tại trong bảng user_cards chưa?\n" +
                                        "2. Database connection có hoạt động không?",
                                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    System.out.println("[CardIssuePanel] pkUserEncoded = null, không lưu vào database");
                }
            }

            // V3: Không lưu PIN_admin_reset vào DB nữa, chỉ derive khi cần
            System.out.println(
                    "[CardIssuePanel] issueCard: V3 - PIN_admin_reset không lưu trong DB, chỉ derive động khi cần");

            // 8. Lưu snapshot demo vào file JSON
            System.out.println("[CardIssuePanel] issueCard: Lưu snapshot demo...");
            UserCardSnapshot snapshot = new UserCardSnapshot();
            snapshot.setCardIdHex(cardIdHex);
            snapshot.setHoTen(userData.getHoTen());
            snapshot.setIdBenhNhan(userData.getIdBenhNhan());
            snapshot.setNgaySinh(userData.getNgaySinh());
            snapshot.setQueQuan(userData.getQueQuan());
            snapshot.setMaBHYT(userData.getMaBHYT());
            snapshot.setBalance(userData.getBalance());
            snapshot.setPinUserDefault(pinUserDefault);
            snapshot.setPinAdminReset(pinAdminReset); // Lưu PIN admin reset để demo

            // Set RSA keys vào snapshot (V3)
            if (pkUserBase64 != null) {
                snapshot.setPkUserBase64(pkUserBase64);
                System.out.println("[CardIssuePanel] ✓ PK_user lưu vào snapshot");
            }
            if (skUserBase64 != null) {
                snapshot.setSkUserBase64(skUserBase64);
                System.out.println("[CardIssuePanel] ⚠️ SK_user lưu vào snapshot (DEMO only)");
            }

            if (UserDemoSnapshotManager.saveSnapshot(snapshot)) {
                System.out.println("[CardIssuePanel] issueCard: Đã lưu snapshot demo cho cardId: " + cardIdHex);
            } else {
                System.err.println("[CardIssuePanel] issueCard: Lỗi khi lưu snapshot demo");
            }

            System.out.println("[CardIssuePanel] ========== PHÁT HÀNH THẺ THÀNH CÔNG ==========");

            // Thông báo đơn giản cho người dùng
            MessageHelper.showCardIssueSuccess(this);

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
        cboGioiTinh.setSelectedIndex(0); // Reset về "Nam"
        txtMaBHYT.setText("");
        txtBalance.setText("0");
        txtPinUserDefault.setText("");
        // V4: Clear thông tin y tế khẩn cấp
        cboNhomMau.setSelectedIndex(0);
        txtDiUng.setText("");
        txtBenhNen.setText("");
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

    /**
     * Helper: Parse short từ byte array (Big-Endian)
     */
    private short getShort(byte[] data, int offset) {
        return (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    /**
     * V6: Upload và resize ảnh đại diện xuống ≤ 20KB
     */
    private void uploadPhoto() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn ảnh đại diện bệnh nhân");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Ảnh (JPG, JPEG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            System.out.println("[CardIssuePanel] Đã chọn file: " + file.getAbsolutePath());

            // Hiển thị progress dialog
            JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                    "Đang xử lý ảnh...", true);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            progressDialog.setSize(350, 120);
            progressDialog.setLocationRelativeTo(this);

            JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
            contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel lblProgress = new JLabel("Đang resize và nén ảnh xuống ≤ 20KB...", SwingConstants.CENTER);
            lblProgress.setFont(ModernUITheme.FONT_BODY);
            contentPanel.add(lblProgress, BorderLayout.CENTER);

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            contentPanel.add(progressBar, BorderLayout.SOUTH);

            progressDialog.add(contentPanel);

            // Xử lý ảnh trong background thread
            SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    // Resize và compress ảnh xuống ≤ 20KB
                    return ImageHelper.resizeAndCompressToBase64(file);
                }

                @Override
                protected void done() {
                    try {
                        photoBase64 = get(); // Lấy kết quả Base64

                        // Hiển thị preview
                        java.awt.image.BufferedImage previewImage = ImageHelper.decodeBase64ToImage(photoBase64);
                        if (previewImage != null) {
                            lblPhotoPreview.setIcon(ImageHelper.createScaledIcon(previewImage, 120, 120));
                            lblPhotoPreview.setText(null);
                        }

                        progressDialog.dispose();

                        // Tính kích thước Base64
                        int sizeBytes = photoBase64.getBytes().length;
                        int sizeKB = sizeBytes / 1024;

                        JOptionPane.showMessageDialog(CardIssuePanel.this,
                                "✓ Upload ảnh thành công!\n\n" +
                                        "File: " + file.getName() + "\n" +
                                        "Kích thước sau nén: " + sizeKB + " KB",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);

                        System.out.println("[CardIssuePanel] Upload ảnh thành công: " + sizeKB + " KB");

                    } catch (Exception ex) {
                        progressDialog.dispose();
                        ex.printStackTrace();

                        // Hiển thị error với hướng dẫn
                        String errorMsg = "Lỗi khi xử lý ảnh: " + ex.getMessage() + "\n\n";

                        if (ex.getMessage() != null && ex.getMessage().contains("không thể nén")) {
                            errorMsg += "Khuyến nghị:\n" +
                                    "• Chọn ảnh đơn giản hơn (ít màu sắc, ít chi tiết)\n" +
                                    "• Chọn ảnh có kích thước gốc nhỏ hơn\n" +
                                    "• Thử ảnh có nền trơn màu hoặc ảnh chụp passport";
                        } else {
                            errorMsg += "Vui lòng kiểm tra:\n" +
                                    "• File ảnh có hợp lệ không?\n" +
                                    "• File có bị hỏng không?\n" +
                                    "• Định dạng file có đúng (JPG, PNG, GIF)?";
                        }

                        JOptionPane.showMessageDialog(CardIssuePanel.this,
                                errorMsg,
                                "Lỗi upload ảnh", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

            worker.execute();
            progressDialog.setVisible(true); // Block until worker completes
        }
    }
}
