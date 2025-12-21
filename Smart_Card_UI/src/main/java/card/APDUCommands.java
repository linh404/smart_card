package card;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * APDUCommands - Định nghĩa và xử lý các lệnh APDU
 * Tham khảo từ SmartCard_Old, mở rộng cho UserApplet
 */
public class APDUCommands {

    private CardChannel channel;
    public static final byte CLA_APPLET = (byte) 0x80;

    // ================== BẢNG MÃ LỆNH (INS) CHO USER APPLET ==================
    // V3 - New instruction codes
    public static final byte INS_GET_STATUS = (byte) 0x01; // V3: Get card status
    public static final byte INS_ISSUE_CARD = (byte) 0x02; // V3: Issue new card
    public static final byte INS_VERIFY_PIN_AND_READ_DATA = (byte) 0x03; // V3: Verify PIN and read patient data
    public static final byte INS_UPDATE_PATIENT_DATA = (byte) 0x04; // V3: Update patient data
    public static final byte INS_ADMIN_RESET_PIN = (byte) 0x05; // V3: Admin reset PIN
    public static final byte INS_CHANGE_PIN = (byte) 0x0A; // User tự đổi PIN (cần PIN cũ) - Đổi từ 0x06 để tránh
                                                           // conflict
    public static final byte INS_DEBIT = (byte) 0x07; // Thanh toán
    public static final byte INS_GET_TXN_STATUS = (byte) 0x08; // Lấy trạng thái giao dịch
    public static final byte INS_CREDIT = (byte) 0x09; // Nạp tiền (0x09 để tránh nhầm lẫn với SW 0x6XXX)
    public static final byte INS_SIGN_CHALLENGE = (byte) 0x10; // V3: Sign challenge with SK_user (0x10 to avoid
                                                               // confusion with SW 0x6XXX)
    public static final byte INS_GET_PIN_CHANGE_STATUS = (byte) 0x11; // V4: Kiểm tra user đã đổi PIN mặc định chưa

    // ================== AID CỦA CÁC APPLET ==================
    // Package chung: HospitalCard
    // Package AID: 11 22 33 44 55 (5 bytes)

