package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * TransactionPanel - Panel nạp tiền và thanh toán
 */
public class TransactionPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;
    
    private JTextField txtAmount;
    private JRadioButton rbCredit, rbDebit;
    private JButton btnExecute, btnRefresh;
    private JLabel lblBalance;
    private NumberFormat currencyFormat;

    public TransactionPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }
    
    public TransactionPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        initUI();
        updateBalance();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Nạp tiền / Thanh toán"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Số dư hiện tại
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        JLabel lblBalanceTitle = new JLabel("Số dư hiện tại:");
        lblBalanceTitle.setFont(new Font("Arial", Font.BOLD, 14));
        formPanel.add(lblBalanceTitle, gbc);
        
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        lblBalance = new JLabel("-");
        lblBalance.setFont(new Font("Arial", Font.BOLD, 18));
        lblBalance.setForeground(new Color(0, 153, 0));
        formPanel.add(lblBalance, gbc);
        
        // Refresh button
        btnRefresh = new JButton("🔄 Làm mới");
        btnRefresh.setPreferredSize(new Dimension(120, 30));
        btnRefresh.addActionListener(e -> updateBalance());
        gbc.gridx = 2;
        formPanel.add(btnRefresh, gbc);

        // Loại giao dịch
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Loại giao dịch:"), gbc);
        ButtonGroup group = new ButtonGroup();
        rbCredit = new JRadioButton("Nạp tiền", true);
        rbDebit = new JRadioButton("Thanh toán");
        group.add(rbCredit);
        group.add(rbDebit);
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(rbCredit);
        radioPanel.add(rbDebit);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(radioPanel, gbc);

        // Số tiền
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        formPanel.add(new JLabel("Số tiền (VNĐ):"), gbc);
        txtAmount = new JTextField(20);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(txtAmount, gbc);

        // Ghi chú
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.CENTER;
        JLabel lblNote = new JLabel("<html><i>Lưu ý: Số tiền được mã hóa và lưu trữ an toàn trên thẻ</i></html>");
        lblNote.setForeground(Color.GRAY);
        formPanel.add(lblNote, gbc);

        // Button
        btnExecute = new JButton("Thực hiện giao dịch");
        btnExecute.setFont(new Font("Arial", Font.BOLD, 14));
        btnExecute.setPreferredSize(new Dimension(200, 40));
        btnExecute.setBackground(new Color(0, 153, 102));
        btnExecute.setForeground(Color.WHITE);
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnExecute);

        btnExecute.addActionListener(e -> executeTransaction());

        add(formPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void updateBalance() {
        if (userFrame == null) {
            lblBalance.setText("N/A");
            return;
        }
        
        UserData userData = userFrame.getUserData();
        if (userData != null) {
            lblBalance.setText(currencyFormat.format(userData.getBalance()));
        } else {
            lblBalance.setText("-");
        }
    }

    private void executeTransaction() {
        if (userFrame == null || userFrame.getUserPin() == null) {
            JOptionPane.showMessageDialog(this, 
                "Không có thông tin xác thực. Vui lòng đăng nhập lại.", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Validate amount
        String amountStr = txtAmount.getText().trim();
        if (amountStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Vui lòng nhập số tiền!", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        long amount;
        try {
            amount = Long.parseLong(amountStr);
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, 
                    "Số tiền phải lớn hơn 0!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, 
                "Số tiền không hợp lệ!", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Get current balance
        UserData userData = userFrame.getUserData();
        if (userData == null) {
            JOptionPane.showMessageDialog(this, 
                "Không thể đọc thông tin thẻ!", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        long currentBalance = userData.getBalance();
        long newBalance;
        
        if (rbCredit.isSelected()) {
            // Nạp tiền
            newBalance = currentBalance + amount;
            
            int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Xác nhận nạp %s vào thẻ?\n\nSố dư hiện tại: %s\nSố dư sau nạp: %s",
                    currencyFormat.format(amount),
                    currencyFormat.format(currentBalance),
                    currencyFormat.format(newBalance)),
                "Xác nhận nạp tiền",
                JOptionPane.YES_NO_OPTION);
            
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        } else {
            // Thanh toán
            if (amount > currentBalance) {
                JOptionPane.showMessageDialog(this, 
                    String.format("Số dư không đủ!\n\nSố dư hiện tại: %s\nSố tiền cần thanh toán: %s",
                        currencyFormat.format(currentBalance),
                        currencyFormat.format(amount)), 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            newBalance = currentBalance - amount;
            
            int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Xác nhận thanh toán %s?\n\nSố dư hiện tại: %s\nSố dư sau thanh toán: %s",
                    currencyFormat.format(amount),
                    currencyFormat.format(currentBalance),
                    currencyFormat.format(newBalance)),
                "Xác nhận thanh toán",
                JOptionPane.YES_NO_OPTION);
            
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        // Update balance on card
        try {
            userData.setBalance(newBalance);
            byte[] newDataBytes = userData.toBytes();
            
            boolean success = apduCommands.updatePatientData(newDataBytes);
            
            if (success) {
                // Refresh local data
                userFrame.refreshUserData();
                updateBalance();
                
                String message = rbCredit.isSelected() ? "Nạp tiền thành công!" : "Thanh toán thành công!";
                JOptionPane.showMessageDialog(this,
                    String.format("%s\n\nSố dư mới: %s",
                        message,
                        currencyFormat.format(newBalance)),
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE);
                
                txtAmount.setText("");
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Giao dịch thất bại! Vui lòng thử lại.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Lỗi khi thực hiện giao dịch: " + e.getMessage(), 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
}

