package ui.admin;

import card.CardManager;
import card.APDUCommands;
import ui.ModernUITheme;
import db.DatabaseConnection;
import model.UserData;
import model.UserCardSnapshot;
import util.AdminPinDerivation;
import util.EnvFileLoader;
import util.UserDemoSnapshotManager;
import util.ImageHelper; // V6

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

    private ModernUITheme.RoundedTextField txtCardId, txtHoTen, txtIdBenhNhan, txtNgaySinh, txtQueQuan, txtMaBHYT,
            txtBalance;
    private JLabel lblGioiTinh; // V5: Hiển thị giới tính (read-only)
    private ModernUITheme.RoundedPasswordField txtPinUserForLoad; // PIN User để load data từ thẻ
    private ModernUITheme.RoundedButton btnLoadFromCard, btnLoadToCard;
    private JLabel lblAdminPinStatus; // Hiển thị trạng thái Admin PIN

    // V4: Thông tin y tế khẩn cấp
    private JComboBox<String> cboNhomMau;
    private JTextArea txtDiUng;
    private JTextArea txtBenhNen;

    // V6: Ảnh đại diện
    private JLabel lblPhotoPreview;
    private ModernUITheme.RoundedButton btnUploadPhoto;
    private String photoBase64; // Lưu ảnh dạng Base64

    public CardManagePanel(CardManager cardManager, APDUCommands apduCommands) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBackground(ModernUITheme.BG_PRIMARY);
        // setBorder(BorderFactory.createTitledBorder("Quản lý thông tin thẻ"));

        // Main content wrapper
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

        addHeader(pnlPersonal, "Thông tin thẻ & Cá nhân");

        // Card ID Section
        addLabel(pnlPersonal, "Card ID (hex):");
        JPanel cardIdPanel = new JPanel(new BorderLayout(10, 0));
        cardIdPanel.setOpaque(false);
        cardIdPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cardIdPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtCardId = new ModernUITheme.RoundedTextField(20);
        txtCardId.setEditable(false);
        txtCardId.setBackground(new Color(245, 245, 245));
        cardIdPanel.add(txtCardId, BorderLayout.CENTER);

        btnLoadFromCard = new ModernUITheme.RoundedButton("Load từ thẻ", ModernUITheme.INFO,
                ModernUITheme.darken(ModernUITheme.INFO, 0.1f), Color.WHITE);
        btnLoadFromCard.setPreferredSize(new Dimension(120, 40));
        btnLoadFromCard.setFont(ModernUITheme.FONT_SMALL);
        cardIdPanel.add(btnLoadFromCard, BorderLayout.EAST);

        pnlPersonal.add(cardIdPanel);
        pnlPersonal.add(Box.createVerticalStrut(15));

        txtHoTen = addLabeledField(pnlPersonal, "Họ tên:", 25);
        txtIdBenhNhan = addLabeledField(pnlPersonal, "ID bệnh nhân:", 25);
        txtNgaySinh = addLabeledField(pnlPersonal, "Ngày sinh:", 25);
        txtQueQuan = addLabeledField(pnlPersonal, "Quê quán:", 25);

        addLabel(pnlPersonal, "Giới tính:");
        lblGioiTinh = new JLabel("-");
        lblGioiTinh.setFont(ModernUITheme.FONT_BODY);
        lblGioiTinh.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnlPersonal.add(lblGioiTinh);
        pnlPersonal.add(Box.createVerticalStrut(15));

        txtMaBHYT = addLabeledField(pnlPersonal, "Mã BHYT:", 25);

        // V6: Photo preview with upload button
        addLabel(pnlPersonal, "Ảnh đại diện:");

        JPanel photoPanel = new JPanel();
        photoPanel.setLayout(new BoxLayout(photoPanel, BoxLayout.X_AXIS));
        photoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        photoPanel.setOpaque(false);

        lblPhotoPreview = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        lblPhotoPreview.setPreferredSize(new Dimension(120, 120));
        lblPhotoPreview.setMaximumSize(new Dimension(120, 120));
        lblPhotoPreview.setMinimumSize(new Dimension(120, 120));
        lblPhotoPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT, 2),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setBackground(new Color(250, 250, 250));
        lblPhotoPreview.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblPhotoPreview.setFont(ModernUITheme.FONT_SMALL);
        photoPanel.add(lblPhotoPreview);

        photoPanel.add(Box.createHorizontalStrut(10));

        // Upload button panel
        JPanel btnPanelPhoto = new JPanel();
        btnPanelPhoto.setLayout(new BoxLayout(btnPanelPhoto, BoxLayout.Y_AXIS));
        btnPanelPhoto.setOpaque(false);

        btnUploadPhoto = new ModernUITheme.RoundedButton(
                "Chọn ảnh",
                ModernUITheme.ADMIN_PRIMARY,
                ModernUITheme.ADMIN_PRIMARY_HOVER,
                Color.WHITE);
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

        // --- RIGHT COLUMN: MEDICAL & ACCOUNT ---
        JPanel pnlMedical = new ModernUITheme.CardPanel();
        pnlMedical.setLayout(new BoxLayout(pnlMedical, BoxLayout.Y_AXIS));
        pnlMedical.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        addHeader(pnlMedical, "Thông tin y tế & Bảo mật");

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

        // Account Section
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        pnlMedical.add(sep);
        pnlMedical.add(Box.createVerticalStrut(15));

        txtBalance = addLabeledField(pnlMedical, "Số dư (VNĐ):", 25);

        // Security Inputs - V7: ẨN PIN User field, dùng dialog popup khi cần
        // addLabel(pnlMedical, "PIN User (để load data):");
        txtPinUserForLoad = new ModernUITheme.RoundedPasswordField(20);
        txtPinUserForLoad.setVisible(false); // Hidden - dùng dialog popup thay thế
        // txtPinUserForLoad.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        // alignLeft(txtPinUserForLoad);
        // pnlMedical.add(txtPinUserForLoad);
        // pnlMedical.add(Box.createVerticalStrut(15));

        // V6: PIN User mặc định đã bị xóa - không cần thiết cho quản lý thẻ cũ

        // Admin PIN Status
        addLabel(pnlMedical, "Admin PIN status:");
        lblAdminPinStatus = new JLabel("Chưa load thẻ");
        lblAdminPinStatus.setFont(ModernUITheme.FONT_SMALL);
        lblAdminPinStatus.setForeground(ModernUITheme.TEXT_SECONDARY);
        alignLeft(lblAdminPinStatus);
        pnlMedical.add(lblAdminPinStatus);

        // Add columns to GridBag
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        contentPanel.add(pnlPersonal, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        contentPanel.add(pnlMedical, gbc);

        // --- BUTTON SECTION ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        btnLoadToCard = new ModernUITheme.RoundedButton("Nạp vào thẻ", ModernUITheme.ADMIN_PRIMARY,
                ModernUITheme.ADMIN_PRIMARY_HOVER, Color.WHITE);

        btnPanel.add(btnLoadToCard);

        // Add to main panel
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

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

        // V6: Event handler cho upload ảnh
        btnUploadPhoto.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uploadPhoto();
            }
        });
    }

    // Helper methods
    private void addHeader(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setFont(ModernUITheme.FONT_HEADING);
        label.setForeground(ModernUITheme.ADMIN_PRIMARY);
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
                lblAdminPinStatus.setText("Không thể derive PIN (kiểm tra K_MASTER)");
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
                        new Object[] {
                                "V3: Cần nhập PIN User (6 chữ số) để đọc dữ liệu từ thẻ.\nVui lòng nhập PIN User:",
                                pinField },
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

                // V5: Hiển thị giới tính
                lblGioiTinh.setText(userData.getGenderLabel());

                // V4: Hiển thị thông tin y tế khẩn cấp
                cboNhomMau.setSelectedIndex(userData.getNhomMau());
                txtDiUng.setText(userData.getDiUng() != null ? userData.getDiUng() : "");
                txtBenhNen.setText(userData.getBenhNen() != null ? userData.getBenhNen() : "");

                // V6: Load ảnh đại diện từ thẻ
                try {
                    System.out.println("[CardManagePanel] Loading photo from card...");
                    String photoBase64 = apduCommands.getPhoto();

                    if (photoBase64 != null && !photoBase64.isEmpty()) {
                        System.out.println("[CardManagePanel] Photo loaded, decoding...");
                        java.awt.image.BufferedImage photoImage = ImageHelper.decodeBase64ToImage(photoBase64);

                        if (photoImage != null) {
                            ImageIcon photoIcon = ImageHelper.createScaledIcon(photoImage, 120, 120);
                            lblPhotoPreview.setIcon(photoIcon);
                            lblPhotoPreview.setText("");
                            System.out.println("[CardManagePanel] ✓ Photo displayed");
                        } else {
                            lblPhotoPreview.setIcon(null);
                            lblPhotoPreview.setText("Lỗi ảnh");
                        }
                    } else {
                        lblPhotoPreview.setIcon(null);
                        lblPhotoPreview.setText("Chưa có ảnh");
                        System.out.println("[CardManagePanel] No photo on card");
                    }
                } catch (Exception photoEx) {
                    System.err.println("[CardManagePanel] Error loading photo: " + photoEx.getMessage());
                    lblPhotoPreview.setIcon(null);
                    lblPhotoPreview.setText("Lỗi load ảnh");
                }

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
        lblGioiTinh.setText("-"); // V5: Clear gender
        // V4: Clear thông tin y tế khẩn cấp
        cboNhomMau.setSelectedIndex(0);
        txtDiUng.setText("");
        txtBenhNen.setText("");
        // V6: Clear photo
        if (lblPhotoPreview != null) {
            lblPhotoPreview.setIcon(null);
            lblPhotoPreview.setText("Chưa có ảnh");
        }
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

            // V3: CardManagePanel chỉ dùng UPDATE_USER_DATA, không issue card mới
            // Nếu cần issue card → dùng CardIssuePanel

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

            // V4: Thông tin y tế khẩn cấp
            userData.setNhomMau(cboNhomMau.getSelectedIndex());
            userData.setDiUng(txtDiUng.getText().trim());
            userData.setBenhNen(txtBenhNen.getText().trim());

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

                // CardManagePanel KHÔNG nên issue card mới
                // Logic này nên được xóa hoặc chuyển sang CardIssuePanel
                JOptionPane.showMessageDialog(this,
                        "Không thể phát hành thẻ mới từ CardManagePanel!\n\n" +
                                "Vui lòng sử dụng chức năng 'Phát hành thẻ mới' (CardIssuePanel).",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;

                /*
                 * LEGACY CODE - XÓA SAU
                 * byte[] result = apduCommands.issueCard(...);
                 * if (result != null && result.length >= 1 && result[0] == 0x00) {
                 * byte[] newCardId = apduCommands.getStatus();
                 * if (newCardId == null || newCardId.length != 16) {
                 * JOptionPane.showMessageDialog(this,
                 * "Phát hành thẻ thành công nhưng không thể đọc cardID!",
                 * "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                 * return;
                 * }
                 * 
                 * String newCardIdHex = bytesToHex(newCardId);
                 * txtCardId.setText(newCardIdHex);
                 * 
                 * try {
                 * EnvFileLoader.load();
                 * String pinAdminResetNew = AdminPinDerivation.deriveAdminResetPIN(newCardId);
                 * System.out.println("[CardManagePanel] Derived Admin PIN for new card: " +
                 * pinAdminResetNew);
                 * lblAdminPinStatus.setText("✓ PIN được derive động (V3): " +
                 * pinAdminResetNew);
                 * lblAdminPinStatus.setForeground(new Color(0, 153, 0));
                 * } catch (Exception e) {
                 * System.err.println("[CardManagePanel] Lỗi khi derive Admin PIN: " +
                 * e.getMessage());
                 * }
                 * success = true;
                 * }
                 */
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
            // V6: Không lưu PIN User mặc định nữa - không cần thiết

            // Tạo UserData mới
            UserData userData = new UserData();
            userData.setHoTen(txtHoTen.getText());
            userData.setIdBenhNhan(txtIdBenhNhan.getText());
            userData.setNgaySinh(txtNgaySinh.getText());
            userData.setQueQuan(txtQueQuan.getText());
            userData.setMaBHYT(txtMaBHYT.getText());

            // V4: Thông tin y tế khẩn cấp
            userData.setNhomMau(cboNhomMau.getSelectedIndex());
            userData.setDiUng(txtDiUng.getText().trim());
            userData.setBenhNen(txtBenhNen.getText().trim());

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
            // snapshot.setPinUserDefault() - KHÔNG lưu nữa

            // V4: Thông tin y tế khẩn cấp
            snapshot.setNhomMau(userData.getNhomMau());
            snapshot.setDiUng(userData.getDiUng());
            snapshot.setBenhNen(userData.getBenhNen());

            // Derive và lưu PIN admin reset nếu có thể
            try {
                byte[] cardIdBytes = UserDemoSnapshotManager.hexToBytes(cardIdHex);
                if (cardIdBytes != null && cardIdBytes.length == 16) {
                    String pinAdminReset = AdminPinDerivation.deriveAdminResetPIN(cardIdBytes);
                    snapshot.setPinAdminReset(pinAdminReset);
                    System.out.println(
                            "[CardManagePanel] saveSnapshotOnly: Đã derive và lưu PIN admin reset: " + pinAdminReset);
                }
            } catch (Exception e) {
                System.err.println(
                        "[CardManagePanel] saveSnapshotOnly: Không thể derive PIN admin reset: " + e.getMessage());
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
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
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
            java.io.File file = fileChooser.getSelectedFile();
            System.out.println("[CardManagePanel] Đã chọn file: " + file.getAbsolutePath());

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

                        System.out.println("[CardManagePanel] Upload ảnh thành công: " + sizeKB + " KB");

                        // V7: Tự động upload lên thẻ ngay lập tức (không hỏi nữa)
                        uploadPhotoToCard();

                    } catch (Exception ex) {
                        progressDialog.dispose();
                        ex.printStackTrace();

                        String errorMsg;
                        String errorTitle = "Lỗi upload ảnh";

                        // Kiểm tra lỗi do ảnh > 20KB
                        if (ex.getMessage() != null &&
                                (ex.getMessage().contains("không thể nén") ||
                                        ex.getMessage().contains("Không thể nén") ||
                                        ex.getMessage().contains("quá phức tạp"))) {

                            errorTitle = "Ảnh quá lớn";
                            errorMsg = "⚠️ KHÔNG THỂ NÉN ẢNH XUỐNG 20KB!\n\n" +
                                    "Ảnh bạn chọn quá phức tạp hoặc có quá nhiều màu sắc.\n\n" +
                                    "VUI LÒNG CHỌN ẢNH KHÁC:\n" +
                                    "• Ảnh đơn giản hơn (ít chi tiết, màu trơn)\n" +
                                    "• Ảnh chân dung passport (nền trơn)\n" +
                                    "• Ảnh đã được tối ưu sẵn\n" +
                                    "• Ảnh có kích thước gốc nhỏ hơn\n\n" +
                                    "❌ Không thể sử dụng ảnh này!";
                        } else {
                            // Lỗi khác (file hỏng, format không hợp lệ, etc.)
                            errorMsg = "Lỗi khi xử lý ảnh: " + ex.getMessage() + "\n\n" +
                                    "Vui lòng kiểm tra:\n" +
                                    "• File ảnh có hợp lệ không?\n" +
                                    "• File có bị hỏng không?\n" +
                                    "• Định dạng file có đúng (JPG, PNG, GIF)?";
                        }

                        JOptionPane.showMessageDialog(CardManagePanel.this,
                                errorMsg,
                                errorTitle,
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

            worker.execute();
            progressDialog.setVisible(true); // Block until worker completes
        }
    }

    /**
     * V6: Upload ảnh lên thẻ ngay lập tức (yêu cầu PIN User)
     */
    private void uploadPhotoToCard() {
        try {
            // Kiểm tra đã có ảnh chưa
            if (photoBase64 == null || photoBase64.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Chưa có ảnh để upload!",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Đảm bảo kết nối thẻ
            if (!CardConnectionHelper.ensureCardAndAppletReady(
                    cardManager, apduCommands, this, true, APDUCommands.AID_USER)) {
                return;
            }

            // Yêu cầu nhập PIN User để verify trước khi upload
            String pinUser = new String(txtPinUserForLoad.getPassword());
            if (pinUser.isEmpty() || pinUser.length() != 6) {
                // Hiển thị dialog để nhập PIN User
                JPasswordField pinField = new JPasswordField(20);
                int option = JOptionPane.showConfirmDialog(this,
                        new Object[] {
                                "Cần nhập PIN User (6 chữ số) để upload ảnh lên thẻ.\nVui lòng nhập PIN User:",
                                pinField
                        },
                        "Nhập PIN User",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (option == JOptionPane.OK_OPTION) {
                    pinUser = new String(pinField.getPassword());
                    if (pinUser.length() != 6 || !pinUser.matches("^[0-9]+$")) {
                        JOptionPane.showMessageDialog(this,
                                "PIN User phải là 6 chữ số!",
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    return; // User cancelled
                }
            }

            // Verify PIN trước
            System.out.println("[CardManagePanel] Verifying PIN before photo upload...");
            byte[] pinBytes = pinUser.getBytes(StandardCharsets.UTF_8);
            byte[] verifyResult = apduCommands.verifyPinAndReadData(pinBytes);

            if (verifyResult == null || verifyResult.length == 0) {
                JOptionPane.showMessageDialog(this,
                        "Không thể verify PIN User!\n\n" +
                                "Vui lòng kiểm tra lại PIN.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            System.out.println("[CardManagePanel] ✓ PIN verified, uploading photo...");

            // Upload photo
            boolean photoUploaded = apduCommands.setPhotoChunked(photoBase64);

            if (photoUploaded) {
                JOptionPane.showMessageDialog(this,
                        "✓ Upload ảnh lên thẻ thành công!",
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
                System.out.println("[CardManagePanel] ✓ Photo uploaded to card successfully!");
            } else {
                JOptionPane.showMessageDialog(this,
                        "Upload ảnh lên thẻ thất bại!\n\n" +
                                "Vui lòng thử lại hoặc kiểm tra kết nối thẻ.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                System.err.println("[CardManagePanel] ✗ Photo upload failed!");
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi upload ảnh lên thẻ: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
