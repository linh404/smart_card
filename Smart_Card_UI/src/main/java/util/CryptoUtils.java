package util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException; // Added for sha256 method
import java.security.SecureRandom;
import java.security.Signature;

/**
 * CryptoUtils - Các hàm tiện ích mã hóa, băm, ký số
 * Tham khảo từ SmartCard_Old, mở rộng thêm các hàm cần thiết
 */
public class CryptoUtils {

    /**
     * Hash data using SHA-1 (matches Java Card MessageDigest.ALG_SHA)
     */
    public static byte[] sha1(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private static final int SALT_LEN = 16;

    /**
     * Sinh chuỗi Salt ngẫu nhiên
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Sinh Master Key ngẫu nhiên (AES 128 bit)
     */
    public static byte[] generateMasterKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        SecretKey key = keyGen.generateKey();
        return key.getEncoded();
    }

    /**
     * Băm PIN bằng SHA-1 (theo đặc tả: SHA-1(PIN || salt))
     */
    public static byte[] hashPin(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(pin.getBytes(StandardCharsets.UTF_8));
            md.update(salt);
            return md.digest();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Dẫn xuất khóa từ PIN bằng PBKDF2 (mô phỏng)
     * Trên thẻ thực tế sẽ dùng javacard.security.PBKDF2
     * Ở đây dùng SHA-256 nhiều lần để mô phỏng
     */
    public static byte[] deriveKeyFromPin(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] input = (pin + new String(salt, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

            // Lặp 1000 lần (mô phỏng PBKDF2)
            for (int i = 0; i < 1000; i++) {
                input = md.digest(input);
            }

            // Lấy 16 bytes đầu làm AES key
            byte[] key = new byte[16];
            System.arraycopy(input, 0, key, 0, 16);
            return key;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Mã hóa AES (ECB mode)
     */
    public static byte[] aesEncrypt(byte[] data, byte[] keyBytes) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * Giải mã AES
     */
    public static byte[] aesDecrypt(byte[] encryptedData, byte[] keyBytes) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(encryptedData);
    }

    /**
     * Kiểm tra chữ ký RSA (Raw RSA - thẻ ký bằng raw encryption, không hash)
     * Thẻ dùng Cipher.MODE_ENCRYPT với private key để ký, nên ta cần decrypt
     * signature bằng public key và so sánh với message
     */
    public static boolean verifyRSASignature(byte[] message, byte[] signature, java.security.PublicKey publicKey) {
        if (message == null || signature == null || publicKey == null) {
            return false;
        }

        // Thử raw RSA trước (thẻ ký bằng raw encryption)
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, publicKey);
            byte[] decryptedMessage = cipher.doFinal(signature);

            if (java.util.Arrays.equals(message, decryptedMessage)) {
                return true;
            }
        } catch (Exception e) {
            // Raw RSA failed, try SHA256withRSA below
        }

        // Fallback: thử SHA256withRSA nếu raw RSA fail
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(message);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Chuyển đổi PublicKey từ byte[] (DER format)
     */
    public static java.security.PublicKey bytesToPublicKey(byte[] keyBytes) {
        try {
            if (keyBytes == null || keyBytes.length == 0) {
                return null;
            }

            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sinh challenge ngẫu nhiên
     */
    public static byte[] generateChallenge() {
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        return challenge;
    }
}
