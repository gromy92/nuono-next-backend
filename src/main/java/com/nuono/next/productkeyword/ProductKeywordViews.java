package com.nuono.next.productkeyword;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProductKeywordViews {
    private static final Set<String> TITLE_TYPES = Set.of(
            "CORE",
            "ATTRIBUTE",
            "SCENE",
            "AUDIENCE",
            "SPEC",
            "TRENDING"
    );
    private static final Set<String> TITLE_USAGE_STATES = Set.of(
            "TITLE_TARGET",
            "TITLE_COVERED",
            "TITLE_MISSING",
            "TITLE_REMOVED",
            "TITLE_NOT_FIT"
    );

    private ProductKeywordViews() {
    }

    public static KeywordItemView keyword(ProductKeywordRecord record) {
        if (record == null) {
            return null;
        }
        Set<String> tags = tags(record.getIntentTagsJson());
        return new KeywordItemView(
                record.getId(),
                record.getOwnerUserId(),
                record.getStoreCode(),
                record.getSiteCode(),
                record.getPartnerSku(),
                record.getKeyword(),
                record.getKeywordNorm(),
                record.getLocale(),
                record.getStatus(),
                record.getIntentTagsJson(),
                record.getSourceSummaryJson(),
                filter(tags, TITLE_TYPES),
                filter(tags, TITLE_USAGE_STATES),
                hasAny(tags, "COMPETITOR_TRACK"),
                hasAny(tags, "ADS_QUERY"),
                hasAny(tags, "NEGATIVE_CANDIDATE"),
                record.getFirstSeenAt(),
                record.getLastSeenAt()
        );
    }

    private static Set<String> tags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Set.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        StringBuilder token = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int index = 0; index < tagsJson.length(); index++) {
            char current = tagsJson.charAt(index);
            if (escaping) {
                token.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (current == '"') {
                if (inString) {
                    addTag(tags, token.toString());
                    token.setLength(0);
                }
                inString = !inString;
                continue;
            }
            if (inString) {
                token.append(current);
                continue;
            }
            if (Character.isLetterOrDigit(current) || current == '_') {
                token.append(current);
                continue;
            }
            if (token.length() > 0) {
                addTag(tags, token.toString());
                token.setLength(0);
            }
        }
        if (token.length() > 0) {
            addTag(tags, token.toString());
        }
        return tags;
    }

    private static void addTag(Set<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        tags.add(value.trim().toUpperCase(Locale.ROOT));
    }

    private static List<String> filter(Set<String> tags, Set<String> allowed) {
        List<String> values = new ArrayList<>();
        for (String tag : tags) {
            if (allowed.contains(tag)) {
                values.add(tag);
            }
        }
        return values;
    }

    private static boolean hasAny(Set<String> tags, String value) {
        return tags.contains(value);
    }

    public static EventItemView event(ProductKeywordUsageEventRecord record) {
        if (record == null) {
            return null;
        }
        return new EventItemView(
                record.getId(),
                record.getKeywordId(),
                record.getStoreCode(),
                record.getSiteCode(),
                record.getPartnerSku(),
                record.getKeyword(),
                record.getKeywordNorm(),
                record.getSourceType(),
                record.getSourceRefType(),
                record.getSourceRefId(),
                record.getSourceRefKey(),
                record.getEventStatus(),
                record.getOccurredAt(),
                record.getPayloadJson(),
                record.getMetricsJson()
        );
    }

    public static class KeywordListView {
        private final List<KeywordItemView> items;

        public KeywordListView(List<KeywordItemView> items) {
            this.items = items == null ? List.of() : items;
        }

        public List<KeywordItemView> getItems() {
            return items;
        }
    }

    public static class ProductKeywordPanelView {
        private final String storeCode;
        private final String siteCode;
        private final String partnerSku;
        private final List<KeywordItemView> keywords;
        private final List<EventItemView> events;

        public ProductKeywordPanelView(
                String storeCode,
                String siteCode,
                String partnerSku,
                List<KeywordItemView> keywords,
                List<EventItemView> events
        ) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.keywords = keywords == null ? List.of() : keywords;
            this.events = events == null ? List.of() : events;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public List<KeywordItemView> getKeywords() {
            return keywords;
        }

        public List<EventItemView> getEvents() {
            return events;
        }
    }

    public static class RebuildIndexResultView {
        private final String storeCode;
        private final String siteCode;
        private final String partnerSku;
        private final String status;

        public RebuildIndexResultView(String storeCode, String siteCode, String partnerSku, String status) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.status = status;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class KeywordItemView {
        private final Long id;
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;
        private final String partnerSku;
        private final String keyword;
        private final String keywordNorm;
        private final String locale;
        private final String status;
        private final String intentTagsJson;
        private final String sourceSummaryJson;
        private final List<String> titleTypes;
        private final List<String> titleUsageStates;
        private final Boolean competitorEvidence;
        private final Boolean adsEvidence;
        private final Boolean negativeCandidate;
        private final LocalDateTime firstSeenAt;
        private final LocalDateTime lastSeenAt;

        public KeywordItemView(
                Long id,
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String keyword,
                String keywordNorm,
                String locale,
                String status,
                String intentTagsJson,
                String sourceSummaryJson,
                List<String> titleTypes,
                List<String> titleUsageStates,
                Boolean competitorEvidence,
                Boolean adsEvidence,
                Boolean negativeCandidate,
                LocalDateTime firstSeenAt,
                LocalDateTime lastSeenAt
        ) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.keyword = keyword;
            this.keywordNorm = keywordNorm;
            this.locale = locale;
            this.status = status;
            this.intentTagsJson = intentTagsJson;
            this.sourceSummaryJson = sourceSummaryJson;
            this.titleTypes = titleTypes == null ? List.of() : titleTypes;
            this.titleUsageStates = titleUsageStates == null ? List.of() : titleUsageStates;
            this.competitorEvidence = Boolean.TRUE.equals(competitorEvidence);
            this.adsEvidence = Boolean.TRUE.equals(adsEvidence);
            this.negativeCandidate = Boolean.TRUE.equals(negativeCandidate);
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = lastSeenAt;
        }

        public Long getId() {
            return id;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public String getKeyword() {
            return keyword;
        }

        public String getKeywordNorm() {
            return keywordNorm;
        }

        public String getLocale() {
            return locale;
        }

        public String getStatus() {
            return status;
        }

        public String getIntentTagsJson() {
            return intentTagsJson;
        }

        public String getSourceSummaryJson() {
            return sourceSummaryJson;
        }

        public List<String> getTitleTypes() {
            return titleTypes;
        }

        public List<String> getTitleUsageStates() {
            return titleUsageStates;
        }

        public Boolean getCompetitorEvidence() {
            return competitorEvidence;
        }

        public Boolean getAdsEvidence() {
            return adsEvidence;
        }

        public Boolean getNegativeCandidate() {
            return negativeCandidate;
        }

        public LocalDateTime getFirstSeenAt() {
            return firstSeenAt;
        }

        public LocalDateTime getLastSeenAt() {
            return lastSeenAt;
        }
    }

    public static class EventItemView {
        private final Long id;
        private final Long keywordId;
        private final String storeCode;
        private final String siteCode;
        private final String partnerSku;
        private final String keyword;
        private final String keywordNorm;
        private final String sourceType;
        private final String sourceRefType;
        private final Long sourceRefId;
        private final String sourceRefKey;
        private final String eventStatus;
        private final LocalDateTime occurredAt;
        private final String payloadJson;
        private final String metricsJson;

        public EventItemView(
                Long id,
                Long keywordId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String keyword,
                String keywordNorm,
                String sourceType,
                String sourceRefType,
                Long sourceRefId,
                String sourceRefKey,
                String eventStatus,
                LocalDateTime occurredAt,
                String payloadJson,
                String metricsJson
        ) {
            this.id = id;
            this.keywordId = keywordId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.keyword = keyword;
            this.keywordNorm = keywordNorm;
            this.sourceType = sourceType;
            this.sourceRefType = sourceRefType;
            this.sourceRefId = sourceRefId;
            this.sourceRefKey = sourceRefKey;
            this.eventStatus = eventStatus;
            this.occurredAt = occurredAt;
            this.payloadJson = payloadJson;
            this.metricsJson = metricsJson;
        }

        public Long getId() {
            return id;
        }

        public Long getKeywordId() {
            return keywordId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public String getKeyword() {
            return keyword;
        }

        public String getKeywordNorm() {
            return keywordNorm;
        }

        public String getSourceType() {
            return sourceType;
        }

        public String getSourceRefType() {
            return sourceRefType;
        }

        public Long getSourceRefId() {
            return sourceRefId;
        }

        public String getSourceRefKey() {
            return sourceRefKey;
        }

        public String getEventStatus() {
            return eventStatus;
        }

        public LocalDateTime getOccurredAt() {
            return occurredAt;
        }

        public String getPayloadJson() {
            return payloadJson;
        }

        public String getMetricsJson() {
            return metricsJson;
        }
    }
}
