package HospitalCard;

import javacard.framework.Util;
import javacard.framework.JCSystem;
import javacard.framework.ISOException;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * CryptoHelper - Helper class for cryptographic operations
 * Includes: AES encryption/decryption, SHA-1 hash, key derivation using ALG_SHA
 */
public class CryptoHelper {

    /**
     * Hash PIN with salt using SHA-1
     * Result: SHA-1(PIN || salt)
     * 
     * @param pin       PIN bytes
     * @param pinOffset PIN offset in buffer
     * @param pinLen    PIN length
     * @param salt      salt bytes (16 bytes)
     * @param hashOut   output buffer for hash (20 bytes for SHA-1)
     * @return hash length (20 bytes)
     */
    public static short hashPin(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, byte[] hashOut) {
        MessageDigest sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        sha.reset();
        sha.update(pin, pinOffset, pinLen);
        sha.doFinal(salt, (short) 0, (short) 16, hashOut, (short) 0);
        return (short) 20; // SHA-1 = 20 bytes
    }

    /**
     * Derive key from PIN using ALG_SHA from Java Card library
     * Uses SHA-1(PIN || salt) - single hash only
     * 
     * @param pin       PIN bytes
     * @param pinOffset PIN offset in buffer
     * @param pinLen    PIN length
     * @param salt      salt bytes (16 bytes)
     * @param keyOut    output buffer for key (must have minimum 20 bytes, first 16
     *                  bytes used as AES-128 key)
     */
    public static void deriveKeyFromPin(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, byte[] keyOut) {
        // Use ALG_SHA from Java Card library
        MessageDigest sha = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);

        // Hash PIN + salt: SHA-1(PIN || salt)
        sha.reset();
        sha.update(pin, pinOffset, pinLen);
        sha.doFinal(salt, (short) 0, (short) 16, keyOut, (short) 0);

