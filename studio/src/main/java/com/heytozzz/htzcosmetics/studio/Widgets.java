package com.heytozzz.htzcosmetics.studio;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/** Flat, dark widgets in the style of the in-game wardrobe (chips, panels, buttons). */
final class Widgets {
    private Widgets() {
    }

    enum Style { NORMAL, PRIMARY, SUCCESS, DANGER, GHOST }

    /** A flat button. PRIMARY is yellow like the wardrobe accent; DANGER is red. */
    static final class FlatButton extends JButton {
        private final Style style;

        FlatButton(String text, Style style) {
            super(text);
            this.style = style;
            setFont(style == Style.GHOST ? Theme.FONT : Theme.FONT_BOLD);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.smooth(g);
            boolean hover = getModel().isRollover() && isEnabled();
            boolean down = getModel().isArmed() && getModel().isPressed();
            Color bg;
            Color fg;
            Color edge;
            switch (style) {
                case PRIMARY -> {
                    bg = hover ? new Color(0xF7D873) : Theme.ACCENT;
                    fg = new Color(0x1A1A1A);
                    edge = new Color(0xB8952F);
                }
                case SUCCESS -> {
                    bg = hover ? new Color(0x3C8547) : Theme.OK_DARK;
                    fg = new Color(0xD6F5DA);
                    edge = new Color(0x4FA75B);
                }
                case DANGER -> {
                    bg = hover ? new Color(0x8A3A3A) : Theme.ERROR_DARK;
                    fg = new Color(0xFFDADA);
                    edge = new Color(0xA04848);
                }
                case GHOST -> {
                    bg = hover ? Theme.CARD_HOVER : new Color(0, 0, 0, 0);
                    fg = hover ? Theme.TEXT : Theme.TEXT_DIM;
                    edge = hover ? Theme.PANEL_EDGE : new Color(0, 0, 0, 0);
                }
                default -> {
                    bg = hover ? Theme.CHIP_ON : Theme.CHIP;
                    fg = Theme.TEXT;
                    edge = Theme.PANEL_EDGE;
                }
            }
            if (!isEnabled()) {
                bg = Theme.CHIP;
                fg = Theme.TEXT_FAINT;
                edge = Theme.PANEL_EDGE;
            }
            int w = getWidth();
            int h = getHeight();
            g2.setColor(bg);
            g2.fillRect(0, 0, w, h);
            g2.setColor(edge);
            g2.drawRect(0, 0, w - 1, h - 1);
            // Minecraft-like bevel: darker bottom edge, lifted when not pressed.
            if (style != Style.GHOST && isEnabled()) {
                g2.setColor(new Color(0, 0, 0, down ? 0 : 70));
                g2.fillRect(1, h - 3, w - 2, 2);
            }
            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            String text = getText();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() + (down ? 1 : 0) - 1;
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    /** A toggle chip like the rarity chips of the wardrobe: coloured text and a coloured underline when on. */
    static final class Chip extends JButton {
        private final Color color;
        private boolean on;

        Chip(String text, Color color) {
            super(text);
            this.color = color;
            setFont(Theme.FONT);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(4, 9, 5, 9));
        }

        void setOn(boolean on) {
            this.on = on;
            repaint();
        }

        boolean isOn() {
            return on;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.smooth(g);
            boolean hover = getModel().isRollover() && isEnabled();
            g2.setColor(on ? Theme.CHIP_ON : hover ? Theme.CARD_HOVER : Theme.CHIP);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (on) {
                g2.setColor(color);
                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
            }
            g2.setFont(getFont());
            g2.setColor(isEnabled() ? color : Theme.TEXT_FAINT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                (getHeight() - fm.getHeight()) / 2 + fm.getAscent() - 1);
            g2.dispose();
        }
    }

    /** A filled panel with the wardrobe's panel colour and edge. */
    static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(Theme.PANEL);
        p.setBorder(Theme.panelBorder());
        return p;
    }

    static JPanel clear(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    static JTextField textField(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(Theme.FONT);
        f.setBackground(Theme.THUMB_BG);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.TEXT);
        f.setSelectionColor(Theme.CHIP_ON);
        f.setSelectedTextColor(Theme.TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.PANEL_EDGE),
            BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        return f;
    }

    static void markField(JTextField f, boolean ok) {
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ok ? Theme.PANEL_EDGE : Theme.ERROR),
            BorderFactory.createEmptyBorder(5, 7, 5, 7)));
    }

    static Dimension size(int w, int h) {
        return new Dimension(w, h);
    }
}
