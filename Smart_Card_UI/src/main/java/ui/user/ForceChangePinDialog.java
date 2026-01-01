package ui.user;

import card.CardManager;
import card.APDUCommands;
import ui.ModernUITheme;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;

/**
 * ForceChangePinDialog - Dialog bắt buộc đổi PIN khi user đăng nhập lần đầu
 * V4: Bắt buộc đổi PIN mặc định 123456 trước khi sử dụng
 */
public class ForceChangePinDialog extends JDialog {

    private JPasswordField txtNewPin;
    private JPasswordField txtConfirmPin;
    private ModernUITheme.RoundedButton btnChange;
    private ModernUITheme.OutlineButton btnExit;

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private String currentPin;

    private boolean pinChanged = false;
    private String newPin;

    public ForceChangePinDialog(JFrame parent, CardManager cardManager, APDUCommands apduCommands, String currentPin) {
        super(parent, "Đổi mã PIN bắt buộc", true); // Modal
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.currentPin = currentPin;

        initUI();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // Không cho đóng cửa sổ
    }

    private void initUI() {
        setSize(520, 530); // Tăng kích thước dialog
        setLocationRelativeTo(getParent());
        setResizable(false);

        // Main panel with gradient background
        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.BG_PRIMARY,
                        getWidth(), getHeight(), ModernUITheme.BG_SECONDARY);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Center card
        ModernUITheme.CardPanel card = new ModernUITheme.CardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Warning icon and title
        JLabel iconLabel = new JLabel("⚠️");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(iconLabel);
        card.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel("ĐỔI MÃ PIN BẮT BUỘC");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(new Color(220, 53, 69)); // Danger color
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(10));

        // Warning message
        JLabel msgLabel = new JLabel("<html><center>" +
                "Bạn đang sử dụng mã PIN mặc định.<br><br>" +
                "<b>Để đảm bảo an toàn, bạn PHẢI đổi<br>mã PIN trước khi sử dụng thẻ.</b><br><br>" +
                "Mã PIN mới phải là 6 chữ số và<br>không được trùng với PIN mặc định." +
                "</center></html>");
        msgLabel.setFont(ModernUITheme.FONT_BODY);
        msgLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(msgLabel);
        card.add(Box.createVerticalStrut(25));

        // Form panel
        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // New PIN field
        JLabel lblNewPin = new JLabel("Mã PIN mới (6 chữ số)");
        lblNewPin.setFont(ModernUITheme.FONT_SUBHEADING);
        lblNewPin.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblNewPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblNewPin);
        formPanel.add(Box.createVerticalStrut(5));

        txtNewPin = new JPasswordField(20);
        txtNewPin.setMaximumSize(new Dimension(300, 45)); // Tăng chiều rộng và cao
        txtNewPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtNewPin.setFont(new Font("Consolas", Font.BOLD, 18)); // Tăng font size
        formPanel.add(txtNewPin);
        formPanel.add(Box.createVerticalStrut(15));

        // Confirm PIN field
        JLabel lblConfirmPin = new JLabel("Xác nhận mã PIN mới");
        lblConfirmPin.setFont(ModernUITheme.FONT_SUBHEADING);
        lblConfirmPin.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblConfirmPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblConfirmPin);
        formPanel.add(Box.createVerticalStrut(5));

        txtConfirmPin = new JPasswordField(20);
        txtConfirmPin.setMaximumSize(new Dimension(300, 45)); // Tăng chiều rộng và cao
        txtConfirmPin.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtConfirmPin.setFont(new Font("Consolas", Font.BOLD, 18)); // Tăng font size
        formPanel.add(txtConfirmPin);

        // Center form
        JPanel formWrapper = new JPanel();
        formWrapper.setOpaque(false);
        formWrapper.add(formPanel);
        card.add(formWrapper);
        card.add(Box.createVerticalStrut(30)); // Tăng khoảng cách

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0)); // Tăng khoảng cách nút
        btnPanel.setOpaque(false);

        btnChange = new ModernUITheme.RoundedButton(
                "Đổi PIN",
                ModernUITheme.SUCCESS,
                new Color(25, 135, 84),
                ModernUITheme.TEXT_WHITE);
        btnChange.setPreferredSize(new Dimension(140, 48)); // Nút to hơn
        btnChange.addActionListener(e -> changePin());
        btnPanel.add(btnChange);

        btnExit = new ModernUITheme.OutlineButton(
                "Thoát",
                ModernUITheme.TEXT_SECONDARY,
                ModernUITheme.BG_SECONDARY,
                ModernUITheme.TEXT_SECONDARY);
        btnExit.setPreferredSize(new Dimension(120, 48)); // Nút to hơn
        btnExit.addActionListener(e -> handleExit());
        btnPanel.add(btnExit);

        card.add(btnPanel);

        // Add card to main panel
        mainPanel.add(card, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // Enter key to submit
        txtConfirmPin.addActionListener(e -> changePin());
    }

    private void changePin() {
        String newPinStr = new String(txtNewPin.getPassword());
        String confirmPinStr = new String(txtConfirmPin.getPassword());

        // Validate
        if (newPinStr.isEmpty()) {
            showError("Vui lòng nhập mã PIN mới!");
            txtNewPin.requestFocus();
            return;
        }

        if (newPinStr.length() != 6 || !newPinStr.matches("^[0-9]+$")) {
            showError("Mã PIN mới phải là 6 chữ số!");
            txtNewPin.setText("");
            txtConfirmPin.setText("");
            txtNewPin.requestFocus();
            return;
        }

        if (!newPinStr.equals(confirmPinStr)) {
            showError("Mã PIN xác nhận không khớp!");
            txtConfirmPin.setText("");
            txtConfirmPin.requestFocus();
            return;
        }

        // Không cho đặt PIN mới = PIN mặc định
        if (newPinStr.equals("123456")) {
            showError("Không được đặt PIN mới trùng với PIN mặc định 123456!");
            txtNewPin.setText("");
            txtConfirmPin.setText("");
            txtNewPin.requestFocus();
            return;
        }

        // Không cho đặt PIN mới = PIN cũ
        if (newPinStr.equals(currentPin)) {
            showError("PIN mới không được trùng PIN cũ!");
            txtNewPin.setText("");
            txtConfirmPin.setText("");
            txtNewPin.requestFocus();
            return;
        }

        // Gọi changePin trên thẻ
        try {
            byte[] oldPinBytes = currentPin.getBytes(StandardCharsets.UTF_8);
            byte[] newPinBytes = newPinStr.getBytes(StandardCharsets.UTF_8);

            System.out.println("[ForceChangePinDialog] Đang đổi PIN...");

            if (apduCommands.changePin(oldPinBytes, newPinBytes)) {
                this.pinChanged = true;
                this.newPin = newPinStr;

                JOptionPane.showMessageDialog(this,
                        "Đổi mã PIN thành công!\n\n" +
                                "Mã PIN mới của bạn: " + newPinStr + "\n\n" +
                                "Vui lòng ghi nhớ mã PIN này!",
                        "Thành công",
                        JOptionPane.INFORMATION_MESSAGE);

                dispose();
            } else {
                showError("Đổi PIN thất bại!\n\nVui lòng thử lại hoặc liên hệ admin.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi khi đổi PIN: " + e.getMessage());
        }
    }

    private void handleExit() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn PHẢI đổi mã PIN trước khi sử dụng!\n\n" +
                        "Nếu thoát bây giờ, bạn sẽ không thể đăng nhập.\n\n" +
                        "Bạn có chắc muốn thoát không?",
                "Cảnh báo",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            this.pinChanged = false;
            dispose();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Kiểm tra user đã đổi PIN thành công chưa
     */
    public boolean isPinChanged() {
        return pinChanged;
    }

    /**
     * Lấy PIN mới (sau khi đổi thành công)
     */
    public String getNewPin() {
        return newPin;
    }
}
