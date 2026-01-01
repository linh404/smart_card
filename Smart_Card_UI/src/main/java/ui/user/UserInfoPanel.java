package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import ui.ModernUITheme;
import ui.SmartCardVisual;
import util.ImageHelper; // V6: Import ImageHelper

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * UserInfoPanel - Panel hiển thị thông tin thẻ User
 * V3: Premium UI với 3D smart card mockup và modern design
 * V4: Thêm hiển thị thông tin y tế khẩn cấp
 */
public class UserInfoPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;

    // Visual components
    private SmartCardVisual cardVisual;
    private JLabel lblBirthDate, lblAddress, lblGioiTinh; // V5: Thêm giới tính
    // V4: Thông tin y tế khẩn cấp
    private JLabel lblNhomMau, lblDiUng, lblBenhNen;
    private JLabel lblPhotoPreview; // V6: Ảnh đại diện
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
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ===== TOP SECTION: 3D Card Visual =====
        JPanel cardSection = new JPanel(new BorderLayout(30, 0));
        cardSection.setOpaque(false);

        // Card visual
        cardVisual = new SmartCardVisual(SmartCardVisual.CardType.USER);

        JPanel cardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        cardWrapper.setOpaque(false);
        cardWrapper.add(cardVisual);
        cardSection.add(cardWrapper, BorderLayout.WEST);

        // V6: Photo preview will be in info card header (removed standalone panel)

        // Info card on the right
        ModernUITheme.CardPanel infoCard = new ModernUITheme.CardPanel();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setPreferredSize(new Dimension(500, 350)); // Tăng width cho ảnh

        // V6: Header with title (left) and photo (right)
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setOpaque(false);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Title on left
        JLabel titleLabel = new JLabel("📋 CHI TIẾT THÔNG TIN");
        titleLabel.setFont(ModernUITheme.FONT_SUBHEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        // Photo on right
        JPanel photoPanel = new JPanel();
        photoPanel.setOpaque(false);
        photoPanel.setLayout(new BoxLayout(photoPanel, BoxLayout.Y_AXIS));

        JLabel lblPhotoLabel = new JLabel("Ảnh đại diện", SwingConstants.CENTER);
        lblPhotoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblPhotoLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblPhotoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoPanel.add(lblPhotoLabel);
        photoPanel.add(Box.createVerticalStrut(3));

        lblPhotoPreview = new JLabel("Chưa có ảnh", SwingConstants.CENTER);
        lblPhotoPreview.setPreferredSize(new Dimension(100, 100));
        lblPhotoPreview.setMinimumSize(new Dimension(100, 100));
        lblPhotoPreview.setMaximumSize(new Dimension(100, 100));
        lblPhotoPreview.setBorder(BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT, 2));
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setBackground(new Color(250, 250, 250));
        lblPhotoPreview.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblPhotoPreview.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblPhotoPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoPanel.add(lblPhotoPreview);

        headerPanel.add(photoPanel, BorderLayout.EAST);

        infoCard.add(headerPanel);
        infoCard.add(Box.createVerticalStrut(15));

        // Info rows
        infoCard.add(createInfoRow("📅 Ngày sinh", "---"));
        lblBirthDate = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(8));

        infoCard.add(createInfoRow("📍 Địa chỉ", "---"));
        lblAddress = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(8));

        // V5: Giới tính
        infoCard.add(createInfoRow("👤 Giới tính", "---"));
        lblGioiTinh = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(15));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(ModernUITheme.BORDER_LIGHT);
        infoCard.add(sep);
        infoCard.add(Box.createVerticalStrut(10));

        // ===== V4: THÔNG TIN Y TẾ KHẨN CẤP =====
        JLabel emergencyTitle = new JLabel("🏥 THÔNG TIN Y TẾ KHẨN CẤP");
        emergencyTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        emergencyTitle.setForeground(new Color(220, 53, 69)); // Màu đỏ cảnh báo
        emergencyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.add(emergencyTitle);
        infoCard.add(Box.createVerticalStrut(10));

        infoCard.add(createInfoRow("🩸 Nhóm máu", "---"));
        lblNhomMau = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(8));

        infoCard.add(createInfoRow("⚠️ Dị ứng", "---"));
        lblDiUng = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(8));

        infoCard.add(createInfoRow("🏥 Bệnh nền", "---"));
        lblBenhNen = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(10));

        // Separator 2
        JSeparator sep2 = new JSeparator();
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep2.setForeground(ModernUITheme.BORDER_LIGHT);
        infoCard.add(sep2);
        infoCard.add(Box.createVerticalStrut(10));

        // Tips
        JLabel tipLabel = new JLabel("<html>" +
                "<b>💡 Mẹo sử dụng:</b><br>" +
                "• Di chuột vào thẻ để xem hiệu ứng 3D<br>" +
                "• Click vào thẻ để xem mặt sau<br>" +
                "• Thông tin y tế giúp cấp cứu nhanh hơn" +
                "</html>");
        tipLabel.setFont(ModernUITheme.FONT_SMALL);
        tipLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        tipLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.add(tipLabel);

        cardSection.add(infoCard, BorderLayout.CENTER);

        add(cardSection, BorderLayout.CENTER);
    }

    private JPanel createInfoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(ModernUITheme.FONT_BODY);
        lblLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblLabel.setPreferredSize(new Dimension(110, 20));
        row.add(lblLabel, BorderLayout.WEST);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(ModernUITheme.FONT_BODY);
        lblValue.setForeground(ModernUITheme.TEXT_PRIMARY);
        row.add(lblValue, BorderLayout.CENTER);

        return row;
    }

    public void loadInfo() {
        try {
            UserData userData = null;

            // V6: Always refresh to ensure PIN is verified (needed for getPhoto)
            // MK_user is transient and cleared when card deselected
            if (userFrame != null && userFrame.getUserPin() != null) {
                System.out.println("[UserInfoPanel] Refreshing user data to verify PIN...");
                if (userFrame.refreshUserData()) {
                    userData = userFrame.getUserData();
                } else {
                    System.err.println("[UserInfoPanel] Failed to refresh user data");
                }
            }

            if (userData == null) {
                cardVisual.setCardHolderName("---");
                cardVisual.setPatientId("---");
                cardVisual.setBalance(0);
                cardVisual.setBhytCode("");
                lblBirthDate.setText("---");
                lblAddress.setText("---");
                lblGioiTinh.setText("---"); // V5
                // V4: Clear thông tin y tế
                lblNhomMau.setText("---");
                lblDiUng.setText("---");
                lblBenhNen.setText("---");

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

            // Update info labels
            lblBirthDate.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "---");
            lblAddress.setText(userData.getQueQuan() != null ? userData.getQueQuan() : "---");
            lblGioiTinh.setText(userData.getGenderLabel()); // V5: Hiển thị giới tính

            // V4: Update thông tin y tế khẩn cấp
            lblNhomMau.setText(userData.getNhomMauLabel());

            String diUng = userData.getDiUng();
            lblDiUng.setText((diUng != null && !diUng.isEmpty()) ? diUng : "Không có");

            String benhNen = userData.getBenhNen();
            lblBenhNen.setText((benhNen != null && !benhNen.isEmpty()) ? benhNen : "Không có");

            // V6: Load ảnh đại diện từ thẻ
            try {
                System.out.println("[UserInfoPanel] Loading photo from card...");
                String photoBase64 = apduCommands.getPhoto();

                if (photoBase64 != null && !photoBase64.isEmpty()) {
                    System.out.println("[UserInfoPanel] Photo loaded, size: " + photoBase64.length() + " chars");
                    java.awt.image.BufferedImage photoImage = ImageHelper.decodeBase64ToImage(photoBase64);

                    if (photoImage != null) {
                        ImageIcon photoIcon = ImageHelper.createScaledIcon(photoImage, 100, 100);
                        lblPhotoPreview.setIcon(photoIcon);
                        lblPhotoPreview.setText("");
                        // Force UI refresh
                        lblPhotoPreview.revalidate();
                        lblPhotoPreview.repaint();
                        System.out.println("[UserInfoPanel] ✓ Photo displayed successfully");
                    } else {
                        lblPhotoPreview.setIcon(null);
                        lblPhotoPreview.setText("Lỗi ảnh");
                        lblPhotoPreview.revalidate();
                        lblPhotoPreview.repaint();
                        System.err.println("[UserInfoPanel] Failed to decode photo");
                    }
                } else {
                    lblPhotoPreview.setIcon(null);
                    lblPhotoPreview.setText("Chưa có ảnh");
                    lblPhotoPreview.revalidate();
                    lblPhotoPreview.repaint();
                    System.out.println("[UserInfoPanel] No photo on card");
                }
            } catch (Exception photoEx) {
                System.err.println("[UserInfoPanel] Error loading photo: " + photoEx.getMessage());
                photoEx.printStackTrace();
                lblPhotoPreview.setIcon(null);
                lblPhotoPreview.setText("Lỗi load ảnh");
                lblPhotoPreview.revalidate();
                lblPhotoPreview.repaint();
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
