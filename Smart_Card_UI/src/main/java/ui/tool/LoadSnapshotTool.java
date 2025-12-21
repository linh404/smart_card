package ui.tool;

import card.CardManager;
import card.APDUCommands;
import db.DatabaseConnection;
import model.UserCardSnapshot;
import model.UserData;
import util.UserDemoSnapshotManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * LoadSnapshotTool - Tool để nạp dữ liệu từ snapshot vào thẻ User
 * Dùng cho mục đích demo với JCIDE
 * 
 * V3: Sửa lỗi RSA - parse và lưu PK_user vào database sau khi nạp thẻ
 */
public class LoadSnapshotTool extends JFrame {

    private CardManager cardManager;
    private APDUCommands apduCommands;

    private JComboBox<String> comboCardId;
    private JTextArea txtSnapshotInfo;
    private JTextArea txtLog;
    private JLabel lblStatus;
    private JButton btnLoad;
    private JButton btnRefresh;

    private Map<String, UserCardSnapshot> allSnapshots;

    public LoadSnapshotTool() {
        initUI();
        cardManager = CardManager.getInstance();
        refreshSnapshotList();
    }

    private void initUI() {
        setTitle("Công Cụ Nạp Snapshot Vào Thẻ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 102, 153));
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("CÔNG CỤ NẠP SNAPSHOT VÀO THẺ USER");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel trên: Chọn snapshot
        JPanel selectPanel = new JPanel(new BorderLayout());
        selectPanel.setBorder(BorderFactory.createTitledBorder("Chọn Snapshot"));

        JPanel comboPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        comboPanel.add(new JLabel("Card ID:"));
        comboCardId = new JComboBox<>();
        comboCardId.setPreferredSize(new Dimension(400, 30));
        comboCardId.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateSnapshotInfo();
            }
        });
        comboPanel.add(comboCardId);

        btnRefresh = new JButton("Làm mới danh sách");
        btnRefresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshSnapshotList();
            }
        });
        comboPanel.add(btnRefresh);

        selectPanel.add(comboPanel, BorderLayout.NORTH);

        // Thông tin snapshot
        txtSnapshotInfo = new JTextArea(8, 80);
        txtSnapshotInfo.setEditable(false);
        txtSnapshotInfo.setFont(new Font("Courier New", Font.PLAIN, 12));
        txtSnapshotInfo.setBackground(new Color(245, 245, 245));
        JScrollPane infoScroll = new JScrollPane(txtSnapshotInfo);
        selectPanel.add(infoScroll, BorderLayout.CENTER);

        mainPanel.add(selectPanel, BorderLayout.NORTH);

        // Panel giữa: Nút Load và Status
        JPanel actionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // Hướng dẫn
        JLabel instructionLabel = new JLabel(
                "<html><b>Hướng dẫn:</b> Chọn Card ID từ danh sách, sau đó nhấn <b>Nạp vào thẻ</b> để nạp dữ liệu từ snapshot vào thẻ User</html>");
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        actionPanel.add(instructionLabel, gbc);

        // Nút Load
        btnLoad = new JButton("NẠP VÀO THẺ");
        btnLoad.setFont(new Font("Arial", Font.BOLD, 16));
        btnLoad.setPreferredSize(new Dimension(250, 50));
        btnLoad.setBackground(new Color(0, 153, 0));
        btnLoad.setForeground(Color.WHITE);
        btnLoad.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadSnapshotToCard();
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        actionPanel.add(btnLoad, gbc);

        // Status
        lblStatus = new JLabel("Trạng thái: Sẵn sàng");
        lblStatus.setFont(new Font("Arial", Font.BOLD, 12));
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        actionPanel.add(lblStatus, gbc);

        mainPanel.add(actionPanel, BorderLayout.CENTER);

        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Nhật ký"));
        txtLog = new JTextArea(10, 80);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Courier New", Font.PLAIN, 11));
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(txtLog);
        logPanel.add(logScroll, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Làm mới danh sách snapshots
     */
    private void refreshSnapshotList() {
        log("Đang tải danh sách snapshots...");
        allSnapshots = UserDemoSnapshotManager.getAllSnapshots();

        comboCardId.removeAllItems();

        if (allSnapshots == null || allSnapshots.isEmpty()) {
            log("Không có snapshot nào trong file!");
            lblStatus.setText("Trạng thái: Không có snapshot");
            lblStatus.setForeground(Color.ORANGE);
            txtSnapshotInfo.setText("Không có snapshot nào.");
            return;
        }

        for (String cardId : allSnapshots.keySet()) {
            comboCardId.addItem(cardId);
        }

        log("Đã tải " + allSnapshots.size() + " snapshot(s)");
        updateSnapshotInfo();
    }

    /**
     * Cập nhật thông tin snapshot được chọn
     */
    private void updateSnapshotInfo() {
        String selectedCardId = (String) comboCardId.getSelectedItem();
        if (selectedCardId == null || allSnapshots == null) {
            txtSnapshotInfo.setText("");
            return;
        }

        UserCardSnapshot snapshot = allSnapshots.get(selectedCardId);
        if (snapshot == null) {
            txtSnapshotInfo.setText("Không tìm thấy snapshot cho Card ID: " + selectedCardId);
            return;
        }

        StringBuilder info = new StringBuilder();
        info.append("Card ID: ").append(snapshot.getCardIdHex()).append("\n");
        info.append("Họ tên: ").append(snapshot.getHoTen() != null ? snapshot.getHoTen() : "-").append("\n");
        info.append("ID bệnh nhân: ").append(snapshot.getIdBenhNhan() != null ? snapshot.getIdBenhNhan() : "-")
                .append("\n");
        info.append("Ngày sinh: ").append(snapshot.getNgaySinh() != null ? snapshot.getNgaySinh() : "-").append("\n");
        info.append("Quê quán: ").append(snapshot.getQueQuan() != null ? snapshot.getQueQuan() : "-").append("\n");
        info.append("Mã BHYT: ").append(snapshot.getMaBHYT() != null ? snapshot.getMaBHYT() : "-").append("\n");
        info.append("PIN User: ").append(snapshot.getPinUserDefault() != null ? snapshot.getPinUserDefault() : "-")
                .append("\n");
        info.append("PIN Admin Reset: ").append(snapshot.getPinAdminReset() != null ? snapshot.getPinAdminReset() : "-")
                .append("\n");

        // V3: Hiển thị trạng thái RSA key
        if (snapshot.getPkUserBase64() != null && !snapshot.getPkUserBase64().isEmpty()) {
            info.append("RSA Public Key: ✓ Có sẵn trong snapshot\n");
        } else {
            info.append("RSA Public Key: ⚠ Không có (sẽ parse từ response thẻ)\n");
        }

        if (snapshot.getCreatedAt() != null) {
            info.append("Tạo lúc: ").append(snapshot.getCreatedAt()).append("\n");
        }
        if (snapshot.getUpdatedAt() != null) {
            info.append("Cập nhật lúc: ").append(snapshot.getUpdatedAt());
        }

        txtSnapshotInfo.setText(info.toString());
    }

    /**
     * Nạp snapshot vào thẻ
     */
    private void loadSnapshotToCard() {
        String selectedCardId = (String) comboCardId.getSelectedItem();
        if (selectedCardId == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một Card ID!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        UserCardSnapshot snapshot = allSnapshots.get(selectedCardId);
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy snapshot cho Card ID: " + selectedCardId, "Lỗi",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Kiểm tra PIN
        if (snapshot.getPinUserDefault() == null || snapshot.getPinUserDefault().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Snapshot không có PIN User!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (snapshot.getPinAdminReset() == null || snapshot.getPinAdminReset().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Snapshot không có PIN Admin Reset!\n\n" +
                            "Có thể snapshot này được tạo trước khi thêm tính năng lưu PIN admin.\n" +
                            "Vui lòng phát hành thẻ mới để có đầy đủ thông tin.",
                    "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            btnLoad.setEnabled(false);
            lblStatus.setText("Trạng thái: Đang xử lý...");
            lblStatus.setForeground(Color.BLUE);
            log("=== BẮT ĐẦU NẠP SNAPSHOT VÀO THẺ ===");
            log("Card ID: " + selectedCardId);

            // 1. Kết nối với đầu đọc thẻ
            log("Bước 1: Đang kết nối với đầu đọc thẻ...");
            if (!cardManager.connect()) {
                log("LỖI: Không thể kết nối với đầu đọc thẻ!");
                lblStatus.setText("Trạng thái: Lỗi kết nối");
                lblStatus.setForeground(Color.RED);
                btnLoad.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                        "Không thể kết nối với đầu đọc thẻ!\nVui lòng kiểm tra:\n" +
                                "1. Đầu đọc thẻ đã được cắm\n" +
                                "2. Thẻ đã được đặt vào đầu đọc\n" +
                                "3. Driver đầu đọc đã được cài đặt",
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log("✓ Đã kết nối với đầu đọc thẻ");

            // 2. Select UserApplet
            log("Bước 2: Đang select UserApplet...");
            if (!cardManager.selectApplet(APDUCommands.AID_USER)) {
                log("LỖI: Không tìm thấy UserApplet trên thẻ!");
                lblStatus.setText("Trạng thái: Không tìm thấy applet");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnLoad.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                        "Không tìm thấy UserApplet trên thẻ!\n\n" +
                                "Vui lòng kiểm tra:\n" +
                                "1. Thẻ User đã được cắm đúng cách\n" +
                                "2. UserApplet đã được cài đặt trên thẻ",
                        "Lỗi UserApplet", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log("✓ Đã chọn UserApplet thành công");
            apduCommands = new APDUCommands(cardManager.getChannel());

            // 3. Chuẩn bị dữ liệu
            log("Bước 3: Đang chuẩn bị dữ liệu...");
            UserData userData = snapshot.toUserData();
            byte[] userDataBytes = userData.toBytes();
            byte[] cardIdBytes = UserDemoSnapshotManager.hexToBytes(selectedCardId);
            byte[] pinUserBytes = snapshot.getPinUserDefault().getBytes();
            byte[] pinAdminBytes = snapshot.getPinAdminReset().getBytes();
            int initialBalance = (int) snapshot.getBalance();

            log("  - Card ID: " + selectedCardId);
            log("  - Họ tên: " + snapshot.getHoTen());
            log("  - ID bệnh nhân: " + snapshot.getIdBenhNhan());
            log("  - PIN User: " + snapshot.getPinUserDefault());
            log("  - PIN Admin Reset: " + snapshot.getPinAdminReset());
            log("  - Số dư: " + initialBalance + " VNĐ");

            // 4. Gửi lệnh ISSUE_CARD (với balance)
            log("Bước 4: Đang nạp dữ liệu vào thẻ (ISSUE_CARD)...");
            log("  Lưu ý: Nếu thẻ đã được phát hành, lệnh này sẽ ghi đè dữ liệu cũ");

            // V3: Sử dụng issueCard 5-param để có response chứa RSA keys
            byte[] result = apduCommands.issueCard(cardIdBytes, userDataBytes, pinUserBytes, pinAdminBytes,
                    initialBalance);

            if (result == null || result.length < 1 || result[0] != 0x00) {
                log("LỖI: Nạp dữ liệu vào thẻ thất bại!");
                String errorDetail = "Status: "
                        + (result != null && result.length > 0 ? String.format("0x%02X", result[0]) : "null");
                log("  " + errorDetail);
                lblStatus.setText("Trạng thái: Nạp thất bại");
                lblStatus.setForeground(Color.RED);
                cardManager.disconnect();
                btnLoad.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                        "Nạp dữ liệu vào thẻ thất bại!\n\n" +
                                "Nguyên nhân có thể:\n" +
                                "1. Thẻ đã được phát hành và không cho phép ghi đè\n" +
                                "2. Dữ liệu quá lớn hoặc format không đúng\n" +
                                "3. Lỗi applet trên thẻ\n\n" +
                                "Vui lòng kiểm tra console để xem chi tiết.",
                        "Nạp thất bại", JOptionPane.ERROR_MESSAGE);
                return;
            }
            log("✓ Đã nạp dữ liệu vào thẻ thành công");

            // 5. V3: QUAN TRỌNG - LUÔN parse RSA public key từ response của thẻ
            // Vì ISSUE_CARD sinh RSA key pair MỚI mỗi lần gọi
            // PK trong snapshot là CŨ (từ lần phát hành trước) và không khớp SK mới trên
            // thẻ
            log("Bước 5: Đang parse RSA public key MỚI từ response...");
            log("  ⚠ LƯU Ý: ISSUE_CARD sinh key MỚI, KHÔNG dùng PK từ snapshot!");
            byte[] pkUserEncoded = null;

            // LUÔN parse từ response (không dùng snapshot vì key đã thay đổi)
            if (result != null && result.length > 3) {
                try {
                    // Format: [status (1)] [pk_mod_len (2)] [pk_modulus] [pk_exp_len (2)]
                    // [pk_exponent] ...
                    short offset = 1; // Skip status byte

                    short pkModLen = getShort(result, offset);
                    offset += 2;
                    byte[] pkModulus = new byte[pkModLen];
                    System.arraycopy(result, offset, pkModulus, 0, pkModLen);
                    offset += pkModLen;

                    short pkExpLen = getShort(result, offset);
                    offset += 2;
                    byte[] pkExponent = new byte[pkExpLen];
                    System.arraycopy(result, offset, pkExponent, 0, pkExpLen);

                    log("  PK_user parsed từ thẻ: modLen=" + pkModLen + ", expLen=" + pkExpLen);

                    // Convert RAW bytes → Java standard format
                    BigInteger n = new BigInteger(1, pkModulus);
                    BigInteger e = new BigInteger(1, pkExponent);

                    RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(n, e);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    java.security.PublicKey javaPublicKey = kf.generatePublic(pubSpec);

                    // Encode sang X.509 format (standard)
                    pkUserEncoded = javaPublicKey.getEncoded();
                    log("  ✓ PK_user MỚI encoded (X.509): " + pkUserEncoded.length + " bytes");

                } catch (Exception e) {
                    log("  ✗ Lỗi parse PK_user từ response: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                log("  ✗ Response quá ngắn, không thể parse RSA key!");
            }

            // Lưu PK_user vào database
            if (pkUserEncoded != null) {
                log("Bước 6: Đang lưu PK_user vào database...");

                // Đảm bảo user_card record tồn tại trước
                if (!DatabaseConnection.saveUserCard(cardIdBytes, snapshot.getIdBenhNhan(), null)) {
                    log("  ⚠ Không thể tạo/cập nhật user_card record");
                }

                // Lưu PK_user
                if (DatabaseConnection.saveUserPublicKey(cardIdBytes, pkUserEncoded)) {
                    log("  ✓ Đã lưu PK_user vào database thành công");
                } else {
                    log("  ⚠ Lưu PK_user vào database thất bại!");
                    log("  → User login sẽ bị lỗi RSA verification!");
                }
            } else {
                log("⚠ Không có PK_user để lưu! User login sẽ bị lỗi RSA!");
            }

            // 7. Verify cardID trên thẻ
            log("Bước 7: Đang verify Card ID trên thẻ...");
            byte[] cardIdOnCard = apduCommands.getStatus();

            if (cardIdOnCard == null || cardIdOnCard.length != 16) {
                log("CẢNH BÁO: Không thể đọc Card ID từ thẻ!");
            } else {
                String cardIdOnCardHex = UserDemoSnapshotManager.bytesToHex(cardIdOnCard);
                log("  Card ID trên thẻ: " + cardIdOnCardHex);
                if (cardIdOnCardHex.equalsIgnoreCase(selectedCardId)) {
                    log("✓ Card ID khớp");
                } else {
                    log("⚠ Card ID không khớp! Expected: " + selectedCardId + ", Got: " + cardIdOnCardHex);
                }
            }

            // Ngắt kết nối
            cardManager.disconnect();
            log("✓ Đã ngắt kết nối với thẻ");

            log("=== NẠP SNAPSHOT VÀO THẺ THÀNH CÔNG ===");
            log("Card ID: " + selectedCardId);
            log("Dữ liệu đã được nạp vào thẻ thành công!");
            if (pkUserEncoded != null) {
                log("RSA Public Key đã được lưu vào database!");
            }

            lblStatus.setText("Trạng thái: Thành công!");
            lblStatus.setForeground(new Color(0, 153, 0));

            String rsaStatus = pkUserEncoded != null ? "\n\n✓ RSA Public Key đã được lưu vào database"
                    : "\n\n⚠ RSA Public Key KHÔNG được lưu - User login sẽ lỗi!";

            JOptionPane.showMessageDialog(this,
                    "Nạp snapshot vào thẻ thành công!\n\n" +
                            "Card ID: " + selectedCardId + "\n" +
                            "Họ tên: " + snapshot.getHoTen() + "\n" +
                            "ID bệnh nhân: " + snapshot.getIdBenhNhan() + "\n\n" +
                            "PIN User: " + snapshot.getPinUserDefault() + "\n" +
                            "PIN Admin Reset: " + snapshot.getPinAdminReset() +
                            rsaStatus,
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);

            btnLoad.setEnabled(true);

        } catch (Exception e) {
            e.printStackTrace();
            log("LỖI: " + e.getMessage());
            lblStatus.setText("Trạng thái: Lỗi");
            lblStatus.setForeground(Color.RED);
            btnLoad.setEnabled(true);
            try {
                cardManager.disconnect();
            } catch (Exception ex) {
                // Ignore
            }
            JOptionPane.showMessageDialog(this, "Lỗi: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Helper: Parse short từ byte array (Big-Endian)
     */
    private short getShort(byte[] data, int offset) {
        return (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    private void log(String message) {
        if (txtLog != null) {
            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
            txtLog.append("[" + timestamp + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        } else {
            System.out.println("[LOG] " + message);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoadSnapshotTool().setVisible(true);
            }
        });
    }
}
