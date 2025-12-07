package ui.user;

import card.CardManager;
import card.APDUCommands;
import db.BHYTInfo;
import db.DatabaseConnection;
import model.UserData;

import javax.swing.*;
import java.awt.*;

/**
 * BHYTPanel - Panel hiển thị thông tin BHYT
 */
public class BHYTPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame; // V3: Reference to UserFrame
    
    private JLabel lblMaBHYT, lblHoTen, lblNgaySinh, lblSoThe, lblNgayHetHan, lblMucHuong;
    private JButton btnLoad;

    public BHYTPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }
    
    public BHYTPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Thông tin BHYT"));

        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Mã BHYT:"), gbc);
        lblMaBHYT = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblMaBHYT, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Họ tên:"), gbc);
        lblHoTen = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblHoTen, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Ngày sinh:"), gbc);
        lblNgaySinh = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblNgaySinh, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Số thẻ:"), gbc);
        lblSoThe = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblSoThe, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Ngày hết hạn:"), gbc);
        lblNgayHetHan = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblNgayHetHan, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Mức hưởng:"), gbc);
        lblMucHuong = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblMucHuong, gbc);

        btnLoad = new JButton("Tải thông tin BHYT");
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnLoad);

        btnLoad.addActionListener(e -> loadBHYTInfo());

        add(infoPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadBHYTInfo() {
        try {
            // V3: Lấy mã BHYT từ userData (đã load khi login)
            UserData userData = null;
            if (userFrame != null) {
                userData = userFrame.getUserData();
            }
            
            if (userData == null) {
                JOptionPane.showMessageDialog(this, 
                    "Không thể đọc dữ liệu từ thẻ!\n\n" +
                    "Vui lòng đăng nhập lại.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            String maBHYT = userData.getMaBHYT();
            if (maBHYT == null || maBHYT.isEmpty()) {
                lblMaBHYT.setText("-");
                JOptionPane.showMessageDialog(this, "Mã BHYT không có trong thẻ!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            lblMaBHYT.setText(maBHYT);

            // 2. Truy vấn Supabase để lấy thông tin BHYT đầy đủ
            BHYTInfo info = DatabaseConnection.getBHYTInfo(maBHYT);
            if (info != null) {
                lblHoTen.setText(info.getHoTen() != null ? info.getHoTen() : "-");
                lblNgaySinh.setText(info.getNgaySinh() != null ? info.getNgaySinh().toString() : "-");
                lblSoThe.setText(info.getSoThe() != null ? info.getSoThe() : "-");
                lblNgayHetHan.setText(info.getNgayHetHan() != null ? info.getNgayHetHan().toString() : "-");
                lblMucHuong.setText(info.getMucHuong() != null ? info.getMucHuong() : "-");
            } else {
                // Hiển thị thông tin từ userData nếu không có trong database
                lblHoTen.setText(userData.getHoTen() != null ? userData.getHoTen() : "-");
                lblNgaySinh.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "-");
                lblSoThe.setText("-");
                lblNgayHetHan.setText("-");
                lblMucHuong.setText("-");
                JOptionPane.showMessageDialog(this, "Không tìm thấy thông tin BHYT đầy đủ trong database!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

