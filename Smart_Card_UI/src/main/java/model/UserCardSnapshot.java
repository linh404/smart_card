package model;

import com.google.gson.annotations.SerializedName;

/**
 * UserCardSnapshot - Model cho snapshot demo của thẻ User
 * Lưu plaintext data để có thể nạp nhanh vào applet User khi demo với JCIDE
 * KHÔNG lưu các khóa bí mật (MK_user, MK_enc_user, MK_enc_user_admin, SK_user...)
 */
public class UserCardSnapshot {
    
    @SerializedName("cardId")
    private String cardIdHex; // Card ID dạng hex string
    
    @SerializedName("hoTen")
    private String hoTen;
    
    @SerializedName("idBenhNhan")
    private String idBenhNhan;
    
    @SerializedName("ngaySinh")
    private String ngaySinh;
    
    @SerializedName("queQuan")
    private String queQuan;
    
    @SerializedName("maBHYT")
    private String maBHYT;
    
    @SerializedName("pinUserDefault")
    private String pinUserDefault; // PIN mặc định (plaintext, chỉ để demo)
    
    @SerializedName("createdAt")
    private String createdAt; // Timestamp tạo snapshot
    
    @SerializedName("updatedAt")
    private String updatedAt; // Timestamp cập nhật lần cuối

    // Constructors
    public UserCardSnapshot() {
    }

    public UserCardSnapshot(String cardIdHex, UserData userData, String pinUserDefault) {
        this.cardIdHex = cardIdHex;
        if (userData != null) {
            this.hoTen = userData.getHoTen();
            this.idBenhNhan = userData.getIdBenhNhan();
            this.ngaySinh = userData.getNgaySinh();
            this.queQuan = userData.getQueQuan();
            this.maBHYT = userData.getMaBHYT();
        }
        this.pinUserDefault = pinUserDefault;
    }

    // Getters and Setters
    public String getCardIdHex() {
        return cardIdHex;
    }

    public void setCardIdHex(String cardIdHex) {
        this.cardIdHex = cardIdHex;
    }

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

    public String getPinUserDefault() {
        return pinUserDefault;
    }

    public void setPinUserDefault(String pinUserDefault) {
        this.pinUserDefault = pinUserDefault;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Chuyển UserCardSnapshot thành UserData
     */
    public UserData toUserData() {
        UserData userData = new UserData();
        userData.setHoTen(this.hoTen);
        userData.setIdBenhNhan(this.idBenhNhan);
        userData.setNgaySinh(this.ngaySinh);
        userData.setQueQuan(this.queQuan);
        userData.setMaBHYT(this.maBHYT);
        return userData;
    }

    /**
     * Tạo UserCardSnapshot từ UserData
     */
    public static UserCardSnapshot fromUserData(String cardIdHex, UserData userData, String pinUserDefault) {
        UserCardSnapshot snapshot = new UserCardSnapshot(cardIdHex, userData, pinUserDefault);
        return snapshot;
    }
}

