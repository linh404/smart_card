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
    private short seq; // Transaction sequence number
    private byte[] txnHash; // Transaction hash (SHA-1, 20 bytes)

    // Getters and Setters
    public Date getThoiGian() { return thoiGian; }
    public void setThoiGian(Date thoiGian) { this.thoiGian = thoiGian; }

    public String getLoai() { return loai; }
    public void setLoai(String loai) { this.loai = loai; }

    public int getSoTien() { return soTien; }
    public void setSoTien(int soTien) { this.soTien = soTien; }

    public int getSoDuSau() { return soDuSau; }
    public void setSoDuSau(int soDuSau) { this.soDuSau = soDuSau; }

    public short getSeq() { return seq; }
    public void setSeq(short seq) { this.seq = seq; }

    public byte[] getTxnHash() { return txnHash; }
    public void setTxnHash(byte[] txnHash) { this.txnHash = txnHash; }
}

