package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Drives the real studio window end to end in DRY-RUN mode (nothing is published or deployed) and saves a
 * screenshot of every screen: add a scratch cosmetic, validate, build site/, simulated publish, maintenance,
 * key-rotation dialog (cancelled), local test client (started, then stopped), delete the scratch cosmetic.
 *
 * <p>Run: {@code gradlew :studio:studioSelfTest --args="<screenshot folder> [--with-client]"}.
 */
public final class StudioSelfTest {
    private static final String SCRATCH = "zz_studio_selftest";
    private static Path out;
    private static int shot;

    public static void main(String[] args) throws Exception {
        System.setProperty("htzstudio.dryRun", "true");
        out = Path.of(args[0]);
        boolean withClient = args.length > 1 && args[1].equals("--with-client");
        Files.createDirectories(out);
        Project project = Project.locate();
        if (Files.exists(project.cosmeticsDir.resolve(SCRATCH))) throw new IllegalStateException("scratch folder exists");

        // Scratch input files: a copy of the top hat under another name, outside the project.
        Path input = Files.createTempDirectory("htzstudio-selftest");
        Path model = Files.copy(project.cosmeticsDir.resolve("top_hat/model.bbmodel"), input.resolve("prueba.bbmodel"));
        Path texture = Files.copy(project.cosmeticsDir.resolve("top_hat/texture.png"), input.resolve("prueba.png"));

        AtomicReference<MainWindow> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow w = new MainWindow(project);
            w.setVisible(true);
            ref.set(w);
        });
        MainWindow w = ref.get();
        waitFor("cosmetics loaded", 30, () -> w.entry("top_hat") != null);
        waitFor("signature status", 30, () -> w.publishedChecked);
        Thread.sleep(800);
        snap(w, "main");

        // --- Add (with a validation error first) ---
        SwingUtilities.invokeLater(w::addCosmetic);
        EditorDialog ed = (EditorDialog) waitDialog("editor", d -> d instanceof EditorDialog);
        Thread.sleep(600);
        snap(ed, "editor-empty");
        edt(() -> ed.testFill(model, texture, "Mal Id!", "Name with § sign", null, Category.HEAD, Rarity.LEGENDARY));
        Thread.sleep(1500);
        snap(ed, "editor-errors");
        check(!ed.testReady(), "save must be disabled with errors");
        edt(() -> ed.testFill(null, null, SCRATCH, "Studio test hat", "Sombrero de prueba", null, null));
        waitFor("editor ready", 20, ed::testReady);
        Thread.sleep(800);
        snap(ed, "editor-ready");
        edt(ed::testSave);
        waitFor("editor closed", 10, () -> !ed.isShowing());
        check(Files.isRegularFile(project.cosmeticsDir.resolve(SCRATCH + "/cosmetic.json")), "cosmetic.json written");
        check(Files.isRegularFile(project.cosmeticsDir.resolve(SCRATCH + "/model.bbmodel")), "model written");
        check(Files.isRegularFile(project.cosmeticsDir.resolve(SCRATCH + "/texture.png")), "texture written");
        waitFor("scratch listed", 20, () -> w.entry(SCRATCH) != null);
        edt(() -> w.select(SCRATCH));
        Thread.sleep(800);
        snap(w, "main-with-scratch");

        // --- Check: everything OK ---
        edt(() -> w.checkAll(true, null));
        JDialog ok = waitDialog("check result", d -> d.getTitle().equals("Todo correcto"));
        snap(ok, "check-ok");
        click(ok, "Entendido");

        // --- Check: a broken cosmetic is reported in Spanish ---
        Path broken = project.cosmeticsDir.resolve(SCRATCH + "_broken");
        Files.createDirectories(broken);
        Files.copy(model, broken.resolve("model.bbmodel"));
        Files.writeString(broken.resolve("cosmetic.json"),
            "{\"name\":{\"en_us\":\"Broken\"},\"category\":\"HEAD\",\"rarity\":\"RARE\",\"anchor\":\"LEFT_LEG\"}");
        edt(() -> w.reload(null));
        Thread.sleep(1500);
        edt(() -> w.checkAll(true, null));
        JDialog bad = waitDialog("check errors", d -> d.getTitle().contains("con errores"));
        snap(bad, "check-errors");
        click(bad, "Entendido");
        Thread.sleep(500);
        snap(w, "main-with-error-card");
        deleteTree(broken);
        edt(() -> w.reload(SCRATCH));
        Thread.sleep(1500);

        // --- Publish (dry run): htzBuildSite for real, then the confirmation, then a simulated publish ---
        edt(() -> w.publish(false));
        JDialog confirm = waitDialog("publish confirmation", d -> d.getTitle().contains("Publicar en Internet"), 400);
        snap(confirm, "publish-confirm");
        check(w.log.text().contains("Site built and verified"), "htzBuildSite ran");
        click(confirm, "Simular publicación");
        JDialog done = waitDialog("publish done", d -> d.getTitle().equals("Simulación terminada"), 400);
        snap(done, "publish-done");
        click(done, "Entendido");
        check(w.log.text().contains("passes every check the mod performs"), "htzVerifySite ran");

        // --- Maintenance ---
        edt(() -> w.showMaintenance(true));
        Thread.sleep(1200);
        snap(w, "maintenance");
        click(w, "Rotar claves…");
        JDialog rot = waitDialog("rotation", d -> d.getTitle().contains("Rotar"));
        snap(rot, "rotate-confirm");
        click(rot, "Cancelar");
        click(w, "Desplegar backend…");
        JDialog dep = waitDialog("deploy", d -> d.getTitle().contains("Desplegar"));
        snap(dep, "deploy-confirm");
        click(dep, "Cancelar");
        edt(() -> w.showMaintenance(false));

        // --- Local test: build site/ and start the dev client, then stop it ---
        if (withClient) {
            edt(w::testLocally);
            waitFor("client loaded cosmetics", 900, () -> w.log.text().contains("Loaded 3 cosmetics"));
            Thread.sleep(15_000);
            snap(w, "test-client-running");
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            ImageIO.write(new Robot().createScreenCapture(screen), "png", out.resolve(String.format("%02d-screen-minecraft.png", ++shot)).toFile());
            edt(w.runner::stop);
            waitFor("client stopped", 120, () -> !w.runner.busy());
            Thread.sleep(5000);
            long knot = ProcessHandle.allProcesses().filter(p -> p.info().commandLine().orElse("").contains("KnotClient")).count();
            check(knot == 0, "Minecraft dev client stopped (" + knot + " left)");
            System.out.println("SELFTEST: dev client started, loaded 3 cosmetics from site/ and was stopped");
        }

        // --- Delete the scratch cosmetic through the UI ---
        CosmeticStore.Entry scratch = w.entry(SCRATCH);
        SwingUtilities.invokeLater(() -> w.deleteCosmetic(scratch));
        JDialog del = waitDialog("delete", d -> d.getTitle().equals("Eliminar cosmético"));
        snap(del, "delete-confirm");
        click(del, "Eliminar");
        waitFor("scratch deleted", 20, () -> !Files.exists(project.cosmeticsDir.resolve(SCRATCH)));
        waitFor("scratch unlisted", 20, () -> w.entry(SCRATCH) == null);
        Thread.sleep(800);
        snap(w, "main-after-delete");
        deleteTree(input);
        System.out.println("SELFTEST PASSED. Screenshots in " + out.toAbsolutePath());
        System.exit(0);
    }

    // ------------------------------------------------------------------ helpers

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("SELFTEST FAILED: " + what);
            System.exit(1);
        }
        System.out.println("  ok: " + what);
    }

    private static void waitFor(String what, int seconds, BooleanSupplier cond) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            AtomicReference<Boolean> r = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> r.set(cond.getAsBoolean()));
            if (r.get()) return;
            Thread.sleep(250);
        }
        check(false, "timed out waiting for " + what);
    }

    private static JDialog waitDialog(String what, java.util.function.Predicate<JDialog> p) throws Exception {
        return waitDialog(what, p, 30);
    }

    private static JDialog waitDialog(String what, java.util.function.Predicate<JDialog> p, int seconds) throws Exception {
        AtomicReference<JDialog> found = new AtomicReference<>();
        waitFor(what, seconds, () -> {
            for (Window win : Window.getWindows()) {
                if (win instanceof JDialog d && d.isShowing() && p.test(d)) {
                    found.set(d);
                    return true;
                }
            }
            return false;
        });
        Thread.sleep(400);
        return found.get();
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void click(Window win, String text) {
        Runnable r = () -> {
            AbstractButton b = find(win, text);
            if (b == null) throw new IllegalStateException("button not found: " + text);
            b.doClick();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private static AbstractButton find(Container c, String text) {
        for (Component child : c.getComponents()) {
            if (child instanceof AbstractButton b && text.equals(b.getText()) && b.isShowing()) return b;
            if (child instanceof Container cc) {
                AbstractButton r = find(cc, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Paints the window's contents into a PNG (works even if other windows cover it). */
    private static void snap(Window win, String name) throws Exception {
        Supplier<BufferedImage> paint = () -> {
            BufferedImage img = new BufferedImage(win.getWidth(), win.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            win.paint(g);
            g.dispose();
            return img;
        };
        AtomicReference<BufferedImage> img = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> img.set(paint.get()));
        Path file = out.resolve(String.format("%02d-%s.png", ++shot, name));
        ImageIO.write(img.get(), "png", file.toFile());
        System.out.println("  screenshot: " + file.getFileName());
    }

    private static void deleteTree(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    @SuppressWarnings("unused")
    private static void copy(Path a, Path b) throws Exception {
        Files.copy(a, b, StandardCopyOption.REPLACE_EXISTING);
    }
}
