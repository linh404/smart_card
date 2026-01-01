package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.Transaction;
import db.DatabaseConnection;
import ui.ModernUITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.security.MessageDigest;

/**
 * HistoryPanel - Panel hiển thị lịch sử giao dịch
 * V3: Modern UI update
 */
public class HistoryPanel extends JPanel {

    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame;

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblStatus;
    private NumberFormat currencyFormat;
    private SimpleDateFormat dateFormat;

    public HistoryPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }

    public HistoryPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        setOpaque(false);
        setBackground(ModernUITheme.BG_PRIMARY);

        initUI();
        loadHistory();
    }

    private void initUI() {
        setLayout(new BorderLayout(20, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("📜 LỊCH SỬ GIAO DỊCH");
        titleLabel.setFont(ModernUITheme.FONT_HEADING);
        titleLabel.setForeground(ModernUITheme.TEXT_PRIMARY);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(ModernUITheme.FONT_SMALL);
        lblStatus.setForeground(ModernUITheme.TEXT_SECONDARY);
        headerPanel.add(lblStatus, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Table Config
        String[] columns = { "STT", "Thời gian", "Loại", "Số tiền", "Số dư sau", "Hash" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(40); // Taller rows
        table.setFont(ModernUITheme.FONT_BODY);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(ModernUITheme.USER_PRIMARY_LIGHT);
        table.setSelectionForeground(ModernUITheme.TEXT_PRIMARY);

        // Header Style
        JTableHeader header = table.getTableHeader();
        header.setFont(ModernUITheme.FONT_SUBHEADING);
        header.setBackground(ModernUITheme.BG_SECONDARY);
        header.setForeground(ModernUITheme.TEXT_SECONDARY);
        header.setPreferredSize(new Dimension(0, 40));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

        // Center align for numeric columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        // Color renderer for transaction type
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                        column);
                c.setHorizontalAlignment(JLabel.CENTER);
                if (value != null) {
                    String type = value.toString();
                    if ("CREDIT".equals(type)) {
                        c.setForeground(ModernUITheme.SUCCESS);
                        c.setText("NẠP TIỀN");
                    } else if ("DEBIT".equals(type)) {
                        c.setForeground(ModernUITheme.ERROR);
                        c.setText("THANH TOÁN");
                    }
                }
                if (isSelected)
                    c.setBackground(table.getSelectionBackground());
                return c;
            }
        });

        // Custom scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(ModernUITheme.BORDER_LIGHT));
        scrollPane.getViewport().setBackground(Color.WHITE);

        // ModernUITheme.CardPanel wrapper for table
        ModernUITheme.CardPanel tableCard = new ModernUITheme.CardPanel(new BorderLayout());
        tableCard.add(scrollPane, BorderLayout.CENTER);

        add(tableCard, BorderLayout.CENTER);

        add(tableCard, BorderLayout.CENTER);
    }

    public void loadHistory() {
        try {
            tableModel.setRowCount(0);
            lblStatus.setText("Running...");

            // Get card ID
            byte[] cardId = apduCommands.getCardId();
            if (cardId == null || cardId.length != 16) {
                lblStatus.setText("❌ Không thể đọc cardId");
                lblStatus.setForeground(ModernUITheme.ERROR);
                return;
            }

            List<Transaction> transactions = DatabaseConnection.getTransactions(cardId);
            transactions.sort(Comparator.comparing(Transaction::getSeq));

            if (transactions.isEmpty()) {
                tableModel.addRow(new Object[] {
                        "-", "-", "Không có giao dịch", "-", "-", "-"
                });
                lblStatus.setText("ℹ️ Không có giao dịch nào");
                return;
            }

            APDUCommands.TransactionStatus cardStatus = apduCommands.getTxnStatus();
            short txnCounterFromCard = cardStatus != null ? cardStatus.txnCounter : 0;
            byte[] lastTxnHashFromCard = cardStatus != null ? cardStatus.lastTxnHash : null;

            boolean hashChainValid = verifyHashChain(transactions, lastTxnHashFromCard);

            List<Transaction> displayTransactions = new ArrayList<>(transactions);
            displayTransactions.sort(Comparator.comparing(Transaction::getThoiGian).reversed());

            if (hashChainValid && lastTxnHashFromCard != null) {
                lblStatus.setText("✅ " + transactions.size() + " giao dịch - Hash chain hợp lệ (seq: "
                        + txnCounterFromCard + ")");
                lblStatus.setForeground(ModernUITheme.SUCCESS);
            } else if (lastTxnHashFromCard != null) {
                lblStatus.setText("⚠️ " + transactions.size() + " giao dịch - Hash chain KHÔNG khớp!");
                lblStatus.setForeground(ModernUITheme.ERROR);
                JOptionPane.showMessageDialog(this,
                        "⚠️ CẢNH BÁO: Hash chain không khớp!\nDữ liệu có thể đã bị sửa đổi.",
                        "Cảnh báo bảo mật", JOptionPane.WARNING_MESSAGE);
            } else {
                lblStatus.setText("ℹ️ " + transactions.size() + " giao dịch - Không thể verify");
                lblStatus.setForeground(ModernUITheme.WARNING);
            }

            int stt = 1;
            for (Transaction txn : displayTransactions) {
                String hashStr = "N/A";
                if (txn.getTxnHash() != null && txn.getTxnHash().length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(8, txn.getTxnHash().length); i++) {
                        sb.append(String.format("%02X", txn.getTxnHash()[i]));
                    }
                    hashStr = sb.toString() + "...";
                }

                tableModel.addRow(new Object[] {
                        stt++,
                        dateFormat.format(txn.getThoiGian()),
                        txn.getLoai(),
                        currencyFormat.format(txn.getSoTien()),
                        currencyFormat.format(txn.getSoDuSau()),
                        hashStr
                });
            }

        } catch (Exception e) {
            lblStatus.setText("❌ Lỗi: " + e.getMessage());
            lblStatus.setForeground(ModernUITheme.ERROR);
            e.printStackTrace();
        }
    }

    private boolean verifyHashChain(List<Transaction> transactions, byte[] lastTxnHashFromCard) {
        if (transactions.isEmpty())
            return true;
        if (lastTxnHashFromCard == null || lastTxnHashFromCard.length != 20)
            return false;

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] prevHash = new byte[20];

            for (Transaction txn : transactions) {
                byte[] hashInput = new byte[31];
                int offset = 0;

                System.arraycopy(prevHash, 0, hashInput, offset, 20);
                offset += 20;

                short seq = txn.getSeq();
                hashInput[offset++] = (byte) (seq >> 8);
                hashInput[offset++] = (byte) (seq & 0xFF);

                hashInput[offset++] = "CREDIT".equals(txn.getLoai()) ? (byte) 0x01 : (byte) 0x02;

                int amount = txn.getSoTien();
                hashInput[offset++] = (byte) (amount >> 24);
                hashInput[offset++] = (byte) (amount >> 16);
                hashInput[offset++] = (byte) (amount >> 8);
                hashInput[offset++] = (byte) (amount & 0xFF);

                int balanceAfter = txn.getSoDuSau();
                hashInput[offset++] = (byte) (balanceAfter >> 24);
                hashInput[offset++] = (byte) (balanceAfter >> 16);
                hashInput[offset++] = (byte) (balanceAfter >> 8);
                hashInput[offset++] = (byte) (balanceAfter & 0xFF);

                sha1.reset();
                prevHash = sha1.digest(hashInput);
            }

            return java.util.Arrays.equals(prevHash, lastTxnHashFromCard);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
