package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * UserLoginFrame - Màn hình đăng nhập User bằng thẻ User
 * Đơn giản hóa: Chỉ cần nhập PIN và verify với PIN hash trong thẻ
 * Mặc định thẻ đã được connect khi mở form này
 */
public class UserLoginFrame extends JFrame {
    
    private JPasswordField txtPin;
    private JButton btnLogin;
    private JButton btnCancel;
    private CardManager cardManager;
    private APDUCommands apduCommands;

    public UserLoginFrame() {
        initUI();
    }

    private void initUI() {
        setTitle("Đăng nhập User");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(450, 280);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 153, 102));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("ĐĂNG NHẬP USER");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 5, 8, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Hướng dẫn
        JLabel instructionLabel = new JLabel("<html>Thẻ đã được kết nối.<br>Vui lòng nhập mã PIN để đăng nhập.</html>");
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 15, 0);
        mainPanel.add(instructionLabel, gbc);

        // PIN Label
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.insets = new Insets(8, 5, 8, 5);
        JLabel pinLabel = new JLabel("Mã PIN:");
        pinLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        mainPanel.add(pinLabel, gbc);

        // PIN Field
        txtPin = new JPasswordField(20);
        txtPin.setFont(new Font("Arial", Font.PLAIN, 14));
        txtPin.setPreferredSize(new Dimension(250, 30));
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(txtPin, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        
        btnLogin = new JButton("Đăng nhập");
        btnLogin.setPreferredSize(new Dimension(120, 35));
        btnLogin.setFont(new Font("Arial", Font.BOLD, 13));
        btnLogin.setBackground(new Color(0, 153, 102));
        btnLogin.setForeground(Color.WHITE);
        btnPanel.add(btnLogin);
        
        btnCancel = new JButton("Hủy");
        btnCancel.setPreferredSize(new Dimension(100, 35));
        btnCancel.setFont(new Font("Arial", Font.PLAIN, 13));
        btnPanel.add(btnCancel);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(20, 0, 0, 0);
        mainPanel.add(btnPanel, gbc);

        add(mainPanel, BorderLayout.CENTER);

        // Event handlers
        btnLogin.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });

        btnCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        // Kết nối thẻ
        cardManager = CardManager.getInstance();
        if (!cardManager.connect()) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối với đầu đọc thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Select UserApplet
        if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy UserApplet trên thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            cardManager.disconnect();
            return;
        }

        apduCommands = new APDUCommands(cardManager.getChannel());
    }

    private void handleLogin() {
        try {
            String pin = new String(txtPin.getPassword());
            
            if (pin.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập mã PIN!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtPin.requestFocus();
                return;
            }

            // V3: Validate PIN length (must be 6 digits)
            if (pin.length() != 6 || !pin.matches("^[0-9]+$")) {
                JOptionPane.showMessageDialog(this, 
                    "PIN phải là 6 chữ số!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            // 1. Kiểm tra cardId_user từ thẻ (V3: dùng getStatus())
            byte[] cardIdUser = apduCommands.getStatus();
            if (cardIdUser == null || cardIdUser.length != 16) {
                JOptionPane.showMessageDialog(this, 
                    "Không thể đọc cardId từ thẻ!\n\n" +
                    "Có thể thẻ chưa được phát hành.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Kiểm tra cardId có rỗng không (toàn số 0 - thẻ chưa được phát hành)
            boolean cardIdEmpty = true;
            for (int i = 0; i < cardIdUser.length; i++) {
                if (cardIdUser[i] != 0) {
                    cardIdEmpty = false;
                    break;
                }
            }
            if (cardIdEmpty) {
                JOptionPane.showMessageDialog(this, 
                    "Thẻ chưa được phát hành!\n\n" +
                    "CardId hiện tại là rỗng (toàn số 0).\n" +
                    "Vui lòng phát hành thẻ trước khi đăng nhập.", 
                    "Thẻ chưa được phát hành", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 2. V3: Xác thực PIN_user và đọc data cùng lúc
            // Gửi PIN plaintext xuống thẻ, thẻ sẽ verify và trả về patient data nếu đúng
            byte[] pinBytes = pin.getBytes();
            byte[] userDataBytes = apduCommands.verifyPinAndReadData(pinBytes);
            
            if (userDataBytes == null || userDataBytes.length == 0) {
                // PIN sai hoặc thẻ bị khóa
                JOptionPane.showMessageDialog(this, 
                    "Mã PIN không đúng hoặc thẻ bị khóa!\n\n" +
                    "Vui lòng kiểm tra lại PIN và thử lại.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            // 3. Parse userData từ response
            UserData userData = UserData.fromBytes(userDataBytes);
            if (userData == null) {
                JOptionPane.showMessageDialog(this, 
                    "Không thể parse dữ liệu từ thẻ!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 4. Đăng nhập thành công - Lưu PIN và userData để dùng trong session
            System.out.println("[User Login] Xác thực PIN thành công!");
            System.out.println("[User Login] UserData loaded: " + userData.getHoTen());
            
            dispose();
            // Truyền PIN và userData xuống UserFrame
            new UserFrame(cardManager, apduCommands, pin, userData).setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

