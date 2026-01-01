package model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * UserData - Model cho dữ liệu bệnh nhân
 * V4: Thêm thông tin y tế khẩn cấp (nhóm máu, dị ứng, bệnh nền)
 */
public class UserData implements Serializable {
    private String hoTen;
    private String idBenhNhan;
    private String ngaySinh;
    private String queQuan;
    private String maBHYT;
    private long balance; // Số dư tài khoản (đơn vị: VNĐ)

    // V4: Thông tin y tế khẩn cấp
    private int nhomMau; // 0-8 (enum index)
    private String diUng; // Dị ứng (text tự do)
    private String benhNen; // Bệnh nền (text tự do)

    // V5: Giới tính
    private int gender; // 1=Nam, 2=Nữ, 3=Khác, 0=Không rõ

    // V6: Ảnh đại diện
    private String anhDaiDien; // Ảnh đại diện (Base64 encoded, resize ≤20KB)

    /**
     * Nhãn nhóm máu để hiển thị trên UI (JComboBox)
     * Index 0 = Chưa xác định, 1-8 = các nhóm máu
     */
    public static final String[] BLOOD_TYPE_LABELS = {
            "Chưa xác định", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    };

    // Getters and Setters
    public String getHoTen() {
        return hoTen;
    }

    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }

    public String getIdBenhNhan() {
        return idBenhNhan;
    }

    public void setIdBenhNhan(String idBenhNhan) {
        this.idBenhNhan = idBenhNhan;
    }

    public String getNgaySinh() {
        return ngaySinh;
    }

    public void setNgaySinh(String ngaySinh) {
        this.ngaySinh = ngaySinh;
    }

    public String getQueQuan() {
        return queQuan;
    }

    public void setQueQuan(String queQuan) {
        this.queQuan = queQuan;
    }

    public String getMaBHYT() {
        return maBHYT;
    }

    public void setMaBHYT(String maBHYT) {
        this.maBHYT = maBHYT;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    // V4: Getters/Setters cho thông tin y tế khẩn cấp
    public int getNhomMau() {
        return nhomMau;
    }

    public void setNhomMau(int nhomMau) {
        this.nhomMau = nhomMau;
    }

    public String getDiUng() {
        return diUng;
    }

    public void setDiUng(String diUng) {
        this.diUng = diUng;
    }

    public String getBenhNen() {
        return benhNen;
    }

    public void setBenhNen(String benhNen) {
        this.benhNen = benhNen;
    }

    /**
     * Lấy nhãn nhóm máu từ index
     */
    public String getNhomMauLabel() {
        if (nhomMau >= 0 && nhomMau < BLOOD_TYPE_LABELS.length) {
            return BLOOD_TYPE_LABELS[nhomMau];
        }
        return BLOOD_TYPE_LABELS[0]; // Chưa xác định
    }

    // V5: Getter/Setter cho giới tính
    public int getGender() {
        return gender;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }

    /**
     * Lấy nhãn giới tính từ int
     */
    public String getGenderLabel() {
        switch (gender) {
            case 1:
                return "Nam";
            case 2:
                return "Nữ";
            case 3:
                return "Khác";
            default:
                return "Không rõ";
        }
    }

    // V6: Getter/Setter cho ảnh đại diện
    public String getAnhDaiDien() {
        return anhDaiDien;
    }

    public void setAnhDaiDien(String anhDaiDien) {
        this.anhDaiDien = anhDaiDien;
    }

    /**
     * Chuyển UserData thành byte[] để gửi xuống thẻ
     * Note: Balance KHÔNG được bao gồm vì balance được lưu riêng biệt trên thẻ
     * V6 Format:
     * hoTen|idBenhNhan|ngaySinh|queQuan|maBHYT|nhomMau|diUng|benhNen|gender|anhDaiDien
     */
    public byte[] toBytes() {
        // Format V6:
        // hoTen|idBenhNhan|ngaySinh|queQuan|maBHYT|nhomMau|diUng|benhNen|gender|anhDaiDien
        // Balance KHÔNG được bao gồm - balance được lưu riêng và mã hóa bằng MK_user
        // trên thẻ
        StringBuilder sb = new StringBuilder();
        sb.append(hoTen).append("|");
        sb.append(idBenhNhan).append("|");
        sb.append(ngaySinh).append("|");
        sb.append(queQuan).append("|");
        sb.append(maBHYT).append("|");
        sb.append(nhomMau).append("|");
        sb.append(diUng != null ? diUng : "").append("|");
        sb.append(benhNen != null ? benhNen : "").append("|");
        sb.append(gender).append("|"); // V5: Thêm gender
        sb.append(anhDaiDien != null ? anhDaiDien : ""); // V6: Thêm ảnh đại diện

        byte[] textBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[textBytes.length + 4];
        System.arraycopy(intToBytes(textBytes.length), 0, result, 0, 4);
        System.arraycopy(textBytes, 0, result, 4, textBytes.length);
        return result;
    }

    /**
     * Parse byte[] thành UserData
     * Format: [patient_data_length (4 bytes)] [patient_data (text)] [balance (4
     * bytes, optional)]
     * Patient data format V6:
     * hoTen|idBenhNhan|ngaySinh|queQuan|maBHYT|nhomMau|diUng|benhNen|gender|anhDaiDien
     * Backward compatible với format cũ (V5: 9 fields, V4: 8 fields, V3: 5 fields)
     */
    public static UserData fromBytes(byte[] data) {
        if (data == null || data.length < 4)
            return null;

        int textLen = bytesToInt(data, 0);
        if (textLen <= 0 || data.length < 4 + textLen)
            return null;

        String text = new String(data, 4, textLen, StandardCharsets.UTF_8);
        String[] parts = text.split("\\|", -1); // -1 để giữ các phần rỗng ở cuối

        if (parts.length < 5)
            return null;

        UserData ud = new UserData();
        ud.setHoTen(parts[0]);
        ud.setIdBenhNhan(parts[1]);
        ud.setNgaySinh(parts[2]);
        ud.setQueQuan(parts[3]);
        ud.setMaBHYT(parts[4]);

        // V4: Parse thông tin y tế khẩn cấp (backward compatible)
        if (parts.length >= 8) {
            try {
                ud.setNhomMau(Integer.parseInt(parts[5]));
            } catch (NumberFormatException e) {
                ud.setNhomMau(0); // Chưa xác định
            }
            ud.setDiUng(parts[6]);
            ud.setBenhNen(parts[7]);
        } else {
            // Thẻ cũ không có thông tin y tế
            ud.setNhomMau(0);
            ud.setDiUng("");
            ud.setBenhNen("");
        }

        // V5: Parse gender (backward compatible)
        if (parts.length >= 9) {
            try {
                ud.setGender(Integer.parseInt(parts[8]));
            } catch (NumberFormatException e) {
                ud.setGender(0); // Không rõ
            }
        } else {
            // Thẻ cũ không có gender
            ud.setGender(0);
        }

        // V6: Parse ảnh đại diện (backward compatible)
        if (parts.length >= 10) {
            ud.setAnhDaiDien(parts[9]);
        } else {
            // Thẻ cũ không có ảnh
            ud.setAnhDaiDien("");
        }

        // Parse balance from end of data (4 bytes after patient_data)
        // Balance is stored separately on card and appended to response
        if (data.length >= 4 + textLen + 4) {
            // Balance is at the end (4 bytes)
            int balanceOffset = 4 + textLen;
            long balance = ((long) (data[balanceOffset] & 0xFF) << 24) |
                    ((long) (data[balanceOffset + 1] & 0xFF) << 16) |
                    ((long) (data[balanceOffset + 2] & 0xFF) << 8) |
                    ((long) (data[balanceOffset + 3] & 0xFF));
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
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8) |
                (bytes[offset + 3] & 0xFF);
    }
}
