package ui.user;

import card.CardManager;
import card.APDUCommands;
import model.UserData;
import util.CryptoUtils;
import db.DatabaseConnection;

import javax.swing.*;
import javax.smartcardio.CardException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.StandardCharsets;

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
        
        // QUAN TRỌNG: Reconnect để đảm bảo channel hợp lệ sau khi admin thao tác
        // Disconnect trước nếu đã kết nối (để refresh connection)
        if (cardManager.isConnected()) {
            cardManager.disconnect();
            try {
                Thread.sleep(100); // Đợi một chút để thẻ ổn định
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        
        if (!cardManager.connect()) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối với đầu đọc thẻ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Select UserApplet (với retry tự động nếu thẻ bị reset)
        if (!cardManager.selectApplet(APDUCommands.AID_USER, true)) {
            JOptionPane.showMessageDialog(this, 
                "Không tìm thấy UserApplet trên thẻ!\n\n" +
                "Có thể thẻ đã bị reset hoặc applet chưa được cài đặt.", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
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
            // Sử dụng UTF-8 để đảm bảo encoding nhất quán với changePin
            byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
            
            // Đảm bảo PIN bytes đúng 6 bytes
            if (pinBytes.length != 6) {
                JOptionPane.showMessageDialog(this, 
                    "Lỗi: PIN không đúng định dạng (phải là 6 bytes)!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }
            
            byte[] userDataBytes = apduCommands.verifyPinAndReadData(pinBytes);
            
            if (userDataBytes == null || userDataBytes.length == 0) {
                // ❌ SAI: PIN sai hoặc thẻ bị khóa
                JOptionPane.showMessageDialog(this, 
                    "Mã PIN KHÔNG ĐÚNG hoặc thẻ bị khóa!\n\n" +
                    "Vui lòng kiểm tra lại PIN và thử lại.", 
                    "Từ chối", JOptionPane.ERROR_MESSAGE);
                txtPin.setText("");
                txtPin.requestFocus();
                return;
            }

            // 3. PIN đúng → Bắt đầu RSA challenge-response (V3)
            System.out.println("[User Login] ✓ PIN đúng, bắt đầu xác thực RSA...");
            
            // 3.1. Lấy PK_user từ database
            byte[] pkUserBytes = DatabaseConnection.getUserPublicKey(cardIdUser);
            
            if (pkUserBytes == null) {
                // ❌ SAI: Thẻ chưa có RSA key (chưa phát hành đúng)
                System.err.println("[User Login] ✗ Thẻ chưa có PK_user trong database");
                
                JOptionPane.showMessageDialog(this, 
                    "THẺ CHƯA CÓ RSA KEY!\n\n" +
                    "Thẻ này chưa được phát hành với RSA.\n" +
                    "Vui lòng liên hệ admin để phát hành lại thẻ.\n\n" +
                    "Lý do bảo mật: Không thể xác thực tính hợp lệ của thẻ.", 
                    "Thẻ không hợp lệ", JOptionPane.ERROR_MESSAGE);
                return; // TỪ CHỐI đăng nhập
            }
            
            System.out.println("[User Login] ✓ Đã lấy PK_user từ database, length: " + pkUserBytes.length);
            
            // 3.2. Sinh challenge (32 bytes)
            byte[] challenge = CryptoUtils.generateChallenge();
            System.out.println("[User Login] Challenge (32 bytes): " + bytesToHex(challenge));
            
            // 3.3. Gửi challenge xuống thẻ, nhận signature
            byte[] signature = null;
            try {
                signature = apduCommands.signChallenge(challenge);
            } catch (CardException e) {
                // ⚠️ LỖI: Exception khi giao tiếp với thẻ
                System.err.println("[User Login] CardException: " + e.getMessage());
                e.printStackTrace();
                
                int retry = JOptionPane.showConfirmDialog(this, 
                    "LỖI GIAO TIẾP VỚI THẺ!\n\n" +
                    "Lỗi: " + e.getMessage() + "\n\n" +
                    "Nguyên nhân có thể:\n" +
                    "- Thẻ bị disconnect\n" +
                    "- Đầu đọc bị lỗi\n" +
                    "- Timeout\n\n" +
                    "Bạn có muốn thử lại không?", 
                    "Lỗi giao tiếp", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.WARNING_MESSAGE);
                
                if (retry == JOptionPane.YES_OPTION) {
                    handleLogin(); // CHO PHÉP retry
                }
                return;
            }
            
            if (signature == null || signature.length == 0) {
                // ⚠️ LỖI: Thẻ không trả về signature
                System.err.println("[User Login] ✗ Thẻ không trả về signature");
                
                int retry = JOptionPane.showConfirmDialog(this, 
                    "LỖI XÁC THỰC!\n\n" +
                    "Không nhận được chữ ký từ thẻ.\n\n" +
                    "Nguyên nhân có thể:\n" +
                    "- Lỗi tạm thời\n" +
                    "- Lỗi giao tiếp\n" +
                    "- Thẻ bị lỗi\n\n" +
                    "Bạn có muốn thử lại không?", 
                    "Lỗi", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.WARNING_MESSAGE);
                
                if (retry == JOptionPane.YES_OPTION) {
                    handleLogin(); // CHO PHÉP retry
                }
                return;
            }
            
            System.out.println("[User Login] ✓ Nhận được signature, length: " + signature.length);
            
            // 3.4. Convert PK_user từ byte[] sang PublicKey
            java.security.PublicKey pkUser = CryptoUtils.bytesToPublicKey(pkUserBytes);
            
            if (pkUser == null) {
                // ⚠️ LỖI: Parse public key thất bại
                System.err.println("[User Login] ✗ Không thể parse PK_user");
                
                JOptionPane.showMessageDialog(this, 
                    "LỖI HỆ THỐNG!\n\n" +
                    "Không thể parse public key từ database.\n" +
                    "Vui lòng liên hệ admin để kiểm tra.", 
                    "Lỗi hệ thống", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 3.5. Verify signature
            boolean isValid = CryptoUtils.verifyRSASignature(challenge, signature, pkUser);
            
            if (!isValid) {
                // ❌ SAI: Signature không hợp lệ → Thẻ giả!
                System.err.println("[User Login] ✗✗✗ SIGNATURE KHÔNG HỢP LỆ! ✗✗✗");
                
                JOptionPane.showMessageDialog(this, 
                    "XÁC THỰC THẺ THẤT BẠI!\n\n" +
                    "CHỮ KÝ SỐ KHÔNG HỢP LỆ.\n" +
                    "THẺ NÀY CÓ THỂ LÀ GIẢ!\n\n" +
                    "⚠️ CẢNH BÁO BẢO MẬT ⚠️\n\n" +
                    "Vui lòng liên hệ admin để kiểm tra ngay.", 
                    "CẢNH BÁO BẢO MẬT", 
                    JOptionPane.ERROR_MESSAGE);
                return; // TỪ CHỐI đăng nhập
            }
            
            // ✅ Xác thực thành công!
            System.out.println("[User Login] ✓✓✓ XÁC THỰC RSA THÀNH CÔNG! ✓✓✓");
            System.out.println("[User Login] PIN đúng + RSA verify OK → Cho phép đăng nhập");

            // 4. Parse userData từ response
            UserData userData = UserData.fromBytes(userDataBytes);
            if (userData == null) {
                JOptionPane.showMessageDialog(this, 
                    "Không thể parse dữ liệu từ thẻ!", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 5. Đăng nhập thành công
            System.out.println("[User Login] UserData loaded: " + userData.getHoTen());
            
            dispose();
            // Truyền PIN và userData xuống UserFrame
            new UserFrame(cardManager, apduCommands, pin, userData).setVisible(true);

        } catch (Exception e) {
            // ⚠️ LỖI: Unexpected exception
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "LỖI KHÔNG XÁC ĐỊNH!\n\n" +
                "Lỗi: " + e.getMessage() + "\n\n" +
                "Vui lòng thử lại hoặc liên hệ admin.", 
                "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Helper: Convert byte array sang hex string
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}

