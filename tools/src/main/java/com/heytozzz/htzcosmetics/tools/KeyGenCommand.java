package com.heytozzz.htzcosmetics.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * {@code gradlew htzKeygen}: creates the primary + backup signing keys once and writes their PUBLIC halves
 * into the mod's TrustedKeys.java. Running it again only re-checks; it never overwrites existing keys.
 *
 * <p>{@code --rotate}: use when the primary key leaked or was lost. The backup becomes the primary,
 * a new backup is created, and the old primary is moved to {@code retired/}.
 */
public final class KeyGenCommand {
    private KeyGenCommand() {
    }

    public static void main(String[] args) {
        Cli.run("htzKeygen", () -> {
            Cli cli = new Cli(args);
            boolean rotate = cli.flag("--rotate");
            Path keysDir = cli.keysDir();
            Path trustedFile = Path.of(cli.option("--trusted-keys-file", TrustedKeysFile.DEFAULT.toString()));
            cli.requireNoMoreArgs();
            if (rotate) rotate(keysDir, trustedFile);
            else create(keysDir, trustedFile);
        });
    }

    static void create(Path keysDir, Path trustedFile) throws IOException {
        Path primaryFile = keysDir.resolve("primary.key");
        Path backupFile = keysDir.resolve("backup.key");
        boolean hasPrimary = Files.exists(primaryFile);
        boolean hasBackup = Files.exists(backupFile);

        if (hasPrimary != hasBackup) {
            throw new ToolException("Only one of primary.key / backup.key exists in " + keysDir
                + ".\nIf the primary was lost, run: gradlew htzKeygen --args=\"--rotate\"");
        }
        if (hasPrimary) {
            List<String> keys = List.of(SigningKey.load(primaryFile).publicBase64(), SigningKey.load(backupFile).publicBase64());
            if (!keys.equals(TrustedKeysFile.read(trustedFile))) {
                TrustedKeysFile.write(trustedFile, keys);
                System.out.println("Keys already existed; TrustedKeys.java was updated to match them.");
            } else {
                System.out.println("Keys already exist and match TrustedKeys.java. Nothing to do.");
            }
            printSummary(keysDir, keys);
            return;
        }

        SigningKey primary = SigningKey.generate();
        SigningKey backup = SigningKey.generate();
        primary.save(primaryFile, "PRIMARY");
        backup.save(backupFile, "BACKUP");
        List<String> keys = List.of(primary.publicBase64(), backup.publicBase64());
        TrustedKeysFile.write(trustedFile, keys);
        System.out.println("New signing keys created.");
        printSummary(keysDir, keys);
    }

    static void rotate(Path keysDir, Path trustedFile) throws IOException {
        Path primaryFile = keysDir.resolve("primary.key");
        Path backupFile = keysDir.resolve("backup.key");
        if (!Files.exists(backupFile)) {
            throw new ToolException("Cannot rotate: no backup.key in " + keysDir + ". Restore it from your offline copy first.");
        }
        SigningKey promoted = SigningKey.load(backupFile);

        if (Files.exists(primaryFile)) {
            Path retired = keysDir.resolve("retired");
            Files.createDirectories(retired);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.move(primaryFile, retired.resolve("primary-" + stamp + ".key"));
        }
        Files.move(backupFile, primaryFile, StandardCopyOption.ATOMIC_MOVE);
        SigningKey newBackup = SigningKey.generate();
        newBackup.save(backupFile, "BACKUP");

        List<String> keys = List.of(promoted.publicBase64(), newBackup.publicBase64());
        TrustedKeysFile.write(trustedFile, keys);
        System.out.println("Keys rotated: the old backup is now the primary key and a new backup was created.");
        System.out.println("IMPORTANT: publish a new mod version now. Old mod versions still trust the retired key.");
        printSummary(keysDir, keys);
    }

    private static void printSummary(Path keysDir, List<String> keys) {
        System.out.println();
        System.out.println("  Private keys folder : " + keysDir.toAbsolutePath());
        System.out.println("  Primary public key  : " + keys.get(0));
        System.out.println("  Backup public key   : " + keys.get(1));
        System.out.println();
        System.out.println("  -> Copy the whole keys folder to a USB stick or password manager NOW.");
        System.out.println("  -> Never upload or share the .key files. Public keys are safe to share.");
    }
}