        // keyOut now has 20 bytes hash (SHA-1 output)
        // Take first 16 bytes as AES-128 key (no copy needed, just ensure keyOut has
        // at least 20 bytes)
        // Note: keyOut must have minimum size of 20 bytes
    }

    /**
     * KDF - Simulates PBKDF2 using SHA-1 (ALG_SHA)
     * 
     * PBKDF2-SHA1 simulation: Iterate hash many times to increase brute force
     * difficulty
     * Algorithm (simulates PBKDF2, no HMAC since JavaCard doesn't support it):
     * T1 = SHA-1(PIN || salt || INT_32_BE(1))
     * T2 = SHA-1(PIN || T1)
     * T3 = SHA-1(PIN || T2)
     * ...
     * Tn = SHA-1(PIN || T(n-1))
     * Key = T1 XOR T2 XOR ... XOR Tn
     * 
     * Note: This algorithm simulates PBKDF2 but doesn't use HMAC (since JavaCard
     * doesn't
     * support HMAC).
     * Still secure and effective with many iterations.
     * 
     * @param pin        PIN bytes
     * @param pinOffset  PIN offset in buffer
     * @param pinLen     PIN length
     * @param salt       salt bytes (16 bytes)
     * @param saltOffset salt offset
     * @param saltLen    salt length
     * @param iterations iteration count (recommended: 1000-10000)
     * @param keyOut     output buffer for key (must have minimum 20 bytes, first 16
     *                   bytes used as AES-128 key)
     * @param sha        MessageDigest instance (ALG_SHA) - passed in for reuse,
     *                   avoid creating multiple times
     */
    public static void KDF(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short saltOffset, short saltLen,
            short iterations, byte[] keyOut, MessageDigest sha) {
        // Validate iterations (minimum 1, maximum 65535)
        if (iterations < 1) {
            iterations = 1;
        }
        if (iterations > 65535) {
            iterations = (short) 65535; // Limit to avoid overflow
        }

        // Create temporary buffer for hash intermediate values
        // T1, T2, ... Tn each 20 bytes (SHA-1 output)
        byte[] t = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);
        byte[] tPrev = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);
        // Buffer for hash input:
        // - First time: PIN || salt || counter (pinLen + saltLen + 4)
        // - Subsequent times: PIN || T(i-1) (pinLen + 20)
        // Take max of 2 to be sufficient for both cases
        short maxInputLen = (short) (pinLen + saltLen + 4);
        if (pinLen + 20 > maxInputLen) {
            maxInputLen = (short) (pinLen + 20);
        }
        byte[] hashInput = JCSystem.makeTransientByteArray(maxInputLen, JCSystem.CLEAR_ON_DESELECT);

        // Initialize keyOut = 0 (will XOR with Ti)
        Util.arrayFillNonAtomic(keyOut, (short) 0, (short) 20, (byte) 0);

        // Calculate T1 = SHA-1(PIN || salt || INT_32_BE(1))
        // Format: PIN || salt || [0x00 0x00 0x00 0x01] (counter = 1, 4 bytes
        // big-endian)
        short inputOffset = 0;
        Util.arrayCopyNonAtomic(pin, pinOffset, hashInput, inputOffset, pinLen);
        inputOffset += pinLen;
        Util.arrayCopyNonAtomic(salt, saltOffset, hashInput, inputOffset, saltLen);
        inputOffset += saltLen;
        // Counter = 1 (4 bytes big-endian)
        hashInput[inputOffset++] = (byte) 0x00;
        hashInput[inputOffset++] = (byte) 0x00;
        hashInput[inputOffset++] = (byte) 0x00;
        hashInput[inputOffset++] = (byte) 0x01;

        sha.reset();
        sha.doFinal(hashInput, (short) 0, inputOffset, t, (short) 0);

        // XOR into keyOut
        for (short i = 0; i < 20; i++) {
            keyOut[i] = (byte) (keyOut[i] ^ t[i]);
        }

        // Copy t into tPrev for next iteration
        Util.arrayCopyNonAtomic(t, (short) 0, tPrev, (short) 0, (short) 20);

        // Calculate T2, T3, ..., Tn
        // Ti = SHA-1(PIN || T(i-1)) - no more salt
        for (short iter = 2; iter <= iterations; iter++) {
            // Create input: PIN || T(i-1)
            inputOffset = 0;
            Util.arrayCopyNonAtomic(pin, pinOffset, hashInput, inputOffset, pinLen);
            inputOffset += pinLen;
            Util.arrayCopyNonAtomic(tPrev, (short) 0, hashInput, inputOffset, (short) 20);
            inputOffset += 20;

            // Hash
            sha.reset();
            sha.doFinal(hashInput, (short) 0, inputOffset, t, (short) 0);

            // XOR into keyOut
            for (short i = 0; i < 20; i++) {
                keyOut[i] = (byte) (keyOut[i] ^ t[i]);
            }

            // Copy t into tPrev for next iteration
            Util.arrayCopyNonAtomic(t, (short) 0, tPrev, (short) 0, (short) 20);
        }

        // keyOut now has 20 bytes (SHA-1 output)
        // Take first 16 bytes as AES-128 key
        // Note: keyOut must have minimum size of 20 bytes
    }

    /**
     * KDF - Overload version with salt offset = 0 and saltLen = 16
     * 
     * @param pin        PIN bytes
     * @param pinOffset  PIN offset in buffer
     * @param pinLen     PIN length
     * @param salt       salt bytes (16 bytes, offset = 0)
     * @param iterations iteration count (recommended: 1000-10000)
     * @param keyOut     output buffer for key (must have minimum 20 bytes)
     * @param sha        MessageDigest instance (ALG_SHA)
     */
    public static void KDF(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short iterations, byte[] keyOut, MessageDigest sha) {
        KDF(pin, pinOffset, pinLen, salt, (short) 0, (short) 16, iterations, keyOut, sha);
    }

    /**
     * Encrypt data using AES-128 ECB
     * 
     * @param key        AES key (16 bytes)
     * @param data       data to encrypt
     * @param dataOffset data offset
     * @param dataLen    data length
     * @param encOut     output buffer for encrypted data
     * @param cipher     AES cipher instance
     * @param aesKey     AES key object
     * @return encrypted data length
     */
    public static short encryptAES(byte[] key, byte[] data, short dataOffset, short dataLen,
            byte[] encOut, Cipher cipher, AESKey aesKey) {
        aesKey.setKey(key, (short) 0);
        cipher.init(aesKey, Cipher.MODE_ENCRYPT);

        // Calculate padding length (PKCS#7 padding)
        short paddingLen = (short) (16 - (dataLen % 16));
        if (paddingLen == 0) {
            paddingLen = 16;
        }
        short paddedLen = (short) (dataLen + paddingLen);

        try {
            // Create transient buffer for padding
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLen,
                    JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(data, dataOffset, paddedData, (short) 0, dataLen);

            // Add PKCS#7 padding: last bytes = number of padding bytes
            for (short i = dataLen; i < paddedLen; i++) {
                paddedData[i] = (byte) paddingLen;
            }

            // Encrypt padded data
            return cipher.doFinal(paddedData, (short) 0, paddedLen, encOut, (short) 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Decrypt data using AES-128 ECB
     * 
     * @param key       AES key (16 bytes)
     * @param encData   encrypted data
     * @param encOffset encrypted data offset
     * @param encLen    encrypted data length
     * @param dataOut   output buffer for decrypted data
     * @param cipher    AES cipher instance
     * @param aesKey    AES key object
     * @return decrypted data length (without padding)
     */
    public static short decryptAES(byte[] key, byte[] encData, short encOffset, short encLen,
            byte[] dataOut, Cipher cipher, AESKey aesKey) {
        aesKey.setKey(key, (short) 0);
        cipher.init(aesKey, Cipher.MODE_DECRYPT);

        try {
            short decryptedLen = cipher.doFinal(encData, encOffset, encLen, dataOut, (short) 0);

            // Remove PKCS#7 padding
            if (decryptedLen > 0) {
                byte paddingLen = dataOut[(short) (decryptedLen - 1)];

                // Validate padding (1 <= padding <= 16)
                if (paddingLen >= 1 && paddingLen <= 16) {
                    decryptedLen -= paddingLen;
                }
            }

            return decryptedLen;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Decrypt master key using key derived from PIN
     * Combines KDF + AES decryption into one helper method
     * 
     * @param pin       PIN bytes
     * @param pinOffset PIN offset
     * @param pinLen    PIN length
     * @param salt      salt for key derivation
     * @param mkEnc     encrypted master key (16 bytes)
     * @param mkOut     decrypted master key output (16 bytes)
     * @param cipher    AES cipher instance
     * @param aesKey    AES key object
     * @param sha       MessageDigest instance
     */
    public static void decryptMasterKeyWithPin(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, byte[] mkEnc, byte[] mkOut,
            javacardx.crypto.Cipher cipher,
            javacard.security.AESKey aesKey,
            MessageDigest sha) {
        // MasterKey is stored in mkOut temporarily (will be overwritten)
        // Uses mkOut as temporary buffer to save derived key (leverage mkOut[0..19])
        deriveKeyFromPin(pin, pinOffset, pinLen, salt, mkOut);

        try {
            // Set key and decrypt - MK into mkOut itself (overwrite derivedKey)
            aesKey.setKey(mkOut, (short) 0); // Take first 16 bytes as key
            cipher.init(aesKey, javacardx.crypto.Cipher.MODE_DECRYPT);
            // Decrypt MK_user into mkOut itself (overwrite derivedKey)
            cipher.doFinal(mkEnc, (short) 0, (short) 16, mkOut, (short) 0);

            // Update aesKey with MK_user for use in encrypting/decrypting data
            aesKey.setKey(mkOut, (short) 0);
        } catch (Exception e) {
            // Handle error silently
        }
    }

    /**
     * Encrypt Master Key using key derived from PIN
     * 
     * @param pin       PIN bytes
     * @param pinOffset PIN offset
     * @param pinLen    PIN length
     * @param salt      salt for key derivation
     * @param mk        master key (16 bytes)
     * @param mkEncOut  output buffer for encrypted master key (32 bytes)
     * @param cipher    AES cipher instance
     * @param aesKey    AES key object
     */
    public static void encryptMasterKey(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, byte[] mk,
            byte[] mkEncOut, Cipher cipher, AESKey aesKey) {
        // Use mkEncOut as temp buffer to save derivedKey (leverage mkEncOut[0..19])
        // deriveKeyFromPin needs 20 byte buffer, mkEncOut has 32 bytes so sufficient
        deriveKeyFromPin(pin, pinOffset, pinLen, salt, mkEncOut); // Save to mkEncOut temporarily

        // Set key and encrypt - MK into mkEncOut itself (overwrite derivedKey)
        aesKey.setKey(mkEncOut, (short) 0); // Take first 16 bytes as key
        cipher.init(aesKey, Cipher.MODE_ENCRYPT);
        // Pad MK to 16 bytes if needed (ECB NOPAD requires 16 bytes block)
        cipher.doFinal(mk, (short) 0, (short) 16, mkEncOut, (short) 0);
    }

    /**
     * Wrap (encrypt) master key with key derived from PIN
     * 
     * Combines KDF + AES encryption in one method to avoid code duplication
     * 
     * @param pin        PIN bytes
     * @param pinOffset  PIN offset
     * @param pinLen     PIN length
     * @param salt       salt for key derivation (usually cardID)
     * @param saltOffset salt offset
     * @param saltLen    salt length
     * @param iterations iterations for KDF
     * @param mkUser     master key to wrap (16 bytes)
     * @param encOut     output buffer for encrypted MK (16 bytes)
     * @param cipher     AES cipher instance
     * @param aesKey     AES key object
     * @param sha        MessageDigest instance
     */
    public static void wrapMasterKeyWithPIN(
            byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short saltOffset, short saltLen,
            short iterations,
            byte[] mkUser, byte[] encOut,
            Cipher cipher, AESKey aesKey, MessageDigest sha) {

        byte[] tempKey = JCSystem.makeTransientByteArray((short) 20,
                JCSystem.CLEAR_ON_DESELECT);
        try {
            // Derive key from PIN
            KDF(pin, pinOffset, pinLen,
                    salt, saltOffset, saltLen,
                    iterations, tempKey, sha);

            // Encrypt MK with derived key
            aesKey.setKey(tempKey, (short) 0);
            cipher.init(aesKey, Cipher.MODE_ENCRYPT);
            cipher.doFinal(mkUser, (short) 0, (short) 16, encOut, (short) 0);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F04 | (e.getReason() & 0x0F)));
        } finally {
            // Always clear tempKey
            Util.arrayFillNonAtomic(tempKey, (short) 0, (short) 20, (byte) 0);
        }
    }

    /**
     * Unwrap (decrypt) master key with key derived from PIN
     * 
     * @param pin        PIN bytes
     * @param pinOffset  PIN offset
     * @param pinLen     PIN length
     * @param salt       salt for key derivation
     * @param saltOffset salt offset
     * @param saltLen    salt length
     * @param iterations iterations for KDF
     * @param mkEnc      encrypted master key (16 bytes)
     * @param mkOut      output buffer for decrypted MK (16 bytes)
     * @param cipher     AES cipher instance
     * @param aesKey     AES key object
     * @param sha        MessageDigest instance
     * @return true if successful, false on error
     */
    public static boolean unwrapMasterKeyWithPIN(
            byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short saltOffset, short saltLen,
            short iterations,
            byte[] mkEnc, byte[] mkOut,
            Cipher cipher, AESKey aesKey, MessageDigest sha) {

        byte[] tempKey = JCSystem.makeTransientByteArray((short) 20,
                JCSystem.CLEAR_ON_DESELECT);
        try {
            // Derive key from PIN
            KDF(pin, pinOffset, pinLen,
                    salt, saltOffset, saltLen,
                    iterations, tempKey, sha);

            // Decrypt MK with derived key
            aesKey.setKey(tempKey, (short) 0);
            cipher.init(aesKey, Cipher.MODE_DECRYPT);
            cipher.doFinal(mkEnc, (short) 0, (short) 16, mkOut, (short) 0);

            return true;

        } catch (CryptoException e) {
            return false;
        } finally {
            Util.arrayFillNonAtomic(tempKey, (short) 0, (short) 20, (byte) 0);
        }
    }
}
