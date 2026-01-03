package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import ui.ModernUITheme;

import javax.swing.*;
import java.awt.*;

/**
 * BHYTPanel - Panel hiển thị thông tin BHYT
 * V3: Modern UI update
 */
public class BHYTPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;

    private JLabel lblMaBHYT, lblHoTen, lblNgaySinh, lblGioiTinh, lblSoThe, lblNgayHetHan, lblMucHuong;

    public BHYTPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }

    public BHYTPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;

        setOpaque(false);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
        loadBHYTInfo();
    }

    private void initUI() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Center Card to hold info
        ModernUITheme.CardPanel card = new ModernUITheme.CardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Title
        JLabel titleLabel = new JLabel("🏥 THÔNG TIN BẢO HIỂM Y TẾ");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(25));

        // Info Rows
        lblMaBHYT = addInfoRow(card, "Mã BHYT", "---");
        card.add(Box.createVerticalStrut(10));

        lblHoTen = addInfoRow(card, "Họ tên", "---");
        card.add(Box.createVerticalStrut(10));

        lblNgaySinh = addInfoRow(card, "Ngày sinh", "---");
        card.add(Box.createVerticalStrut(10));

        lblGioiTinh = addInfoRow(card, "Giới tính", "---");
        card.add(Box.createVerticalStrut(10));

        lblSoThe = addInfoRow(card, "Số thẻ", "---");
        card.add(Box.createVerticalStrut(10));

        lblNgayHetHan = addInfoRow(card, "Ngày hết hạn", "---");
        card.add(Box.createVerticalStrut(10));

        lblMucHuong = addInfoRow(card, "Mức hưởng", "---");
        card.add(Box.createVerticalStrut(15));

        // Wrap logic for center alignment
        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerWrapper.setOpaque(false);
        // Make card wide enough but not too wide
        card.setPreferredSize(new Dimension(500, 450));
        centerWrapper.add(card);

        add(centerWrapper, BorderLayout.CENTER);
    }

    private JLabel addInfoRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblLabel = new JLabel(label + ":");
        lblLabel.setFont(ModernUITheme.FONT_SUBHEADING);
        lblLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        lblLabel.setPreferredSize(new Dimension(120, 30));
        row.add(lblLabel, BorderLayout.WEST);

        JLabel lblValue = new JLabel(value);
        lblValue.setFont(ModernUITheme.FONT_BODY);
        lblValue.setForeground(ModernUITheme.TEXT_PRIMARY);
        row.add(lblValue, BorderLayout.CENTER);

        // Add a bottom border separator for cleaner look
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(row, BorderLayout.CENTER);

        JSeparator sep = new JSeparator();
        sep.setForeground(ModernUITheme.BORDER_LIGHT);
        wrapper.add(sep, BorderLayout.SOUTH);

        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        parent.add(wrapper);

        return lblValue;
    }

    public void loadBHYTInfo() {
        try {
            UserData userData = null;
            if (userFrame != null) {
                userData = userFrame.getUserData();
            }

            if (userData == null) {
                JOptionPane.showMessageDialog(this,
                        "Không thể đọc dữ liệu từ thẻ!\n\n" +
                                "Vui lòng đăng nhập lại.",
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String maBHYT = userData.getMaBHYT();
            if (maBHYT == null || maBHYT.isEmpty()) {
                lblMaBHYT.setText("---");
                JOptionPane.showMessageDialog(this, "Mã BHYT không có trong thẻ!", "Cảnh báo",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Update labels
            // Note: need to access the JLabel inside the wrapper components
            // But strict component structure access is brittle.
            // In initUI, we assigned the class fields to the created labels.
            // So we can just set text directly.

            lblMaBHYT.setText(maBHYT);
            lblHoTen.setText(userData.getHoTen() != null ? userData.getHoTen() : "---");
            lblNgaySinh.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "---");
            lblGioiTinh.setText(userData.getGenderLabel());
            lblSoThe.setText(maBHYT);

            // V7: Đọc mức hưởng và ngày hết hạn từ UserData (với fallback)
            String expiryDate = userData.getBhytExpiryDate();
            int coverageRate = userData.getBhytCoverageRate();

            lblNgayHetHan.setText(expiryDate != null && !expiryDate.isEmpty() ? expiryDate : "31/12/2027");
            lblMucHuong.setText(coverageRate > 0 ? coverageRate + "%" : "60%");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
