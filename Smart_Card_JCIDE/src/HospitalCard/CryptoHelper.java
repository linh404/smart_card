package HospitalCard;

import javacard.framework.Util;
import javacard.framework.JCSystem;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * CryptoHelper - Helper class cho các thao tác mã hóa/giải mã
 * Bao gồm: AES encryption/decryption, SHA-1 hash, key derivation sử dụng ALG_SHA
 */
public class CryptoHelper {
    
    /**
     * Hash PIN với salt bằng SHA-1
     * Kết quả: SHA-1(PIN || salt)
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN trong buffer
     * @param pinLen độ dài PIN
     * @param salt salt bytes (16 bytes)
     * @param hashOut output buffer cho hash (20 bytes cho SHA-1)
     * @return độ dài hash (20 bytes)
     */
    public static short hashPin(byte[] pin, short pinOffset, short pinLen, 
                                byte[] salt, byte[] hashOut) {
        MessageDigest sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        sha.reset();
        sha.update(pin, pinOffset, pinLen);
        sha.doFinal(salt, (short)0, (short)16, hashOut, (short)0);
        return (short)20; // SHA-1 = 20 bytes
    }
    
    /**
     * Dẫn xuất khóa từ PIN sử dụng ALG_SHA từ thư viện Java Card
     * Sử dụng SHA-1(PIN || salt) - một lần hash duy nhất
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN trong buffer
     * @param pinLen độ dài PIN
     * @param salt salt bytes (16 bytes)
     * @param keyOut output buffer cho key (phải có tối thiểu 20 bytes, lấy 16 bytes đầu làm AES-128 key)
     */
    public static void deriveKeyFromPin(byte[] pin, short pinOffset, short pinLen, 
                                        byte[] salt, byte[] keyOut) {
        // Sử dụng ALG_SHA từ thư viện Java Card
        MessageDigest sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        
        // Hash PIN + salt: SHA-1(PIN || salt)
        sha.reset();
        sha.update(pin, pinOffset, pinLen);
        sha.doFinal(salt, (short)0, (short)16, keyOut, (short)0);
        
        // keyOut đã có 20 bytes hash (SHA-1 output)
        // Lấy 16 bytes đầu làm AES-128 key (không cần copy, chỉ cần đảm bảo keyOut có ít nhất 20 bytes)
        // Note: keyOut phải có kích thước tối thiểu 20 bytes
    }
    
