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
     * @deprecated UNUSED - No code uses this parsing method
     */
    /*
     * public static short parseDataWithLength(byte[] buf, short offset, byte[]
     * dataOut) {
     * short dataLen = Util.getShort(buf, offset);
     * offset += 2;
     * Util.arrayCopy(buf, offset, dataOut, (short) 0, dataLen);
     * return (short) (offset + dataLen);
     * }
     */

    /**
     * @deprecated UNUSED - No code uses this parsing method
     */
    /*
     * public static short parsePin(byte[] buf, short offset, byte[] pinOut) {
     * short pinLen = Util.getShort(buf, offset);
     * offset += 2;
     * if (pinLen > pinOut.length) {
     * ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
     * }
     * Util.arrayCopy(buf, offset, pinOut, (short) 0, pinLen);
     * return offset;
     * }
     */

    /**
     * @deprecated NOT IMPLEMENTED - Transaction logs feature not implemented
     *             Method exists but is never called
     */
    /*
     * public static short updateLogs(byte[] logsEnc, short logsLen,
     * byte type, int amount, int balance,
     * short txnCounter,
     * byte[] mkUser, javacardx.crypto.Cipher cipher,
     * javacard.security.AESKey aesKey) {
     * final short MAX_LOGS = 10;
     * final short LOG_ENTRY_SIZE = 11;
     * final short LOG_BUFFER_SIZE = (short) (MAX_LOGS * LOG_ENTRY_SIZE);
     * 
     * byte[] logs = JCSystem.makeTransientByteArray(LOG_BUFFER_SIZE,
     * JCSystem.CLEAR_ON_DESELECT);
     * 
     * try {
     * short currentLogsLen = 0;
     * if (logsLen > 0) {
     * aesKey.setKey(mkUser, (short) 0);
     * cipher.init(aesKey, javacardx.crypto.Cipher.MODE_DECRYPT);
     * currentLogsLen = cipher.doFinal(logsEnc, (short) 0, logsLen, logs, (short)
     * 0);
     * 
     * while (currentLogsLen > 0 && logs[(short) (currentLogsLen - 1)] == 0) {
     * currentLogsLen--;
     * }
     * }
     * 
     * short currentEntries = (short) (currentLogsLen / LOG_ENTRY_SIZE);
     * 
     * if (currentEntries >= MAX_LOGS) {
     * Util.arrayCopyNonAtomic(logs, LOG_ENTRY_SIZE,
     * logs, (short) 0,
     * (short) ((MAX_LOGS - 1) * LOG_ENTRY_SIZE));
     * currentEntries = (short) (MAX_LOGS - 1);
     * }
     * 
     * short newEntryOffset = (short) (currentEntries * LOG_ENTRY_SIZE);
     * 
     * logs[newEntryOffset++] = type;
     * setInt(logs, newEntryOffset, amount);
     * newEntryOffset += 4;
     * setInt(logs, newEntryOffset, balance);
     * newEntryOffset += 4;
     * Util.setShort(logs, newEntryOffset, txnCounter);
     * newEntryOffset += 2;
     * 
     * short newLogsLen = (short) ((currentEntries + 1) * LOG_ENTRY_SIZE);
     * 
     * short paddedLen = (short) ((newLogsLen + 15) / 16 * 16);
     * byte[] paddedLogs = JCSystem.makeTransientByteArray(paddedLen,
     * JCSystem.CLEAR_ON_DESELECT);
     * Util.arrayCopyNonAtomic(logs, (short) 0, paddedLogs, (short) 0, newLogsLen);
     * 
     * aesKey.setKey(mkUser, (short) 0);
     * cipher.init(aesKey, javacardx.crypto.Cipher.MODE_ENCRYPT);
     * short encLen = cipher.doFinal(paddedLogs, (short) 0, paddedLen, logsEnc,
     * (short) 0);
     * 
     * Util.arrayFillNonAtomic(logs, (short) 0, LOG_BUFFER_SIZE, (byte) 0);
     * Util.arrayFillNonAtomic(paddedLogs, (short) 0, paddedLen, (byte) 0);
     * 
     * return encLen;
     * 
     * } catch (Exception e) {
     * return logsLen;
     * }
     * }
     */
}
