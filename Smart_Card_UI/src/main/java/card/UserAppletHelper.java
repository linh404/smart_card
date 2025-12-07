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
     * Đọc thông tin User từ thẻ (UserData)
     * @return UserData dạng byte[] hoặc null nếu lỗi
     */
    public byte[] readUserData() {
        try {
            System.out.println("[UserAppletHelper] readUserData: Đang đọc UserData...");
            byte[] userData = apduCommands.getUserData();
            if (userData != null) {
                System.out.println("[UserAppletHelper] readUserData: Đã đọc được UserData, length = " + userData.length);
                return userData;
            } else {
                System.out.println("[UserAppletHelper] readUserData: Không đọc được UserData");
                return null;
            }
        } catch (javax.smartcardio.CardException e) {
            System.err.println("[UserAppletHelper] readUserData: CardException - " + e.getMessage());
            System.err.println("[UserAppletHelper] Cần xác thực PIN User trước khi đọc UserData");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("[UserAppletHelper] readUserData: Lỗi - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Đọc số dư từ thẻ
     * @return Số dư hoặc -1 nếu lỗi
     */
    public int getBalance() {
        try {
            System.out.println("[UserAppletHelper] getBalance: Đang đọc số dư...");
            int balance = apduCommands.getBalance();
            if (balance >= 0) {
                System.out.println("[UserAppletHelper] getBalance: Số dư = " + balance);
                return balance;
            } else {
                System.out.println("[UserAppletHelper] getBalance: Không đọc được số dư");
                return -1;
            }
        } catch (Exception e) {
            System.err.println("[UserAppletHelper] getBalance: Lỗi - " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }
    
    /**
     * Kiểm tra PIN User có đúng không
     * @param pinPlaintext PIN dạng plaintext
     * @return true nếu PIN đúng
     */
    public boolean verifyPin(byte[] pinPlaintext) {
        try {
            System.out.println("[UserAppletHelper] verifyPin: Đang xác thực PIN User...");
            boolean result = apduCommands.verifyPinUser(pinPlaintext);
            if (result) {
                System.out.println("[UserAppletHelper] verifyPin: PIN đúng");
            } else {
                System.out.println("[UserAppletHelper] verifyPin: PIN sai");
            }
            return result;
        } catch (Exception e) {
            System.err.println("[UserAppletHelper] verifyPin: Lỗi - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Reset toàn bộ thẻ User (xóa tất cả dữ liệu)
     * @param pinAdmin PIN Admin để xác thực
     * @return true nếu reset thành công
     */
    public boolean resetCard(byte[] pinAdmin) {
        try {
            System.out.println("[UserAppletHelper] resetCard: Bắt đầu reset thẻ User...");
            boolean result = apduCommands.resetUserCard(pinAdmin);
            if (result) {
                System.out.println("[UserAppletHelper] resetCard: Reset thẻ thành công");
            } else {
                System.err.println("[UserAppletHelper] resetCard: Reset thẻ thất bại");
            }
            return result;
        } catch (Exception e) {
            System.err.println("[UserAppletHelper] resetCard: Lỗi - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
            
            int balance = getBalance();
            if (balance >= 0) {
                info.append("Số dư: ").append(balance).append("\n");
            } else {
                info.append("Số dư: Không đọc được\n");
            }
            
            byte[] userData = readUserData();
            if (userData != null && userData.length > 0) {
                info.append("UserData: Có dữ liệu (").append(userData.length).append(" bytes)\n");
            } else {
                info.append("UserData: Không có hoặc không đọc được\n");
            }
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

