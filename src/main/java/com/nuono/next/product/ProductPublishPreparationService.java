package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class ProductPublishPreparationService {

    private static final ZoneId PRODUCT_MANAGEMENT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_SALE_WINDOW_YEARS = 10;
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final List<String> PUBLISH_OFFER_FIELDS = List.of(
            "site",
            "pskuCode",
            "offerCode",
            "currency",
            "price",
            "salePrice",
            "saleStart",
            "saleEnd",
            "priceMin",
            "priceMax",
            "isActive",
            "idWarranty"
    );

    private static final List<String> PRICING_MIRROR_FIELDS = List.of(
            "price",
            "salePrice",
            "saleStart",
            "saleEnd",
            "priceMin",
            "priceMax",
            "isActive",
            "idWarranty",
            "offerNote"
    );

    private final ObjectMapper objectMapper;
    private final ProductDraftMergePolicy productDraftMergePolicy;

    public ProductPublishPreparationService(
            ObjectMapper objectMapper,
            ProductDraftMergePolicy productDraftMergePolicy
    ) {
        this.objectMapper = objectMapper;
        this.productDraftMergePolicy = productDraftMergePolicy;
    }

    public ProductMasterSnapshotView prepareForPublish(
            ProductMasterSnapshotView requestedSnapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (requestedSnapshot == null || baseline == null) {
            return requestedSnapshot;
        }
        ProductMasterSnapshotView preparedSnapshot =
                ProductPublishSnapshotSupport.copySnapshot(objectMapper, requestedSnapshot);
        hydrateWritableOfferFieldsForPublish(preparedSnapshot, baseline, currentSiteCode);
        return preparedSnapshot;
    }

    private void hydrateWritableOfferFieldsForPublish(
            ProductMasterSnapshotView target,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (target == null || baseline == null || target.getSiteOffers() == null) {
            return;
        }
        Map<String, Map<String, Object>> baselineOffers =
                ProductPublishSnapshotSupport.siteOfferMap(baseline.getSiteOffers());
        String normalizedCurrentSiteCode = ProductPublishSnapshotSupport.normalize(currentSiteCode);
        for (Map<String, Object> targetOffer : target.getSiteOffers()) {
            if (targetOffer == null) {
                continue;
            }
            String siteCode = ProductPublishSnapshotSupport.siteOfferCode(targetOffer);
            if (StringUtils.hasText(normalizedCurrentSiteCode)
                    && !normalizedCurrentSiteCode.equalsIgnoreCase(siteCode)) {
                continue;
            }
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer == null) {
                continue;
            }
            hydrateWritableOfferFieldsForPublish(targetOffer, baselineOffer);
            if (isPublishOfferChanged(targetOffer, baselineOffer)) {
                applyDefaultSaleWindowForPublish(targetOffer);
            }
            mirrorCurrentOfferToPricing(target, targetOffer, normalizedCurrentSiteCode);
        }
    }

    private void hydrateWritableOfferFieldsForPublish(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer
    ) {
        Map<String, Object> hydrated = productDraftMergePolicy.hydrateMissingOfferFieldsForPublish(
                targetOffer,
                baselineOffer,
                PUBLISH_OFFER_FIELDS
        );
        targetOffer.clear();
        targetOffer.putAll(hydrated);
        copyBaselineOfferFieldIfMissing(targetOffer, baselineOffer, "offerNote");
    }

    private void applyDefaultSaleWindowForPublish(Map<String, Object> siteOffer) {
        if (ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("salePrice")) == null) {
            return;
        }
        boolean missingSaleStart =
                !StringUtils.hasText(ProductPublishSnapshotSupport.textValue(siteOffer.get("saleStart")));
        boolean missingSaleEnd =
                !StringUtils.hasText(ProductPublishSnapshotSupport.textValue(siteOffer.get("saleEnd")));
        if (!missingSaleStart && !missingSaleEnd) {
            return;
        }
        Map<String, String> saleWindow = saleWindowForPublish(siteOffer);
        if (missingSaleStart && StringUtils.hasText(saleWindow.get("saleStart"))) {
            siteOffer.put("saleStart", saleWindow.get("saleStart"));
        }
        if (missingSaleEnd && StringUtils.hasText(saleWindow.get("saleEnd"))) {
            siteOffer.put("saleEnd", saleWindow.get("saleEnd"));
        }
    }

    private Map<String, String> saleWindowForPublish(Map<String, Object> siteOffer) {
        Map<String, String> saleWindow = new LinkedHashMap<>();
        if (siteOffer == null) {
            return saleWindow;
        }

        String saleStart = ProductPublishSnapshotSupport.normalizeOfferDateForNoon(siteOffer.get("saleStart"));
        String saleEnd = ProductPublishSnapshotSupport.normalizeOfferDateForNoon(siteOffer.get("saleEnd"));
        if (ProductPublishSnapshotSupport.asBigDecimal(siteOffer.get("salePrice")) != null) {
            LocalDate today = LocalDate.now(PRODUCT_MANAGEMENT_ZONE);
            if (!StringUtils.hasText(saleStart)) {
                saleStart = today.format(NOON_OFFER_DATE_FORMATTER);
            }
            if (!StringUtils.hasText(saleEnd)) {
                saleEnd = today.plusYears(DEFAULT_SALE_WINDOW_YEARS).format(NOON_OFFER_DATE_FORMATTER);
            }
        }

        if (StringUtils.hasText(saleStart)) {
            saleWindow.put("saleStart", saleStart);
        }
        if (StringUtils.hasText(saleEnd)) {
            saleWindow.put("saleEnd", saleEnd);
        }
        return saleWindow;
    }

    private boolean isPublishOfferChanged(Map<String, Object> siteOffer, Map<String, Object> baselineOffer) {
        return !objectMapper.valueToTree(ProductPublishSnapshotSupport.siteOfferComparable(siteOffer, false))
                .equals(objectMapper.valueToTree(ProductPublishSnapshotSupport.siteOfferComparable(baselineOffer, false)));
    }

    private void mirrorCurrentOfferToPricing(
            ProductMasterSnapshotView target,
            Map<String, Object> siteOffer,
            String currentSiteCode
    ) {
        String siteCode = ProductPublishSnapshotSupport.siteOfferCode(siteOffer);
        Map<String, Object> storeContext = target.getStoreContext() == null ? Map.of() : target.getStoreContext();
        String pricingSiteCode = ProductPublishSnapshotSupport.firstNonBlank(
                currentSiteCode,
                ProductPublishSnapshotSupport.textValue(storeContext.get("storeCode"))
        );
        if (!StringUtils.hasText(siteCode)
                || !StringUtils.hasText(pricingSiteCode)
                || !siteCode.equalsIgnoreCase(pricingSiteCode)) {
            return;
        }
        if (target.getPricing() == null) {
            target.setPricing(new LinkedHashMap<>());
        }
        for (String field : PRICING_MIRROR_FIELDS) {
            if (siteOffer.containsKey(field)) {
                target.getPricing().put(field, siteOffer.get(field));
            }
        }
    }

    private void copyBaselineOfferFieldIfMissing(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer,
            String field
    ) {
        if (!targetOffer.containsKey(field) && baselineOffer.containsKey(field)) {
            targetOffer.put(field, baselineOffer.get(field));
        }
    }
}
