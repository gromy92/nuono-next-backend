package com.nuono.next.procurement.aliorder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ali1688Dp10RuntimeEnvironmentAttestationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsLaterEnvironmentDriftAndCrossSlotReplay() throws Exception {
        Fixture fixture = fixture("blue");

        assertTrue(verify(fixture, fixture.app));
        Path otherSlot = secureDirectory(temporaryDirectory.resolve("green"));
        assertFalse(Ali1688Dp10RuntimeEnvironmentAttestation.verify(
                otherSlot,
                fixture.app,
                fixture.jar,
                fixture.evidence,
                fixture.attestation
        ));
        Path linkedSlot = temporaryDirectory.resolve("blue-link");
        Files.createSymbolicLink(linkedSlot, fixture.app.getFileName());
        assertFalse(Ali1688Dp10RuntimeEnvironmentAttestation.verify(
                linkedSlot,
                linkedSlot,
                linkedSlot.resolve(fixture.app.relativize(fixture.jar)),
                linkedSlot.resolve(fixture.app.relativize(fixture.evidence)),
                linkedSlot.resolve(fixture.app.relativize(fixture.attestation))
        ));

        Files.writeString(fixture.env, "DRIFT=1\n", java.nio.file.StandardOpenOption.APPEND);
        assertFalse(verify(fixture, fixture.app));
    }

    @Test
    void rejectsAttestationWithWrongModeOrSymbolicLink() throws Exception {
        Fixture fixture = fixture("blue");
        assertTrue(verify(fixture, fixture.app));

        Files.setPosixFilePermissions(
                fixture.attestation,
                PosixFilePermissions.fromString("rw-r--r--")
        );
        assertFalse(verify(fixture, fixture.app));
        Files.setPosixFilePermissions(
                fixture.attestation,
                PosixFilePermissions.fromString("rw-------")
        );
        Path real = fixture.attestation.resolveSibling("runtime-env-real.sha256");
        Files.move(fixture.attestation, real);
        Files.createSymbolicLink(fixture.attestation, real.getFileName());

        assertFalse(verify(fixture, fixture.app));
    }

    @Test
    void rejectsGroupOrWorldReadableRuntimeEnvironment() throws Exception {
        Fixture fixture = fixture("blue");
        assertTrue(verify(fixture, fixture.app));

        Files.setPosixFilePermissions(
                fixture.env,
                PosixFilePermissions.fromString("rw-r-----")
        );
        assertFalse(verify(fixture, fixture.app));
        Files.setPosixFilePermissions(
                fixture.env,
                PosixFilePermissions.fromString("rw----r--")
        );
        assertFalse(verify(fixture, fixture.app));
    }

    @Test
    void acceptsReadOnlySearchableAppDirectoryButRejectsWritableAppDirectory() throws Exception {
        Fixture fixture = fixture("blue");

        Files.setPosixFilePermissions(
                fixture.app,
                PosixFilePermissions.fromString("rwxr-xr-x")
        );
        assertTrue(verify(fixture, fixture.app));

        Files.setPosixFilePermissions(
                fixture.app,
                PosixFilePermissions.fromString("rwxrwxr-x")
        );
        assertFalse(verify(fixture, fixture.app));
    }

    @Test
    void rejectsHardlinkedJarEvidenceEnvironmentOrAttestation() throws Exception {
        for (String target : java.util.List.of("jar", "evidence", "env", "attestation")) {
            Fixture fixture = fixture("slot-" + target);
            Path original = target.equals("jar") ? fixture.jar
                    : target.equals("evidence") ? fixture.evidence
                    : target.equals("env") ? fixture.env
                    : fixture.attestation;
            Files.createLink(original.resolveSibling(original.getFileName() + ".link"), original);

            assertFalse(verify(fixture, fixture.app), target);
        }
    }

    private Fixture fixture(String slot) throws Exception {
        Path app = secureDirectory(temporaryDirectory.resolve(slot));
        Path releaseRoot = secureDirectory(app.resolve(".release-evidence"));
        Path release = secureDirectory(releaseRoot.resolve("release-identity"));
        Path jar = Files.write(app.resolve("candidate.jar"), "jar".getBytes(StandardCharsets.UTF_8));
        Path evidence = Files.write(
                release.resolve("dp10-openapi-execution.json"),
                "evidence".getBytes(StandardCharsets.UTF_8)
        );
        Path env = Files.writeString(app.resolve(".env"), "CONFIG=stable\n");
        Files.setPosixFilePermissions(jar, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(evidence, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(env, PosixFilePermissions.fromString("rw-------"));
        String digest = Ali1688Dp10OpenApiProbeEvidenceSupport.sha256File(env);
        Path attestation = Files.createFile(
                release.resolve("runtime-env.sha256"),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")
                )
        );
        Files.writeString(attestation, digest + "\n");
        return new Fixture(app, jar, evidence, env, attestation);
    }

    private Path secureDirectory(Path path) throws Exception {
        return Files.createDirectory(
                path,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")
                )
        );
    }

    private boolean verify(Fixture fixture, Path processDirectory) {
        return Ali1688Dp10RuntimeEnvironmentAttestation.verify(
                processDirectory,
                fixture.app,
                fixture.jar,
                fixture.evidence,
                fixture.attestation
        );
    }

    private static final class Fixture {
        private final Path app;
        private final Path jar;
        private final Path evidence;
        private final Path env;
        private final Path attestation;

        private Fixture(Path app, Path jar, Path evidence, Path env, Path attestation) {
            this.app = app;
            this.jar = jar;
            this.evidence = evidence;
            this.env = env;
            this.attestation = attestation;
        }
    }
}
