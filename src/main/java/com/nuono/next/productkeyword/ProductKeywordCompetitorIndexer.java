package com.nuono.next.productkeyword;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductKeywordCompetitorIndexer {
    private final ProductKeywordMapper mapper;
    private final ProductKeywordNormalizer normalizer;

    public ProductKeywordCompetitorIndexer(ProductKeywordMapper mapper, ProductKeywordNormalizer normalizer) {
        this.mapper = mapper;
        this.normalizer = normalizer;
    }

    public void indexKeyword(CompetitorKeywordIndexCommand command) {
        if (!isIndexable(command)) {
            return;
        }
        String storeCode = command.storeCode.trim().toUpperCase(Locale.ROOT);
        String siteCode = command.siteCode.trim().toUpperCase(Locale.ROOT);
        String partnerSku = command.partnerSku.trim();
        String keyword = command.keyword.trim();
        String keywordNorm = normalizer.normalize(keyword);
        if (!StringUtils.hasText(keywordNorm)) {
            return;
        }
        LocalDateTime occurredAt = command.occurredAt == null ? LocalDateTime.now() : command.occurredAt;
        ProductKeywordRecord keywordRecord = mapper.selectByScopeAndNorm(
                command.ownerUserId,
                storeCode,
                siteCode,
                partnerSku,
                keywordNorm
        );
        if (keywordRecord == null) {
            keywordRecord = new ProductKeywordRecord();
            keywordRecord.setId(mapper.nextKeywordId());
            keywordRecord.setOwnerUserId(command.ownerUserId);
            keywordRecord.setStoreCode(storeCode);
            keywordRecord.setSiteCode(siteCode);
            keywordRecord.setPartnerSku(partnerSku);
            keywordRecord.setKeyword(keyword);
            keywordRecord.setKeywordNorm(keywordNorm);
            keywordRecord.setStatus(ProductKeywordStatus.OBSERVED.name());
            keywordRecord.setIntentTagsJson("[\"COMPETITOR_TRACK\"]");
            keywordRecord.setSourceSummaryJson(sourceSummaryJson(command));
            keywordRecord.setFirstSeenAt(occurredAt);
            keywordRecord.setCreatedBy(command.actorUserId);
        }
        keywordRecord.setLastSeenAt(occurredAt);
        keywordRecord.setUpdatedBy(command.actorUserId);
        mapper.upsertKeyword(keywordRecord);
        mapper.upsertUsageEvent(buildEvent(command, keywordRecord, keyword, keywordNorm, occurredAt));
    }

    private boolean isIndexable(CompetitorKeywordIndexCommand command) {
        return command != null
                && command.ownerUserId != null
                && command.watchProductId != null
                && command.keywordId != null
                && StringUtils.hasText(command.storeCode)
                && StringUtils.hasText(command.siteCode)
                && StringUtils.hasText(command.partnerSku)
                && StringUtils.hasText(command.keyword)
                && "ACTIVE".equalsIgnoreCase(command.status);
    }

    private ProductKeywordUsageEventRecord buildEvent(
            CompetitorKeywordIndexCommand command,
            ProductKeywordRecord keywordRecord,
            String keyword,
            String keywordNorm,
            LocalDateTime occurredAt
    ) {
        ProductKeywordUsageEventRecord event = new ProductKeywordUsageEventRecord();
        event.setId(mapper.nextUsageEventId());
        event.setKeywordId(keywordRecord.getId());
        event.setOwnerUserId(keywordRecord.getOwnerUserId());
        event.setStoreCode(keywordRecord.getStoreCode());
        event.setSiteCode(keywordRecord.getSiteCode());
        event.setPartnerSku(keywordRecord.getPartnerSku());
        event.setKeyword(keyword);
        event.setKeywordNorm(keywordNorm);
        event.setSourceType(ProductKeywordSourceType.COMPETITOR_KEYWORD.name());
        event.setSourceRefType("operations_competitor_keyword");
        event.setSourceRefId(command.keywordId);
        event.setSourceRefKey(command.watchProductId + ":" + command.keywordId);
        event.setEventStatus(ProductKeywordEventStatus.OBSERVED.name());
        event.setOccurredAt(occurredAt);
        event.setPayloadJson(payloadJson(command, keyword));
        event.setMetricsJson("{}");
        event.setEventNaturalKey(naturalKey(command.keywordId, keywordRecord, keywordNorm));
        event.setCreatedBy(command.actorUserId);
        event.setUpdatedBy(command.actorUserId);
        return event;
    }

    private String naturalKey(Long keywordId, ProductKeywordRecord keywordRecord, String keywordNorm) {
        return String.join(
                "|",
                ProductKeywordSourceType.COMPETITOR_KEYWORD.name(),
                "operations_competitor_keyword",
                String.valueOf(keywordId),
                keywordRecord.getSiteCode(),
                keywordRecord.getPartnerSku(),
                keywordNorm,
                ProductKeywordEventStatus.OBSERVED.name()
        );
    }

    private String sourceSummaryJson(CompetitorKeywordIndexCommand command) {
        return "{" + String.join(",", summaryEntries(command)) + "}";
    }

    private List<String> summaryEntries(CompetitorKeywordIndexCommand command) {
        List<String> entries = new ArrayList<>();
        entries.add("\"watchProductId\":" + command.watchProductId);
        entries.add("\"competitorKeywordId\":" + command.keywordId);
        return entries;
    }

    private String payloadJson(CompetitorKeywordIndexCommand command, String keyword) {
        List<String> entries = summaryEntries(command);
        entries.add("\"keyword\":" + quote(keyword));
        return "{" + String.join(",", entries) + "}";
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    public static class CompetitorKeywordIndexCommand {
        private final Long ownerUserId;
        private final Long watchProductId;
        private final Long keywordId;
        private final String storeCode;
        private final String siteCode;
        private final String partnerSku;
        private final String keyword;
        private final String status;
        private final LocalDateTime occurredAt;
        private final Long actorUserId;

        public CompetitorKeywordIndexCommand(
                Long ownerUserId,
                Long watchProductId,
                Long keywordId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String keyword,
                String status,
                LocalDateTime occurredAt,
                Long actorUserId
        ) {
            this.ownerUserId = ownerUserId;
            this.watchProductId = watchProductId;
            this.keywordId = keywordId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.keyword = keyword;
            this.status = status;
            this.occurredAt = occurredAt;
            this.actorUserId = actorUserId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public Long getWatchProductId() {
            return watchProductId;
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

        public String getStatus() {
            return status;
        }

        public LocalDateTime getOccurredAt() {
            return occurredAt;
        }

        public Long getActorUserId() {
            return actorUserId;
        }
    }
}
