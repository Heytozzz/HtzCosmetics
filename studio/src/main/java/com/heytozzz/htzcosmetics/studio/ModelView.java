package com.heytozzz.htzcosmetics.studio;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

/** Large 3D preview: turns slowly by itself; drag to rotate, mouse wheel to zoom. */
final class ModelView extends JComponent {
    private ModelRenderer.Mesh mesh;
    private BufferedImage texture;
    private String placeholder = "Sin vista previa";
    private double yaw = -30;
    private double pitch = 22;
    private double zoom = 1;
    private boolean spinning = true;
    private int lastX, lastY;
    private final Timer timer;

    ModelView() {
        setOpaque(true);
        setBackground(Theme.THUMB_BG);
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        setToolTipText("Arrastra para girar · rueda del ratón para acercar");
        timer = new Timer(33, e -> {
            if (spinning && mesh != null && isShowing()) {
                yaw = (yaw + 1.2) % 360;
                repaint();
            }
        });
        timer.start();
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                spinning = false;
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - lastX) * 0.8;
                pitch = Math.max(-80, Math.min(80, pitch + (e.getY() - lastY) * 0.6));
                lastX = e.getX();
                lastY = e.getY();
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    spinning = true;
                    zoom = 1;
                    pitch = 22;
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom = Math.max(0.4, Math.min(4, zoom * Math.pow(0.9, e.getPreciseWheelRotation())));
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    void show(CosmeticStore.Loaded loaded, String placeholderText) {
        this.mesh = loaded == null ? null : new ModelRenderer.Mesh(loaded.model());
        this.texture = loaded == null ? null : loaded.texture();
        this.placeholder = placeholderText;
        this.spinning = true;
        this.zoom = 1;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        // Soft floor grid, like a Blockbench viewport.
        g2.setColor(Theme.PANEL);
        for (int x = 0; x < getWidth(); x += 16) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += 16) g2.drawLine(0, y, getWidth(), y);
        if (mesh != null) {
            ModelRenderer.draw(g2, mesh, texture, 8, 8, getWidth() - 16, getHeight() - 16, yaw, pitch, zoom);
        } else {
            g2.setFont(Theme.FONT);
            g2.setColor(Theme.TEXT_FAINT);
            FontMetrics fm = g2.getFontMetrics();
            String[] lines = placeholder.split("\n");
            int y = (getHeight() - lines.length * fm.getHeight()) / 2 + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y);
                y += fm.getHeight();
            }
        }
        g2.dispose();
    }

    /** Renders a static thumbnail (3/4 view) into an image. */
    static BufferedImage thumbnail(CosmeticStore.Loaded loaded, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        if (loaded == null) return img;
        Graphics2D g = img.createGraphics();
        ModelRenderer.draw(g, new ModelRenderer.Mesh(loaded.model()), loaded.texture(), 6, 6, size - 12, size - 12,
            -30, 22, 1);
        g.dispose();
        return img;
    }
}
