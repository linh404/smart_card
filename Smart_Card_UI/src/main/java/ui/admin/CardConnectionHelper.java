package ui.admin;

import card.CardManager;
import card.APDUCommands;
import javax.swing.JOptionPane;
import javax.swing.JComponent;

/**
 * CardConnectionHelper - Helper class để đảm bảo kết nối thẻ và channel luôn sẵn sàng
 * Cung cấp các phương thức tiện ích để các admin panels sử dụng
 */
public class CardConnectionHelper {
    
    /**
     * Đảm bảo card đã được kết nối và APDUCommands có channel hợp lệ
     * @param cardManager CardManager instance
     * @param apduCommands APDUCommands instance
     * @param parentComponent Component cha để hiển thị dialog (có thể null)
     * @param showErrorDialog Nếu true, hiển thị dialog lỗi khi thất bại
     * @return true nếu connection và channel đã sẵn sàng, false nếu thất bại
     */
    public static boolean ensureCardConnection(CardManager cardManager, APDUCommands apduCommands, 
                                               JComponent parentComponent, boolean showErrorDialog) {
        try {
            // 1. Kiểm tra và kết nối thẻ nếu chưa kết nối
            if (!cardManager.isConnected()) {
                System.out.println("[CardConnectionHelper] Card chưa kết nối, đang kết nối...");
                if (!cardManager.connect()) {
                    String errorMsg = "❌ KHÔNG THỂ KẾT NỐI VỚI ĐẦU ĐỌC THẺ\n\n" +
                        "📋 VUI LÒNG KIỂM TRA CÁC BƯỚC SAU:\n\n" +
                        "1. ✅ Đầu đọc thẻ đã được cắm vào cổng USB của máy tính\n" +
                        "2. ✅ Thẻ thông minh đã được đặt vào đầu đọc (đúng chiều)\n" +
                        "3. ✅ Driver đầu đọc thẻ đã được cài đặt đúng\n" +
                        "   → Kiểm tra Device Manager (Windows) hoặc lsusb (Linux)\n" +
                        "4. ✅ Không có ứng dụng khác đang sử dụng đầu đọc thẻ\n" +
                        "   → Đóng các ứng dụng khác có thể đang dùng đầu đọc\n" +
                        "5. ✅ Đầu đọc thẻ hoạt động bình thường\n" +
                        "   → Thử rút và cắm lại đầu đọc\n\n" +
                        "💡 GỢI Ý: Nếu vẫn lỗi, thử khởi động lại ứng dụng hoặc máy tính.";
                    
                    System.err.println("[CardConnectionHelper] " + errorMsg);
                    
                    if (showErrorDialog && parentComponent != null) {
                        JOptionPane.showMessageDialog(parentComponent, errorMsg, 
                            "Lỗi kết nối đầu đọc thẻ", JOptionPane.ERROR_MESSAGE);
                    }
                    return false;
                }
                System.out.println("[CardConnectionHelper] ✓ Đã kết nối thẻ thành công");
            }
            
            // 2. Cập nhật channel cho APDUCommands (QUAN TRỌNG!)
            // Điều này đảm bảo APDUCommands luôn có channel hợp lệ
            apduCommands.setChannel(cardManager.getChannel());
            
            if (!apduCommands.isChannelReady()) {
                String errorMsg = "❌ CHANNEL KHÔNG SẴN SÀNG\n\n" +
                    "⚠️ Kết nối vật lý đã thành công nhưng không thể thiết lập kênh giao tiếp.\n\n" +
                    "📋 VUI LÒNG THỬ:\n\n" +
                    "1. ✅ Rút và cắm lại thẻ vào đầu đọc\n" +
                    "2. ✅ Kiểm tra thẻ có bị hỏng không (thử thẻ khác nếu có)\n" +
                    "3. ✅ Khởi động lại ứng dụng\n" +
                    "4. ✅ Kiểm tra đầu đọc thẻ có hoạt động bình thường không\n\n" +
                    "💡 Nếu vẫn lỗi, có thể đầu đọc hoặc thẻ bị lỗi phần cứng.";
                
                System.err.println("[CardConnectionHelper] " + errorMsg);
                
                if (showErrorDialog && parentComponent != null) {
                    JOptionPane.showMessageDialog(parentComponent, errorMsg, 
                        "Lỗi thiết lập kênh giao tiếp", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
            
            System.out.println("[CardConnectionHelper] ✓ Channel đã được cập nhật thành công");
            return true;
            
        } catch (Exception e) {
            String errorMsg = "Lỗi khi kiểm tra kết nối thẻ: " + e.getMessage();
            System.err.println("[CardConnectionHelper] " + errorMsg);
            e.printStackTrace();
            
            if (showErrorDialog && parentComponent != null) {
                JOptionPane.showMessageDialog(parentComponent, 
                    errorMsg + "\n\nVui lòng kiểm tra console để xem chi tiết.", 
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
    }
    
    /**
     * Đảm bảo card đã được kết nối, channel sẵn sàng, và applet đã được select
     * @param cardManager CardManager instance
     * @param apduCommands APDUCommands instance
     * @param parentComponent Component cha để hiển thị dialog (có thể null)
     * @param showErrorDialog Nếu true, hiển thị dialog lỗi khi thất bại
     * @param appletAID AID của applet cần select (có thể null nếu không cần select)
     * @return true nếu tất cả đã sẵn sàng, false nếu thất bại
     */
    public static boolean ensureCardAndAppletReady(CardManager cardManager, APDUCommands apduCommands,
                                                    JComponent parentComponent, boolean showErrorDialog,
                                                    byte[] appletAID) {
        // 1. Đảm bảo connection và channel
        if (!ensureCardConnection(cardManager, apduCommands, parentComponent, showErrorDialog)) {
            return false;
        }
        
        // 2. Select applet nếu có AID
        if (appletAID != null) {
            try {
                // Kiểm tra xem applet đã được select chưa bằng cách thử gửi GET_STATUS
                // Nếu applet đã được select, GET_STATUS sẽ thành công (SW = 0x9000)
                // Nếu chưa, sẽ trả về lỗi và chúng ta cần select
                boolean needSelect = true;
                try {
                    javax.smartcardio.CommandAPDU testCmd = new javax.smartcardio.CommandAPDU(
                        0x80, 0x01, 0x00, 0x00, 19); // GET_STATUS command
                    javax.smartcardio.ResponseAPDU testResp = cardManager.getChannel().transmit(testCmd);
                    if (testResp.getSW() == 0x9000) {
                        // Applet đã được select, không cần select lại
                        needSelect = false;
                        System.out.println("[CardConnectionHelper] Applet đã được select, bỏ qua select lại");
                    }
                } catch (Exception testEx) {
                    // Test command failed, cần select applet
                    needSelect = true;
                }
                
                if (needSelect) {
                    System.out.println("[CardConnectionHelper] Đang select applet...");
                    
                    // Select applet với retry tự động khi thẻ bị reset
                    if (!cardManager.selectApplet(appletAID, true)) {
                        String aidHex = bytesToHex(appletAID);
                        String errorMsg = "❌ KHÔNG TÌM THẤY APPLET TRÊN THẺ\n\n" +
                            "⚠️ Không thể tìm thấy applet với AID: " + aidHex + "\n\n" +
                            "📋 VUI LÒNG KIỂM TRA:\n\n" +
                            "1. ✅ Thẻ đã được cắm đúng cách vào đầu đọc\n" +
                            "2. ✅ Applet đã được cài đặt trên thẻ\n" +
                            "   → Sử dụng JCIDE hoặc công cụ cài đặt applet để kiểm tra\n" +
                            "3. ✅ AID của applet đúng: " + aidHex + "\n" +
                            "   → Kiểm tra lại file .exp hoặc source code\n" +
                            "4. ✅ Thẻ không bị hỏng hoặc corrupt\n" +
                            "   → Thử thẻ khác nếu có\n" +
                            "5. ✅ Nếu thẻ vừa bị reset, đã thử reconnect tự động\n\n" +
                            "💡 GỢI Ý: Nếu applet chưa được cài, vui lòng cài đặt applet trước khi sử dụng.";
                        
                        System.err.println("[CardConnectionHelper] " + errorMsg);
                        
                        if (showErrorDialog && parentComponent != null) {
                            JOptionPane.showMessageDialog(parentComponent, errorMsg, 
                                "Lỗi không tìm thấy Applet", JOptionPane.ERROR_MESSAGE);
                        }
                        return false;
                    }
                }
                
                // Cập nhật lại channel sau khi select applet (đảm bảo channel vẫn hợp lệ)
                apduCommands.setChannel(cardManager.getChannel());
                System.out.println("[CardConnectionHelper] ✓ Select applet thành công");
                
            } catch (Exception e) {
                String errorMsg = "Lỗi khi select applet: " + e.getMessage();
                System.err.println("[CardConnectionHelper] " + errorMsg);
                e.printStackTrace();
                
                // Nếu là lỗi reset card, thử reconnect một lần nữa
                if (errorMsg.contains("SCARD_W_RESET_CARD") || errorMsg.contains("reset")) {
                    System.out.println("[CardConnectionHelper] ⚠️ Phát hiện thẻ reset, đang thử reconnect...");
                    try {
                        cardManager.disconnect();
                        Thread.sleep(200);
                        if (cardManager.connect() && cardManager.selectApplet(appletAID, false)) {
                            apduCommands.setChannel(cardManager.getChannel());
                            System.out.println("[CardConnectionHelper] ✓ Đã reconnect và select applet thành công");
                            return true;
                        }
                    } catch (Exception retryEx) {
                        System.err.println("[CardConnectionHelper] ✗ Lỗi khi reconnect: " + retryEx.getMessage());
                    }
                }
                
                if (showErrorDialog && parentComponent != null) {
                    JOptionPane.showMessageDialog(parentComponent, errorMsg, 
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Kiểm tra trạng thái kết nối thẻ (không hiển thị dialog)
     * @param cardManager CardManager instance
     * @param apduCommands APDUCommands instance
     * @return true nếu connection và channel sẵn sàng, false nếu không
     */
    public static boolean checkConnectionStatus(CardManager cardManager, APDUCommands apduCommands) {
        try {
            boolean connected = cardManager.isConnected();
            boolean channelReady = apduCommands != null && apduCommands.isChannelReady();
            return connected && channelReady;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Lấy thông báo trạng thái kết nối dạng text
     * @param cardManager CardManager instance
     * @param apduCommands APDUCommands instance
     * @return String mô tả trạng thái
     */
    public static String getConnectionStatusText(CardManager cardManager, APDUCommands apduCommands) {
        try {
            boolean connected = cardManager.isConnected();
            boolean channelReady = apduCommands != null && apduCommands.isChannelReady();
            
            if (connected && channelReady) {
                return "Đã kết nối";
            } else if (connected && !channelReady) {
                return "Đã kết nối (channel chưa sẵn sàng)";
            } else {
                return "Chưa kết nối";
            }
        } catch (Exception e) {
            return "Lỗi kiểm tra";
        }
    }
    
    /**
     * Helper method để chuyển byte[] sang hex string
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}

