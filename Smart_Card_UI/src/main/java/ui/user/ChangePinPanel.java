package ui.user;

import card.CardManager;
import card.APDUCommands;
import ui.ModernUITheme;
import util.CryptoUtils;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

/**
 * ChangePinPanel - Panel đổi PIN User
 * V3: Modern UI update
 */
public class ChangePinPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;

    private ModernUITheme.RoundedPasswordField txtOldPin, txtNewPin, txtConfirmPin;
    private ModernUITheme.RoundedButton btnChange;

    public ChangePinPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }

    public ChangePinPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;

        setOpaque(false);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Center Card
        ModernUITheme.CardPanel card = new ModernUITheme.CardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Title
        JLabel titleLabel = new JLabel("🔐 ĐỔI MÃ PIN");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        JLabel subtitleLabel = new JLabel("Cập nhật mã PIN bảo mật cho thẻ của bạn");
        subtitleLabel.setFont(ModernUITheme.FONT_SMALL);
        subtitleLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(subtitleLabel);

        card.add(Box.createVerticalStrut(25));

        // Form Fields
        card.add(createLabel("Mã PIN hiện tại"));
        txtOldPin = new ModernUITheme.RoundedPasswordField(20);
        txtOldPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtOldPin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        card.add(txtOldPin);
        card.add(Box.createVerticalStrut(15));

        card.add(createLabel("Mã PIN mới (6 số)"));
        txtNewPin = new ModernUITheme.RoundedPasswordField(20);
        txtNewPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtNewPin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        card.add(txtNewPin);
        card.add(Box.createVerticalStrut(15));

        card.add(createLabel("Xác nhận PIN mới"));
        txtConfirmPin = new ModernUITheme.RoundedPasswordField(20);
        txtConfirmPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtConfirmPin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        card.add(txtConfirmPin);
        card.add(Box.createVerticalStrut(30));

        // Button
        btnChange = new ModernUITheme.RoundedButton(
                "✓ Đổi mã PIN",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnChange.setPreferredSize(new Dimension(100, 45)); // Width handled by layout
        btnChange.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        btnChange.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnChange.addActionListener(e -> changePin());
        card.add(btnChange);

        // Wrap for center
        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerWrapper.setOpaque(false);
        card.setPreferredSize(new Dimension(400, 420));
        centerWrapper.add(card);

        add(centerWrapper, BorderLayout.CENTER);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ModernUITheme.FONT_SUBHEADING);
        label.setForeground(ModernUITheme.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void changePin() {
        try {
            String oldPin = new String(txtOldPin.getPassword());
            String newPin = new String(txtNewPin.getPassword());
            String confirmPin = new String(txtConfirmPin.getPassword());

            // Validate
            if (oldPin.isEmpty() || newPin.isEmpty() || confirmPin.isEmpty()) {
                showWarning("Vui lòng điền đầy đủ thông tin!");
                return;
            }

            if (!isValidPin(oldPin) || !isValidPin(newPin) || !isValidPin(confirmPin)) {
                showError("Mã PIN phải là 6 chữ số!");
                return;
            }

            if (!newPin.equals(confirmPin)) {
                showWarning("Mã PIN mới xác nhận không khớp!");
                return;
            }

            if (oldPin.equals(newPin)) {
                showWarning("Mã PIN mới phải khác mã PIN cũ!");
                return;
            }

            if (apduCommands == null) {
                showError("Lỗi kết nối thẻ!");
                return;
            }

            byte[] oldPinBytes = oldPin.getBytes(StandardCharsets.UTF_8);
            byte[] newPinBytes = newPin.getBytes(StandardCharsets.UTF_8);

            if (apduCommands.changePin(oldPinBytes, newPinBytes)) {
                JOptionPane.showMessageDialog(this,
                        "Đổi PIN thành công!\nVui lòng sử dụng PIN mới cho lần đăng nhập sau.",
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
                clearFields();
            } else {
                showError("Đổi PIN thất bại! PIN cũ không đúng hoặc thẻ bị lỗi.");
                txtOldPin.setText("");
                txtOldPin.requestFocus();
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi: " + e.getMessage());
        }
    }

    private boolean isValidPin(String pin) {
        return pin.length() == 6 && pin.matches("^[0-9]+$");
    }

    private void clearFields() {
        txtOldPin.setText("");
        txtNewPin.setText("");
        txtConfirmPin.setText("");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Cảnh báo", JOptionPane.WARNING_MESSAGE);
    }
}
