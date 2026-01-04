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
}
