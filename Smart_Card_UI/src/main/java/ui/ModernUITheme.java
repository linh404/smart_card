package ui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

/**
 * ModernUITheme - Modern Light Theme cho ứng dụng quản lý thẻ bệnh viện
 * Cung cấp các component tùy chỉnh với hiệu ứng mượt mà
 */
public class ModernUITheme {

    // ==================== COLOR PALETTE ====================

    // Background colors
    public static final Color BG_PRIMARY = new Color(248, 250, 252); // Light gray-blue
    public static final Color BG_SECONDARY = new Color(241, 245, 249); // Slightly darker
    public static final Color BG_CARD = Color.WHITE; // Card background
    public static final Color BG_HEADER = new Color(255, 255, 255); // Header background

    // Admin accent colors (Blue/Indigo theme)
    public static final Color ADMIN_PRIMARY = new Color(79, 70, 229); // Indigo-600
    public static final Color ADMIN_PRIMARY_HOVER = new Color(99, 102, 241); // Indigo-500
    public static final Color ADMIN_PRIMARY_LIGHT = new Color(238, 242, 255); // Indigo-50
    public static final Color ADMIN_GRADIENT_START = new Color(79, 70, 229);
    public static final Color ADMIN_GRADIENT_END = new Color(147, 51, 234); // Purple-600

    // User accent colors (Teal/Emerald theme)
    public static final Color USER_PRIMARY = new Color(16, 185, 129); // Emerald-500
    public static final Color USER_PRIMARY_HOVER = new Color(52, 211, 153); // Emerald-400
    public static final Color USER_PRIMARY_LIGHT = new Color(236, 253, 245); // Emerald-50
    public static final Color USER_GRADIENT_START = new Color(16, 185, 129);
    public static final Color USER_GRADIENT_END = new Color(20, 184, 166); // Teal-500

    // Text colors
    public static final Color TEXT_PRIMARY = new Color(15, 23, 42); // Slate-900
    public static final Color TEXT_SECONDARY = new Color(100, 116, 139); // Slate-500
    public static final Color TEXT_MUTED = new Color(148, 163, 184); // Slate-400
    public static final Color TEXT_WHITE = Color.WHITE;

    // Border & Shadow colors
    public static final Color BORDER_LIGHT = new Color(226, 232, 240); // Slate-200
    public static final Color BORDER_FOCUS = new Color(99, 102, 241); // Indigo-500
    public static final Color SHADOW_COLOR = new Color(0, 0, 0, 25); // Subtle shadow

    // Status colors
    public static final Color SUCCESS = new Color(34, 197, 94); // Green-500
    public static final Color WARNING = new Color(245, 158, 11); // Amber-500
    public static final Color ERROR = new Color(239, 68, 68); // Red-500
    public static final Color INFO = new Color(59, 130, 246); // Blue-500

    // ==================== TYPOGRAPHY ====================

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBHEADING = new Font("Segoe UI Semibold", Font.PLAIN, 14);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BUTTON = new Font("Segoe UI Semibold", Font.PLAIN, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);

    // ==================== DIMENSIONS ====================

    public static final int BORDER_RADIUS = 12;
    public static final int BORDER_RADIUS_SMALL = 8;
    public static final int PADDING = 16;
    public static final int PADDING_SMALL = 8;

    // ==================== SETUP METHODS ====================

