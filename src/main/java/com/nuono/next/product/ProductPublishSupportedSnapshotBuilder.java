package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductPublishSupportedSnapshotBuilder {

    private final ObjectMapper objectMapper;
    private final ProductPublishPlanner productPublishPlanner;

    ProductPublishSupportedSnapshotBuilder(
            ObjectMapper objectMapper,
            ProductPublishPlanner productPublishPlanner
    ) {
        this.objectMapper = objectMapper;
        this.productPublishPlanner = productPublishPlanner;
    }

    ProductMasterSnapshotView build(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            ProductPublishUnsupportedChanges unsupportedChanges
    ) {
        ProductMasterSnapshotView publishable = copySnapshot(draft);
        ProductPublishPlan plan = productPublishPlanner.plan(publishable, baseline, null);
        if (!plan.isPublishable() && unsupportedChanges != null) {
            unsupportedChanges.getPublishBlockers().addAll(plan.getBlockers());
        }
        return plan.getPublishableSnapshot();
    }

    void overlayUnsupportedDraft(
            ProductMasterSnapshotView targetDraft,
            ProductMasterSnapshotView sourceDraft,
            ProductPublishUnsupportedChanges unsupportedChanges
    ) {
        if (targetDraft == null || sourceDraft == null || unsupportedChanges == null) {
            return;
        }
        if (unsupportedChanges.isGroupChanged()) {
            targetDraft.setGroup(copyMap(sourceDraft.getGroup()));
        }
        if (unsupportedChanges.isVariantStructureChanged()) {
            targetDraft.setVariants(copyRecordList(sourceDraft.getVariants()));
        }
        if (!unsupportedChanges.getUnsupportedAttributeCodes().isEmpty()) {
            Map<String, Map<String, Object>> targetAttributes = keyAttributeMap(targetDraft.getKeyAttributes());
            Map<String, Map<String, Object>> sourceAttributes = keyAttributeMap(sourceDraft.getKeyAttributes());
            for (String code : unsupportedChanges.getUnsupportedAttributeCodes()) {
                if (targetAttributes.containsKey(code) && sourceAttributes.containsKey(code)) {
                    targetAttributes.put(code, copyMap(sourceAttributes.get(code)));
                }
            }
            targetDraft.setKeyAttributes(new ArrayList<>(targetAttributes.values()));
        }
        if (!unsupportedChanges.getUnsupportedSiteFields().isEmpty()) {
            Map<String, Map<String, Object>> targetOffers = siteOfferMap(targetDraft.getSiteOffers());
            Map<String, Map<String, Object>> sourceOffers = siteOfferMap(sourceDraft.getSiteOffers());
            for (Map.Entry<String, Set<String>> entry : unsupportedChanges.getUnsupportedSiteFields().entrySet()) {
                Map<String, Object> targetOffer = targetOffers.get(entry.getKey());
                Map<String, Object> sourceOffer = sourceOffers.get(entry.getKey());
                if (targetOffer == null || sourceOffer == null) {
                    continue;
                }
                for (String field : entry.getValue()) {
                    targetOffer.put(field, sourceOffer.get(field));
                }
            }
            targetDraft.setSiteOffers(new ArrayList<>(targetOffers.values()));
        }
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private List<Map<String, Object>> copyRecordList(List<Map<String, Object>> source) {
        return source == null
                ? new ArrayList<>()
                : objectMapper.convertValue(source, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Map.class));
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private Map<String, Map<String, Object>> keyAttributeMap(List<Map<String, Object>> keyAttributes) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (keyAttributes == null) {
            return map;
        }
        for (Map<String, Object> attribute : keyAttributes) {
            String code = textValue(attribute.get("code"));
            if (StringUtils.hasText(code)) {
                map.put(code, copyMap(attribute));
            }
        }
        return map;
    }

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            map.put(siteOfferCode(siteOffer), copyMap(siteOffer));
        }
        return map;
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
