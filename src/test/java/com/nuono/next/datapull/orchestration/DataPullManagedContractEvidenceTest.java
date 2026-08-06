package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class DataPullManagedContractEvidenceTest {
    private static final String COMMIT = "c".repeat(40);
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final List<DataPullRuntimeReleaseRequirement> REQUIREMENTS = List.of(
            DataPullRuntimeReleaseRequirement.DP04_STABLE_SNAPSHOT,
            DataPullRuntimeReleaseRequirement.DP06_COMPLETE_CAMPAIGN_ENUMERATION,
            DataPullRuntimeReleaseRequirement.DP07A_STABLE_SNAPSHOT,
            DataPullRuntimeReleaseRequirement.DP10_MODIFIED_TIME_VISIBILITY_CONTRACT
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactCandidateBundleVerifiesEveryManagedContractRequirement() throws Exception {
        Fixture fixture = new Fixture();
        fixture.writeBundle((ignored) -> { });

        for (DataPullRuntimeReleaseRequirement requirement : REQUIREMENTS) {
            DataPullManagedContractEvidence evidence = fixture.evidence(requirement);
            assertEquals(requirement, evidence.requirement());
            assertTrue(evidence.verified(), requirement.name());
        }
    }

    @Test
    void emptyCandidatePolicyKeepsOtherwiseValidBundleBlocked() throws Exception {
        Fixture fixture = new Fixture(false);
        fixture.writeBundle((ignored) -> { });

        assertAllBlocked(fixture);
    }

    @Test
    void sourceStatusExpiryAndIdentityAreAllFailClosed() throws Exception {
        Fixture fixture = new Fixture();

        fixture.writeBundle((root) -> item(root, "DP06_COMPLETE_CAMPAIGN_ENUMERATION")
                .put("status", "READY"));
        assertAllBlocked(fixture);

        fixture.writeBundle((root) -> item(root, "DP04_STABLE_SNAPSHOT")
                .put("source_kind", "LOCAL_ASSUMPTION"));
        assertAllBlocked(fixture);

        fixture.writeBundle((root) -> item(root, "DP07A_STABLE_SNAPSHOT")
                .put("source_identity_sha256", "not-a-sha"));
        assertAllBlocked(fixture);

        fixture.writeBundle((root) -> item(root, "DP10_MODIFIED_TIME_VISIBILITY_CONTRACT")
                .put("expires_at", NOW.minusSeconds(1).toString()));
        assertAllBlocked(fixture);

        fixture.writeBundle((root) -> item(root, "DP04_STABLE_SNAPSHOT")
                .put("source_identity_sha256", "f".repeat(64)));
        assertFalse(fixture.evidence(
                DataPullRuntimeReleaseRequirement.DP04_STABLE_SNAPSHOT
        ).verified());
        assertTrue(fixture.evidence(
                DataPullRuntimeReleaseRequirement.DP06_COMPLETE_CAMPAIGN_ENUMERATION
        ).verified());
    }

    @Test
    void commitJarEvidenceAndFullEnvironmentIdentityCannotDrift() throws Exception {
        Fixture commit = new Fixture("commit");
        commit.writeBundle((root) -> root.put("manifest_commit", "d".repeat(40)));
        assertAllBlocked(commit);

        Fixture jar = new Fixture("jar");
        jar.writeBundle((ignored) -> { });
        Files.writeString(jar.jar, "drift", StandardOpenOption.TRUNCATE_EXISTING);
        assertAllBlocked(jar);

        Fixture configuration = new Fixture("configuration");
        configuration.writeBundle((ignored) -> { });
        Files.writeString(
                configuration.envFile, "CONFIG=drift\n", StandardOpenOption.APPEND
        );
        assertAllBlocked(configuration);

        Fixture evidence = new Fixture("evidence");
        evidence.writeBundle((ignored) -> { });
        evidence.environment.setProperty(
                DataPullManagedContractEvidence.SHA, "f".repeat(64)
        );
        assertAllBlocked(evidence);
    }

    private void assertAllBlocked(Fixture fixture) {
        for (DataPullRuntimeReleaseRequirement requirement : REQUIREMENTS) {
            assertFalse(fixture.evidence(requirement).verified(), requirement.name());
        }
    }

    private static ObjectNode item(ObjectNode root, String requirement) {
        for (com.fasterxml.jackson.databind.JsonNode item : root.withArray("evidence")) {
            if (requirement.equals(item.path("requirement").asText())) {
                return (ObjectNode) item;
            }
        }
        throw new IllegalArgumentException("missing requirement " + requirement);
    }

    private final class Fixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final MockEnvironment environment = new MockEnvironment();
        private final Path app;
        private final Path release;
        private final Path jar;
        private final Path evidenceFile;
        private final Path envFile;
        private final Path attestation;

        private Fixture() throws Exception {
            this("slot", true);
        }

        private Fixture(boolean approveSources) throws Exception {
            this("slot", approveSources);
        }

        private Fixture(String slot) throws Exception {
            this(slot, true);
        }

        private Fixture(String slot, boolean approveSources) throws Exception {
            app = secureDirectory(temporaryDirectory.resolve(slot));
            Path root = secureDirectory(app.resolve(".release-evidence"));
            release = secureDirectory(root.resolve(COMMIT + "-" + "a".repeat(64)));
            jar = policyJar(app.resolve("candidate.jar"), approveSources);
            evidenceFile = secureFile(
                    release.resolve("dp-runtime-contract-evidence.json"), "{}"
            );
            envFile = secureFile(app.resolve(".env"), "");
            attestation = secureFile(release.resolve("runtime-env.sha256"), "");
        }

        private Path policyJar(Path path, boolean approveSources) throws Exception {
            ObjectNode policy = mapper.createObjectNode();
            policy.put("schema", DataPullManagedContractPolicy.SCHEMA);
            ArrayNode requirements = policy.putArray("requirements");
            for (int index = 0; index < REQUIREMENTS.size(); index++) {
                DataPullRuntimeReleaseRequirement requirement = REQUIREMENTS.get(index);
                ObjectNode item = requirements.addObject();
                item.put("requirement", requirement.name());
                item.put(
                        "source_kind",
                        DataPullManagedContractPolicy.sourceKind(requirement)
                );
                ArrayNode approved = item.putArray("approved_source_sha256");
                if (approveSources) approved.add(Integer.toHexString(index + 1).repeat(64));
            }
            Path file = secureFile(path, "");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(
                    file, StandardOpenOption.TRUNCATE_EXISTING
            ))) {
                output.putNextEntry(new ZipEntry(DataPullManagedContractPolicy.ENTRY));
                output.write(mapper.writeValueAsBytes(policy));
                output.closeEntry();
            }
            return file;
        }

        private void writeBundle(Consumer<ObjectNode> mutation) throws Exception {
            ObjectNode root = mapper.createObjectNode();
            root.put("schema", DataPullManagedContractEvidence.SCHEMA);
            root.put("type", DataPullManagedContractEvidence.TYPE);
            root.put("manifest_commit", COMMIT);
            root.put(
                    "candidate_jar_sha256",
                    DataPullManagedEvidenceTopology.sha256File(jar)
            );
            ArrayNode items = root.putArray("evidence");
            for (int index = 0; index < REQUIREMENTS.size(); index++) {
                DataPullRuntimeReleaseRequirement requirement = REQUIREMENTS.get(index);
                ObjectNode item = items.addObject();
                item.put("requirement", requirement.name());
                item.put("status", DataPullManagedContractEvidence.STATUS);
                item.put(
                        "source_kind",
                        DataPullManagedContractEvidence.sourceKind(requirement)
                );
                item.put("source_identity_sha256", Integer.toHexString(index + 1)
                        .repeat(64));
                item.put("verified_at", NOW.minusSeconds(60).toString());
                item.put("expires_at", NOW.plusSeconds(3600).toString());
            }
            mutation.accept(root);
            Files.writeString(
                    evidenceFile,
                    mapper.writeValueAsString(root) + "\n",
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            bindEnvironment(DataPullManagedEvidenceTopology.sha256File(evidenceFile));
        }

        private void bindEnvironment(String evidenceSha) throws Exception {
            environment.setProperty("NUONO_NEXT_APP_DIR", app.toString());
            environment.setProperty("NUONO_NEXT_JAR", jar.toString());
            environment.setProperty(
                    DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT,
                    COMMIT
            );
            environment.setProperty(
                    DataPullManagedContractEvidence.FILE,
                    evidenceFile.toString()
            );
            environment.setProperty(DataPullManagedContractEvidence.SHA, evidenceSha);
            environment.setProperty(
                    DataPullManagedContractEvidence.ENV_ATTESTATION,
                    attestation.toString()
            );
            String runtime = String.join("\n",
                    "NUONO_NEXT_APP_DIR=" + app,
                    "NUONO_NEXT_JAR=" + jar,
                    DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT + "=" + COMMIT,
                    DataPullManagedContractEvidence.FILE + "=" + evidenceFile,
                    DataPullManagedContractEvidence.SHA + "=" + evidenceSha,
                    DataPullManagedContractEvidence.ENV_ATTESTATION + "=" + attestation,
                    ""
            );
            Files.writeString(envFile, runtime, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(
                    attestation,
                    DataPullManagedEvidenceTopology.sha256File(envFile) + "\n",
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        }

        private DataPullManagedContractEvidence evidence(
                DataPullRuntimeReleaseRequirement requirement
        ) {
            return new DataPullManagedContractEvidence(
                    requirement,
                    environment,
                    mapper,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    app
            );
        }
    }

    private static Path secureDirectory(Path path) throws Exception {
        return Files.createDirectory(
                path,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")
                )
        );
    }

    private static Path secureFile(Path path, String content) throws Exception {
        Path file = Files.createFile(
                path,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")
                )
        );
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
