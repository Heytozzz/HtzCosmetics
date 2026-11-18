package com.heytozzz.htzcosmetics.studio;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the owner's usual commands (gradlew / npm) one after another as child processes, streaming their output
 * to the {@link LogPanel}. Only one task runs at a time.
 */
final class TaskRunner {
    record Step(String title, List<String> command, Path dir) {
    }

    /** Result of a whole task. {@code output} holds every line printed by the steps. */
    record Result(boolean ok, boolean cancelled, String output) {
    }

    interface Done {
        void done(Result result);
    }

    private final LogPanel log;
    private final Runnable busyChanged;
    private volatile Process current;
    private volatile boolean cancelled;
    private volatile boolean busy;

    TaskRunner(LogPanel log, Runnable busyChanged) {
        this.log = log;
        this.busyChanged = busyChanged;
        log.stop.addActionListener(e -> stop());
    }

    boolean busy() {
        return busy;
    }

    static Step gradle(Project p, String title, String... args) {
        List<String> cmd = new ArrayList<>(List.of("cmd.exe", "/c", p.gradlew().toString(), "--console=plain"));
        cmd.addAll(List.of(args));
        return new Step(title, cmd, p.root);
    }

    static Step npm(Project p, String title, String... args) {
        List<String> cmd = new ArrayList<>(List.of("cmd.exe", "/c", "npm"));
        cmd.addAll(List.of(args));
        return new Step(title, cmd, p.backendDir);
    }

    /** Runs the steps in order and stops at the first failure. {@code done} runs on the Swing thread. */
    void run(String title, List<Step> steps, Done done) {
        if (busy) throw new IllegalStateException("A task is already running");
        busy = true;
        cancelled = false;
        log.started(title);
        busyChanged.run();
        Thread t = new Thread(() -> {
            StringBuilder all = new StringBuilder();
            boolean ok = true;
            for (Step step : steps) {
                if (cancelled) {
                    ok = false;
                    break;
                }
                SwingUtilities.invokeLater(() -> log.info(step.title() + "   ($ " + String.join(" ",
                    step.command().subList(2, step.command().size())) + ")"));
                try {
                    ProcessBuilder pb = new ProcessBuilder(step.command()).directory(step.dir().toFile())
                        .redirectErrorStream(true);
                    Process p = pb.start();
                    current = p;
                    p.getOutputStream().close(); // never wait for keyboard input
                    Charset cs = Charset.forName(System.getProperty("native.encoding", "UTF-8"));
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), cs))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            all.append(line).append('\n');
                            String l = line;
                            SwingUtilities.invokeLater(() -> log.line(l));
                        }
                    }
                    int exit = p.waitFor();
                    current = null;
                    if (exit != 0) {
                        ok = false;
                        break;
                    }
                } catch (Exception e) {
                    all.append(e).append('\n');
                    String msg = e.getMessage();
                    SwingUtilities.invokeLater(() -> log.error("No se pudo ejecutar: " + msg));
                    ok = false;
                    break;
                }
            }
            boolean success = ok && !cancelled;
            Result result = new Result(success, cancelled, all.toString());
            SwingUtilities.invokeLater(() -> {
                busy = false;
                log.finished(success, success ? title + ": terminado"
                    : result.cancelled() ? title + ": detenido" : title + ": ha fallado (mira el registro)");
                busyChanged.run();
                done.done(result);
            });
        }, "htzstudio-task");
        t.setDaemon(true);
        t.start();
    }

    /** Stops the running step and everything it started (e.g. the Minecraft test client). */
    void stop() {
        cancelled = true;
        Process p = current;
        if (p != null) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    /** Lines of the output that are errors reported by the tools ("[htzBuildSite] ERROR: ..."), with continuation. */
    static List<String> toolErrors(String output) {
        List<String> out = new ArrayList<>();
        String[] lines = output.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].matches("^\\[htz\\w+] (UNEXPECTED )?ERROR:?.*")) {
                StringBuilder sb = new StringBuilder(lines[i].strip());
                while (i + 1 < lines.length && lines[i + 1].startsWith("  ")) sb.append('\n').append(lines[++i].strip());
                out.add(sb.toString());
            }
        }
        return out;
    }
}
