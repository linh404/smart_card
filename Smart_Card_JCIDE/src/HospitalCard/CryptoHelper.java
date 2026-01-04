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

    public static void KDF(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short saltOffset, short saltLen,
            short iterations, byte[] keyOut, short keyOutOffset, MessageDigest sha,
            byte[] scratch, short scratchOff) {
        // Validate iterations (minimum 1, maximum 65535)
        if (iterations < 1) {
            iterations = 1;
        }
        if (iterations > 65535) {
            iterations = (short) 65535; // Limit to avoid overflow
        }

        // Partition scratch buffer:
        // t: 20 bytes at scratchOff
        // tPrev: 20 bytes at scratchOff + 20
        // hashInput: rest (need at least pinLen + 20, usually ~60 bytes is safe)

        short tOff = scratchOff;
        short tPrevOff = (short) (scratchOff + 20);
        short hashInputOff = (short) (scratchOff + 40);

        // Initialize keyOut = 0 (will XOR with Ti)
        Util.arrayFillNonAtomic(keyOut, keyOutOffset, (short) 20, (byte) 0);

        // Calculate T1 = SHA-1(PIN || salt || INT_32_BE(1))
        // Format: PIN || salt || [0x00 0x00 0x00 0x01]
        short inputOffset = hashInputOff;
        Util.arrayCopyNonAtomic(pin, pinOffset, scratch, inputOffset, pinLen);
        inputOffset += pinLen;
        Util.arrayCopyNonAtomic(salt, saltOffset, scratch, inputOffset, saltLen);
        inputOffset += saltLen;
        // Counter = 1 (4 bytes big-endian)
        scratch[inputOffset++] = (byte) 0x00;
        scratch[inputOffset++] = (byte) 0x00;
        scratch[inputOffset++] = (byte) 0x00;
        scratch[inputOffset++] = (byte) 0x01;

        sha.reset();
        sha.doFinal(scratch, hashInputOff, (short) (inputOffset - hashInputOff), scratch, tOff);

        // XOR into keyOut
        for (short i = 0; i < 20; i++) {
            keyOut[(short) (keyOutOffset + i)] = (byte) (keyOut[(short) (keyOutOffset + i)]
                    ^ scratch[(short) (tOff + i)]);
        }

        // Copy t into tPrev for next iteration
        Util.arrayCopyNonAtomic(scratch, tOff, scratch, tPrevOff, (short) 20);

        // Calculate T2, T3, ..., Tn
        // Ti = SHA-1(PIN || T(i-1)) - no more salt
        for (short iter = 2; iter <= iterations; iter++) {
            // Create input: PIN || T(i-1)
            inputOffset = hashInputOff;
            Util.arrayCopyNonAtomic(pin, pinOffset, scratch, inputOffset, pinLen);
            inputOffset += pinLen;
            Util.arrayCopyNonAtomic(scratch, tPrevOff, scratch, inputOffset, (short) 20);
            inputOffset += 20;

            // Hash
            sha.reset();
            sha.doFinal(scratch, hashInputOff, (short) (inputOffset - hashInputOff), scratch, tOff);

            // XOR into keyOut
            for (short i = 0; i < 20; i++) {
                keyOut[(short) (keyOutOffset + i)] = (byte) (keyOut[(short) (keyOutOffset + i)]
                        ^ scratch[(short) (tOff + i)]);
            }

            // Copy t into tPrev for next iteration
            Util.arrayCopyNonAtomic(scratch, tOff, scratch, tPrevOff, (short) 20);
        }
    }

    /**
     * Encrypt data with AES-128 and PKCS#7 padding
     * 
     * @param key        AES key (16 bytes)
     * @param data       data to encrypt
     * @param dataOffset data offset
     * @param dataLen    data length
     * @param encOut     output buffer
     * @param cipher     AES cipher instance
     * @param aesKey     AES key object
     * @return encrypted length (0 if error)
     */
    public static short encryptAES(byte[] key, byte[] data, short dataOffset,
            short dataLen, byte[] encOut, Cipher cipher, AESKey aesKey) {
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
     * Decrypt AES data and remove PKCS#7 padding
     * 
     * @param key       AES key (16 bytes)
     * @param encData   encrypted data
     * @param encOffset encrypted data offset
     * @param encLen    encrypted data length
     * @param dataOut   output buffer
     * @param cipher    AES cipher instance
     * @param aesKey    AES key object
     * @return decrypted length (0 if error)
     */
    public static short decryptAES(byte[] key, byte[] encData, short encOffset,
            short encLen, byte[] dataOut, Cipher cipher, AESKey aesKey) {
        aesKey.setKey(key, (short) 0);
        cipher.init(aesKey, Cipher.MODE_DECRYPT);

        try {
            short decryptedLen = cipher.doFinal(encData, encOffset, encLen, dataOut,
                    (short) 0);

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
     * @param scratch    Scratch buffer for temporary calculation (must be at least
     *                   80 bytes)
     * @param scratchOff Offset in scratch buffer
     */
    public static void wrapMasterKeyWithPIN(
            byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short saltOffset, short saltLen,
            short iterations,
            byte[] mkUser, byte[] encOut,
            Cipher cipher, AESKey aesKey, MessageDigest sha,
            byte[] scratch, short scratchOff) {

        // Use scratch buffer for derived key (20 bytes)
        short derivedKeyOff = scratchOff;
        // KDF needs its own scratch space, so partition the provided scratch buffer
        short kdfScratchOff = (short) (scratchOff + 20); // KDF needs ~60 bytes, so this is fine

        try {
            // Derive key from PIN, writing into scratch at derivedKeyOff
            KDF(pin, pinOffset, pinLen,
                    salt, saltOffset, saltLen,
                    iterations, scratch, derivedKeyOff, sha,
                    scratch, kdfScratchOff);

            // Encrypt MK with derived key
            aesKey.setKey(scratch, derivedKeyOff); // Take first 16 bytes as key
            cipher.init(aesKey, Cipher.MODE_ENCRYPT);
            cipher.doFinal(mkUser, (short) 0, (short) 16, encOut, (short) 0);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F04 | (e.getReason() & 0x0F)));
        } finally {
            // Always clear derived key from scratch
            Util.arrayFillNonAtomic(scratch, derivedKeyOff, (short) 20, (byte) 0);
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
     * @param scratch    Scratch buffer for temporary calculation (must be at least
     *                   80 bytes)
     * @param scratchOff Offset in scratch buffer
     * @return true if successful, false on error
     */
    public static boolean unwrapMasterKeyWithPIN(
            byte[] pin, short pinOffset, short pinLen,
            byte[] salt, short saltOffset, short saltLen,
            short iterations,
            byte[] mkEnc, byte[] mkOut,
            Cipher cipher, AESKey aesKey, MessageDigest sha,
            byte[] scratch, short scratchOff) {

        // Use scratch buffer for derived key (20 bytes)
        short derivedKeyOff = scratchOff;
        // KDF needs its own scratch space, so partition the provided scratch buffer
        short kdfScratchOff = (short) (scratchOff + 20); // KDF needs ~60 bytes, so this is fine

        try {
            // Derive key from PIN, writing into scratch at derivedKeyOff
            KDF(pin, pinOffset, pinLen,
                    salt, saltOffset, saltLen,
                    iterations, scratch, derivedKeyOff, sha,
                    scratch, kdfScratchOff);

            // Decrypt MK with derived key
            aesKey.setKey(scratch, derivedKeyOff); // Take first 16 bytes as key
            cipher.init(aesKey, Cipher.MODE_DECRYPT);
            cipher.doFinal(mkEnc, (short) 0, (short) 16, mkOut, (short) 0);

            return true;

        } catch (CryptoException e) {
            return false;
        } finally {
            Util.arrayFillNonAtomic(scratch, derivedKeyOff, (short) 20, (byte) 0);
        }
    }
}