    /**
     * Áp dụng theme cho toàn bộ ứng dụng
     */
    public static void applyTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fallback to default
        }

        // Global UI defaults
        UIManager.put("Panel.background", BG_PRIMARY);
        UIManager.put("OptionPane.background", BG_CARD);
        UIManager.put("OptionPane.messageFont", FONT_BODY);
        UIManager.put("Button.font", FONT_BUTTON);
        UIManager.put("Label.font", FONT_BODY);
        UIManager.put("TextField.font", FONT_BODY);
        UIManager.put("PasswordField.font", FONT_BODY);
        UIManager.put("Table.font", FONT_BODY);
        UIManager.put("TableHeader.font", FONT_SUBHEADING);
        UIManager.put("TabbedPane.font", FONT_BUTTON);

        // Make tooltips look better
        UIManager.put("ToolTip.background", TEXT_PRIMARY);
        UIManager.put("ToolTip.foreground", TEXT_WHITE);
        UIManager.put("ToolTip.font", FONT_SMALL);
    }

    // ==================== CUSTOM COMPONENTS ====================

    /**
     * Modern rounded button với hover animation
     */
    public static class RoundedButton extends JButton {
        private Color normalColor;
        private Color hoverColor;
        private Color pressedColor;
        private Color textColor;
        private boolean isHovered = false;
        private boolean isPressed = false;
        private int radius = BORDER_RADIUS;

        public RoundedButton(String text) {
            this(text, ADMIN_PRIMARY, ADMIN_PRIMARY_HOVER, TEXT_WHITE);
        }

        public RoundedButton(String text, Color bgColor, Color hoverColor, Color textColor) {
            super(text);
            this.normalColor = bgColor;
            this.hoverColor = hoverColor;
            this.pressedColor = darken(bgColor, 0.15f);
            this.textColor = textColor;

            setFont(FONT_BUTTON);
            setForeground(textColor);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140, 42));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    isPressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    isPressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    isPressed = false;
                    repaint();
                }
            });
        }

        public void setRadius(int radius) {
            this.radius = radius;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bgColor = isPressed ? pressedColor : (isHovered ? hoverColor : normalColor);

            // Shadow (only when not pressed)
            if (!isPressed) {
                g2.setColor(new Color(0, 0, 0, 20));
                g2.fill(new RoundRectangle2D.Float(2, 3, getWidth() - 4, getHeight() - 4, radius, radius));
            }

            // Background
            g2.setColor(bgColor);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - (isPressed ? 1 : 3), radius,
                    radius));

            // Text
            g2.setColor(textColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = (getWidth() - fm.stringWidth(getText())) / 2;
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent() - (isPressed ? 0 : 1);
            g2.drawString(getText(), textX, textY);

            g2.dispose();
        }
    }

    /**
     * Secondary/outline button style
     */
    public static class OutlineButton extends JButton {
        private Color borderColor;
        private Color hoverBgColor;
        private Color textColor;
        private boolean isHovered = false;
        private int radius = BORDER_RADIUS;

        public OutlineButton(String text) {
            this(text, ADMIN_PRIMARY, ADMIN_PRIMARY_LIGHT, ADMIN_PRIMARY);
        }

        public OutlineButton(String text, Color borderColor, Color hoverBgColor, Color textColor) {
            super(text);
            this.borderColor = borderColor;
            this.hoverBgColor = hoverBgColor;
            this.textColor = textColor;

            setFont(FONT_BUTTON);
            setForeground(textColor);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140, 42));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g2.setColor(isHovered ? hoverBgColor : BG_CARD);
            g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, radius, radius));

            // Border
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, radius, radius));

            // Text
            g2.setColor(textColor);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = (getWidth() - fm.stringWidth(getText())) / 2;
            int textY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), textX, textY);

            g2.dispose();
        }
    }

    /**
     * Modern rounded text field
     */
    public static class RoundedTextField extends JTextField {
        private int radius = BORDER_RADIUS;
        private boolean isFocused = false;

        public RoundedTextField(int columns) {
            super(columns);
            setOpaque(false);
            setFont(FONT_BODY);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setBackground(BG_CARD);

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    isFocused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    isFocused = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));

            // Border
            g2.setColor(isFocused ? BORDER_FOCUS : BORDER_LIGHT);
            g2.setStroke(new BasicStroke(isFocused ? 2f : 1f));
            g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Modern rounded password field
     */
    public static class RoundedPasswordField extends JPasswordField {
        private int radius = BORDER_RADIUS;
        private boolean isFocused = false;

        public RoundedPasswordField(int columns) {
            super(columns);
            setOpaque(false);
            setFont(FONT_BODY);
            setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            setBackground(BG_CARD);

            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    isFocused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    isFocused = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));

            // Border
            g2.setColor(isFocused ? BORDER_FOCUS : BORDER_LIGHT);
            g2.setStroke(new BasicStroke(isFocused ? 2f : 1f));
            g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, radius, radius));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Card panel với shadow effect
     */
    public static class CardPanel extends JPanel {
        private int radius = BORDER_RADIUS;
        private int shadowSize = 4;

        public CardPanel() {
            setOpaque(false);
            setBackground(BG_CARD);
            setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        }

        public CardPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBackground(BG_CARD);
            setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Shadow
            for (int i = 0; i < shadowSize; i++) {
                float alpha = 0.05f * (shadowSize - i);
                g2.setColor(new Color(0, 0, 0, (int) (alpha * 255)));
                g2.fill(new RoundRectangle2D.Float(
                        i, i + 1,
                        getWidth() - i * 2 - 1,
                        getHeight() - i * 2 - 1,
                        radius, radius));
            }

            // Background
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - shadowSize, radius, radius));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Gradient header panel
     */
    public static class GradientHeader extends JPanel {
        private Color startColor;
        private Color endColor;

        public GradientHeader(Color startColor, Color endColor) {
            this.startColor = startColor;
            this.endColor = endColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            GradientPaint gp = new GradientPaint(0, 0, startColor, getWidth(), 0, endColor);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Modern styled TabbedPane
     */
    public static JTabbedPane createModernTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(FONT_BUTTON);
        tabbedPane.setBackground(BG_PRIMARY);
        tabbedPane.setForeground(TEXT_PRIMARY);
        tabbedPane.setBorder(BorderFactory.createEmptyBorder());

        tabbedPane.setUI(new BasicTabbedPaneUI() {
            private final Insets tabInsets = new Insets(12, 20, 12, 20);

            @Override
            protected void installDefaults() {
                super.installDefaults();
                tabAreaInsets = new Insets(0, 10, 0, 10);
                contentBorderInsets = new Insets(0, 0, 0, 0);
            }

            @Override
            protected Insets getTabInsets(int tabPlacement, int tabIndex) {
                return tabInsets;
            }

            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                    int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isSelected) {
                    g2.setColor(ADMIN_PRIMARY);
                    g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 8, 8));
                } else {
                    g2.setColor(BG_SECONDARY);
                    g2.fill(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, h - 2, 8, 8));
                }

                g2.dispose();
            }

            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                    int x, int y, int w, int h, boolean isSelected) {
                // No border
            }

            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                // No content border
            }

            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects,
                    int tabIndex, Rectangle iconRect, Rectangle textRect,
                    boolean isSelected) {
                // No focus indicator
            }

            @Override
            protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics,
                    int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setFont(font);
                g2.setColor(isSelected ? TEXT_WHITE : TEXT_SECONDARY);
                g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
                g2.dispose();
            }
        });

        return tabbedPane;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Làm tối màu
     */
    public static Color darken(Color color, float factor) {
        return new Color(
                Math.max((int) (color.getRed() * (1 - factor)), 0),
                Math.max((int) (color.getGreen() * (1 - factor)), 0),
                Math.max((int) (color.getBlue() * (1 - factor)), 0),
                color.getAlpha());
    }

    /**
     * Làm sáng màu
     */
    public static Color lighten(Color color, float factor) {
        return new Color(
                Math.min((int) (color.getRed() + (255 - color.getRed()) * factor), 255),
                Math.min((int) (color.getGreen() + (255 - color.getGreen()) * factor), 255),
                Math.min((int) (color.getBlue() + (255 - color.getBlue()) * factor), 255),
                color.getAlpha());
    }

    /**
     * Tạo status badge
     */
    public static JLabel createStatusBadge(String text, Color bgColor, Color textColor) {
        JLabel badge = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setForeground(textColor);
        badge.setFont(FONT_SMALL);
        badge.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        return badge;
    }

    /**
     * Tạo icon placeholder với chữ cái đầu
     */
    public static JPanel createAvatarIcon(String name, Color bgColor) {
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();

        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(bgColor);
                g2.fillOval(0, 0, getWidth(), getHeight());

                g2.setColor(TEXT_WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(initial)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initial, x, y);

                g2.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(36, 36));
        return avatar;
    }
}
