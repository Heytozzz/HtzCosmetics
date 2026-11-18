package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.manifest.Manifest;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/**
 * "Mantenimiento / Avanzado": signature status and renewal, key rotation and backend commands. Kept apart from the
 * everyday screen, and every action asks for confirmation first.
 *
 * <p>Keys: only the folder path and whether {@code primary.key} / {@code backup.key} exist are shown. The files are
 * never opened, read or copied by the studio.
 */
final class MaintenancePanel extends JPanel {
    private final MainWindow w;
    private final JPanel signatureBody = column();
    private final JPanel keysBody = column();

    MaintenancePanel(MainWindow w) {
        super(new BorderLayout());
        this.w = w;
        setOpaque(false);

        JPanel content = column();
        content.setBackground(Theme.BG);
        content.setOpaque(true);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 6));

        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setBackground(Theme.DANGER_BG);
        banner.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.DANGER_EDGE),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JPanel bt = column();
        bt.add(Theme.label("⚠  Mantenimiento / Avanzado", Theme.FONT_BIG, Theme.ERROR));
        bt.add(Theme.label("Estas acciones afectan a todos los jugadores, a tus claves o al servidor. "
            + "Cada una te pedirá confirmación. Para el día a día no hace falta entrar aquí.", Theme.FONT, Theme.TEXT_DIM));
        banner.add(bt, BorderLayout.CENTER);
        Widgets.FlatButton back = new Widgets.FlatButton("← Volver a cosméticos", Widgets.Style.NORMAL);
        back.addActionListener(e -> w.showMaintenance(false));
        JPanel backWrap = Widgets.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        backWrap.add(back);
        banner.add(backWrap, BorderLayout.EAST);
        add(banner, BorderLayout.NORTH);

        // 1. Signature
        JPanel sig = section("Firma del manifiesto", Theme.ACCENT,
            "Los jugadores solo aceptan la lista de cosméticos si está firmada y no ha caducado. Cada publicación "
                + "firma para 180 días: renueva antes de que caduque, aunque no cambies nada.");
        sig.add(signatureBody);
        sig.add(Box.createVerticalStrut(10));
        sig.add(buttons(
            w.maintenanceAction("Renovar firma…", Widgets.Style.PRIMARY,
                "Vuelve a construir, firmar y publicar site/ (sin cambiar los cosméticos)", this::renew),
            w.maintenanceAction("Verificar site/", Widgets.Style.NORMAL,
                "Comprueba site/ exactamente como lo comprobaría el mod (htzVerifySite)", this::verifySite),
            ghost("Actualizar estado", w::refreshSignature)));
        content.add(sig);

        // 2. Keys
        JPanel keys = section("Claves de firma", Theme.ERROR,
            "Las claves privadas firman la lista de cosméticos. Están fuera del proyecto y nunca se suben a git. "
                + "Esta aplicación no las abre ni las copia: solo comprueba que existen.");
        keys.add(keysBody);
        keys.add(Box.createVerticalStrut(10));
        keys.add(buttons(w.maintenanceAction("Rotar claves…", Widgets.Style.DANGER,
            "Solo si crees que alguien ha conseguido primary.key (htzKeygen --rotate)", this::rotate)));
        content.add(keys);

        // 3. Backend
        JPanel backend = section("Backend (Cloudflare)", Theme.WARN,
            "El servidor que guarda qué cosméticos lleva equipados cada jugador. Comandos de la carpeta backend/.");
        backend.add(buttons(
            w.maintenanceAction("Ejecutar tests (npm test)", Widgets.Style.NORMAL,
                "Comprueba el backend en tu PC. No cambia nada en Internet.", this::backendTest),
            w.maintenanceAction("Desplegar backend…", Widgets.Style.DANGER,
                "Sube el backend a Cloudflare (npm run deploy)", this::backendDeploy),
            w.maintenanceAction("Actualizar claves de Mojang…", Widgets.Style.NORMAL,
                "Solo si los inicios de sesión fallan con invalid_certificate", this::mojangKeys)));
        content.add(backend);
        content.add(Box.createVerticalGlue());

        JPanel wrap = Widgets.clear(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        wrap.add(Theme.scroll(content), BorderLayout.CENTER);
        add(wrap, BorderLayout.CENTER);
        refresh();
    }

    private static JPanel column() {
        JPanel p = Widgets.clear(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JPanel section(String title, Color color, String text) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0),
                BorderFactory.createMatteBorder(1, 4, 1, 1, color)),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(left(Theme.label(title, Theme.FONT_TITLE, Theme.TEXT)));
        p.add(Box.createVerticalStrut(4));
        p.add(left(Theme.label("<html>" + text + "</html>", Theme.FONT, Theme.TEXT_DIM)));
        p.add(Box.createVerticalStrut(8));
        return p;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JPanel buttons(JButton... bs) {
        JPanel row = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
        for (JButton b : bs) {
            row.add(b);
            row.add(Box.createHorizontalStrut(8));
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JButton ghost(String text, Runnable r) {
        Widgets.FlatButton b = new Widgets.FlatButton(text, Widgets.Style.GHOST);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Re-reads the status (signature dates, key files present). */
    void refresh() {
        signatureBody.removeAll();
        Manifest pub = w.published;
        if (!w.publishedChecked) {
            signatureBody.add(left(Theme.label("Comprobando lo publicado…", Theme.FONT, Theme.TEXT_DIM)));
        } else if (pub == null) {
            signatureBody.add(left(Theme.label("No he podido descargar " + ManifestStatus.PUBLISHED
                + " (¿sin Internet o sin publicar?).", Theme.FONT, Theme.WARN)));
        } else {
            long days = ManifestStatus.daysLeft(pub.expiresAt());
            Color c = days < 0 ? Theme.ERROR : days < ManifestStatus.WARN_DAYS ? Theme.WARN : Theme.OK;
            JPanel big = Widgets.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
            big.add(Theme.label(days < 0 ? "CADUCADA" : days + " días", Theme.FONT.deriveFont(java.awt.Font.BOLD, 34f), c));
            big.add(Box.createHorizontalStrut(14));
            JPanel txt = column();
            txt.add(Theme.label(days < 0 ? "La firma publicada ha caducado: los jugadores no reciben cosméticos nuevos."
                : "hasta que caduque la firma publicada", Theme.FONT_BOLD, c));
            txt.add(Theme.label("Válida hasta el " + ManifestStatus.date(pub.expiresAt()) + " · versión "
                + pub.manifestVersion() + " · " + pub.cosmetics().size() + " cosméticos publicados", Theme.FONT, Theme.TEXT_DIM));
            big.add(txt);
            big.setAlignmentX(Component.LEFT_ALIGNMENT);
            big.setMaximumSize(new Dimension(Integer.MAX_VALUE, big.getPreferredSize().height));
            signatureBody.add(big);
            if (days >= 0 && days < ManifestStatus.WARN_DAYS) {
                signatureBody.add(Box.createVerticalStrut(6));
                signatureBody.add(left(Theme.label("⚠ Quedan menos de " + ManifestStatus.WARN_DAYS
                    + " días. Pulsa «Renovar firma» cuanto antes.", Theme.FONT_BOLD, Theme.WARN)));
            }
        }
        Manifest loc = w.local;
        signatureBody.add(Box.createVerticalStrut(6));
        signatureBody.add(left(Theme.label(loc == null ? "En tu PC (site/): todavía no se ha construido."
            : "En tu PC (site/): válida hasta el " + ManifestStatus.date(loc.expiresAt()) + " · versión "
            + loc.manifestVersion() + (pub != null && loc.manifestVersion() > pub.manifestVersion()
            ? " (más nueva que la publicada: aún no está subida)" : ""), Theme.FONT_SMALL, Theme.TEXT_FAINT)));

        keysBody.removeAll();
        Project p = w.project;
        keysBody.add(left(Theme.label("Carpeta: " + p.keysDir, Theme.FONT_MONO, Theme.TEXT)));
        keysBody.add(Box.createVerticalStrut(4));
        keysBody.add(left(keyLine("primary.key", "clave principal (firma)", p.primaryKeyExists())));
        keysBody.add(left(keyLine("backup.key", "clave de reserva", p.backupKeyExists())));
        keysBody.add(Box.createVerticalStrut(6));
        keysBody.add(left(Theme.label("<html>💾 <b>Haz una copia</b> de esa carpeta en un USB o en un gestor de contraseñas. "
            + "Si pierdes las dos claves tendrás que sacar una versión nueva del mod. "
            + "<b>Nunca</b> compartas los archivos .key.</html>", Theme.FONT, Theme.ACCENT)));
        revalidate();
        repaint();
    }

    private static JLabel keyLine(String file, String what, boolean exists) {
        return Theme.label((exists ? "✔ " : "✖ ") + file + " — " + what + (exists ? ": existe" : ": NO ENCONTRADA"),
            Theme.FONT, exists ? Theme.OK : Theme.ERROR);
    }

    // ------------------------------------------------------------------ actions

    private void renew() {
        if (w.refuseWhileBusy()) return;
        if (!Dialogs.confirm(this, "Renovar firma",
            "Se va a <b>construir, firmar y publicar</b> de nuevo la lista de cosméticos con una firma válida otros "
                + "180 días.<br><br>Antes de subir nada te enseñaré qué cambia y te pediré otra confirmación.",
            Dialogs.Kind.WARNING, "Continuar")) {
            return;
        }
        w.publish(true);
    }

    private void verifySite() {
        if (w.refuseWhileBusy()) return;
        w.runner.run("Verificar site/", List.of(TaskRunner.gradle(w.project, "Verificando site/ (htzVerifySite)",
            "htzVerifySite")), r -> {
            if (r.ok()) Dialogs.message(this, "site/ es correcto", "site/ pasa todas las comprobaciones del mod.", Dialogs.Kind.OK);
            else if (!r.cancelled()) w.explainFailure("site/ no es válido", r);
        });
    }

    private void rotate() {
        if (w.refuseWhileBusy()) return;
        if (!Dialogs.confirmTyping(this, "Rotar las claves de firma",
            "<p><b>Haz esto solo si crees que alguien ha conseguido tu primary.key.</b></p>"
                + "<p>Qué pasará:</p>" + Dialogs.list(List.of(
                "La clave de reserva (backup.key) pasa a ser la principal.",
                "Se crea una clave de reserva nueva y la principal antigua se guarda en keys/retired/.",
                "Se actualiza TrustedKeys.java (las claves públicas que lleva el mod)."))
                + "<p>Después tendrás que:</p>" + Dialogs.list(List.of(
                "Renovar la firma (para publicar firmado con la clave nueva).",
                "Publicar una versión nueva del mod. Las versiones antiguas seguirán confiando en la clave "
                    + "filtrada hasta que los jugadores actualicen.",
                "Hacer una copia nueva de la carpeta de claves."))
                + "<p style='color:#F08A8A'>No se puede deshacer.</p>",
            "Rotar claves", "ROTAR")) {
            return;
        }
        if (w.project.dryRun) {
            w.log.info("MODO PRUEBA: aquí se ejecutaría «gradlew htzKeygen --args=--rotate». No se ha cambiado nada.");
            return;
        }
        w.runner.run("Rotar claves", List.of(TaskRunner.gradle(w.project, "Rotando claves (htzKeygen --rotate)",
            "htzKeygen", "--args=--rotate")), r -> {
            refresh();
            if (r.ok()) {
                Dialogs.message(this, "Claves rotadas", "Ahora:" + Dialogs.list(List.of(
                    "Haz una copia nueva de la carpeta de claves.",
                    "Pulsa «Renovar firma» para publicar con la clave nueva.",
                    "Publica una versión nueva del mod (TrustedKeys.java ha cambiado).")), Dialogs.Kind.WARNING);
            } else if (!r.cancelled()) {
                w.explainFailure("No se pudieron rotar las claves", r);
            }
        });
    }

    private void backendTest() {
        if (w.refuseWhileBusy()) return;
        w.runner.run("Tests del backend", List.of(TaskRunner.npm(w.project, "npm test (backend/)", "test")), r -> {
            if (r.ok()) Dialogs.message(this, "Tests correctos", "Todos los tests del backend han pasado.", Dialogs.Kind.OK);
            else if (!r.cancelled()) Dialogs.message(this, "Algún test ha fallado",
                "Mira el <b>registro</b> de abajo para ver cuál.", Dialogs.Kind.DANGER);
        });
    }

    private void backendDeploy() {
        if (w.refuseWhileBusy()) return;
        if (!Dialogs.confirm(this, "Desplegar el backend",
            "Se subirá el código de <b>backend/</b> a Cloudflare y lo usarán <b>todos los jugadores</b> al momento.<br><br>"
                + "Antes se ejecutarán los tests; si alguno falla, no se despliega nada.",
            Dialogs.Kind.DANGER, project().dryRun ? "Simular despliegue" : "Desplegar")) {
            return;
        }
        List<TaskRunner.Step> steps = project().dryRun
            ? List.of(TaskRunner.npm(project(), "npm test (backend/) — MODO PRUEBA: no se despliega", "test"))
            : List.of(TaskRunner.npm(project(), "npm test (backend/)", "test"),
            TaskRunner.npm(project(), "npm run deploy (sube a Cloudflare)", "run", "deploy"));
        w.runner.run("Desplegar backend", steps, r -> {
            if (r.ok()) Dialogs.message(this, project().dryRun ? "Simulación terminada" : "Backend desplegado",
                project().dryRun ? "Los tests pasan. En modo prueba no se ha desplegado nada." : "El backend está actualizado.",
                Dialogs.Kind.OK);
            else if (!r.cancelled()) Dialogs.message(this, "No se ha desplegado",
                "Algo ha fallado. Mira el <b>registro</b> de abajo.", Dialogs.Kind.DANGER);
        });
    }

    private void mojangKeys() {
        if (w.refuseWhileBusy()) return;
        if (!Dialogs.confirm(this, "Actualizar claves de Mojang",
            "Úsalo <b>solo</b> si los inicios de sesión empiezan a fallar con <code>invalid_certificate</code>.<br><br>"
                + "Descarga las claves públicas actuales de Mojang al backend y después ejecuta los tests. "
                + "No despliega nada: si todo va bien, después pulsa «Desplegar backend».",
            Dialogs.Kind.WARNING, "Actualizar")) {
            return;
        }
        w.runner.run("Claves de Mojang", List.of(
            TaskRunner.npm(project(), "npm run update-mojang-keys", "run", "update-mojang-keys"),
            TaskRunner.npm(project(), "npm test", "test")), r -> {
            if (r.ok()) Dialogs.message(this, "Claves actualizadas",
                "Los tests pasan. Ahora pulsa «Desplegar backend» para subirlas.", Dialogs.Kind.OK);
            else if (!r.cancelled()) Dialogs.message(this, "Algo ha fallado", "Mira el <b>registro</b> de abajo.",
                Dialogs.Kind.DANGER);
        });
    }

    private Project project() {
        return w.project;
    }
}
