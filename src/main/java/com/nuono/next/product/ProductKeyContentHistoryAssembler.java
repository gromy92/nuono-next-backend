package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductKeyContentHistoryAssembler {

    public KeyContentHistoryCandidate buildCandidate(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView published,
            String targetSiteCode
    ) {
        if (baseline == null || published == null) {
            return null;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        LinkedHashSet<String> changeTypes = new LinkedHashSet<>();

        Map<String, Object> titleBefore = buildTextGroup(
                baseline.getContent(),
                "titleEn",
                "titleAr"
        );
        Map<String, Object> titleAfter = buildTextGroup(
                published.getContent(),
                "titleEn",
                "titleAr"
        );
        appendChange(summary, changeTypes, "title", titleBefore, titleAfter);

        Map<String, Object> descriptionBefore = buildTextGroup(
                baseline.getContent(),
                "descriptionEn",
                "descriptionAr"
        );
        Map<String, Object> descriptionAfter = buildTextGroup(
                published.getContent(),
                "descriptionEn",
                "descriptionAr"
        );
        appendChange(summary, changeTypes, "description", descriptionBefore, descriptionAfter);

        List<String> imagesBefore = normalizeStringList(baseline.getContent().get("images"));
        List<String> imagesAfter = normalizeStringList(published.getContent().get("images"));
        appendChange(summary, changeTypes, "images", imagesBefore, imagesAfter);

        if (changeTypes.isEmpty()) {
            return null;
        }

        putIfNotBlank(summary, "skuParent", textValue(published.getIdentity().get("skuParent")));
        putIfNotBlank(summary, "partnerSku", textValue(published.getIdentity().get("partnerSku")));
        putIfNotBlank(summary, "pskuCode", textValue(published.getIdentity().get("pskuCode")));
        putIfNotBlank(summary, "offerCode", textValue(published.getIdentity().get("offerCode")));
        putIfNotBlank(summary, "targetSiteCode", normalize(targetSiteCode));

        return new KeyContentHistoryCandidate(new ArrayList<>(changeTypes), summary);
    }

    private Map<String, Object> buildTextGroup(Map<String, Object> content, String firstKey, String secondKey) {
        Map<String, Object> values = new LinkedHashMap<>();
        putIfNotBlank(values, firstKey, textValue(content != null ? content.get(firstKey) : null));
        putIfNotBlank(values, secondKey, textValue(content != null ? content.get(secondKey) : null));
        return values;
    }

    private void appendChange(
            Map<String, Object> summary,
            LinkedHashSet<String> changeTypes,
            String changeType,
            Object before,
            Object after
    ) {
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", before);
        change.put("after", after);
        summary.put(changeType, change);
        changeTypes.add(changeType);
    }

    private List<String> normalizeStringList(Object value) {
        List<String> normalized = new ArrayList<>();
        if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
                String text = textValue(item);
                if (StringUtils.hasText(text)) {
                    normalized.add(text);
                }
            }
        }
        return normalized;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key, value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    public static class KeyContentHistoryCandidate {
        private final List<String> changeTypes;
        private final Map<String, Object> summary;

        public KeyContentHistoryCandidate(List<String> changeTypes, Map<String, Object> summary) {
            this.changeTypes = changeTypes;
            this.summary = summary;
        }

        public List<String> getChangeTypes() {
            return changeTypes;
        }

        public Map<String, Object> getSummary() {
            return summary;
        }
    }
}
