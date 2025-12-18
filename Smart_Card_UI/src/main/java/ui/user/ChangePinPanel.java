package ui.user;

import card.CardManager;
import card.APDUCommands;
import util.CryptoUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;

/**
 * ChangePinPanel - Panel đổi PIN User (User tự đổi khi biết PIN cũ)
 */
public class ChangePinPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame; // V3: Reference to UserFrame
    
    private JPasswordField txtOldPin, txtNewPin, txtConfirmPin;
    private JButton btnChange;

    public ChangePinPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }
    
    public ChangePinPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Đổi PIN"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // PIN cũ
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("PIN cũ:"), gbc);
        txtOldPin = new JPasswordField(20);
        gbc.gridx = 1;
        formPanel.add(txtOldPin, gbc);

        // PIN mới
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("PIN mới:"), gbc);
        txtNewPin = new JPasswordField(20);
        gbc.gridx = 1;
        formPanel.add(txtNewPin, gbc);

        // Xác nhận PIN mới
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Xác nhận PIN mới:"), gbc);
        txtConfirmPin = new JPasswordField(20);
        gbc.gridx = 1;
        formPanel.add(txtConfirmPin, gbc);

        // Button
        btnChange = new JButton("Đổi PIN");
        btnChange.setFont(new Font("Arial", Font.BOLD, 14));
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnChange);

        btnChange.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                changePin();
            }
        });

        add(formPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void changePin() {
        try {
            String oldPin = new String(txtOldPin.getPassword());
            String newPin = new String(txtNewPin.getPassword());
            String confirmPin = new String(txtConfirmPin.getPassword());

            // Validate: Không được rỗng
            if (oldPin.isEmpty() || newPin.isEmpty() || confirmPin.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ thông tin!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validate: PIN phải là 6 chữ số
            if (oldPin.length() != 6 || !oldPin.matches("^[0-9]+$")) {
                JOptionPane.showMessageDialog(this, 
                    "PIN cũ phải là 6 chữ số!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtOldPin.setText("");
                txtOldPin.requestFocus();
                return;
            }

            if (newPin.length() != 6 || !newPin.matches("^[0-9]+$")) {
                JOptionPane.showMessageDialog(this, 
                    "PIN mới phải là 6 chữ số!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtNewPin.setText("");
                txtNewPin.requestFocus();
                return;
            }

            if (confirmPin.length() != 6 || !confirmPin.matches("^[0-9]+$")) {
                JOptionPane.showMessageDialog(this, 
                    "Xác nhận PIN phải là 6 chữ số!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtConfirmPin.setText("");
                txtConfirmPin.requestFocus();
                return;
            }

            // Validate: PIN mới và xác nhận phải khớp
            if (!newPin.equals(confirmPin)) {
                JOptionPane.showMessageDialog(this, "PIN mới và xác nhận không khớp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtNewPin.setText("");
                txtConfirmPin.setText("");
                txtNewPin.requestFocus();
                return;
            }

            // Validate: PIN mới phải khác PIN cũ
            if (oldPin.equals(newPin)) {
                JOptionPane.showMessageDialog(this, "PIN mới phải khác PIN cũ!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtNewPin.setText("");
                txtConfirmPin.setText("");
                txtNewPin.requestFocus();
                return;
            }

            // Kiểm tra card có sẵn không
            if (apduCommands == null || !apduCommands.isChannelReady()) {
                JOptionPane.showMessageDialog(this, 
                    "Không thể kết nối với thẻ!\n\n" +
                    "Vui lòng đảm bảo thẻ đã được cắm vào đầu đọc.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Gửi PIN cũ và PIN mới dạng plaintext xuống thẻ
            // Thẻ sẽ hash và cập nhật trên thẻ
            // Sử dụng UTF-8 để đảm bảo encoding nhất quán
            byte[] oldPinBytes = oldPin.getBytes(StandardCharsets.UTF_8);
            byte[] newPinBytes = newPin.getBytes(StandardCharsets.UTF_8);
            
            // Đảm bảo PIN bytes đúng 6 bytes (cho numeric PIN, UTF-8 = ASCII = 1 byte per char)
            if (oldPinBytes.length != 6) {
                JOptionPane.showMessageDialog(this, 
                    "Lỗi: PIN cũ không đúng định dạng (phải là 6 bytes)!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (newPinBytes.length != 6) {
                JOptionPane.showMessageDialog(this, 
                    "Lỗi: PIN mới không đúng định dạng (phải là 6 bytes)!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Gửi lệnh CHANGE_PIN
            if (apduCommands.changePin(oldPinBytes, newPinBytes)) {
                JOptionPane.showMessageDialog(this, 
                    "Đổi PIN thành công!\n\n" +
                    "Vui lòng sử dụng PIN mới cho lần đăng nhập tiếp theo.", 
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);
                txtOldPin.setText("");
                txtNewPin.setText("");
                txtConfirmPin.setText("");
                
                // Cập nhật PIN trong UserFrame nếu có
                if (userFrame != null) {
                    // Note: UserFrame lưu PIN cũ, cần cập nhật sau khi đổi PIN thành công
                    // Tuy nhiên, để đảm bảo bảo mật, không tự động cập nhật
                    // User sẽ cần đăng nhập lại với PIN mới
                }
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Đổi PIN thất bại!\n\n" +
                    "Có thể:\n" +
                    "- PIN cũ không đúng\n" +
                    "- Thẻ đã bị khóa\n" +
                    "- Thẻ chưa được phát hành\n\n" +
                    "Vui lòng kiểm tra lại và thử lại.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtOldPin.setText("");
                txtOldPin.requestFocus();
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Lỗi: " + e.getMessage() + "\n\n" +
                "Vui lòng thử lại hoặc liên hệ quản trị viên.", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

