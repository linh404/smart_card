package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JsonFileManager - Quản lý việc lưu và đọc JSON files cho thẻ Admin
 */
public class JsonFileManager {
    
    private static final String DATA_DIR = "data";
    private static final String ADMIN_DIR = DATA_DIR + File.separator + "admin";
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Đảm bảo thư mục tồn tại
     */
    private static void ensureDirectoryExists(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi tạo thư mục: " + dirPath);
            e.printStackTrace();
        }
    }

    /**
     * Lưu JSON config cho thẻ Admin
     * @param cardId Card ID của thẻ Admin (hex string)
     * @param config Object chứa config (sẽ được serialize thành JSON)
     * @return true nếu thành công
     */
    public static boolean saveAdminConfig(String cardId, Object config) {
        ensureDirectoryExists(ADMIN_DIR);
        
        // Tên file: admin_<cardId>_<timestamp>.json
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String fileName = String.format("admin_%s_%s.json", cardId.substring(0, Math.min(8, cardId.length())), timestamp);
        String filePath = ADMIN_DIR + File.separator + fileName;
        
        return saveJson(filePath, config);
    }

    /**
     * Lưu JSON vào file (public để có thể dùng từ bên ngoài)
     */
    public static boolean saveJson(String filePath, Object config) {
        try {
            String json = gson.toJson(config);
            try (FileWriter writer = new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            System.out.println("[JsonFileManager] Đã lưu JSON vào: " + filePath);
            return true;
        } catch (IOException e) {
            System.err.println("[JsonFileManager] Lỗi khi lưu JSON: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đọc JSON config cho thẻ Admin
     * @param filePath Đường dẫn file JSON
     * @param clazz Class của object cần deserialize
     * @return Object đã deserialize hoặc null nếu lỗi
     */
    public static <T> T loadAdminConfig(String filePath, Class<T> clazz) {
        return loadJson(filePath, clazz);
    }

    /**
     * Đọc JSON từ file
     */
    private static <T> T loadJson(String filePath, Class<T> clazz) {
        try {
            String json = new String(Files.readAllBytes(Paths.get(filePath)), java.nio.charset.StandardCharsets.UTF_8);
            return gson.fromJson(json, clazz);
        } catch (IOException e) {
            System.err.println("[JsonFileManager] Lỗi khi đọc JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Lấy đường dẫn thư mục admin
     */
    public static String getAdminDir() {
        ensureDirectoryExists(ADMIN_DIR);
        return ADMIN_DIR;
    }
}

