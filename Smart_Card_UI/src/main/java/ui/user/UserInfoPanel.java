package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import ui.ModernUITheme;
import ui.SmartCardVisual;
import util.ImageHelper;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * UserInfoPanel - Panel hiển thị thông tin thẻ User
 * V7: Split layout - Photo (left) and Info (right)
 */
public class UserInfoPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;

    // Info labels
    private JLabel lblHoTen, lblBirthDate, lblAddress, lblGioiTinh, lblMaBHYT, lblBalance;
    private JLabel lblNhomMau, lblDiUng, lblBenhNen;
    private JLabel lblPhotoPreview;
    private SmartCardVisual cardVisual;
    private NumberFormat currencyFormat;

    public UserInfoPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }

    public UserInfoPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

        setOpaque(false);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
        loadInfo();
    }

    private void initUI() {
        setLayout(new BorderLayout(30, 30));
        setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        // Main content - Split 40% left (photo) / 60% right (info)
        JPanel mainContent = new JPanel(new GridBagLayout());
        mainContent.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 15);

        // ===== LEFT SIDE: PHOTO =====
        gbc.gridx = 0;
        gbc.weightx = 0.4;

        ModernUITheme.CardPanel photoCard = new ModernUITheme.CardPanel();
        photoCard.setLayout(new GridBagLayout());

        GridBagConstraints photoGbc = new GridBagConstraints();
        photoGbc.gridx = 0;
        photoGbc.gridy = 0;
        photoGbc.anchor = GridBagConstraints.CENTER;

        JPanel photoContainer = new JPanel();
        photoContainer.setLayout(new BoxLayout(photoContainer, BoxLayout.Y_AXIS));
        photoContainer.setOpaque(false);

        // SmartCardVisual - 3D card
        cardVisual = new SmartCardVisual(SmartCardVisual.CardType.USER);
        cardVisual.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoContainer.add(cardVisual);
        photoContainer.add(Box.createVerticalStrut(30));

        JLabel photoTitle = new JLabel("ẢNH ĐẠI DIỆN", SwingConstants.CENTER);
        photoTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        photoTitle.setForeground(ModernUITheme.USER_PRIMARY);
        photoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoContainer.add(photoTitle);
        photoContainer.add(Box.createVerticalStrut(20));

        lblPhotoPreview = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        lblPhotoPreview.setPreferredSize(new Dimension(160, 160));
        lblPhotoPreview.setMinimumSize(new Dimension(160, 160));
        lblPhotoPreview.setMaximumSize(new Dimension(160, 160));
        lblPhotoPreview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ModernUITheme.USER_PRIMARY, 3),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setBackground(new Color(250, 250, 250));
        lblPhotoPreview.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblPhotoPreview.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPhotoPreview.setAlignmentX(Component.CENTER_ALIGNMENT);

        photoContainer.add(lblPhotoPreview);

        photoCard.add(photoContainer, photoGbc);
        mainContent.add(photoCard, gbc);

        // ===== RIGHT SIDE: INFO (2 columns) =====
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        gbc.insets = new Insets(0, 15, 0, 0);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 20));
        infoPanel.setOpaque(false);

        // Personal info card
        ModernUITheme.CardPanel personalCard = new ModernUITheme.CardPanel();
        personalCard.setLayout(new BoxLayout(personalCard, BoxLayout.Y_AXIS));

        JLabel personalTitle = new JLabel("THÔNG TIN CÁ NHÂN");
        personalTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        personalTitle.setForeground(ModernUITheme.USER_PRIMARY);
        personalTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        personalCard.add(personalTitle);
        personalCard.add(Box.createVerticalStrut(15));

        lblHoTen = addInfoRow(personalCard, "Họ và tên", "---");
        personalCard.add(Box.createVerticalStrut(10));

        lblBirthDate = addInfoRow(personalCard, "Ngày sinh", "---");
        personalCard.add(Box.createVerticalStrut(10));

        lblGioiTinh = addInfoRow(personalCard, "Giới tính", "---");
        personalCard.add(Box.createVerticalStrut(10));

        lblAddress = addInfoRow(personalCard, "Địa chỉ", "---");
        personalCard.add(Box.createVerticalStrut(10));

        lblMaBHYT = addInfoRow(personalCard, "Mã BHYT", "---");
        personalCard.add(Box.createVerticalStrut(10));

        lblBalance = addInfoRow(personalCard, "Số dư", "---");

        infoPanel.add(personalCard);

        // Medical info card
        ModernUITheme.CardPanel medicalCard = new ModernUITheme.CardPanel();
        medicalCard.setLayout(new BoxLayout(medicalCard, BoxLayout.Y_AXIS));

        JLabel medicalTitle = new JLabel("THÔNG TIN Y TẾ");
        medicalTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        medicalTitle.setForeground(new Color(220, 53, 69));
        medicalTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        medicalCard.add(medicalTitle);
        medicalCard.add(Box.createVerticalStrut(15));

        lblNhomMau = addInfoRow(medicalCard, "Nhóm máu", "---");
        medicalCard.add(Box.createVerticalStrut(10));

        lblDiUng = addInfoRowMultiline(medicalCard, "Dị ứng", "---");
        medicalCard.add(Box.createVerticalStrut(10));

        lblBenhNen = addInfoRowMultiline(medicalCard, "Bệnh nền", "---");
        medicalCard.add(Box.createVerticalGlue());

        infoPanel.add(medicalCard);

        mainContent.add(infoPanel, gbc);

        add(mainContent, BorderLayout.CENTER);
    }

    private JLabel addInfoRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblLabel = new JLabel(label + ":");
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblLabel.setPreferredSize(new Dimension(120, 26));
        row.add(lblLabel, BorderLayout.WEST);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblValue.setForeground(ModernUITheme.TEXT_PRIMARY);
        row.add(lblValue, BorderLayout.CENTER);

        parent.add(row);
        return lblValue;
    }

    private JLabel addInfoRowMultiline(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblLabel = new JLabel("<html>" + label + ":</html>");
        lblLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblLabel.setPreferredSize(new Dimension(120, 26));
        lblLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(lblLabel, BorderLayout.WEST);

        JLabel lblValue = new JLabel("<html>" + value + "</html>");
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblValue.setForeground(ModernUITheme.TEXT_PRIMARY);
        row.add(lblValue, BorderLayout.CENTER);

        parent.add(row);
        return lblValue;
    }

    public void loadInfo() {
        try {
            UserData userData = null;

            if (userFrame != null && userFrame.getUserPin() != null) {
                System.out.println("[UserInfoPanel] Refreshing user data...");
                if (userFrame.refreshUserData()) {
                    userData = userFrame.getUserData();
                } else {
                    System.err.println("[UserInfoPanel] Failed to refresh user data");
                }
            }

            if (userData == null) {
                // Clear all fields
                cardVisual.setCardHolderName("---");
                cardVisual.setPatientId("---");
                cardVisual.setBalance(0);
                cardVisual.setBhytCode("");

                lblHoTen.setText("---");
                lblBirthDate.setText("---");
                lblAddress.setText("---");
                lblGioiTinh.setText("---");
                lblMaBHYT.setText("---");
                lblBalance.setText("---");
                lblNhomMau.setText("---");
                lblDiUng.setText("<html>---</html>");
                lblBenhNen.setText("<html>---</html>");

                JOptionPane.showMessageDialog(this,
                        "Không thể đọc dữ liệu từ thẻ!\n\nVui lòng đăng nhập lại.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Update card visual
            cardVisual.setCardHolderName(userData.getHoTen());
            cardVisual.setPatientId(userData.getIdBenhNhan());
            cardVisual.setBalance(userData.getBalance());
            cardVisual.setBhytCode(userData.getMaBHYT());

            // Update personal info
            lblHoTen.setText(userData.getHoTen() != null ? userData.getHoTen() : "---");
            lblBirthDate.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "---");
            lblAddress.setText(userData.getQueQuan() != null ? userData.getQueQuan() : "---");
            lblGioiTinh.setText(userData.getGenderLabel());
            lblMaBHYT.setText(userData.getMaBHYT() != null ? userData.getMaBHYT() : "---");
            lblBalance.setText(currencyFormat.format(userData.getBalance()));

            // Update medical info
            lblNhomMau.setText(userData.getNhomMauLabel());

            String diUng = userData.getDiUng();
            lblDiUng.setText("<html>" + ((diUng != null && !diUng.isEmpty()) ? diUng : "Không có") + "</html>");

            String benhNen = userData.getBenhNen();
            lblBenhNen.setText("<html>" + ((benhNen != null && !benhNen.isEmpty()) ? benhNen : "Không có") + "</html>");

            // Load photo
            try {
                System.out.println("[UserInfoPanel] Loading photo from card...");
                String photoBase64 = apduCommands.getPhoto();

                if (photoBase64 != null && !photoBase64.isEmpty()) {
                    System.out.println("[UserInfoPanel] Photo loaded, size: " + photoBase64.length() + " chars");
                    java.awt.image.BufferedImage photoImage = ImageHelper.decodeBase64ToImage(photoBase64);

                    if (photoImage != null) {
                        ImageIcon photoIcon = ImageHelper.createScaledIcon(photoImage, 160, 160);
                        lblPhotoPreview.setIcon(photoIcon);
                        lblPhotoPreview.setText("");
                        lblPhotoPreview.revalidate();
                        lblPhotoPreview.repaint();
                        System.out.println("[UserInfoPanel] ✓ Photo displayed successfully");
                    } else {
                        lblPhotoPreview.setIcon(null);
                        lblPhotoPreview.setText("Lỗi ảnh");
                        System.err.println("[UserInfoPanel] Failed to decode photo");
                    }
                } else {
                    lblPhotoPreview.setIcon(null);
                    lblPhotoPreview.setText("Chưa có ảnh");
                    System.out.println("[UserInfoPanel] No photo on card");
                }
            } catch (Exception photoEx) {
                System.err.println("[UserInfoPanel] Error loading photo: " + photoEx.getMessage());
                photoEx.printStackTrace();
                lblPhotoPreview.setIcon(null);
                lblPhotoPreview.setText("Lỗi load ảnh");
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
