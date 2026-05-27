package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class ProductSnapshotReaderService {

    private final ProductNoonSnapshotFetcher snapshotFetcher;
    private final ObjectMapper objectMapper;

    public ProductSnapshotReaderService(ProductNoonSnapshotFetcher snapshotFetcher) {
        this(snapshotFetcher, new ObjectMapper());
    }

    ProductSnapshotReaderService(ProductNoonSnapshotFetcher snapshotFetcher, ObjectMapper objectMapper) {
        this.snapshotFetcher = snapshotFetcher;
        this.objectMapper = objectMapper;
    }

    public ProductMasterSnapshotView readNoonSnapshot(ProductMasterFetchCommand command, String reason) {
        return readNoonSnapshot(command, reason, null);
    }

    public ProductMasterSnapshotView readNoonSnapshot(
            ProductMasterFetchCommand command,
            String reason,
            ProductMasterSnapshotView siteOfferReuseSeed
    ) {
        return snapshotFetcher.fetch(command, reason, siteOfferReuseSeed);
    }

    public ProductBaselineRefreshDecision refreshBaseline(
            ProductMasterSnapshotView liveSnapshot,
            ProductMasterSnapshotView currentBaseline,
            ProductMasterSnapshotView currentDraft,
            String syncMergePolicy
    ) {
        ProductMasterSnapshotView refreshedBaseline = copySnapshot(liveSnapshot);
        if (hasDraftChanges(currentDraft, currentBaseline) && isKeepDraftSyncPolicy(syncMergePolicy)) {
            ProductMasterSnapshotView hydratedDraft = copySnapshot(currentDraft);
            hydrateProjectionOnlyFields(hydratedDraft, refreshedBaseline);
            if (sameSnapshot(hydratedDraft, refreshedBaseline)) {
                return new ProductBaselineRefreshDecision(
                        refreshedBaseline,
                        hydratedDraft,
                        "synced",
                        "已按 Noon 当前版本刷新基线，当前草稿与最新基线一致。"
                );
            }
            return new ProductBaselineRefreshDecision(
                    refreshedBaseline,
                    hydratedDraft,
                    "draft",
                    "已按 Noon 当前版本刷新基线，并保留本地草稿。"
            );
        }
        return new ProductBaselineRefreshDecision(
                refreshedBaseline,
                copySnapshot(refreshedBaseline),
                "synced",
                "已按 Noon 当前版本刷新基线，并使用 Noon 内容覆盖本地草稿。"
        );
    }

    public Map<String, ProductFieldRead<?>> classifySnapshotFieldReads(
            Map<String, Object> incoming,
            Collection<String> fields,
            Map<String, String> readErrors
    ) {
        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        if (fields == null) {
            return reads;
        }
        for (String field : fields) {
            if (field == null) {
                continue;
            }
            if (readErrors != null && readErrors.containsKey(field)) {
                reads.put(field, ProductFieldRead.error(readErrors.get(field)));
            } else if (incoming != null && incoming.containsKey(field)) {
                reads.put(field, ProductFieldRead.value(incoming.get(field)));
            } else {
                reads.put(field, ProductFieldRead.absent());
            }
        }
        return reads;
    }

    private boolean hasDraftChanges(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
        return draft != null && baseline != null && !sameSnapshot(draft, baseline);
    }

    private boolean sameSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right) {
        return objectMapper.valueToTree(refreshComparableSnapshot(left))
                .equals(objectMapper.valueToTree(refreshComparableSnapshot(right)));
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return ProductPublishSnapshotSupport.copySnapshot(objectMapper, source);
    }

    private Map<String, Object> refreshComparableSnapshot(ProductMasterSnapshotView snapshot) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (snapshot == null) {
            return comparable;
        }
        comparable.put("identity", snapshot.getIdentity());
        comparable.put("taxonomy", snapshot.getTaxonomy());
        comparable.put("content", snapshot.getContent());
        comparable.put("keyAttributes", snapshot.getKeyAttributes());
        comparable.put("group", snapshot.getGroup());
        comparable.put("variants", snapshot.getVariants());
        comparable.put(
                "siteOffers",
                ProductPublishSnapshotSupport.siteOfferComparableList(snapshot, null, true)
        );
        return comparable;
    }

    private boolean isKeepDraftSyncPolicy(String syncMergePolicy) {
        String normalized = normalize(syncMergePolicy);
        return "keep_draft".equalsIgnoreCase(normalized)
                || "keep-local".equalsIgnoreCase(normalized)
                || "keep_local".equalsIgnoreCase(normalized);
    }

    private void hydrateProjectionOnlyFields(ProductMasterSnapshotView target, ProductMasterSnapshotView source) {
        if (target == null || source == null) {
            return;
        }
        Map<String, Map<String, Object>> sourceOffers =
                ProductPublishSnapshotSupport.siteOfferMap(source.getSiteOffers());
        List<Map<String, Object>> targetOffers = target.getSiteOffers();
        if (targetOffers != null) {
            for (Map<String, Object> targetOffer : targetOffers) {
                String storeCode = ProductPublishSnapshotSupport.siteOfferCode(targetOffer);
                Map<String, Object> sourceOffer = sourceOffers.get(storeCode);
                if (sourceOffer == null) {
                    continue;
                }
                copyProjectionOnlyOfferFields(targetOffer, sourceOffer);
            }
        }
        copyProjectionOnlyOfferFields(target.getPricing(), source.getPricing());
        target.setPlatformSignals(new LinkedHashMap<>(source.getPlatformSignals()));
    }

    private void copyProjectionOnlyOfferFields(Map<String, Object> target, Map<String, Object> source) {
        copyProjectionOnlyOfferField(target, source, "finalPrice");
        copyProjectionOnlyOfferField(target, source, "finalPriceSource");
        copyProjectionOnlyOfferField(target, source, "activePromotionCode");
        copyProjectionOnlyOfferField(target, source, "activePromotionName");
        copyProjectionOnlyOfferField(target, source, "activePromotionUrl");
        copyProjectionOnlyOfferField(target, source, "barcode");
        copyProjectionOnlyOfferField(target, source, "fbnStock");
        copyProjectionOnlyOfferField(target, source, "supermallStock");
        copyProjectionOnlyOfferField(target, source, "fbpStock");
        copyProjectionOnlyOfferField(target, source, "statusCode");
        copyProjectionOnlyOfferField(target, source, "deliveryMethod");
        copyProjectionOnlyOfferField(target, source, "isWinningBuybox");
        copyProjectionOnlyOfferField(target, source, "viewsCount");
        copyProjectionOnlyOfferField(target, source, "unitsSold");
        copyProjectionOnlyOfferField(target, source, "salesAmount");
        copyProjectionOnlyOfferField(target, source, "salesCurrency");
    }

    private void copyProjectionOnlyOfferField(Map<String, Object> target, Map<String, Object> source, String fieldName) {
        if (target == null || source == null || !source.containsKey(fieldName)) {
            return;
        }
        Object value = source.get(fieldName);
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
