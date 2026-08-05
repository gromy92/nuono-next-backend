package com.nuono.next.procurement.aliorder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.regex.Pattern;

/** Verifies the protected full-.env digest without exposing secret-derived hashes. */
final class Ali1688Dp10RuntimeEnvironmentAttestation {
    private static final Pattern PAYLOAD = Pattern.compile("[0-9a-f]{64}\\n");
    private static final Set<PosixFilePermission> FILE_MODE =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_MODE =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<Set<PosixFilePermission>> APP_DIRECTORY_MODES = Set.of(
            PRIVATE_DIRECTORY_MODE,
            PosixFilePermissions.fromString("rwxr-x---"),
            PosixFilePermissions.fromString("rwxr-xr-x")
    );

    private Ali1688Dp10RuntimeEnvironmentAttestation() {
    }

    static boolean verify(
            Path processDirectory,
            Path appDirectory,
            Path jarFile,
            Path evidenceFile,
            Path attestationFile
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
                    || !"dp10-openapi-execution.json".equals(
                            evidence.getFileName().toString())
                    || !"runtime-env.sha256".equals(attestation.getFileName().toString())
                    || !securePrivateDirectory(releaseRoot, owner)
                    || !securePrivateDirectory(releaseDirectory, owner)
                    || !secureFile(jar, owner)
                    || !secureFile(evidence, owner)
                    || !secureFile(envFile, owner)
                    || !secureFile(attestation, owner)) return false;
            String expected = Files.readString(attestation, StandardCharsets.US_ASCII);
            return PAYLOAD.matcher(expected).matches()
                    && expected.substring(0, 64).equals(
                            Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(envFile));
        } catch (RuntimeException | java.io.IOException invalid) {
            return false;
        }
    }

    private static Path absolute(Path path) {
        if (path == null || !path.isAbsolute()) {
            throw new IllegalArgumentException("DP10_RUNTIME_PATH_INVALID");
        }
        return path.normalize();
    }

    private static boolean regularNoLink(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private static boolean secureFile(Path path, UserPrincipal owner)
            throws java.io.IOException {
        return regularNoLink(path)
                && Files.getPosixFilePermissions(path).equals(FILE_MODE)
                && owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
                && ((Number) Files.getAttribute(
                        path,
                        "unix:nlink",
                        LinkOption.NOFOLLOW_LINKS
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
                && Files.getPosixFilePermissions(path).equals(PRIVATE_DIRECTORY_MODE)
                && owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS));
    }

    private static boolean directoryNoLink(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }
}
