package model;

import java.util.Date;

/**
 * Transaction - Model cho giao dịch
 */
public class Transaction {
    private Date thoiGian;
    private String loai; // "CREDIT" hoặc "DEBIT"
    private int soTien;
    private int soDuSau;

    // Getters and Setters
    public Date getThoiGian() { return thoiGian; }
    public void setThoiGian(Date thoiGian) { this.thoiGian = thoiGian; }

    public String getLoai() { return loai; }
    public void setLoai(String loai) { this.loai = loai; }

    public int getSoTien() { return soTien; }
    public void setSoTien(int soTien) { this.soTien = soTien; }

    public int getSoDuSau() { return soDuSau; }
    public void setSoDuSau(int soDuSau) { this.soDuSau = soDuSau; }
}