    // UserApplet AID: 11 22 33 44 55 01 (6 bytes)
    public static final byte[] AID_USER = {
            (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x01
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

    // ================== CÁC HÀM CHO USER APPLET ==================

    /**
     * V3: Sign challenge bằng SK_user
     * 
     * @param challenge Challenge bytes (typically 32 bytes)
     * @return Signature (128 bytes for RSA 1024) hoặc null nếu lỗi
     * @throws CardException nếu có lỗi giao tiếp
     */
    public byte[] signChallenge(byte[] challenge) throws CardException {
        try {
            System.out.println("[APDUCommands] signChallenge: Gửi challenge xuống thẻ...");
            System.out.println("[APDUCommands] Challenge length: " + (challenge != null ? challenge.length : 0));
            if (challenge != null && challenge.length > 0) {
                System.out.print("[APDUCommands] Challenge (first 16 bytes): ");
                for (int i = 0; i < Math.min(16, challenge.length); i++) {
                    System.out.printf("%02X ", challenge[i]);
                }
                System.out.println();
            }

            ResponseAPDU resp = send(INS_SIGN_CHALLENGE, (byte) 0, (byte) 0, challenge, 128);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] signChallenge response SW: " + String.format("0x%04X", sw));

            if (sw == 0x9000) {
                byte[] signature = resp.getData();
                System.out.println("[APDUCommands] ✓ Nhận được signature, length: " +
                        (signature != null ? signature.length : 0));
                if (signature != null && signature.length > 0) {
                    System.out.print("[APDUCommands] Signature (first 16 bytes): ");
                    for (int i = 0; i < Math.min(16, signature.length); i++) {
                        System.out.printf("%02X ", signature[i]);
                    }
                    System.out.println();
                }
                return signature;
            } else if (sw == 0x6982) {
                System.err.println("[APDUCommands] ✗ Security status not satisfied - PIN chưa verify");
                throw new CardException("Security status not satisfied (0x6982) - PIN User chưa được verify");
            } else if (sw == 0x6985) {
                System.err.println("[APDUCommands] ✗ Thẻ chưa được phát hành");
                throw new CardException("Card not issued (0x6985)");
            } else {
                System.err.println("[APDUCommands] ✗ signChallenge THẤT BẠI! SW: " + String.format("0x%04X", sw));
                throw new CardException("Sign challenge failed with SW: " + String.format("0x%04X", sw));
            }
        } catch (CardException e) {
            throw e; // Re-throw CardException
        } catch (Exception e) {
            System.err.println("[APDUCommands] Exception: " + e.getMessage());
            e.printStackTrace();
            throw new CardException("Exception in signChallenge: " + e.getMessage());
        }
    }

    /**
     * V3: Get card status
     * Response: [initialized (1), pin_retry_counter (1), blocked_flag (1), cardID
     * (16)]
     */
    public byte[] getStatus() {
        try {
            ResponseAPDU resp = send(INS_GET_STATUS, (byte) 0, (byte) 0, null, 19);
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
     * 
     * @return CardStatus object with initialized, pinRetryCounter, blockedFlag,
     *         cardId
     */
    public CardStatus getCardStatus() {
        try {
            ResponseAPDU resp = send(INS_GET_STATUS, (byte) 0, (byte) 0, null, 19);
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
     * Đọc cardId_user từ thẻ (V3: wrapper của getStatus())
     * 
     * @return cardId (16 bytes) hoặc null nếu lỗi
     */
    public byte[] getCardId() {
        return getStatus();
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
     * Result class for verifyPinAndReadData
     * Chứa thông tin chi tiết về kết quả xác thực PIN
     */
    public static class VerifyPinResult {
        public static final int STATUS_SUCCESS = 0;
        public static final int STATUS_WRONG_PIN = 1;
        public static final int STATUS_CARD_BLOCKED = 2;
        public static final int STATUS_CARD_NOT_ISSUED = 3;
        public static final int STATUS_CRYPTO_ERROR = 4;
        public static final int STATUS_UNKNOWN_ERROR = 5;

        public int status;
        public int retriesLeft; // Số lần thử còn lại (chỉ có ý nghĩa khi STATUS_WRONG_PIN)
        public byte[] patientData; // Dữ liệu bệnh nhân (chỉ có khi STATUS_SUCCESS)
        public String errorMessage; // Thông báo lỗi chi tiết
        public int swCode; // Status Word từ thẻ

        public VerifyPinResult(int status) {
            this.status = status;
            this.retriesLeft = -1;
            this.patientData = null;
            this.errorMessage = null;
            this.swCode = 0;
        }

        public boolean isSuccess() {
            return status == STATUS_SUCCESS;
        }

        public boolean isWrongPin() {
            return status == STATUS_WRONG_PIN;
        }

        public boolean isBlocked() {
            return status == STATUS_CARD_BLOCKED;
        }
    }

    /**
     * V4: Kiểm tra user đã đổi PIN mặc định chưa
     * 
     * @return true nếu đã đổi PIN, false nếu chưa đổi (vẫn dùng PIN mặc định)
     */
    public boolean isPinChanged() {
        try {
            System.out.println("[APDUCommands] isPinChanged: Kiểm tra trạng thái PIN...");
            ResponseAPDU resp = send(INS_GET_PIN_CHANGE_STATUS, (byte) 0, (byte) 0, null, 1);
            int sw = resp.getSW();

            if (sw == 0x9000) {
                byte[] data = resp.getData();
                if (data != null && data.length >= 1) {
                    boolean changed = (data[0] == 1);
                    System.out.println(
                            "[APDUCommands] isPinChanged: " + (changed ? "Đã đổi PIN" : "Chưa đổi PIN mặc định"));
                    return changed;
                }
            } else if (sw == 0x6D00) {
                // INS không được hỗ trợ - applet cũ chưa có lệnh này
                System.out.println("[APDUCommands] isPinChanged: Applet chưa hỗ trợ GET_PIN_CHANGE_STATUS");
                return true; // Coi như đã đổi để không block user với thẻ cũ
            } else {
                System.err.println("[APDUCommands] isPinChanged: Error SW = " + String.format("0x%04X", sw));
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] isPinChanged error: " + e.getMessage());
            e.printStackTrace();
        }
        return false; // Mặc định coi như chưa đổi
    }

    /**
     * Đọc UserData đã giải mã (V3: wrapper - cần verify PIN trước)
     * 
     * @return UserData bytes nếu thành công, null nếu thất bại
     * @throws CardException nếu gặp lỗi security
     * @deprecated V3: Không còn method riêng, dùng verifyPinAndReadData() thay thế
     */
    @Deprecated
    public byte[] getUserData() throws CardException {
        throw new CardException("getUserData() is deprecated in V3. Use verifyPinAndReadData() instead.");
    }

    /**
     * V3: Phát hành thẻ User mới
     * 
     * @param cardId        Card ID (16 bytes) - Backend sinh trước để derive PIN
     *                      admin
     * @param userData      Dữ liệu bệnh nhân (plaintext)
     * @param pinUser       PIN user (6 bytes)
     * @param pinAdminReset PIN admin reset (6 bytes) - Derived từ K_master và
     *                      cardId
     * @return status byte (0x00 = success) hoặc null nếu lỗi
     */
    public byte[] issueCard(byte[] cardId, byte[] userData, byte[] pinUser, byte[] pinAdminReset) {
        return issueCard(cardId, userData, pinUser, pinAdminReset, 0);
    }

    /**
     * Issue card with initial balance
     * 
     * @param cardId         Card ID (16 bytes)
     * @param userData       User data bytes (patient info, without balance)
     * @param pinUser        PIN user (6 bytes)
     * @param pinAdminReset  PIN admin reset (6 bytes)
     * @param initialBalance Initial balance (default 0)
     * @return Response data or null if error
     */
    public byte[] issueCard(byte[] cardId, byte[] userData, byte[] pinUser, byte[] pinAdminReset, int initialBalance) {
        try {
            System.out.println("[APDUCommands] issueCard: Bắt đầu phát hành thẻ User (V3)");
            System.out.println("[APDUCommands] issueCard: cardId length = " + (cardId != null ? cardId.length : 0));
            System.out
                    .println("[APDUCommands] issueCard: userData length = " + (userData != null ? userData.length : 0));
            System.out.println("[APDUCommands] issueCard: pinUser length = " + (pinUser != null ? pinUser.length : 0));
            System.out.println("[APDUCommands] issueCard: pinAdminReset length = "
                    + (pinAdminReset != null ? pinAdminReset.length : 0));

            // V3 Format: [cardID (16 bytes)] [patient_info_length (2)] [patient_info]
            // [PIN_user (6)] [PIN_admin_reset (6)] [initial_balance (4 bytes)]
            int totalLen = (cardId != null ? cardId.length : 0) + userData.length + pinUser.length
                    + pinAdminReset.length + 4;
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
            setShort(data, offset, (short) userData.length);
            offset += 2;
            System.arraycopy(userData, 0, data, offset, userData.length);
            offset += userData.length;

            // Ghi PIN_user (6 bytes, no length prefix in V3)
            System.arraycopy(pinUser, 0, data, offset, pinUser.length);
            offset += pinUser.length;

            // Ghi PIN_admin_reset (6 bytes, no length prefix in V3)
            System.arraycopy(pinAdminReset, 0, data, offset, pinAdminReset.length);
            offset += pinAdminReset.length;

            // Ghi initial_balance (4 bytes, int)
            data[offset++] = (byte) (initialBalance >> 24);
            data[offset++] = (byte) (initialBalance >> 16);
            data[offset++] = (byte) (initialBalance >> 8);
            data[offset++] = (byte) (initialBalance & 0xFF);

            System.out.println("[APDUCommands] issueCard: Gửi lệnh INS_ISSUE_CARD (0x"
                    + String.format("%02X", INS_ISSUE_CARD) + ")");
            System.out.println("[APDUCommands] issueCard: Initial balance = " + initialBalance);
            System.out.println("[APDUCommands] issueCard: Total data length = " + data.length);

            ResponseAPDU resp = send(INS_ISSUE_CARD, (byte) 0, (byte) 0, data, 1);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] issueCard: Response SW = " + String.format("0x%04X", sw));

            // Decode error codes
            if (sw != 0x9000) {
                String errorMsg = decodeIssueCardError(sw);
                System.err.println("[APDUCommands] issueCard: " + errorMsg);
            }

            if (sw == 0x9000) {
                byte[] result = resp.getData();
                System.out.println("[APDUCommands] issueCard: Thành công! Status = "
                        + (result != null && result.length > 0 ? String.format("0x%02X", result[0]) : "null"));

                // V3: Response is just status byte (0x00 = success)
                // CardID will be read via GET_STATUS after issuance
                return result;
            } else {
                String errorMsg = "[APDUCommands] issueCard: THẤT BẠI! SW = " + String.format("0x%04X", sw);
                System.err.println(errorMsg);

                // Giải thích các mã lỗi phổ biến
                if (sw == 0x6D00) {
                    System.err.println(
                            "  -> Lệnh không được hỗ trợ (INS_NOT_SUPPORTED) - Kiểm tra applet có hỗ trợ ISSUE_CARD V3");
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
     * 
     * @param pinAdminReset PIN admin reset (6 bytes)
     * @param pinUserNew    PIN mới cho user (6 bytes)
     * @return true nếu thành công
     */
    public boolean resetPinByAdmin(byte[] pinAdminReset, byte[] pinUserNew) {
        try {
            System.out.println("[APDUCommands] resetPinByAdmin: Bắt đầu reset PIN user (V3)");
            System.out.println("[APDUCommands] resetPinByAdmin: PIN Admin Reset length = "
                    + (pinAdminReset != null ? pinAdminReset.length : 0));
            System.out.println("[APDUCommands] resetPinByAdmin: PIN User New length = "
                    + (pinUserNew != null ? pinUserNew.length : 0));

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

            ResponseAPDU resp = send(INS_ADMIN_RESET_PIN, (byte) 0, (byte) 0, data, 1);
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
     * Transaction result class
     */
    public static class TransactionResult {
        public short seq;
        public int balanceAfter;
        public byte[] currHash;

        public TransactionResult(short seq, int balanceAfter, byte[] currHash) {
            this.seq = seq;
            this.balanceAfter = balanceAfter;
            this.currHash = currHash;
        }
    }

    /**
     * CREDIT (0x09) - Nạp tiền
     * 
     * @param amount Số tiền (int, 4 bytes)
     * @return TransactionResult hoặc null nếu lỗi
     * @throws CardException nếu có lỗi giao tiếp
     * 
     *                       Note: INS = 0x09 (không dùng 0x06 để tránh nhầm lẫn với
     *                       Status Word 0x6XXX)
     */
    public TransactionResult creditTransaction(int amount) throws CardException {
        try {
            // Format: [amount (4 bytes, int)]
            byte[] data = new byte[4];
            data[0] = (byte) (amount >> 24);
            data[1] = (byte) (amount >> 16);
            data[2] = (byte) (amount >> 8);
            data[3] = (byte) amount;

            ResponseAPDU resp = send(INS_CREDIT, (byte) 0, (byte) 0, data, 27); // 1 + 2 + 4 + 20 = 27 bytes response
            int sw = resp.getSW();

            if (sw == 0x9000) {
                byte[] result = resp.getData();
                if (result != null && result.length >= 27) {
                    // Parse: [status (1)] [seq (2)] [balance_after (4)] [curr_hash (20)]
                    byte status = result[0];
                    if (status == 0x00) {
                        short seq = (short) (((result[1] & 0xFF) << 8) | (result[2] & 0xFF));
                        int balanceAfter = ((result[3] & 0xFF) << 24) |
                                ((result[4] & 0xFF) << 16) |
                                ((result[5] & 0xFF) << 8) |
                                (result[6] & 0xFF);
                        byte[] currHash = new byte[20];
                        System.arraycopy(result, 7, currHash, 0, 20);

                        return new TransactionResult(seq, balanceAfter, currHash);
                    }
                }
            } else if (sw == 0x6982) {
                throw new CardException("Security status not satisfied (0x6982) - PIN User chưa được verify");
            } else if (sw == 0x6983) {
                throw new CardException("Card blocked (0x6983)");
            } else if (sw == 0x6A80) {
                throw new CardException(
                        "Invalid transaction (0x6A80) - Có thể là số tiền không hợp lệ hoặc số dư không đủ");
            } else {
                throw new CardException("Credit transaction failed with SW: " + String.format("0x%04X", sw));
            }
        } catch (CardException e) {
            throw e;
        } catch (Exception e) {
            throw new CardException("Exception in creditTransaction: " + e.getMessage());
        }
        return null;
    }

    /**
     * DEBIT (0x07) - Thanh toán
     * 
     * @param amount Số tiền (int, 4 bytes)
     * @return TransactionResult hoặc null nếu lỗi
     * @throws CardException nếu có lỗi giao tiếp
     */
    public TransactionResult debitTransaction(int amount) throws CardException {
        try {
            // Format: [amount (4 bytes, int)]
            byte[] data = new byte[4];
            data[0] = (byte) (amount >> 24);
            data[1] = (byte) (amount >> 16);
            data[2] = (byte) (amount >> 8);
            data[3] = (byte) amount;

            ResponseAPDU resp = send(INS_DEBIT, (byte) 0, (byte) 0, data, 27); // 1 + 2 + 4 + 20 = 27 bytes response
            int sw = resp.getSW();

            if (sw == 0x9000) {
                byte[] result = resp.getData();
                if (result != null && result.length >= 27) {
                    // Parse: [status (1)] [seq (2)] [balance_after (4)] [curr_hash (20)]
                    byte status = result[0];
                    if (status == 0x00) {
                        short seq = (short) (((result[1] & 0xFF) << 8) | (result[2] & 0xFF));
                        int balanceAfter = ((result[3] & 0xFF) << 24) |
                                ((result[4] & 0xFF) << 16) |
                                ((result[5] & 0xFF) << 8) |
                                (result[6] & 0xFF);
                        byte[] currHash = new byte[20];
                        System.arraycopy(result, 7, currHash, 0, 20);

                        return new TransactionResult(seq, balanceAfter, currHash);
                    }
                }
            } else if (sw == 0x6982) {
                throw new CardException("Security status not satisfied (0x6982) - PIN User chưa được verify");
            } else if (sw == 0x6983) {
                throw new CardException("Card blocked (0x6983)");
            } else if (sw == 0x6A80) {
                throw new CardException(
                        "Invalid transaction (0x6A80) - Có thể là số tiền không hợp lệ hoặc số dư không đủ");
            } else {
                throw new CardException("Debit transaction failed with SW: " + String.format("0x%04X", sw));
            }
        } catch (CardException e) {
            throw e;
        } catch (Exception e) {
            throw new CardException("Exception in debitTransaction: " + e.getMessage());
        }
        return null;
    }

    /**
     * GET_TXN_STATUS (0x08) - Lấy trạng thái giao dịch
     * 
     * @return Object với txnCounter và lastTxnHash, hoặc null nếu lỗi
     * @throws CardException nếu có lỗi giao tiếp
     */
    public TransactionStatus getTxnStatus() throws CardException {
        try {
            ResponseAPDU resp = send(INS_GET_TXN_STATUS, (byte) 0, (byte) 0, null, 22); // 2 + 20 = 22 bytes response
            int sw = resp.getSW();

            if (sw == 0x9000) {
                byte[] result = resp.getData();
                if (result != null && result.length >= 22) {
                    // Parse: [txn_counter (2)] [last_txn_hash (20)]
                    short txnCounter = (short) (((result[0] & 0xFF) << 8) | (result[1] & 0xFF));
                    byte[] lastTxnHash = new byte[20];
                    System.arraycopy(result, 2, lastTxnHash, 0, 20);

                    return new TransactionStatus(txnCounter, lastTxnHash);
                }
            } else if (sw == 0x6985) {
                throw new CardException("Card not issued (0x6985)");
            } else {
                throw new CardException("Get transaction status failed with SW: " + String.format("0x%04X", sw));
            }
        } catch (CardException e) {
            throw e;
        } catch (Exception e) {
            throw new CardException("Exception in getTxnStatus: " + e.getMessage());
        }
        return null;
    }

    /**
     * Transaction status class
     */
    public static class TransactionStatus {
        public short txnCounter;
        public byte[] lastTxnHash;

        public TransactionStatus(short txnCounter, byte[] lastTxnHash) {
            this.txnCounter = txnCounter;
            this.lastTxnHash = lastTxnHash;
        }
    }

    /**
     * V3: Verify PIN and read patient data
     * 
     * @param pinUser PIN user (6 bytes)
     * @return Patient data (plaintext) hoặc null nếu lỗi
     */
    public byte[] verifyPinAndReadData(byte[] pinUser) {
        try {
            // DEBUG: In ra PIN bytes để kiểm tra
            if (pinUser != null) {
                System.out.println("[APDUCommands] verifyPinAndReadData: PIN bytes (hex): " + bytesToHex(pinUser));
                System.out.println("[APDUCommands] verifyPinAndReadData: PIN bytes (ascii): "
                        + new String(pinUser, StandardCharsets.UTF_8));
                System.out.println("[APDUCommands] verifyPinAndReadData: PIN length: " + pinUser.length);
            }

            // [LOG] Gửi APDU command
            System.out.println("[APDUCommands] verifyPinAndReadData: Sending APDU command...");
            System.out.println("[APDUCommands] verifyPinAndReadData: INS = 0x"
                    + String.format("%02X", INS_VERIFY_PIN_AND_READ_DATA));

            // V3 Format: [PIN_user (6)]
            ResponseAPDU resp = send(INS_VERIFY_PIN_AND_READ_DATA, (byte) 0, (byte) 0, pinUser, 255);
            int sw = resp.getSW();

            System.out.println("[APDUCommands] verifyPinAndReadData: Response SW: " + String.format("0x%04X", sw));

            // [LOG] Debug: Log chi tiết các mã lỗi từ applet
            // Kiểm tra các mã lỗi cụ thể trước, sau đó mới xử lý trường hợp chung
            if (sw == 0x6F01) {
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Step 1 failed: Hash calculation error");
            } else if (sw == 0x6F03) {
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Step 3 failed: KDF calculation error");
            } else if (sw == 0x6F04) {
                System.err
                        .println("[APDUCommands] verifyPinAndReadData: [LOG] Step 4 failed: AES decrypt MK_user error");
            } else if (sw == 0x6F05) {
                System.err.println(
                        "[APDUCommands] verifyPinAndReadData: [LOG] Step 5 failed: AES decrypt patient data error");
            } else if (sw == 0x6F06) {
                System.err
                        .println("[APDUCommands] verifyPinAndReadData: [LOG] Step 6 failed: AES decrypt balance error");
            } else if (sw == 0x6F30) {
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Buffer overflow error");
            } else if ((sw & 0xFFF0) == 0x6F00) {
                // Crypto error với reason code (general case)
                int reason = sw & 0x000F;
                System.err.println("[APDUCommands] verifyPinAndReadData: Crypto error, reason code: " + reason);
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Lỗi crypto có thể do:");
                System.err.println(
                        "[APDUCommands] verifyPinAndReadData: [LOG] - Applet state bị reset sau khi select lại");
                System.err.println(
                        "[APDUCommands] verifyPinAndReadData: [LOG] - Thẻ bị reset hoặc kết nối không ổn định");
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] - Lỗi trong quá trình decrypt dữ liệu");
            }

            if (sw == 0x9000) {
                System.out.println("[APDUCommands] verifyPinAndReadData: [LOG] Success! SW = 0x9000");
                byte[] data = resp.getData();
                System.out.println("[APDUCommands] verifyPinAndReadData: [LOG] Response data length: "
                        + (data != null ? data.length : 0));
                if (data != null && data.length >= 7) {
                    // Parse: [status (1)] [length (2)] [patient_data] [balance (4)]
                    byte status = data[0];
                    System.out.println("[APDUCommands] verifyPinAndReadData: [LOG] Status byte: 0x"
                            + String.format("%02X", status));
                    if (status == 0x00) {
                        short length = (short) (((data[1] & 0xFF) << 8) | (data[2] & 0xFF));
                        System.out.println("[APDUCommands] verifyPinAndReadData: [LOG] Patient data length: " + length);
                        // Check if we have patient_data + balance (4 bytes)
                        if (length > 0 && data.length >= 3 + length + 4) {
                            // Format: [patient_data_length (4 bytes)] [patient_data] [balance (4 bytes)]
                            // This matches UserData.fromBytes() format
                            byte[] patientData = new byte[4 + length + 4]; // 4 (length) + length (data) + 4 (balance)
                            // Prepend length (4 bytes, big-endian int)
                            patientData[0] = (byte) (length >> 24);
                            patientData[1] = (byte) (length >> 16);
                            patientData[2] = (byte) (length >> 8);
                            patientData[3] = (byte) (length & 0xFF);
                            // Copy patient_data
                            System.arraycopy(data, 3, patientData, 4, length);
                            // Append balance (4 bytes) at the end
                            System.arraycopy(data, 3 + length, patientData, 4 + length, 4);
                            System.out.println(
                                    "[APDUCommands] verifyPinAndReadData: [LOG] Successfully parsed patient data with balance");
                            return patientData;
                        } else if (length > 0 && data.length >= 3 + length) {
                            // Backward compatibility: no balance in response
                            // Format: [patient_data_length (4 bytes)] [patient_data]
                            byte[] patientData = new byte[4 + length];
                            patientData[0] = (byte) (length >> 24);
                            patientData[1] = (byte) (length >> 16);
                            patientData[2] = (byte) (length >> 8);
                            patientData[3] = (byte) (length & 0xFF);
                            System.arraycopy(data, 3, patientData, 4, length);
                            return patientData;
                        }
                    }
                }
            } else if ((sw & 0xFF00) == 0x63C0) {
                // PIN retry counter in SW2
                int retries = sw & 0x000F;
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Step 2 failed: PIN mismatch!");
                System.err.println("[APDUCommands] verifyPinAndReadData: PIN sai! Còn lại " + retries + " lần thử");
            } else if (sw == 0x6983) {
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Card is blocked!");
                System.err.println("[APDUCommands] verifyPinAndReadData: Thẻ bị khóa!");
            } else if (sw == 0x6985) {
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Card not initialized!");
                System.err.println("[APDUCommands] verifyPinAndReadData: Thẻ chưa được phát hành!");
            } else {
                // Unknown error
                System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Unknown error with SW: "
                        + String.format("0x%04X", sw));
            }
        } catch (CardException e) {
            System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] CardException caught!");
            System.err.println("[APDUCommands] verifyPinAndReadData: Error message: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[APDUCommands] verifyPinAndReadData: [LOG] Unexpected exception caught!");
            System.err.println("[APDUCommands] verifyPinAndReadData: Error type: " + e.getClass().getName());
            System.err.println("[APDUCommands] verifyPinAndReadData: Error message: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * V5: Verify PIN and read patient data với kết quả chi tiết
     * Trả về VerifyPinResult thay vì null để UI có thể hiển thị thông tin retry
     * 
     * @param pinUser PIN user (6 bytes)
     * @return VerifyPinResult chứa thông tin chi tiết về kết quả xác thực
     */
    public VerifyPinResult verifyPinWithResult(byte[] pinUser) {
        try {
            // DEBUG: In ra PIN bytes để kiểm tra
            if (pinUser != null) {
                System.out.println("[APDUCommands] verifyPinWithResult: PIN bytes (hex): " + bytesToHex(pinUser));
                System.out.println("[APDUCommands] verifyPinWithResult: PIN length: " + pinUser.length);
            }

            System.out.println("[APDUCommands] verifyPinWithResult: Sending APDU command...");

            // V3 Format: [PIN_user (6)]
            ResponseAPDU resp = send(INS_VERIFY_PIN_AND_READ_DATA, (byte) 0, (byte) 0, pinUser, 255);
            int sw = resp.getSW();

            System.out.println("[APDUCommands] verifyPinWithResult: Response SW: " + String.format("0x%04X", sw));

            // Xử lý kết quả
            if (sw == 0x9000) {
                // Success
                byte[] data = resp.getData();
                if (data != null && data.length >= 7) {
                    byte status = data[0];
                    if (status == 0x00) {
                        short length = (short) (((data[1] & 0xFF) << 8) | (data[2] & 0xFF));
                        if (length > 0 && data.length >= 3 + length + 4) {
                            byte[] patientData = new byte[4 + length + 4];
                            patientData[0] = (byte) (length >> 24);
                            patientData[1] = (byte) (length >> 16);
                            patientData[2] = (byte) (length >> 8);
                            patientData[3] = (byte) (length & 0xFF);
                            System.arraycopy(data, 3, patientData, 4, length);
                            System.arraycopy(data, 3 + length, patientData, 4 + length, 4);

                            VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_SUCCESS);
                            result.patientData = patientData;
                            result.swCode = sw;
                            return result;
                        }
                    }
                }
                // Success but no data
                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_SUCCESS);
                result.swCode = sw;
                return result;

            } else if ((sw & 0xFFF0) == 0x63C0) {
                // PIN sai, có retry counter
                int retries = sw & 0x000F;
                System.err.println("[APDUCommands] verifyPinWithResult: PIN SAI! Còn " + retries + "/5 lần thử");

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_WRONG_PIN);
                result.retriesLeft = retries;
                result.swCode = sw;
                result.errorMessage = "Mã PIN không đúng! Còn " + retries
                        + "/5 lần thử.\nSau 5 lần sai, thẻ sẽ bị KHÓA.";
                return result;

            } else if (sw == 0x6983) {
                // Card blocked
                System.err.println("[APDUCommands] verifyPinWithResult: THẺ BỊ KHÓA!");

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_CARD_BLOCKED);
                result.retriesLeft = 0;
                result.swCode = sw;
                result.errorMessage = "THẺ ĐÃ BỊ KHÓA!\n\nVui lòng liên hệ quầy Admin để mở khóa thẻ.";
                return result;

            } else if (sw == 0x6985) {
                // Card not issued
                System.err.println("[APDUCommands] verifyPinWithResult: Thẻ chưa được phát hành!");

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_CARD_NOT_ISSUED);
                result.swCode = sw;
                result.errorMessage = "Thẻ chưa được phát hành!\n\nVui lòng liên hệ quầy Admin để phát hành thẻ.";
                return result;

            } else if (sw == 0x6F50) {
                // NullPointerException - crypto không được khởi tạo
                System.err.println("[APDUCommands] verifyPinWithResult: Crypto objects NULL - Applet cần reinstall!");

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_CRYPTO_ERROR);
                result.swCode = sw;
                result.errorMessage = "Applet lỗi khởi tạo!\n\nVui lòng reinstall applet trên thẻ.\n(Error: 0x6F50)";
                return result;

            } else if (sw == 0x6F99) {
                // Exception chung - lỗi không xác định trong applet
                System.err.println("[APDUCommands] verifyPinWithResult: General exception in applet!");

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_UNKNOWN_ERROR);
                result.swCode = sw;
                result.errorMessage = "Lỗi không xác định trong applet!\n\n(Error: 0x6F99)";
                return result;

            } else if ((sw & 0xFFF0) == 0x6F00) {
                // Crypto error
                int reason = sw & 0x000F;
                System.err.println("[APDUCommands] verifyPinWithResult: Crypto error, reason: " + reason);

                String errorDetail = "";
                switch (sw) {
                    case 0x6F01:
                        errorDetail = "Hash calculation failed";
                        break;
                    case 0x6F03:
                        errorDetail = "KDF calculation failed";
                        break;
                    case 0x6F04:
                        errorDetail = "AES decrypt MK_user failed";
                        break;
                    case 0x6F05:
                        errorDetail = "AES decrypt patient data failed";
                        break;
                    case 0x6F06:
                        errorDetail = "AES decrypt balance failed";
                        break;
                    default:
                        errorDetail = "Crypto error reason " + reason;
                }

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_CRYPTO_ERROR);
                result.swCode = sw;
                result.errorMessage = "Lỗi bảo mật trên thẻ!\n\n" + errorDetail + "\n(Error: 0x"
                        + String.format("%04X", sw) + ")";
                return result;

            } else {
                // Unknown error
                System.err.println("[APDUCommands] verifyPinWithResult: Unknown SW: " + String.format("0x%04X", sw));

                VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_UNKNOWN_ERROR);
                result.swCode = sw;
                result.errorMessage = "Lỗi không xác định!\n\n(Error code: 0x" + String.format("%04X", sw) + ")";
                return result;
            }

        } catch (CardException e) {
            System.err.println("[APDUCommands] verifyPinWithResult: CardException: " + e.getMessage());
            e.printStackTrace();

            VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_UNKNOWN_ERROR);
            result.errorMessage = "Lỗi giao tiếp với thẻ!\n\n" + e.getMessage();
            return result;

        } catch (Exception e) {
            System.err.println("[APDUCommands] verifyPinWithResult: Exception: " + e.getMessage());
            e.printStackTrace();

            VerifyPinResult result = new VerifyPinResult(VerifyPinResult.STATUS_UNKNOWN_ERROR);
            result.errorMessage = "Lỗi hệ thống!\n\n" + e.getMessage();
            return result;
        }
    }

    /**
     * V3: Update patient data
     * 
     * @param patientData Patient data (plaintext)
     * @return true nếu thành công
     */
    public boolean updatePatientData(byte[] patientData) {
        try {
            // V3 Format: [patient_data_length (2)] [patient_data]
            byte[] data = new byte[2 + patientData.length];
            setShort(data, 0, (short) patientData.length);
            System.arraycopy(patientData, 0, data, 2, patientData.length);

            ResponseAPDU resp = send(INS_UPDATE_PATIENT_DATA, (byte) 0, (byte) 0, data, 1);
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
     * Cập nhật UserData (V3: wrapper của updatePatientData)
     * 
     * @param newUserData UserData bytes
     * @return true nếu thành công
     */
    public boolean updateUserData(byte[] newUserData) {
        return updatePatientData(newUserData);
    }

    /**
     * CHANGE_PIN (0x0A) - User tự đổi PIN khi biết PIN cũ
     * 
     * @param oldPinPlaintext PIN cũ (6 bytes)
     * @param newPinPlaintext PIN mới (6 bytes)
     * @return true nếu thành công
     */
    public boolean changePin(byte[] oldPinPlaintext, byte[] newPinPlaintext) {
        try {
            System.out.println("[APDUCommands] changePin: Bắt đầu đổi PIN user");
            System.out.println("[APDUCommands] changePin: PIN cũ length = "
                    + (oldPinPlaintext != null ? oldPinPlaintext.length : 0));
            System.out.println("[APDUCommands] changePin: PIN mới length = "
                    + (newPinPlaintext != null ? newPinPlaintext.length : 0));

            // DEBUG: In ra PIN bytes để kiểm tra
            if (oldPinPlaintext != null) {
                System.out.println("[APDUCommands] changePin: PIN cũ bytes (hex): " + bytesToHex(oldPinPlaintext));
                System.out.println("[APDUCommands] changePin: PIN cũ bytes (ascii): "
                        + new String(oldPinPlaintext, StandardCharsets.UTF_8));
            }
            if (newPinPlaintext != null) {
                System.out.println("[APDUCommands] changePin: PIN mới bytes (hex): " + bytesToHex(newPinPlaintext));
                System.out.println("[APDUCommands] changePin: PIN mới bytes (ascii): "
                        + new String(newPinPlaintext, StandardCharsets.UTF_8));
            }

            // Validate input
            if (oldPinPlaintext == null || newPinPlaintext == null) {
                System.err.println("[APDUCommands] changePin: PIN không được null");
                return false;
            }

            if (oldPinPlaintext.length != 6 || newPinPlaintext.length != 6) {
                System.err.println("[APDUCommands] changePin: PIN phải là 6 bytes");
                return false;
            }

            // V3 Format: [PIN_user_old (6)] [PIN_user_new (6)]
            byte[] data = new byte[oldPinPlaintext.length + newPinPlaintext.length];
            int offset = 0;

            // Ghi PIN_user_old (6 bytes)
            System.arraycopy(oldPinPlaintext, 0, data, offset, oldPinPlaintext.length);
            offset += oldPinPlaintext.length;

            // Ghi PIN_user_new (6 bytes)
            System.arraycopy(newPinPlaintext, 0, data, offset, newPinPlaintext.length);

            String logMsg = "[APDUCommands] CHANGE_PIN (V3):\n" +
                    "  - INS: 0x" + String.format("%02X", INS_CHANGE_PIN) + "\n" +
                    "  - Total data length: " + data.length + "\n" +
                    "  - Data (hex): " + bytesToHex(data) + "\n" +
                    "  - PIN cũ (hex): " + bytesToHex(oldPinPlaintext) + "\n" +
                    "  - PIN mới (hex): " + bytesToHex(newPinPlaintext);
            System.out.println(logMsg);

            ResponseAPDU resp = send(INS_CHANGE_PIN, (byte) 0, (byte) 0, data, 1);
            int sw = resp.getSW();
            System.out.println("[APDUCommands] CHANGE_PIN response SW: " + String.format("0x%04X", sw));

            if (sw == 0x9000) {
                byte[] result = resp.getData();
                if (result != null && result.length > 0 && result[0] == 0x00) {
                    System.out.println("[APDUCommands] Đổi PIN user thành công!");
                    return true;
                }
            } else if ((sw & 0xFF00) == 0x63C0) {
                // PIN retry counter in SW2
                int retries = sw & 0x000F;
                System.err.println("[APDUCommands] PIN cũ sai! Còn lại " + retries + " lần thử");
                if (retries == 0) {
                    System.err.println("[APDUCommands] Thẻ đã bị khóa do nhập sai PIN quá nhiều lần!");
                }
            } else {
                String errorMsg = "[APDUCommands] CHANGE_PIN THẤT BẠI! SW: " + String.format("0x%04X", sw);
                System.err.println(errorMsg);

                // Giải thích các mã lỗi chi tiết
                if (sw == 0x6D00) {
                    System.err.println("  -> Lệnh không được hỗ trợ (INS_NOT_SUPPORTED)");
                    System.err.println("     Có thể applet chưa được build lại hoặc INS code bị sai");
                } else if (sw == 0x6700) {
                    System.err.println("  -> Độ dài dữ liệu sai (WRONG_LENGTH)");
                } else if (sw == 0x6A80) {
                    System.err.println("  -> PIN mới trùng PIN cũ (WRONG_DATA)");
                    System.err.println("     PIN mới phải khác PIN cũ");
                } else if (sw == 0x6983) {
                    System.err.println("  -> Thẻ bị khóa (Card blocked)");
                } else if (sw == 0x6985) {
                    System.err.println("  -> Điều kiện không thỏa mãn - Thẻ chưa được phát hành?");
                } else if (sw == 0x6F00) {
                    System.err.println("  -> Lỗi không xác định từ applet");
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("[APDUCommands] Exception trong changePin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Decode error codes từ ISSUE_CARD command
     */
    private static String decodeIssueCardError(int sw) {
        switch (sw) {
            case 0x6F01:
                return "✗ RSA KEY GENERATION FAILED (0x6F01)\n" +
                        "   → RSA key pair không thể generate\n" +
                        "   → Kiểm tra: Card có hỗ trợ RSA 1024-bit không?";

            case 0x6F02:
                return "✗ RSA PUBLIC KEY EXPORT FAILED (0x6F02)\n" +
                        "   → Không thể export public key\n" +
                        "   → Lỗi bất thường (PK nên export được)";

            case 0x6F03:
                return "✗ HASH OPERATION FAILED (0x6F03)\n" +
                        "   → SHA-1 hash thất bại\n" +
                        "   → Kiểm tra: Card có hỗ trợ SHA-1 không?";

            case 0x6F04:
                return "✗ AES ENCRYPTION FAILED (0x6F04)\n" +
                        "   → AES-128 encryption thất bại\n" +
                        "   → Kiểm tra: Card có hỗ trợ AES-128 không?";

            case 0x6F10:
            case 0x6F11:
            case 0x6F12:
            case 0x6F13:
            case 0x6F14:
            case 0x6F15:
            case 0x6F16:
            case 0x6F17:
            case 0x6F18:
            case 0x6F19:
            case 0x6F1A:
            case 0x6F1B:
            case 0x6F1C:
            case 0x6F1D:
            case 0x6F1E:
            case 0x6F1F:
                int reason = sw & 0x0F;
                return String.format("✗ RSA KEY GENERATION CRYPTO ERROR (0x%04X)\n", sw) +
                        "   → CryptoException reason code: " + reason + "\n" +
                        "   → Lỗi crypto trong quá trình generate RSA key";

            case 0x6F20:
            case 0x6F21:
            case 0x6F22:
            case 0x6F23:
            case 0x6F24:
            case 0x6F25:
            case 0x6F26:
            case 0x6F27:
            case 0x6F28:
            case 0x6F29:
            case 0x6F2A:
            case 0x6F2B:
            case 0x6F2C:
            case 0x6F2D:
            case 0x6F2E:
            case 0x6F2F:
                int cryptoReason = sw & 0x0F;
                return String.format("✗ UNCAUGHT CRYPTO EXCEPTION (0x%04X)\n", sw) +
                        "   → CryptoException reason: " + cryptoReason + "\n" +
                        "   → Exception xảy ra ngoài các try-catch block";

            case 0x6F30:
                return "✗ BUFFER OVERFLOW (0x6F30)\n" +
                        "   → ArrayIndexOutOfBoundsException\n" +
                        "   → Response quá lớn (PK + SK vượt quá buffer APDU)\n" +
                        "   → GIẢI PHÁP: Bỏ qua SK export (chỉ gửi PK)";

            case 0x6F31:
                return "✗ RESPONSE SEND FAILED (0x6F31)\n" +
                        "   → Exception trong apdu.setOutgoingAndSend()\n" +
                        "   → Response length: Có thể quá lớn hoặc không hợp lệ";

            case 0x6F40:
                return "✗ INVALID PATIENT INFO LENGTH (0x6F40)\n" +
                        "   → Patient info length <= 0 hoặc > MAX\n" +
                        "   → Kiểm tra data format";

            case 0x6985:
                return "✗ CONDITIONS NOT SATISFIED (0x6985)\n" +
                        "   → Thẻ đã được phát hành rồi\n" +
                        "   → Không thể issue lại";

            case 0x6F00:
                return "✗ UNKNOWN ERROR (0x6F00)\n" +
                        "   → Lỗi không xác định từ applet\n" +
                        "   → Có thể: Buffer overflow, exception không catch được";

            default:
                return String.format("✗ ERROR (0x%04X)\n   → Mã lỗi: %s",
                        sw, getStatusWordDescription(sw));
        }
    }

    /**
     * Get human-readable description of status word
     */
    private static String getStatusWordDescription(int sw) {
        switch (sw) {
            case 0x9000:
                return "Success";
            case 0x6300:
                return "Authentication failed";
            case 0x6700:
                return "Wrong length";
            case 0x6982:
                return "Security status not satisfied";
            case 0x6983:
                return "Authentication method blocked";
            case 0x6984:
                return "Referenced data invalidated";
            case 0x6985:
                return "Conditions not satisfied";
            case 0x6986:
                return "Command not allowed";
            case 0x6A80:
                return "Incorrect parameters";
            case 0x6A82:
                return "File not found";
            case 0x6A86:
                return "Incorrect P1 P2";
            case 0x6D00:
                return "Instruction not supported";
            case 0x6E00:
                return "Class not supported";
            case 0x6F00:
                return "Unknown error";
            default:
                return String.format("0x%04X", sw);
        }
    }

    // Helper method để set short (tương tự javacard.framework.Util)
    private void setShort(byte[] bArray, int bOff, short sValue) {
        bArray[bOff] = (byte) (sValue >> 8);
        bArray[bOff + 1] = (byte) sValue;
    }
}
