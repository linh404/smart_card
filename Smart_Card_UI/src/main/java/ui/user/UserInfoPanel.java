package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;

import javax.swing.*;
import java.awt.*;

/**
 * UserInfoPanel - Panel hiển thị thông tin thẻ User
 */
public class UserInfoPanel extends JPanel {
    
    private CardManager cardManager;
    private APDUCommands apduCommands;
    private UserFrame userFrame; // V3: Reference to UserFrame để lấy PIN và userData
    
    private JLabel lblHoTen, lblIdBenhNhan, lblNgaySinh, lblQueQuan, lblMaBHYT, lblBalance;
    private JButton btnRefresh;

    public UserInfoPanel(CardManager cardManager, APDUCommands apduCommands) {
        this(cardManager, apduCommands, null);
    }
    
    public UserInfoPanel(CardManager cardManager, APDUCommands apduCommands, UserFrame userFrame) {
        this.cardManager = cardManager;
        this.apduCommands = apduCommands;
        this.userFrame = userFrame;
        initUI();
        loadInfo();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Thông tin thẻ"));

        JPanel infoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Họ tên:"), gbc);
        lblHoTen = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblHoTen, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("ID bệnh nhân:"), gbc);
        lblIdBenhNhan = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblIdBenhNhan, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Ngày sinh:"), gbc);
        lblNgaySinh = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblNgaySinh, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Quê quán:"), gbc);
        lblQueQuan = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblQueQuan, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Mã BHYT:"), gbc);
        lblMaBHYT = new JLabel("-");
        gbc.gridx = 1;
        infoPanel.add(lblMaBHYT, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row;
        infoPanel.add(new JLabel("Số dư:"), gbc);
        lblBalance = new JLabel("-");
        lblBalance.setFont(new Font("Arial", Font.BOLD, 16));
        lblBalance.setForeground(new Color(0, 153, 0));
        gbc.gridx = 1;
        infoPanel.add(lblBalance, gbc);

        btnRefresh = new JButton("Làm mới");
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnPanel.add(btnRefresh);

        btnRefresh.addActionListener(e -> loadInfo());

        add(infoPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    private void loadInfo() {
        try {
            // V3: Lấy userData từ UserFrame (đã được load khi login)
            UserData userData = null;
            if (userFrame != null) {
                userData = userFrame.getUserData();
            }
            
            // Nếu không có, thử refresh từ thẻ (cần PIN)
            if (userData == null && userFrame != null && userFrame.getUserPin() != null) {
                if (userFrame.refreshUserData()) {
                    userData = userFrame.getUserData();
                }
            }
            
            // Nếu vẫn không có, hiển thị lỗi
            if (userData == null) {
                lblHoTen.setText("-");
                lblIdBenhNhan.setText("-");
                lblNgaySinh.setText("-");
                lblQueQuan.setText("-");
                lblMaBHYT.setText("-");
                lblBalance.setText("-");
                
                JOptionPane.showMessageDialog(this, 
                    "Không thể đọc dữ liệu từ thẻ!\n\n" +
                    "Vui lòng đăng nhập lại.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Hiển thị thông tin
            lblHoTen.setText(userData.getHoTen() != null ? userData.getHoTen() : "-");
            lblIdBenhNhan.setText(userData.getIdBenhNhan() != null ? userData.getIdBenhNhan() : "-");
            lblNgaySinh.setText(userData.getNgaySinh() != null ? userData.getNgaySinh() : "-");
            lblQueQuan.setText(userData.getQueQuan() != null ? userData.getQueQuan() : "-");
            lblMaBHYT.setText(userData.getMaBHYT() != null ? userData.getMaBHYT() : "-");
            
            // V3: Balance không còn trong V3 spec, hiển thị N/A
            lblBalance.setText("N/A (V3 - không hỗ trợ balance)");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

