package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * SmartCardVisual - 3D Smart Card Mockup Component
 * Hiển thị thẻ thông minh với thiết kế premium, hiệu ứng 3D và glassmorphism
 */
public class SmartCardVisual extends JPanel {

    // Card dimensions (credit card ratio 85.6 × 53.98 mm → ~1.586:1)
    private static final int CARD_WIDTH = 380;
    private static final int CARD_HEIGHT = 240;

    // Card data
    private String cardHolderName = "";
    private String patientId = "";
    private String cardNumber = "**** **** **** ****";
    private String expiryDate = "--/--";
    private String bhytCode = "";
    private long balance = 0;
    private boolean isFlipped = false;

    // Colors for card gradient
    private Color gradientStart;
    private Color gradientEnd;
    private Color accentColor;

    // Animation
    private float rotationAngle = 0;
    private Timer rotationTimer;

    // Currency formatter
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    public SmartCardVisual() {
        this(CardType.USER);
    }

    public SmartCardVisual(CardType type) {
        setOpaque(false);
        setPreferredSize(new Dimension(CARD_WIDTH + 40, CARD_HEIGHT + 60));

        if (type == CardType.ADMIN) {
            gradientStart = new Color(79, 70, 229); // Indigo
            gradientEnd = new Color(147, 51, 234); // Purple
            accentColor = new Color(199, 210, 254); // Light indigo
        } else {
            gradientStart = new Color(16, 185, 129); // Emerald
            gradientEnd = new Color(6, 182, 212); // Cyan
            accentColor = new Color(167, 243, 208); // Light emerald
        }

        setupHoverAnimation();
    }

    public enum CardType {
        USER, ADMIN
    }

