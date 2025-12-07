package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.Transaction;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

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

    public HistoryPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }
    
    public HistoryPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        initUI();
        loadHistory();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Lịch sử giao dịch"));

        // Table
        String[] columns = {"Thời gian", "Loại", "Số tiền", "Số dư sau"};
        tableModel = new DefaultTableModel(columns, 0);
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(table);

        btnRefresh = new JButton("Làm mới");
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnRefresh);

        btnRefresh.addActionListener(e -> loadHistory());

        add(scrollPane, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadHistory() {
        try {
            // V3: getLogs() không còn trong V3 spec
            tableModel.setRowCount(0);
            
            // Hiển thị thông báo
            tableModel.addRow(new Object[]{
                "N/A",
                "V3 - Không hỗ trợ",
                "Lịch sử giao dịch không còn được lưu trên thẻ",
                "N/A"
            });
            
            JOptionPane.showMessageDialog(this, 
                "Chức năng lịch sử giao dịch không còn được hỗ trợ trong V3.\n\n" +
                "Lịch sử giao dịch được lưu trong database thay vì trên thẻ.", 
                "Thông báo", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

