package util;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.ImageIcon; // Import ImageIcon
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.Iterator;

/**
 * ImageHelper - Utility class để xử lý ảnh
 * V6: Hỗ trợ resize và compress ảnh xuống ≤ 20KB để lưu vào thẻ thông minh
 */
public class ImageHelper {

    private static final int MAX_SIZE_BYTES = 20 * 1024; // 20KB
    private static final int INITIAL_WIDTH = 200; // Kích thước ban đầu: 200x200
    private static final int INITIAL_HEIGHT = 200;
    private static final int MIN_WIDTH = 50; // Kích thước tối thiểu
    private static final int MIN_HEIGHT = 50;

    /**
     * Resize và compress ảnh từ file xuống ≤ 20KB
     * 
     * @param imageFile File ảnh gốc
     * @return Base64 string của ảnh đã resize/compress, hoặc null nếu lỗi
     * @throws Exception nếu không thể xử lý ảnh
     */
    public static String resizeAndCompressToBase64(File imageFile) throws Exception {
        // 1. Đọc ảnh gốc
        BufferedImage originalImage = ImageIO.read(imageFile);
        if (originalImage == null) {
            throw new Exception("Không thể đọc file ảnh: " + imageFile.getName());
        }

        System.out.println("[ImageHelper] Ảnh gốc: " + originalImage.getWidth() + "x" +
                originalImage.getHeight() + " pixels");

        // 2. Resize dần dần cho đến khi <= 20KB
        int targetWidth = INITIAL_WIDTH;
        int targetHeight = INITIAL_HEIGHT;
        float compressionQuality = 0.85f; // Bắt đầu với quality 85%

        byte[] imageBytes = null;
        int attemptCount = 0;
        final int MAX_ATTEMPTS = 15; // Tối đa 15 lần thử

        while (attemptCount < MAX_ATTEMPTS) {
            attemptCount++;
            System.out.println("[ImageHelper] Attempt #" + attemptCount +
                    " - Size: " + targetWidth + "x" + targetHeight +
                    ", Quality: " + (int) (compressionQuality * 100) + "%");

            // Resize ảnh
            BufferedImage resizedImage = resizeImage(originalImage, targetWidth, targetHeight);

            // Compress ảnh
            imageBytes = compressImage(resizedImage, compressionQuality);

            System.out.println("[ImageHelper] Result: " + imageBytes.length + " bytes (" +
                    (imageBytes.length / 1024) + " KB)");

            // Kiểm tra kích thước
            if (imageBytes.length <= MAX_SIZE_BYTES) {
                System.out.println("[ImageHelper] ✓ Đạt yêu cầu sau " + attemptCount + " lần thử!");
                break;
            }

            // Giảm quality hoặc giảm size
            if (compressionQuality > 0.3f) {
                // Giảm quality trước (từ 0.85 → 0.3)
                compressionQuality -= 0.1f;
            } else {
                // Nếu quality đã thấp rồi, giảm kích thước
                targetWidth = (int) (targetWidth * 0.85);
                targetHeight = (int) (targetHeight * 0.85);
                compressionQuality = 0.7f; // Reset quality về 70%

                // Không cho nhỏ hơn kích thước tối thiểu
                if (targetWidth < MIN_WIDTH || targetHeight < MIN_HEIGHT) {
                    System.err.println("[ImageHelper] ✗ Không thể giảm kích thước thêm nữa!");
                    throw new Exception("Ảnh quá phức tạp, không thể nén xuống 20KB.\n" +
                            "Vui lòng chọn ảnh đơn giản hơn hoặc ảnh với ít màu sắc hơn.");
                }
            }
        }

        // AGGRESSIVE FALLBACK: Nếu sau MAX_ATTEMPTS vẫn > 20KB
        if (imageBytes != null && imageBytes.length > MAX_SIZE_BYTES) {
            System.out
                    .println("[ImageHelper] ⚠️ Sau " + MAX_ATTEMPTS + " lần vẫn > 20KB, áp dụng AGGRESSIVE resize...");

            // Force resize xuống rất nhỏ với quality thấp
            int aggressiveWidth = 30;
            int aggressiveHeight = 30;
            float aggressiveQuality = 0.1f;

            while (aggressiveWidth >= 20 && aggressiveHeight >= 20) {
                BufferedImage tinyImage = resizeImage(originalImage, aggressiveWidth, aggressiveHeight);
                imageBytes = compressImage(tinyImage, aggressiveQuality);

                System.out.println("[ImageHelper] Aggressive: " + aggressiveWidth + "x" + aggressiveHeight +
                        " @ " + (int) (aggressiveQuality * 100) + "% = " + imageBytes.length + " bytes");

                if (imageBytes.length <= MAX_SIZE_BYTES) {
                    System.out.println("[ImageHelper] ✓ Aggressive resize thành công!");
                    break;
                }

                // Giảm size thêm 10px
                aggressiveWidth -= 5;
                aggressiveHeight -= 5;
            }

            // Nếu vẫn > 20KB sau aggressive resize → throw error
            if (imageBytes.length > MAX_SIZE_BYTES) {
                throw new Exception("Không thể nén ảnh xuống 20KB ngay cả ở kích thước tối thiểu.\n" +
                        "Ảnh quá phức tạp hoặc có vấn đề. Vui lòng chọn ảnh khác.");
            }
        }

        // 3. Encode sang Base64
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        System.out.println("[ImageHelper] Base64 length: " + base64.length() + " chars");
        System.out.println("[ImageHelper] Final size: " + imageBytes.length + " bytes / 20480 bytes");

        return base64;
    }

    /**
     * Resize ảnh về kích thước mới
     * 
     * @param originalImage Ảnh gốc
     * @param targetWidth   Chiều rộng mới
     * @param targetHeight  Chiều cao mới
     * @return BufferedImage đã resize
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        // Tạo ảnh mới với kích thước target
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        // Vẽ ảnh gốc lên ảnh mới với smooth scaling
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * Compress ảnh với chất lượng chỉ định
     * 
     * @param image   Ảnh cần compress
     * @param quality Chất lượng (0.0 - 1.0)
     * @return byte[] của ảnh đã compress
     * @throws Exception nếu không thể compress
     */
    private static byte[] compressImage(BufferedImage image, float quality) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Lấy ImageWriter cho JPEG
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new Exception("Không tìm thấy JPEG encoder!");
        }

        ImageWriter writer = writers.next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);

        // Set compression quality
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        // Write ảnh
        writer.write(null, new IIOImage(image, null, null), param);

        // Cleanup
        ios.close();
        writer.dispose();

        return baos.toByteArray();
    }

    /**
     * Decode Base64 thành BufferedImage để hiển thị
     * 
     * @param base64String Base64 string của ảnh
     * @return BufferedImage hoặc null nếu lỗi
     */
    public static BufferedImage decodeBase64ToImage(String base64String) {
        try {
            if (base64String == null || base64String.isEmpty()) {
                return null;
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64String);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes);
            return ImageIO.read(bais);
        } catch (Exception e) {
            System.err.println("[ImageHelper] Lỗi decode Base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Tạo ImageIcon từ BufferedImage với kích thước chỉ định
     * 
     * @param image  BufferedImage
     * @param width  Chiều rộng mong muốn
     * @param height Chiều cao mong muốn
     * @return ImageIcon đã scale
     */
    public static ImageIcon createScaledIcon(BufferedImage image, int width, int height) {
        if (image == null) {
            return null;
        }
        Image scaledImage = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }
}
