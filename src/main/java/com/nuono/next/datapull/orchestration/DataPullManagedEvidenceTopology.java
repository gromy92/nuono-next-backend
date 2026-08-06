package com.nuono.next.datapull.orchestration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.util.Set;
import java.util.regex.Pattern;

/** Slot-local ownership, mode, hardlink and full-environment binding for release evidence. */
final class DataPullManagedEvidenceTopology {
    private static final Pattern ATTESTATION = Pattern.compile("[0-9a-f]{64}\\n");
    private static final Set<PosixFilePermission> PRIVATE_FILE =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<Set<PosixFilePermission>> APP_DIRECTORY_MODES = Set.of(
            PRIVATE_DIRECTORY,
            PosixFilePermissions.fromString("rwxr-x---"),
            PosixFilePermissions.fromString("rwxr-xr-x")
    );

    private DataPullManagedEvidenceTopology() {
    }

    static boolean verify(
            Path processDirectory,
            Path appDirectory,
            Path jarFile,
            Path evidenceFile,
            Path attestationFile,
            String expectedCommit
    ) {
        try {
            Path process = absolute(processDirectory);
            Path app = absolute(appDirectory);
            Path jar = absolute(jarFile);
            Path evidence = absolute(evidenceFile);
            Path attestation = absolute(attestationFile);
            Path releaseRoot = app.resolve(".release-evidence");
            Path releaseDirectory = evidence.getParent();
            Path envFile = app.resolve(".env");
            UserPrincipal owner = Files.getOwner(app, LinkOption.NOFOLLOW_LINKS);
            if (!process.equals(app)
                    || !secureAppDirectory(app, owner)
                    || !jar.getParent().equals(app)
                    || releaseDirectory == null
                    || !releaseRoot.equals(releaseDirectory.getParent())
                    || !releaseDirectory.equals(attestation.getParent())
                    || !releaseDirectory.getFileName().toString().matches(
                            Pattern.quote(expectedCommit) + "-[0-9a-f]{64}")
                    || !"dp-runtime-contract-evidence.json".equals(
                            evidence.getFileName().toString())
                    || !"runtime-env.sha256".equals(attestation.getFileName().toString())
                    || !securePrivateDirectory(releaseRoot, owner)
                    || !securePrivateDirectory(releaseDirectory, owner)
                    || !secureFile(jar, owner)
                    || !secureFile(evidence, owner)
                    || !secureFile(envFile, owner)
                    || !secureFile(attestation, owner)) return false;
            String expected = Files.readString(attestation, StandardCharsets.US_ASCII);
            return ATTESTATION.matcher(expected).matches()
                    && expected.substring(0, 64).equals(sha256File(envFile));
        } catch (RuntimeException | java.io.IOException invalid) {
            return false;
        }
    }

    static String sha256File(Path path) throws java.io.IOException {
        MessageDigest digest = sha256();
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, read);
            }
        }
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(64);
        for (byte item : digest.digest()) {
            int value = item & 0xff;
            result.append(digits[value >>> 4]).append(digits[value & 0x0f]);
        }
        return result.toString();
    }

    private static Path absolute(Path path) {
        if (path == null || !path.isAbsolute()) {
            throw new IllegalArgumentException("DP_RUNTIME_EVIDENCE_PATH_INVALID");
        }
        return path.normalize();
    }

    private static boolean secureFile(Path path, UserPrincipal owner)
            throws java.io.IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && PRIVATE_FILE.equals(Files.getPosixFilePermissions(path))
                && owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
                && ((Number) Files.getAttribute(
                        path, "unix:nlink", LinkOption.NOFOLLOW_LINKS
                )).longValue() == 1L;
    }

    private static boolean secureAppDirectory(Path path, UserPrincipal owner)
            throws java.io.IOException {
        return directoryNoLink(path)
                && APP_DIRECTORY_MODES.contains(Files.getPosixFilePermissions(path))
                && owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS));
    }

    private static boolean securePrivateDirectory(Path path, UserPrincipal owner)
            throws java.io.IOException {
        return directoryNoLink(path)
                && PRIVATE_DIRECTORY.equals(Files.getPosixFilePermissions(path))
                && owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS));
    }

    private static boolean directoryNoLink(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE", impossible);
        }
    }
}
