package util;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * MessageHelper - Utility class để hiển thị thông báo chuẩn hóa
 * Chỉ hiển thị thông báo thành công/thất bại đơn giản, không chi tiết kỹ thuật
 */
public class MessageHelper {

    private static final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    // ========== SUCCESS MESSAGES ==========

    public static void showCardIssueSuccess(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Phát hành thẻ thành công!",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showChangePinSuccess(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Đổi PIN thành công!",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showResetPinSuccess(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Reset PIN thành công!",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Thông báo giao dịch CREDIT thành công (nạp tiền)
     */
    public static void showCreditSuccess(Component parent, long newBalance) {
        JOptionPane.showMessageDialog(parent,
                String.format("Nạp tiền thành công!\n\nSố dư mới: %s",
                        currencyFormat.format(newBalance)),
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Thông báo giao dịch DEBIT thành công (thanh toán)
     * GIỮ NGUYÊN chi tiết BHYT theo yêu cầu
     */
    public static void showDebitSuccess(Component parent, long totalCost, long insurancePays,
            long userPays, long newBalance) {
        JOptionPane.showMessageDialog(parent,
                String.format(
                        "Thanh toán thành công!\n\n" +
                                "Tổng chi phí: %s\n" +
                                "BHYT đã chi trả: %s\n" +
                                "Bạn đã thanh toán: %s\n\n" +
                                "Số dư mới: %s",
                        currencyFormat.format(totalCost),
                        currencyFormat.format(insurancePays),
                        currencyFormat.format(userPays),
                        currencyFormat.format(newBalance)),
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showLoadCardInfoSuccess(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Đọc thông tin thẻ thành công!",
                "Thành công",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ========== FAILURE MESSAGES ==========

    public static void showCardIssueFailure(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Phát hành thẻ thất bại!\n\nVui lòng thử lại hoặc liên hệ quản trị viên.",
                "Thất bại",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void showChangePinFailure(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Đổi PIN thất bại!\n\nVui lòng kiểm tra PIN cũ và thử lại.",
                "Thất bại",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void showResetPinFailure(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Reset PIN thất bại!\n\nVui lòng thử lại hoặc liên hệ quản trị viên.",
                "Thất bại",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void showTransactionFailure(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Giao dịch thất bại!\n\nVui lòng thử lại.",
                "Thất bại",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void showLoadCardInfoFailure(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "Đọc thông tin thẻ thất bại!\n\nVui lòng thử lại.",
                "Thất bại",
                JOptionPane.ERROR_MESSAGE);
    }

    // ========== VALIDATION & WARNINGS ==========

    /**
     * Validation errors - giữ lại vì cần hướng dẫn người dùng cụ thể
     */
    public static void showValidationError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent,
                message,
                "Lỗi nhập liệu",
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Generic error
     */
    public static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent,
                message,
                "Lỗi",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Generic warning
     */
    public static void showWarning(Component parent, String message) {
        JOptionPane.showMessageDialog(parent,
                message,
                "Cảnh báo",
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Generic info
     */
    public static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent,
                message,
                "Thông báo",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
