package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * UserApplet V3 - Applet cho th User (th bnh nhân)
 * Lung mi vi K_master và PIN_admin_reset derive ng
 * 
 * Package: HospitalCard
 * Package AID: 11 22 33 44 55
 * Applet AID:  11 22 33 44 55 01
 * 
 * Các APDU commands V3:
 * - GET_STATUS (0x01): Ly trng thái th
 * - ISSUE_CARD (0x02): Phát hành th
 * - VERIFY_PIN_AND_READ_DATA (0x03): Xác thc PIN và c d liu
 * - UPDATE_PATIENT_DATA (0x04): Cp nht d liu bnh nhân
 * - ADMIN_RESET_PIN (0x05): Reset PIN bng admin PIN
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
    private static final byte INS_SIGN_CHALLENGE = (byte)0x10; // V3: Sign challenge with SK_user (changed from 0x06 to avoid confusion with SW 0x6XXX)
    
    // Constants
    private static final byte MAX_PIN_TRIES = 3;
    private static final short CARD_ID_LENGTH = 16;
    private static final short HASH_LENGTH = 20; // SHA-1 (JavaCard 2.2.x compatible)
    private static final short MK_USER_LENGTH = 16; // AES-128
    private static final short MK_USER_ENC_LENGTH = 32; // AES-128 + padding
    private static final short MAX_PATIENT_DATA_LENGTH = 256;
    
    // Trng thái th
    private byte initialized; // 0 = cha phát hành, 1 = ã phát hành
    private byte pinRetryCounter; // S ln th PIN còn li (0-3)
    private byte blockedFlag; // 0 = không khóa, 1 = b khóa
    
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
    
    // Transient (RAM) - MK_user ch tn ti khi ã xác thc
    private byte[] mkUser; // Master Key (transient, cleared on deselect)
    
    // RSA Key Pair (V3 - for challenge-response authentication)
    private KeyPair rsaKeyPair;
    private RSAPrivateKey skUser;
    private RSAPublicKey pkUser;
    private Cipher rsaCipher;
    
    // Crypto objects
    private MessageDigest sha256; // Note: Actually SHA-1 for JavaCard 2.2.x compatibility
    private Cipher aesCipher;
    private AESKey aesKey;
    private RandomData randomData;

    protected UserApplet(byte[] bArray, short bOffset, byte bLength) {
        // Cp phát b nh EEPROM (persistent)
        cardID = new byte[CARD_ID_LENGTH];
        hashPinUser = new byte[HASH_LENGTH];
        hashPinAdminReset = new byte[HASH_LENGTH];
        encUser = new byte[MK_USER_ENC_LENGTH];
        encAdmin = new byte[MK_USER_ENC_LENGTH];
        encPatient = new byte[MAX_PATIENT_DATA_LENGTH];
        
        // Cp phát b nh RAM (transient)
        mkUser = JCSystem.makeTransientByteArray((short)(MK_USER_LENGTH + 16), JCSystem.CLEAR_ON_DESELECT);
        
        // Khi to trng thái
        initialized = 0;
        pinRetryCounter = MAX_PIN_TRIES;
        blockedFlag = 0;
        encPatientLength = 0;
        
        // Khi to crypto
        try {
            sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA, false); // SHA-1 for JavaCard 2.2.x
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
            aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            randomData = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            
            // Khi to RSA (V3)
            rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
            rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
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

        // Các lnh cn nhn d liu
        if (ins == INS_ISSUE_CARD || ins == INS_VERIFY_PIN_AND_READ_DATA ||
            ins == INS_UPDATE_PATIENT_DATA || ins == INS_ADMIN_RESET_PIN ||
            ins == INS_SIGN_CHALLENGE) {
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
                
            case INS_SIGN_CHALLENGE:
                signChallenge(apdu);
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
     * Response: [status (1 byte)] [PK_user data] [SK_user data (demo)]
     * 
     * V3 - RSA Support:
     * Response format:
     * - status (1 byte): 0x00 = success
     * - PK_user: [mod_len (2)] [modulus] [exp_len (2)] [exponent]
     * - SK_user: [mod_len (2)] [modulus] [exp_len (2)] [exponent] (DEMO ONLY)
     * 
     * Note: Nu cardID c cung cp (16 bytes u tiên không phi toàn 0), th s dùng cardID ó.
     * Nu không, th s t sinh cardID ngu nhiên.
     */
    private void issueCard(APDU apdu) {
        // Kim tra th ã c phát hành cha
        if (initialized == 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - already issued
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        try {
            // 1. Parse cardID (16 bytes, optional)
            // Kim tra xem có cardID c cung cp không (không phi toàn 0)
            boolean hasProvidedCardID = false;
            short cardIDOffset = offset;
            for (short i = 0; i < CARD_ID_LENGTH; i++) {
                if (buf[(short)(cardIDOffset + i)] != 0) {
                    hasProvidedCardID = true;
                    break;
                }
            }
            
            if (hasProvidedCardID) {
                // Dùng cardID t backend
                Util.arrayCopyNonAtomic(buf, cardIDOffset, cardID, (short)0, CARD_ID_LENGTH);
                offset += CARD_ID_LENGTH;
            } else {
                // Backend không cung cp cardID, th t sinh
                randomData.generateData(cardID, (short)0, CARD_ID_LENGTH);
                // Không tng offset vì không có cardID trong data
            }
            
            // 2. Parse patient_info_length
            short patientInfoLength = Util.getShort(buf, offset);
            offset += 2;
            
            if (patientInfoLength <= 0 || patientInfoLength > MAX_PATIENT_DATA_LENGTH) {
                ISOException.throwIt((short)0x6F40); // DEBUG: Invalid patient info length
            }
            
            // 3. Lu patient_info vào buffer tm (s mã hóa sau)
            short patientInfoOffset = offset;
            offset += patientInfoLength;
            
            // 4. Parse PIN_user (6 bytes)
            short pinUserOffset = offset;
            short pinUserLength = 6;
            offset += pinUserLength;
            
            // 5. Parse PIN_admin_reset (6 bytes)
            short pinAdminResetOffset = offset;
            short pinAdminResetLength = 6;
            
            // 6. Sinh MK_user ngu nhiên (16 bytes)
            randomData.generateData(mkUser, (short)0, MK_USER_LENGTH);
            
            // 6.1. Sinh RSA key pair (V3)
            try {
                if (!RSAHelper.generateKeyPair(rsaKeyPair)) {
                    ISOException.throwIt((short)0x6F01); // RSA key generation failed
                }
                skUser = (RSAPrivateKey) rsaKeyPair.getPrivate();
                pkUser = (RSAPublicKey) rsaKeyPair.getPublic();
                
                if (skUser == null || pkUser == null || !skUser.isInitialized() || !pkUser.isInitialized()) {
                    ISOException.throwIt((short)0x6F01); // RSA key not initialized
                }
            } catch (CryptoException ce) {
                ISOException.throwIt((short)(0x6F10 | (ce.getReason() & 0x0F))); // 0x6F1X: RSA gen error
            }
            
            // 7. Tính hash_PIN_user = SHA-1(PIN_user)
            try {
                sha256.reset();
                sha256.doFinal(buf, pinUserOffset, pinUserLength, hashPinUser, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F03); // Hash operation failed
            }
            
            // 8. Tính hash_PIN_admin_reset = SHA-1(PIN_admin_reset)
            try {
                sha256.reset();
                sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, hashPinAdminReset, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F03); // Hash operation failed
            }
            
            // 9. Tính K_user = KDF(PIN_user) - simplified: SHA-1(PIN_user || "USER")
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            try {
                sha256.reset();
                sha256.update(buf, pinUserOffset, pinUserLength);
                sha256.doFinal(new byte[]{(byte)'U', (byte)'S', (byte)'E', (byte)'R'}, (short)0, (short)4, tempKey, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F03); // Hash operation failed
            }
            
            // 10. Bc MK_user: Enc_user = AES(K_user, MK_user)
            try {
                aesKey.setKey(tempKey, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(mkUser, (short)0, MK_USER_LENGTH, encUser, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F04); // AES encryption failed
            }
            
            // 11. Tính K_admin = KDF(PIN_admin_reset) - simplified: SHA-1(PIN_admin_reset || "ADMIN")
            try {
                sha256.reset();
                sha256.update(buf, pinAdminResetOffset, pinAdminResetLength);
                sha256.doFinal(new byte[]{(byte)'A', (byte)'D', (byte)'M', (byte)'I', (byte)'N'}, (short)0, (short)5, tempKey, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F03); // Hash operation failed
            }
            
            // 12. Bc MK_user: Enc_admin = AES(K_admin, MK_user)
            try {
                aesKey.setKey(tempKey, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(mkUser, (short)0, MK_USER_LENGTH, encAdmin, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F04); // AES encryption failed
            }
            
            // 13. Mã hóa d liu bnh nhân: Enc_patient = AES(MK_user, patient_data)
            try {
                aesKey.setKey(mkUser, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                
                // Pad patient data to multiple of 16 bytes
                short paddedLength = (short)((patientInfoLength + 15) / 16 * 16);
                byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
                Util.arrayCopyNonAtomic(buf, patientInfoOffset, paddedData, (short)0, patientInfoLength);
                // Fill padding with zeros (already zero by default)
                
                encPatientLength = aesCipher.doFinal(paddedData, (short)0, paddedLength, encPatient, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F04); // AES encryption failed
            }
            
            // 14. t initialized = 1
            initialized = 1;
            
            // 15. Clear sensitive data from RAM
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            Util.arrayFillNonAtomic(tempKey, (short)0, (short)20, (byte)0);
            
            // 16. Return success with RSA keys (V3)
            short respOffset = 0;
            buf[respOffset++] = (byte)0x00; // Status = success
            
            // Export PUBLIC KEY (always works)
            // PK size estimate: ~135 bytes (2 + 128 + 2 + 3)
            try {
                short pkLen = RSAHelper.getPublicKeyBytes(pkUser, buf, respOffset);
                
                // Safety check: Ensure PK export didn't overflow
                if (pkLen <= 0 || pkLen > 200) {
                    ISOException.throwIt((short)0x6F02); // Invalid PK length
                }
                
                respOffset += pkLen;
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F02); // RSA public key export failed
            } catch (ArrayIndexOutOfBoundsException ae) {
                ISOException.throwIt((short)0x6F30); // Buffer overflow during PK export
            }
            
            // TRY Export PRIVATE KEY (DEMO - may not work on all JavaCards)
            // NOTE: Most JavaCard implementations DO NOT allow exporting private key components
            // This is a security feature. We try anyway for demo purposes.
            // IMPORTANT: APDU buffer is limited (~256 bytes), SK export may cause buffer overflow
            // Estimated SK size: ~260 bytes (2 + 128 + 2 + 128)
            // If PK + SK > 256, we skip SK to avoid buffer overflow
            
            short estimatedSkSize = (short)260; // Conservative estimate
            short availableSpace = (short)(256 - respOffset); // Assume 256 byte buffer
            
            if (availableSpace > estimatedSkSize) {
                // Enough space, try to export SK
                try {
                    // Get modulus
                    short skModLen = skUser.getModulus(buf, (short)(respOffset + 2));
                    Util.setShort(buf, respOffset, skModLen);
                    respOffset += (short)(2 + skModLen);
                    
                    // Get private exponent
                    short skExpLen = skUser.getExponent(buf, (short)(respOffset + 2));
                    Util.setShort(buf, respOffset, skExpLen);
                    respOffset += (short)(2 + skExpLen);
                    
                    // If we reach here, SK export succeeded (rare)
                } catch (CryptoException e) {
                    // SK export failed (expected on most cards) - SKIP IT
                    // Response will only contain status + PK_user
                    // UI will detect missing SK and handle accordingly
                }
            }
            // else: Not enough buffer space for SK, skip it (UI will detect missing SK)
            
            // Send response
            try {
                apdu.setOutgoingAndSend((short)0, respOffset);
            } catch (Exception e) {
                // Response send failed - likely buffer overflow
                ISOException.throwIt((short)0x6F31); // Response send failed
            }
            
        } catch (CryptoException e) {
            // Crypto-related errors already throw specific codes (0x6F01-0x6F04, 0x6F1X)
            // If we reach here, it's an uncaught CryptoException
            ISOException.throwIt((short)(0x6F20 | (e.getReason() & 0x0F)));
        } catch (ArrayIndexOutOfBoundsException e) {
            // Buffer overflow
            ISOException.throwIt((short)0x6F30);
        } catch (Exception e) {
            // Other unexpected errors
            ISOException.throwIt((short)0x6F00);
        }
    }

    /**
     * VERIFY_PIN_AND_READ_DATA (0x03)
     * Data: [PIN_user (6 bytes)]
     * Response: [status (1 byte)] [patient_data_length (2 bytes)] [patient_data] hoc li
     */
    private void verifyPinAndReadData(APDU apdu) {
        // Kim tra th ã c phát hành cha
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kim tra th b khóa cha
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
            
            // 2. So sánh vi hash_PIN_user
            if (Util.arrayCompare(tempHash, (short)0, hashPinUser, (short)0, HASH_LENGTH) != 0) {
                // PIN sai
                pinRetryCounter--;
                if (pinRetryCounter == 0) {
                    blockedFlag = 1;
                }
                // Return SW with retry counter in SW2
                ISOException.throwIt((short)(0x63C0 | pinRetryCounter));
            }
            
            // 3. PIN úng - Reset retry counter
            pinRetryCounter = MAX_PIN_TRIES;
            
            // 4. Tính K_user = KDF(PIN_user)
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.update(buf, pinUserOffset, pinUserLength);
            sha256.doFinal(new byte[]{(byte)'U', (byte)'S', (byte)'E', (byte)'R'}, (short)0, (short)4, tempKey, (short)0);
            
            // 5. Gii MK_user t Enc_user: MK_user = AES_Decrypt(K_user, Enc_user)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encUser, (short)0, MK_USER_LENGTH, mkUser, (short)0);
            
            // 6. Gii mã d liu bnh nhân: patient_data = AES_Decrypt(MK_user, Enc_patient)
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
     * Yêu cu: mkUser phi ã c m (qua VERIFY_PIN_AND_READ_DATA)
     */
    private void updatePatientData(APDU apdu) {
        // Kim tra th ã c phát hành cha
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kim tra mkUser ã c m cha (check if first byte is not 0)
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
            
            // 2. Mã hóa d liu mi: Enc_patient_new = AES(MK_user, new_patient_data)
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            
            // Pad to multiple of 16 bytes
            short paddedLength = (short)((patientDataLength + 15) / 16 * 16);
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buf, offset, paddedData, (short)0, patientDataLength);
            
            // 3. Ghi è Enc_patient trong EEPROM
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
        // Kim tra th ã c phát hành cha
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
            // 1. Xác thc admin PIN - Tính Hash(PIN_admin_reset_input)
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, tempHash, (short)0);
            
            // 2. So sánh vi hash_PIN_admin_reset
            if (Util.arrayCompare(tempHash, (short)0, hashPinAdminReset, (short)0, HASH_LENGTH) != 0) {
                // Admin PIN sai
                ISOException.throwIt((short)0x6300); // Admin PIN incorrect
            }
            
            // 3. Tính K_admin = KDF(PIN_admin_reset_input)
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.update(buf, pinAdminResetOffset, pinAdminResetLength);
            sha256.doFinal(new byte[]{(byte)'A', (byte)'D', (byte)'M', (byte)'I', (byte)'N'}, (short)0, (short)5, tempKey, (short)0);
            
            // 4. Gii MK_user t Enc_admin: MK_user = AES_Decrypt(K_admin, Enc_admin)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encAdmin, (short)0, MK_USER_LENGTH, mkUser, (short)0);
            
            // 5. T new_PIN_user: Tính K_user_new = KDF(new_PIN_user)
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
    
    /**
     * SIGN_CHALLENGE (0x06)
     * Data: [challenge (N bytes, typically 32)]
     * Response: [signature (128 bytes for RSA 1024)]
     * 
     * Yêu cu: mkUser phi ã c m (user ã verify PIN)
     */
    private void signChallenge(APDU apdu) {
        // Kim tra th ã c phát hành cha
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kim tra mkUser ã c m cha (user ã verify PIN)
        if (mkUser[0] == 0 && mkUser[1] == 0 && mkUser[2] == 0 && mkUser[3] == 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short challengeLen = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
        short challengeOffset = ISO7816.OFFSET_CDATA;
        
        // Validate challenge length (RSA 1024 can sign max ~117 bytes with PKCS1 padding)
        if (challengeLen <= 0 || challengeLen > 117) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        try {
            // Ký challenge bng SK_user
            // Signature s c ghi vào u buffer
            short sigLen = RSAHelper.sign(rsaCipher, skUser, 
                                          buf, challengeOffset, challengeLen,
                                          buf, (short)0);
            
            apdu.setOutgoingAndSend((short)0, sigLen);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }
}
