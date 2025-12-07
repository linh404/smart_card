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
     * @param bOff offset
     * @return int value
     */
    public static int getInt(byte[] bArray, short bOff) {
        return ((int)(bArray[bOff] & 0xFF) << 24) |
               ((int)(bArray[(short)(bOff + 1)] & 0xFF) << 16) |
               ((int)(bArray[(short)(bOff + 2)] & 0xFF) << 8) |
               ((int)(bArray[(short)(bOff + 3)] & 0xFF));
    }
    
    /**
     * Ghi int (4 bytes) vào byte array
     * JavaCard không có Util.setInt(), nên tự implement
     * 
     * @param bArray byte array
     * @param bOff offset
     * @param value int value
     */
    public static void setInt(byte[] bArray, short bOff, int value) {
        bArray[bOff] = (byte)(value >> 24);
        bArray[(short)(bOff + 1)] = (byte)(value >> 16);
        bArray[(short)(bOff + 2)] = (byte)(value >> 8);
        bArray[(short)(bOff + 3)] = (byte)value;
    }
    
    /**
     * Parse data từ APDU buffer với format: len(2) + data
     * 
     * @param buf buffer
     * @param offset offset hiện tại
     * @param dataOut output buffer cho data
     * @return offset mới sau khi parse
     */
    public static short parseDataWithLength(byte[] buf, short offset, byte[] dataOut) {
        short dataLen = Util.getShort(buf, offset);
        offset += 2;
        Util.arrayCopy(buf, offset, dataOut, (short)0, dataLen);
        return (short)(offset + dataLen);
    }
    
    /**
     * Parse PIN từ APDU buffer với format: len(2) + PIN
     * 
     * @param buf buffer
     * @param offset offset hiện tại
     * @param pinOut output buffer cho PIN
     * @return new offset and PIN length in pinOut[0] (temporarily use different method)
     */
    public static short parsePin(byte[] buf, short offset, byte[] pinOut) {
        short pinLen = Util.getShort(buf, offset);
        offset += 2;
        if (pinLen > pinOut.length) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        Util.arrayCopy(buf, offset, pinOut, (short)0, pinLen);
        return offset;
    }
    
    /**
     * Cập nhật logs (stub - chưa implement đầy đủ)
     * Format: timestamp|type|amount|balance\n
     * 
     * @param logsEnc encrypted logs buffer
     * @param logsLen current logs length
     * @param type transaction type (0x01 = CREDIT, 0x02 = DEBIT)
     * @param amount transaction amount
     * @param balance new balance
     * @param mkUser master key để mã hóa
     * @param cipher AES cipher
     * @param aesKey AES key
     * @return new logs length
     */
    public static short updateLogs(byte[] logsEnc, short logsLen,
                                   byte type, int amount, int balance,
                                   byte[] mkUser, javacardx.crypto.Cipher cipher, 
                                   javacard.security.AESKey aesKey) {
        return logsLen;
    }
}

