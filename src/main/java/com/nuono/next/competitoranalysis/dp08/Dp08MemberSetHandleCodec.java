package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskScopeSnapshot;
import com.nuono.next.datapull.runtime.OperationCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Objects;

/** Canonical codec and content-addressing rule for compact DP08 member-set handles. */
public final class Dp08MemberSetHandleCodec {
    public static final String KEYWORD_TYPE = "DP08_KEYWORD_MEMBER_SET_V1";
    public static final String LIST_TYPE = "DP08_LIST_MEMBER_SET_V1";
    public static final int MAX_PAYLOAD_BYTES = 4_096;

    private final ObjectMapper objectMapper;

    public Dp08MemberSetHandleCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    public Dp08MemberSetHandle seal(
            Dp08MemberSetBase base,
            long memberCount,
            String orderedSha256,
            LocalDateTime effectiveFromUtc
    ) {
        String basePayload = encodeBase(base);
        String memberSetId = sha256(
                "DP08_MEMBER_SET_V1\0"
                        + basePayload + "\0"
                        + memberCount + "\0"
                        + orderedSha256 + "\0"
                        + effectiveFromUtc
        );
        return new Dp08MemberSetHandle(
                base,
                memberSetId,
                memberCount,
                orderedSha256
        );
    }

    public String encode(Dp08MemberSetHandle handle) {
        Dp08MemberSetHandle value = Objects.requireNonNull(handle, "handle");
        ObjectNode node = baseNode(value.base());
        node.put("memberSetId", value.getMemberSetId());
        node.put("memberCount", value.getMemberCount());
        node.put("memberOrderedSha256", value.getMemberOrderedSha256());
        return writeBounded(node);
    }

    String encodeBase(Dp08MemberSetBase base) {
        return writeBounded(baseNode(base));
    }

    Dp08MemberSetBase decodeBase(String payload) {
        return base(read(payload));
    }

    public Dp08MemberSetHandle decode(DataPullTask task) {
        OperationCode operation = Objects.requireNonNull(task, "task").getOperationCode();
        if (operation != OperationCode.DP08A && operation != OperationCode.DP08B) {
            throw new IllegalStateException("DP08 handle attached to another operation");
        }
        String payloadType = operation == OperationCode.DP08A
                ? KEYWORD_TYPE
                : LIST_TYPE;
        JsonNode node = read(DataPullTaskScopeSnapshot.requirePayload(
                task,
                operation,
                payloadType
        ));
        Dp08MemberSetHandle handle = new Dp08MemberSetHandle(
                base(node),
                text(node, "memberSetId"),
                positive(node, "memberCount"),
                text(node, "memberOrderedSha256")
        );
        requireTaskIdentity(task, handle);
        return handle;
    }

    private void requireTaskIdentity(
            DataPullTask task,
            Dp08MemberSetHandle handle
    ) {
        if (!task.getScopeKey().equals(handle.getStableScopeKey())
                || !task.getOwnerUserId().equals(handle.getOwnerUserId())
                || !Objects.equals(task.getLogicalStoreId(), handle.getLogicalStoreId())
                || !task.getStoreCode().equals(handle.getStoreCode())
                || !task.getSiteCode().equals(handle.getSiteCode())) {
            throw new IllegalStateException("DP08 member-set task identity drift");
        }
    }

    private ObjectNode baseNode(Dp08MemberSetBase value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("operationCode", value.operationCode().name());
        node.put("ownerUserId", value.ownerUserId());
        put(node, "logicalStoreId", value.logicalStoreId());
        put(node, "watchProductId", value.watchProductId());
        put(node, "keywordId", value.keywordId());
        node.put("storeCode", value.storeCode());
        node.put("siteCode", value.siteCode());
        put(node, "keyword", value.keyword());
        put(node, "locale", value.locale());
        node.put("noonProductCode", value.noonProductCode());
        node.put(
                "representativeWatchProductId",
                value.representativeWatchProductId()
        );
        put(
                node,
                "representativeCompetitorProductId",
                value.representativeCompetitorProductId()
        );
        node.put("stableScopeKey", value.stableScopeKey());
        return node;
    }

    private Dp08MemberSetBase base(JsonNode node) {
        return new Dp08MemberSetBase(
                OperationCode.valueOf(text(node, "operationCode")),
                positive(node, "ownerUserId"),
                nullableLong(node, "logicalStoreId"),
                nullableLong(node, "watchProductId"),
                nullableLong(node, "keywordId"),
                text(node, "storeCode"),
                text(node, "siteCode"),
                nullableText(node, "keyword"),
                nullableText(node, "locale"),
                text(node, "noonProductCode"),
                positive(node, "representativeWatchProductId"),
                nullableLong(node, "representativeCompetitorProductId"),
                text(node, "stableScopeKey")
        );
    }

    private String writeBounded(JsonNode node) {
        try {
            String value = objectMapper.writeValueAsString(node);
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new IllegalStateException("DP08 handle payload too large");
            }
            return value;
        } catch (IOException encodingFailure) {
            throw new IllegalStateException("DP08 handle encoding failed", encodingFailure);
        }
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException invalidJson) {
            throw new IllegalStateException("DP08 handle is invalid JSON", invalidJson);
        }
    }

    private void put(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void put(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
            throw new IllegalStateException("missing " + field);
        }
        return value.textValue();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : text(node, field);
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : positive(node, field);
    }

    private long positive(JsonNode node, String field) {
        long value = node.path(field).longValue();
        if (value < 1L) {
            throw new IllegalStateException("invalid " + field);
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) {
                output.append(String.format("%02x", item & 255));
            }
            return output.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
