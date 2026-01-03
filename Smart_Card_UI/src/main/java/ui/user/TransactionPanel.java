package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import model.Transaction;
import db.DatabaseConnection;
import ui.ModernUITheme;
import ui.SmartCardVisual;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Date;
import java.nio.charset.StandardCharsets;

/**
 * TransactionPanel - Panel nạp tiền và thanh toán
 * V3: Premium UI với visual balance display và modern form design
 */
public class TransactionPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;

    private ModernUITheme.RoundedTextField txtAmount;
    private JRadioButton rbCredit, rbDebit;
    private ModernUITheme.RoundedButton btnExecute;
    private JLabel lblBalance;
    private JPanel balanceCard;
    private NumberFormat currencyFormat;

    public TransactionPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }

    public TransactionPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

        setOpaque(false);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
        updateBalance();
    }

    private void initUI() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ===== TOP SECTION: Balance Display =====
        JPanel topSection = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        topSection.setOpaque(false);

        // Balance card with gradient
        balanceCard = createBalanceCard();
        topSection.add(balanceCard);

        // Quick action cards
        topSection.add(createQuickActionCard("💰", "Nạp tiền nhanh", "100.000đ", () -> {
            txtAmount.setText("100000");
            rbCredit.setSelected(true);
        }));

        topSection.add(createQuickActionCard("💳", "Thanh toán nhanh", "50.000đ", () -> {
            txtAmount.setText("50000");
            rbDebit.setSelected(true);
        }));

        add(topSection, BorderLayout.NORTH);

        // ===== CENTER: Transaction Form =====
        ModernUITheme.CardPanel formCard = new ModernUITheme.CardPanel();
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));

        // Title
        JLabel titleLabel = new JLabel("📝 THỰC HIỆN GIAO DỊCH");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(titleLabel);
        formCard.add(Box.createVerticalStrut(25));

        // Transaction type selection with styled radio buttons
        JLabel lblType = new JLabel("Loại giao dịch");
        lblType.setFont(ModernUITheme.FONT_SUBHEADING);
        lblType.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblType.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(lblType);
        formCard.add(Box.createVerticalStrut(10));

        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        typePanel.setOpaque(false);
        typePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        typePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        rbCredit = createStyledRadioButton("💵 Nạp tiền", true, ModernUITheme.USER_PRIMARY);
        rbDebit = createStyledRadioButton("💳 Thanh toán", false, ModernUITheme.WARNING);

        ButtonGroup group = new ButtonGroup();
        group.add(rbCredit);
        group.add(rbDebit);

        typePanel.add(rbCredit);
        typePanel.add(rbDebit);
        formCard.add(typePanel);
        formCard.add(Box.createVerticalStrut(20));

        // Amount input
        JLabel lblAmount = new JLabel("Số tiền (VNĐ)");
        lblAmount.setFont(ModernUITheme.FONT_SUBHEADING);
        lblAmount.setForeground(ModernUITheme.TEXT_PRIMARY);
        lblAmount.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(lblAmount);
        formCard.add(Box.createVerticalStrut(8));

        txtAmount = new ModernUITheme.RoundedTextField(20);
        txtAmount.setMaximumSize(new Dimension(300, 48));
        txtAmount.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtAmount.setFont(new Font("Segoe UI", Font.BOLD, 18));
        formCard.add(txtAmount);
        formCard.add(Box.createVerticalStrut(10));

        // Quick amount buttons
        JPanel quickAmounts = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickAmounts.setOpaque(false);
        quickAmounts.setAlignmentX(Component.LEFT_ALIGNMENT);

        String[] amounts = { "50.000", "100.000", "200.000", "500.000" };
        for (String amt : amounts) {
            JButton quickBtn = createQuickAmountButton(amt);
            quickAmounts.add(quickBtn);
        }
        formCard.add(quickAmounts);
        formCard.add(Box.createVerticalStrut(20));

        // Note
        JLabel lblNote = new JLabel("<html><i>🔒 Giao dịch được bảo mật và mã hóa trên chip thẻ</i></html>");
        lblNote.setFont(ModernUITheme.FONT_SMALL);
        lblNote.setForeground(ModernUITheme.TEXT_MUTED);
        lblNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(lblNote);
        formCard.add(Box.createVerticalStrut(25));

        // Execute button
        btnExecute = new ModernUITheme.RoundedButton(
                "✓ Thực hiện giao dịch",
                ModernUITheme.USER_PRIMARY,
                ModernUITheme.USER_PRIMARY_HOVER,
                ModernUITheme.TEXT_WHITE);
        btnExecute.setPreferredSize(new Dimension(220, 50));
        btnExecute.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnExecute.addActionListener(e -> executeTransaction());
        formCard.add(btnExecute);

        // Wrap in a panel to center
        JPanel formWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        formWrapper.setOpaque(false);
        formWrapper.add(formCard);

        add(formWrapper, BorderLayout.CENTER);
    }

    private JPanel createBalanceCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Gradient background
                GradientPaint gp = new GradientPaint(
                        0, 0, ModernUITheme.USER_PRIMARY,
                        getWidth(), getHeight(), ModernUITheme.USER_GRADIENT_END);
                g2.setPaint(gp);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));

                // Decorative circle
                g2.setColor(new Color(255, 255, 255, 20));
                g2.fillOval(getWidth() - 80, -30, 120, 120);

                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(220, 130));
        card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        card.setOpaque(false);

        JLabel titleLbl = new JLabel("💰 SỐ DƯ HIỆN TẠI");
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        titleLbl.setForeground(new Color(255, 255, 255, 200));
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLbl);
        card.add(Box.createVerticalStrut(10));

        lblBalance = new JLabel("...");
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblBalance.setForeground(Color.WHITE);
        lblBalance.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(lblBalance);
        card.add(Box.createVerticalStrut(15));

        JLabel refreshLbl = new JLabel("🔄 Click để làm mới");
        refreshLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        refreshLbl.setForeground(new Color(255, 255, 255, 150));
        refreshLbl.setCursor(new Cursor(Cursor.HAND_CURSOR));
        refreshLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshLbl.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                updateBalance();
            }
        });
        card.add(refreshLbl);

        return card;
    }

    private JPanel createQuickActionCard(String emoji, String title, String value, Runnable action) {
        JPanel card = new JPanel() {
            private boolean isHovered = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        isHovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        isHovered = false;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        action.run();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(isHovered ? ModernUITheme.USER_PRIMARY_LIGHT : ModernUITheme.BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));

                // Border
                g2.setColor(isHovered ? ModernUITheme.USER_PRIMARY : ModernUITheme.BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));

                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(140, 130));
        card.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        card.setOpaque(false);
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel emojiLbl = new JLabel(emoji);
        emojiLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        emojiLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(emojiLbl);
        card.add(Box.createVerticalStrut(8));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(ModernUITheme.FONT_SMALL);
        titleLbl.setForeground(ModernUITheme.TEXT_SECONDARY);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLbl);

        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(ModernUITheme.FONT_SUBHEADING);
        valueLbl.setForeground(ModernUITheme.TEXT_PRIMARY);
        valueLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(valueLbl);

        return card;
    }

    private JRadioButton createStyledRadioButton(String text, boolean selected, Color accentColor) {
        JRadioButton rb = new JRadioButton(text, selected);
        rb.setFont(ModernUITheme.FONT_BODY);
        rb.setForeground(ModernUITheme.TEXT_PRIMARY);
        rb.setOpaque(false);
        rb.setFocusPainted(false);
        rb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return rb;
    }

    private JButton createQuickAmountButton(String amount) {
        JButton btn = new JButton(amount) {
            private boolean isHovered = false;
            {
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        isHovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        isHovered = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(isHovered ? ModernUITheme.USER_PRIMARY_LIGHT : ModernUITheme.BG_SECONDARY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));

                g2.setColor(isHovered ? ModernUITheme.USER_PRIMARY : ModernUITheme.TEXT_SECONDARY);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(getText(), x, y);

                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(85, 32));
        btn.setFont(ModernUITheme.FONT_SMALL);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            String value = amount.replace(".", "");
            txtAmount.setText(value);
        });
        return btn;
    }

    public void updateBalance() {
        if (userFrame == null) {
            lblBalance.setText("N/A");
            return;
        }

        UserData userData = userFrame.getUserData();
        if (userData != null) {
            lblBalance.setText(currencyFormat.format(userData.getBalance()));
        } else {
            lblBalance.setText("---");
        }
    }

    private void executeTransaction() {
        if (userFrame == null || userFrame.getUserPin() == null) {
            showError("Không có thông tin xác thực. Vui lòng đăng nhập lại.");
            return;
        }

        String amountStr = txtAmount.getText().trim();
        if (amountStr.isEmpty()) {
            showError("Vui lòng nhập số tiền!");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(amountStr);
            if (amount <= 0) {
                showError("Số tiền phải lớn hơn 0!");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Số tiền không hợp lệ!");
            return;
        }

        UserData userData = userFrame.getUserData();
        if (userData == null) {
            showError("Không thể đọc thông tin thẻ!");
            return;
        }

        long currentBalance = userData.getBalance();

        // V7: Lấy mức hưởng BHYT từ UserData
        int coverageRate = 60; // Default
        if (userData != null) {
            coverageRate = userData.getBhytCoverageRate();
            if (coverageRate <= 0)
                coverageRate = 60; // Fallback
        }

        // Tính toán số tiền thực tế
        long totalCost = amount; // Tổng chi phí
        long insurancePays = 0; // BHYT chi trả
        long userPays = amount; // Người dùng trả

        if (!rbCredit.isSelected()) {
            // Chỉ áp dụng BHYT cho debit (thanh toán)
            insurancePays = (amount * coverageRate) / 100;
            userPays = amount - insurancePays;

            // Kiểm tra số dư theo số tiền người dùng cần trả
            if (userPays > currentBalance) {
                showError(String.format(
                        "Số dư không đủ!\n\n" +
                                "Tổng chi phí: %s\n" +
                                "BHYT chi trả (%d%%): %s\n" +
                                "Bạn cần thanh toán: %s\n" +
                                "Số dư hiện tại: %s",
                        currencyFormat.format(totalCost),
                        coverageRate,
                        currencyFormat.format(insurancePays),
                        currencyFormat.format(userPays),
                        currencyFormat.format(currentBalance)));
                return;
            }
        }

        // --- Xác thực PIN ---
        String actionType = rbCredit.isSelected() ? "Nạp tiền" : "Thanh toán";
        String dialogTitle = "Xác thực " + actionType;
        String dialogMsg;

        if (rbCredit.isSelected()) {
            dialogMsg = String.format(
                    "<html>Thực hiện %s: <b>%s</b><br>Vui lòng nhập PIN để xác nhận:</html>",
                    actionType.toLowerCase(),
                    currencyFormat.format(amount));
        } else {
            dialogMsg = String.format(
                    "<html>Thực hiện thanh toán:<br>" +
                            "<b>Tổng chi phí: %s</b><br>" +
                            "BHYT chi trả (%d%%): %s<br>" +
                            "<b style='color: #2196F3;'>Bạn thanh toán: %s</b><br><br>" +
                            "Vui lòng nhập PIN để xác nhận:</html>",
                    currencyFormat.format(totalCost),
                    coverageRate,
                    currencyFormat.format(insurancePays),
                    currencyFormat.format(userPays));
        }

        String pin = showPinDialog(dialogTitle, dialogMsg);
        if (pin == null) {
            return; // User cancelled
        }
        if (pin.isEmpty()) {
            showError("Vui lòng nhập mã PIN!");
            return;
        }

        // Verify PIN with card
        try {
            byte[] verifyData = apduCommands.verifyPinAndReadData(pin.getBytes(StandardCharsets.UTF_8));
            if (verifyData == null) {
                showError("Mã PIN không đúng! Giao dịch bị hủy.");
                return;
            }
        } catch (Exception e) {
            showError("Lỗi xác thực PIN: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        try {
            APDUCommands.TransactionResult result;

            if (rbCredit.isSelected()) {
                result = apduCommands.creditTransaction((int) amount);
            } else {
                // V7: Debit với số tiền sau khi áp dụng BHYT
                result = apduCommands.debitTransaction((int) userPays);
            }

            if (result != null) {
                Transaction txn = new Transaction();
                txn.setThoiGian(new Date());
                txn.setLoai(rbCredit.isSelected() ? "CREDIT" : "DEBIT");

                // V7: Lưu số tiền thực tế đã trừ/nạp (quan trọng cho hashchain validation)
                // Credit: lưu full amount
                // Debit: lưu userPays (số tiền thực tế trừ sau khi áp dụng BHYT)
                txn.setSoTien(rbCredit.isSelected() ? (int) amount : (int) userPays);

                txn.setSoDuSau(result.balanceAfter);
                txn.setSeq(result.seq);
                txn.setTxnHash(result.currHash);

                byte[] cardId = apduCommands.getCardId();
                if (cardId != null && cardId.length == 16) {
                    DatabaseConnection.saveTransaction(cardId, txn);
                }

                if (userData != null) {
                    userData.setBalance(result.balanceAfter);
                }

                updateBalance();

                // V7: Success message với breakdown cho debit
                if (rbCredit.isSelected()) {
                    showSuccess(String.format(
                            "✓ Nạp tiền thành công!\n\nSố dư mới: %s",
                            currencyFormat.format(result.balanceAfter)));
                } else {
                    showSuccess(String.format(
                            "✓ Thanh toán thành công!\n\n" +
                                    "Tổng chi phí: %s\n" +
                                    "BHYT đã chi trả: %s\n" +
                                    "Bạn đã thanh toán: %s\n\n" +
                                    "Số dư mới: %s",
                            currencyFormat.format(totalCost),
                            currencyFormat.format(insurancePays),
                            currencyFormat.format(userPays),
                            currencyFormat.format(result.balanceAfter)));
                }

                txtAmount.setText("");
            } else {
                showError("Giao dịch thất bại! Vui lòng thử lại.");
            }
        } catch (javax.smartcardio.CardException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("0x6982")) {
                showError("Lỗi: PIN chưa được xác thực. Vui lòng đăng nhập lại.");
            } else if (errorMsg != null && errorMsg.contains("0x6A80")) {
                showError("Lỗi: " + errorMsg);
            } else {
                showError("Lỗi khi thực hiện giao dịch: " + errorMsg);
            }
            e.printStackTrace();
        } catch (Exception e) {
            showError("Lỗi khi thực hiện giao dịch: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "Thành công", JOptionPane.INFORMATION_MESSAGE);
    }

    private String showPinDialog(String title, String message) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        JLabel label = new JLabel(message);
        label.setFont(ModernUITheme.FONT_BODY);
        panel.add(label, BorderLayout.NORTH);

        JPasswordField pass = new JPasswordField(10);
        pass.setFont(new Font("Segoe UI", Font.BOLD, 14));
        panel.add(pass, BorderLayout.CENTER);

        // Auto focus password field
        pass.addAncestorListener(new javax.swing.event.AncestorListener() {
            public void ancestorAdded(javax.swing.event.AncestorEvent event) {
                pass.requestFocusInWindow();
            }

            public void ancestorRemoved(javax.swing.event.AncestorEvent event) {
            }

            public void ancestorMoved(javax.swing.event.AncestorEvent event) {
            }
        });

        int result = JOptionPane.showConfirmDialog(this, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return new String(pass.getPassword());
        }
        return null;
    }
}
