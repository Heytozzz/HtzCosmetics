package com.heytozzz.htzcosmetics.studio;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** The card grid of the wardrobe: thumbnail, rarity line, name, rarity, badges. */
final class CardGrid extends JPanel implements Scrollable {
    static final int CARD_W = 158;
    static final int CARD_H = CARD_W + 52;
    static final int GAP = 8;
    private String emptyText = "";

    CardGrid() {
        setBackground(Theme.PANEL);
        setLayout(new GridFlow());
    }

    void setEmptyText(String text) {
        this.emptyText = text;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getComponentCount() == 0 && !emptyText.isEmpty()) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setFont(Theme.FONT);
            g2.setColor(Theme.TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            int y = 80;
            for (String line : emptyText.split("\n")) {
                g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
                y += fm.getHeight() + 2;
            }
            g2.dispose();
        }
    }

    // Scrollable: follow the viewport width, scroll vertically.
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
        return 24;
    }

    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return CARD_H;
    }

    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }

    /** Lays the cards out left-to-right in as many columns as fit. */
    private static final class GridFlow implements LayoutManager {
        public void addLayoutComponent(String name, Component c) {
        }

        public void removeLayoutComponent(Component c) {
        }

        private int cols(Container p) {
            return Math.max(1, (Math.max(p.getWidth(), CARD_W + 2 * GAP) - GAP) / (CARD_W + GAP));
        }

        public Dimension preferredLayoutSize(Container p) {
            int cols = cols(p);
            int rows = (p.getComponentCount() + cols - 1) / cols;
            return new Dimension(cols * (CARD_W + GAP) + GAP, rows * (CARD_H + GAP) + GAP);
        }

        public Dimension minimumLayoutSize(Container p) {
            return new Dimension(CARD_W + 2 * GAP, CARD_H + 2 * GAP);
        }

        public void layoutContainer(Container p) {
            int cols = cols(p);
            for (int i = 0; i < p.getComponentCount(); i++) {
                p.getComponent(i).setBounds(GAP + (i % cols) * (CARD_W + GAP), GAP + (i / cols) * (CARD_H + GAP),
                    CARD_W, CARD_H);
            }
        }
    }

    /** One cosmetic card. Click selects, double-click edits. */
    static final class Card extends JComponent {
        final CosmeticStore.Entry entry;
        private final BufferedImage thumb;
        private boolean hover;
        private boolean selected;
        private boolean hasProblem;

        Card(CosmeticStore.Entry entry, BufferedImage thumb, Consumer<Card> onSelect, Consumer<Card> onEdit) {
            this.entry = entry;
            this.thumb = thumb;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(entry.id());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    onSelect.accept(Card.this);
                    if (e.getClickCount() == 2) onEdit.accept(Card.this);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setSelected(boolean s) {
            selected = s;
            repaint();
        }

        void setProblem(boolean p) {
            hasProblem = p;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.smooth(g);
            int w = getWidth(), h = getHeight();
            CosmeticStore.Info info = entry.info();
            Color rc = info != null ? Theme.rarityColor(info.rarity()) : Theme.TEXT_FAINT;
            g2.setColor(hover ? Theme.CARD_HOVER : Theme.CARD);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.THUMB_BG);
            g2.fillRect(1, 1, w - 2, w - 1);
            g2.setColor(rc);
            g2.fillRect(0, w, w, 2);
            if (thumb != null) g2.drawImage(thumb, 4, 4, w - 8, w - 8, null);
            else {
                g2.setColor(Theme.ERROR);
                g2.setFont(Theme.FONT_BOLD);
                String t = "Sin vista previa";
                g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, w / 2);
            }

            g2.setFont(Theme.FONT_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(clip(entry.displayName(), fm, w - 12), 7, w + 19);
            g2.setFont(Theme.FONT_SMALL);
            if (info != null) {
                g2.setColor(rc);
                String r = Theme.rarityName(info.rarity());
                g2.drawString(r, 7, w + 37);
                g2.setColor(Theme.TEXT_FAINT);
                String c = Theme.categoryName(info.category());
                g2.drawString(c, w - 7 - g2.getFontMetrics().stringWidth(c), w + 37);
            }

            if (entry.animated()) badge(g2, 4, 4, "▶ Animado", Theme.ACCENT, Theme.CHIP_ON);
            if (hasProblem || entry.problem() != null) {
                String t = "! Error";
                int bw = g2.getFontMetrics(Theme.FONT_SMALL.deriveFont(java.awt.Font.BOLD)).stringWidth(t) + 10;
                badge(g2, w - 4 - bw, 4, t, new Color(0xFFDADA), Theme.ERROR_DARK);
            }
            if (selected || hasProblem || entry.problem() != null) {
                g2.setColor(selected ? Theme.ACCENT : Theme.ERROR);
                g2.setStroke(new BasicStroke(selected ? 2f : 1f));
                g2.drawRect(selected ? 1 : 0, selected ? 1 : 0, w - (selected ? 3 : 1), h - (selected ? 3 : 1));
            }
            g2.dispose();
        }

        private static void badge(Graphics2D g2, int x, int y, String text, Color fg, Color bg) {
            g2.setFont(Theme.FONT_SMALL.deriveFont(java.awt.Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(text) + 10;
            g2.setColor(bg);
            g2.fillRect(x, y, bw, 18);
            g2.setColor(fg);
            g2.drawString(text, x + 5, y + 13);
        }

        private static String clip(String s, FontMetrics fm, int max) {
            if (fm.stringWidth(s) <= max) return s;
            while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
    }
}
