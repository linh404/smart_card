package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import ui.ModernUITheme;
import ui.SmartCardVisual;

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
    private JLabel lblBirthDate, lblAddress;
    // V4: Thông tin y tế khẩn cấp
    private JLabel lblNhomMau, lblDiUng, lblBenhNen;
    private ModernUITheme.RoundedButton btnRefresh;
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

        // Info card on the right
        ModernUITheme.CardPanel infoCard = new ModernUITheme.CardPanel();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setPreferredSize(new Dimension(400, 350)); // Tăng chiều cao cho thông tin y tế

        // Title
        JLabel titleLabel = new JLabel("📋 CHI TIẾT THÔNG TIN");
        titleLabel.setFont(ModernUITheme.FONT_SUBHEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.add(titleLabel);
        infoCard.add(Box.createVerticalStrut(15));

        // Info rows
        infoCard.add(createInfoRow("📅 Ngày sinh", "---"));
        lblBirthDate = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
        infoCard.add(Box.createVerticalStrut(8));

        infoCard.add(createInfoRow("📍 Địa chỉ", "---"));
        lblAddress = (JLabel) ((JPanel) infoCard.getComponent(infoCard.getComponentCount() - 1)).getComponent(1);
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

        // ===== BOTTOM: Refresh Button =====
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);

        btnRefresh = new ModernUITheme.RoundedButton(
                "🔄 Làm mới thông tin",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnRefresh.setPreferredSize(new Dimension(180, 44));
        btnRefresh.addActionListener(e -> loadInfo());
        btnPanel.add(btnRefresh);

        add(btnPanel, BorderLayout.SOUTH);
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

    private void loadInfo() {
        try {
            UserData userData = null;
            if (userFrame != null) {
                userData = userFrame.getUserData();
            }

            if (userData == null && userFrame != null && userFrame.getUserPin() != null) {
                if (userFrame.refreshUserData()) {
                    userData = userFrame.getUserData();
                }
            }

            if (userData == null) {
                cardVisual.setCardHolderName("---");
                cardVisual.setPatientId("---");
                cardVisual.setBalance(0);
                cardVisual.setBhytCode("");
                lblBirthDate.setText("---");
                lblAddress.setText("---");
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

            // V4: Update thông tin y tế khẩn cấp
            lblNhomMau.setText(userData.getNhomMauLabel());

            String diUng = userData.getDiUng();
            lblDiUng.setText((diUng != null && !diUng.isEmpty()) ? diUng : "Không có");

            String benhNen = userData.getBenhNen();
            lblBenhNen.setText((benhNen != null && !benhNen.isEmpty()) ? benhNen : "Không có");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
