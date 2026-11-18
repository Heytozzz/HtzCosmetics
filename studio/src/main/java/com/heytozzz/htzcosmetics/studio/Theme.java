package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * Colours, fonts and small painting helpers. The palette is the one of the in-game wardrobe
 * ({@code CosmeticsScreen}), so the studio looks like the menu players see.
 */
final class Theme {
    static final Color BG = new Color(0x101217);
    static final Color PANEL = new Color(0x1C1F27);
    static final Color PANEL_EDGE = new Color(0x2B2F3A);
    static final Color CARD = new Color(0x222632);
    static final Color CARD_HOVER = new Color(0x2A2F3D);
    static final Color THUMB_BG = new Color(0x181B22);
    static final Color TEXT = new Color(0xE6E8EE);
    static final Color TEXT_DIM = new Color(0x9AA0AD);
    static final Color TEXT_FAINT = new Color(0x5F6573);
    static final Color ACCENT = new Color(0xF2C94C);
    static final Color OK = new Color(0x5FD068);
    static final Color OK_DARK = new Color(0x2F6B38);
    static final Color ERROR = new Color(0xF08A8A);
    static final Color ERROR_DARK = new Color(0x6B2F2F);
    static final Color WARN = new Color(0xF2A84C);
    static final Color CHIP = new Color(0x2A2E39);
    static final Color CHIP_ON = new Color(0x3A3F4D);
    static final Color DANGER_BG = new Color(0x2A1D21);
    static final Color DANGER_EDGE = new Color(0x5A2E35);

    /** "Segoe UI Symbol" has the normal letters plus the ✔ ✖ ▶ ⚠ symbols that plain Segoe UI lacks. */
    static final String FAMILY = java.util.Arrays.asList(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getAvailableFontFamilyNames()).contains("Segoe UI Symbol") ? "Segoe UI Symbol" : Font.DIALOG;
    static final Font FONT = new Font(FAMILY, Font.PLAIN, 13);
    static final Font FONT_SMALL = FONT.deriveFont(11.5f);
    static final Font FONT_BOLD = FONT.deriveFont(Font.BOLD);
    static final Font FONT_TITLE = FONT.deriveFont(Font.BOLD, 16f);
    static final Font FONT_BIG = FONT.deriveFont(Font.BOLD, 20f);
    static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);

    private Theme() {
    }

    static Color rarityColor(Rarity r) {
        return switch (r) {
            case COMMON -> new Color(0xD8DCE3);
            case UNCOMMON -> new Color(0x7FE07F);
            case RARE -> new Color(0x6FE3F2);
            case EPIC -> new Color(0xE58BF0);
            case LEGENDARY -> new Color(0xF2C94C);
        };
    }

    /** Same Spanish names as the mod's es_es.json. */
    static String rarityName(Rarity r) {
        return switch (r) {
            case COMMON -> "Común";
            case UNCOMMON -> "Poco común";
            case RARE -> "Raro";
            case EPIC -> "Épico";
            case LEGENDARY -> "Legendario";
        };
    }

    static String categoryName(Category c) {
        return switch (c) {
            case HEAD -> "Cabeza";
            case BACK -> "Espalda";
            case SHOULDER -> "Hombro";
            case WAIST -> "Cintura";
        };
    }

    static String anchorName(Anchor a) {
        return switch (a) {
            case HEAD -> "Cabeza";
            case BODY -> "Cuerpo";
            case LEFT_ARM -> "Brazo izquierdo";
            case RIGHT_ARM -> "Brazo derecho";
            case LEFT_LEG -> "Pierna izquierda";
            case RIGHT_LEG -> "Pierna derecha";
        };
    }

    static Border panelBorder() {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(PANEL_EDGE),
            BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    static JLabel header(String text) {
        JLabel l = label(text.toUpperCase(), FONT_SMALL.deriveFont(Font.BOLD), TEXT_FAINT);
        l.setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 2));
        return l;
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

    /** Dark scroll pane with a thin flat scrollbar. */
    static JScrollPane scroll(JComponent view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(view.getBackground());
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        javax.swing.JPanel corner = new javax.swing.JPanel();
        corner.setBackground(PANEL);
        sp.setCorner(JScrollPane.LOWER_RIGHT_CORNER, corner);
        sp.getVerticalScrollBar().setUI(new FlatScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new FlatScrollBarUI());
        sp.getVerticalScrollBar().setBackground(PANEL);
        sp.getHorizontalScrollBar().setBackground(PANEL);
        return sp;
    }

    private static final class FlatScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = CHIP_ON;
            trackColor = PANEL;
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int orientation) {
            return zero();
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int orientation) {
            return zero();
        }

        private static javax.swing.JButton zero() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new java.awt.Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            g.setColor(isDragging ? TEXT_FAINT : thumbColor);
            g.fillRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }
}
