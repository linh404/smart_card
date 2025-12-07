package ui.user;

import card.CardManager;
import card.APDUCommands;
import util.CryptoUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

            if (oldPin.isEmpty() || newPin.isEmpty() || confirmPin.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ thông tin!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!newPin.equals(confirmPin)) {
                JOptionPane.showMessageDialog(this, "PIN mới và xác nhận không khớp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (oldPin.equals(newPin)) {
                JOptionPane.showMessageDialog(this, "PIN mới phải khác PIN cũ!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Gửi PIN cũ và PIN mới dạng plaintext xuống thẻ
            // Thẻ sẽ hash và cập nhật trên thẻ
            byte[] oldPinBytes = oldPin.getBytes();
            byte[] newPinBytes = newPin.getBytes();

            // Gửi lệnh CHANGE_PIN
            if (apduCommands.changePin(oldPinBytes, newPinBytes)) {
                JOptionPane.showMessageDialog(this, "Đổi PIN thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                txtOldPin.setText("");
                txtNewPin.setText("");
                txtConfirmPin.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Đổi PIN thất bại! Kiểm tra lại PIN cũ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

