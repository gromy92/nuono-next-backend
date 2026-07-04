package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductKeywordTitleIndexerTest {

    @Test
    void indexesAfterTitleFromHistorySummaryAndExcludesObservedKeywords() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        mapper.titleTargets.add(keyword(300001L, "SA", "Milk Bottle", "milk bottle", "ACTIVE", "[\"CORE\",\"TITLE_TARGET\"]"));
        mapper.titleTargets.add(keyword(300002L, "SA", "one off ads query", "one off ads query", "OBSERVED", "[\"ADS_CANDIDATE\"]"));
        ProductKeywordTitleIndexer indexer =
                new ProductKeywordTitleIndexer(mapper, new ProductKeywordMatcher(new ProductKeywordNormalizer()));

        indexer.indexPublishedHistory(new ProductKeywordTitleIndexer.TitleHistoryIndexCommand(
                99L,
                88001L,
                59001L,
                "STR108065-NSA",
                "SA",
                "PSKU-1",
                summaryAfterTitle("Milk Bottle for Kids", "زجاجة حليب"),
                LocalDateTime.parse("2026-07-04T10:00:00"),
                7L
        ));

        assertThat(mapper.events).singleElement().satisfies(event -> {
            assertThat(event.getSourceType()).isEqualTo("TITLE_HISTORY");
            assertThat(event.getSourceRefType()).isEqualTo("product_key_content_history");
            assertThat(event.getSourceRefId()).isEqualTo(59001L);
            assertThat(event.getKeywordNorm()).isEqualTo("milk bottle");
            assertThat(event.getEventNaturalKey()).contains("TITLE_HISTORY", "59001", "milk bottle", "MATCHED");
            assertThat(event.getPayloadJson()).contains("titleEn").contains("Milk Bottle for Kids");
            assertThat(event.getPayloadJson()).contains("title_hash").contains("matchedFields");
        });
    }

    @Test
    void siteLessHistoryUsesProductLevelScopeAndWildcardKeywords() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        mapper.titleTargets.add(keyword(300003L, "*", "Milk Bottle", "milk bottle", "ACTIVE", "[\"TITLE_TARGET\"]"));
        ProductKeywordTitleIndexer indexer =
                new ProductKeywordTitleIndexer(mapper, new ProductKeywordMatcher(new ProductKeywordNormalizer()));

        indexer.indexPublishedHistory(new ProductKeywordTitleIndexer.TitleHistoryIndexCommand(
                99L,
                88001L,
                59002L,
                "STR108065-NSA",
                null,
                "PSKU-1",
                summaryAfterTitle("Travel Milk Bottle", null),
                LocalDateTime.parse("2026-07-04T11:00:00"),
                7L
        ));

        assertThat(mapper.events).singleElement().satisfies(event -> {
            assertThat(event.getSiteCode()).isEqualTo("*");
            assertThat(event.getEventNaturalKey()).contains("TITLE_HISTORY", "*", "PSKU-1");
        });
    }

    private static Map<String, Object> summaryAfterTitle(String titleEn, String titleAr) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("titleEn", titleEn);
        after.put("titleAr", titleAr);
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("after", after);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", title);
        summary.put("partnerSku", "PSKU-1");
        return summary;
    }

    private static ProductKeywordRecord keyword(
            Long id,
            String siteCode,
            String keyword,
            String keywordNorm,
            String status,
            String tagsJson
    ) {
        ProductKeywordRecord record = new ProductKeywordRecord();
        record.setId(id);
        record.setOwnerUserId(99L);
        record.setStoreCode("STR108065-NSA");
        record.setSiteCode(siteCode);
        record.setPartnerSku("PSKU-1");
        record.setKeyword(keyword);
        record.setKeywordNorm(keywordNorm);
        record.setStatus(status);
        record.setIntentTagsJson(tagsJson);
        return record;
    }

    private static final class FakeProductKeywordMapper implements ProductKeywordMapper {
        private long nextEventId = 320001L;
        private final List<ProductKeywordRecord> titleTargets = new ArrayList<>();
        private final List<ProductKeywordUsageEventRecord> events = new ArrayList<>();

        @Override
        public int allocateId(IdSequenceCommand command) {
            return 1;
        }

        @Override
        public Long nextKeywordId() {
            return 300001L;
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
            return null;
        }

        @Override
        public ProductKeywordRecord selectById(Long ownerUserId, Long keywordId) {
            return null;
        }

        @Override
        public int upsertKeyword(ProductKeywordRecord record) {
            return 1;
        }

        @Override
        public int upsertUsageEvent(ProductKeywordUsageEventRecord record) {
            events.add(record);
            return 1;
        }

        @Override
        public List<ProductKeywordRecord> listKeywords(ProductKeywordListQuery query) {
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
            return List.of();
        }

        @Override
        public List<ProductKeywordRecord> listActiveTitleTargetKeywords(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku
        ) {
            return titleTargets;
        }

        @Override
        public boolean adsQueryFactTableExists() {
            return false;
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
    }
}