    /**
     * KDF - Mô phỏng PBKDF2 sử dụng SHA-1 (ALG_SHA)
     * 
     * PBKDF2-SHA1 mô phỏng: Lặp lại hash nhiều lần để tăng độ khó brute force
     * Algorithm (mô phỏng PBKDF2, không dùng HMAC vì JavaCard không hỗ trợ):
     *   T1 = SHA-1(PIN || salt || INT_32_BE(1))
     *   T2 = SHA-1(PIN || T1)
     *   T3 = SHA-1(PIN || T2)
     *   ...
     *   Tn = SHA-1(PIN || T(n-1))
     *   Key = T1 XOR T2 XOR ... XOR Tn
     * 
     * Note: Thuật toán này mô phỏng PBKDF2 nhưng không dùng HMAC (vì JavaCard không hỗ trợ HMAC).
     * Vẫn an toàn và hiệu quả với nhiều iterations.
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN trong buffer
     * @param pinLen độ dài PIN
     * @param salt salt bytes (16 bytes)
     * @param saltOffset offset của salt
     * @param saltLen độ dài salt
     * @param iterations số lần lặp (khuyến nghị: 1000-10000)
     * @param keyOut output buffer cho key (phải có tối thiểu 20 bytes, lấy 16 bytes đầu làm AES-128 key)
     * @param sha MessageDigest instance (ALG_SHA) - được truyền vào để tái sử dụng, tránh tạo mới nhiều lần
     */
    public static void KDF(byte[] pin, short pinOffset, short pinLen,
                           byte[] salt, short saltOffset, short saltLen,
                           short iterations, byte[] keyOut, MessageDigest sha) {
        // Validate iterations (tối thiểu 1, tối đa 65535)
        if (iterations < 1) {
            iterations = 1;
        }
        if (iterations > 65535) {
            iterations = (short)65535; // Giới hạn để tránh overflow
        }
        
        // Tạo buffer tạm cho hash intermediate values
        // T1, T2, ... Tn mỗi cái 20 bytes (SHA-1 output)
        byte[] t = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
        byte[] tPrev = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
        // Buffer cho hash input: 
        // - Lần đầu: PIN || salt || counter (pinLen + saltLen + 4)
        // - Các lần sau: PIN || T(i-1) (pinLen + 20)
        // Lấy max của 2 để đủ cho cả 2 trường hợp
        short maxInputLen = (short)(pinLen + saltLen + 4);
        if (pinLen + 20 > maxInputLen) {
            maxInputLen = (short)(pinLen + 20);
        }
        byte[] hashInput = JCSystem.makeTransientByteArray(maxInputLen, JCSystem.CLEAR_ON_DESELECT);
        
        // Khởi tạo keyOut = 0 (sẽ XOR với các Ti)
        Util.arrayFillNonAtomic(keyOut, (short)0, (short)20, (byte)0);
        
        // Tính T1 = SHA-1(PIN || salt || INT_32_BE(1))
        // Format: PIN || salt || [0x00 0x00 0x00 0x01] (counter = 1, 4 bytes big-endian)
        short inputOffset = 0;
        Util.arrayCopyNonAtomic(pin, pinOffset, hashInput, inputOffset, pinLen);
        inputOffset += pinLen;
        Util.arrayCopyNonAtomic(salt, saltOffset, hashInput, inputOffset, saltLen);
        inputOffset += saltLen;
        // Counter = 1 (4 bytes big-endian)
        hashInput[inputOffset++] = (byte)0x00;
        hashInput[inputOffset++] = (byte)0x00;
        hashInput[inputOffset++] = (byte)0x00;
        hashInput[inputOffset++] = (byte)0x01;
        
        sha.reset();
        sha.doFinal(hashInput, (short)0, inputOffset, t, (short)0);
        
        // XOR vào keyOut
        for (short i = 0; i < 20; i++) {
            keyOut[i] = (byte)(keyOut[i] ^ t[i]);
        }
        
        // Copy t vào tPrev để dùng cho lần lặp tiếp theo
        Util.arrayCopyNonAtomic(t, (short)0, tPrev, (short)0, (short)20);
        
        // Tính T2, T3, ..., Tn
        // Ti = SHA-1(PIN || T(i-1)) - không dùng salt nữa
        for (short iter = 2; iter <= iterations; iter++) {
            // Tạo input: PIN || T(i-1)
            inputOffset = 0;
            Util.arrayCopyNonAtomic(pin, pinOffset, hashInput, inputOffset, pinLen);
            inputOffset += pinLen;
            Util.arrayCopyNonAtomic(tPrev, (short)0, hashInput, inputOffset, (short)20);
            inputOffset += 20;
            
            // Hash
            sha.reset();
            sha.doFinal(hashInput, (short)0, inputOffset, t, (short)0);
            
            // XOR vào keyOut
            for (short i = 0; i < 20; i++) {
                keyOut[i] = (byte)(keyOut[i] ^ t[i]);
            }
            
            // Copy t vào tPrev cho lần lặp tiếp theo
            Util.arrayCopyNonAtomic(t, (short)0, tPrev, (short)0, (short)20);
        }
        
        // keyOut đã có 20 bytes (SHA-1 output)
        // Lấy 16 bytes đầu làm AES-128 key
        // Note: keyOut phải có kích thước tối thiểu 20 bytes
    }
    
    /**
     * KDF - Overload version với salt offset = 0 và saltLen = 16
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN trong buffer
     * @param pinLen độ dài PIN
     * @param salt salt bytes (16 bytes, offset = 0)
     * @param iterations số lần lặp (khuyến nghị: 1000-10000)
     * @param keyOut output buffer cho key (phải có tối thiểu 20 bytes)
     * @param sha MessageDigest instance (ALG_SHA)
     */
    public static void KDF(byte[] pin, short pinOffset, short pinLen,
                           byte[] salt, short iterations, byte[] keyOut, MessageDigest sha) {
        KDF(pin, pinOffset, pinLen, salt, (short)0, (short)16, iterations, keyOut, sha);
    }
    
