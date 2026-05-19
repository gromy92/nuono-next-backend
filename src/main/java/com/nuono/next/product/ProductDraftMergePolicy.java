package com.nuono.next.product;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public class ProductDraftMergePolicy {

    private static final Set<String> DEFAULT_EXPLICIT_CLEARABLE_FIELDS = Set.of(
            "salePrice",
            "saleStart",
            "saleEnd"
    );

    public Map<String, Object> hydrateMissingOfferFieldsForPublish(
            Map<String, Object> draftOffer,
            Map<String, Object> baselineOffer,
            Collection<String> publishFields
    ) {
        return hydrateMissingOfferFieldsForPublish(
                draftOffer,
                baselineOffer,
                publishFields,
                DEFAULT_EXPLICIT_CLEARABLE_FIELDS
        );
    }

    public Map<String, Object> hydrateMissingOfferFieldsForPublish(
            Map<String, Object> draftOffer,
            Map<String, Object> baselineOffer,
            Collection<String> publishFields,
            Set<String> explicitClearableFields
    ) {
        Map<String, Object> hydrated = new LinkedHashMap<>();
        if (draftOffer != null) {
            hydrated.putAll(draftOffer);
        }
        if (baselineOffer == null || publishFields == null) {
            return hydrated;
        }
        for (String field : publishFields) {
            if (field == null) {
                continue;
            }
            if (shouldCopyBaselineField(hydrated, field, explicitClearableFields) && baselineOffer.containsKey(field)) {
                hydrated.put(field, baselineOffer.get(field));
            }
        }
        return hydrated;
    }

    private boolean shouldCopyBaselineField(
            Map<String, Object> hydrated,
            String field,
            Set<String> explicitClearableFields
    ) {
        if (!hydrated.containsKey(field)) {
            return true;
        }
        Set<String> clearableFields = explicitClearableFields != null ? explicitClearableFields : Set.of();
        if (clearableFields.contains(field)) {
            return false;
        }
        Object value = hydrated.get(field);
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return !StringUtils.hasText((String) value);
        }
        return false;
    }

    public boolean hasExplicitDraftValue(Map<String, Object> draftNode, String field) {
        return draftNode != null && field != null && draftNode.containsKey(field);
    }
}
