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
     * @deprecated UNUSED - Use hashPinSimple() instead
     *             This method was designed with salt support but is never called
     */
    /*
     * public static boolean verifyPin(byte[] pin, short pinOffset, short pinLen,
     * byte[] pinHash, byte[] salt) {
     * // Create transient buffer (RAM) for hash calculation
     * byte[] computedHash = JCSystem.makeTransientByteArray((short) 20,
     * JCSystem.CLEAR_ON_DESELECT);
     * CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, computedHash);
     * 
     * // Compare with stored hash
     * return (Util.arrayCompare(computedHash, (short) 0, pinHash, (short) 0,
     * (short) 20) == 0);
     * }
     */

    /**
     * @deprecated UNUSED - Use hashPinSimple() instead
     */
    /*
     * public static void createPinHash(byte[] pin, short pinOffset, short pinLen,
     * byte[] salt, byte[] hashOut) {
     * CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, hashOut);
     * }
     */

    /**
     * @deprecated UNUSED - Retry counter handled in verifyPinWithRetry()
     */
    /*
     * public static short checkPinTries(byte pinTriesRemaining) {
     * if (pinTriesRemaining == 0) {
     * ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
     * }
     * return (short) (pinTriesRemaining - 1);
     * }
     */

    /**
     * @deprecated UNUSED - Exception throwing handled in verifyPinWithRetry()
     */
    /*
     * public static void throwPinTriesException(byte pinTriesRemaining) {
     * if (pinTriesRemaining == 0) {
     * ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
     * } else {
     * ISOException.throwIt((short) (0x63C0 | pinTriesRemaining));
     * }
     * }
     */

    /**
     * @deprecated UNUSED - Comparison logic handled inline where needed
     */
    /*
     * public static boolean isPinDifferent(byte[] oldPin, short oldPinOffset, short
     * oldPinLen,
     * byte[] newPin, short newPinOffset, short newPinLen) {
     * if (oldPinLen != newPinLen) {
     * return true;
     * }
     * return (Util.arrayCompare(oldPin, oldPinOffset, newPin, newPinOffset,
     * oldPinLen) != 0);
     * }
     */

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

    // ==================== NEW METHODS FOR CLEAN ARCHITECTURE ====================

    /**
     * Hash PIN without salt (simple SHA-1 hashing)
     * Replaces direct safeHash() calls for PIN hashing
     * 
     * Current implementation: SHA-1(PIN) without salt
     * 
     * @param pin       PIN bytes
     * @param pinOffset PIN offset
     * @param pinLen    PIN length
     * @param hashOut   output buffer for hash (20 bytes)
     * @param sha       MessageDigest instance
     * @throws ISOException 0x6F01 if hash fails
     */
    public static void hashPinSimple(byte[] pin, short pinOffset, short pinLen,
            byte[] hashOut, MessageDigest sha) {
        safeHash(sha, pin, pinOffset, pinLen, hashOut, (short) 0);
    }

    /**
     * Verify PIN and compute updated counter values
     * Returns counter values WITHOUT throwing - caller must update instance vars
     * and throw
     * 
     * @param pin                 PIN bytes to verify
     * @param pinOffset           PIN offset
     * @param pinLen              PIN length
     * @param storedPinHash       stored PIN hash (20 bytes)
     * @param currentRetryCounter current retry counter
     * @param sha                 MessageDigest instance
     * @param tempHashBuffer      temp buffer (20 bytes)
     * @return byte[2]: [0]=newRetryCounter, [1]=newBlockedFlag
     *         Returns null if PIN correct (caller should reset counter to MAX)
     * @throws ISOException 0x6F01 if hash fails
     */
    public static byte[] verifyPinAndComputeCounters(
            byte[] pin, short pinOffset, short pinLen,
            byte[] storedPinHash,
            byte currentRetryCounter,
            MessageDigest sha,
            byte[] tempHashBuffer) {

        // Hash input PIN
        safeHash(sha, pin, pinOffset, pinLen, tempHashBuffer, (short) 0);

        // Compare
        if (Util.arrayCompare(tempHashBuffer, (short) 0, storedPinHash, (short) 0, (short) 20) != 0) {
            // PIN incorrect - calculate new values
            byte newRetryCounter = (byte) (currentRetryCounter - 1);
            byte newBlockedFlag = (byte) (newRetryCounter == 0 ? 1 : 0);

            byte[] result = JCSystem.makeTransientByteArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
            result[0] = newRetryCounter;
            result[1] = newBlockedFlag;
            return result;
        }

        // PIN correct
        return null;
    }

    /**
     * Validate PIN change: verify old PIN, hash new PIN, check difference
     * Returns updated counter values WITHOUT throwing
     * 
     * @param oldPin              old PIN bytes
     * @param oldPinOffset        old PIN offset
     * @param oldPinLen           old PIN length
     * @param newPin              new PIN bytes
     * @param newPinOffset        new PIN offset
     * @param newPinLen           new PIN length
     * @param storedPinHash       stored PIN hash
     * @param newPinHashOut       output for new PIN hash (20 bytes)
     * @param currentRetryCounter current retry counter
     * @param sha                 MessageDigest instance
     * @param tempBuffer          temp buffer (20 bytes)
     * @return byte[2]: [0]=newRetryCounter, [1]=newBlockedFlag
     *         Returns null if old PIN correct and new PIN different
     * @throws ISOException 0x6A80 if new PIN same as old
     * @throws ISOException 0x6F01 if hash fails
     */
    public static byte[] validatePinChangeAndComputeCounters(
            byte[] oldPin, short oldPinOffset, short oldPinLen,
            byte[] newPin, short newPinOffset, short newPinLen,
            byte[] storedPinHash,
            byte[] newPinHashOut,
            byte currentRetryCounter,
            MessageDigest sha,
            byte[] tempBuffer) {

        // Hash and verify old PIN
        safeHash(sha, oldPin, oldPinOffset, oldPinLen, tempBuffer, (short) 0);

        if (Util.arrayCompare(tempBuffer, (short) 0, storedPinHash, (short) 0, (short) 20) != 0) {
            // Old PIN wrong - compute new counters
            byte newRetryCounter = (byte) (currentRetryCounter - 1);
            byte newBlockedFlag = (byte) (newRetryCounter == 0 ? 1 : 0);

            byte[] result = JCSystem.makeTransientByteArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
            result[0] = newRetryCounter;
            result[1] = newBlockedFlag;
            return result;
        }

        // Hash new PIN
        safeHash(sha, newPin, newPinOffset, newPinLen, newPinHashOut, (short) 0);

        // Check new PIN different from old
        if (Util.arrayCompare(newPinHashOut, (short) 0, storedPinHash, (short) 0, (short) 20) == 0) {
            ISOException.throwIt((short) 0x6A80); // New PIN same as old
        }

        // Success
        return null;
    }

    // ==================== DEPRECATED METHODS ====================
    // The following methods are kept for reference but marked as deprecated
    // They are not used in the current implementation

    /**
     * @deprecated UNUSED - Use hashPinSimple() instead
     *             This method was designed with salt support but is never called
     */
    /*
     * public static boolean verifyPin(byte[] pin, short pinOffset, short pinLen,
     * byte[] pinHash, byte[] salt) {
     * byte[] computedHash = JCSystem.makeTransientByteArray((short) 20,
     * JCSystem.CLEAR_ON_DESELECT);
     * CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, computedHash);
     * return (Util.arrayCompare(computedHash, (short) 0, pinHash, (short) 0,
     * (short) 20) == 0);
     * }
     */

    /**
     * @deprecated UNUSED - Use hashPinSimple() instead
     */
    /*
     * public static void createPinHash(byte[] pin, short pinOffset, short pinLen,
     * byte[] salt, byte[] hashOut) {
     * CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, hashOut);
     * }
     */

    /**
     * @deprecated UNUSED - Retry counter handled in verifyPinWithRetry()
     */
    /*
     * public static short checkPinTries(byte pinTriesRemaining) {
     * if (pinTriesRemaining == 0) {
     * ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
     * }
     * return (short) (pinTriesRemaining - 1);
     * }
     */

    /**
     * @deprecated UNUSED - Exception throwing handled in verifyPinWithRetry()
     */
    /*
     * public static void throwPinTriesException(byte pinTriesRemaining) {
     * if (pinTriesRemaining == 0) {
     * ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
     * } else {
     * ISOException.throwIt((short) (0x63C0 | pinTriesRemaining));
     * }
     * }
     */

    /**
     * @deprecated UNUSED - Comparison logic handled inline where needed
     */
    /*
     * public static boolean isPinDifferent(byte[] oldPin, short oldPinOffset, short
     * oldPinLen,
     * byte[] newPin, short newPinOffset, short newPinLen) {
     * if (oldPinLen != newPinLen) {
     * return true;
     * }
     * return (Util.arrayCompare(oldPin, oldPinOffset, newPin, newPinOffset,
     * oldPinLen) != 0);
     * }
     */
}
