package HospitalCard;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.Cipher;

/**
 * AdminApplet - Applet cho thẻ Admin
 * 
 * Package: HospitalCard (chung cho cả AdminApplet và UserApplet)
 * Package AID: 11 22 33 44 55
 * Applet AID:  11 22 33 44 55 00
 * 
 * Chức năng:
 * - Xác thực PIN_admin
 * - Ký số bằng SK_admin (RSA)
 * - Sinh token reset PIN User
 */
public class AdminApplet extends Applet {
    
    // Package AID: 11 22 33 44 55 (chung cho cả 2 applet)
    private static final byte[] PACKAGE_AID = {
        (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55
    };
    
    // Applet AID: 11 22 33 44 55 00
    private static final byte[] APPLET_AID = {
        (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55, (byte)0x00
    };

    private static final byte CLA = (byte)0x80;
    
    // Instruction codes
    private static final byte INS_VERIFY_PIN_ADMIN = (byte)0x10;
    private static final byte INS_SIGN_CHALLENGE_ADMIN = (byte)0x11;
    private static final byte INS_GEN_RESET_TOKEN = (byte)0x12;
    private static final byte INS_GET_CARD_ID = (byte)0x13;
    private static final byte INS_INIT_ADMIN_CARD = (byte)0x14; // Khởi tạo thẻ Admin: set PIN hash và salt
    private static final byte INS_GET_PUBLIC_KEY = (byte)0x15; // Lấy PK_admin

    // PIN và bảo mật - lưu vào EEPROM (instance variables tự động lưu vào EEPROM)
    private byte[] pinAdminHash; // SHA-1(PIN_admin || salt_admin_hash)
    private byte[] saltAdminHash; // Salt để hash PIN
    private byte pinTriesRemaining;
    private boolean isAuthenticated; // Note: JavaCard không hỗ trợ từ khóa transient, nhưng boolean rất nhỏ
    
    // RSA keys - lưu vào EEPROM
    private KeyPair rsaKeyPair;
    private RSAPrivateKey rsaPrivKey;
    private RSAPublicKey rsaPubKey;
    private Cipher rsaCipher; // Note: Cipher sẽ được khởi tạo lại khi cần
    
    // Card ID - lưu vào EEPROM
    private byte[] cardIdAdmin; // 16 bytes

    protected AdminApplet(byte[] bArray, short bOffset, byte bLength) {
        // Cấp phát bộ nhớ - các biến instance này sẽ tự động lưu vào EEPROM
        pinAdminHash = new byte[20]; // SHA-1 = 20 bytes
        saltAdminHash = new byte[16];
        cardIdAdmin = new byte[16];
        
        // Khởi tạo
        pinTriesRemaining = 3;
        isAuthenticated = false;
        
        // Chỉ sinh cardId_admin nếu chưa có (kiểm tra xem có phải lần đầu install không)
        // Nếu cardIdAdmin đã có giá trị (từ EEPROM), không ghi đè
        boolean cardIdEmpty = true;
        short i;
        for (i = (short)0; i < (short)16; i = (short)(i + 1)) {
            if (cardIdAdmin[i] != 0) {
                cardIdEmpty = false;
                break;
            }
        }
        
        if (cardIdEmpty) {
            // Sinh cardId_admin ngẫu nhiên chỉ khi chưa có
            RandomData rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            rng.generateData(cardIdAdmin, (short)0, (short)16);
        }
        
        // Khởi tạo RSA key pair - nếu chưa có thì tạo mới
        try {
            if (rsaKeyPair == null) {
                rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
                rsaPrivKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
                rsaPubKey = (RSAPublicKey) rsaKeyPair.getPublic();
            } else {
                // Nếu đã có từ EEPROM, lấy lại references
                rsaPrivKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
                rsaPubKey = (RSAPublicKey) rsaKeyPair.getPublic();
            }
            // Cipher là transient, luôn tạo mới
            rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
        } catch (Exception e) {
            rsaKeyPair = null;
        }
        
        register(bArray, (short)(bOffset + 1), bArray[bOffset]);
    }

    /**
     * Install applet
     * AID: 11 22 33 44 55 00
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new AdminApplet(bArray, bOffset, bLength);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            // Reset trạng thái xác thực khi select applet (không lưu vào EEPROM vì là transient)
            isAuthenticated = false;
            // Khởi tạo lại cipher nếu cần (transient, không lưu vào EEPROM)
            if (rsaCipher == null && rsaKeyPair != null) {
                try {
                    rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
                } catch (Exception e) {
                    // Ignore
                }
            }
            return;
        }

        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buf[ISO7816.OFFSET_INS];

        // Các lệnh cần nhận dữ liệu
        if (ins == INS_VERIFY_PIN_ADMIN || ins == INS_SIGN_CHALLENGE_ADMIN || 
            ins == INS_GEN_RESET_TOKEN || ins == INS_INIT_ADMIN_CARD) {
            short lc = apdu.setIncomingAndReceive();
            if (lc == 0 && ins != INS_GET_CARD_ID && ins != INS_GET_PUBLIC_KEY) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
        }

        switch (ins) {
            case INS_VERIFY_PIN_ADMIN:
                verifyPinAdmin(apdu);
                break;
                
            case INS_SIGN_CHALLENGE_ADMIN:
                signChallengeAdmin(apdu);
                break;
                
            case INS_GEN_RESET_TOKEN:
                generateResetToken(apdu);
                break;
                
            case INS_GET_CARD_ID:
                getCardId(apdu);
                break;
                
            case INS_INIT_ADMIN_CARD:
                initAdminCard(apdu);
                break;
                
            case INS_GET_PUBLIC_KEY:
                getPublicKey(apdu);
                break;
                
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /**
     * Xác thực PIN_admin
     * Nhận PIN plaintext từ UI, hash trên thẻ bằng SHA-1(PIN || salt_admin_hash), so sánh với pinAdminHash
     */
    private void verifyPinAdmin(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        
        // Kiểm tra PIN tries
        if (pinTriesRemaining == 0) {
            ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
        }
        
        // Nhận PIN plaintext
        short pinLen = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
        if (pinLen == 0 || pinLen > 16) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Verify PIN bằng helper
        if (PINHelper.verifyPin(buf, ISO7816.OFFSET_CDATA, pinLen, pinAdminHash, saltAdminHash)) {
            isAuthenticated = true;
            pinTriesRemaining = 3;
        } else {
            isAuthenticated = false;
            pinTriesRemaining--;
            PINHelper.throwPinTriesException(pinTriesRemaining);
        }
    }

    /**
     * Ký challenge bằng SK_admin
     */
    private void signChallengeAdmin(APDU apdu) {
        if (!isAuthenticated) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short challengeLen = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
        
        short sigLen = RSAHelper.sign(rsaCipher, rsaPrivKey,
                                     buf, ISO7816.OFFSET_CDATA, challengeLen,
                                     buf, (short)0);
        apdu.setOutgoingAndSend((short)0, sigLen);
    }

    /**
     * Sinh token reset PIN User
     * Nhận message M, ký bằng SK_admin
     */
    private void generateResetToken(APDU apdu) {
        if (!isAuthenticated) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        
        byte[] buf = apdu.getBuffer();
        short messageLen = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
        
        short sigLen = RSAHelper.sign(rsaCipher, rsaPrivKey,
                                     buf, ISO7816.OFFSET_CDATA, messageLen,
                                     buf, (short)0);
        apdu.setOutgoingAndSend((short)0, sigLen);
    }

    /**
     * Return cardId_admin
     */
    private void getCardId(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(cardIdAdmin, (short)0, buf, (short)0, (short)16);
        apdu.setOutgoingAndSend((short)0, (short)16);
    }
    
    /**
     * Khởi tạo thẻ Admin
     * Nhận PIN plaintext, hash và lưu vào pinAdminHash
     * Format: PIN_len(1) + PIN + salt(16)
     * Tất cả dữ liệu sẽ tự động lưu vào EEPROM khi gán vào instance variables
     */
    private void initAdminCard(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        
        // Kiểm tra LC (Length of Command data)
        short lc = (short)(buf[ISO7816.OFFSET_LC] & 0xFF);
        if (lc < 17) { // Tối thiểu: 1 (pinLen) + 1 (PIN) + 16 (salt) = 18, nhưng có thể PIN = 1 byte
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Parse: PIN_len(1) + PIN + salt(16)
        short offset = ISO7816.OFFSET_CDATA;
        byte pinLen = buf[offset];
        offset++;
        
        if (pinLen == 0 || pinLen > 16) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // Kiểm tra tổng độ dài: 1 + pinLen + 16
        if (lc != (short)(1 + pinLen + 16)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        
        // PIN nằm ở offset (sau pinLen)
        short pinOffset = offset;
        
        // Salt nằm sau PIN: offset + pinLen
        short saltOffset = (short)(offset + pinLen);
        
        // Read salt (16 bytes) - lưu vào EEPROM (instance variable)
        Util.arrayCopyNonAtomic(buf, saltOffset, saltAdminHash, (short)0, (short)16);
        
        // Hash PIN: SHA-1(PIN || salt) - lưu vào EEPROM (instance variable)
        PINHelper.createPinHash(buf, pinOffset, (short)pinLen, saltAdminHash, pinAdminHash);
        
        // Sinh RSA key pair nếu chưa có - lưu vào EEPROM (instance variable)
        if (rsaKeyPair == null) {
            try {
                rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_1024);
                rsaPrivKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
                rsaPubKey = (RSAPublicKey) rsaKeyPair.getPublic();
            } catch (Exception e) {
                ISOException.throwIt(ISO7816.SW_UNKNOWN);
            }
        }
        RSAHelper.generateKeyPair(rsaKeyPair);
        
        // Reset PIN tries - lưu vào EEPROM (instance variable)
        pinTriesRemaining = 3;
        isAuthenticated = false;
        
        // Tất cả dữ liệu đã được lưu vào EEPROM tự động khi gán vào instance variables
        // Return success - JavaCard automatically returns 0x9000 if no exception
    }
    
    /**
     * Get PK_admin (public key) from card
     * Return: modLen(2) + modulus + expLen(2) + exponent
     */
    private void getPublicKey(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        
        short pkLen = RSAHelper.getPublicKeyBytes(rsaPubKey, buf, (short)0);
        apdu.setOutgoingAndSend((short)0, pkLen);
    }
}
