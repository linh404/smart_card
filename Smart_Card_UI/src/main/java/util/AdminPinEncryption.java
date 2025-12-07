package util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AdminPinEncryption - Utility class để encrypt/decrypt Admin PIN
 * 
 * Sử dụng AES-256-GCM (Galois/Counter Mode) để mã hóa Admin PIN
 * - AES-256: Mã hóa đối xứng mạnh
 * - GCM: Authenticated encryption (đảm bảo tính toàn vẹn)
 * - Mỗi Admin PIN có IV (Initialization Vector) riêng
 * 
 * Format lưu trong DB: Base64(IV(12 bytes) + EncryptedData + AuthTag(16 bytes))
 */
public class AdminPinEncryption {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // 96 bits cho GCM
    private static final int TAG_LENGTH = 16; // 128 bits authentication tag
    private static final int KEY_LENGTH = 256; // 256 bits = 32 bytes
    
    private final SecretKey secretKey;
    
    /**
     * Constructor với secret key từ byte array
     */
    public AdminPinEncryption(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("Key must be exactly 32 bytes (256 bits)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    /**
     * Constructor với secret key từ Base64 string
     */
    public AdminPinEncryption(String keyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Key must be exactly 32 bytes (256 bits)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    /**
     * Generate một master key mới (dùng một lần khi setup hệ thống)
     * @return Base64 encoded key string
     */
    public static String generateMasterKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH, new SecureRandom());
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Error generating master key", e);
        }
    }
    
    /**
     * Encrypt Admin PIN
     * @param adminPin PIN cần mã hóa (plaintext)
     * @return Base64 encoded string: IV + EncryptedData + AuthTag
     */
    public String encryptAdminPin(String adminPin) {
        if (adminPin == null || adminPin.isEmpty()) {
            throw new IllegalArgumentException("Admin PIN cannot be null or empty");
        }
        
        try {
            // Generate random IV cho mỗi lần encrypt
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // Initialize cipher với GCM
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            // Encrypt
            byte[] encryptedBytes = cipher.doFinal(adminPin.getBytes("UTF-8"));
            
            // Combine IV + EncryptedData (IV prepended)
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            buffer.put(iv);
            buffer.put(encryptedBytes);
            
            // Return Base64 encoded
            return Base64.getEncoder().encodeToString(buffer.array());
            
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting Admin PIN", e);
        }
    }
    
    /**
     * Decrypt Admin PIN
     * @param encryptedPin Base64 encoded string: IV + EncryptedData + AuthTag
     * @return Plaintext Admin PIN
     */
    public String decryptAdminPin(String encryptedPin) {
        if (encryptedPin == null || encryptedPin.isEmpty()) {
            throw new IllegalArgumentException("Encrypted PIN cannot be null or empty");
        }
        
        try {
            // Decode Base64
            byte[] data = Base64.getDecoder().decode(encryptedPin);
            
            if (data.length < IV_LENGTH + TAG_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data length");
            }
            
            // Extract IV và encrypted data
            byte[] iv = new byte[IV_LENGTH];
            byte[] encryptedData = new byte[data.length - IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            System.arraycopy(data, IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            // Initialize cipher với GCM
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(encryptedData);
            
            return new String(decryptedBytes, "UTF-8");
            
        } catch (javax.crypto.AEADBadTagException e) {
            throw new RuntimeException("Authentication failed - Admin PIN may be corrupted or key is incorrect", e);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting Admin PIN", e);
        }
    }
    
    // Master key được lưu trong memory (tự động generate nếu chưa có)
    private static String cachedMasterKey = null;
    
    /**
     * Get master key từ environment variable, system property, hoặc tự động generate
     * Nếu không có trong env/property, sẽ tự động generate và cache trong memory
     */
    public static String getMasterKeyFromEnv() {
        // Thử lấy từ environment variable trước
        String key = System.getenv("ADMIN_PIN_MASTER_KEY");
        if (key != null && !key.isEmpty()) {
            return key;
        }
        
        // Thử lấy từ system property
        key = System.getProperty("admin.pin.master.key");
        if (key != null && !key.isEmpty()) {
            return key;
        }
        
        // Nếu đã có cached key, dùng lại
        if (cachedMasterKey != null) {
            return cachedMasterKey;
        }
        
        // Tự động generate master key mới và cache
        System.out.println("[AdminPinEncryption] Tự động generate master key (không có trong env/property)");
        cachedMasterKey = generateMasterKey();
        System.out.println("[AdminPinEncryption] Master key đã được generate và cache trong memory");
        System.out.println("[AdminPinEncryption] Lưu ý: Key này chỉ tồn tại trong session hiện tại. " +
                          "Để lưu vĩnh viễn, set ADMIN_PIN_MASTER_KEY environment variable.");
        return cachedMasterKey;
    }
    
    /**
     * Clear cached master key (dùng khi cần reset)
     */
    public static void clearCachedMasterKey() {
        cachedMasterKey = null;
    }
}

