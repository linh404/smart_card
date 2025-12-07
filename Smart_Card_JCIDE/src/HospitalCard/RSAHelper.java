package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * RSAHelper - Helper class cho các thao tác RSA
 * Bao gồm: sign, verify, generate key pair
 */
public class RSAHelper {
    
    /**
     * Ký dữ liệu bằng RSA private key
     * 
     * @param cipher RSA cipher instance
     * @param privKey RSA private key
     * @param data dữ liệu cần ký
     * @param dataOffset offset của data
     * @param dataLen độ dài data
     * @param signatureOut output buffer cho signature
     * @param sigOffset offset của signature
     * @return độ dài signature
     */
    public static short sign(Cipher cipher, RSAPrivateKey privKey,
                            byte[] data, short dataOffset, short dataLen,
                            byte[] signatureOut, short sigOffset) {
        if (privKey == null || !privKey.isInitialized()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        
        try {
            cipher.init(privKey, Cipher.MODE_ENCRYPT); // RSA sign = encrypt với private key
            return cipher.doFinal(data, dataOffset, dataLen, signatureOut, sigOffset);
        } catch (Exception e) {
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
            return 0; // Never reaches here
        }
    }
    
    /**
     * Verify chữ ký bằng RSA public key
     * 
     * @param cipher RSA cipher instance
     * @param pubKey RSA public key
     * @param signature chữ ký cần verify
     * @param sigOffset offset của signature
     * @param sigLen độ dài signature
     * @param data dữ liệu gốc
     * @param dataOffset offset của data
     * @param dataLen độ dài data
     * @return true nếu verify thành công
     */
    public static boolean verify(Cipher cipher, RSAPublicKey pubKey,
                                byte[] signature, short sigOffset, short sigLen,
                                byte[] data, short dataOffset, short dataLen) {
        if (pubKey == null || !pubKey.isInitialized()) {
            return false;
        }
        
        try {
            cipher.init(pubKey, Cipher.MODE_DECRYPT); // RSA verify = decrypt với public key
            byte[] decrypted = new byte[128]; // RSA 1024 = 128 bytes
            short decryptedLen = cipher.doFinal(signature, sigOffset, sigLen, decrypted, (short)0);
            
            // So sánh decrypted với data gốc
            if (decryptedLen != dataLen) {
                return false;
            }
            return (Util.arrayCompare(decrypted, (short)0, data, dataOffset, dataLen) == 0);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Lấy RSA public key (modulus + exponent)
     * 
     * @param pubKey RSA public key
     * @param buf buffer để lưu kết quả
     * @param offset offset trong buffer
     * @return tổng độ dài (modLen + expLen + 4 bytes cho 2 length fields)
     */
    public static short getPublicKeyBytes(RSAPublicKey pubKey, byte[] buf, short offset) {
        if (pubKey == null || !pubKey.isInitialized()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            return 0;
        }
        
        // Read modulus into offset + 2 (reserve first 2 bytes for modLen)
        short modLen = pubKey.getModulus(buf, (short)(offset + 2));
        short expOffset = (short)(offset + 2 + modLen);
        
        // Read exponent into expOffset + 2 (reserve 2 bytes for expLen)
        short expLen = pubKey.getExponent(buf, (short)(expOffset + 2));
        
        // Ghi modLen và expLen
        Util.setShort(buf, offset, modLen);
        Util.setShort(buf, expOffset, expLen);
        
        // Return total length: modLen(2) + modulus + expLen(2) + exponent
        return (short)(4 + modLen + expLen);
    }
    
    /**
     * Khởi tạo RSA key pair
     * 
     * @param keyPair RSA key pair
     * @return true nếu thành công
     */
    public static boolean generateKeyPair(KeyPair keyPair) {
        if (keyPair == null) {
            return false;
        }
        
        try {
            if (!((RSAPrivateKey)keyPair.getPrivate()).isInitialized()) {
                keyPair.genKeyPair();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

