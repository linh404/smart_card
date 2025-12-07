package HospitalCard;

import javacard.framework.*;
import javacard.framework.JCSystem;

/**
 * PINHelper - Helper class cho các thao tác liên quan đến PIN
 * Bao gồm: verify PIN, change PIN, reset PIN
 */
public class PINHelper {
    
    /**
     * Xác thực PIN bằng cách so sánh hash
     * 
     * NOTE: Sử dụng pinHash làm buffer tạm để tính toán, sau đó restore lại
     * 
     * @param pin PIN bytes cần verify
     * @param pinOffset offset của PIN
     * @param pinLen độ dài PIN
     * @param pinHash stored PIN hash (20 bytes) - CẢNH BÁO: buffer này sẽ bị overwrite tạm thời
     * @param salt salt để hash PIN (16 bytes)
     * @return true nếu PIN đúng, false nếu sai
     */
    public static boolean verifyPin(byte[] pin, short pinOffset, short pinLen,
                                    byte[] pinHash, byte[] salt) {
        // Tạo transient buffer (RAM) để tính hash
        byte[] computedHash = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
        CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, computedHash);
        
        // So sánh với stored hash
        return (Util.arrayCompare(computedHash, (short)0, pinHash, (short)0, (short)20) == 0);
    }
    
    /**
     * Tạo PIN hash mới từ PIN
     * 
     * @param pin PIN bytes
     * @param pinOffset offset của PIN
     * @param pinLen độ dài PIN
     * @param salt salt để hash (16 bytes)
     * @param hashOut output buffer cho hash (20 bytes)
     */
    public static void createPinHash(byte[] pin, short pinOffset, short pinLen,
                                     byte[] salt, byte[] hashOut) {
        CryptoHelper.hashPin(pin, pinOffset, pinLen, salt, hashOut);
    }
    
    /**
     * Kiểm tra PIN tries và throw exception nếu cần
     * 
     * @param pinTriesRemaining số lần thử còn lại
     * @return số lần thử còn lại sau khi giảm
     */
    public static short checkPinTries(byte pinTriesRemaining) {
        if (pinTriesRemaining == 0) {
            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        }
        return (short)(pinTriesRemaining - 1);
    }
    
    /**
     * Throw exception với số lần thử còn lại
     * 
     * @param pinTriesRemaining số lần thử còn lại
     */
    public static void throwPinTriesException(byte pinTriesRemaining) {
        if (pinTriesRemaining == 0) {
            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        } else {
            ISOException.throwIt((short)(0x63C0 | pinTriesRemaining));
        }
    }
    
    /**
     * Kiểm tra PIN mới có khác PIN cũ không
     * 
     * @param oldPin PIN cũ
     * @param oldPinOffset offset của PIN cũ
     * @param oldPinLen độ dài PIN cũ
     * @param newPin PIN mới
     * @param newPinOffset offset của PIN mới
     * @param newPinLen độ dài PIN mới
     * @return true nếu PIN mới khác PIN cũ
     */
    public static boolean isPinDifferent(byte[] oldPin, short oldPinOffset, short oldPinLen,
                                         byte[] newPin, short newPinOffset, short newPinLen) {
        if (oldPinLen != newPinLen) {
            return true;
        }
        return (Util.arrayCompare(oldPin, oldPinOffset, newPin, newPinOffset, oldPinLen) != 0);
    }
}

