package ui.user;

import card.CardManager;
import card.APDUCommands;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * TransactionPanel - Panel nạp tiền và thanh toán
 */
public class TransactionPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame; // V3: Reference to UserFrame
    
    private JTextField txtAmount;
    private JRadioButton rbCredit, rbDebit;
    private JButton btnExecute;
    private JLabel lblBalance;

    public TransactionPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }
    
    public TransactionPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        initUI();
        updateBalance();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Nạp tiền / Thanh toán"));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // Số dư hiện tại
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Số dư hiện tại:"), gbc);
        lblBalance = new JLabel("-");
        lblBalance.setFont(new Font("Arial", Font.BOLD, 16));
        lblBalance.setForeground(new Color(0, 153, 0));
        gbc.gridx = 1;
        formPanel.add(lblBalance, gbc);

        // Loại giao dịch
        gbc.gridx = 0; gbc.gridy = 1;
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
        formPanel.add(radioPanel, gbc);

        // Số tiền
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Số tiền (VNĐ):"), gbc);
        txtAmount = new JTextField(20);
        gbc.gridx = 1;
        formPanel.add(txtAmount, gbc);

        // Button
        btnExecute = new JButton("Thực hiện");
        btnExecute.setFont(new Font("Arial", Font.BOLD, 14));
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnExecute);

        btnExecute.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeTransaction();
            }
        });

        add(formPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void updateBalance() {
        // V3: Balance không còn trong V3 spec
        lblBalance.setText("N/A (V3 - không hỗ trợ balance)");
    }

    private void executeTransaction() {
        // V3: Credit/Debit không còn trong V3 spec
        JOptionPane.showMessageDialog(this, 
            "Chức năng nạp tiền/thanh toán không còn được hỗ trợ trong V3.\n\n" +
            "Vui lòng liên hệ admin để được hỗ trợ.", 
            "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }
}

