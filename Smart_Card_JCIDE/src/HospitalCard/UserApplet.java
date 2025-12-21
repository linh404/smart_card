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
        short offset = ISO7816.OFFSET_CDATA;

        try {
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

            int initialBalance = 0;
            short dataLen = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
            short cardIdPart = (short) (hasProvidedCardID ? CARD_ID_LENGTH : 0);
            short expectedMinLen = (short) (cardIdPart + 2 + patientInfoLength + pinUserLength + pinAdminResetLength);
            if (dataLen >= expectedMinLen + 4) {
                initialBalance = DataHelper.getInt(buf, offset);
            } else {
                initialBalance = 0;
            }

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

            try {
                sha256.reset();
                sha256.doFinal(buf, pinUserOffset, pinUserLength, hashPinUser, (short) 0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short) 0x6F03);
            }

            try {
                sha256.reset();
                sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, hashPinAdminReset, (short) 0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short) 0x6F03);
            }

            byte[] tempKey = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);
            try {
                CryptoHelper.KDF(buf, pinUserOffset, pinUserLength,
                        cardID, (short) 0, CARD_ID_LENGTH,
                        KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException ce) {
                ISOException.throwIt((short) 0x6F03);
            }

            try {
                aesKey.setKey(tempKey, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(mkUser, (short) 0, MK_USER_LENGTH, encUser, (short) 0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short) 0x6F04);
            }

            try {
                CryptoHelper.KDF(buf, pinAdminResetOffset, pinAdminResetLength,
                        cardID, (short) 0, CARD_ID_LENGTH,
                        KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException ce) {
                ISOException.throwIt((short) 0x6F03);
            }

            try {
                aesKey.setKey(tempKey, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(mkUser, (short) 0, MK_USER_LENGTH, encAdmin, (short) 0);
            } catch (CryptoException ce) {
                ISOException.throwIt((short) 0x6F04);
            }

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

            initialized = 1;
            pinChangedFlag = 0;

            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            Util.arrayFillNonAtomic(tempKey, (short) 0, (short) 20, (byte) 0);

            short respOffset = 0;
            buf[respOffset++] = (byte) 0x00;

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
                }
            }

            try {
                apdu.setOutgoingAndSend((short) 0, respOffset);
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

        // Step marker for debugging
        byte[] tempHash = null;

        try {
            // Step A1: Create transient array for hash
            tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
        } catch (SystemException e) {
            ISOException.throwIt((short) 0x6FA1); // Step A1 failed - transient memory
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FA2); // Step A1 failed - other
        }

        try {
            // Step A2: Hash the PIN
            sha256.reset();
            sha256.doFinal(buf, pinUserOffset, pinUserLength, tempHash, (short) 0);
        } catch (CryptoException e) {
            ISOException.throwIt((short) 0x6F01);
        } catch (NullPointerException e) {
            ISOException.throwIt((short) 0x6FA3); // sha256 is null
        } catch (Exception e) {
            ISOException.throwIt((short) 0x6FA4);
        }

        // Step A3: Compare PIN hash
        if (Util.arrayCompare(tempHash, (short) 0, hashPinUser, (short) 0, HASH_LENGTH) != 0) {
            pinRetryCounter--;
            if (pinRetryCounter == 0) {
                blockedFlag = 1;
            }
            ISOException.throwIt((short) (0x63C0 | pinRetryCounter));
        }

        pinRetryCounter = MAX_PIN_TRIES;

        try {
            byte[] tempKey = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);

            try {
                CryptoHelper.KDF(buf, pinUserOffset, pinUserLength,
                        cardID, (short) 0, CARD_ID_LENGTH,
                        KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException e) {
                ISOException.throwIt((short) 0x6F03);
            }

            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            try {
                aesKey.setKey(tempKey, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                aesCipher.doFinal(encUser, (short) 0, MK_USER_LENGTH, mkUser, (short) 0);
            } catch (CryptoException e) {
                Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
                short reason = e.getReason();
                if (reason == 0) {
                    ISOException.throwIt((short) 0x6F04);
                } else {
                    ISOException.throwIt((short) (0x6F04 | (reason & 0x0F)));
                }
            }

            byte[] decryptedData = JCSystem.makeTransientByteArray(MAX_PATIENT_DATA_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            short decryptedLength = 0;
            try {
                aesKey.setKey(mkUser, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);

                decryptedLength = aesCipher.doFinal(encPatient, (short) 0, encPatientLength, decryptedData, (short) 0);

                while (decryptedLength > 0 && decryptedData[(short) (decryptedLength - 1)] == 0) {
                    decryptedLength--;
                }
            } catch (CryptoException e) {
                ISOException.throwIt((short) 0x6F05);
            }

            byte[] balanceBytes = JCSystem.makeTransientByteArray(BALANCE_ENC_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            int balance = 0;
            try {
                aesKey.setKey(mkUser, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                aesCipher.doFinal(encBalance, (short) 0, BALANCE_ENC_LENGTH, balanceBytes, (short) 0);
                balance = DataHelper.getInt(balanceBytes, (short) 0);
            } catch (CryptoException e) {
                ISOException.throwIt((short) 0x6F06);
            }

            short offset = 0;
            buf[offset++] = (byte) 0x00;
            Util.setShort(buf, offset, decryptedLength);
            offset += 2;
            Util.arrayCopyNonAtomic(decryptedData, (short) 0, buf, offset, decryptedLength);
            offset += decryptedLength;
            DataHelper.setInt(buf, offset, balance);
            offset += 4;

            Util.arrayFillNonAtomic(tempKey, (short) 0, (short) 20, (byte) 0);
            Util.arrayFillNonAtomic(tempHash, (short) 0, HASH_LENGTH, (byte) 0);
            Util.arrayFillNonAtomic(balanceBytes, (short) 0, BALANCE_ENC_LENGTH, (byte) 0);

            apdu.setOutgoingAndSend((short) 0, offset);

        } catch (

        CryptoException e) {
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
            ISOException.throwIt((short) 0x6F99);
        }
    }

    private void updatePatientData(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (mkUser[0] == 0 && mkUser[1] == 0 && mkUser[2] == 0 && mkUser[3] == 0) {
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
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            try {
                sha256.reset();
                sha256.doFinal(buf, pinOldOffset, pinOldLength, tempHash, (short) 0);
            } catch (CryptoException e) {
                ISOException.throwIt((short) 0x6F01);
            }

            if (Util.arrayCompare(tempHash, (short) 0, hashPinUser, (short) 0, HASH_LENGTH) != 0) {
                pinRetryCounter--;
                if (pinRetryCounter == 0) {
                    blockedFlag = 1;
                }
                ISOException.throwIt((short) (0x63C0 | pinRetryCounter));
            }

            byte[] tempHashNew = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            try {
                sha256.reset();
                sha256.doFinal(buf, pinNewOffset, pinNewLength, tempHashNew, (short) 0);
            } catch (CryptoException e) {
                ISOException.throwIt((short) 0x6F01);
            }

            if (Util.arrayCompare(tempHashNew, (short) 0, hashPinUser, (short) 0, HASH_LENGTH) == 0) {
                ISOException.throwIt((short) 0x6A80);
            }

            byte[] tempKey = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);
            try {
                CryptoHelper.KDF(buf, pinOldOffset, pinOldLength,
                        cardID, (short) 0, CARD_ID_LENGTH,
                        KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException e) {
                ISOException.throwIt((short) (0x6F03 | (e.getReason() & 0x0F)));
            }

            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            try {
                aesKey.setKey(tempKey, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
                aesCipher.doFinal(encUser, (short) 0, MK_USER_LENGTH, mkUser, (short) 0);

                boolean mkUserValid = false;
                for (short i = 0; i < MK_USER_LENGTH; i++) {
                    if (mkUser[i] != 0) {
                        mkUserValid = true;
                        break;
                    }
                }
                if (!mkUserValid) {
                    Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
                    ISOException.throwIt((short) 0x6F04);
                }
            } catch (CryptoException e) {
                Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
                ISOException.throwIt((short) (0x6F04 | (e.getReason() & 0x0F)));
            }

            try {
                CryptoHelper.KDF(buf, pinNewOffset, pinNewLength,
                        cardID, (short) 0, CARD_ID_LENGTH,
                        KDF_ITERATIONS, tempKey, sha256);
            } catch (CryptoException e) {
                Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
                ISOException.throwIt((short) (0x6F03 | (e.getReason() & 0x0F)));
            }

            try {
                aesKey.setKey(tempKey, (short) 0);
                aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
                aesCipher.doFinal(mkUser, (short) 0, MK_USER_LENGTH, encUser, (short) 0);
            } catch (CryptoException e) {
                Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
                ISOException.throwIt((short) (0x6F04 | (e.getReason() & 0x0F)));
            }

            try {
                sha256.reset();
                sha256.doFinal(buf, pinNewOffset, pinNewLength, hashPinUser, (short) 0);
            } catch (CryptoException e) {
                Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
                ISOException.throwIt((short) 0x6F01);
            }

            pinRetryCounter = MAX_PIN_TRIES;
            pinChangedFlag = 1;

            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            Util.arrayFillNonAtomic(tempKey, (short) 0, (short) 20, (byte) 0);
            Util.arrayFillNonAtomic(tempHash, (short) 0, HASH_LENGTH, (byte) 0);
            Util.arrayFillNonAtomic(tempHashNew, (short) 0, HASH_LENGTH, (byte) 0);

            buf[0] = (byte) 0x00;
            apdu.setOutgoingAndSend((short) 0, (short) 1);

        } catch (CryptoException e) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) (0x6F00 | (e.getReason() & 0x0F)));
        } catch (ArrayIndexOutOfBoundsException e) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) 0x6F30);
        } catch (Exception e) {
            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            ISOException.throwIt((short) 0x6F00);
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
            byte[] tempHash = JCSystem.makeTransientByteArray(HASH_LENGTH, JCSystem.CLEAR_ON_DESELECT);
            sha256.reset();
            sha256.doFinal(buf, pinAdminResetOffset, pinAdminResetLength, tempHash, (short) 0);

            if (Util.arrayCompare(tempHash, (short) 0, hashPinAdminReset, (short) 0, HASH_LENGTH) != 0) {
                ISOException.throwIt((short) 0x6300);
            }

            byte[] tempKey = JCSystem.makeTransientByteArray((short) 20, JCSystem.CLEAR_ON_DESELECT);
            CryptoHelper.KDF(buf, pinAdminResetOffset, pinAdminResetLength,
                    cardID, (short) 0, CARD_ID_LENGTH,
                    KDF_ITERATIONS, tempKey, sha256);

            aesKey.setKey(tempKey, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_DECRYPT);
            aesCipher.doFinal(encAdmin, (short) 0, MK_USER_LENGTH, mkUser, (short) 0);

            CryptoHelper.KDF(buf, newPinUserOffset, newPinUserLength,
                    cardID, (short) 0, CARD_ID_LENGTH,
                    KDF_ITERATIONS, tempKey, sha256);

            aesKey.setKey(tempKey, (short) 0);
            aesCipher.init(aesKey, Cipher.MODE_ENCRYPT);
            aesCipher.doFinal(mkUser, (short) 0, MK_USER_LENGTH, encUser, (short) 0);

            sha256.reset();
            sha256.doFinal(buf, newPinUserOffset, newPinUserLength, hashPinUser, (short) 0);

            pinRetryCounter = MAX_PIN_TRIES;
            blockedFlag = 0;
            pinChangedFlag = 0;

            Util.arrayFillNonAtomic(mkUser, (short) 0, (short) (MK_USER_LENGTH + 16), (byte) 0);
            Util.arrayFillNonAtomic(tempKey, (short) 0, (short) 20, (byte) 0);
            Util.arrayFillNonAtomic(tempHash, (short) 0, HASH_LENGTH, (byte) 0);

            buf[0] = (byte) 0x00;
            apdu.setOutgoingAndSend((short) 0, (short) 1);

        } catch (CryptoException e) {
            ISOException.throwIt((short) (0x6F00 | e.getReason()));
        }
    }

    private void signChallenge(APDU apdu) {
        if (initialized != 1) {
            ISOException.throwIt((short) 0x6985);
        }

        if (mkUser[0] == 0 && mkUser[1] == 0 && mkUser[2] == 0 && mkUser[3] == 0) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        byte[] buf = apdu.getBuffer();
        short challengeLen = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
        short challengeOffset = ISO7816.OFFSET_CDATA;

        if (challengeLen <= 0 || challengeLen > 117) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        try {
            short sigLen = RSAHelper.sign(rsaCipher, skUser,
                    buf, challengeOffset, challengeLen,
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

        if (mkUser[0] == 0 && mkUser[1] == 0 && mkUser[2] == 0 && mkUser[3] == 0) {
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
}
