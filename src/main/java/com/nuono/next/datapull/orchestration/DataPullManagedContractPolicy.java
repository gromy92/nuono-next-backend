package com.nuono.next.datapull.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Immutable candidate-Jar allowlist for externally governed provider evidence. */
final class DataPullManagedContractPolicy {
    static final String ENTRY =
            "BOOT-INF/classes/META-INF/nuono/dp-runtime-provider-contract-policy-v1.json";
    static final String SCHEMA = "nuono.dp-runtime-provider-contract-policy/v1";
    private static final int MAX_BYTES = 65_536;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ROOT_FIELDS = Set.of("schema", "requirements");
    private static final Set<String> ITEM_FIELDS = Set.of(
            "requirement", "source_kind", "approved_source_sha256"
    );
    private static final Map<DataPullRuntimeReleaseRequirement, String> SOURCES = sources();

    private final Map<DataPullRuntimeReleaseRequirement, Set<String>> approvedSources;

    private DataPullManagedContractPolicy(
            Map<DataPullRuntimeReleaseRequirement, Set<String>> approvedSources
    ) {
        this.approvedSources = Map.copyOf(approvedSources);
    }

    static DataPullManagedContractPolicy load(Path candidateJar, ObjectMapper mapper) {
        try (ZipFile jar = new ZipFile(candidateJar.toFile())) {
            ZipEntry entry = jar.getEntry(ENTRY);
            if (entry == null || entry.isDirectory() || entry.getSize() <= 0
                    || entry.getSize() > MAX_BYTES) {
                throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_MISSING");
            }
            byte[] payload;
            try (InputStream input = jar.getInputStream(entry)) {
                payload = input.readNBytes(MAX_BYTES + 1);
            }
            if (payload.length == 0 || payload.length > MAX_BYTES) {
                throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_SIZE_INVALID");
            }
            return parse(mapper.readTree(payload));
        } catch (java.io.IOException invalid) {
            throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_INVALID", invalid);
        }
    }

    boolean approves(
            DataPullRuntimeReleaseRequirement requirement,
            String sourceIdentitySha256
    ) {
        return approvedSources.getOrDefault(requirement, Set.of())
                .contains(sourceIdentitySha256);
    }

    static String sourceKind(DataPullRuntimeReleaseRequirement requirement) {
        return SOURCES.get(requirement);
    }

    static Set<DataPullRuntimeReleaseRequirement> requirements() {
        return SOURCES.keySet();
    }

    private static DataPullManagedContractPolicy parse(JsonNode root) {
        if (root == null || !root.isObject() || !exactFields(root, ROOT_FIELDS)
                || !SCHEMA.equals(text(root, "schema"))) {
            throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_SCHEMA_INVALID");
        }
        JsonNode requirements = root.get("requirements");
        if (requirements == null || !requirements.isArray()
                || requirements.size() != SOURCES.size()) {
            throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_COHORT_INVALID");
        }
        EnumMap<DataPullRuntimeReleaseRequirement, Set<String>> approved =
                new EnumMap<>(DataPullRuntimeReleaseRequirement.class);
        for (JsonNode item : requirements) {
            if (item == null || !item.isObject() || !exactFields(item, ITEM_FIELDS)) {
                throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_ITEM_INVALID");
            }
            DataPullRuntimeReleaseRequirement requirement =
                    DataPullRuntimeReleaseRequirement.valueOf(text(item, "requirement"));
            if (!SOURCES.containsKey(requirement)
                    || !SOURCES.get(requirement).equals(text(item, "source_kind"))
                    || approved.containsKey(requirement)) {
                throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_SOURCE_INVALID");
            }
            approved.put(requirement, approvedDigests(item.get("approved_source_sha256")));
        }
        if (!approved.keySet().equals(SOURCES.keySet())) {
            throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_INCOMPLETE");
        }
        return new DataPullManagedContractPolicy(approved);
    }

    private static Set<String> approvedDigests(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_DIGESTS_INVALID");
        }
        List<String> ordered = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || !SHA256.matcher(value.textValue()).matches()) {
                throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_DIGEST_INVALID");
            }
            ordered.add(value.textValue());
        }
        Set<String> unique = new HashSet<>(ordered);
        List<String> canonical = new ArrayList<>(unique);
        canonical.sort(String::compareTo);
        if (unique.size() != ordered.size() || !canonical.equals(ordered)) {
            throw new IllegalArgumentException("DP_RUNTIME_CONTRACT_POLICY_ORDER_INVALID");
        }
        return Set.copyOf(unique);
    }

    private static boolean exactFields(JsonNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        return expected.equals(actual);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? "" : value.textValue();
    }

    private static Map<DataPullRuntimeReleaseRequirement, String> sources() {
        Map<DataPullRuntimeReleaseRequirement, String> sources = new HashMap<>();
        sources.put(DataPullRuntimeReleaseRequirement.DP04_STABLE_SNAPSHOT,
                "PROVIDER_SNAPSHOT_AUTHORITY");
        sources.put(DataPullRuntimeReleaseRequirement.DP06_COMPLETE_CAMPAIGN_ENUMERATION,
                "PROVIDER_COMPLETE_CAMPAIGN_ENUMERATION");
        sources.put(DataPullRuntimeReleaseRequirement.DP07A_STABLE_SNAPSHOT,
                "PROVIDER_SNAPSHOT_AUTHORITY");
        sources.put(DataPullRuntimeReleaseRequirement.DP10_MODIFIED_TIME_VISIBILITY_CONTRACT,
                "PROVIDER_MODIFIED_TIME_VISIBILITY");
        return Map.copyOf(sources);
    }
}
