package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import ui.ModernUITheme;
import util.ImageHelper;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

/**
 * EditInfoPanel - Panel cho phép User chỉnh sửa thông tin cá nhân
 * Các trường có thể chỉnh sửa: Địa chỉ, Nhóm máu, Dị ứng, Bệnh nền, Giới tính
 * V6: Thêm panel đổi thông tin cho User
 */
public class EditInfoPanel extends JPanel {

    private APDUCommands apduCommands;
    private UserFrame userFrame;

    // Form fields
    private JTextField txtQueQuan;
    private JComboBox<String> cboNhomMau;
    private JTextField txtDiUng;
    private JTextField txtBenhNen;
    private JComboBox<String> cboGioiTinh;

    // Display only fields
    private JLabel lblHoTen, lblNgaySinh, lblMaBHYT;

    // Photo fields
    private JLabel lblPhotoPreview;
    private String photoBase64;
    private boolean photoChanged = false;

    // Buttons
    private ModernUITheme.RoundedButton btnSave;
    private ModernUITheme.OutlineButton btnReset;

    public EditInfoPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;

        setOpaque(false);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
        loadCurrentInfo();
    }

    private void initUI() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ===== Main Card =====
        ModernUITheme.CardPanel mainCard = new ModernUITheme.CardPanel();
        mainCard.setLayout(new BorderLayout(20, 20));
        mainCard.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        // ===== Title Section =====
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("CHỈNH SỬA THÔNG TIN CÁ NHÂN");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titlePanel.add(titleLabel, BorderLayout.WEST);

        JLabel subtitleLabel = new JLabel("Cập nhật thông tin y tế và liên hệ của bạn");
        subtitleLabel.setFont(ModernUITheme.FONT_SMALL);
        subtitleLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        titlePanel.add(subtitleLabel, BorderLayout.SOUTH);

        mainCard.add(titlePanel, BorderLayout.NORTH);

        // ===== Form Section =====
        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        // --- INFO READONLY SECTION ---
        formPanel.add(createSectionHeader("Thông tin cơ bản (Chỉ xem)"));
        formPanel.add(Box.createVerticalStrut(10));

        JPanel readonlyPanel = new JPanel(new GridLayout(1, 3, 20, 10));
        readonlyPanel.setOpaque(false);
        readonlyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        readonlyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblHoTen = createReadonlyField("Họ tên");
        lblNgaySinh = createReadonlyField("Ngày sinh");
        lblMaBHYT = createReadonlyField("Mã BHYT");

        readonlyPanel.add(createLabeledReadonly("Họ tên:", lblHoTen));
        readonlyPanel.add(createLabeledReadonly("Ngày sinh:", lblNgaySinh));
        readonlyPanel.add(createLabeledReadonly("Mã BHYT:", lblMaBHYT));

        formPanel.add(readonlyPanel);
        formPanel.add(Box.createVerticalStrut(20));

        // Separator
        JSeparator sep1 = new JSeparator();
        sep1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep1.setForeground(ModernUITheme.BORDER_LIGHT);
        formPanel.add(sep1);
        formPanel.add(Box.createVerticalStrut(15));

        // --- EDITABLE SECTION ---
        formPanel.add(createSectionHeader("Thông tin có thể chỉnh sửa"));
        formPanel.add(Box.createVerticalStrut(15));

        // Địa chỉ
        formPanel.add(createFormRow("Địa chỉ (Quê quán):", txtQueQuan = createTextField()));
        formPanel.add(Box.createVerticalStrut(12));

        // Giới tính
        cboGioiTinh = new JComboBox<String>(new String[] { "Không rõ", "Nam", "Nữ", "Khác" });
        styleComboBox(cboGioiTinh);
        formPanel.add(createFormRow("Giới tính:", cboGioiTinh));
        formPanel.add(Box.createVerticalStrut(20));

        // Separator 2
        JSeparator sep2 = new JSeparator();
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep2.setForeground(ModernUITheme.BORDER_LIGHT);
        formPanel.add(sep2);
        formPanel.add(Box.createVerticalStrut(15));

        // --- MEDICAL INFO SECTION ---
        formPanel.add(createSectionHeader("Thông tin y tế khẩn cấp"));
        formPanel.add(Box.createVerticalStrut(15));

        // Nhóm máu
        cboNhomMau = new JComboBox<String>(UserData.BLOOD_TYPE_LABELS);
        styleComboBox(cboNhomMau);
        formPanel.add(createFormRow("Nhóm máu:", cboNhomMau));
        formPanel.add(Box.createVerticalStrut(12));

        // Dị ứng
        formPanel.add(createFormRow("Dị ứng:", txtDiUng = createTextField()));
        formPanel.add(Box.createVerticalStrut(12));

        // Bệnh nền
        formPanel.add(createFormRow("Bệnh nền:", txtBenhNen = createTextField()));
        formPanel.add(Box.createVerticalStrut(20));

        // Remove note section completely

        // Separator 3
        JSeparator sep3 = new JSeparator();
        sep3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep3.setForeground(ModernUITheme.BORDER_LIGHT);
        formPanel.add(sep3);
        formPanel.add(Box.createVerticalStrut(15));

        // --- PHOTO SECTION ---
        formPanel.add(createSectionHeader("Ảnh đại diện"));
        formPanel.add(Box.createVerticalStrut(15));

        JPanel photoSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        photoSection.setOpaque(false);
        photoSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        photoSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        // Photo preview
        JPanel photoPreviewPanel = new JPanel(new BorderLayout());
        photoPreviewPanel.setOpaque(false);

        lblPhotoPreview = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        lblPhotoPreview.setPreferredSize(new Dimension(100, 100));
        lblPhotoPreview.setMinimumSize(new Dimension(100, 100));
        lblPhotoPreview.setBorder(BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT, 2));
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setBackground(new Color(250, 250, 250));
        lblPhotoPreview.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblPhotoPreview.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        photoPreviewPanel.add(lblPhotoPreview, BorderLayout.CENTER);

        photoSection.add(photoPreviewPanel);

        // Photo buttons
        JPanel photoButtonPanel = new JPanel();
        photoButtonPanel.setOpaque(false);
        photoButtonPanel.setLayout(new BoxLayout(photoButtonPanel, BoxLayout.Y_AXIS));

        ModernUITheme.RoundedButton btnUploadPhoto = new ModernUITheme.RoundedButton(
                "Chọn ảnh mới",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnUploadPhoto.setPreferredSize(new Dimension(140, 36));
        btnUploadPhoto.setMaximumSize(new Dimension(140, 36));
        btnUploadPhoto.addActionListener(e -> selectPhoto());
        photoButtonPanel.add(btnUploadPhoto);

        photoButtonPanel.add(Box.createVerticalStrut(8));

        ModernUITheme.OutlineButton btnRemovePhoto = new ModernUITheme.OutlineButton(
                "Xóa ảnh",
                ModernUITheme.TEXT_SECONDARY,
                ModernUITheme.BG_SECONDARY,
                new Color(220, 53, 69));
        btnRemovePhoto.setPreferredSize(new Dimension(140, 36));
        btnRemovePhoto.setMaximumSize(new Dimension(140, 36));
        btnRemovePhoto.addActionListener(e -> removePhoto());
        photoButtonPanel.add(btnRemovePhoto);

        photoButtonPanel.add(Box.createVerticalStrut(8));

        JLabel photoHint = new JLabel("<html><i>Ảnh sẽ được tự động<br>resize ≤ 20KB</i></html>");
        photoHint.setFont(ModernUITheme.FONT_SMALL);
        photoHint.setForeground(ModernUITheme.TEXT_MUTED);
        photoButtonPanel.add(photoHint);

        photoSection.add(photoButtonPanel);
        formPanel.add(photoSection);

        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        mainCard.add(scrollPane, BorderLayout.CENTER);

        // ===== Button Panel =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        buttonPanel.setOpaque(false);

        btnReset = new ModernUITheme.OutlineButton(
                "Làm mới",
                ModernUITheme.TEXT_SECONDARY,
                ModernUITheme.BG_SECONDARY,
                ModernUITheme.USER_PRIMARY);
        btnReset.setPreferredSize(new Dimension(140, 44));
        btnReset.addActionListener(e -> loadCurrentInfo());
        buttonPanel.add(btnReset);

        btnSave = new ModernUITheme.RoundedButton(
                "Lưu thay đổi",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnSave.setPreferredSize(new Dimension(160, 44));
        btnSave.addActionListener(e -> saveChanges());
        buttonPanel.add(btnSave);

        mainCard.add(buttonPanel, BorderLayout.SOUTH);

        add(mainCard, BorderLayout.CENTER);
    }

    private JLabel createSectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(ModernUITheme.USER_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createReadonlyField(String placeholder) {
        JLabel label = new JLabel(placeholder);
        label.setFont(ModernUITheme.FONT_BODY);
        label.setForeground(ModernUITheme.TEXT_PRIMARY);
        return label;
    }

    private JPanel createLabeledReadonly(String labelText, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(ModernUITheme.FONT_BODY);
        label.setForeground(ModernUITheme.TEXT_SECONDARY);
        label.setPreferredSize(new Dimension(110, 24));
        panel.add(label, BorderLayout.WEST);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(valueLabel, BorderLayout.CENTER);

        return panel;
    }

    private JTextField createTextField() {
        JTextField field = new JTextField();
        field.setFont(ModernUITheme.FONT_BODY);
        field.setPreferredSize(new Dimension(300, 36));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT, 1, true),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        return field;
    }

    private void styleComboBox(JComboBox<?> combo) {
        combo.setFont(ModernUITheme.FONT_BODY);
        combo.setPreferredSize(new Dimension(200, 36));
        combo.setBackground(Color.WHITE);
    }

    private JPanel createFormRow(String labelText, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(15, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(ModernUITheme.FONT_BODY);
        label.setForeground(ModernUITheme.TEXT_PRIMARY);
        label.setPreferredSize(new Dimension(180, 36));
        row.add(label, BorderLayout.WEST);

        row.add(field, BorderLayout.CENTER);

        return row;
    }

    private void loadCurrentInfo() {
        UserData userData = null;
        if (userFrame != null) {
            userData = userFrame.getUserData();
        }

        if (userData == null && userFrame != null) {
            if (userFrame.refreshUserData()) {
                userData = userFrame.getUserData();
            }
        }

        if (userData == null) {
            JOptionPane.showMessageDialog(this,
                    "Không thể đọc dữ liệu từ thẻ!\nVui lòng đăng nhập lại.",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Readonly fields
        lblHoTen.setText(userData.getHoTen() != null ? userData.getHoTen() : "---");
        lblNgaySinh.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "---");
        lblMaBHYT.setText(userData.getMaBHYT() != null ? userData.getMaBHYT() : "---");

        // Editable fields
        txtQueQuan.setText(userData.getQueQuan() != null ? userData.getQueQuan() : "");
        cboGioiTinh.setSelectedIndex(userData.getGender());
        cboNhomMau.setSelectedIndex(userData.getNhomMau());
        txtDiUng.setText(userData.getDiUng() != null ? userData.getDiUng() : "");
        txtBenhNen.setText(userData.getBenhNen() != null ? userData.getBenhNen() : "");

        // Load photo from card
        loadPhotoFromCard();
    }

    private void saveChanges() {
        UserData userData = userFrame.getUserData();
        if (userData == null) {
            JOptionPane.showMessageDialog(this,
                    "Không thể đọc dữ liệu hiện tại từ thẻ!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validate địa chỉ
        String queQuan = txtQueQuan.getText().trim();
        if (queQuan.length() > 100) {
            JOptionPane.showMessageDialog(this,
                    "Địa chỉ quá dài (tối đa 100 ký tự)!",
                    "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            txtQueQuan.requestFocus();
            return;
        }

        // Validate dị ứng
        String diUng = txtDiUng.getText().trim();
        if (diUng.length() > 50) {
            JOptionPane.showMessageDialog(this,
                    "Thông tin dị ứng quá dài (tối đa 50 ký tự)!",
                    "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            txtDiUng.requestFocus();
            return;
        }

        // Validate bệnh nền
        String benhNen = txtBenhNen.getText().trim();
        if (benhNen.length() > 50) {
            JOptionPane.showMessageDialog(this,
                    "Thông tin bệnh nền quá dài (tối đa 50 ký tự)!",
                    "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            txtBenhNen.requestFocus();
            return;
        }

        // Confirm dialog
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn cập nhật thông tin?\n\n" +
                        "• Địa chỉ: " + (queQuan.isEmpty() ? "(trống)" : queQuan) + "\n" +
                        "• Giới tính: " + cboGioiTinh.getSelectedItem() + "\n" +
                        "• Nhóm máu: " + cboNhomMau.getSelectedItem() + "\n" +
                        "• Dị ứng: " + (diUng.isEmpty() ? "(không có)" : diUng) + "\n" +
                        "• Bệnh nền: " + (benhNen.isEmpty() ? "(không có)" : benhNen),
                "Xác nhận cập nhật",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Đang lưu...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                // Create new UserData with updated values
                UserData newData = new UserData();

                // Copy readonly fields
                newData.setHoTen(userData.getHoTen());
                newData.setIdBenhNhan(userData.getIdBenhNhan());
                newData.setNgaySinh(userData.getNgaySinh());
                newData.setMaBHYT(userData.getMaBHYT());
                newData.setBalance(userData.getBalance());

                // Set editable fields
                newData.setQueQuan(queQuan);
                newData.setGender(cboGioiTinh.getSelectedIndex());
                newData.setNhomMau(cboNhomMau.getSelectedIndex());
                newData.setDiUng(diUng);
                newData.setBenhNen(benhNen);

                // Send to card
                byte[] dataBytes = newData.toBytes();
                return apduCommands.updateUserData(dataBytes);
            }

            @Override
            protected void done() {
                btnSave.setEnabled(true);
                btnSave.setText("Lưu thay đổi");

                try {
                    boolean success = get();
                    if (success) {
                        // Refresh userData in UserFrame
                        userFrame.refreshUserData();

                        JOptionPane.showMessageDialog(EditInfoPanel.this,
                                "Cập nhật thông tin thành công!\n\nThông tin mới đã được lưu vào thẻ.",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);

                        // Reload form with new data
                        loadCurrentInfo();
                    } else {
                        JOptionPane.showMessageDialog(EditInfoPanel.this,
                                "Không thể cập nhật thông tin!\n\nVui lòng thử lại hoặc liên hệ Admin.",
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(EditInfoPanel.this,
                            "Lỗi khi cập nhật: " + e.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }

    private void selectPhoto() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn ảnh đại diện");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Ảnh (*.jpg, *.jpeg, *.png, *.gif)", "jpg", "jpeg", "png", "gif"));
        fileChooser.setAcceptAllFileFilterUsed(false);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            try {
                // Resize và compress ảnh
                String base64 = ImageHelper.resizeAndCompressToBase64(selectedFile);

                if (base64 != null && !base64.isEmpty()) {
                    // Lưu vào thẻ ngay
                    boolean success = apduCommands.setPhotoChunked(base64);

                    if (success) {
                        photoBase64 = base64;
                        photoChanged = true;

                        // Hiển thị preview
                        java.awt.image.BufferedImage img = ImageHelper.decodeBase64ToImage(base64);
                        if (img != null) {
                            ImageIcon icon = ImageHelper.createScaledIcon(img, 100, 100);
                            lblPhotoPreview.setIcon(icon);
                            lblPhotoPreview.setText("");
                        }

                        JOptionPane.showMessageDialog(this,
                                "Đã cập nhật ảnh đại diện thành công!",
                                "Thành công", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Không thể lưu ảnh vào thẻ!\nVui lòng thử lại.",
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Lỗi khi xử lý ảnh:\n" + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void removePhoto() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa ảnh đại diện?",
                "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                // Xóa ảnh trên thẻ (gửi chuỗi rỗng)
                boolean success = apduCommands.setPhotoChunked("");

                if (success) {
                    photoBase64 = null;
                    photoChanged = true;
                    lblPhotoPreview.setIcon(null);
                    lblPhotoPreview.setText("Chưa có ảnh");

                    JOptionPane.showMessageDialog(this,
                            "Đã xóa ảnh đại diện!",
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Không thể xóa ảnh!\nVui lòng thử lại.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Lỗi khi xóa ảnh: " + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadPhotoFromCard() {
        try {
            String photo = apduCommands.getPhoto();
            if (photo != null && !photo.isEmpty()) {
                photoBase64 = photo;
                java.awt.image.BufferedImage img = ImageHelper.decodeBase64ToImage(photo);
                if (img != null) {
                    ImageIcon icon = ImageHelper.createScaledIcon(img, 100, 100);
                    lblPhotoPreview.setIcon(icon);
                    lblPhotoPreview.setText("");
                } else {
                    lblPhotoPreview.setIcon(null);
                    lblPhotoPreview.setText("Lỗi ảnh");
                }
            } else {
                lblPhotoPreview.setIcon(null);
                lblPhotoPreview.setText("Chưa có ảnh");
                photoBase64 = null;
            }
        } catch (Exception e) {
            System.err.println("[EditInfoPanel] Error loading photo: " + e.getMessage());
            lblPhotoPreview.setIcon(null);
            lblPhotoPreview.setText("Lỗi load ảnh");
        }
    }
}
