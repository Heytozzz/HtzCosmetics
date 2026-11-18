package com.heytozzz.htzcosmetics.studio;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Live output of the running task (Gradle, npm...), coloured line by line. */
final class LogPanel extends JPanel {
    private static final int MAX_CHARS = 400_000;
    private final JTextPane text = new JTextPane();
    private final JLabel title = Theme.label("Registro", Theme.FONT_BOLD, Theme.TEXT);
    private final JLabel status = Theme.label("", Theme.FONT, Theme.TEXT_DIM);
    final Widgets.FlatButton stop = new Widgets.FlatButton("Detener", Widgets.Style.DANGER);
    private final Widgets.FlatButton clear = new Widgets.FlatButton("Limpiar", Widgets.Style.GHOST);
    private final Widgets.FlatButton toggle = new Widgets.FlatButton("Ocultar", Widgets.Style.GHOST);
    private final JScrollPane scroll;
    private final Timer spinner;
    private int dots;
    private String running;

    LogPanel() {
        super(new BorderLayout());
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.PANEL_EDGE));

        JPanel bar = Widgets.clear(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        JPanel left = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.add(title);
        left.add(status);
        JPanel right = Widgets.clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        stop.setVisible(false);
        stop.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        clear.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        toggle.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        right.add(stop);
        right.add(clear);
        right.add(toggle);
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        text.setEditable(false);
        text.setFont(Theme.FONT_MONO);
        text.setBackground(Theme.THUMB_BG);
        text.setForeground(Theme.TEXT_DIM);
        text.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        scroll = Theme.scroll(text);
        scroll.setPreferredSize(new Dimension(100, 190));
        add(scroll, BorderLayout.CENTER);

        clear.addActionListener(e -> text.setText(""));
        toggle.addActionListener(e -> setExpanded(!scroll.isVisible()));
        spinner = new Timer(400, e -> {
            dots = (dots + 1) % 4;
            status.setText(running == null ? "" : running + " " + ".".repeat(dots));
        });
        info("HtzCosmetics Studio listo. Aquí verás lo que hace cada acción.");
    }

    void setExpanded(boolean expanded) {
        scroll.setVisible(expanded);
        toggle.setText(expanded ? "Ocultar" : "Mostrar registro");
        revalidate();
    }

    void started(String what) {
        setExpanded(true);
        running = what;
        status.setForeground(Theme.ACCENT);
        stop.setVisible(true);
        spinner.start();
        info("► " + what);
    }

    void finished(boolean ok, String message) {
        running = null;
        spinner.stop();
        stop.setVisible(false);
        status.setText(message);
        status.setForeground(ok ? Theme.OK : Theme.ERROR);
        if (ok) ok("[OK] " + message);
        else error("[ERROR] " + message);
    }

    void line(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        Color c = Theme.TEXT_DIM;
        if (l.contains("error") || l.contains("failed") || l.contains("exception")) c = Theme.ERROR;
        else if (l.contains("warn")) c = Theme.WARN;
        else if (l.contains("build successful") || l.startsWith("ok:") || l.contains("published")
            || l.contains("site built")) c = Theme.OK;
        else if (l.startsWith("> task")) c = Theme.TEXT_FAINT;
        append(line, c, false);
    }

    void info(String s) {
        append(stamp() + s, Theme.ACCENT, true);
    }

    void ok(String s) {
        append(stamp() + s, Theme.OK, true);
    }

    void error(String s) {
        append(stamp() + s, Theme.ERROR, true);
    }

    /** Everything shown so far (used by the automated self-test). */
    String text() {
        return text.getText();
    }

    private static String stamp() {
        return "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ";
    }

    private void append(String s, Color color, boolean bold) {
        StyledDocument doc = text.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setBold(a, bold);
        try {
            doc.insertString(doc.getLength(), s + "\n", a);
            if (doc.getLength() > MAX_CHARS) doc.remove(0, doc.getLength() - MAX_CHARS * 3 / 4);
        } catch (BadLocationException ignored) {
            // cannot happen: offsets come from the document itself
        }
        text.setCaretPosition(doc.getLength());
    }
}
