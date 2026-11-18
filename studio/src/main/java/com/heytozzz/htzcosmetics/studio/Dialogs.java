package com.heytozzz.htzcosmetics.studio;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;

/** Dark, wardrobe-style message and confirmation dialogs. Every risky action goes through {@link #confirm}. */
final class Dialogs {
    enum Kind { INFO, OK, WARNING, DANGER }

    private Dialogs() {
    }

    static void message(Component parent, String title, String html, Kind kind) {
        show(parent, title, html, kind, null, "Entendido", null);
    }

    /** @return true if the user pressed the confirm button */
    static boolean confirm(Component parent, String title, String html, Kind kind, String confirmText) {
        return show(parent, title, html, kind, confirmText, "Cancelar", null);
    }

    /** Strong confirmation: the user must also type {@code word} before the button is enabled. */
    static boolean confirmTyping(Component parent, String title, String html, String confirmText, String word) {
        return show(parent, title, html, Kind.DANGER, confirmText, "Cancelar", word);
    }

    static String list(List<String> items) {
        StringBuilder sb = new StringBuilder("<ul style='margin-top:2px;margin-bottom:4px;margin-left:16px'>");
        for (String s : items) sb.append("<li>").append(esc(s)).append("</li>");
        return sb.append("</ul>").toString();
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static boolean show(Component parent, String title, String html, Kind kind, String confirmText,
                                String cancelText, String typeWord) {
        Window owner = parent instanceof Window w ? w : parent == null ? null
            : javax.swing.SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        boolean[] result = {false};
        Color stripe = switch (kind) {
            case OK -> Theme.OK;
            case WARNING -> Theme.WARN;
            case DANGER -> Theme.ERROR;
            default -> Theme.ACCENT;
        };

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(kind == Kind.DANGER ? Theme.DANGER_BG : Theme.PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, stripe),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)));

        String icon = switch (kind) {
            case OK -> "✔  ";
            case WARNING -> "⚠  ";
            case DANGER -> "⚠  ";
            default -> "";
        };
        JLabel head = Theme.label(icon + title, Theme.FONT_TITLE, kind == Kind.INFO ? Theme.TEXT : stripe);
        root.add(head, BorderLayout.NORTH);

        JEditorPane body = new JEditorPane("text/html", "<html><body style='font-family:" + Theme.FAMILY + ";font-size:11pt;"
            + "color:#E6E8EE'>" + html + "</body></html>");
        body.setEditable(false);
        body.setOpaque(false);
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        body.setBorder(BorderFactory.createEmptyBorder());
        body.setSize(new Dimension(500, Short.MAX_VALUE));
        JScrollPane sp = Theme.scroll(body);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        int h = Math.min(380, body.getPreferredSize().height + 6);
        sp.setPreferredSize(new Dimension(520, h));
        JPanel center = Widgets.clear(new BorderLayout(0, 8));
        center.add(sp, BorderLayout.CENTER);

        JPanel buttons = Widgets.clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        Widgets.FlatButton cancel = new Widgets.FlatButton(cancelText, Widgets.Style.NORMAL);
        cancel.addActionListener(e -> d.dispose());
        Widgets.FlatButton ok = null;
        if (confirmText != null) {
            ok = new Widgets.FlatButton(confirmText, kind == Kind.DANGER ? Widgets.Style.DANGER
                : kind == Kind.WARNING ? Widgets.Style.PRIMARY : Widgets.Style.SUCCESS);
            Widgets.FlatButton okButton = ok;
            ok.addActionListener(e -> {
                result[0] = true;
                d.dispose();
            });
            if (typeWord != null) {
                ok.setEnabled(false);
                JTextField field = Widgets.textField(12);
                JPanel typeRow = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 6, 0));
                typeRow.add(Theme.label("Para continuar escribe " + typeWord + ":", Theme.FONT_BOLD, Theme.ERROR));
                typeRow.add(field);
                center.add(typeRow, BorderLayout.SOUTH);
                field.getDocument().addDocumentListener(new DocumentListener() {
                    private void update() {
                        okButton.setEnabled(field.getText().strip().equals(typeWord));
                    }

                    public void insertUpdate(DocumentEvent e) {
                        update();
                    }

                    public void removeUpdate(DocumentEvent e) {
                        update();
                    }

                    public void changedUpdate(DocumentEvent e) {
                        update();
                    }
                });
            }
        }
        // Safe choice first (left), the action on the right. Enter never confirms a risky action by itself.
        buttons.add(cancel);
        if (ok != null) buttons.add(ok);
        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(root);
        JRootPane rp = d.getRootPane();
        rp.registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (confirmText == null) rp.setDefaultButton(cancel);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }
}
