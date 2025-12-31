package HospitalCard;

import javacard.framework.*;
import javacard.framework.JCSystem;
import javacard.security.MessageDigest;
import javacard.security.CryptoException;

/**
 * PINHelper - Helper class for PIN-related operations
 * Includes: verify PIN, change PIN, reset PIN
 */
public class PINHelper {

    /**
     * Verify PIN by comparing hash
     * 
     * NOTE: Uses pinHash as temporary buffer for calculation, then restores it
     * 
     * @param pin       PIN bytes to verify
     * @param pinOffset PIN offset
     * @param pinLen    PIN length
     * @param pinHash   stored PIN hash (20 bytes) - WARNING: this buffer will be
     *                  temporarily overwritten
     * @param salt      salt for hashing PIN (16 bytes)
     * @return true if PIN is correct, false if incorrect
     */
    public static boolean verifyPin(byte[] pin, short pinOffset, short pinLen,
            byte[] pinHash, byte[] salt) {
        // Create transient buffer (RAM) for hash calculation
        byte[] computedHash = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);
        CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, computedHash);

        // Compare with stored hash
        return (Util.arrayCompare(computedHash, (short) 0, pinHash, (short) 0, (short) 20) == 0);
    }

    /**
     * Create new PIN hash from PIN
     * 
     * @param pin       PIN bytes
     * @param pinOffset PIN offset
     * @param pinLen    PIN length
     * @param salt      salt for hashing (16 bytes)
     * @param hashOut   output buffer for hash (20 bytes)
     */
    public static void createPinHash(byte[] pin, short pinOffset, short pinLen,
            byte[] salt, byte[] hashOut) {
        CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, hashOut);
    }

    /**
     * Check PIN tries and throw exception if needed
     * 
     * @param pinTriesRemaining number of remaining tries
     * @return remaining tries after decrement
     */
    public static short checkPinTries(byte pinTriesRemaining) {
        if (pinTriesRemaining == 0) {
            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        }
        return (short) (pinTriesRemaining - 1);
    }

    /**
     * Throw exception with remaining tries count
     * 
     * @param pinTriesRemaining number of remaining tries
     */
    public static void throwPinTriesException(byte pinTriesRemaining) {
        if (pinTriesRemaining == 0) {
            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        } else {
            ISOException.throwIt((short) (0x63C0 | pinTriesRemaining));
        }
    }

    /**
     * Check if new PIN is different from old PIN
     * 
     * @param oldPin       old PIN
     * @param oldPinOffset old PIN offset
     * @param oldPinLen    old PIN length
     * @param newPin       new PIN
     * @param newPinOffset new PIN offset
     * @param newPinLen    new PIN length
     * @return true if new PIN is different from old PIN
     */
    public static boolean isPinDifferent(byte[] oldPin, short oldPinOffset, short oldPinLen,
            byte[] newPin, short newPinOffset, short newPinLen) {
        if (oldPinLen != newPinLen) {
            return true;
        }
        return (Util.arrayCompare(oldPin, oldPinOffset, newPin, newPinOffset, oldPinLen) != 0);
    }

    /**
     * Safe hash with automatic error handling
     * Eliminates repetitive try-catch blocks in code
     * 
     * @param sha    MessageDigest instance
     * @param input  data to hash
     * @param inOff  input offset
     * @param inLen  input length
     * @param output buffer for hash output
     * @param outOff output offset
     * @throws ISOException 0x6F01 if hash fails
     */
    public static void safeHash(MessageDigest sha,
            byte[] input, short inOff, short inLen,
            byte[] output, short outOff) {
        try {
            sha.reset();
            sha.doFinal(input, inOff, inLen, output, outOff);
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F01);
        }
    }
}
