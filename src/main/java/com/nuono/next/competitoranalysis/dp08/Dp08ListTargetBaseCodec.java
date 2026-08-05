package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical date-neutral payload for DP08B binding reconciliation. */
final class Dp08ListTargetBaseCodec {
    static final String PAYLOAD_TYPE = "DP08_LIST_TARGET_BASE_V1";
    private final ObjectMapper mapper;

    Dp08ListTargetBaseCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy();
    }

    String encode(Dp08ListTargetBase base) {
        ObjectNode node = mapper.createObjectNode();
        node.put("ownerUserId", base.ownerUserId());
        if (base.logicalStoreId() == null) node.putNull("logicalStoreId");
        else node.put("logicalStoreId", base.logicalStoreId());
        node.put("storeCode", base.storeCode());
        node.put("siteCode", base.siteCode());
        node.put("noonProductCode", base.noonProductCode());
        node.put("stableScopeKey", base.stableScopeKey());
        ArrayNode references = node.putArray("references");
        base.references().stream()
                .sorted(Comparator.comparingLong(Dp08ListTarget.Reference::getWatchProductId)
                        .thenComparing((item) -> item.getCompetitorProductId() == null
                                ? Long.MIN_VALUE : item.getCompetitorProductId()))
                .forEach((reference) -> {
                    ObjectNode item = references.addObject();
                    item.put("watchProductId", reference.getWatchProductId());
                    if (reference.getCompetitorProductId() == null) {
                        item.putNull("competitorProductId");
                    } else {
                        item.put("competitorProductId", reference.getCompetitorProductId());
                    }
                });
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception invalid) {
            throw new IllegalStateException("DP08B base payload encoding failed", invalid);
        }
    }

    Dp08ListTargetBase decode(String payload, java.time.LocalDateTime effectiveFromUtc) {
        try {
            JsonNode node = mapper.readTree(payload);
            List<Dp08ListTarget.Reference> references = new ArrayList<>();
            for (JsonNode item : node.path("references")) {
                JsonNode competitor = item.get("competitorProductId");
                references.add(new Dp08ListTarget.Reference(
                        item.path("watchProductId").longValue(),
                        competitor == null || competitor.isNull() ? null : competitor.longValue()
                ));
            }
            JsonNode logical = node.get("logicalStoreId");
            return new Dp08ListTargetBase(
                    node.path("ownerUserId").longValue(),
                    logical == null || logical.isNull() ? null : logical.longValue(),
                    node.path("storeCode").textValue(), node.path("siteCode").textValue(),
                    node.path("noonProductCode").textValue(),
                    node.path("stableScopeKey").textValue(), references, effectiveFromUtc
            );
        } catch (RuntimeException | java.io.IOException invalid) {
            throw new IllegalStateException("DP08B base payload decoding failed", invalid);
        }
    }
}
