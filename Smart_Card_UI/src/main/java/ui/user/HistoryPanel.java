package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.Transaction;
import db.DatabaseConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
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
 */
public class HistoryPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame; // V3: Reference to UserFrame
    
    private JTable table;
    private DefaultTableModel tableModel;
    private JButton btnRefresh;
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
        initUI();
        loadHistory();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Lịch sử giao dịch");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Arial", Font.PLAIN, 12));
        headerPanel.add(lblStatus, BorderLayout.EAST);
        
        // Table
        String[] columns = {"STT", "Thời gian", "Loại", "Số tiền", "Số dư sau", "Hash"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make all cells non-editable
            }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(25);
        table.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Center align for numeric columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(centerRenderer); // STT
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer); // Loại
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer); // Số tiền
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // Số dư sau
        
        // Color renderer for transaction type
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(JLabel.CENTER);
                if (value != null) {
                    String type = value.toString();
                    if ("CREDIT".equals(type)) {
                        c.setForeground(new Color(0, 128, 0)); // Green
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else if ("DEBIT".equals(type)) {
                        c.setForeground(new Color(200, 0, 0)); // Red
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        });
        
        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);  // STT
        table.getColumnModel().getColumn(1).setPreferredWidth(180); // Thời gian
        table.getColumnModel().getColumn(2).setPreferredWidth(100); // Loại
        table.getColumnModel().getColumn(3).setPreferredWidth(150); // Số tiền
        table.getColumnModel().getColumn(4).setPreferredWidth(150); // Số dư sau
        table.getColumnModel().getColumn(5).setPreferredWidth(100); // Hash (shortened)
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Danh sách giao dịch"));

        // Button panel
        btnRefresh = new JButton("🔄 Làm mới");
        btnRefresh.setFont(new Font("Arial", Font.PLAIN, 12));
        btnRefresh.setPreferredSize(new Dimension(120, 35));
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnRefresh);

        btnRefresh.addActionListener(e -> loadHistory());

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(scrollPane, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadHistory() {
        try {
            tableModel.setRowCount(0);
            lblStatus.setText("Đang tải...");
            lblStatus.setForeground(Color.GRAY);
            
            // Get card ID
            byte[] cardId = apduCommands.getCardId();
            if (cardId == null || cardId.length != 16) {
                lblStatus.setText("❌ Không thể đọc cardId từ thẻ!");
                lblStatus.setForeground(Color.RED);
                JOptionPane.showMessageDialog(this, 
                    "Không thể đọc cardId từ thẻ!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Get transactions from database
            List<Transaction> transactions = DatabaseConnection.getTransactions(cardId);
            
            // Sort by sequence number (ascending) for hash chain verification
            // Hash chain must be calculated in order: seq 1 -> 2 -> 3 -> ...
            transactions.sort(Comparator.comparing(Transaction::getSeq));
            
            if (transactions.isEmpty()) {
                tableModel.addRow(new Object[]{
                    "-", "-", "Không có giao dịch", "-", "-", "-"
                });
                lblStatus.setText("ℹ️ Không có giao dịch nào");
                lblStatus.setForeground(Color.GRAY);
                return;
            }
            
            // Get transaction status from card
            APDUCommands.TransactionStatus cardStatus = apduCommands.getTxnStatus();
            short txnCounterFromCard = cardStatus != null ? cardStatus.txnCounter : 0;
            byte[] lastTxnHashFromCard = cardStatus != null ? cardStatus.lastTxnHash : null;
            
            // Verify hash chain (must be in seq order)
            boolean hashChainValid = verifyHashChain(transactions, lastTxnHashFromCard);
            
            // Sort by time (newest first) for display, but keep original list for verification
            List<Transaction> displayTransactions = new ArrayList<>(transactions);
            displayTransactions.sort(Comparator.comparing(Transaction::getThoiGian).reversed());
            
            // Update status label
            if (hashChainValid && lastTxnHashFromCard != null) {
                lblStatus.setText("✅ " + transactions.size() + " giao dịch - Hash chain hợp lệ (seq: " + txnCounterFromCard + ")");
                lblStatus.setForeground(new Color(0, 128, 0));
            } else if (lastTxnHashFromCard != null) {
                lblStatus.setText("⚠️ " + transactions.size() + " giao dịch - Hash chain KHÔNG khớp!");
                lblStatus.setForeground(Color.RED);
                JOptionPane.showMessageDialog(this, 
                    "⚠️ CẢNH BÁO: Hash chain không khớp!\n\n" +
                    "Có thể dữ liệu giao dịch trong database đã bị sửa đổi.\n" +
                    "Vui lòng kiểm tra lại.", 
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            } else {
                lblStatus.setText("ℹ️ " + transactions.size() + " giao dịch - Không thể verify hash chain");
                lblStatus.setForeground(Color.ORANGE);
            }
            
            // Display transactions (newest first)
            int stt = 1;
            for (Transaction txn : displayTransactions) {
                String hashStr = "N/A";
                if (txn.getTxnHash() != null && txn.getTxnHash().length > 0) {
                    // Show first 8 bytes of hash in hex
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(8, txn.getTxnHash().length); i++) {
                        sb.append(String.format("%02X", txn.getTxnHash()[i]));
                    }
                    hashStr = sb.toString() + "...";
                }
                
                tableModel.addRow(new Object[]{
                    stt++,
                    dateFormat.format(txn.getThoiGian()),
                    txn.getLoai(),
                    currencyFormat.format(txn.getSoTien()),
                    currencyFormat.format(txn.getSoDuSau()),
                    hashStr
                });
            }

        } catch (javax.smartcardio.CardException e) {
            lblStatus.setText("❌ Lỗi đọc thẻ: " + e.getMessage());
            lblStatus.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, 
                "Lỗi đọc thẻ: " + e.getMessage(), 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } catch (Exception e) {
            lblStatus.setText("❌ Lỗi: " + e.getMessage());
            lblStatus.setForeground(Color.RED);
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Verify hash chain integrity
     * @param transactions List of transactions from database
     * @param lastTxnHashFromCard Last transaction hash from card
     * @return true if hash chain is valid
     */
    private boolean verifyHashChain(List<Transaction> transactions, byte[] lastTxnHashFromCard) {
        if (transactions.isEmpty()) {
            return true; // No transactions to verify
        }
        
        if (lastTxnHashFromCard == null || lastTxnHashFromCard.length != 20) {
            System.err.println("[HistoryPanel] Cannot verify: lastTxnHashFromCard is null or invalid length");
            return false; // Cannot verify without card hash
        }
        
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] prevHash = new byte[20]; // Start with all zeros (initial hash)
            
            // Verify transactions are in seq order (should be already sorted)
            short expectedSeq = 1;
            for (Transaction txn : transactions) {
                if (txn.getSeq() != expectedSeq) {
                    System.err.println("[HistoryPanel] Warning: Transaction seq mismatch. Expected: " + expectedSeq + ", Got: " + txn.getSeq());
                    // Continue anyway - maybe seq is not sequential
                }
                expectedSeq++;
                
                // Build hash input: prev_hash || seq || type || amount || balance_after
                // Total: 20 + 2 + 1 + 4 + 4 = 31 bytes
                byte[] hashInput = new byte[31];
                int offset = 0;
                
                // prev_hash (20 bytes) - from previous transaction or zeros for first
                System.arraycopy(prevHash, 0, hashInput, offset, 20);
                offset += 20;
                
                // seq (2 bytes, big-endian)
                short seq = txn.getSeq();
                hashInput[offset++] = (byte)(seq >> 8);
                hashInput[offset++] = (byte)(seq & 0xFF);
                
                // type (1 byte: 0x01 = CREDIT, 0x02 = DEBIT)
                hashInput[offset++] = "CREDIT".equals(txn.getLoai()) ? (byte)0x01 : (byte)0x02;
                
                // amount (4 bytes, big-endian)
                int amount = txn.getSoTien();
                hashInput[offset++] = (byte)(amount >> 24);
                hashInput[offset++] = (byte)(amount >> 16);
                hashInput[offset++] = (byte)(amount >> 8);
                hashInput[offset++] = (byte)(amount & 0xFF);
                
                // balance_after (4 bytes, big-endian)
                int balanceAfter = txn.getSoDuSau();
                hashInput[offset++] = (byte)(balanceAfter >> 24);
                hashInput[offset++] = (byte)(balanceAfter >> 16);
                hashInput[offset++] = (byte)(balanceAfter >> 8);
                hashInput[offset++] = (byte)(balanceAfter & 0xFF);
                
                // Calculate SHA-1 hash
                sha1.reset();
                prevHash = sha1.digest(hashInput);
            }
            
            // Compare final hash with card's last_txn_hash
            boolean isValid = java.util.Arrays.equals(prevHash, lastTxnHashFromCard);
            
            if (!isValid) {
                System.err.println("[HistoryPanel] Hash mismatch!");
                System.err.println("  Calculated hash: " + bytesToHex(prevHash));
                System.err.println("  Card hash:       " + bytesToHex(lastTxnHashFromCard));
            }
            
            return isValid;
            
        } catch (Exception e) {
            System.err.println("[HistoryPanel] Error verifying hash chain: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}

