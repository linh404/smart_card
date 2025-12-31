package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

public class UserApplet extends Applet {

    private static final byte CLA = (byte) 0x80;

    private static final byte INS_GET_STATUS = (byte) 0x01;
    private static final byte INS_ISSUE_CARD = (byte) 0x02;
    private static final byte INS_VERIFY_PIN_AND_READ_DATA = (byte) 0x03;
    private static final byte INS_UPDATE_PATIENT_DATA = (byte) 0x04;
    private static final byte INS_ADMIN_RESET_PIN = (byte) 0x05;
    private static final byte INS_CHANGE_PIN = (byte) 0x0A;
    private static final byte INS_DEBIT = (byte) 0x07;
    private static final byte INS_GET_TXN_STATUS = (byte) 0x08;
    private static final byte INS_CREDIT = (byte) 0x09;
    private static final byte INS_SIGN_CHALLENGE = (byte) 0x10;
    private static final byte INS_GET_PIN_CHANGE_STATUS = (byte) 0x11;

    private static final byte MAX_PIN_TRIES = 5;
    private static final short CARD_ID_LENGTH = 16;
    private static final short HASH_LENGTH = 20;
    private static final short MK_USER_LENGTH = 16;
    private static final short MK_USER_ENC_LENGTH = 32;
    private static final short MAX_PATIENT_DATA_LENGTH = 256;
    private static final short BALANCE_LENGTH = 4;
    private static final short BALANCE_ENC_LENGTH = 16;
    private static final short KDF_ITERATIONS = 1000;

    private byte initialized;
    private byte pinRetryCounter;
    private byte blockedFlag;
    private byte pinChangedFlag;

    private byte[] cardID;

    private byte[] hashPinUser;
    private byte[] hashPinAdminReset;

    private byte[] encUser;
    private byte[] encAdmin;

    private byte[] encPatient;
    private short encPatientLength;

    private byte[] encBalance;
    private short txnCounter;
    private byte[] lastTxnHash;

    private byte[] mkUser;

    private KeyPair rsaKeyPair;
    private RSAPrivateKey skUser;
    private RSAPublicKey pkUser;
    private Cipher rsaCipher;

    private MessageDigest sha256;
    private Cipher aesCipher;
    private AESKey aesKey;
    private RandomData randomData;

    // Reusable transient buffers to prevent memory exhaustion
    private byte[] tempHashBuffer;
    private byte[] tempPinBuffer;
    private byte[] tempDecryptBuffer;
    private byte[] tempBalanceBuffer;

    protected UserApplet(byte[] bArray, short bOffset, byte bLength) {
        cardID = new byte[CARD_ID_LENGTH];
        hashPinUser = new byte[HASH_LENGTH];
        hashPinAdminReset = new byte[HASH_LENGTH];
        encUser = new byte[MK_USER_ENC_LENGTH];
        encAdmin = new byte[MK_USER_ENC_LENGTH];
        encPatient = new byte[MAX_PATIENT_DATA_LENGTH];
        encBalance = new byte[BALANCE_ENC_LENGTH];
        lastTxnHash = new byte[HASH_LENGTH];

        mkUser = JCSystem.makeTransientByteArray((short) (MK_USER_LENGTH + 16), JCSystem.CLEAR_ON_DESELECT);

        // Allocate reusable transient buffers ONCE to prevent memory exhaustion
        tempHashBuffer = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
        tempPinBuffer = JCSystem.makeTransientByteArray((short) 6, JCSystem.CLEAR_ON_DESELECT);
        tempDecryptBuffer = JCSystem.makeTransientByteArray(MAX_PATIENT_DATA_LENGTH, JCSystem.CLEAR_ON_DESELECT);
        tempBalanceBuffer = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);

        initialized = 0;
        pinRetryCounter = MAX_PIN_TRIES;
        blockedFlag = 0;
        pinChangedFlag = 0;
        encPatientLength = 0;
        txnCounter = 0;
        Util.arrayFillNonAtomic(lastTxnHash, (short) 0, HASH_LENGTH, (byte) 0);

