package util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * AdminPinDerivation - Derive PIN_admin_reset từ K_master và cardID
 * 
 * Công thức: PIN_admin_reset = HMAC-SHA256(K_master, cardID || "ADMINPIN") mod 10^6
 * 
 * K_master được lưu trong environment variable K_MASTER (hex string)
 * Mỗi thẻ có một PIN_admin_reset khác nhau dựa trên cardID
 */
public class AdminPinDerivation {
    
    /**
     * Derive PIN_admin_reset từ K_master và cardID
     * 
     * @param cardID Card ID (16 bytes)
     * @return PIN 6 chữ số (String, format: "000000" - "999999")
     * @throws IllegalStateException nếu K_MASTER environment variable không được set
     * @throws RuntimeException nếu có lỗi crypto
     */
    public static String deriveAdminResetPIN(byte[] cardID) {
        try {
            // Load .env file if not already loaded
            EnvFileLoader.load();
            
            // 1. Lấy K_master từ environment variable hoặc .env file
            String kMasterEnv = EnvFileLoader.getEnv("K_MASTER");
            if (kMasterEnv == null || kMasterEnv.isEmpty()) {
                throw new IllegalStateException("K_MASTER environment variable not set! " +
                        "Please set K_MASTER in your .env file or system environment variables.");
            }
            
            // 2. Convert K_master từ hex string sang byte[]
            byte[] kMaster = hexStringToByteArray(kMasterEnv);
            
            // Validate K_master length (should be 32 bytes for AES-256)
            if (kMaster.length != 32) {
                throw new IllegalStateException("K_MASTER must be 32 bytes (64 hex characters). " +
                        "Current length: " + kMaster.length + " bytes");
            }
            
            // 3. Tạo input: cardID || "ADMINPIN"
            byte[] adminPinBytes = "ADMINPIN".getBytes(StandardCharsets.UTF_8);
            byte[] input = new byte[cardID.length + adminPinBytes.length];
            System.arraycopy(cardID, 0, input, 0, cardID.length);
            System.arraycopy(adminPinBytes, 0, input, cardID.length, adminPinBytes.length);
            
            // 4. Tính HMAC-SHA256
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(kMaster, "HmacSHA256");
            hmac.init(keySpec);
            byte[] hmacResult = hmac.doFinal(input);
            
            // 5. Mod 10^6 để có PIN 6 số
            BigInteger bigInt = new BigInteger(1, hmacResult);
            int pin = bigInt.mod(BigInteger.valueOf(1000000)).intValue();
            
            // 6. Format thành 6 chữ số
            return String.format("%06d", pin);
            
        } catch (IllegalStateException e) {
            // Re-throw illegal state exceptions
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error deriving admin reset PIN: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert hex string sang byte array
     * 
     * @param hexString Hex string (e.g., "0123456789ABCDEF")
     * @return byte array
     */
    private static byte[] hexStringToByteArray(String hexString) {
        // Remove whitespace and convert to uppercase
        hexString = hexString.replaceAll("\\s+", "").toUpperCase();
        
        int len = hexString.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                                 + Character.digit(hexString.charAt(i+1), 16));
        }
        return data;
    }
    
    /**
     * Convert byte array sang hex string (for debugging)
     * 
     * @param bytes Byte array
     * @return Hex string
     */
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    /**
     * Generate a random K_MASTER hex string (32 bytes = 64 hex chars)
     * Chỉ dùng để khởi tạo lần đầu, không dùng trong production
     * 
     * @return Hex string of 32 random bytes
     */
    public static String generateRandomKMaster() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] kMaster = new byte[32];
        random.nextBytes(kMaster);
        return bytesToHexString(kMaster);
    }
    
    /**
     * Test method để verify derivation
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Test với một cardID mẫu
        byte[] testCardID = {
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
            0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
        };
        
        System.out.println("=== AdminPinDerivation Test ===");
        System.out.println("Test CardID: " + bytesToHexString(testCardID));
        
        // Load .env first
        EnvFileLoader.load();
        
        // Check if K_MASTER is set
        String kMaster = EnvFileLoader.getEnv("K_MASTER");
        if (kMaster == null || kMaster.isEmpty()) {
            String randomKMaster = generateRandomKMaster();
            System.out.println("\nWarning: K_MASTER not set in environment!");
            System.out.println("You can use this random K_MASTER for testing:");
            System.out.println("K_MASTER=" + randomKMaster);
            System.out.println("\nSet it in your .env file or system environment variables.");
            System.out.println("On Windows: set K_MASTER=" + randomKMaster);
            System.out.println("On Linux/Mac: export K_MASTER=" + randomKMaster);
            return;
        }
        
        try {
            String pin = deriveAdminResetPIN(testCardID);
            System.out.println("\nDerived Admin Reset PIN: " + pin);
            System.out.println("PIN length: " + pin.length());
            
            // Test consistency - derive lại nên cho ra cùng kết quả
            String pin2 = deriveAdminResetPIN(testCardID);
            System.out.println("\nConsistency check: " + (pin.equals(pin2) ? "PASS" : "FAIL"));
            
            // Test với cardID khác
            byte[] testCardID2 = {
                0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
                0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
            };
            String pin3 = deriveAdminResetPIN(testCardID2);
            System.out.println("\nDifferent CardID: " + bytesToHexString(testCardID2));
            System.out.println("Derived PIN: " + pin3);
            System.out.println("Different from first PIN: " + (!pin.equals(pin3) ? "PASS" : "FAIL"));
            
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

