package card;

import javax.smartcardio.*;
import java.util.List;

/**
 * CardManager - Quản lý kết nối và giao tiếp với thẻ thông minh qua PC/SC
 * Tham khảo từ SmartCard_Old, giữ nguyên pattern Singleton và cách tổ chức
 */
public class CardManager {

    private static CardManager instance;
    private CardChannel channel;
    private Card card;

    private CardManager() { }

    public static CardManager getInstance() {
        if (instance == null) {
            instance = new CardManager();
        }
        return instance;
    }

    /**
     * Kết nối với đầu đọc thẻ
     * @return true nếu kết nối thành công
     */
    public boolean connect() {
        try {
            // 1. Tìm Terminal
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();
            if (terminals.isEmpty()) {
                System.out.println("Không tìm thấy đầu đọc thẻ!");
                return false;
            }

            // 2. Kết nối vật lý
            CardTerminal terminal = terminals.get(0);
            card = terminal.connect("*"); // T=0 hoặc T=1
            channel = card.getBasicChannel();
            
            // QUAN TRỌNG: KHÔNG SELECT APPLET Ở ĐÂY
            // Để các Panel tự Select applet tương ứng (AdminApplet hoặc UserApplet)
            
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ngắt kết nối với thẻ
     */
    public void disconnect() {
        try {
            if (card != null) {
                card.disconnect(false);
            }
        } catch (CardException e) {
            e.printStackTrace();
        }
    }

    /**
     * Kiểm tra trạng thái kết nối
     */
    public boolean isConnected() {
        return channel != null;
    }
    
    /**
     * Lấy CardChannel để gửi APDU
     */
    public CardChannel getChannel() {
        return channel;
    }

    /**
     * Gửi CommandAPDU và nhận ResponseAPDU
     */
    public ResponseAPDU send(CommandAPDU cmd) throws CardException {
        if (channel == null) {
            throw new CardException("Chưa kết nối thẻ!");
        }
        return channel.transmit(cmd);
    }
    
    /**
     * Select applet bằng AID
     * @param aid Application Identifier của applet
     * @return true nếu select thành công
     */
    public boolean selectApplet(byte[] aid) {
        try {
            String aidHex = bytesToHex(aid);
            System.out.println("[CardManager] Đang select applet với AID: " + aidHex);
            
            CommandAPDU selectCmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aid);
            System.out.println("[CardManager] Select command: " + bytesToHex(selectCmd.getBytes()));
            
            ResponseAPDU resp = send(selectCmd);
            int sw = resp.getSW();
            System.out.println("[CardManager] Select response SW: " + String.format("0x%04X", sw));
            
            if (resp.getData().length > 0) {
                System.out.println("[CardManager] Select response data: " + bytesToHex(resp.getData()));
            }
            
            boolean success = (sw == 0x9000);
            if (success) {
                System.out.println("[CardManager] Select applet thành công với AID: " + aidHex);
            } else {
                System.out.println("[CardManager] Select applet THẤT BẠI với AID: " + aidHex + ", SW: " + String.format("0x%04X", sw));
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("[CardManager] Lỗi khi select applet: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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