    /**
     * Mã hóa dữ liệu bằng AES-128 ECB
     * Tự động thêm PKCS#7 padding nếu cần (vì dùng NOPAD)
     * 
     * @param cipher AES cipher instance
     * @param key AES key
     * @param plaintext dữ liệu cần mã hóa
     * @param plainOffset offset của plaintext
     * @param plainLen độ dài plaintext
     * @param ciphertext output buffer cho ciphertext
     * @param cipherOffset offset của ciphertext
     * @return độ dài ciphertext
     */
    public static short encryptAES(Cipher cipher, AESKey key, 
                                   byte[] plaintext, short plainOffset, short plainLen,
                                   byte[] ciphertext, short cipherOffset) {
        // Tính độ dài sau khi pad (phải là bội số của 16)
        short paddedLen = (short)((plainLen + 15) / 16 * 16);
        
        // Nếu cần padding, tạo buffer với padding
        if (paddedLen != plainLen) {
            // Tạo transient buffer để padding
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLen, JCSystem.CLEAR_ON_DESELECT);
            
            // Copy dữ liệu gốc
            Util.arrayCopy(plaintext, plainOffset, paddedData, (short)0, plainLen);
            
            // Thêm PKCS#7 padding: các byte cuối = số byte padding
            byte paddingValue = (byte)(paddedLen - plainLen);
            Util.arrayFillNonAtomic(paddedData, plainLen, paddingValue, paddingValue);
            
            // Mã hóa dữ liệu đã padding
            cipher.init(key, Cipher.MODE_ENCRYPT);
            return cipher.doFinal(paddedData, (short)0, paddedLen, ciphertext, cipherOffset);
        } else {
            // Không cần padding
            cipher.init(key, Cipher.MODE_ENCRYPT);
            return cipher.doFinal(plaintext, plainOffset, plainLen, ciphertext, cipherOffset);
        }
    }
    
    /**
     * Giải mã dữ liệu bằng AES-128 ECB
     * Tự động loại bỏ PKCS#7 padding nếu có
     * 
     * @param cipher AES cipher instance
     * @param key AES key
     * @param ciphertext dữ liệu cần giải mã
     * @param cipherOffset offset của ciphertext
     * @param cipherLen độ dài ciphertext
     * @param plaintext output buffer cho plaintext
     * @param plainOffset offset của plaintext
     * @return độ dài plaintext (sau khi loại bỏ padding)
     */
    public static short decryptAES(Cipher cipher, AESKey key,
                                   byte[] ciphertext, short cipherOffset, short cipherLen,
                                   byte[] plaintext, short plainOffset) {
        cipher.init(key, Cipher.MODE_DECRYPT);
        short decryptedLen = cipher.doFinal(ciphertext, cipherOffset, cipherLen, plaintext, plainOffset);
        
        // Loại bỏ PKCS#7 padding: byte cuối chỉ số byte padding
        if (decryptedLen > 0) {
            byte paddingValue = plaintext[(short)(plainOffset + decryptedLen - 1)];
            
            // Kiểm tra padding hợp lệ (1 <= padding <= 16)
            if (paddingValue > 0 && paddingValue <= 16 && paddingValue <= decryptedLen) {
                // Verify tất cả padding bytes đều giống nhau
                boolean validPadding = true;
                short i;
                for (i = (short)(decryptedLen - paddingValue); i < decryptedLen; i = (short)(i + 1)) {
                    if (plaintext[(short)(plainOffset + i)] != paddingValue) {
                        validPadding = false;
                        break;
                    }
                }
                
                if (validPadding) {
                    // Trả về độ dài không bao gồm padding
                    return (short)(decryptedLen - paddingValue);
                }
            }
        }
        
        return decryptedLen;
    }
    
    /**
     * Mở Master Key từ encrypted MK bằng PIN
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN
     * @param pinLen độ dài PIN
     * @param salt salt để derive key
     * @param mkEnc encrypted master key (32 bytes)
     * @param mkOut output buffer cho master key (phải có ít nhất 20 bytes)
     * @param cipher AES cipher instance
     * @param aesKey AES key object để set key
     * @return true nếu thành công, false nếu lỗi
     */
    public static boolean openMasterKey(byte[] pin, short pinOffset, short pinLen,
                                       byte[] salt, byte[] mkEnc,
                                       byte[] mkOut, Cipher cipher, AESKey aesKey) {
        try {
            // Dẫn xuất keyUser từ PIN vào mkOut (tạm thời, cần 20 bytes)
            deriveKeyFromPin(pin, pinOffset, pinLen, salt, mkOut);
            
            // Set key vào aesKey (lấy 16 bytes đầu)
            aesKey.setKey(mkOut, (short)0);
            
            // Giải mã MK_user vào chính mkOut (overwrite derivedKey)
            cipher.init(aesKey, Cipher.MODE_DECRYPT);
            short mkLen = cipher.doFinal(mkEnc, (short)0, (short)16, mkOut, (short)0);
            
            // Cập nhật aesKey với MK_user để dùng cho mã hóa/giải mã dữ liệu
            aesKey.setKey(mkOut, (short)0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Mã hóa Master Key bằng key derived từ PIN
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN
     * @param pinLen độ dài PIN
     * @param salt salt để derive key
     * @param mk master key (16 bytes)
     * @param mkEncOut output buffer cho encrypted master key (32 bytes)
     * @param cipher AES cipher instance
     * @param aesKey AES key object
     */
    public static void encryptMasterKey(byte[] pin, short pinOffset, short pinLen,
                                        byte[] salt, byte[] mk,
                                        byte[] mkEncOut, Cipher cipher, AESKey aesKey) {
        // Sử dụng mkEncOut làm buffer tạm để lưu derivedKey (tận dụng mkEncOut[0..19])
        // deriveKeyFromPin cần buffer 20 bytes, mkEncOut có 32 bytes nên đủ
        deriveKeyFromPin(pin, pinOffset, pinLen, salt, mkEncOut); // Lưu vào mkEncOut tạm thời
        
        // Set key và mã hóa - MK vào chính mkEncOut (overwrite derivedKey)
        aesKey.setKey(mkEncOut, (short)0); // Lấy 16 bytes đầu làm key
        cipher.init(aesKey, Cipher.MODE_ENCRYPT);
        // Pad MK to 16 bytes if needed (ECB NOPAD requires 16 bytes block)
        cipher.doFinal(mk, (short)0, (short)16, mkEncOut, (short)0);
    }
}

