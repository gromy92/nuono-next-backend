package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductSnapshotHydrator {

    private final ObjectMapper objectMapper;

    ProductSnapshotHydrator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ProductMasterSnapshotView sanitizeSnapshot(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView fallback
    ) {
        if (snapshot == null) {
            return copySnapshot(fallback);
        }
        ProductMasterSnapshotView sanitized = copySnapshot(snapshot);
        sanitized.setMode("local-db");
        sanitized.setReady(true);
        if (sanitized.getWarnings() == null) {
            sanitized.setWarnings(new ArrayList<>());
        }
        sanitized.setWarnings(userVisibleWarnings(sanitized.getWarnings()));
        if (sanitized.getMissingCoreTables() == null) {
            sanitized.setMissingCoreTables(new ArrayList<>());
        }
        if (sanitized.getMissingOperationalKeys() == null) {
            sanitized.setMissingOperationalKeys(new ArrayList<>());
        }
        hydrateProjectionOnlyFields(sanitized, fallback);
        return sanitized;
    }

    ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    List<Map<String, Object>> copyRecordList(List<Map<String, Object>> source) {
        return source == null
                ? new ArrayList<>()
                : objectMapper.convertValue(source, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Map.class));
    }

    void hydrateProjectionOnlyFields(ProductMasterSnapshotView target, ProductMasterSnapshotView source) {
        if (target == null || source == null) {
            return;
        }
        Map<String, Map<String, Object>> sourceOffers = siteOfferMap(source.getSiteOffers());
        for (Map<String, Object> targetOffer : target.getSiteOffers()) {
            String storeCode = siteOfferCode(targetOffer);
            Map<String, Object> sourceOffer = sourceOffers.get(storeCode);
            if (sourceOffer == null) {
                continue;
            }
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "finalPrice");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "finalPriceSource");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "activePromotionCode");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "activePromotionName");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "activePromotionUrl");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "barcode");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "fbnStock");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "supermallStock");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "fbpStock");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "statusCode");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "deliveryMethod");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "isWinningBuybox");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "viewsCount");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "unitsSold");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "salesAmount");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "salesCurrency");
        }
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "finalPrice");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "finalPriceSource");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "activePromotionCode");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "activePromotionName");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "activePromotionUrl");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "barcode");
        target.setPlatformSignals(new LinkedHashMap<>(source.getPlatformSignals()));
    }

    List<String> mergeWarnings(List<String> baseWarnings, List<String> extraWarnings) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (baseWarnings != null) {
            merged.addAll(userVisibleWarnings(baseWarnings));
        }
        if (extraWarnings != null) {
            merged.addAll(userVisibleWarnings(extraWarnings));
        }
        return new ArrayList<>(merged);
    }

    List<String> userVisibleWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> visible = new ArrayList<>();
        for (String warning : warnings) {
            if (!StringUtils.hasText(warning) || isNonBlockingNoonRealtimeWarning(warning)) {
                continue;
            }
            visible.add(warning);
        }
        return visible;
    }

    void clearResolvedOperationalWarnings(ProductMasterSnapshotView snapshot) {
        if (snapshot == null) {
            return;
        }
        boolean hasPartnerSku = StringUtils.hasText(textValue(snapshot.getIdentity().get("partnerSku")));
        boolean hasPskuCode = StringUtils.hasText(textValue(snapshot.getIdentity().get("pskuCode")));
        if (snapshot.getMissingOperationalKeys() != null) {
            snapshot.getMissingOperationalKeys().removeIf((key) ->
                    (hasPartnerSku && "partnerSku".equalsIgnoreCase(String.valueOf(key)))
                            || (hasPskuCode && "pskuCode".equalsIgnoreCase(String.valueOf(key)))
            );
        }
        if (snapshot.getWarnings() != null) {
            snapshot.getWarnings().removeIf((warning) ->
                    (hasPartnerSku && hasPskuCode && warning.contains("当前索引缺少 partnerSku / pskuCode"))
                            || (hasPartnerSku && warning.contains("当前索引缺少 partnerSku"))
                            || (hasPskuCode && warning.contains("当前索引缺少 pskuCode"))
                            || (hasPartnerSku && warning.contains("当前详情还缺少 partnerSku"))
                            || isNonBlockingNoonRealtimeWarning(warning)
            );
        }
        if (snapshot.getMissingOperationalKeys() == null || snapshot.getMissingOperationalKeys().isEmpty()) {
            snapshot.setDegraded(false);
        }
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

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            if (siteOffer != null) {
                map.put(siteOfferCode(siteOffer), new LinkedHashMap<>(siteOffer));
            }
        }
        return map;
    }

    private boolean isNonBlockingNoonRealtimeWarning(String warning) {
        if (!StringUtils.hasText(warning)) {
            return false;
        }
        String normalized = warning.toLowerCase(Locale.ROOT);
        return normalized.contains("价格信息失败")
                || normalized.contains("库存摘要失败")
                || normalized.contains("读取 fulltype 模板失败")
                || normalized.contains("fulltype 模板实时读取失败")
                || normalized.contains("没有返回 product_fulltype")
                || normalized.contains("类目模板读取已跳过");
    }

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
