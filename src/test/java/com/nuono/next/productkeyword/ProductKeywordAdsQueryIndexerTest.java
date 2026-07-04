package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductKeywordAdsQueryIndexerTest {

    @Test
    void existingActiveKeywordReceivesAdsQueryEventWithMetrics() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordRecord active = keyword(300900L, "ACTIVE", "[\"CORE\"]");
        mapper.upsertKeyword(active);
        ProductKeywordAdsQueryIndexer indexer = new ProductKeywordAdsQueryIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexFact(fact(500001L, "Milk Bottle", "PSKU-1", 8, 1, "12.50", "75.00", "6.00"));

        assertThat(mapper.keywords.values()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getId()).isEqualTo(300900L);
            assertThat(keyword.getStatus()).isEqualTo("ACTIVE");
            assertThat(keyword.getIntentTagsJson()).contains("CORE", "ADS_QUERY");
        });
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getKeywordId()).isEqualTo(300900L);
            assertThat(event.getSourceType()).isEqualTo("ADS_QUERY");
            assertThat(event.getEventStatus()).isEqualTo("PERFORMED");
            assertThat(event.getMetricsJson()).contains("\"ordersCount\":1", "\"roas\":6.00");
            assertThat(event.getEventNaturalKey())
                    .isEqualTo("ADS_QUERY|noon_ad_query_fact|500001|2026-06-25|2026-07-01|SA|PSKU-1|milk bottle|PERFORMED");
        });
    }

    @Test
    void lowVolumeQueryWritesEventWithoutObservedKeywordRow() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordAdsQueryIndexer indexer = new ProductKeywordAdsQueryIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexFact(fact(500002L, "Low Volume Phrase", "PSKU-1", 1, 0, "0.80", "0.00", "0.00"));

        assertThat(mapper.keywords).isEmpty();
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getKeywordId()).isNull();
            assertThat(event.getKeywordNorm()).isEqualTo("low volume phrase");
            assertThat(event.getSourceType()).isEqualTo("ADS_QUERY");
        });
    }

    @Test
    void highRoasQueryCreatesObservedCandidateNotActive() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordAdsQueryIndexer indexer = new ProductKeywordAdsQueryIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexFact(fact(500003L, "Winning Query", "PSKU-1", 4, 1, "4.00", "40.00", "10.00"));

        assertThat(mapper.keywords.values()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getStatus()).isEqualTo("OBSERVED");
            assertThat(keyword.getIntentTagsJson()).contains("ADS_QUERY");
            assertThat(keyword.getKeywordNorm()).isEqualTo("winning query");
        });
    }

    @Test
    void zeroOrderHighSpendQueryCreatesNegativeObservedCandidate() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordAdsQueryIndexer indexer = new ProductKeywordAdsQueryIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexFact(fact(500004L, "Wasted Query", "PSKU-1", 3, 0, "35.00", "0.00", "0.00"));

        assertThat(mapper.keywords.values()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getStatus()).isEqualTo("OBSERVED");
            assertThat(keyword.getIntentTagsJson()).contains("NEGATIVE_CANDIDATE");
            assertThat(keyword.getKeywordNorm()).isEqualTo("wasted query");
        });
    }

    @Test
    void blankPartnerSkuIsSkippedForProductKeywordAttachment() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordAdsQueryIndexer indexer = new ProductKeywordAdsQueryIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexFact(fact(500005L, "Unresolved Product Query", " ", 30, 2, "6.00", "60.00", "10.00"));

        assertThat(mapper.keywords).isEmpty();
        assertThat(mapper.events).isEmpty();
    }

    private static ProductKeywordRecord keyword(Long id, String status, String tagsJson) {
        ProductKeywordRecord record = new ProductKeywordRecord();
        record.setId(id);
        record.setOwnerUserId(99L);
        record.setStoreCode("STR108065-NSA");
        record.setSiteCode("SA");
        record.setPartnerSku("PSKU-1");
        record.setKeyword("Milk Bottle");
        record.setKeywordNorm("milk bottle");
        record.setStatus(status);
        record.setIntentTagsJson(tagsJson);
        return record;
    }

    private static NoonAdvertisingQueryFact fact(
            Long id,
            String queryText,
            String partnerSku,
            long clicks,
            long orders,
            String spend,
            String revenue,
            String roas
    ) {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setId(id);
        fact.setBatchId(700001L);
        fact.setSourceSystem("noon_ads");
        fact.setOwnerUserId(99L);
        fact.setProjectCode("PRJ108065");
        fact.setStoreCode("STR108065-NSA");
        fact.setSiteCode("SA");
        fact.setReportDateFrom(LocalDate.parse("2026-06-25"));
        fact.setReportDateTo(LocalDate.parse("2026-07-01"));
        fact.setCampaignCode("C_AUTO");
        fact.setCampaignName("Auto Campaign");
        fact.setAdSkuCode("ZSKU-1");
        fact.setPartnerSku(partnerSku);
        fact.setQueryText(queryText);
        fact.setQueryHash("hash-" + id);
        fact.setQueryKind("SEARCH");
        fact.setViews(100);
        fact.setClicks(clicks);
        fact.setOrdersCount(orders);
        fact.setSpendAmount(new BigDecimal(spend));
        fact.setAdRevenue(new BigDecimal(revenue));
        fact.setRoas(new BigDecimal(roas));
        return fact;
    }

    private static final class FakeProductKeywordMapper implements ProductKeywordMapper {
        private long nextKeywordId = 300001L;
        private long nextEventId = 320001L;
        private final Map<String, ProductKeywordRecord> keywords = new LinkedHashMap<>();
        private final Map<String, ProductKeywordUsageEventRecord> events = new LinkedHashMap<>();

        @Override
        public int allocateId(IdSequenceCommand command) {
            return 1;
        }

        @Override
        public Long nextKeywordId() {
            return nextKeywordId++;
        }

        @Override
        public Long nextUsageEventId() {
            return nextEventId++;
        }

        @Override
        public ProductKeywordRecord selectByScopeAndNorm(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String keywordNorm
        ) {
            return keywords.get(scopeKey(ownerUserId, storeCode, siteCode, partnerSku, keywordNorm));
        }

        @Override
        public ProductKeywordRecord selectById(Long ownerUserId, Long keywordId) {
            return null;
        }

        @Override
        public int upsertKeyword(ProductKeywordRecord record) {
            keywords.put(scopeKey(
                    record.getOwnerUserId(),
                    record.getStoreCode(),
                    record.getSiteCode(),
                    record.getPartnerSku(),
                    record.getKeywordNorm()
            ), record);
            return 1;
        }

        @Override
        public int upsertUsageEvent(ProductKeywordUsageEventRecord record) {
            ProductKeywordUsageEventRecord existing = events.get(record.getEventNaturalKey());
            if (existing != null) {
                record.setId(existing.getId());
            }
            events.put(record.getEventNaturalKey(), record);
            return 1;
        }

        @Override
        public List<ProductKeywordRecord> listKeywords(ProductKeywordListQuery query) {
            return new ArrayList<>(keywords.values());
        }

        @Override
        public List<ProductKeywordRecord> listActiveTitleTargetKeywords(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku
        ) {
            return List.of();
        }

        @Override
        public List<ProductKeywordUsageEventRecord> listEvents(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                Integer limit
        ) {
            return new ArrayList<>(events.values());
        }

        @Override
        public boolean adsQueryFactTableExists() {
            return true;
        }

        @Override
        public List<NoonAdvertisingQueryFact> listAdsQueryFactsForKeywordIndexing(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                LocalDate dateFrom,
                LocalDate dateTo,
                Integer limit
        ) {
            return List.of();
        }

        private static String scopeKey(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String keywordNorm
        ) {
            return ownerUserId + "|" + storeCode + "|" + siteCode + "|" + partnerSku + "|" + keywordNorm;
        }
    }
}