    private void setupHoverAnimation() {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                startRotationAnimation(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                startRotationAnimation(false);
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                isFlipped = !isFlipped;
                repaint();
            }
        });
    }

    private void startRotationAnimation(boolean hover) {
        if (rotationTimer != null) {
            rotationTimer.stop();
        }

        float targetAngle = hover ? 5f : 0f;
        rotationTimer = new Timer(16, e -> {
            float diff = targetAngle - rotationAngle;
            if (Math.abs(diff) < 0.5f) {
                rotationAngle = targetAngle;
                ((Timer) e.getSource()).stop();
            } else {
                rotationAngle += diff * 0.15f;
            }
            repaint();
        });
        rotationTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        // Enable high quality rendering
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        // Apply 3D perspective transform
        AffineTransform original = g2.getTransform();
        g2.translate(centerX, centerY);

        // Subtle 3D rotation effect
        double skewX = Math.toRadians(rotationAngle * 0.3);
        double skewY = Math.toRadians(rotationAngle * 0.5);
        g2.shear(skewX, skewY);

        g2.translate(-CARD_WIDTH / 2, -CARD_HEIGHT / 2);

        if (!isFlipped) {
            drawCardFront(g2);
        } else {
            drawCardBack(g2);
        }

        g2.setTransform(original);
        g2.dispose();
    }

    private void drawCardFront(Graphics2D g2) {
        // ===== SHADOW =====
        for (int i = 5; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 8 + i * 4));
            g2.fill(new RoundRectangle2D.Float(
                    i * 2, i * 3 + 5,
                    CARD_WIDTH, CARD_HEIGHT,
                    24, 24));
        }

        // ===== CARD BACKGROUND =====
        GradientPaint cardGradient = new GradientPaint(
                0, 0, gradientStart,
                CARD_WIDTH, CARD_HEIGHT, gradientEnd);
        g2.setPaint(cardGradient);
        g2.fill(new RoundRectangle2D.Float(0, 0, CARD_WIDTH, CARD_HEIGHT, 24, 24));

        // ===== DECORATIVE PATTERNS =====
        // Large circle pattern (glassmorphism effect)
        g2.setColor(new Color(255, 255, 255, 20));
        g2.fill(new Ellipse2D.Float(CARD_WIDTH - 150, -50, 250, 250));

        g2.setColor(new Color(255, 255, 255, 15));
        g2.fill(new Ellipse2D.Float(-80, CARD_HEIGHT - 100, 200, 200));

        // Wave pattern
        g2.setColor(new Color(255, 255, 255, 10));
        g2.setStroke(new BasicStroke(40));
        g2.draw(new Arc2D.Float(-100, 80, 300, 200, 0, 180, Arc2D.OPEN));

        // ===== CHIP (Smart Card Chip) =====
        drawChip(g2, 30, 85);

        // ===== CONTACTLESS ICON =====
        drawContactlessIcon(g2, 95, 90);

        // ===== HOSPITAL LOGO =====
        drawHospitalLogo(g2, CARD_WIDTH - 70, 25);

        // ===== CARD TYPE LABEL =====
        g2.setColor(new Color(255, 255, 255, 180));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g2.drawString("SMART HEALTH CARD", 30, 35);

        // ===== BALANCE DISPLAY =====
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI Light", Font.PLAIN, 11));
        g2.drawString("SỐ DƯ", 30, 155);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
        String balanceStr = currencyFormat.format(balance);
        g2.drawString(balanceStr, 30, 182);

        // ===== CARD HOLDER NAME =====
        g2.setFont(new Font("Segoe UI Light", Font.PLAIN, 10));
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString("CHỦ THẺ", 30, 205);

        g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        g2.setColor(Color.WHITE);
        String displayName = cardHolderName.isEmpty() ? "---" : cardHolderName.toUpperCase();
        g2.drawString(displayName, 30, 224);

        // ===== PATIENT ID =====
        g2.setFont(new Font("Segoe UI Light", Font.PLAIN, 10));
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawString("MÃ BỆNH NHÂN", 200, 205);

        g2.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        g2.setColor(Color.WHITE);
        String displayId = patientId.isEmpty() ? "---" : patientId;
        g2.drawString(displayId, 200, 224);

        // ===== BHYT CODE (if exists) =====
        if (!bhytCode.isEmpty()) {
            g2.setFont(new Font("Consolas", Font.PLAIN, 11));
            g2.setColor(new Color(255, 255, 255, 150));
            g2.drawString("BHYT: " + bhytCode, CARD_WIDTH - 150, 224);
        }

        // ===== SHINE EFFECT =====
        GradientPaint shine = new GradientPaint(
                0, 0, new Color(255, 255, 255, 30),
                CARD_WIDTH / 2, CARD_HEIGHT / 2, new Color(255, 255, 255, 0));
        g2.setPaint(shine);
        g2.fill(new RoundRectangle2D.Float(0, 0, CARD_WIDTH, CARD_HEIGHT / 2, 24, 24));
    }

    private void drawCardBack(Graphics2D g2) {
        // Shadow
        for (int i = 5; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 8 + i * 4));
            g2.fill(new RoundRectangle2D.Float(
                    i * 2, i * 3 + 5,
                    CARD_WIDTH, CARD_HEIGHT,
                    24, 24));
        }

        // Card background (darker)
        g2.setColor(ModernUITheme.darken(gradientStart, 0.3f));
        g2.fill(new RoundRectangle2D.Float(0, 0, CARD_WIDTH, CARD_HEIGHT, 24, 24));

        // Magnetic stripe
        g2.setColor(new Color(30, 30, 30));
        g2.fillRect(0, 35, CARD_WIDTH, 45);

        // Signature strip
        g2.setColor(new Color(230, 230, 230));
        g2.fillRect(30, 100, CARD_WIDTH - 100, 40);

        g2.setColor(new Color(100, 100, 100));
        g2.setFont(new Font("Script", Font.ITALIC, 16));
        String sig = cardHolderName.isEmpty() ? "" : cardHolderName;
        g2.drawString(sig, 45, 127);

        // CVV box
        g2.setColor(Color.WHITE);
        g2.fillRect(CARD_WIDTH - 60, 100, 45, 40);

        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Consolas", Font.BOLD, 14));
        g2.drawString("***", CARD_WIDTH - 50, 127);

        // Instructions
        g2.setColor(new Color(200, 200, 200));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        g2.drawString("Click để xem mặt trước", 30, 180);
        g2.drawString("Thẻ này là tài sản của Bệnh viện", 30, 195);
        g2.drawString("Nếu tìm thấy vui lòng liên hệ: 1900-xxxx", 30, 210);
    }

    private void drawChip(Graphics2D g2, int x, int y) {
        // Chip body
        GradientPaint chipGradient = new GradientPaint(
                x, y, new Color(218, 165, 32),
                x + 50, y + 40, new Color(255, 215, 0));
        g2.setPaint(chipGradient);
        g2.fill(new RoundRectangle2D.Float(x, y, 50, 38, 6, 6));

        // Chip lines
        g2.setColor(new Color(180, 140, 30));
        g2.setStroke(new BasicStroke(1));

        // Horizontal lines
        g2.drawLine(x + 5, y + 12, x + 45, y + 12);
        g2.drawLine(x + 5, y + 26, x + 45, y + 26);

        // Vertical lines
        g2.drawLine(x + 17, y + 5, x + 17, y + 33);
        g2.drawLine(x + 33, y + 5, x + 33, y + 33);

        // Center square
        g2.fillRect(x + 20, y + 14, 10, 10);
    }

    private void drawContactlessIcon(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Radio waves
        for (int i = 0; i < 4; i++) {
            int arcSize = 10 + i * 8;
            g2.draw(new Arc2D.Float(x - arcSize / 2, y - arcSize / 2, arcSize, arcSize, -45, 90, Arc2D.OPEN));
        }
    }

    private void drawHospitalLogo(Graphics2D g2, int x, int y) {
        // Circle background
        g2.setColor(new Color(255, 255, 255, 40));
        g2.fill(new Ellipse2D.Float(x, y, 40, 40));

        // Cross
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x + 20, y + 10, x + 20, y + 30);
        g2.drawLine(x + 10, y + 20, x + 30, y + 20);
    }

    // ===== SETTERS =====

    public void setCardHolderName(String name) {
        this.cardHolderName = name != null ? name : "";
        repaint();
    }

    public void setPatientId(String id) {
        this.patientId = id != null ? id : "";
        repaint();
    }

    public void setBalance(long balance) {
        this.balance = balance;
        repaint();
    }

    public void setBhytCode(String code) {
        this.bhytCode = code != null ? code : "";
        repaint();
    }

    public void setCardNumber(String number) {
        this.cardNumber = number != null ? number : "";
        repaint();
    }

    public void setExpiryDate(String date) {
        this.expiryDate = date != null ? date : "";
        repaint();
    }

    /**
     * Helper method to create a complete card info panel with the 3D card
     */
    public static JPanel createCardInfoSection(SmartCardVisual cardVisual) {
        JPanel section = new JPanel(new BorderLayout(20, 0));
        section.setOpaque(false);

        // Card on the left
        JPanel cardWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        cardWrapper.setOpaque(false);
        cardWrapper.add(cardVisual);
        section.add(cardWrapper, BorderLayout.WEST);

        // Info panel on the right
        ModernUITheme.CardPanel infoCard = new ModernUITheme.CardPanel();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setPreferredSize(new Dimension(300, 240));

        JLabel tipLabel = new JLabel("<html><b>💡 Mẹo:</b><br>" +
                "• Di chuột vào thẻ để xem hiệu ứng 3D<br>" +
                "• Click vào thẻ để xem mặt sau<br>" +
                "• Thẻ hiển thị thông tin trực tiếp từ chip</html>");
        tipLabel.setFont(ModernUITheme.FONT_SMALL);
        tipLabel.setForeground(ModernUITheme.TEXT_SECONDARY);
        infoCard.add(tipLabel);

        section.add(infoCard, BorderLayout.CENTER);

        return section;
    }
}
