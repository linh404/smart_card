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
     * Note: Balance KHÔNG được bao gồm vì balance được lưu riêng biệt trên thẻ
     */
    public byte[] toBytes() {
        // Format: hoTen|idBenhNhan|ngaySinh|queQuan|maBHYT
        // Balance KHÔNG được bao gồm - balance được lưu riêng và mã hóa bằng MK_user trên thẻ
        StringBuilder sb = new StringBuilder();
        sb.append(hoTen).append("|");
        sb.append(idBenhNhan).append("|");
        sb.append(ngaySinh).append("|");
        sb.append(queQuan).append("|");
        sb.append(maBHYT);
        
        byte[] textBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[textBytes.length + 4];
        System.arraycopy(intToBytes(textBytes.length), 0, result, 0, 4);
        System.arraycopy(textBytes, 0, result, 4, textBytes.length);
        return result;
    }

    /**
     * Parse byte[] thành UserData
     * Format: [patient_data_length (4 bytes)] [patient_data (text)] [balance (4 bytes, optional)]
     * Patient data format: hoTen|idBenhNhan|ngaySinh|queQuan|maBHYT
     */
    public static UserData fromBytes(byte[] data) {
        if (data == null || data.length < 4) return null;
        
        int textLen = bytesToInt(data, 0);
        if (textLen <= 0 || data.length < 4 + textLen) return null;
        
        String text = new String(data, 4, textLen, StandardCharsets.UTF_8);
        String[] parts = text.split("\\|");
        
        if (parts.length < 5) return null;
        
        UserData ud = new UserData();
        ud.setHoTen(parts[0]);
        ud.setIdBenhNhan(parts[1]);
        ud.setNgaySinh(parts[2]);
        ud.setQueQuan(parts[3]);
        ud.setMaBHYT(parts[4]);
        
        // Parse balance from end of data (4 bytes after patient_data)
        // Balance is stored separately on card and appended to response
        if (data.length >= 4 + textLen + 4) {
            // Balance is at the end (4 bytes)
            int balanceOffset = 4 + textLen;
            long balance = ((long)(data[balanceOffset] & 0xFF) << 24) |
                          ((long)(data[balanceOffset + 1] & 0xFF) << 16) |
                          ((long)(data[balanceOffset + 2] & 0xFF) << 8) |
                          ((long)(data[balanceOffset + 3] & 0xFF));
            // Handle negative (shouldn't happen but just in case)
            if (balance > 0x7FFFFFFF) {
                balance = balance - 0x100000000L;
            }
            ud.setBalance(balance);
        } else {
            // No balance in response, default to 0
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

