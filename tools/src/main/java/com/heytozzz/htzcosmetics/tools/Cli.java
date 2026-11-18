package com.heytozzz.htzcosmetics.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tiny command-line helpers shared by the tool commands. */
final class Cli {
    /** Where private keys live by default: outside the project, so they are never committed or zipped by accident. */
    static final Path DEFAULT_KEYS_DIR = Path.of(System.getProperty("user.home"), ".htzcosmetics", "keys");

    private final List<String> args;

    Cli(String[] args) {
        this.args = new ArrayList<>(List.of(args));
    }

    boolean flag(String name) {
        return args.remove(name);
    }

    String option(String name, String fallback) {
        int i = args.indexOf(name);
        if (i < 0) return fallback;
        if (i + 1 >= args.size()) throw new ToolException("Missing value after " + name);
        String value = args.get(i + 1);
        args.remove(i + 1);
        args.remove(i);
        return value;
    }

    Path keysDir() {
        String env = System.getenv("HTZ_KEYS_DIR");
        return Path.of(option("--keys", env != null && !env.isBlank() ? env : DEFAULT_KEYS_DIR.toString()));
    }

    void requireNoMoreArgs() {
        if (!args.isEmpty()) throw new ToolException("Unknown arguments: " + String.join(" ", args));
    }

    /** Runs a command, printing friendly errors instead of stack traces for expected problems. */
    static void run(String title, ThrowingRunnable body) {
        try {
            body.run();
        } catch (ToolException e) {
            System.err.println();
            System.err.println("[" + title + "] ERROR: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println();
            System.err.println("[" + title + "] UNEXPECTED ERROR:");
            e.printStackTrace();
            System.exit(2);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
