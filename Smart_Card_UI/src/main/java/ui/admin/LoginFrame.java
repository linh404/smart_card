package ui.admin;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetAddress;

/**
 * LoginFrame - Màn hình đăng nhập Admin bằng username/password
 * V2: Admin không cần thẻ nữa, đăng nhập bằng username/password
 */
public class LoginFrame extends JFrame {
    
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;
    private JButton btnCancel;

    // Lưu current admin user (static để các panel khác có thể truy cập)
    private static DatabaseConnection.AdminUserInfo currentAdminUser = null;

    public LoginFrame() {
        initUI();
    }
    
    /**
     * Get current logged-in admin user
     */
    public static DatabaseConnection.AdminUserInfo getCurrentAdminUser() {
        return currentAdminUser;
    }
    
    /**
     * Clear current admin user (khi logout)
     */
    public static void clearCurrentAdminUser() {
        currentAdminUser = null;
    }

    private void initUI() {
        setTitle("Đăng nhập Admin");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(550, 320);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        // Tiêu đề
        JLabel titleLabel = new JLabel("ĐĂNG NHẬP ADMIN");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        mainPanel.add(titleLabel, gbc);

        // Username
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        JLabel lblUsername = new JLabel("Username:");
        lblUsername.setFont(new Font("Arial", Font.PLAIN, 13));
        mainPanel.add(lblUsername, gbc);

        txtUsername = new JTextField(30);
        txtUsername.setFont(new Font("Arial", Font.PLAIN, 14));
        txtUsername.setPreferredSize(new Dimension(300, 35));
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(txtUsername, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel lblPassword = new JLabel("Password:");
        lblPassword.setFont(new Font("Arial", Font.PLAIN, 13));
        mainPanel.add(lblPassword, gbc);

        txtPassword = new JPasswordField(30);
        txtPassword.setFont(new Font("Arial", Font.PLAIN, 14));
        txtPassword.setPreferredSize(new Dimension(300, 35));
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(txtPassword, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnLogin = new JButton("Đăng nhập");
        btnLogin.setPreferredSize(new Dimension(140, 40));
        btnLogin.setFont(new Font("Arial", Font.BOLD, 13));
        btnCancel = new JButton("Hủy");
        btnCancel.setPreferredSize(new Dimension(140, 40));
        btnCancel.setFont(new Font("Arial", Font.PLAIN, 13));
        btnPanel.add(btnLogin);
        btnPanel.add(btnCancel);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
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
        
        // Enter key để đăng nhập
        txtPassword.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });
    }

    private void handleLogin() {
        try {
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());
            
            // Validate input
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập username!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtUsername.requestFocus();
                return;
            }
            
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập password!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtPassword.requestFocus();
                return;
            }

            // Xác thực admin user
            System.out.println("[Admin Login] Đang xác thực: username = " + username);
            DatabaseConnection.AdminUserInfo adminUser = DatabaseConnection.authenticateAdmin(username, password);
            
            if (adminUser == null) {
                JOptionPane.showMessageDialog(this, 
                    "Username hoặc password không đúng!", 
                    "Đăng nhập thất bại", 
                    JOptionPane.ERROR_MESSAGE);
                txtPassword.setText("");
                txtPassword.requestFocus();
                return;
            }

            // Lưu current admin user
            currentAdminUser = adminUser;
            
            // Lấy IP address để log
            String ipAddress = null;
            try {
                ipAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                // Ignore
            }
            
            // Lưu audit log
            DatabaseConnection.saveAdminAuditLog(
                adminUser.id, 
                "LOGIN", 
                null, 
                "Login successful from " + username,
                ipAddress
            );
            
            System.out.println("[Admin Login] Đăng nhập thành công: " + adminUser.username);
            
            // Đăng nhập thành công
            JOptionPane.showMessageDialog(this, 
                "Đăng nhập thành công!\nChào mừng: " + 
                (adminUser.fullName != null ? adminUser.fullName : adminUser.username), 
                "Thành công", 
                JOptionPane.INFORMATION_MESSAGE);
            
            // Khởi tạo CardManager (cần cho các panel khác)
            CardManager cardManager = CardManager.getInstance();
            APDUCommands apduCommands = new APDUCommands(cardManager.getChannel());
            
            dispose();
            new AdminFrame(cardManager, apduCommands).setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Lỗi khi đăng nhập: " + e.getMessage(), 
                "Lỗi", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
}

