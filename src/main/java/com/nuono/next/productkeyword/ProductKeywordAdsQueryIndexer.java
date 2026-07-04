package com.nuono.next.productkeyword;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductKeywordAdsQueryIndexer {
    private static final BigDecimal POSITIVE_ROAS_THRESHOLD = new BigDecimal("5");
    private static final BigDecimal NEGATIVE_SPEND_THRESHOLD = new BigDecimal("30");

    private final ProductKeywordMapper mapper;
    private final ProductKeywordNormalizer normalizer;

    public ProductKeywordAdsQueryIndexer(ProductKeywordMapper mapper, ProductKeywordNormalizer normalizer) {
        this.mapper = mapper;
        this.normalizer = normalizer;
    }

    public int indexRecentFacts(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer limit
    ) {
        if (!mapper.adsQueryFactTableExists()) {
            return 0;
        }
        List<NoonAdvertisingQueryFact> facts = mapper.listAdsQueryFactsForKeywordIndexing(
                ownerUserId,
                normalizeUpper(storeCode),
                normalizeUpper(siteCode),
                dateFrom,
                dateTo,
                safeLimit(limit)
        );
        int indexed = 0;
        for (NoonAdvertisingQueryFact fact : facts) {
            if (indexFact(fact)) {
                indexed++;
            }
        }
        return indexed;
    }

    public boolean indexFact(NoonAdvertisingQueryFact fact) {
        if (!isIndexable(fact)) {
            return false;
        }
        String storeCode = fact.getStoreCode().trim().toUpperCase(Locale.ROOT);
        String siteCode = fact.getSiteCode().trim().toUpperCase(Locale.ROOT);
        String partnerSku = fact.getPartnerSku().trim();
        String keyword = fact.getQueryText().trim();
        String keywordNorm = normalizer.normalize(keyword);
        if (!StringUtils.hasText(keywordNorm)) {
            return false;
        }
        ProductKeywordRecord keywordRecord = mapper.selectByScopeAndNorm(
                fact.getOwnerUserId(),
                storeCode,
                siteCode,
                partnerSku,
                keywordNorm
        );
        List<String> candidateTags = candidateTags(fact);
        if (keywordRecord == null && !candidateTags.isEmpty()) {
            keywordRecord = new ProductKeywordRecord();
            keywordRecord.setId(mapper.nextKeywordId());
            keywordRecord.setOwnerUserId(fact.getOwnerUserId());
            keywordRecord.setStoreCode(storeCode);
            keywordRecord.setSiteCode(siteCode);
            keywordRecord.setPartnerSku(partnerSku);
            keywordRecord.setKeyword(keyword);
            keywordRecord.setKeywordNorm(keywordNorm);
            keywordRecord.setStatus(ProductKeywordStatus.OBSERVED.name());
            keywordRecord.setIntentTagsJson(tagsJson(candidateTags));
            keywordRecord.setSourceSummaryJson(sourceSummaryJson(fact, candidateTags));
            keywordRecord.setFirstSeenAt(occurredAt(fact));
            keywordRecord.setCreatedBy(null);
            keywordRecord.setLastSeenAt(occurredAt(fact));
            keywordRecord.setUpdatedBy(null);
            mapper.upsertKeyword(keywordRecord);
        } else if (keywordRecord != null) {
            keywordRecord.setLastSeenAt(occurredAt(fact));
            mapper.upsertKeyword(keywordRecord);
        }
        mapper.upsertUsageEvent(buildEvent(fact, keywordRecord, storeCode, siteCode, partnerSku, keyword, keywordNorm));
        return true;
    }

    private boolean isIndexable(NoonAdvertisingQueryFact fact) {
        return fact != null
                && fact.getId() != null
                && fact.getOwnerUserId() != null
                && fact.getReportDateFrom() != null
                && fact.getReportDateTo() != null
                && StringUtils.hasText(fact.getStoreCode())
                && StringUtils.hasText(fact.getSiteCode())
                && StringUtils.hasText(fact.getPartnerSku())
                && StringUtils.hasText(fact.getQueryText());
    }

    private ProductKeywordUsageEventRecord buildEvent(
            NoonAdvertisingQueryFact fact,
            ProductKeywordRecord keywordRecord,
            String storeCode,
            String siteCode,
            String partnerSku,
            String keyword,
            String keywordNorm
    ) {
        ProductKeywordUsageEventRecord event = new ProductKeywordUsageEventRecord();
        event.setId(mapper.nextUsageEventId());
        event.setKeywordId(keywordRecord == null ? null : keywordRecord.getId());
        event.setOwnerUserId(fact.getOwnerUserId());
        event.setStoreCode(storeCode);
        event.setSiteCode(siteCode);
        event.setPartnerSku(partnerSku);
        event.setKeyword(keyword);
        event.setKeywordNorm(keywordNorm);
        event.setSourceType(ProductKeywordSourceType.ADS_QUERY.name());
        event.setSourceRefType("noon_ad_query_fact");
        event.setSourceRefId(fact.getId());
        event.setSourceRefKey(fact.getBatchId() + ":" + fact.getId());
        event.setEventStatus(ProductKeywordEventStatus.PERFORMED.name());
        event.setOccurredAt(occurredAt(fact));
        event.setFactDateFrom(fact.getReportDateFrom());
        event.setFactDateTo(fact.getReportDateTo());
        event.setPayloadJson(payloadJson(fact));
        event.setMetricsJson(metricsJson(fact));
        event.setEventNaturalKey(naturalKey(fact, siteCode, partnerSku, keywordNorm));
        event.setCreatedBy(null);
        event.setUpdatedBy(null);
        return event;
    }

    private String naturalKey(NoonAdvertisingQueryFact fact, String siteCode, String partnerSku, String keywordNorm) {
        return String.join(
                "|",
                ProductKeywordSourceType.ADS_QUERY.name(),
                "noon_ad_query_fact",
                String.valueOf(fact.getId()),
                String.valueOf(fact.getReportDateFrom()),
                String.valueOf(fact.getReportDateTo()),
                siteCode,
                partnerSku,
                keywordNorm,
                ProductKeywordEventStatus.PERFORMED.name()
        );
    }

    private List<String> candidateTags(NoonAdvertisingQueryFact fact) {
        List<String> tags = new ArrayList<>();
        if (fact.getOrdersCount() > 0
                || decimal(fact.getRoas()).compareTo(POSITIVE_ROAS_THRESHOLD) >= 0
                || fact.getClicks() >= 20) {
            tags.add("ADS_QUERY");
        }
        if (decimal(fact.getSpendAmount()).compareTo(NEGATIVE_SPEND_THRESHOLD) >= 0
                && fact.getOrdersCount() == 0) {
            tags.add("NEGATIVE_CANDIDATE");
        }
        return tags;
    }

    private LocalDateTime occurredAt(NoonAdvertisingQueryFact fact) {
        return fact.getReportDateTo().atStartOfDay();
    }

    private String sourceSummaryJson(NoonAdvertisingQueryFact fact, List<String> tags) {
        List<String> entries = new ArrayList<>();
        entries.add("\"queryFactId\":" + fact.getId());
        entries.add("\"campaignCode\":" + quote(fact.getCampaignCode()));
        entries.add("\"candidateTags\":" + tagsJson(tags));
        return "{" + String.join(",", entries) + "}";
    }

    private String payloadJson(NoonAdvertisingQueryFact fact) {
        List<String> entries = new ArrayList<>();
        entries.add("\"queryFactId\":" + fact.getId());
        entries.add("\"batchId\":" + fact.getBatchId());
        entries.add("\"campaignCode\":" + quote(fact.getCampaignCode()));
        entries.add("\"campaignName\":" + quote(fact.getCampaignName()));
        entries.add("\"adSkuCode\":" + quote(fact.getAdSkuCode()));
        entries.add("\"queryText\":" + quote(fact.getQueryText()));
        entries.add("\"queryKind\":" + quote(fact.getQueryKind()));
        return "{" + String.join(",", entries) + "}";
    }

    private String metricsJson(NoonAdvertisingQueryFact fact) {
        List<String> entries = new ArrayList<>();
        entries.add("\"views\":" + fact.getViews());
        entries.add("\"clicks\":" + fact.getClicks());
        entries.add("\"ordersCount\":" + fact.getOrdersCount());
        entries.add("\"assistedOrders\":" + fact.getAssistedOrders());
        entries.add("\"atcCount\":" + fact.getAtcCount());
        entries.add("\"spendAmount\":" + decimalText(fact.getSpendAmount()));
        entries.add("\"adRevenue\":" + decimalText(fact.getAdRevenue()));
        entries.add("\"roas\":" + decimalText(fact.getRoas()));
        entries.add("\"cpc\":" + decimalText(fact.getCpc()));
        entries.add("\"cps\":" + decimalText(fact.getCps()));
        entries.add("\"cvrPercentage\":" + decimalText(fact.getCvrPercentage()));
        return "{" + String.join(",", entries) + "}";
    }

    private String tagsJson(List<String> tags) {
        return "[" + tags.stream().map(this::quote).reduce((left, right) -> left + "," + right).orElse("") + "]";
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

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String decimalText(BigDecimal value) {
        return decimal(value).toPlainString();
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : value;
    }

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 1000;
        }
        return Math.min(limit, 5000);
    }
}
