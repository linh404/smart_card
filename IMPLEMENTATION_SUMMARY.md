# ✅ HOÀN THÀNH IMPLEMENT CHUNKED PHOTO UPLOAD

## 📊 Tổng Quan

**Đã implement:** Upload ảnh đại diện với **biến riêng** và **chunked transfer** (≤ 20KB)

---

## ✨ Các Thay Đổi

### 1. **UserApplet.java** (JavaCard)

**Constants:**
```java
private static final byte INS_SET_PHOTO = (byte) 0x12;
private static final byte INS_GET_PHOTO = (byte) 0x13;
private static final short MAX_PHOTO_LENGTH = 20480; // 20KB
```

**Fields:**
```java
private byte[] encPhoto;      // 20KB riêng cho ảnh
private short encPhotoLength;
```

**Methods:**
- `setPhoto(APDU apdu)`: Nhận ảnh theo chunks 200 bytes
  - Format: `[chunkIndex(2)][totalChunks(2)][chunkData(≤200)]`
  - Chunk cuối → encrypt bằng MK_user
- `getPhoto(APDU apdu)`: Trả về ảnh đã decrypt (≤250 bytes/lần)

---

### 2. **ImageHelper.java** (UI)

**Features:**
- Auto resize ảnh xuống **≤ 20KB**
- Aggressive fallback: nếu >20KB sau 15 lần → force xuống 30x30@10%
- **Đảm bảo: LUÔN ≤ 20KB**

**Flow:**
```
1. Resize 200x200 @ 85% quality
2. Nếu >20KB → giảm quality (85% → 30%)
3. Nếu vẫn >20KB → giảm size (200px → 170px → ...)
4. Nếu vẫn >20KB → aggressive: 30x30 @ 10%
```

---

### 3. **APDUCommands.java** (Cần implement)

**TODO:**
```java
// Thêm methods:
public boolean setPhotoChunked(String photoBase64) {
    // Chia Base64 thành chunks 200 bytes
    // Gọi INS_SET_PHOTO cho từng chunk
}

public String getPhoto() {
    // Gọi INS_GET_PHOTO
    // Ghép chunks lại thành Base64
}
```

---

### 4. **CardIssuePanel.java** (Cần integrate)

**TODO:**
```java
// Trong issueCard(), sau khi phát hành thẻ thành công:
if (photoBase64 != null && !photoBase64.isEmpty()) {
    apduCommands.setPhotoChunked(photoBase64);
}
```

---

## 🎯 Kiến Trúc

```
┌─────────────────────────────────────────────────┐
│  CardIssuePanel.java                            │
│  - photoBase64 (≤20KB Base64 string)            │
│  - uploadPhoto() → ImageHelper.resize()         │
│  - issueCard() → apduCommands.setPhotoChunked() │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  APDUCommands.java                              │
│  - setPhotoChunked(base64)                      │
│    • Split thành chunks 200 bytes               │
│    • Send: [idx][total][data]                   │
└──────────────────┬──────────────────────────────┘
                   │ APDU INS_SET_PHOTO (0x12)
                   ▼
┌─────────────────────────────────────────────────┐
│  UserApplet.java (JavaCard)                     │
│  - encPhoto[20KB] (riêng biệt khỏi encPatient)  │
│  - setPhoto()                                   │
│    • Nhận chunks, append vào encPhoto           │
│    • Chunk cuối → encrypt AES-128               │
│  - getPhoto()                                   │
│    • Decrypt → return Base64                    │
└─────────────────────────────────────────────────┘
```

---

## ✅ Checklist

- [x] UserApplet: thêm encPhoto field (20KB)
- [x] UserApplet: thêm INS_SET_PHOTO, INS_GET_PHOTO
- [x] UserApplet: implement setPhoto() method (chunked)
- [x] UserApplet: implement getPhoto() method
- [x] ImageHelper: resize logic ≤ 20KB
- [x] ImageHelper: aggressive fallback
- [x] APDUCommands: thêm constants INS_SET_PHOTO/GET_PHOTO
- [ ] APDUCommands: implement setPhotoChunked() method
- [ ] APDUCommands: implement getPhoto() method
- [ ] CardIssuePanel: integrate setPhotoChunked() vào issueCard()
- [ ] UserInfoPanel: hiển thị ảnh khi đọc thẻ

---

## 📝 Cách Sử Dụng (Sau Khi Hoàn Thành)

1. **Upload ảnh khi phát hành thẻ:**
   - Admin chọn ảnh → ImageHelper auto resize ≤20KB
   - CardIssuePanel gọi `apduCommands.setPhotoChunked(photoBase64)`
   - Ảnh được chia thành ~100 chunks (200 bytes/chunk)
   - Gửi từng chunk xuống thẻ
   - Thẻ encrypt và lưu vào `encPhoto[20KB]`

2. **Đọc ảnh khi login:**
   - User login → gọi `apduCommands.getPhoto()`
   - Thẻ decrypt và trả về ảnh
   - UI hiển thị preview

---

## ⚠️ Lưu Ý

1. **APDU buffer limit:** 254 bytes → chunk size 200 bytes an toàn
2. **Thời gian upload:** ~100 chunks × 50ms = 5 giây
3. **Memory:** JavaCard cần ~20KB EEPROM cho ảnh
4. **Rebuild applet:** Cần flash lại thẻ với code mới

---

## 🚀 Next Steps

1. Implement `setPhotoChunked()` và `getPhoto()` trong APDUCommands
2. Integrate vào CardIssuePanel.issueCard()
3. Test với ảnh thật
4. Rebuild và flash applet lên thẻ