        try {
            sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD, false);
            aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            randomData = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);

            rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
            rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
        } catch (Exception e) {
        }

        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new UserApplet(bArray, bOffset, bLength);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buf[ISO7816.OFFSET_INS];

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

            case INS_GET_PIN_CHANGE_STATUS:
                getPinChangeStatus(apdu);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void getStatus(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short offset = 0;

        buf[offset++] = initialized;
        buf[offset++] = pinRetryCounter;
        buf[offset++] = blockedFlag;

        Util.arrayCopyNonAtomic(cardID, (short) 0, buf, offset, CARD_ID_LENGTH);
        offset += CARD_ID_LENGTH;

        apdu.setOutgoingAndSend((short) 0, offset);
    }

    private void issueCard(APDU apdu) {
        if (initialized == 1) {
            ISOException.throwIt((short) 0x6985);
        }

        byte[] buf = apdu.getBuffer();

        try {
            // Parse input data (cardID, patient info, PINs, balance)
            short[] inputData = JCSystem.makeTransientShortArray((short) 7, JCSystem.CLEAR_ON_DESELECT);
            parseIssueCardInput(buf, inputData);

            // Extract parsed values
            short patientInfoOffset = inputData[1];
            short patientInfoLength = inputData[2];
            short pinUserOffset = inputData[3];
            short pinAdminResetOffset = inputData[4];
            int initialBalance = (int) ((inputData[5] << 16) | (inputData[6] & 0xFFFF));

            // Generate master key and RSA keypair
            generateAndInitializeKeys();

            // Hash PINs and wrap master key
            setupPINsAndEncryptMK(buf, pinUserOffset, pinAdminResetOffset);

            // Encrypt patient data
            encryptAndStorePatientData(buf, patientInfoOffset, patientInfoLength);

            // Encrypt balance
            encryptAndStoreBalance(initialBalance);

            // Mark card as initialized
            initialized = 1;
            pinChangedFlag = 0;

            // Clear master key from memory
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);

            // Build and send response with public key
            short respLength = buildIssueCardResponse(buf);

            try {
                apdu.setOutgoingAndSend((short) 0, respLength);
            } catch (Exception e) {
                ISOException.throwIt((short) 0x6F31);
            }

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F20 | (e.getReason() & 0x0F)));
        } catch (ArrayIndexOutOfBoundsException e) {
            ISOException.throwIt((short) 0x6F30);
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6F00);
        }
    }

    private void verifyPinAndReadData(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (blockedFlag == 1) {
            ISOException.throwIt((short) 0x6983);
        }

        byte[] buf = apdu.getBuffer();
        short pinUserOffset = ISO7816.OFFSET_CDATA;
        short pinUserLength = 6;

        try {
            // Validate PIN
            try {
                validateAndVerifyPIN(buf, pinUserOffset, pinUserLength);
            } catch (ISOException e) {
                // Re-throw ISOException from helper (e.g., wrong PIN)
                throw e;
            } catch (Exception e) {
                ISOException.throwIt((short) 0x6FAA); // validateAndVerifyPIN threw non-ISO exception
            }

            // Decrypt data and build response
            short respLength = 0;
            try {
                respLength = decryptDataAndBuildResponse(buf, pinUserOffset, pinUserLength, apdu);
            } catch (ISOException e) {
                throw e;
            } catch (Exception e) {
                ISOException.throwIt((short) 0x6FAB); // decryptDataAndBuildResponse threw non-ISO exception
            }

            // Send response (mkUser is kept for subsequent operations like signChallenge)
            try {
                apdu.setOutgoingAndSend((short) 0, respLength);
            } catch (Exception e) {
                ISOException.throwIt((short) 0x6FA9); // setOutgoingAndSend failed
            }

        } catch (ISOException e) {
            // Re-throw ISO exceptions from helpers (PIN mismatch, crypto errors, etc.)
            ISOException.throwIt(e.getReason());
        } catch (CryptoException e) {
            short reason = e.getReason();
            if (reason == 0) {
                ISOException.throwIt((short) 0x6F00);
            } else {
                ISOException.throwIt((short) (0x6F00 | (reason & 0x0F)));
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            ISOException.throwIt((short) 0x6F30);
        } catch (NullPointerException e) {
            // Crypto objects not initialized - applet needs reinstall
            ISOException.throwIt((short) 0x6F50);
        } catch (Exception e) {
            // Debug: 0x6F98 means new refactored code is running
            ISOException.throwIt((short) 0x6F98);
        }
    }

    private void updatePatientData(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (!isMasterKeyValid()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;

        try {
            short patientDataLength = Util.getShort(buf, offset);
            offset += 2;

            if (patientDataLength <= 0 || patientDataLength > MAX_PATIENT_DATA_LENGTH) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }

            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);

            short paddedLength = (short) ((patientDataLength + 15) / 16 * 16);
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buf, offset, paddedData, (short) 0, patientDataLength);

            encPatientLength = aesCipher.doFinal(paddedData, (short) 0, paddedLength, encPatient, (short) 0);

            buf[0] = (byte) 0x00;
            apdu.setOutgoingAndSend((short) 0, (short) 1);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | e.getReason()));
        }
    }

    private void changePin(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (blockedFlag == 1) {
            ISOException.throwIt((short) 0x6983);
        }

        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;

        short pinOldOffset = offset;
        short pinOldLength = 6;
        offset += pinOldLength;

        short pinNewOffset = offset;
        short pinNewLength = 6;

        try {
            // Use reusable buffer for new PIN hash instead of allocating
            Util.arrayFillNonAtomic(tempHashBuffer, (short) 0, HASH_LENGTH, (byte) 0);

            // Validate old PIN and new PIN
            try {
                validatePINChange(buf, pinOldOffset, pinOldLength, pinNewOffset, pinNewLength, tempHashBuffer);
            } catch (ISOException e) {
                throw e;
            } catch (Exception e) {
                ISOException.throwIt((short) 0x6FB0); // validatePINChange threw non-ISO exception
            }

            // Re-encrypt master key with new PIN
            try {
                reEncryptMasterKeyWithNewPIN(buf, pinOldOffset, pinOldLength, pinNewOffset, pinNewLength,
                        tempHashBuffer);
            } catch (ISOException e) {
                throw e;
            } catch (Exception e) {
                ISOException.throwIt((short) 0x6FB1); // reEncryptMasterKeyWithNewPIN threw non-ISO exception
            }

            // Success
            buf[0] = (byte) 0x00;
            apdu.setOutgoingAndSend((short) 0, (short) 1);

        } catch (ISOException e) {
            // Re-throw ISO exceptions from helpers (PIN mismatch, etc.)
            ISOException.throwIt(e.getReason());
        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | e.getReason()));
        } catch (Exception e) {
            // Debug: 0x6F99 means unexpected exception
            ISOException.throwIt((short) 0x6F99);
        }
    }

    private void adminResetPin(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;

        short pinAdminResetOffset = offset;
        short pinAdminResetLength = 6;
        offset += pinAdminResetLength;

        short newPinUserOffset = offset;
        short newPinUserLength = 6;

        try {
            // Hash and verify PIN_admin_reset
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            PINHelper.safeHash(sha256, buf, pinAdminResetOffset, pinAdminResetLength, tempHash, (short) 0);

            if (Util.arrayCompare(tempHash, (short) 0, hashPinAdminReset, (short) 0, HASH_LENGTH) != 0) {
                ISOException.throwIt((short) 0x6300);
            }

            // Unwrap MK with PIN_admin_reset
            boolean success = CryptoHelper.unwrapMasterKeyWithPIN(
                    buf, pinAdminResetOffset, pinAdminResetLength,
                    cardID, (short) 0, CARD_ID_LENGTH,
                    KDF_ITERATIONS,
                    encAdmin, mkUser,
                    aesCipher, aesKey, sha256);

            if (!success) {
                ISOException.throwIt((short) 0x6F04);
            }

            // Re-wrap MK with new PIN_user
            CryptoHelper.wrapMasterKeyWithPIN(
                    buf, newPinUserOffset, newPinUserLength,
                    cardID, (short) 0, CARD_ID_LENGTH,
                    KDF_ITERATIONS,
                    mkUser, encUser,
                    aesCipher, aesKey, sha256);

            // Hash new PIN
            PINHelper.safeHash(sha256, buf, newPinUserOffset, newPinUserLength, hashPinUser, (short) 0);

            pinRetryCounter = MAX_PIN_TRIES;
            blockedFlag = 0;
            pinChangedFlag = 0;

            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);

            buf[0] = (byte) 0x00;
            apdu.setOutgoingAndSend((short) 0, (short) 1);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | e.getReason()));
        }
    }

    /**
     * Check if MasterKey is valid (not all zeros)
     * 
     * @return true if MK is valid
     */
    private boolean isMasterKeyValid() {
        for (short i = 0; i < MK_USER_LENGTH; i++) {
            if (mkUser[i] != 0) {
                return true;
            }
        }
        return false;
    }

    private void signChallenge(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (!isMasterKeyValid()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short challengeLen = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
        short challengeOffset = ISO7816.OFFSET_CDATA;

        if (challengeLen <= 0 || challengeLen > 117) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        try {
            // Hash challenge before signing (security best practice)
            // This prevents chosen-plaintext attacks on RSA
            // Use reusable buffer instead of allocating
            Util.arrayFillNonAtomic(tempHashBuffer, (short) 0, HASH_LENGTH, (byte) 0);

            sha256.reset();
            sha256.doFinal(buf, challengeOffset, challengeLen, tempHashBuffer, (short) 0);

            // Sign the HASH, not raw challenge
            short sigLen = RSAHelper.sign(rsaCipher, skUser,
                    tempHashBuffer, (short) 0, HASH_LENGTH,
                    buf, (short) 0);

            apdu.setOutgoingAndSend((short) 0, sigLen);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | e.getReason()));
        }
    }

    private void processTransaction(APDU apdu, boolean isCredit) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (blockedFlag == 1) {
            ISOException.throwIt((short) 0x6983);
        }

        if (!isMasterKeyValid()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short offset = ISO7816.OFFSET_CDATA;

        try {
            int amount = DataHelper.getInt(buf, offset);
            offset += 4;

            if (amount <= 0) {
                ISOException.throwIt(ISO7816.SW_WRONG_DATA);
            }

            byte[] balanceBytes = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encBalance, (short) 0, BALANCE_ENC_LENGTH, balanceBytes, (short) 0);

            int currentBalance = DataHelper.getInt(balanceBytes, (short) 0);

            if (currentBalance < 0) {
                currentBalance = 0;
            }

            int newBalance;

            if (isCredit) {
                if (amount > 0 && currentBalance > 2147483647 - amount) {
                    ISOException.throwIt((short) 0x6A80);
                }
                newBalance = currentBalance + amount;
            } else {
                if (amount < 0) {
                    ISOException.throwIt(ISO7816.SW_WRONG_DATA);
                }
                if (amount > currentBalance) {
                    ISOException.throwIt((short) 0x6A80);
                }
                newBalance = currentBalance - amount;
                if (newBalance < 0) {
                    newBalance = 0;
                }
            }

            if (txnCounter >= 65535) {
                ISOException.throwIt((short) 0x6A80);
            }
            txnCounter = (short) (txnCounter + 1);

            byte[] hashInput = JCSystem.makeTransientByteArray((short) (HASH_LENGTH + 2 + 1 + 4 + 4),
                    JCSystem.CLEAR_ON_DESELECT);
            short hashInputOffset = 0;

            Util.arrayCopyNonAtomic(lastTxnHash, (short) 0, hashInput, hashInputOffset, HASH_LENGTH);
            hashInputOffset += HASH_LENGTH;

            Util.setShort(hashInput, hashInputOffset, txnCounter);
            hashInputOffset += 2;

            hashInput[hashInputOffset++] = isCredit ? (byte) 0x01 : (byte) 0x02;

            DataHelper.setInt(hashInput, hashInputOffset, amount);
            hashInputOffset += 4;

            DataHelper.setInt(hashInput, hashInputOffset, newBalance);
            hashInputOffset += 4;

            sha256.reset();
            short currHashLen = sha256.doFinal(hashInput, (short) 0, hashInputOffset, lastTxnHash, (short) 0);
            if (currHashLen != HASH_LENGTH) {
                ISOException.throwIt((short) 0x6F03);
            }

            Util.arrayFillNonAtomic(balanceBytes, (short) 0, BALANCE_ENC_LENGTH, (byte) 0);
            DataHelper.setInt(balanceBytes, (short) 0, newBalance);
            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(balanceBytes, (short) 0, BALANCE_ENC_LENGTH, encBalance, (short) 0);

            short respOffset = 0;
            buf[respOffset++] = (byte) 0x00;
            Util.setShort(buf, respOffset, txnCounter);
            respOffset += 2;
            DataHelper.setInt(buf, respOffset, newBalance);
            respOffset += 4;
            Util.arrayCopyNonAtomic(lastTxnHash, (short) 0, buf, respOffset, HASH_LENGTH);
            respOffset += HASH_LENGTH;

            Util.arrayFillNonAtomic(balanceBytes, (short) 0, BALANCE_ENC_LENGTH, (byte) 0);
            Util.arrayFillNonAtomic(hashInput, (short) 0, (short) (HASH_LENGTH + 2 + 1 + 4 + 4), (byte) 0);

            apdu.setOutgoingAndSend((short) 0, respOffset);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | e.getReason()));
        }
    }

    private void creditTransaction(APDU apdu) {
        processTransaction(apdu, true);
    }

    private void debitTransaction(APDU apdu) {
        processTransaction(apdu, false);
    }

    private void getTxnStatus(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        byte[] buf = apdu.getBuffer();
        short offset = 0;

        Util.setShort(buf, offset, txnCounter);
        offset += 2;
        Util.arrayCopyNonAtomic(lastTxnHash, (short) 0, buf, offset, HASH_LENGTH);
        offset += HASH_LENGTH;

        apdu.setOutgoingAndSend((short) 0, offset);
    }

    private void getPinChangeStatus(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        buf[0] = pinChangedFlag;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // ========== Phase 3: issueCard() Helper Methods ==========

    /**
     * Parse and validate input data for card issuance
     * Returns array: [hasProvidedCardID, patientInfoOffset, patientInfoLength,
     * pinUserOffset, pinAdminResetOffset, initialBalance_high16,
     * initialBalance_low16]
     */
    private void parseIssueCardInput(byte[] buf, short[] output) {
        short offset = ISO7816.OFFSET_CDATA;

        // Check if cardID is provided (non-zero)
        boolean hasProvidedCardID = false;
        short cardIDOffset = offset;
        for (short i = 0; i < CARD_ID_LENGTH; i++) {
            if (buf[(short) (cardIDOffset + i)] != 0) {
                hasProvidedCardID = true;
                break;
            }
        }

        if (hasProvidedCardID) {
            Util.arrayCopyNonAtomic(buf, cardIDOffset, cardID, (short) 0, CARD_ID_LENGTH);
            offset += CARD_ID_LENGTH;
        } else {
            randomData.generateData(cardID, (short) 0, CARD_ID_LENGTH);
        }

        short patientInfoLength = Util.getShort(buf, offset);
        offset += 2;

        if (patientInfoLength <= 0 || patientInfoLength > MAX_PATIENT_DATA_LENGTH) {
            ISOException.throwIt((short) 0x6F40);
        }

        short patientInfoOffset = offset;
        offset += patientInfoLength;

        short pinUserOffset = offset;
        short pinUserLength = 6;
        offset += pinUserLength;

        short pinAdminResetOffset = offset;
        short pinAdminResetLength = 6;
        offset += pinAdminResetLength;

        // Parse initial balance (if provided)
        int initialBalance = 0;
        short dataLen = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
        short cardIdPart = (short) (hasProvidedCardID ? CARD_ID_LENGTH : 0);
        short expectedMinLen = (short) (cardIdPart + 2 + patientInfoLength + pinUserLength + pinAdminResetLength);
        if (dataLen >= expectedMinLen + 4) {
            initialBalance = DataHelper.getInt(buf, offset);
        }

        // Pack output
        output[0] = (short) (hasProvidedCardID ? 1 : 0);
        output[1] = patientInfoOffset;
        output[2] = patientInfoLength;
        output[3] = pinUserOffset;
        output[4] = pinAdminResetOffset;
        output[5] = (short) (initialBalance >>> 16); // high 16 bits
        output[6] = (short) (initialBalance & 0xFFFF); // low 16 bits
    }

    /**
     * Generate master key and RSA keypair
     */
    private void generateAndInitializeKeys() {
        randomData.generateData(mkUser, (short) 0, MK_USER_LENGTH);

        try {
            if (!RSAHelper.generateKeyPair(rsaKeyPair)) {
                ISOException.throwIt((short) 0x6F01);
            }
            skUser = (RSAPrivateKey) rsaKeyPair.getPrivate();
            pkUser = (RSAPublicKey) rsaKeyPair.getPublic();

            if (skUser == null || pkUser == null || !skUser.isInitialized() || !pkUser.isInitialized()) {
                ISOException.throwIt((short) 0x6F01);
            }
        } catch (CryptoException ce) {
            ISOException.throwIt((short) (0x6F10 | (ce.getReason() & 0x0F)));
        }
    }

    /**
     * Hash PINs and encrypt master key with both PINs
     */
    private void setupPINsAndEncryptMK(byte[] buf, short pinUserOffset, short pinAdminResetOffset) {
        // Hash PINs using safeHash helper
        PINHelper.safeHash(sha256, buf, pinUserOffset, (short) 6, hashPinUser, (short) 0);
        PINHelper.safeHash(sha256, buf, pinAdminResetOffset, (short) 6, hashPinAdminReset, (short) 0);

        // Wrap MK with PIN_user using helper
        CryptoHelper.wrapMasterKeyWithPIN(
                buf, pinUserOffset, (short) 6,
                cardID, (short) 0, CARD_ID_LENGTH,
                KDF_ITERATIONS,
                mkUser, encUser,
                aesCipher, aesKey, sha256);

        // Wrap MK with PIN_admin_reset using helper
        CryptoHelper.wrapMasterKeyWithPIN(
                buf, pinAdminResetOffset, (short) 6,
                cardID, (short) 0, CARD_ID_LENGTH,
                KDF_ITERATIONS,
                mkUser, encAdmin,
                aesCipher, aesKey, sha256);
    }

    /**
     * Encrypt patient data with master key
     */
    private void encryptAndStorePatientData(byte[] buf, short patientInfoOffset, short patientInfoLength) {
        try {
            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);

            short paddedLength = (short) ((patientInfoLength + 15) / 16 * 16);
            byte[] paddedData = JCSystem.makeTransientByteArray(paddedLength, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayCopyNonAtomic(buf, patientInfoOffset, paddedData, (short) 0, patientInfoLength);

            encPatientLength = aesCipher.doFinal(paddedData, (short) 0, paddedLength, encPatient, (short) 0);
        } catch (CryptoException ce) {
            ISOException.throwIt((short) 0x6F04);
        }
    }

    /**
     * Encrypt and store initial balance
     */
    private void encryptAndStoreBalance(int initialBalance) {
        try {
            byte[] balanceBytes = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            Util.arrayFillNonAtomic(balanceBytes, (short) 0, BALANCE_ENC_LENGTH, (byte) 0);
            DataHelper.setInt(balanceBytes, (short) 0, initialBalance);
            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(balanceBytes, (short) 0, BALANCE_ENC_LENGTH, encBalance, (short) 0);
        } catch (CryptoException ce) {
            ISOException.throwIt((short) 0x6F04);
        }
    }

    /**
     * Build response with public key (and optionally private key if space permits)
     */
    private short buildIssueCardResponse(byte[] buf) {
        short respOffset = 0;
        buf[respOffset++] = (byte) 0x00; // Status: success

        try {
            short pkLen = RSAHelper.getPublicKeyBytes(pkUser, buf, respOffset);

            if (pkLen <= 0 || pkLen > 200) {
                ISOException.throwIt((short) 0x6F02);
            }

            respOffset += pkLen;
        } catch (CryptoException ce) {
            ISOException.throwIt((short) 0x6F02);
        } catch (ArrayIndexOutOfBoundsException ae) {
            ISOException.throwIt((short) 0x6F30);
        }

        // Optionally include private key if space permits
        short estimatedSkSize = (short) 260;
        short availableSpace = (short) (256 - respOffset);

        if (availableSpace > estimatedSkSize) {
            try {
                short skModLen = skUser.getModulus(buf, (short) (respOffset + 2));
                Util.setShort(buf, respOffset, skModLen);
                respOffset += (short) (2 + skModLen);

                short skExpLen = skUser.getExponent(buf, (short) (respOffset + 2));
                Util.setShort(buf, respOffset, skExpLen);
                respOffset += (short) (2 + skExpLen);

            } catch (CryptoException e) {
                // Ignore if SK export fails (not critical)
            }
        }

        return respOffset;
    }

    // ========== Phase 3: verifyPinAndReadData() Helper Methods ==========

    /**
     * Validate PIN by comparing hash with stored PIN hash
     * Returns true if PIN is valid, throws exception if invalid
     */
    private void validateAndVerifyPIN(byte[] buf, short pinOffset, short pinLength) {
        // Use reusable buffer instead of allocating
        Util.arrayFillNonAtomic(tempHashBuffer, (short) 0, HASH_LENGTH, (byte) 0);

        try {
            // Hash the input PIN
            sha256.reset();
            sha256.doFinal(buf, pinOffset, pinLength, tempHashBuffer, (short) 0);
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F01);
        } catch (NullPointerException e) {
            ISOException.throwIt((short) 0x6FA3); // sha256 is null
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FA4);
        }

        // Compare PIN hash
        if (Util.arrayCompare(tempHashBuffer, (short) 0, hashPinUser, (short) 0, HASH_LENGTH) != 0) {
            pinRetryCounter--;
            if (pinRetryCounter == 0) {
                blockedFlag = 1;
            }
            ISOException.throwIt((short) (0x63C0 | pinRetryCounter));
        }

        // PIN valid - reset retry counter
        pinRetryCounter = MAX_PIN_TRIES;
    }

    private short decryptDataAndBuildResponse(byte[] buf, short pinOffset, short pinLength, APDU apdu) {
        // CRITICAL: Clear all reusable buffers first to prevent pollution from previous
        // calls
        Util.arrayFillNonAtomic(tempPinBuffer, (short) 0, (short) 6, (byte) 0);
        Util.arrayFillNonAtomic(tempDecryptBuffer, (short) 0, (short) 20, (byte) 0); // At least first 20 bytes used for
                                                                                     // hashing

        // Copy PIN to reusable buffer BEFORE any processing (buf might get overwritten)
        Util.arrayCopyNonAtomic(buf, pinOffset, tempPinBuffer, (short) 0, pinLength);

        // Unwrap MK with PIN_user using helper
        try {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FA5); // Failed to clear mkUser
        }

        boolean success = false;
        try {
            success = CryptoHelper.unwrapMasterKeyWithPIN(
                    tempPinBuffer, (short) 0, pinLength, // Use tempPinBuffer instead of buf!
                    cardID, (short) 0, CARD_ID_LENGTH,
                    KDF_ITERATIONS,
                    encUser, mkUser,
                    aesCipher, aesKey, sha256);
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FAC); // unwrapMasterKeyWithPIN threw exception
        }

        if (!success) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) 0x6F04);
        }

        // Decrypt patient data - use reusable buffer
        Util.arrayFillNonAtomic(tempDecryptBuffer, (short) 0, MAX_PATIENT_DATA_LENGTH, (byte) 0);

        short decryptedLength = 0;
        try {
            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);

            decryptedLength = aesCipher.doFinal(encPatient, (short) 0, encPatientLength, tempDecryptBuffer, (short) 0);

            // Remove padding
            while (decryptedLength > 0 && tempDecryptBuffer[(short) (decryptedLength - 1)] == 0) {
                decryptedLength--;
            }
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F05);
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FAD); // Non-crypto exception in patient data decrypt
        }

        // Decrypt balance - use reusable buffer
        Util.arrayFillNonAtomic(tempBalanceBuffer, (short) 0, BALANCE_ENC_LENGTH, (byte) 0);

        int balance = 0;
        try {
            aesKey.setKey(mkUser, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encBalance, (short) 0, BALANCE_ENC_LENGTH, tempBalanceBuffer, (short) 0);
            balance = DataHelper.getInt(tempBalanceBuffer, (short) 0);
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F06);
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FAE); // Non-crypto exception in balance decrypt
        }

        // Build response: status(1) + dataLen(2) + patientData + balance(4)
        short offset = 0;
        try {
            buf[offset++] = (byte) 0x00; // Status: success
            Util.setShort(buf, offset, decryptedLength);
            offset += 2;
            Util.arrayCopyNonAtomic(tempDecryptBuffer, (short) 0, buf, offset, decryptedLength);
            offset += decryptedLength;
            DataHelper.setInt(buf, offset, balance);
            offset += 4;
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FA8); // Failed to build response
        }

        return offset;
    }

    // ========== Phase 3: changePin() Helper Methods ==========

    /**
     * Validate old PIN and new PIN for PIN change operation
     * Stores new PIN hash in tempHashNew array
     */
    private void validatePINChange(byte[] buf, short pinOldOffset, short pinOldLength,
            short pinNewOffset, short pinNewLength, byte[] tempHashNew) {
        // Use tempPinBuffer as temporary storage for old PIN hash (reuse existing
        // buffer)
        // Note: tempPinBuffer is only 6 bytes but we need 20 bytes for hash
        // So we'll use tempBalanceBuffer (16 bytes) - still not enough!
        // We need to use tempDecryptBuffer which is large enough
        // Actually, let's use a portion of tempDecryptBuffer for old PIN hash

        // Hash and verify old PIN into tempDecryptBuffer[0..19]
        try {
            sha256.reset();
            sha256.doFinal(buf, pinOldOffset, pinOldLength, tempDecryptBuffer, (short) 0);
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F01);
        }

        if (Util.arrayCompare(tempDecryptBuffer, (short) 0, hashPinUser, (short) 0, HASH_LENGTH) != 0) {
            pinRetryCounter--;
            if (pinRetryCounter == 0) {
                blockedFlag = 1;
            }
            ISOException.throwIt((short) (0x63C0 | pinRetryCounter));
        }

        // Hash new PIN into tempHashNew parameter
        try {
            sha256.reset();
            sha256.doFinal(buf, pinNewOffset, pinNewLength, tempHashNew, (short) 0);
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F01);
        }

        // Check new PIN is different from old PIN
        if (Util.arrayCompare(tempHashNew, (short) 0, hashPinUser, (short) 0, HASH_LENGTH) == 0) {
            ISOException.throwIt((short) 0x6A80); // New PIN same as old
        }
    }

    /**
     * Re-encrypt master key with new PIN and update stored values
     */
    private void reEncryptMasterKeyWithNewPIN(byte[] buf, short pinOldOffset, short pinOldLength,
            short pinNewOffset, short pinNewLength, byte[] newPinHash) {
        // Unwrap MK with old PIN
        Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
        boolean unwrapSuccess = CryptoHelper.unwrapMasterKeyWithPIN(
                buf, pinOldOffset, pinOldLength,
                cardID, (short) 0, CARD_ID_LENGTH,
                KDF_ITERATIONS,
                encUser, mkUser,
                aesCipher, aesKey, sha256);

        if (!unwrapSuccess) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) 0x6F04);
        }

        // Validate MK
        if (!isMasterKeyValid()) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) 0x6F04);
        }

        // Re-wrap MK with new PIN
        try {
            CryptoHelper.wrapMasterKeyWithPIN(
                    buf, pinNewOffset, pinNewLength,
                    cardID, (short) 0, CARD_ID_LENGTH,
                    KDF_ITERATIONS,
                    mkUser, encUser,
                    aesCipher, aesKey, sha256);
        } catch (Exception e) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) 0x6F04);
        }

        // Update stored PIN hash
        Util.arrayCopyNonAtomic(newPinHash, (short) 0, hashPinUser, (short) 0, HASH_LENGTH);

        // Reset retry counter and set PIN changed flag
        pinRetryCounter = MAX_PIN_TRIES;
        pinChangedFlag = 1;

        // Clear master key
        Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
    }
}
