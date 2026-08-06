package com.nuono.next.datapull.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

/** One requirement view over the candidate-bound managed provider-contract bundle. */
public final class DataPullManagedContractEvidence implements DataPullRuntimeReleaseEvidence {
    static final String FILE = "NUONO_DP_RUNTIME_CONTRACT_EVIDENCE_FILE";
    static final String SHA = "NUONO_DP_RUNTIME_CONTRACT_EVIDENCE_SHA256";
    static final String ENV_ATTESTATION =
            "NUONO_DP_RUNTIME_RELEASE_ENV_SHA256_FILE";
    static final String SCHEMA = "nuono.dp-runtime-provider-contracts/v1";
    static final String TYPE = "DP_RUNTIME_PROVIDER_CONTRACTS";
    static final String STATUS = "CONTRACT_PROVEN";
    static final Duration MAX_VALIDITY = Duration.ofDays(30);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema", "type", "manifest_commit", "candidate_jar_sha256", "evidence"
    );
    private static final Set<String> ITEM_FIELDS = Set.of(
            "requirement", "status", "source_kind", "source_identity_sha256",
            "verified_at", "expires_at"
    );
    private final DataPullRuntimeReleaseRequirement requirement;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path processDirectory;

    public DataPullManagedContractEvidence(
            DataPullRuntimeReleaseRequirement requirement,
            Environment environment,
            ObjectMapper objectMapper
    ) {
        this(
                requirement, environment, objectMapper, Clock.systemUTC(),
                Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        );
    }

    DataPullManagedContractEvidence(
            DataPullRuntimeReleaseRequirement requirement,
            Environment environment,
            ObjectMapper objectMapper,
            Clock clock,
            Path processDirectory
    ) {
        if (!DataPullManagedContractPolicy.requirements().contains(requirement)) {
            throw new IllegalArgumentException("unsupported managed contract requirement");
        }
        this.requirement = requirement;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.processDirectory = Objects.requireNonNull(processDirectory, "processDirectory");
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return requirement;
    }

    @Override
    public boolean verified() {
        try {
            String expectedSha = value(SHA);
            String expectedCommit = value(
                    DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT
            );
            if (!matches(expectedSha, SHA256) || !matches(expectedCommit, COMMIT)) {
                return false;
            }
            Path file = absolute(value(FILE));
            Path app = absolute(value("NUONO_NEXT_APP_DIR"));
            Path jar = absolute(value("NUONO_NEXT_JAR"));
            Path attestation = absolute(value(ENV_ATTESTATION));
            if (!DataPullManagedEvidenceTopology.verify(
                    processDirectory, app, jar, file, attestation, expectedCommit
            ) || !expectedSha.equals(DataPullManagedEvidenceTopology.sha256File(file))
                    || !environmentFileBinds(
                            app, jar, file, attestation, expectedCommit, expectedSha
                    )) {
                return false;
            }
            JsonNode root = objectMapper.readTree(Files.readAllBytes(file));
            return verifyRoot(
                    root,
                    expectedCommit,
                    jar,
                    DataPullManagedContractPolicy.load(jar, objectMapper)
            );
        } catch (RuntimeException | java.io.IOException invalid) {
            return false;
        }
    }

    private boolean verifyRoot(
            JsonNode root,
            String expectedCommit,
            Path jar,
            DataPullManagedContractPolicy policy
    )
            throws java.io.IOException {
        if (root == null || !root.isObject() || !exactFields(root, ROOT_FIELDS)
                || !SCHEMA.equals(text(root, "schema"))
                || !TYPE.equals(text(root, "type"))
                || !expectedCommit.equals(text(root, "manifest_commit"))
                || !DataPullManagedEvidenceTopology.sha256File(jar).equals(
                        text(root, "candidate_jar_sha256"))) return false;
        JsonNode evidence = root.get("evidence");
        if (evidence == null || !evidence.isArray()
                || evidence.size() != DataPullManagedContractPolicy.requirements().size()) {
            return false;
        }
        Set<DataPullRuntimeReleaseRequirement> seen = new HashSet<>();
        boolean requestedVerified = false;
        for (JsonNode item : evidence) {
            EvidenceItem verifiedItem = verifyItem(item);
            if (verifiedItem == null || !seen.add(verifiedItem.requirement)) return false;
            if (requirement == verifiedItem.requirement) {
                requestedVerified = policy.approves(
                        requirement, verifiedItem.sourceIdentitySha256
                );
            }
        }
        return seen.equals(DataPullManagedContractPolicy.requirements())
                && requestedVerified;
    }

    private EvidenceItem verifyItem(JsonNode item) {
        try {
            if (item == null || !item.isObject() || !exactFields(item, ITEM_FIELDS)) {
                return null;
            }
            DataPullRuntimeReleaseRequirement itemRequirement =
                    DataPullRuntimeReleaseRequirement.valueOf(text(item, "requirement"));
            String sourceIdentity = text(item, "source_identity_sha256");
            if (!DataPullManagedContractPolicy.requirements().contains(itemRequirement)
                    || !STATUS.equals(text(item, "status"))
                    || !DataPullManagedContractPolicy.sourceKind(itemRequirement).equals(
                            text(item, "source_kind"))
                    || !matches(sourceIdentity, SHA256)) return null;
            Instant verifiedAt = Instant.parse(text(item, "verified_at"));
            Instant expiresAt = Instant.parse(text(item, "expires_at"));
            Instant now = clock.instant();
            Duration validity = Duration.between(verifiedAt, expiresAt);
            if (validity.isNegative() || validity.isZero()
                    || validity.compareTo(MAX_VALIDITY) > 0
                    || verifiedAt.isAfter(now.plusSeconds(30))
                    || !expiresAt.isAfter(now)) return null;
            return new EvidenceItem(itemRequirement, sourceIdentity);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    static String sourceKind(DataPullRuntimeReleaseRequirement requirement) {
        return DataPullManagedContractPolicy.sourceKind(requirement);
    }

    private String value(String name) {
        String value = environment.getProperty(name);
        return value == null ? "" : value.trim();
    }

    private boolean environmentFileBinds(
            Path app,
            Path jar,
            Path file,
            Path attestation,
            String commit,
            String evidenceSha
    ) throws java.io.IOException {
        List<String> lines = Files.readAllLines(
                app.resolve(".env"), StandardCharsets.UTF_8
        );
        return exactBinding(lines, "NUONO_NEXT_APP_DIR", app.toString())
                && exactBinding(lines, "NUONO_NEXT_JAR", jar.toString())
                && exactBinding(lines, FILE, file.toString())
                && exactBinding(lines, SHA, evidenceSha)
                && exactBinding(lines, ENV_ATTESTATION, attestation.toString())
                && exactBinding(
                        lines,
                        DataPullManagedReleaseProvenanceEvidence.EXPECTED_COMMIT,
                        commit
                );
    }

    private boolean exactBinding(List<String> lines, String key, String expected) {
        String prefix = key + "=";
        int matches = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (!line.startsWith(prefix)) continue;
            if (!line.equals(prefix + expected)) return false;
            matches++;
        }
        return matches == 1;
    }

    private Path absolute(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalArgumentException("evidence path is relative");
        return path.normalize();
    }

    private static boolean exactFields(JsonNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        return expected.equals(actual);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || !value.isTextual() ? "" : value.textValue();
    }

    private static boolean matches(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches();
    }

    private static final class EvidenceItem {
        private final DataPullRuntimeReleaseRequirement requirement;
        private final String sourceIdentitySha256;

        private EvidenceItem(
                DataPullRuntimeReleaseRequirement requirement,
                String sourceIdentitySha256
        ) {
            this.requirement = requirement;
            this.sourceIdentitySha256 = sourceIdentitySha256;
        }
    }
}
