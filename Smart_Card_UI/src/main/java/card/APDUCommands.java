package card;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * APDUCommands - Định nghĩa và xử lý các lệnh APDU
 * Tham khảo từ SmartCard_Old, mở rộng cho AdminApplet và UserApplet
 */
public class APDUCommands {

    private CardChannel channel;
    public static final byte CLA_APPLET = (byte)0x80;

    // ================== BẢNG MÃ LỆNH (INS) CHO ADMIN APPLET ==================
    public static final byte INS_VERIFY_PIN_ADMIN = (byte)0x10; // Nhận PIN plaintext, hash và verify trên thẻ
    public static final byte INS_SIGN_CHALLENGE_ADMIN = (byte)0x11;
    public static final byte INS_GEN_RESET_TOKEN = (byte)0x12; // Sinh token reset PIN User
    public static final byte INS_INIT_ADMIN_CARD = (byte)0x14; // Khởi tạo thẻ Admin: set PIN hash và salt
    public static final byte INS_GET_PUBLIC_KEY = (byte)0x15; // Lấy PK_admin từ thẻ

    // ================== BẢNG MÃ LỆNH (INS) CHO USER APPLET ==================
    // V3 - New instruction codes
    public static final byte INS_GET_STATUS = (byte)0x01; // V3: Get card status
    public static final byte INS_ISSUE_CARD = (byte)0x02; // V3: Issue new card
    public static final byte INS_VERIFY_PIN_AND_READ_DATA = (byte)0x03; // V3: Verify PIN and read patient data
    public static final byte INS_UPDATE_PATIENT_DATA = (byte)0x04; // V3: Update patient data
    public static final byte INS_ADMIN_RESET_PIN = (byte)0x05; // V3: Admin reset PIN
    
    // V2 - Deprecated (kept for backward compatibility)
    @Deprecated
    public static final byte INS_VERIFY_PIN_USER = (byte)0x20; // V2: Deprecated
    @Deprecated
    public static final byte INS_SIGN_CHALLENGE_USER = (byte)0x21; // V2: Deprecated
    @Deprecated
    public static final byte INS_ISSUE_CARD_V2 = (byte)0x30; // V2: Deprecated
    @Deprecated
    public static final byte INS_GET_CARD_ID = (byte)0x40; // V2: Deprecated - Use GET_STATUS instead
    @Deprecated
    public static final byte INS_GET_USER_DATA = (byte)0x41; // V2: Deprecated - Use VERIFY_PIN_AND_READ_DATA instead
    @Deprecated
    public static final byte INS_GET_BALANCE = (byte)0x42; // V2: Deprecated
    @Deprecated
    public static final byte INS_GET_LOGS = (byte)0x43; // V2: Deprecated
    @Deprecated
    public static final byte INS_GET_BHYT = (byte)0x44; // V2: Deprecated
    @Deprecated
    public static final byte INS_CREDIT = (byte)0x50; // V2: Deprecated
    @Deprecated
    public static final byte INS_DEBIT = (byte)0x51; // V2: Deprecated
    @Deprecated
    public static final byte INS_CHANGE_PIN = (byte)0xA0; // V2: Deprecated
    @Deprecated
    public static final byte INS_RESET_PIN_BY_ADMIN = (byte)0xA2; // V2: Deprecated - Use INS_ADMIN_RESET_PIN instead
    @Deprecated
    public static final byte INS_UPDATE_USER_DATA = (byte)0xB0; // V2: Deprecated - Use INS_UPDATE_PATIENT_DATA instead
    @Deprecated
    public static final byte INS_RESET_USER_CARD = (byte)0x80; // V2: Deprecated
 
    // ================== AID CỦA CÁC APPLET ==================
    // Package chung: HospitalCard
    // Package AID: 11 22 33 44 55 (5 bytes)
    
