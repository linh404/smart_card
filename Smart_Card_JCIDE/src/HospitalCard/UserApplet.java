package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * UserApplet V3 - Applet cho th User (th bnh nh�n)
 * Lung mi vi K_master v� PIN_admin_reset derive ng
 * 
 * Package: HospitalCard
 * Package AID: 11 22 33 44 55
 * Applet AID:  11 22 33 44 55 01
 * 
 * C�c APDU commands V3:
 * Kh�ng c ph�p s dng 0x06 cho bt k� INS n�o
 * - GET_STATUS (0x01): Ly trng th�i th
 * - ISSUE_CARD (0x02): Ph�t h�nh th
 * - VERIFY_PIN_AND_READ_DATA (0x03): X�c thc PIN v� c d liu
 * - UPDATE_PATIENT_DATA (0x04): Cp nht d liu bnh nh�n
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
    private static final byte INS_CHANGE_PIN = (byte)0x0A; // User tự đổi PIN (cần PIN cũ) - Đổi từ 0x06 để tránh conflict
    private static final byte INS_DEBIT = (byte)0x07; // Thanh toán
    private static final byte INS_GET_TXN_STATUS = (byte)0x08; // Lấy trạng thái giao dịch (txn_counter, last_txn_hash)
    private static final byte INS_CREDIT = (byte)0x09; // Nạp tiền (0x09 để tránh nhầm lẫn với SW 0x6XXX)
    private static final byte INS_SIGN_CHALLENGE = (byte)0x10; // V3: Sign challenge with SK_user
    
    // Constants
    private static final byte MAX_PIN_TRIES = 3;
    private static final short CARD_ID_LENGTH = 16;
    private static final short HASH_LENGTH = 20; // SHA-1 (JavaCard 2.2.x compatible)
    private static final short MK_USER_LENGTH = 16; // AES-128
    private static final short MK_USER_ENC_LENGTH = 32; // AES-128 + padding
    private static final short MAX_PATIENT_DATA_LENGTH = 256;
    private static final short BALANCE_LENGTH = 4; // int (4 bytes) - balance in VNĐ
    private static final short BALANCE_ENC_LENGTH = 16; // AES-128 block (padded to 16 bytes)
    private static final short KDF_ITERATIONS = 1000; // Số lần lặp cho PBKDF2 mô phỏng (KDF)
    
    // Trng th�i th
    private byte initialized; // 0 = cha ph�t h�nh, 1 = � ph�t h�nh
    private byte pinRetryCounter; // S ln th PIN c�n li (0-3)
    private byte blockedFlag; // 0 = kh�ng kh�a, 1 = b kh�a
    
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
    
    // Transaction data (separate from patient data)
    private byte[] encBalance; // Balance encrypted with MK_user (16 bytes, padded)
    private short txnCounter; // Transaction counter (2 bytes, max 65535)
    private byte[] lastTxnHash; // Last transaction hash (SHA-1, 20 bytes)
    
    // Transient (RAM) - MK_user ch tn ti khi � x�c thc
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
        // Cp ph�t b nh EEPROM (persistent)
        cardID = new byte[CARD_ID_LENGTH];
        hashPinUser = new byte[HASH_LENGTH];
        hashPinAdminReset = new byte[HASH_LENGTH];
        encUser = new byte[MK_USER_ENC_LENGTH];
        encAdmin = new byte[MK_USER_ENC_LENGTH];
        encPatient = new byte[MAX_PATIENT_DATA_LENGTH];
        encBalance = new byte[BALANCE_ENC_LENGTH];
        lastTxnHash = new byte[HASH_LENGTH];
        
        // Cp ph�t b nh RAM (transient)
        mkUser = JCSystem.makeTransientByteArray((short)(MK_USER_LENGTH + 16), JCSystem.CLEAR_ON_DESELECT);
        
        // Khi to trng th�i
        initialized = 0;
        pinRetryCounter = MAX_PIN_TRIES;
        blockedFlag = 0;
        encPatientLength = 0;
        txnCounter = 0;
        // Initialize lastTxnHash to all zeros (first transaction will start from zeros)
        Util.arrayFillNonAtomic(lastTxnHash, (short)0, HASH_LENGTH, (byte)0);
        
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

        // C�c lnh cn nhn d liu
        if (ins == INS_ISSUE_CARD || ins == INS_VERIFY_PIN_AND_READ_DATA ||
            ins == INS_UPDATE_PATIENT_DATA || ins == INS_ADMIN_RESET_PIN ||
            ins == INS_CHANGE_PIN || ins == INS_CREDIT || ins == INS_DEBIT ||
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
                
            case INS_CHANGE_PIN:
                changePin(apdu);
                break;
                
            case INS_DEBIT:
                debitTransaction(apdu);
                break;
                
            case INS_GET_TXN_STATUS:
                getTxnStatus(apdu);
                break;
                
            case INS_CREDIT:
                creditTransaction(apdu);
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
     * Note: Nu cardID c cung cp (16 bytes u ti�n kh�ng phi to�n 0), th s d�ng cardID �.
     * Nu kh�ng, th s t sinh cardID ngu nhi�n.
     */
    private void issueCard(APDU apdu) {
        // Kim tra th � c ph�t h�nh cha
        if (initialized == 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - already issued
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        try {
            // 1. Parse cardID (16 bytes, optional)
            // Kim tra xem c� cardID c cung cp kh�ng (kh�ng phi to�n 0)
            boolean hasProvidedCardID = false;
            short cardIDOffset = offset;
            for (short i = 0; i < CARD_ID_LENGTH; i++) {
                if (buf[(short)(cardIDOffset + i)] != 0) {
                    hasProvidedCardID = true;
                    break;
                }
            }
            
            if (hasProvidedCardID) {
                // D�ng cardID t backend
                Util.arrayCopyNonAtomic(buf, cardIDOffset, cardID, (short)0, CARD_ID_LENGTH);
                offset += CARD_ID_LENGTH;
            } else {
                // Backend kh�ng cung cp cardID, th t sinh
                randomData.generateData(cardID, (short)0, CARD_ID_LENGTH);
                // Kh�ng tng offset v� kh�ng c� cardID trong data
            }
            
            // 2. Parse patient_info_length
            short patientInfoLength = Util.getShort(buf, offset);
            offset += 2;
            
            if (patientInfoLength <= 0 || patientInfoLength > MAX_PATIENT_DATA_LENGTH) {
                ISOException.throwIt((short)0x6F40); // DEBUG: Invalid patient info length
            }
            
            // 3. Lu patient_info v�o buffer tm (s m� h�a sau)
            short patientInfoOffset = offset;
            offset += patientInfoLength;
            
            // 4. Parse PIN_user (6 bytes)
            short pinUserOffset = offset;
            short pinUserLength = 6;
            offset += pinUserLength;
            
            // 5. Parse PIN_admin_reset (6 bytes)
            short pinAdminResetOffset = offset;
            short pinAdminResetLength = 6;
            offset += pinAdminResetLength;
            
            // 5.1. Parse initial_balance (4 bytes, int) - optional, default to 0 if not present
            int initialBalance = 0;
            short dataLen = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
            // Calculate expected minimum length
            short cardIdPart = (short)(hasProvidedCardID ? CARD_ID_LENGTH : 0);
            short expectedMinLen = (short)(cardIdPart + 2 + patientInfoLength + pinUserLength + pinAdminResetLength);
            if (dataLen >= expectedMinLen + 4) {
                // Có balance trong data
                initialBalance = DataHelper.getInt(buf, offset);
            } else {
                // Không có balance, mặc định là 0
                initialBalance = 0;
            }
            
            // 6. Sinh MK_user ngu nhi�n (16 bytes)
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
            
            // 7. T�nh hash_PIN_user = SHA-1(PIN_user)
            try {
                sha256.reset();
                sha256.doFinal(buf, pinUserOffset, pinUserLength, hashPinUser, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F03); // Hash operation failed
            }
            
            // 8. T�nh hash_PIN_admin_reset = SHA-1(PIN_admin_reset)
            try {
                sha256.reset();
                sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, hashPinAdminReset, (short)0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short)0x6F03); // Hash operation failed
            }
            
            // 9. T�nh K_user = KDF(PIN_user) - simplified: SHA-1(PIN_user || "USER")
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            try {
                CryptoHelper.KDF(buf, pinUserOffset, pinUserLength,
                                cardID, (short)0, CARD_ID_LENGTH,
                                KDF_ITERATIONS, tempKey, sha256);
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
            
            // 11. T�nh K_admin = KDF(PIN_admin_reset) - simplified: SHA-1(PIN_admin_reset || "ADMIN")
            try {
                CryptoHelper.KDF(buf, pinAdminResetOffset, pinAdminResetLength,
                                cardID, (short)0, CARD_ID_LENGTH,
                                KDF_ITERATIONS, tempKey, sha256);
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
            
            // 13. M� h�a d liu bnh nh�n: Enc_patient = AES(MK_user, patient_data)
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
            
            // 13.1. Initialize balance: Enc_balance = AES(MK_user, initial_balance)
            try {
                byte[] balanceBytes = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);
                // Balance (4 bytes), pad to 16 bytes with zeros
                Util.arrayFillNonAtomic(balanceBytes, (short)0, BALANCE_ENC_LENGTH, (byte)0);
                DataHelper.setInt(balanceBytes, (short)0, initialBalance);
                aesKey.setKey(mkUser, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(balanceBytes, (short)0, BALANCE_ENC_LENGTH, encBalance, (short)0);
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
        // Kim tra th � c ph�t h�nh cha
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kim tra th b kh�a cha
        if (blockedFlag == 1) {
            ISOException.throwIt((short)0x6983); // Card blocked
        }
        
        byte[] buf = apdu.getBuffer();
        short pinUserOffset = ISO7816.OFFSET_CDATA;
        short pinUserLength = 6;
        
        try {
            // 1. T�nh Hash(PIN_user_input)
            // [LOG] Step 1: Calculate Hash(PIN_user_input)
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            try {
                sha256.reset();
                sha256.doFinal(buf, pinUserOffset, pinUserLength, tempHash, (short)0);
            } catch (CryptoException e) {
                // [LOG] Step 1 failed: Hash calculation error
                ISOException.throwIt((short)0x6F01); // Error at step 1
            }
            
            // 2. So s�nh vi hash_PIN_user
            if (Util.arrayCompare(tempHash, (short)0, hashPinUser, (short)0, HASH_LENGTH) != 0) {
                // PIN sai
                pinRetryCounter--;
                if (pinRetryCounter == 0) {
                    blockedFlag = 1;
                }
                // Return SW with retry counter in SW2
                ISOException.throwIt((short)(0x63C0 | pinRetryCounter));
            }
            
            // 3. PIN �ng - Reset retry counter
            pinRetryCounter = MAX_PIN_TRIES;
            
            // 4. T�nh K_user = KDF(PIN_user)
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            // [LOG] Step 3: Calculate K_user = KDF(PIN_user)
            try {
                CryptoHelper.KDF(buf, pinUserOffset, pinUserLength,
                                cardID, (short)0, CARD_ID_LENGTH,
                                KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException e) {
                // [LOG] Step 3 failed: KDF calculation error
                ISOException.throwIt((short)0x6F03); // Error at step 3
            }
            
            // [LOG] Step 4: Decrypt MK_user from Enc_user
            // 5. Gii MK_user t Enc_user: MK_user = AES_Decrypt(K_user, Enc_user)
            // Clear mkUser trc  m bo clean state (c� th c�n data t ln verify trc)
			Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            try {
                aesKey.setKey(tempKey, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                aesCipher.doFinal(encUser, (short)0, MK_USER_LENGTH, mkUser, (short)0);
            } catch (CryptoException e) {
                // [LOG] Step 4 failed: AES decrypt MK_user error
                // Clear mkUser on error
                Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
                short reason = e.getReason();
                if (reason == 0) {
                    ISOException.throwIt((short)0x6F04); // Error at step 4
                } else {
                    ISOException.throwIt((short)(0x6F04 | (reason & 0x0F))); // Error at step 4 with reason
                }
            }
            
            // [LOG] Step 5: Decrypt patient data
            // 6. Gii m� d liu bnh nh�n: patient_data = AES_Decrypt(MK_user, Enc_patient)
            byte[] decryptedData = JCSystem.makeTransientByteArray(MAX_PATIENT_DATA_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            short decryptedLength = 0;
            try {
                aesKey.setKey(mkUser, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                
                // Decrypt to temporary buffer
                decryptedLength = aesCipher.doFinal(encPatient, (short)0, encPatientLength, decryptedData, (short)0);
                
                // Find actual data length (remove padding zeros from end)
                while (decryptedLength > 0 && decryptedData[(short)(decryptedLength - 1)] == 0) {
                    decryptedLength--;
                }
            } catch (CryptoException e) {
                // [LOG] Step 5 failed: AES decrypt patient data error
                ISOException.throwIt((short)0x6F05); // Error at step 5
            }
            
            // [LOG] Step 6: Decrypt balance
            // 6.1. Decrypt balance: balance = AES_Decrypt(MK_user, Enc_balance)
            byte[] balanceBytes = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            int balance = 0;
            try {
                // Re-init cipher với mkUser key để đảm bảo clean state
                aesKey.setKey(mkUser, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                aesCipher.doFinal(encBalance, (short)0, BALANCE_ENC_LENGTH, balanceBytes, (short)0);
                balance = DataHelper.getInt(balanceBytes, (short)0);
            } catch (CryptoException e) {
                // [LOG] Step 6 failed: AES decrypt balance error
                ISOException.throwIt((short)0x6F06); // Error at step 6
            }
            
            // 7. Return: [status (0x00)] [patient_data_length (2 bytes)] [patient_data] [balance (4 bytes)]
            short offset = 0;
            buf[offset++] = (byte)0x00; // Success
            Util.setShort(buf, offset, decryptedLength);
            offset += 2;
            Util.arrayCopyNonAtomic(decryptedData, (short)0, buf, offset, decryptedLength);
            offset += decryptedLength;
            // Add balance (4 bytes) to response
            DataHelper.setInt(buf, offset, balance);
            offset += 4;
            
            // 8. Clear sensitive data
            Util.arrayFillNonAtomic(tempKey, (short)0, (short)20, (byte)0);
            Util.arrayFillNonAtomic(tempHash, (short)0, HASH_LENGTH, (byte)0);
            Util.arrayFillNonAtomic(balanceBytes, (short)0, BALANCE_ENC_LENGTH, (byte)0);
            
            apdu.setOutgoingAndSend((short)0, offset);
            
        } catch (CryptoException e) {
            // [LOG] verifyPinAndReadData: CryptoException caught, reason: e.getReason()
            // Use specific error codes to identify which step failed
            short reason = e.getReason();
            if (reason == 0) {
                // [LOG] General crypto error at unknown step
                ISOException.throwIt((short)0x6F00);
            } else {
                // [LOG] Crypto error with specific reason
                ISOException.throwIt((short)(0x6F00 | (reason & 0x0F)));
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // [LOG] verifyPinAndReadData: Buffer overflow error
            ISOException.throwIt((short)0x6F30); // Buffer overflow
        } catch (Exception e) {
            // [LOG] verifyPinAndReadData: Unexpected error
            ISOException.throwIt((short)0x6F00); // General error
        }
    }

    /**
     * UPDATE_PATIENT_DATA (0x04)
     * Data: [patient_data_length (2 bytes)] [patient_data]
     * Response: [status (1 byte)] (0x00 = success)
     * 
     * Y�u cu: mkUser phi � c m (qua VERIFY_PIN_AND_READ_DATA)
     */
    private void updatePatientData(APDU apdu) {
        // Kim tra th � c ph�t h�nh cha
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kim tra mkUser � c m cha (check if first byte is not 0)
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
            
            // 2. M� h�a d liu mi: Enc_patient_new = AES(MK_user, new_patient_data)
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            
            // Pad to multiple of 16 bytes
            short paddedLength = (short)((patientDataLength + 15) / 16 * 16);
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buf, offset, paddedData, (short)0, patientDataLength);
            
            // 3. Ghi � Enc_patient trong EEPROM
            encPatientLength = aesCipher.doFinal(paddedData, (short)0, paddedLength, encPatient, (short)0);
            
            // 4. Return success
            buf[0] = (byte)0x00;
            apdu.setOutgoingAndSend((short)0, (short)1);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }

    /**
     * CHANGE_PIN (0x0A)
     * Data: [PIN_user_old (6 bytes)] [PIN_user_new (6 bytes)]
     * Response: [status (1 byte)] (0x00 = success)
     * 
     * User tự đổi PIN khi biết PIN cũ
     * Theo README IV.5: Đổi PIN_user (User tự thực hiện)
     */
    private void changePin(APDU apdu) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kiểm tra thẻ bị khóa chưa
        if (blockedFlag == 1) {
            ISOException.throwIt((short)0x6983); // Card blocked
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        // Parse PIN_user_old (6 bytes)
        short pinOldOffset = offset;
        short pinOldLength = 6;
        offset += pinOldLength;
        
        // Parse PIN_user_new (6 bytes)
        short pinNewOffset = offset;
        short pinNewLength = 6;
        
        try {
            // 1. Xác thực PIN_user_old - Tính Hash(PIN_user_old)
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            try {
                sha256.reset();
                sha256.doFinal(buf, pinOldOffset, pinOldLength, tempHash, (short)0);
            } catch (CryptoException e) {
                ISOException.throwIt((short)0x6F01); // Hash operation failed
            }
            
            // 2. So sánh với hash_PIN_user
            if (Util.arrayCompare(tempHash, (short)0, hashPinUser, (short)0, HASH_LENGTH) != 0) {
                // PIN cũ sai
                pinRetryCounter--;
                if (pinRetryCounter == 0) {
                    blockedFlag = 1;
                }
                // Return SW with retry counter in SW2
                ISOException.throwIt((short)(0x63C0 | pinRetryCounter));
            }
            
            // 3. Kiểm tra PIN_moi ≠ PIN_cu (so sánh hash)
            byte[] tempHashNew = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            try {
                sha256.reset();
                sha256.doFinal(buf, pinNewOffset, pinNewLength, tempHashNew, (short)0);
            } catch (CryptoException e) {
                ISOException.throwIt((short)0x6F01); // Hash operation failed
            }
            
            if (Util.arrayCompare(tempHashNew, (short)0, hashPinUser, (short)0, HASH_LENGTH) == 0) {
                // PIN mới trùng PIN cũ
                ISOException.throwIt((short)0x6A80); // Wrong data - PIN mới phải khác PIN cũ
            }
            
            // 4. Mở MK_user bằng PIN_cu: K_user_old = KDF(PIN_user_old) - dùng PBKDF2 mô phỏng
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            try {
                CryptoHelper.KDF(buf, pinOldOffset, pinOldLength,
                                cardID, (short)0, CARD_ID_LENGTH,
                                KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException e) {
                ISOException.throwIt((short)(0x6F03 | (e.getReason() & 0x0F))); // KDF failed
            }
            
            // 5. Giải MK_user từ Enc_user: MK_user = AES_Decrypt(K_user_old, Enc_user)
            // Clear mkUser trước để đảm bảo clean state
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            try {
                aesKey.setKey(tempKey, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                aesCipher.doFinal(encUser, (short)0, MK_USER_LENGTH, mkUser, (short)0);
                
                // Validate mkUser sau khi decrypt - không được toàn số 0
                boolean mkUserValid = false;
                for (short i = 0; i < MK_USER_LENGTH; i++) {
                    if (mkUser[i] != 0) {
                        mkUserValid = true;
                        break;
                    }
                }
                if (!mkUserValid) {
                    // mkUser toàn số 0 - có thể encUser đã bị hỏng
                    Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
                    ISOException.throwIt((short)0x6F04); // Error: Invalid mkUser after decrypt
                }
            } catch (CryptoException e) {
                // Decrypt thất bại - clear mkUser và throw exception
                Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
                ISOException.throwIt((short)(0x6F04 | (e.getReason() & 0x0F))); // Error decrypting encUser
            }
            
            // 6. Tạo hash và khóa mới cho PIN_moi: K_user_new = KDF(PIN_user_new) - dùng PBKDF2 mô phỏng
            try {
                CryptoHelper.KDF(buf, pinNewOffset, pinNewLength,
                                cardID, (short)0, CARD_ID_LENGTH,
                                KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException e) {
                // KDF thất bại - clear mkUser và throw exception
                Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
                ISOException.throwIt((short)(0x6F03 | (e.getReason() & 0x0F))); // Error in KDF
            }
            
            // 7. Enc_user_new = AES(K_user_new, MK_user)
            // QUAN TRỌNG: Update encUser TRƯỚC khi update hashPinUser (giống adminResetPin)
            // NOTE: Không clear encUser trước khi encrypt (giống adminResetPin) để tránh lỗi decrypt sau này
            try {
                aesKey.setKey(tempKey, (short)0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(mkUser, (short)0, MK_USER_LENGTH, encUser, (short)0);
            } catch (CryptoException e) {
                // Encrypt thất bại - clear mkUser và throw exception
                // QUAN TRỌNG: Không update hashPinUser nếu encrypt thất bại
                Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
                ISOException.throwIt((short)(0x6F04 | (e.getReason() & 0x0F))); // Error encrypting encUser
            }
            
            // 8. hash_PIN_user_new = Hash(PIN_user_new)
            // Update hashPinUser SAU KHI đã update encUser thành công
            try {
                sha256.reset();
                sha256.doFinal(buf, pinNewOffset, pinNewLength, hashPinUser, (short)0);
            } catch (CryptoException e) {
                // Hash thất bại - đã update encUser rồi nên không rollback
                // Clear mkUser và throw exception
                Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
                ISOException.throwIt((short)0x6F01); // Hash operation failed
            }
            
            // 9. Reset pin_retry_counter, giữ blocked_flag = 0
            pinRetryCounter = MAX_PIN_TRIES;
            
            // 10. Clear sensitive data
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            Util.arrayFillNonAtomic(tempKey, (short)0, (short)20, (byte)0);
            Util.arrayFillNonAtomic(tempHash, (short)0, HASH_LENGTH, (byte)0);
            Util.arrayFillNonAtomic(tempHashNew, (short)0, HASH_LENGTH, (byte)0);
            
            // 11. Return success
            buf[0] = (byte)0x00;
            apdu.setOutgoingAndSend((short)0, (short)1);
            
        } catch (CryptoException e) {
            // Clear sensitive data on error
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            ISOException.throwIt((short)(0x6F00 | (e.getReason() & 0x0F)));
        } catch (ArrayIndexOutOfBoundsException e) {
            // Buffer overflow
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            ISOException.throwIt((short)0x6F30); // Buffer overflow
        } catch (Exception e) {
            // Other unexpected errors
            Util.arrayFillNonAtomic(mkUser, (short)0, (short)(MK_USER_LENGTH + 16), (byte)0);
            ISOException.throwIt((short)0x6F00); // General error
        }
    }
    
    /**
     * ADMIN_RESET_PIN (0x05)
     * Data: [PIN_admin_reset (6 bytes)] [new_PIN_user (6 bytes)]
     * Response: [status (1 byte)] (0x00 = success)
     */
    private void adminResetPin(APDU apdu) {
        // Kim tra th � c ph�t h�nh cha
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
            // 1. X�c thc admin PIN - T�nh Hash(PIN_admin_reset_input)
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, tempHash, (short)0);
            
            // 2. So s�nh vi hash_PIN_admin_reset
            if (Util.arrayCompare(tempHash, (short)0, hashPinAdminReset, (short)0, HASH_LENGTH) != 0) {
                // Admin PIN sai
                ISOException.throwIt((short)0x6300); // Admin PIN incorrect
            }
            
            // 3. T�nh K_admin = KDF(PIN_admin_reset_input)
            byte[] tempKey = JCSystem.makeTransientByteArray((short)20, JCSystem.CLEAR_ON_DESELECT);
            CryptoHelper.KDF(buf, pinAdminResetOffset, pinAdminResetLength,
                            cardID, (short)0, CARD_ID_LENGTH,
                            KDF_ITERATIONS, tempKey, sha256);
            
            // 4. Gii MK_user t Enc_admin: MK_user = AES_Decrypt(K_admin, Enc_admin)
            aesKey.setKey(tempKey, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encAdmin, (short)0, MK_USER_LENGTH, mkUser, (short)0);
            
            // 5. T new_PIN_user: T�nh K_user_new = KDF(new_PIN_user)
            CryptoHelper.KDF(buf, newPinUserOffset, newPinUserLength,
                            cardID, (short)0, CARD_ID_LENGTH,
                            KDF_ITERATIONS, tempKey, sha256);
            
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
     * SIGN_CHALLENGE (0x10)
     * Data: [challenge (N bytes, typically 32)]
     * Response: [signature (128 bytes for RSA 1024)]
     * 
     * Y�u cu: mkUser phi � c m (user � verify PIN)
     */
    private void signChallenge(APDU apdu) {
        // Kim tra th � c ph�t h�nh cha
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kim tra mkUser � c m cha (user � verify PIN)
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
            // K� challenge bng SK_user
            // Signature s c ghi v�o u buffer
            short sigLen = RSAHelper.sign(rsaCipher, skUser, 
                                          buf, challengeOffset, challengeLen,
                                          buf, (short)0);
            
            apdu.setOutgoingAndSend((short)0, sigLen);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }
    
    /**
     * Common helper method to process transaction (CREDIT or DEBIT)
     * @param apdu APDU object
     * @param isCredit true for CREDIT, false for DEBIT
     */
    private void processTransaction(APDU apdu, boolean isCredit) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        // Kiểm tra thẻ bị khóa chưa
        if (blockedFlag == 1) {
            ISOException.throwIt((short)0x6983); // Card blocked
        }
        
        // Kiểm tra mkUser đã được mở chưa (user đã verify PIN)
        if (mkUser[0] == 0 && mkUser[1] == 0 && mkUser[2] == 0 && mkUser[3] == 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;
        
        try {
            // 1. Parse amount (4 bytes, int)
            int amount = DataHelper.getInt(buf, offset);
            offset += 4;
            
            if (amount <= 0) {
                ISOException.throwIt(ISO7816.SW_WRONG_DATA); // Invalid amount
            }
            
            // 2. Decrypt balance: balance = AES_Decrypt(MK_user, Enc_balance)
            byte[] balanceBytes = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encBalance, (short)0, BALANCE_ENC_LENGTH, balanceBytes, (short)0);
            
            // Balance is stored as 4 bytes (int) at the beginning
            int currentBalance = DataHelper.getInt(balanceBytes, (short)0);
            
            // Ensure currentBalance is non-negative (should not happen, but safety check)
            if (currentBalance < 0) {
                currentBalance = 0; // Reset to 0 if somehow negative
            }
            
            int newBalance;
            
            // 3. Calculate new balance
            if (isCredit) {
                // CREDIT: add amount
                // Check for overflow before calculating
                // Integer.MAX_VALUE = 2147483647 (0x7FFFFFFF)
                // Check: currentBalance + amount > 2147483647
                if (amount > 0 && currentBalance > 2147483647 - amount) {
                    ISOException.throwIt((short)0x6A80); // Balance overflow
                }
                newBalance = currentBalance + amount;
            } else {
                // DEBIT: subtract amount
                if (amount < 0) {
                    ISOException.throwIt(ISO7816.SW_WRONG_DATA); // Invalid negative amount
                }
                if (amount > currentBalance) {
                    ISOException.throwIt((short)0x6A80); // Insufficient funds
                }
                newBalance = currentBalance - amount;
                // Ensure newBalance is non-negative
                if (newBalance < 0) {
                    newBalance = 0;
                }
            }
            
            // 4. Increment transaction counter
            if (txnCounter >= 65535) {
                ISOException.throwIt((short)0x6A80); // Transaction counter overflow
            }
            txnCounter = (short)(txnCounter + 1);
            
            // 5. Calculate hash chain: SHA-1(prev_hash || txn_counter || type || amount || balance_after)
            byte[] hashInput = JCSystem.makeTransientByteArray((short)(HASH_LENGTH + 2 + 1 + 4 + 4), JCSystem.CLEAR_ON_DESELECT);
            short hashInputOffset = 0;
            
            // prev_hash (20 bytes)
            Util.arrayCopyNonAtomic(lastTxnHash, (short)0, hashInput, hashInputOffset, HASH_LENGTH);
            hashInputOffset += HASH_LENGTH;
            
            // txn_counter (2 bytes)
            Util.setShort(hashInput, hashInputOffset, txnCounter);
            hashInputOffset += 2;
            
            // type (1 byte: 0x01 = CREDIT, 0x02 = DEBIT)
            hashInput[hashInputOffset++] = isCredit ? (byte)0x01 : (byte)0x02;
            
            // amount (4 bytes)
            DataHelper.setInt(hashInput, hashInputOffset, amount);
            hashInputOffset += 4;
            
            // balance_after (4 bytes)
            DataHelper.setInt(hashInput, hashInputOffset, newBalance);
            hashInputOffset += 4;
            
            // Calculate SHA-1 hash
            sha256.reset();
            short currHashLen = sha256.doFinal(hashInput, (short)0, hashInputOffset, lastTxnHash, (short)0);
            if (currHashLen != HASH_LENGTH) {
                ISOException.throwIt((short)0x6F03); // Hash calculation failed
            }
            
            // 6. Encrypt new balance: Enc_balance_new = AES(MK_user, balance_new)
            // Clear balanceBytes first, then set new balance
            Util.arrayFillNonAtomic(balanceBytes, (short)0, BALANCE_ENC_LENGTH, (byte)0);
            DataHelper.setInt(balanceBytes, (short)0, newBalance);
            // Remaining 12 bytes are already zero (padding for 16-byte block)
            aesKey.setKey(mkUser, (short)0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(balanceBytes, (short)0, BALANCE_ENC_LENGTH, encBalance, (short)0);
            
            // 7. Return: [status (1)] [seq (2)] [balance_after (4)] [curr_hash (20)]
            short respOffset = 0;
            buf[respOffset++] = (byte)0x00; // Status = success
            Util.setShort(buf, respOffset, txnCounter); // seq
            respOffset += 2;
            DataHelper.setInt(buf, respOffset, newBalance); // balance_after
            respOffset += 4;
            Util.arrayCopyNonAtomic(lastTxnHash, (short)0, buf, respOffset, HASH_LENGTH); // curr_hash
            respOffset += HASH_LENGTH;
            
            // Clear sensitive data
            Util.arrayFillNonAtomic(balanceBytes, (short)0, BALANCE_ENC_LENGTH, (byte)0);
            Util.arrayFillNonAtomic(hashInput, (short)0, (short)(HASH_LENGTH + 2 + 1 + 4 + 4), (byte)0);
            
            apdu.setOutgoingAndSend((short)0, respOffset);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short)(0x6F00 | e.getReason()));
        }
    }
    
    /**
     * CREDIT (0x09) - Nạp tiền
     * Data: [amount (4 bytes, int)]
     * Response: [status (1 byte)] [seq (2 bytes)] [balance_after (4 bytes)] [curr_hash (20 bytes)]
     * 
     * Yêu cầu: mkUser phải đã được mở (user đã verify PIN)
     * 
     * Note: INS = 0x09 (không dùng 0x06 để tránh nhầm lẫn với Status Word 0x6XXX)
     */
    private void creditTransaction(APDU apdu) {
        processTransaction(apdu, true);
    }
    
    /**
     * DEBIT (0x07) - Thanh toán
     * Data: [amount (4 bytes, int)]
     * Response: [status (1 byte)] [seq (2 bytes)] [balance_after (4 bytes)] [curr_hash (20 bytes)]
     * 
     * Yêu cầu: mkUser phải đã được mở (user đã verify PIN)
     */
    private void debitTransaction(APDU apdu) {
        processTransaction(apdu, false);
    }
    
    /**
     * GET_TXN_STATUS (0x08) - Lấy trạng thái giao dịch
     * Response: [txn_counter (2 bytes)] [last_txn_hash (20 bytes)]
     */
    private void getTxnStatus(APDU apdu) {
        // Kiểm tra thẻ đã được phát hành chưa
        if (initialized != 1) {
            ISOException.throwIt((short)0x6985); // Conditions not satisfied - not issued
        }
        
        byte[] buf = apdu.getBuffer();
        short offset = 0;
        
        // Return txn_counter and last_txn_hash
        Util.setShort(buf, offset, txnCounter);
        offset += 2;
        Util.arrayCopyNonAtomic(lastTxnHash, (short)0, buf, offset, HASH_LENGTH);
        offset += HASH_LENGTH;
        
        apdu.setOutgoingAndSend((short)0, offset);
    }
}
