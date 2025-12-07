package ui.tool;

import db.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * CreateAdminUserTool - Công cụ tạo admin user đầu tiên
 * Dùng khi chưa có admin user nào trong hệ thống
 */
public class CreateAdminUserTool extends JFrame {
    
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtPasswordConfirm;
    private JTextField txtFullName;
    private JTextField txtEmail;
    private JButton btnCreate;
    private JButton btnCancel;
    private JTextArea txtLog;
    private JLabel lblStatus;
    
    public CreateAdminUserTool() {
        initUI();
    }
    
    private void initUI() {
        setTitle("Tạo Admin User Đầu Tiên");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 102, 153));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel title = new JLabel("TẠO ADMIN USER ĐẦU TIÊN");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Thông tin Admin User"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // Username
        gbc.gridx = 0; gbc.gridy = row;
        formPanel.add(new JLabel("Username *:"), gbc);
        txtUsername = new JTextField(25);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtUsername, gbc);
        
        // Password
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Password *:"), gbc);
        txtPassword = new JPasswordField(25);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtPassword, gbc);
        
        // Password Confirm
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Xác nhận Password *:"), gbc);
        txtPasswordConfirm = new JPasswordField(25);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtPasswordConfirm, gbc);
        
        // Full Name
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Họ tên:"), gbc);
        txtFullName = new JTextField(25);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtFullName, gbc);
        
        // Email
        row++;
        gbc.gridx = 0; gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Email:"), gbc);
        txtEmail = new JTextField(25);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(txtEmail, gbc);
        
        mainPanel.add(formPanel, BorderLayout.NORTH);
        
        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout());
        btnCreate = new JButton("Tạo Admin User");
        btnCreate.setPreferredSize(new Dimension(150, 35));
        btnCreate.setBackground(new Color(0, 153, 0));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setFont(new Font("Arial", Font.BOLD, 12));
        btnPanel.add(btnCreate);
        
        btnCancel = new JButton("Hủy");
        btnCancel.setPreferredSize(new Dimension(100, 35));
        btnPanel.add(btnCancel);
        
        // Status
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        lblStatus.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(btnPanel, BorderLayout.CENTER);
        statusPanel.add(lblStatus, BorderLayout.SOUTH);
        
        mainPanel.add(statusPanel, BorderLayout.CENTER);
        
        // Log area
        txtLog = new JTextArea(8, 50);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Courier New", Font.PLAIN, 11));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Log"));
        mainPanel.add(scrollPane, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Event handlers
        btnCreate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createAdminUser();
            }
        });
        
        btnCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        
        // Enter key để tạo
        txtPasswordConfirm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createAdminUser();
            }
        });
        
        // Kiểm tra xem đã có admin user chưa
        checkExistingAdminUsers();
    }
    
    /**
     * Kiểm tra xem đã có admin user nào chưa
     */
    private void checkExistingAdminUsers() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT COUNT(*) as count FROM admin";
            try (PreparedStatement pst = conn.prepareStatement(sql);
                 ResultSet rs = pst.executeQuery()) {
                
                if (rs.next()) {
                    int count = rs.getInt("count");
                    if (count > 0) {
                        log("⚠ CẢNH BÁO: Đã có " + count + " admin user trong hệ thống!");
                        log("Bạn vẫn có thể tạo thêm admin user mới.");
                        lblStatus.setText("Trạng thái: Đã có " + count + " admin user");
                        lblStatus.setForeground(Color.ORANGE);
                    } else {
                        log("✓ Chưa có admin user nào. Bạn có thể tạo admin user đầu tiên.");
                        lblStatus.setText("Trạng thái: Chưa có admin user");
                        lblStatus.setForeground(new Color(0, 153, 0));
                    }
                }
            }
            conn.close();
        } catch (Exception e) {
            log("LỖI: Không thể kiểm tra admin users - " + e.getMessage());
            lblStatus.setText("Trạng thái: Lỗi kết nối");
            lblStatus.setForeground(Color.RED);
        }
    }
    
    /**
     * Tạo admin user mới
     */
    private void createAdminUser() {
        try {
            txtLog.setText("");
            log("=== TẠO ADMIN USER ===");
            lblStatus.setText("Trạng thái: Đang tạo...");
            lblStatus.setForeground(Color.BLUE);
            
            // Validate input
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());
            String passwordConfirm = new String(txtPasswordConfirm.getPassword());
            String fullName = txtFullName.getText().trim();
            String email = txtEmail.getText().trim();
            
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập username!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtUsername.requestFocus();
                return;
            }
            
            if (username.length() < 3 || username.length() > 50) {
                JOptionPane.showMessageDialog(this, "Username phải từ 3 đến 50 ký tự!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtUsername.requestFocus();
                return;
            }
            
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập password!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtPassword.requestFocus();
                return;
            }
            
            if (password.length() < 6) {
                JOptionPane.showMessageDialog(this, "Password phải có ít nhất 6 ký tự!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtPassword.requestFocus();
                return;
            }
            
            if (!password.equals(passwordConfirm)) {
                JOptionPane.showMessageDialog(this, "Password và xác nhận password không khớp!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                txtPasswordConfirm.requestFocus();
                return;
            }
            
            log("Username: " + username);
            log("Full Name: " + (fullName.isEmpty() ? "(trống)" : fullName));
            log("Email: " + (email.isEmpty() ? "(trống)" : email));
            log("");
            log("Đang tạo admin user...");
            
            // Tạo admin user
            boolean success = DatabaseConnection.createAdminUser(username, password, 
                fullName.isEmpty() ? null : fullName, 
                email.isEmpty() ? null : email);
            
            if (success) {
                log("✓✓✓ TẠO ADMIN USER THÀNH CÔNG! ✓✓✓");
                log("");
                log("Thông tin đăng nhập:");
                log("  Username: " + username);
                log("  Password: " + password);
                log("");
                log("Bạn có thể đóng cửa sổ này và đăng nhập vào hệ thống.");
                
                lblStatus.setText("Trạng thái: Tạo thành công");
                lblStatus.setForeground(new Color(0, 153, 0));
                
                JOptionPane.showMessageDialog(this,
                    "Tạo admin user thành công!\n\n" +
                    "Username: " + username + "\n" +
                    "Password: " + password + "\n\n" +
                    "Vui lòng lưu lại thông tin đăng nhập này!",
                    "Thành công",
                    JOptionPane.INFORMATION_MESSAGE);
                
                // Clear form
                txtUsername.setText("");
                txtPassword.setText("");
                txtPasswordConfirm.setText("");
                txtFullName.setText("");
                txtEmail.setText("");
                
                // Kiểm tra lại số lượng admin users
                checkExistingAdminUsers();
                
            } else {
                log("✗✗✗ TẠO ADMIN USER THẤT BẠI! ✗✗✗");
                log("Có thể do username đã tồn tại hoặc lỗi database.");
                
                lblStatus.setText("Trạng thái: Tạo thất bại");
                lblStatus.setForeground(Color.RED);
                
                JOptionPane.showMessageDialog(this,
                    "Tạo admin user thất bại!\n\n" +
                    "Có thể do:\n" +
                    "- Username đã tồn tại\n" +
                    "- Lỗi kết nối database\n" +
                    "- Lỗi hệ thống\n\n" +
                    "Vui lòng kiểm tra log để biết chi tiết.",
                    "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            }
            
        } catch (Exception e) {
            log("EXCEPTION: " + e.getMessage());
            e.printStackTrace();
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            
            JOptionPane.showMessageDialog(this,
                "Lỗi: " + e.getMessage(),
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void log(String message) {
        String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
        txtLog.append("[" + timestamp + "] " + message + "\n");
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new CreateAdminUserTool().setVisible(true);
            }
        });
    }
}

