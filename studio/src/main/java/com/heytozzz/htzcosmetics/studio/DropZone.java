package com.heytozzz.htzcosmetics.studio;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FileDialog;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A box you can drop a file on, or click to pick one with the normal Windows file dialog. */
final class DropZone extends JComponent {
    private final String what;
    private final String extension;
    private final Consumer<Path> onFile;
    private String current;
    private boolean hover;
    private boolean dragging;

    DropZone(String what, String extension, Consumer<Path> onFile) {
        this.what = what;
        this.extension = extension;
        this.onFile = onFile;
        setPreferredSize(Widgets.size(240, 64));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                choose();
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
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                boolean ok = s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                dragging = ok;
                repaint();
                return ok;
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                dragging = false;
                repaint();
                try {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        if (f.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
                            onFile.accept(f.toPath());
                            return true;
                        }
                    }
                    Dialogs.message(DropZone.this, "Archivo no válido",
                        "Aquí solo se puede soltar un archivo <b>" + extension + "</b>.", Dialogs.Kind.WARNING);
                } catch (Exception ignored) {
                    // unsupported drop: nothing to do
                }
                return false;
            }
        });
    }

    /** Text shown when a file is set (e.g. its name), or null for the empty state. */
    void setCurrent(String text) {
        this.current = text;
        repaint();
    }

    void choose() {
        Window w = javax.swing.SwingUtilities.getWindowAncestor(this);
        FileDialog fd = w instanceof java.awt.Dialog dlg ? new FileDialog(dlg, "Elegir " + what, FileDialog.LOAD)
            : new FileDialog((Frame) w, "Elegir " + what, FileDialog.LOAD);
        fd.setFile("*" + extension); // works as a filter in the Windows dialog
        fd.setVisible(true);
        if (fd.getFile() != null) {
            File f = new File(fd.getDirectory(), fd.getFile());
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(extension) && f.isFile()) onFile.accept(f.toPath());
            else Dialogs.message(this, "Archivo no válido", "Elige un archivo <b>" + extension + "</b>.", Dialogs.Kind.WARNING);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth(), h = getHeight();
        g2.setColor(dragging ? Theme.CHIP_ON : hover ? Theme.CARD_HOVER : Theme.THUMB_BG);
        g2.fillRect(0, 0, w, h);
        Color edge = current != null ? Theme.OK : dragging || hover ? Theme.ACCENT : Theme.TEXT_FAINT;
        g2.setColor(edge);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6, 4}, 0));
        g2.drawRect(1, 1, w - 3, h - 3);
        g2.setFont(Theme.FONT_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        String line1 = current != null ? "✔ " + current : "Arrastra aquí " + what;
        String line2 = current != null ? "Haz clic o arrastra otro para cambiarlo" : "o haz clic para elegirlo (" + extension + ")";
        g2.setColor(current != null ? Theme.TEXT : Theme.TEXT_DIM);
        g2.drawString(clip(line1, fm, w - 20), 12, h / 2 - 3);
        g2.setFont(Theme.FONT_SMALL);
        g2.setColor(Theme.TEXT_FAINT);
        g2.drawString(clip(line2, g2.getFontMetrics(), w - 20), 12, h / 2 + 15);
        g2.dispose();
    }

    private static String clip(String s, FontMetrics fm, int max) {
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
