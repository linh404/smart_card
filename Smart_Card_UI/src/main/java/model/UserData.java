package model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * UserData - Model cho dữ liệu bệnh nhân
 */
public class UserData implements Serializable {
    private String hoTen;
    private String idBenhNhan;
    private String ngaySinh;
    private String queQuan;
    private String maBHYT;
    private long balance; // Số dư tài khoản (đơn vị: VNĐ)

    // Getters and Setters
    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }

    public String getIdBenhNhan() { return idBenhNhan; }
    public void setIdBenhNhan(String idBenhNhan) { this.idBenhNhan = idBenhNhan; }

    public String getNgaySinh() { return ngaySinh; }
    public void setNgaySinh(String ngaySinh) { this.ngaySinh = ngaySinh; }

    public String getQueQuan() { return queQuan; }
    public void setQueQuan(String queQuan) { this.queQuan = queQuan; }

    public String getMaBHYT() { return maBHYT; }
    public void setMaBHYT(String maBHYT) { this.maBHYT = maBHYT; }

    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }

    /**
     * Chuyển UserData thành byte[] để gửi xuống thẻ
     */
    public byte[] toBytes() {
        // Format: hoTen|idBenhNhan|ngaySinh|queQuan|maBHYT|balance
        // Đơn giản hóa: dùng JSON hoặc format cố định
        StringBuilder sb = new StringBuilder();
        sb.append(hoTen).append("|");
        sb.append(idBenhNhan).append("|");
        sb.append(ngaySinh).append("|");
        sb.append(queQuan).append("|");
        sb.append(maBHYT).append("|");
        sb.append(balance);
        
        byte[] textBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[textBytes.length + 4];
        System.arraycopy(intToBytes(textBytes.length), 0, result, 0, 4);
        System.arraycopy(textBytes, 0, result, 4, textBytes.length);
        return result;
    }

    /**
     * Parse byte[] thành UserData
     */
    public static UserData fromBytes(byte[] data) {
        if (data == null || data.length < 4) return null;
        
        int textLen = bytesToInt(data, 0);
        String text = new String(data, 4, textLen, StandardCharsets.UTF_8);
        String[] parts = text.split("\\|");
        
        if (parts.length < 5) return null;
        
        UserData ud = new UserData();
        ud.setHoTen(parts[0]);
        ud.setIdBenhNhan(parts[1]);
        ud.setNgaySinh(parts[2]);
        ud.setQueQuan(parts[3]);
        ud.setMaBHYT(parts[4]);
        
        // Parse balance (backward compatible - default to 0 if not present)
        if (parts.length >= 6) {
            try {
                ud.setBalance(Long.parseLong(parts[5]));
            } catch (NumberFormatException e) {
                ud.setBalance(0);
            }
        } else {
            ud.setBalance(0);
        }
        
        return ud;
    }

    private static byte[] intToBytes(int value) {
        return new byte[] {
            (byte)(value >> 24),
            (byte)(value >> 16),
            (byte)(value >> 8),
            (byte)value
        };
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }
}

