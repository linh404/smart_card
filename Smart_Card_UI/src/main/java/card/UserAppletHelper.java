package card;

import javax.smartcardio.ResponseAPDU;

/**
 * UserAppletHelper - Class helper cho UserApplet
 * Cung cấp các phương thức tiện ích để làm việc với UserApplet
 */
public class UserAppletHelper {
    
    private APDUCommands apduCommands;
    
    public UserAppletHelper(APDUCommands apduCommands) {
        this.apduCommands = apduCommands;
    }
    
    /**
     * Kiểm tra cardId có rỗng không (toàn số 0)
     */
    private boolean isCardIdEmpty(byte[] cardId) {
        if (cardId == null || cardId.length == 0) {
            return true;
        }
        for (int i = 0; i < cardId.length; i++) {
            if (cardId[i] != 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Kiểm tra thẻ User đã được phát hành chưa (có cardId_user chưa)
     * @return cardId_user nếu có, null nếu chưa có
     */
    public byte[] getCardId() {
        try {
            System.out.println("[UserAppletHelper] getCardId: Đang đọc cardId_user...");
            byte[] cardId = apduCommands.getCardId();
            if (cardId != null && cardId.length > 0 && !isCardIdEmpty(cardId)) {
                System.out.println("[UserAppletHelper] getCardId: Thẻ đã có cardId_user = " + bytesToHex(cardId));
                return cardId;
            } else {
                System.out.println("[UserAppletHelper] getCardId: Thẻ chưa có cardId_user (chưa được phát hành)");
                return null;
            }
        } catch (Exception e) {
            System.err.println("[UserAppletHelper] getCardId: Lỗi - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Kiểm tra trạng thái thẻ User
     * @return true nếu thẻ đã được phát hành (có cardId_user hợp lệ)
     */
    public boolean isCardIssued() {
        byte[] cardId = getCardId();
        return cardId != null && cardId.length > 0 && !isCardIdEmpty(cardId);
    }
    
    /**
     * V3: Không còn method riêng để đọc UserData
     * Dùng verifyPinAndReadData() thay thế
     */
    @Deprecated
    public byte[] readUserData() {
        System.err.println("[UserAppletHelper] readUserData() is deprecated in V3. Use verifyPinAndReadData() instead.");
        return null;
    }

    /**
     * V3: Không còn method riêng để đọc balance
     * Balance được lưu trong UserData, dùng verifyPinAndReadData() để lấy
     */
    @Deprecated
    public int getBalance() {
        System.err.println("[UserAppletHelper] getBalance() is deprecated in V3. Balance is in UserData.");
        return -1;
    }

    /**
     * V3: Không còn method riêng để verify PIN
     * Dùng verifyPinAndReadData() thay thế
     */
    @Deprecated
    public boolean verifyPin(byte[] pinPlaintext) {
        System.err.println("[UserAppletHelper] verifyPin() is deprecated in V3. Use verifyPinAndReadData() instead.");
        return false;
    }

    /**
     * V3: Reset card không còn được hỗ trợ
     */
    @Deprecated
    public boolean resetCard(byte[] pinAdmin) {
        System.err.println("[UserAppletHelper] resetCard() is deprecated in V3. Not supported.");
        return false;
    }
    
    /**
     * Lấy thông tin tổng quan về thẻ User
     * @return String mô tả trạng thái thẻ
     */
    public String getCardInfo() {
        StringBuilder info = new StringBuilder();
        
        byte[] cardId = getCardId();
        if (cardId != null && cardId.length > 0) {
            info.append("Card ID: ").append(bytesToHex(cardId)).append("\n");
            info.append("V3: Dùng verifyPinAndReadData() để lấy UserData và balance\n");
        } else {
            info.append("Thẻ chưa được phát hành (chưa có cardId_user)\n");
        }
        
        return info.toString();
    }
    
    /**
     * Helper method để chuyển byte[] sang hex string
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}

