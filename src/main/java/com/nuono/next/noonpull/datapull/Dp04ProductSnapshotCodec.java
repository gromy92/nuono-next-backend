package com.nuono.next.noonpull.datapull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.snapshot.SnapshotItemDescriptor;
import com.nuono.next.datapull.snapshot.SnapshotPayloadCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Strict versioned staging codec for the DP-04 projection payload. */
public final class Dp04ProductSnapshotCodec
        implements SnapshotPayloadCodec<Dp04ProductSnapshotItem>,
        SnapshotItemDescriptor<Dp04ProductSnapshotItem> {

    private static final int VERSION = 2;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion",
            "rowKind",
            "stableIdentity",
            "presencePartnerSku",
            "projection"
    );
    private final ObjectMapper objectMapper;

    public Dp04ProductSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    @Override
    public String encode(Dp04ProductSnapshotItem item) {
        try {
            Dp04ProductSnapshotItem value = java.util.Objects.requireNonNull(item, "item");
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", VERSION);
            root.put("rowKind", value.getRowKind());
            root.put("stableIdentity", value.getStableIdentity());
            root.put("presencePartnerSku", value.getPresencePartnerSku());
            root.set("projection", objectMapper.valueToTree(value.getStagedProjectionPayload()));
            return objectMapper.writeValueAsString(root);
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-04 item cannot be encoded", failure);
        }
    }

    @Override
    public Dp04ProductSnapshotItem decode(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            requireRoot(root);
            Map<String, Object> projection = objectMapper.convertValue(
                    root.get("projection"),
                    new TypeReference<Map<String, Object>>() { }
            );
            Dp04ProductSnapshotItem item = Dp04ProductSnapshotItem.fromStoredPayload(
                    root.path("rowKind").asText(),
                    root.path("stableIdentity").asText(),
                    root.path("presencePartnerSku").isNull()
                            ? null
                            : root.path("presencePartnerSku").asText(),
                    projection
            );
            if (!encode(item).equals(payload)) {
                throw new IllegalArgumentException("DP-04 staging payload is not canonical");
            }
            return item;
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-04 staging payload is invalid", failure);
        }
    }

    @Override
    public String stableIdentity(Dp04ProductSnapshotItem item) {
        return java.util.Objects.requireNonNull(item, "item").getStableIdentity();
    }

    @Override
    public String stableContentFingerprint(Dp04ProductSnapshotItem item) {
        return sha256(encode(item));
    }

    @Override
    public boolean isValidatedIdentityCandidate(Dp04ProductSnapshotItem item) {
        return java.util.Objects.requireNonNull(item, "item").isWritableProjection();
    }

    @Override
    public boolean isAbsenceReconciliationSafe(Dp04ProductSnapshotItem item) {
        return java.util.Objects.requireNonNull(item, "item").isAbsenceReconciliationSafe();
    }

    private void requireRoot(JsonNode root) {
        if (root == null || !root.isObject()
                || root.path("schemaVersion").asInt(-1) != VERSION
                || !root.path("rowKind").isTextual()
                || !root.path("stableIdentity").isTextual()
                || !(root.path("presencePartnerSku").isTextual()
                || root.path("presencePartnerSku").isNull())
                || !root.path("projection").isObject()) {
            throw new IllegalArgumentException("unsupported DP-04 staging payload");
        }
        Set<String> fields = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        names.forEachRemaining(fields::add);
        if (!ROOT_FIELDS.equals(fields)) {
            throw new IllegalArgumentException("unexpected DP-04 staging fields");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }
}
