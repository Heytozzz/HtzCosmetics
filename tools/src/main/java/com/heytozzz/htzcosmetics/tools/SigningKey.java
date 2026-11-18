package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.crypto.SignatureVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

/**
 * An Ed25519 key pair stored as a small text file:
 * <pre>
 * # comment lines
 * public=&lt;raw 32-byte public key, Base64&gt;
 * private=&lt;PKCS#8 private key, Base64&gt;
 * </pre>
 */
public final class SigningKey {
    private final String publicBase64;
    private final PrivateKey privateKey;
    private final byte[] pkcs8;

    private SigningKey(String publicBase64, byte[] pkcs8) {
        this.publicBase64 = publicBase64;
        this.pkcs8 = pkcs8;
        try {
            this.privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new ToolException("The private key is damaged or not an Ed25519 key", e);
        }
    }

    public static SigningKey generate() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] x509 = pair.getPublic().getEncoded();
            String pub = Base64.getEncoder().encodeToString(Arrays.copyOfRange(x509, x509.length - 32, x509.length));
            return new SigningKey(pub, pair.getPrivate().getEncoded());
        } catch (GeneralSecurityException e) {
            throw new ToolException("This Java does not support Ed25519 (Java 15+ required)", e);
        }
    }

    public String publicBase64() {
        return publicBase64;
    }

    public byte[] sign(byte[] data) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(privateKey);
            s.update(data);
            byte[] sig = s.sign();
            // Self-check: never publish a signature the public key cannot verify.
            if (!SignatureVerifier.fromBase64Keys(publicBase64).verify(data, sig)) {
                throw new ToolException("Key file is inconsistent: public and private keys do not match");
            }
            return sig;
        } catch (GeneralSecurityException e) {
            throw new ToolException("Signing failed", e);
        }
    }

    public static SigningKey load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ToolException("Key file not found: " + file + "\nRun 'gradlew htzKeygen' first, or pass --keys <folder>.");
        }
        try {
            String pub = null;
            String priv = null;
            for (String line : Files.readAllLines(file, StandardCharsets.US_ASCII)) {
                line = line.trim();
                if (line.startsWith("public=")) pub = line.substring(7).trim();
                if (line.startsWith("private=")) priv = line.substring(8).trim();
            }
            if (pub == null || priv == null) throw new ToolException("Key file is incomplete: " + file);
            return new SigningKey(pub, Base64.getDecoder().decode(priv));
        } catch (IOException | IllegalArgumentException e) {
            throw new ToolException("Cannot read key file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Writes the key file. Refuses to overwrite and tries to make it readable only by the current user. */
    public void save(Path file, String role) throws IOException {
        Files.createDirectories(file.getParent());
        String text = "# HtzCosmetics " + role + " manifest signing key (Ed25519).\n"
            + "# PRIVATE: never share, upload or commit this file. Keep an offline copy (USB / password manager).\n"
            + "public=" + publicBase64 + "\n"
            + "private=" + Base64.getEncoder().encodeToString(pkcs8) + "\n";
        Files.writeString(file, text, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        restrictToOwner(file);
    }

    /** Best effort: owner-only permissions (POSIX 600, or a Windows ACL with only the owner). */
    static void restrictToOwner(Path file) {
        try {
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                return;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl == null) return;
            UserPrincipal owner = Files.getOwner(file);
            AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
            acl.setAcl(List.of(entry));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            System.out.println("  (note: could not restrict file permissions: " + e.getMessage() + ")");
        }
    }
}