    // AdminApplet AID: 11 22 33 44 55 00 (6 bytes)
    public static final byte[] AID_ADMIN = {
        (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55, (byte)0x00
    };
    
    // UserApplet AID: 11 22 33 44 55 01 (6 bytes)
    public static final byte[] AID_USER = {
        (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55, (byte)0x01
    };

    public APDUCommands(CardChannel channel) {
        this.channel = channel;
    }
    
    /**
     * Cập nhật CardChannel (dùng khi channel thay đổi sau khi connect/selectApplet)
     */
    public void setChannel(CardChannel channel) {
        this.channel = channel;
    }
    
    /**
     * Kiểm tra channel có null không
     */
    public boolean isChannelReady() {
        return channel != null;
    }

    public ResponseAPDU transmit(CommandAPDU cmd) throws CardException {
        return channel.transmit(cmd);
    }
    
    /**
     * Hàm gửi lệnh có log debug
     */
    private ResponseAPDU send(byte ins, byte p1, byte p2, byte[] data, int le) throws CardException {
        CommandAPDU cmd;
        if (data != null) {
            cmd = (le >= 0) ? new CommandAPDU(CLA_APPLET, ins, p1, p2, data, le)
                            : new CommandAPDU(CLA_APPLET, ins, p1, p2, data);
        } else {
            cmd = (le >= 0) ? new CommandAPDU(CLA_APPLET, ins, p1, p2, le)
                            : new CommandAPDU(CLA_APPLET, ins, p1, p2);
        }
        
        if (channel == null) {
            throw new IllegalStateException("CardChannel is null! Please connect to card first.");
        }
        
        System.out.println("[APDU] Sending: " + bytesToHex(cmd.getBytes()));
        ResponseAPDU resp = channel.transmit(cmd);
        System.out.println("[APDU] Response SW: " + String.format("0x%04X", resp.getSW()));
        
        return resp;
    }

    // ================== CÁC HÀM CHO ADMIN APPLET ==================

    /**
     * Xác thực PIN Admin
     * Gửi PIN plaintext xuống thẻ, thẻ sẽ hash và verify
     */
    public boolean verifyPinAdmin(byte[] pinPlaintext) {
        try {
            ResponseAPDU resp = send(INS_VERIFY_PIN_ADMIN, (byte)0, (byte)0, pinPlaintext, -1);
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ký challenge bằng SK_admin
     */
    public byte[] signChallengeAdmin(byte[] challenge) {
        try {
            System.out.println("[APDUCommands] signChallengeAdmin: Gửi challenge lên thẻ...");
            System.out.println("[APDUCommands] Challenge length: " + (challenge != null ? challenge.length : 0));
            if (challenge != null && challenge.length > 0) {
                System.out.print("[APDUCommands] Challenge (first 16 bytes hex): ");
                for (int i = 0; i < Math.min(16, challenge.length); i++) {
                    System.out.printf("%02X ", challenge[i]);
                }
                System.out.println();
            }
            
            ResponseAPDU resp = send(INS_SIGN_CHALLENGE_ADMIN, (byte)0, (byte)0, challenge, 128);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] signChallengeAdmin response SW: " + String.format("0x%04X", sw));
            
            if (sw == 0x9000) {
                byte[] signature = resp.getData();
                System.out.println("[APDUCommands] signChallengeAdmin: Nhận được signature, length: " + (signature != null ? signature.length : 0));
                if (signature != null && signature.length > 0) {
                    System.out.print("[APDUCommands] Signature (first 16 bytes hex): ");
                    for (int i = 0; i < Math.min(16, signature.length); i++) {
                        System.out.printf("%02X ", signature[i]);
                    }
                    System.out.println();
                }
                return signature;
            } else {
                System.err.println("[APDUCommands] signChallengeAdmin THẤT BẠI! SW: " + String.format("0x%04X", sw));
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] Exception trong signChallengeAdmin: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Sinh token reset PIN User
     * @param message Thông điệp M = "RESET_PIN" || cardId_user || timestamp || nonce
     * @return signature_admin
     */
    public byte[] generateResetToken(byte[] message) {
        try {
            ResponseAPDU resp = send(INS_GEN_RESET_TOKEN, (byte)0, (byte)0, message, 128);
            if (resp.getSW() == 0x9000) {
                return resp.getData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Khởi tạo thẻ Admin
     * Gửi PIN plaintext và salt, thẻ sẽ hash và lưu PIN hash
     * @param pinPlaintext PIN admin plaintext
     * @param salt Salt 16 bytes
     * @return true nếu thành công
     */
    public boolean initAdminCard(byte[] pinPlaintext, byte[] salt) {
        return initAdminCard(pinPlaintext, salt, null);
    }
    
    /**
     * Khởi tạo thẻ Admin (với callback để log)
     * @param pinPlaintext PIN admin plaintext
     * @param salt Salt 16 bytes
     * @param logCallback Callback để log thông tin (có thể null)
     * @return true nếu thành công
     */
    public boolean initAdminCard(byte[] pinPlaintext, byte[] salt, java.util.function.Consumer<String> logCallback) {
        try {
            // Format: PIN_len(1) + PIN + salt(16)
            byte[] data = new byte[1 + pinPlaintext.length + 16];
            data[0] = (byte)pinPlaintext.length;
            System.arraycopy(pinPlaintext, 0, data, 1, pinPlaintext.length);
            System.arraycopy(salt, 0, data, 1 + pinPlaintext.length, 16);
            
            String logMsg = "[APDUCommands] INIT_ADMIN_CARD:\n" +
                "  - INS: 0x" + String.format("%02X", INS_INIT_ADMIN_CARD) + "\n" +
                "  - PIN length: " + pinPlaintext.length + "\n" +
                "  - Data length: " + data.length + "\n" +
                "  - Data (hex): " + bytesToHex(data);
            System.out.println(logMsg);
            if (logCallback != null) {
                logCallback.accept(logMsg);
            }
            
            ResponseAPDU resp = send(INS_INIT_ADMIN_CARD, (byte)0, (byte)0, data, -1);
            int sw = resp.getSW();
            String swMsg = "[APDUCommands] INIT_ADMIN_CARD response SW: " + String.format("0x%04X", sw);
            System.out.println(swMsg);
            if (logCallback != null) {
                logCallback.accept("Response SW: " + String.format("0x%04X", sw));
            }
            
            if (sw != 0x9000) {
                String errorMsg = "[APDUCommands] INIT_ADMIN_CARD THẤT BẠI! SW: " + String.format("0x%04X", sw);
                System.err.println(errorMsg);
                
                String detailMsg = "";
                if (sw == 0x6D00) {
                    detailMsg = "Lệnh không được hỗ trợ (INS_NOT_SUPPORTED)\n" +
                        "Có thể applet chưa được cập nhật hoặc chưa được select đúng!";
                } else if (sw == 0x6700) {
                    detailMsg = "Độ dài dữ liệu sai (WRONG_LENGTH)";
                } else if (sw == 0x6A86) {
                    detailMsg = "Tham số P1/P2 sai (INCORRECT_P1P2)";
                } else if (sw == 0x6985) {
                    detailMsg = "Điều kiện không thỏa mãn (CONDITIONS_NOT_SATISFIED)";
                } else if (sw == 0x6A80) {
                    detailMsg = "Dữ liệu không đúng (WRONG_DATA)";
                } else {
                    detailMsg = "Lỗi không xác định";
                }
                
                System.err.println("  -> " + detailMsg);
                if (logCallback != null) {
                    logCallback.accept("LỖI: " + String.format("0x%04X", sw) + " - " + detailMsg);
                }
            }
            
            return sw == 0x9000;
        } catch (Exception e) {
            String errorMsg = "[APDUCommands] Exception trong initAdminCard: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            if (logCallback != null) {
                logCallback.accept("Exception: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Lấy cardId_admin từ thẻ Admin
     */
    public byte[] getAdminCardId() {
        try {
            // AdminApplet dùng INS_GET_CARD_ID = 0x13
            System.out.println("[APDUCommands] getAdminCardId: Sending INS = 0x13");
            ResponseAPDU resp = send((byte)0x13, (byte)0, (byte)0, null, 16);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] getAdminCardId response SW: " + String.format("0x%04X", sw));
            
            if (sw == 0x9000) {
                byte[] cardId = resp.getData();
                System.out.println("[APDUCommands] getAdminCardId: Card ID = " + bytesToHex(cardId));
                return cardId;
            } else {
                System.err.println("[APDUCommands] getAdminCardId THẤT BẠI! SW: " + String.format("0x%04X", sw));
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] Exception trong getAdminCardId: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Lấy PK_admin từ thẻ
     * @return PK_admin dạng raw (modLen(2) + modulus + expLen(2) + exponent) hoặc null nếu lỗi
     */
    public byte[] getAdminPublicKey() {
        try {
            ResponseAPDU resp = send(INS_GET_PUBLIC_KEY, (byte)0, (byte)0, null, 256);
            if (resp.getSW() == 0x9000) {
                return resp.getData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ================== CÁC HÀM CHO USER APPLET ==================

    /**
     * Xác thực PIN User
     * Gửi PIN plaintext xuống thẻ, thẻ sẽ hash và verify
     */
    public boolean verifyPinUser(byte[] pinPlaintext) {
        try {
            ResponseAPDU resp = send(INS_VERIFY_PIN_USER, (byte)0, (byte)0, pinPlaintext, -1);
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ký challenge bằng SK_user
     */
    public byte[] signChallengeUser(byte[] challenge) {
        try {
            System.out.println("[APDUCommands] signChallengeUser: Gửi challenge lên thẻ...");
            System.out.println("[APDUCommands] Challenge length: " + (challenge != null ? challenge.length : 0));
            if (challenge != null && challenge.length > 0) {
                System.out.print("[APDUCommands] Challenge (first 16 bytes hex): ");
                for (int i = 0; i < Math.min(16, challenge.length); i++) {
                    System.out.printf("%02X ", challenge[i]);
                }
                System.out.println();
            }
            
            ResponseAPDU resp = send(INS_SIGN_CHALLENGE_USER, (byte)0, (byte)0, challenge, 128);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] signChallengeUser response SW: " + String.format("0x%04X", sw));
            
            if (sw == 0x9000) {
                byte[] signature = resp.getData();
                System.out.println("[APDUCommands] signChallengeUser: Nhận được signature, length: " + (signature != null ? signature.length : 0));
                if (signature != null && signature.length > 0) {
                    System.out.print("[APDUCommands] Signature (first 16 bytes hex): ");
                    for (int i = 0; i < Math.min(16, signature.length); i++) {
                        System.out.printf("%02X ", signature[i]);
                    }
                    System.out.println();
                }
                return signature;
            } else {
                System.err.println("[APDUCommands] signChallengeUser THẤT BẠI! SW: " + String.format("0x%04X", sw));
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] Exception trong signChallengeUser: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * V3: Get card status
     * Response: [initialized (1), pin_retry_counter (1), blocked_flag (1), cardID (16)]
     */
    public byte[] getStatus() {
        try {
            ResponseAPDU resp = send(INS_GET_STATUS, (byte)0, (byte)0, null, 19);
            if (resp.getSW() == 0x9000) {
                byte[] data = resp.getData();
                if (data != null && data.length >= 19) {
                    // Extract cardID (last 16 bytes)
                    byte[] cardId = new byte[16];
                    System.arraycopy(data, 3, cardId, 0, 16);
                    return cardId;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * V3: Get card status (full response)
     * @return CardStatus object with initialized, pinRetryCounter, blockedFlag, cardId
     */
    public CardStatus getCardStatus() {
        try {
            ResponseAPDU resp = send(INS_GET_STATUS, (byte)0, (byte)0, null, 19);
            if (resp.getSW() == 0x9000) {
                byte[] data = resp.getData();
                if (data != null && data.length >= 19) {
                    CardStatus status = new CardStatus();
                    status.initialized = data[0];
                    status.pinRetryCounter = data[1];
                    status.blockedFlag = data[2];
                    status.cardId = new byte[16];
                    System.arraycopy(data, 3, status.cardId, 0, 16);
                    return status;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * V2: Đọc cardId_user (Deprecated - Use getStatus() instead)
     */
    @Deprecated
    public byte[] getCardId() {
        // Try V3 first
        byte[] cardId = getStatus();
        if (cardId != null) {
            return cardId;
        }
        // Fallback to V2 (if card still has old applet)
        try {
            ResponseAPDU resp = send(INS_GET_CARD_ID, (byte)0, (byte)0, null, 16);
            if (resp.getSW() == 0x9000) {
                return resp.getData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Inner class for card status
     */
    public static class CardStatus {
        public byte initialized;
        public byte pinRetryCounter;
        public byte blockedFlag;
        public byte[] cardId;
    }

    /**
     * Đọc UserData đã giải mã
     * @return UserData bytes nếu thành công, null nếu thất bại
     * @throws CardException nếu gặp lỗi security (SW 0x6982)
     */
    public byte[] getUserData() throws CardException {
        try {
            ResponseAPDU resp = send(INS_GET_USER_DATA, (byte)0, (byte)0, null, 255);
            int sw = resp.getSW();
            if (sw == 0x9000) {
                return resp.getData();
            } else {
                // Log SW code chi tiết
                System.err.println("[APDUCommands] getUserData failed with SW: 0x" + String.format("%04X", sw));
                if (sw == 0x6982) {
                    System.err.println("[APDUCommands] Security status not satisfied - PIN User authentication required");
                    throw new CardException("Security status not satisfied (SW: 0x6982). PIN User authentication required.");
                }
                return null;
            }
        } catch (CardException e) {
            throw e; // Re-throw CardException
        } catch (Exception e) {
            System.err.println("[APDUCommands] getUserData exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Đọc số dư
     */
    public int getBalance() {
        try {
            ResponseAPDU resp = send(INS_GET_BALANCE, (byte)0, (byte)0, null, 4);
            if (resp.getSW() == 0x9000) {
                byte[] data = resp.getData();
                return ByteBuffer.wrap(data).getInt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Nạp tiền
     */
    public boolean credit(int amount) {
        try {
            byte[] amt = ByteBuffer.allocate(4).putInt(amount).array();
            ResponseAPDU resp = send(INS_CREDIT, (byte)0, (byte)0, amt, -1);
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Thanh toán
     */
    public boolean debit(int amount) {
        try {
            byte[] amt = ByteBuffer.allocate(4).putInt(amount).array();
            ResponseAPDU resp = send(INS_DEBIT, (byte)0, (byte)0, amt, -1);
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Đọc mã BHYT
     */
    public String getBHYT() {
        try {
            ResponseAPDU resp = send(INS_GET_BHYT, (byte)0, (byte)0, null, 50);
            if (resp.getSW() == 0x9000) {
                return new String(resp.getData(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Đọc lịch sử giao dịch
     */
    public byte[] getLogs() {
        try {
            ResponseAPDU resp = send(INS_GET_LOGS, (byte)0, (byte)0, null, 255);
            if (resp.getSW() == 0x9000) {
                return resp.getData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * User tự đổi PIN
     * Gửi PIN cũ và PIN mới dạng plaintext, thẻ sẽ hash và cập nhật
     */
    public boolean changePin(byte[] oldPinPlaintext, byte[] newPinPlaintext) {
        try {
            byte[] data = new byte[oldPinPlaintext.length + newPinPlaintext.length + 4];
            setShort(data, 0, (short)oldPinPlaintext.length);
            System.arraycopy(oldPinPlaintext, 0, data, 2, oldPinPlaintext.length);
            setShort(data, 2 + oldPinPlaintext.length, (short)newPinPlaintext.length);
            System.arraycopy(newPinPlaintext, 0, data, 4 + oldPinPlaintext.length, newPinPlaintext.length);
            ResponseAPDU resp = send(INS_CHANGE_PIN, (byte)0, (byte)0, data, -1);
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * V3: Phát hành thẻ User mới
     * @param cardId Card ID (16 bytes) - Backend sinh trước để derive PIN admin
     * @param userData Dữ liệu bệnh nhân (plaintext)
     * @param pinUser PIN user (6 bytes)
     * @param pinAdminReset PIN admin reset (6 bytes) - Derived từ K_master và cardId
     * @return status byte (0x00 = success) hoặc null nếu lỗi
     */
    public byte[] issueCard(byte[] cardId, byte[] userData, byte[] pinUser, byte[] pinAdminReset) {
        try {
            System.out.println("[APDUCommands] issueCard: Bắt đầu phát hành thẻ User (V3)");
            System.out.println("[APDUCommands] issueCard: cardId length = " + (cardId != null ? cardId.length : 0));
            System.out.println("[APDUCommands] issueCard: userData length = " + (userData != null ? userData.length : 0));
            System.out.println("[APDUCommands] issueCard: pinUser length = " + (pinUser != null ? pinUser.length : 0));
            System.out.println("[APDUCommands] issueCard: pinAdminReset length = " + (pinAdminReset != null ? pinAdminReset.length : 0));
            
            // V3 Format: [cardID (16 bytes)] [patient_info_length (2)] [patient_info] [PIN_user (6)] [PIN_admin_reset (6)]
            int totalLen = (cardId != null ? cardId.length : 0) + userData.length + pinUser.length + pinAdminReset.length;
            byte[] data = new byte[totalLen + 2]; // +2 cho patient_info_length
            int offset = 0;
            
            // Ghi cardID (16 bytes) - Backend cung cấp để thẻ dùng
            if (cardId != null && cardId.length == 16) {
                System.arraycopy(cardId, 0, data, offset, 16);
                offset += 16;
            } else {
                // Không có cardID, thẻ sẽ tự sinh (fill 16 bytes với 0)
                for (int i = 0; i < 16; i++) {
                    data[offset++] = 0;
                }
            }
            
            // Ghi patient_info_length và data
            setShort(data, offset, (short)userData.length);
            offset += 2;
            System.arraycopy(userData, 0, data, offset, userData.length);
            offset += userData.length;
            
            // Ghi PIN_user (6 bytes, no length prefix in V3)
            System.arraycopy(pinUser, 0, data, offset, pinUser.length);
            offset += pinUser.length;
            
            // Ghi PIN_admin_reset (6 bytes, no length prefix in V3)
            System.arraycopy(pinAdminReset, 0, data, offset, pinAdminReset.length);
            
            System.out.println("[APDUCommands] issueCard: Gửi lệnh INS_ISSUE_CARD (0x" + String.format("%02X", INS_ISSUE_CARD) + ")");
            System.out.println("[APDUCommands] issueCard: Total data length = " + data.length);
            
            ResponseAPDU resp = send(INS_ISSUE_CARD, (byte)0, (byte)0, data, 1);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] issueCard: Response SW = " + String.format("0x%04X", sw));
            
            if (sw == 0x9000) {
                byte[] result = resp.getData();
                System.out.println("[APDUCommands] issueCard: Thành công! Status = " + (result != null && result.length > 0 ? String.format("0x%02X", result[0]) : "null"));
                
                // V3: Response is just status byte (0x00 = success)
                // CardID will be read via GET_STATUS after issuance
                return result;
            } else {
                String errorMsg = "[APDUCommands] issueCard: THẤT BẠI! SW = " + String.format("0x%04X", sw);
                System.err.println(errorMsg);
                
                // Giải thích các mã lỗi phổ biến
                if (sw == 0x6D00) {
                    System.err.println("  -> Lệnh không được hỗ trợ (INS_NOT_SUPPORTED) - Kiểm tra applet có hỗ trợ ISSUE_CARD V3");
                } else if (sw == 0x6700) {
                    System.err.println("  -> Độ dài dữ liệu sai (WRONG_LENGTH)");
                } else if (sw == 0x6A86) {
                    System.err.println("  -> Tham số P1/P2 sai (INCORRECT_P1P2)");
                } else if (sw == 0x6985) {
                    System.err.println("  -> Điều kiện không thỏa mãn - Thẻ đã được phát hành trước đó?");
                } else if (sw == 0x6A80) {
                    System.err.println("  -> Dữ liệu không đúng (WRONG_DATA) - Kiểm tra format dữ liệu");
                } else if (sw == 0x6F00) {
                    System.err.println("  -> Lỗi không xác định từ applet (UNKNOWN_ERROR)");
                }
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] issueCard: Exception - " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * V3: Reset PIN User bởi Admin
     * @param pinAdminReset PIN admin reset (6 bytes)
     * @param pinUserNew PIN mới cho user (6 bytes)
     * @return true nếu thành công
     */
    public boolean resetPinByAdmin(byte[] pinAdminReset, byte[] pinUserNew) {
        try {
            System.out.println("[APDUCommands] resetPinByAdmin: Bắt đầu reset PIN user (V3)");
            System.out.println("[APDUCommands] resetPinByAdmin: PIN Admin Reset length = " + (pinAdminReset != null ? pinAdminReset.length : 0));
            System.out.println("[APDUCommands] resetPinByAdmin: PIN User New length = " + (pinUserNew != null ? pinUserNew.length : 0));
            
            // V3 Format: [PIN_admin_reset (6)] [new_PIN_user (6)]
            byte[] data = new byte[pinAdminReset.length + pinUserNew.length];
            int offset = 0;
            
            // Ghi PIN_admin_reset (6 bytes)
            System.arraycopy(pinAdminReset, 0, data, offset, pinAdminReset.length);
            offset += pinAdminReset.length;
            
            // Ghi new_PIN_user (6 bytes)
            System.arraycopy(pinUserNew, 0, data, offset, pinUserNew.length);
            
            String logMsg = "[APDUCommands] ADMIN_RESET_PIN (V3):\n" +
                "  - INS: 0x" + String.format("%02X", INS_ADMIN_RESET_PIN) + "\n" +
                "  - Total data length: " + data.length + "\n" +
                "  - Data (hex): " + bytesToHex(data);
            System.out.println(logMsg);
            
            ResponseAPDU resp = send(INS_ADMIN_RESET_PIN, (byte)0, (byte)0, data, 1);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] ADMIN_RESET_PIN response SW: " + String.format("0x%04X", sw));
            
            if (sw == 0x9000) {
                byte[] result = resp.getData();
                if (result != null && result.length > 0 && result[0] == 0x00) {
                    System.out.println("[APDUCommands] Reset PIN user thành công!");
                    return true;
                }
            } else {
                String errorMsg = "[APDUCommands] ADMIN_RESET_PIN THẤT BẠI! SW: " + String.format("0x%04X", sw);
                System.err.println(errorMsg);
                
                // Giải thích các mã lỗi chi tiết
                if (sw == 0x6D00) {
                    System.err.println("  -> Lệnh không được hỗ trợ (INS_NOT_SUPPORTED)");
                    System.err.println("     Có thể applet chưa được build lại hoặc INS code bị sai");
                } else if (sw == 0x6700) {
                    System.err.println("  -> Độ dài dữ liệu sai (WRONG_LENGTH)");
                } else if (sw == 0x6300) {
                    System.err.println("  -> PIN Admin không đúng (Authentication failed)");
                    System.err.println("     Kiểm tra lại PIN Admin Reset");
                } else if (sw == 0x6985) {
                    System.err.println("  -> Điều kiện không thỏa mãn - Thẻ chưa được phát hành?");
                } else if (sw == 0x6983) {
                    System.err.println("  -> Thẻ bị khóa (Card blocked)");
                } else if (sw == 0x6F00) {
                    System.err.println("  -> Lỗi không xác định từ applet");
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] Exception trong resetPinByAdmin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return false;
    }

    /**
     * V3: Verify PIN and read patient data
     * @param pinUser PIN user (6 bytes)
     * @return Patient data (plaintext) hoặc null nếu lỗi
     */
    public byte[] verifyPinAndReadData(byte[] pinUser) {
        try {
            // V3 Format: [PIN_user (6)]
            ResponseAPDU resp = send(INS_VERIFY_PIN_AND_READ_DATA, (byte)0, (byte)0, pinUser, 255);
            int sw = resp.getSW();
            
            if (sw == 0x9000) {
                byte[] data = resp.getData();
                if (data != null && data.length >= 3) {
                    // Parse: [status (1)] [length (2)] [data]
                    byte status = data[0];
                    if (status == 0x00) {
                        short length = (short)(((data[1] & 0xFF) << 8) | (data[2] & 0xFF));
                        if (length > 0 && data.length >= 3 + length) {
                            byte[] patientData = new byte[length];
                            System.arraycopy(data, 3, patientData, 0, length);
                            return patientData;
                        }
                    }
                }
            } else if ((sw & 0xFF00) == 0x63C0) {
                // PIN retry counter in SW2
                int retries = sw & 0x000F;
                System.err.println("[APDUCommands] PIN sai! Còn lại " + retries + " lần thử");
            } else if (sw == 0x6983) {
                System.err.println("[APDUCommands] Thẻ bị khóa!");
            } else if (sw == 0x6985) {
                System.err.println("[APDUCommands] Thẻ chưa được phát hành!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * V3: Update patient data
     * @param patientData Patient data (plaintext)
     * @return true nếu thành công
     */
    public boolean updatePatientData(byte[] patientData) {
        try {
            // V3 Format: [patient_data_length (2)] [patient_data]
            byte[] data = new byte[2 + patientData.length];
            setShort(data, 0, (short)patientData.length);
            System.arraycopy(patientData, 0, data, 2, patientData.length);
            
            ResponseAPDU resp = send(INS_UPDATE_PATIENT_DATA, (byte)0, (byte)0, data, 1);
            int sw = resp.getSW();
            
            if (sw == 0x9000) {
                byte[] result = resp.getData();
                return result != null && result.length > 0 && result[0] == 0x00;
            } else if (sw == 0x6982) {
                System.err.println("[APDUCommands] Security status not satisfied - Need to verify PIN first");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * V2: Cập nhật UserData (Deprecated - Use updatePatientData() instead)
     */
    @Deprecated
    public boolean updateUserData(byte[] newUserData) {
        return updatePatientData(newUserData);
    }

    /**
     * Reset toàn bộ thẻ User (xóa tất cả dữ liệu trong EEPROM)
     * @param pinAdmin PIN Admin để xác thực quyền reset
     * @return true nếu reset thành công
     */
    public boolean resetUserCard(byte[] pinAdmin) {
        try {
            System.out.println("[APDUCommands] resetUserCard: Bắt đầu reset thẻ User");
            System.out.println("[APDUCommands] resetUserCard: PIN Admin length = " + (pinAdmin != null ? pinAdmin.length : 0));
            
            ResponseAPDU resp = send(INS_RESET_USER_CARD, (byte)0, (byte)0, pinAdmin, -1);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] resetUserCard: Response SW = " + String.format("0x%04X", sw));
            
            if (sw == 0x9000) {
                System.out.println("[APDUCommands] resetUserCard: Reset thẻ thành công");
                return true;
            } else {
                String errorMsg = "[APDUCommands] resetUserCard: THẤT BẠI! SW = " + String.format("0x%04X", sw);
                System.err.println(errorMsg);
                
                // Giải thích các mã lỗi
                if (sw == 0x6D00) {
                    System.err.println("  -> Lệnh không được hỗ trợ (INS_NOT_SUPPORTED) - Applet chưa hỗ trợ reset");
                } else if (sw == 0x6300) {
                    System.err.println("  -> PIN Admin không đúng");
                } else if (sw == 0x6985) {
                    System.err.println("  -> Điều kiện không thỏa mãn - Cần xác thực PIN Admin trước");
                } else if (sw == 0x6A80) {
                    System.err.println("  -> Dữ liệu không đúng (WRONG_DATA)");
                }
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] resetUserCard: Exception - " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    // Helper method để set short (tương tự javacard.framework.Util)
    private void setShort(byte[] bArray, int bOff, short sValue) {
        bArray[bOff] = (byte)(sValue >> 8);
        bArray[bOff + 1] = (byte)sValue;
    }
}

