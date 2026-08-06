package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskScopeSnapshot;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical DP-08 task-scope payload codec; decoding never consults the live catalog. */
final class Dp08ScopeSnapshotCodec {
    static final String KEYWORD_V1 = "DP08_KEYWORD_V1";
    static final String LIST_TARGET_V1 = "DP08_LIST_TARGET_V1";

    private final ObjectMapper objectMapper;

    Dp08ScopeSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    String encode(Dp08KeywordScope scope) {
        Dp08KeywordScope value = Objects.requireNonNull(scope, "scope");
        ObjectNode node = objectMapper.createObjectNode();
        node.put("ownerUserId", value.getOwnerUserId());
        putNullableLong(node, "logicalStoreId", value.getLogicalStoreId());
        node.put("watchProductId", value.getWatchProductId());
        node.put("keywordId", value.getKeywordId());
        node.put("storeCode", value.getStoreCode());
        node.put("siteCode", value.getSiteCode());
        node.put("keyword", value.getKeyword());
        node.put("locale", value.getLocale());
        node.put("stableScopeKey", value.getStableScopeKey());
        ArrayNode trackedProducts = node.putArray("trackedProducts");
        value.getTrackedProducts().stream()
                .sorted(Comparator.comparing(Dp08TrackedProduct::getSubjectType)
                        .thenComparing(Dp08TrackedProduct::getNoonProductCode))
                .forEach((product) -> {
                    ObjectNode item = trackedProducts.addObject();
                    item.put("subjectType", product.getSubjectType().name());
                    putNullableLong(item, "competitorProductId", product.getCompetitorProductId());
                    item.put("noonProductCode", product.getNoonProductCode());
                });
        return write(node);
    }

    String encode(Dp08ListTarget target) {
        Dp08ListTarget value = Objects.requireNonNull(target, "target");
        ObjectNode node = objectMapper.createObjectNode();
        node.put("ownerUserId", value.getOwnerUserId());
        putNullableLong(node, "logicalStoreId", value.getLogicalStoreId());
        node.put("storeCode", value.getStoreCode());
        node.put("siteCode", value.getSiteCode());
        node.put("noonProductCode", value.getNoonProductCode());
        node.put("stableScopeKey", value.getStableScopeKey());
        node.put("factDate", value.getFactDate().toString());
        node.put("exactSearchRequired", value.isExactSearchRequired());
        ArrayNode references = node.putArray("references");
        value.getReferences().stream()
                .sorted(Comparator.comparingLong(Dp08ListTarget.Reference::getWatchProductId)
                        .thenComparing(
                                Dp08ListTarget.Reference::getCompetitorProductId,
                                Comparator.nullsFirst(Comparator.naturalOrder())
                        ))
                .forEach((reference) -> {
                    ObjectNode item = references.addObject();
                    item.put("watchProductId", reference.getWatchProductId());
                    putNullableLong(item, "competitorProductId", reference.getCompetitorProductId());
                });
        return write(node);
    }

    Dp08KeywordScope decodeKeyword(DataPullTask task) {
        JsonNode node = read(DataPullTaskScopeSnapshot.requirePayload(
                task, OperationCode.DP08A, KEYWORD_V1
        ));
        List<Dp08TrackedProduct> trackedProducts = new ArrayList<>();
        JsonNode tracked = node.get("trackedProducts");
        if (tracked == null || !tracked.isArray()) {
            throw new IllegalStateException("DP-08 keyword tracked products are missing");
        }
        for (JsonNode item : tracked) {
            trackedProducts.add(new Dp08TrackedProduct(
                    subjectType(item), nullableLong(item, "competitorProductId"),
                    text(item, "noonProductCode")
            ));
        }
        Dp08KeywordScope result = new Dp08KeywordScope(
                longValue(node, "ownerUserId"), nullableLong(node, "logicalStoreId"),
                longValue(node, "watchProductId"), longValue(node, "keywordId"),
                text(node, "storeCode"), text(node, "siteCode"),
                text(node, "keyword"), text(node, "locale"),
                text(node, "stableScopeKey"), trackedProducts
        );
        requireTaskIdentity(task, result.toDataPullScope());
        return result;
    }

    Dp08ListTarget decodeListTarget(DataPullTask task) {
        JsonNode node = read(DataPullTaskScopeSnapshot.requirePayload(
                task, OperationCode.DP08B, LIST_TARGET_V1
        ));
        List<Dp08ListTarget.Reference> references = new ArrayList<>();
        JsonNode items = node.get("references");
        if (items == null || !items.isArray()) {
            throw new IllegalStateException("DP-08 list scope references are missing");
        }
        for (JsonNode item : items) {
            references.add(new Dp08ListTarget.Reference(
                    longValue(item, "watchProductId"),
                    nullableLong(item, "competitorProductId")
            ));
        }
        Dp08ListTarget result = new Dp08ListTarget(
                longValue(node, "ownerUserId"), nullableLong(node, "logicalStoreId"),
                text(node, "storeCode"), text(node, "siteCode"),
                text(node, "noonProductCode"), text(node, "stableScopeKey"),
                LocalDate.parse(text(node, "factDate")),
                booleanValue(node, "exactSearchRequired"), references
        );
        requireTaskIdentity(task, result.toDataPullScope());
        return result;
    }

    private void requireTaskIdentity(
            DataPullTask task,
            com.nuono.next.datapull.orchestration.DataPullScope scope
    ) {
        if (!Objects.equals(task.getScopeKey(), scope.getStableScopeKey())
                || !Objects.equals(task.getOwnerUserId(), scope.getOwnerUserId())
                || !Objects.equals(task.getLogicalStoreId(), scope.getLogicalStoreId())
                || !Objects.equals(task.getAccountKey(), scope.getAccountKey())
                || !Objects.equals(task.getStoreCode(), scope.getStoreCode())
                || !Objects.equals(task.getSiteCode(), scope.getSiteCode())) {
            throw new IllegalStateException("DP-08 task scope snapshot identity drift");
        }
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception invalid) {
            throw new IllegalStateException("DP-08 task scope snapshot is not valid JSON", invalid);
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception invalid) {
            throw new IllegalStateException("DP-08 scope snapshot cannot be encoded", invalid);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("DP-08 task scope has invalid " + field);
        }
        return value.textValue();
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() < 1L) {
            throw new IllegalStateException("DP-08 task scope has invalid " + field);
        }
        return value.longValue();
    }

    private static Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : longValue(node, field);
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("DP-08 task scope has invalid " + field);
        }
        return value.booleanValue();
    }

    private static Dp08TrackedProduct.SubjectType subjectType(JsonNode node) {
        try {
            return Dp08TrackedProduct.SubjectType.valueOf(text(node, "subjectType"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("DP-08 task scope has invalid subjectType", invalid);
        }
    }

    private static void putNullableLong(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
