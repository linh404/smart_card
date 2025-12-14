package ui;

import db.DatabaseConnection;
import ui.admin.LoginFrame;
import ui.user.UserLoginFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * MainFrame - Màn hình chính của ứng dụng
 * Cho phép chọn đăng nhập Admin hoặc User
 * 
 * V2: Admin không cần thẻ, đăng nhập bằng username/password
 */
public class MainFrame extends JFrame {
    
    public MainFrame() {
        setTitle("Hệ Thống Thẻ Thông Minh Bệnh Viện");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 300);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        // Panel chính
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Tiêu đề
        JLabel titleLabel = new JLabel("HỆ THỐNG THẺ THÔNG MINH BỆNH VIỆN");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        mainPanel.add(titleLabel, gbc);
        
        // Nút Admin
        JButton adminButton = new JButton("Đăng nhập Admin");
        adminButton.setPreferredSize(new Dimension(200, 50));
        adminButton.setFont(new Font("Arial", Font.PLAIN, 14));
        adminButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                new LoginFrame().setVisible(true);
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        mainPanel.add(adminButton, gbc);
        
        // Nút User
        JButton userButton = new JButton("Đăng nhập User");
        userButton.setPreferredSize(new Dimension(200, 50));
        userButton.setFont(new Font("Arial", Font.PLAIN, 14));
        userButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                new UserLoginFrame().setVisible(true);
            }
        });
        gbc.gridx = 1;
        gbc.gridy = 1;
        mainPanel.add(userButton, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    public static void main(String[] args) {
        // Thiết lập encoding UTF-8 để hiển thị tiếng Việt trong console
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        
        // Thiết lập default charset cho JVM
        try {
            java.lang.reflect.Field charsetField = java.nio.charset.Charset.class.getDeclaredField("defaultCharset");
            charsetField.setAccessible(true);
            charsetField.set(null, java.nio.charset.Charset.forName("UTF-8"));
        } catch (Exception e) {
            // Ignore nếu không thể set
        }
        
        // Thiết lập encoding cho System.out và System.err
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (Exception e) {
            System.err.println("Warning: Could not set UTF-8 encoding for console output");
        }
        
        // Thiết lập font mặc định cho UI hỗ trợ tiếng Việt
        try {
            Font defaultFont = new Font("Arial Unicode MS", Font.PLAIN, 12);
            // Fallback to Arial if Arial Unicode MS not available
            if (!defaultFont.getFamily().equals("Arial Unicode MS")) {
                defaultFont = new Font("Arial", Font.PLAIN, 12);
            }
            
            java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                Object value = UIManager.get(key);
                if (value instanceof javax.swing.plaf.FontUIResource) {
                    UIManager.put(key, defaultFont);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not set default UI font");
        }
        
        // Thêm shutdown hook để đóng HikariCP pool khi thoát ứng dụng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[MainFrame] Đang đóng database connection pool...");
            DatabaseConnection.shutdown();
        }));
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
}

