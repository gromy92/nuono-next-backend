package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.snapshot.SnapshotItemDescriptor;
import com.nuono.next.datapull.snapshot.SnapshotPayloadCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Versioned deterministic staging payload for one complete provider rank page. */
final class Dp08RankPageCodec
        implements SnapshotPayloadCodec<NoonSearchPage>, SnapshotItemDescriptor<NoonSearchPage> {
    private static final int VERSION = 1;
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "page");
    private final ObjectMapper objectMapper;

    Dp08RankPageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    @Override
    public String encode(NoonSearchPage page) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", VERSION);
            root.set("page", objectMapper.valueToTree(page));
            return objectMapper.writeValueAsString(root);
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-08 rank page cannot be encoded", failure);
        }
    }

    @Override
    public NoonSearchPage decode(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            requireRoot(root);
            return objectMapper.treeToValue(root.get("page"), NoonSearchPage.class);
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("DP-08 rank page payload is invalid", failure);
        }
    }

    @Override
    public String stableIdentity(NoonSearchPage page) {
        Integer pageNo = page == null ? null : page.getProviderPage();
        if (pageNo == null || pageNo < 1) {
            throw new IllegalArgumentException("rank page has no provider page identity");
        }
        return "dp08-rank-page:" + pageNo;
    }

    @Override
    public String stableContentFingerprint(NoonSearchPage page) {
        return sha256(encode(page));
    }

    private void requireRoot(JsonNode root) {
        if (root == null || !root.isObject()
                || root.path("schemaVersion").asInt(-1) != VERSION
                || !root.hasNonNull("page")) {
            throw new IllegalArgumentException("unsupported DP-08 rank page payload");
        }
        Set<String> fields = new HashSet<>();
        Iterator<String> names = root.fieldNames();
        names.forEachRemaining(fields::add);
        if (!ROOT_FIELDS.equals(fields)) {
            throw new IllegalArgumentException("unexpected DP-08 rank page fields");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }
}
