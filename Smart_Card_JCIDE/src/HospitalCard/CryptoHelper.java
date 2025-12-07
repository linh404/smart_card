package HospitalCard;

import javacard.framework.Util;
import javacard.framework.JCSystem;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * CryptoHelper - Helper class cho các thao tác mã hóa/giải mã
 * Bao gồm: AES encryption/decryption, SHA-1 hash, PBKDF2 key derivation
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
     * Derive key from PIN using SHA-256 multiple times (simulate PBKDF2)
     * If JavaCard supports PBKDF2, should use PBKDF2 instead
     * 
     * NOTE: Giảm số iterations từ 1000 xuống 10 để tránh timeout và tiết kiệm tài nguyên
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN trong buffer
     * @param pinLen độ dài PIN
     * @param salt salt bytes (16 bytes)
     * @param keyOut output buffer cho key (16 bytes cho AES-128)
     */
    public static void deriveKeyFromPin(byte[] pin, short pinOffset, short pinLen, 
                                        byte[] salt, byte[] keyOut) {
        MessageDigest sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        
        // Hash PIN + salt: SHA-1(PIN || salt)
        sha.reset();
        sha.update(pin, pinOffset, pinLen);
        sha.doFinal(salt, (short)0, (short)16, keyOut, (short)0); // Output vào keyOut (20 bytes)
        
        // Hash multiple times - GIẢM từ 1000 xuống 10 iterations để tránh timeout
        // Khai báo biến i BÊN NGOÀI vòng lặp (yêu cầu của JavaCard)
        short i;
        for (i = (short)1; i < (short)10; i = (short)(i + 1)) {
            sha.reset();
            sha.doFinal(keyOut, (short)0, (short)20, keyOut, (short)0);
        }
        
        // keyOut đã có 20 bytes hash, lấy 16 bytes đầu làm AES key (không cần copy)
        // Note: keyOut phải có kích thước tối thiểu 20 bytes
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

