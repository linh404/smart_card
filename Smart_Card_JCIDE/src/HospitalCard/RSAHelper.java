package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * RSAHelper - Helper class for RSA operations
 * Includes: sign, verify, generate key pair
 */
public class RSAHelper {

    /**
     * Sign data using RSA private key
     * 
     * @param cipher       RSA cipher instance
     * @param privKey      RSA private key
     * @param data         data to sign
     * @param dataOffset   data offset
     * @param dataLen      data length
     * @param signatureOut output buffer for signature
     * @param sigOffset    signature offset
     * @return signature length
     */
    public static short sign(Cipher cipher, RSAPrivateKey privKey,
            byte[] data, short dataOffset, short dataLen,
            byte[] signatureOut, short sigOffset) {
        if (privKey == null || !privKey.isInitialized()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }

        try {
            cipher.init(privKey, Cipher.MODE_ENCRYPT); // RSA sign = encrypt with private key
            return cipher.doFinal(data, dataOffset, dataLen, signatureOut, sigOffset);
        } catch (Exception e) {
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
            return 0; // Never reaches here
        }
    }

    /**
     * Get RSA public key (modulus + exponent)
     * 
     * @param pubKey RSA public key
     * @param buf    buffer to store result
     * @param offset offset in buffer
     * @return total length (modLen + expLen + 4 bytes for 2 length fields)
     */
    public static short getPublicKeyBytes(RSAPublicKey pubKey, byte[] buf, short offset) {
        if (pubKey == null || !pubKey.isInitialized()) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            return 0;
        }

        // Read modulus into offset + 2 (reserve first 2 bytes for modLen)
        short modLen = pubKey.getModulus(buf, (short) (offset + 2));
        short expOffset = (short) (offset + 2 + modLen);

        // Read exponent into expOffset + 2 (reserve 2 bytes for expLen)
        short expLen = pubKey.getExponent(buf, (short) (expOffset + 2));

        // Write modLen and expLen
        Util.setShort(buf, offset, modLen);
        Util.setShort(buf, expOffset, expLen);

        // Return total length: modLen(2) + modulus + expLen(2) + exponent
        return (short) (4 + modLen + expLen);
    }

    /**
     * Initialize RSA key pair
     * 
     * @param keyPair RSA key pair
     * @return true if successful
     */
    public static boolean generateKeyPair(KeyPair keyPair) {
        if (keyPair == null) {
            return false;
        }

        try {
            if (!((RSAPrivateKey) keyPair.getPrivate()).isInitialized()) {
                keyPair.genKeyPair();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
