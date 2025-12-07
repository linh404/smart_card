package db;

import java.util.Date;

/**
 * BHYTInfo - Model cho thông tin BHYT
 */
public class BHYTInfo {
    private String maBHYT;
    private String hoTen;
    private Date ngaySinh;
    private String soThe;
    private Date ngayHetHan;
    private String mucHuong;

    // Getters and Setters
    public String getMaBHYT() { return maBHYT; }
    public void setMaBHYT(String maBHYT) { this.maBHYT = maBHYT; }

    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }

    public Date getNgaySinh() { return ngaySinh; }
    public void setNgaySinh(Date ngaySinh) { this.ngaySinh = ngaySinh; }

    public String getSoThe() { return soThe; }
    public void setSoThe(String soThe) { this.soThe = soThe; }

    public Date getNgayHetHan() { return ngayHetHan; }
    public void setNgayHetHan(Date ngayHetHan) { this.ngayHetHan = ngayHetHan; }

    public String getMucHuong() { return mucHuong; }
    public void setMucHuong(String mucHuong) { this.mucHuong = mucHuong; }
}

