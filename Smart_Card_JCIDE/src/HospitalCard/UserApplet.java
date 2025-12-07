package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * UserApplet V3 - Applet cho thẻ User (thẻ bệnh nhân)
 * Luồng mới với K_master và PIN_admin_reset derive động
 * 
 * Package: HospitalCard
 * Package AID: 11 22 33 44 55
 * Applet AID:  11 22 33 44 55 01
 * 
 * Các APDU commands V3:
 * - GET_STATUS (0x01): Lấy trạng thái thẻ
 * - ISSUE_CARD (0x02): Phát hành thẻ
 * - VERIFY_PIN_AND_READ_DATA (0x03): Xác thực PIN và đọc dữ liệu
 * - UPDATE_PATIENT_DATA (0x04): Cập nhật dữ liệu bệnh nhân
 * - ADMIN_RESET_PIN (0x05): Reset PIN bằng admin PIN
 */
public class UserApplet extends Applet {
    
    // CLA byte
    private static final byte CLA = (byte)0x80;
    
    // Instruction codes V3
    private static final byte INS_GET_STATUS = (byte)0x01;
    private static final byte INS_ISSUE_CARD = (byte)0x02;
    private static final byte INS_VERIFY_PIN_AND_READ_DATA = (byte)0x03;
    private static final byte INS_UPDATE_PATIENT_DATA = (byte)0x04;
    private static final byte INS_ADMIN_RESET_PIN = (byte)0x05;
    
    // Constants
    private static final byte MAX_PIN_TRIES = 3;
    private static final short CARD_ID_LENGTH = 16;
    private static final short HASH_LENGTH = 20; // SHA-1 (JavaCard 2.2.x compatible)
    private static final short MK_USER_LENGTH = 16; // AES-128
    private static final short MK_USER_ENC_LENGTH = 32; // AES-128 + padding
    private static final short MAX_PATIENT_DATA_LENGTH = 256;
    
    // Trạng thái thẻ
    private byte initialized; // 0 = chưa phát hành, 1 = đã phát hành
    private byte pinRetryCounter; // Số lần thử PIN còn lại (0-3)
    private byte blockedFlag; // 0 = không khóa, 1 = bị khóa
    
    // Card ID
    private byte[] cardID;
    
    // PIN hashes
    private byte[] hashPinUser; // SHA-256(PIN_user)
    private byte[] hashPinAdminReset; // SHA-256(PIN_admin_reset)
    
    // Encrypted Master Key
    private byte[] encUser; // MK_user encrypted with K_user = KDF(PIN_user)
    private byte[] encAdmin; // MK_user encrypted with K_admin = KDF(PIN_admin_reset)
    
    // Encrypted Patient Data
    private byte[] encPatient; // Patient data encrypted with MK_user
    private short encPatientLength;
    
    // Transient (RAM) - MK_user chỉ tồn tại khi đã xác thực
    private byte[] mkUser; // Master Key (transient, cleared on deselect)
    
    // Crypto objects
    private MessageDigest sha256; // Note: Actually SHA-1 for JavaCard 2.2.x compatibility
    private Cipher aesCipher;
    private AESKey aesKey;
    private RandomData randomData;

    protected UserApplet(byte[] bArray, short bOffset, byte bLength) {
        // Cấp phát bộ nhớ EEPROM (persistent)
        cardID = new byte[CARD_ID_LENGTH];
        hashPinUser = new byte[HASH_LENGTH];
        hashPinAdminReset = new byte[HASH_LENGTH];
        encUser = new byte[MK_USER_ENC_LENGTH];
        encAdmin = new byte[MK_USER_ENC_LENGTH];
        encPatient = new byte[MAX_PATIENT_DATA_LENGTH];
        
        // Cấp phát bộ nhớ RAM (transient)
        mkUser = JCSystem.makeTransientByteArray((short)(MK_USER_LENGTH + 16), JCSystem.CLEAR_ON_DESELECT);
        
        // Khởi tạo trạng thái
        initialized = 0;
        pinRetryCounter = MAX_PIN_TRIES;
        blockedFlag = 0;
        encPatientLength = 0;
        
        // Khởi tạo crypto
        try {
            sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA, false); // SHA-1 for JavaCard 2.2.x
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
            aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            randomData = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        } catch (Exception e) {
            // Crypto not available
        }
        
