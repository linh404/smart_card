package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import model.UserCardSnapshot;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * UserDemoSnapshotManager - Quản lý snapshot demo cho các thẻ User
 * Lưu plaintext data vào file JSON local để có thể nạp nhanh vào applet User khi demo
 */
public class UserDemoSnapshotManager {
    
    private static final String DEMO_SNAPSHOT_FILE = "data" + File.separator + "user" + File.separator + "demo_snapshots.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Đảm bảo thư mục và file tồn tại
     */
    private static void ensureFileExists() {
        try {
            Path filePath = Paths.get(DEMO_SNAPSHOT_FILE);
            Path parentDir = filePath.getParent();
            
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            if (!Files.exists(filePath)) {
                // Tạo file mới với cấu trúc rỗng
                DemoSnapshotContainer container = new DemoSnapshotContainer();
                container.snapshots = new HashMap<>();
                saveSnapshots(container);
            }
        } catch (IOException e) {
            System.err.println("[UserDemoSnapshotManager] Lỗi khi tạo file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Lưu snapshot của một thẻ User
     * @param snapshot Snapshot cần lưu
     * @return true nếu thành công
     */
    public static boolean saveSnapshot(UserCardSnapshot snapshot) {
        ensureFileExists();
        
        try {
            DemoSnapshotContainer container = loadSnapshots();
            if (container == null) {
                container = new DemoSnapshotContainer();
                container.snapshots = new HashMap<>();
            }
            
            // Set timestamp
            if (container.snapshots.containsKey(snapshot.getCardIdHex())) {
                snapshot.setUpdatedAt(LocalDateTime.now().format(DATE_FORMATTER));
                // Giữ nguyên createdAt nếu đã có
                UserCardSnapshot existing = container.snapshots.get(snapshot.getCardIdHex());
                if (existing.getCreatedAt() != null) {
                    snapshot.setCreatedAt(existing.getCreatedAt());
                }
            } else {
                snapshot.setCreatedAt(LocalDateTime.now().format(DATE_FORMATTER));
                snapshot.setUpdatedAt(LocalDateTime.now().format(DATE_FORMATTER));
            }
            
            container.snapshots.put(snapshot.getCardIdHex(), snapshot);
            
            return saveSnapshots(container);
        } catch (Exception e) {
            System.err.println("[UserDemoSnapshotManager] Lỗi khi lưu snapshot: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy snapshot theo cardId (hex string)
     * @param cardIdHex Card ID dạng hex string
     * @return UserCardSnapshot hoặc null nếu không tìm thấy
     */
    public static UserCardSnapshot getSnapshot(String cardIdHex) {
        ensureFileExists();
        
        try {
            DemoSnapshotContainer container = loadSnapshots();
            if (container == null || container.snapshots == null) {
                return null;
            }
            
            return container.snapshots.get(cardIdHex);
        } catch (Exception e) {
            System.err.println("[UserDemoSnapshotManager] Lỗi khi đọc snapshot: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lấy tất cả snapshots
     * @return Map<String, UserCardSnapshot> với key là cardIdHex, hoặc null nếu lỗi
     */
    public static Map<String, UserCardSnapshot> getAllSnapshots() {
        ensureFileExists();
        
        try {
            DemoSnapshotContainer container = loadSnapshots();
            if (container == null || container.snapshots == null) {
                return new HashMap<>();
            }
            
            return container.snapshots;
        } catch (Exception e) {
            System.err.println("[UserDemoSnapshotManager] Lỗi khi đọc tất cả snapshots: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }


    /**
     * Load snapshots từ file JSON
     */
    private static DemoSnapshotContainer loadSnapshots() {
        try {
            Path filePath = Paths.get(DEMO_SNAPSHOT_FILE);
            if (!Files.exists(filePath)) {
                return null;
            }
            
            String json = new String(Files.readAllBytes(filePath), java.nio.charset.StandardCharsets.UTF_8);
            return gson.fromJson(json, DemoSnapshotContainer.class);
        } catch (Exception e) {
            System.err.println("[UserDemoSnapshotManager] Lỗi khi đọc file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lưu snapshots vào file JSON
     */
    private static boolean saveSnapshots(DemoSnapshotContainer container) {
        try {
            Path filePath = Paths.get(DEMO_SNAPSHOT_FILE);
            String json = gson.toJson(container);
            
            Files.write(filePath, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            System.out.println("[UserDemoSnapshotManager] Đã lưu snapshots vào: " + DEMO_SNAPSHOT_FILE);
            return true;
        } catch (Exception e) {
            System.err.println("[UserDemoSnapshotManager] Lỗi khi lưu file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Container class để lưu tất cả snapshots trong một file JSON
     */
    private static class DemoSnapshotContainer {
        @com.google.gson.annotations.SerializedName("snapshots")
        Map<String, UserCardSnapshot> snapshots;
    }

    /**
     * Helper method: Chuyển byte[] sang hex string
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Helper method: Chuyển hex string sang byte[]
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}

