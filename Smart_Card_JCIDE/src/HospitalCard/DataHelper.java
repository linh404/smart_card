package HospitalCard;

import javacard.framework.*;

/**
 * DataHelper - Helper class for read/write data operations
 * Includes: read/write int, parse data from APDU buffer
 */
public class DataHelper {

    /**
     * Read int (4 bytes) from byte array
     * JavaCard doesn't have Util.getInt(), so implement it ourselves
     * 
     * @param bArray byte array
     * @param bOff   offset
     * @return int value
     */
    public static int getInt(byte[] bArray, short bOff) {
        return ((int) (bArray[bOff] & 0xFF) << 24) |
                ((int) (bArray[(short) (bOff + 1)] & 0xFF) << 16) |
                ((int) (bArray[(short) (bOff + 2)] & 0xFF) << 8) |
                ((int) (bArray[(short) (bOff + 3)] & 0xFF));
    }

    /**
     * Write int (4 bytes) to byte array
     * JavaCard doesn't have Util.setInt(), so implement it ourselves
     * 
     * @param bArray byte array
     * @param bOff   offset
     * @param value  int value
     */
    public static void setInt(byte[] bArray, short bOff, int value) {
        bArray[bOff] = (byte) (value >> 24);
        bArray[(short) (bOff + 1)] = (byte) (value >> 16);
        bArray[(short) (bOff + 2)] = (byte) (value >> 8);
        bArray[(short) (bOff + 3)] = (byte) value;
    }

    /**
     * Parse data from APDU buffer with format: len(2) + data
     * 
     * @param buf     buffer
     * @param offset  current offset
     * @param dataOut output buffer for data
     * @return new offset after parsing
     */
    public static short parseDataWithLength(byte[] buf, short offset, byte[] dataOut) {
        short dataLen = Util.getShort(buf, offset);
        offset += 2;
        Util.arrayCopy(buf, offset, dataOut, (short) 0, dataLen);
        return (short) (offset + dataLen);
    }

    /**
     * Parse PIN from APDU buffer with format: len(2) + PIN
     * 
     * @param buf    buffer
     * @param offset current offset
     * @param pinOut output buffer for PIN
     * @return new offset and PIN length in pinOut[0] (temporarily use different
     *         method)
     */
    public static short parsePin(byte[] buf, short offset, byte[] pinOut) {
        short pinLen = Util.getShort(buf, offset);
        offset += 2;
        if (pinLen > pinOut.length) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        Util.arrayCopy(buf, offset, pinOut, (short) 0, pinLen);
        return offset;
    }

    /**
     * Update transaction logs with new transaction
     * 
     * Format per log entry: type(1) | amount(4) | balance(4) | txnCounter(2) = 11
     * bytes
     * Store up to 10 most recent transactions (circular buffer)
     * 
     * @param logsEnc    encrypted logs buffer (max 128 bytes encrypted)
     * @param logsLen    current logs length
     * @param type       transaction type (0x01 = credit, 0x02 = debit)
     * @param amount     transaction amount
     * @param balance    new balance after transaction
     * @param txnCounter transaction counter
     * @param mkUser     master key for encryption
     * @param cipher     AES cipher instance
     * @param aesKey     AES key object
     * @return new logs length after encryption
     */
    public static short updateLogs(byte[] logsEnc, short logsLen,
            byte type, int amount, int balance,
            short txnCounter,
            byte[] mkUser, javacardx.crypto.Cipher cipher,
            javacard.security.AESKey aesKey) {
        final short MAX_LOGS = 10;
        final short LOG_ENTRY_SIZE = 11; // type(1) + amount(4) + balance(4) + counter(2)
        final short LOG_BUFFER_SIZE = (short) (MAX_LOGS * LOG_ENTRY_SIZE); // 110 bytes

        // Create transient buffer for processing logs
        byte[] logs = JCSystem.makeTransientByteArray(LOG_BUFFER_SIZE,
                JCSystem.CLEAR_ON_DESELECT);

        try {
            // Decrypt existing logs (if any)
            short currentLogsLen = 0;
            if (logsLen > 0) {
                aesKey.setKey(mkUser, (short) 0);
                cipher.init(aesKey, javacardx.crypto.Cipher.MODE_DECRYPT);
                currentLogsLen = cipher.doFinal(logsEnc, (short) 0, logsLen, logs, (short) 0);

                // Remove padding
                while (currentLogsLen > 0 && logs[(short) (currentLogsLen - 1)] == 0) {
                    currentLogsLen--;
                }
            }

            // Calculate current number of entries
            short currentEntries = (short) (currentLogsLen / LOG_ENTRY_SIZE);

            // If already have 10 entries, remove oldest entry (shift left)
            if (currentEntries >= MAX_LOGS) {
                // Move entries 1-9 to positions 0-8
                Util.arrayCopyNonAtomic(logs, LOG_ENTRY_SIZE,
                        logs, (short) 0,
                        (short) ((MAX_LOGS - 1) * LOG_ENTRY_SIZE));
                currentEntries = (short) (MAX_LOGS - 1);
            }

            // Add new entry at the end
            short newEntryOffset = (short) (currentEntries * LOG_ENTRY_SIZE);

            logs[newEntryOffset++] = type; // 1 byte: type
            setInt(logs, newEntryOffset, amount); // 4 bytes: amount
            newEntryOffset += 4;
            setInt(logs, newEntryOffset, balance); // 4 bytes: balance
            newEntryOffset += 4;
            Util.setShort(logs, newEntryOffset, txnCounter); // 2 bytes: counter
            newEntryOffset += 2;

            // Calculate new logs length
            short newLogsLen = (short) ((currentEntries + 1) * LOG_ENTRY_SIZE);

            // Pad to multiple of 16 (AES block size)
            short paddedLen = (short) ((newLogsLen + 15) / 16 * 16);
            byte[] paddedLogs = JCSystem.makeTransientByteArray(paddedLen,
                    JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(logs, (short) 0, paddedLogs, (short) 0, newLogsLen);

            // Encrypt new logs
            aesKey.setKey(mkUser, (short) 0);
            cipher.init(aesKey, javacardx.crypto.Cipher.MODE_ENCRYPT);
            short encLen = cipher.doFinal(paddedLogs, (short) 0, paddedLen, logsEnc, (short) 0);

            // Cleanup
            Util.arrayFillNonAtomic(logs, (short) 0, LOG_BUFFER_SIZE, (byte) 0);
            Util.arrayFillNonAtomic(paddedLogs, (short) 0, paddedLen, (byte) 0);

            return encLen;

        } catch (Exception e) {
            // If error, return old logs (no update)
            return logsLen;
        }
    }
}