        register(bArray, (short)(bOffset + 1), bArray[bOffset]);
    }

    /**
     * Install applet
     * AID: 11 22 33 44 55 01
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new UserApplet(bArray, bOffset, bLength);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            // Clear transient data on select
            return;
        }

        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buf[ISO7816.OFFSET_INS];

        // Các lệnh cần nhận dữ liệu
        if (ins == INS_ISSUE_CARD || ins == INS_VERIFY_PIN_AND_READ_DATA ||
            ins == INS_UPDATE_PATIENT_DATA || ins == INS_ADMIN_RESET_PIN) {
            apdu.setIncomingAndReceive();
        }

        switch (ins) {
            case INS_GET_STATUS:
                getStatus(apdu);
                break;
                
            case INS_ISSUE_CARD:
                issueCard(apdu);
                break;
                
            case INS_VERIFY_PIN_AND_READ_DATA:
                verifyPinAndReadData(apdu);
                break;
                
            case INS_UPDATE_PATIENT_DATA:
                updatePatientData(apdu);
                break;
                
            case INS_ADMIN_RESET_PIN:
                adminResetPin(apdu);
                break;
                
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /**
     * GET_STATUS (0x01)
     * Response: [initialized (1 byte), pin_retry_counter (1 byte), blocked_flag (1 byte), cardID (16 bytes)]
     */
    private void getStatus(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short offset = 0;
        
        buf[offset++] = initialized;
        buf[offset++] = pinRetryCounter;
        buf[offset++] = blockedFlag;
        
        Util.arrayCopyNonAtomic(cardID, (short)0, buf, offset, CARD_ID_LENGTH);
        offset += CARD_ID_LENGTH;
        
        apdu.setOutgoingAndSend((short)0, offset);
    }

    /**
     * ISSUE_CARD (0x02)
     * Data: [cardID (16 bytes, optional)] [patient_info_length (2 bytes)] [patient_info] [PIN_user (6 bytes)] [PIN_admin_reset (6 bytes)]
     * Response: [status (1 byte)] (0x00 = success)
     * 
     * Note: Nếu cardID được cung cấp (16 bytes đầu tiên không phải toàn 0), thẻ sẽ dùng cardID đó.
     * Nếu không, thẻ sẽ tự sinh cardID ngẫu nhiên.
     */
    private void issueCard(APDU apdu) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized == 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - already issued
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        try {
            // 1. Parse cardID (16 bytes, optional)
            // Kiểm tra xem có cardID được cung cấp không (không phải toàn 0)
            boolean hasProvidedCardID = false;
            short cardIDOffset = offset;
            for (short i = 0; i < CARD_ID_LENGTH; i++) {
                if (buf[(short)(cardIDOffset + i)] != 0) {
                    hasProvidedCardID = true;
                    break;
                }
            }
            
            if (hasProvidedCardID) {
                // Dùng cardID từ backend
                Util.arrayCopyNonAtomic(buf, cardIDOffset, cardID, (short)0, CARD_ID_LENGTH);
                offset += CARD_ID_LENGTH;
            } else {
                // Backend không cung cấp cardID, thẻ tự sinh
                randomData.generateData(cardID, (short)0, CARD_ID_LENGTH);
                // Không tăng offset vì không có cardID trong data
            }
            
            // 2. Parse patient_info_length
            short patientInfoLength = Util.getShort(buf, offset);
            offset += 2;
            
            if (patientInfoLength <= 0 || patientInfoLength > MAX_PATIENT_DATA_LENGTH) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            
            // 3. Lưu patient_info vào buffer tạm (sẽ mã hóa sau)
            short patientInfoOffset = offset;
            offset += patientInfoLength;
            
            // 4. Parse PIN_user (6 bytes)
            short pinUserOffset = offset;
            short pinUserLength = 6;
            offset += pinUserLength;
            
            // 5. Parse PIN_admin_reset (6 bytes)
            short pinAdminResetOffset = offset;
            short pinAdminResetLength = 6;
            
            // 6. Sinh MK_user ngẫu nhiên (16 bytes)
            
            // 6. Sinh MK_user ngẫu nhiên (16 bytes)
            randomData.generateData(mkUser, (short)0, MK_USER_LENGTH);
            
            // 7. Tính hash_PIN_user = SHA-1(PIN_user)
            sha256.reset();
            sha256.doFinal(buf, pinUserOffset, pinUserLength, hashPinUser, (short)0);
            
            // 8. Tính hash_PIN_admin_reset = SHA-1(PIN_admin_reset)
            sha256.reset();
            sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, hashPinAdminReset, (short)0);
            
            // 9. Tính K_user = KDF(PIN_user) - simplified: SHA-1(PIN_user || "USER")
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.update(buf, pinUserOffset, pinUserLength);
            sha256.doFinal(new byte[]{(byte)'U', (byte)'S', (byte)'E', (byte)'R'}, (short)0, (short)4, tempKey, (short)0);
            
            // 10. Bọc MK_user: Enc_user = AES(K_user, MK_user)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(mkUser, (short)0, MK_USER_LENGTH, encUser, (short)0);
            
            // 11. Tính K_admin = KDF(PIN_admin_reset) - simplified: SHA-1(PIN_admin_reset || "ADMIN")
            sha256.reset();
            sha256.update(buf, pinAdminResetOffset, pinAdminResetLength);
            sha256.doFinal(new byte[]{(byte)'A', (byte)'D', (byte)'M', (byte)'I', (byte)'N'}, (short)0, (short)5, tempKey, (short)0);
            
            // 12. Bọc MK_user: Enc_admin = AES(K_admin, MK_user)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(mkUser, (short)0, MK_USER_LENGTH, encAdmin, (short)0);
            
            // 13. Mã hóa dữ liệu bệnh nhân: Enc_patient = AES(MK_user, patient_data)
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            
            // Pad patient data to multiple of 16 bytes
            short paddedLength = (short)((patientInfoLength + 15) / 16 * 16);
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buf, patientInfoOffset, paddedData, (short)0, patientInfoLength);
            // Fill padding with zeros (already zero by default)
            
            encPatientLength = aesCipher.doFinal(paddedData, (short)0, paddedLength, encPatient, (short)0);
            
            // 14. Đặt initialized = 1
            initialized = 1;
            
            // 15. Clear sensitive data from RAM
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            Util.arrayFillNonAtomic(tempKey, (short)0, (short)20, (byte)0);
            
            // 16. Return success
            buf[0] = (byte)0x00;
            apdu.setOutgoingAndSend((short)0, (short)1);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }

    /**
     * VERIFY_PIN_AND_READ_DATA (0x03)
     * Data: [PIN_user (6 bytes)]
     * Response: [status (1 byte)] [patient_data_length (2 bytes)] [patient_data] hoặc lỗi
     */
    private void verifyPinAndReadData(APDU apdu) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kiểm tra thẻ bị khóa chưa
        if (blockedFlag == 1) {
            ISOException.throwIt((short)0x6983); // Card blocked
        }
        
        byte[] buf = apdu.getBuffer();
        short pinUserOffset = ISO7816.OFFSET_CDATA;
        short pinUserLength = 6;
        
        try {
            // 1. Tính Hash(PIN_user_input)
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.doFinal(buf, pinUserOffset, pinUserLength, tempHash, (short)0);
            
            // 2. So sánh với hash_PIN_user
            if (Util.arrayCompare(tempHash, (short)0, hashPinUser, (short)0, HASH_LENGTH) != 0) {
                // PIN sai
                pinRetryCounter--;
                if (pinRetryCounter == 0) {
                    blockedFlag = 1;
                }
                // Return SW with retry counter in SW2
                ISOException.throwIt((short)(0x63C0 | pinRetryCounter));
            }
            
            // 3. PIN đúng - Reset retry counter
            pinRetryCounter = MAX_PIN_TRIES;
            
            // 4. Tính K_user = KDF(PIN_user)
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.update(buf, pinUserOffset, pinUserLength);
            sha256.doFinal(new byte[]{(byte)'U', (byte)'S', (byte)'E', (byte)'R'}, (short)0, (short)4, tempKey, (short)0);
            
            // 5. Giải MK_user từ Enc_user: MK_user = AES_Decrypt(K_user, Enc_user)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encUser, (short)0, MK_USER_LENGTH, mkUser, (short)0);
            
            // 6. Giải mã dữ liệu bệnh nhân: patient_data = AES_Decrypt(MK_user, Enc_patient)
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            
            // Decrypt to temporary buffer
            byte[] decryptedData = JCSystem.makeTransientByteArray(MAX_PATIENT_DATA_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            short decryptedLength = aesCipher.doFinal(encPatient, (short)0, encPatientLength, decryptedData, (short)0);
            
            // Find actual data length (remove padding zeros from end)
            while (decryptedLength > 0 && decryptedData[(short)(decryptedLength - 1)] == 0) {
                decryptedLength--;
            }
            
            // 7. Return: [status (0x00)] [length (2 bytes)] [data]
            short offset = 0;
            buf[offset++] = (byte)0x00; // Success
            Util.setShort(buf, offset, decryptedLength);
            offset += 2;
            Util.arrayCopyNonAtomic(decryptedData, (short)0, buf, offset, decryptedLength);
            offset += decryptedLength;
            
            // 8. Clear sensitive data
            Util.arrayFillNonAtomic(tempKey, (short)0, (short)20, (byte)0);
            Util.arrayFillNonAtomic(tempHash, (short)0, HASH_LENGTH, (byte)0);
            
            apdu.setOutgoingAndSend((short)0, offset);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }

    /**
     * UPDATE_PATIENT_DATA (0x04)
     * Data: [patient_data_length (2 bytes)] [patient_data]
     * Response: [status (1 byte)] (0x00 = success)
     * 
     * Yêu cầu: mkUser phải đã được mở (qua VERIFY_PIN_AND_READ_DATA)
     */
    private void updatePatientData(APDU apdu) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kiểm tra mkUser đã được mở chưa (check if first byte is not 0)
        if (mkUser[0] == 0 && mkUser[1] == 0 && mkUser[2] == 0 && mkUser[3] == 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        try {
            // 1. Parse patient_data_length
            short patientDataLength = Util.getShort(buf, offset);
            offset += 2;
            
            if (patientDataLength <= 0 || patientDataLength > MAX_PATIENT_DATA_LENGTH) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            
            // 2. Mã hóa dữ liệu mới: Enc_patient_new = AES(MK_user, new_patient_data)
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            
            // Pad to multiple of 16 bytes
            short paddedLength = (short)((patientDataLength + 15) / 16 * 16);
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buf, offset, paddedData, (short)0, patientDataLength);
            
            // 3. Ghi đè Enc_patient trong EEPROM
            encPatientLength = aesCipher.doFinal(paddedData, (short)0, paddedLength, encPatient, (short)0);
            
            // 4. Return success
            buf[0] = (byte)0x00;
            apdu.setOutgoingAndSend((short)0, (short)1);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }

    /**
     * ADMIN_RESET_PIN (0x05)
     * Data: [PIN_admin_reset (6 bytes)] [new_PIN_user (6 bytes)]
     * Response: [status (1 byte)] (0x00 = success)
     */
    private void adminResetPin(APDU apdu) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        // Parse PIN_admin_reset (6 bytes)
        short pinAdminResetOffset = offset;
        short pinAdminResetLength = 6;
        offset += pinAdminResetLength;
        
        // Parse new_PIN_user (6 bytes)
        short newPinUserOffset = offset;
        short newPinUserLength = 6;
        
        try {
            // 1. Xác thực admin PIN - Tính Hash(PIN_admin_reset_input)
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, tempHash, (short)0);
            
            // 2. So sánh với hash_PIN_admin_reset
            if (Util.arrayCompare(tempHash, (short)0, hashPinAdminReset, (short)0, HASH_LENGTH) != 0) {
                // Admin PIN sai
                ISOException.throwIt((short)0x6300); // Admin PIN incorrect
            }
            
            // 3. Tính K_admin = KDF(PIN_admin_reset_input)
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.update(buf, pinAdminResetOffset, pinAdminResetLength);
            sha256.doFinal(new byte[]{(byte)'A', (byte)'D', (byte)'M', (byte)'I', (byte)'N'}, (short)0, (short)5, tempKey, (short)0);
            
            // 4. Giải MK_user từ Enc_admin: MK_user = AES_Decrypt(K_admin, Enc_admin)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encAdmin, (short)0, MK_USER_LENGTH, mkUser, (short)0);
            
            // 5. Từ new_PIN_user: Tính K_user_new = KDF(new_PIN_user)
            sha256.reset();
            sha256.update(buf, newPinUserOffset, newPinUserLength);
            sha256.doFinal(new byte[]{(byte)'U', (byte)'S', (byte)'E', (byte)'R'}, (short)0, (short)4, tempKey, (short)0);
            
            // 6. Enc_user_new = AES(K_user_new, MK_user)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(mkUser, (short)0, MK_USER_LENGTH, encUser, (short)0);
            
            // 7. hash_PIN_user_new = Hash(new_PIN_user)
            sha256.reset();
            sha256.doFinal(buf, newPinUserOffset, newPinUserLength, hashPinUser, (short)0);
            
            // 8. Reset counters
            pinRetryCounter = MAX_PIN_TRIES;
            blockedFlag = 0;
            
            // 9. Clear sensitive data
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            Util.arrayFillNonAtomic(tempKey, (short)0, (short)20, (byte)0);
            Util.arrayFillNonAtomic(tempHash, (short)0, HASH_LENGTH, (byte)0);
            
            // 10. Return success
            buf[0] = (byte)0x00;
            apdu.setOutgoingAndSend((short)0, (short)1);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }
}
