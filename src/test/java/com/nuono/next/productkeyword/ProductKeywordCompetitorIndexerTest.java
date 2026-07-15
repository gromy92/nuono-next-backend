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

class ProductKeywordCompetitorIndexerTest {

    @Test
    void activeCompetitorKeywordCreatesObservedAssetAndIdempotentEvent() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordCompetitorIndexer indexer = new ProductKeywordCompetitorIndexer(mapper, new ProductKeywordNormalizer());

        ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand command =
                new ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand(
                        99L,
                        180001L,
                        190001L,
                        "STR108065-NSA",
                        "SA",
                        "PSKU-1",
                        "Milk Bottle",
                        "ACTIVE",
                        LocalDateTime.parse("2026-07-04T12:00:00"),
                        7L
                );

        indexer.indexKeyword(command);
        indexer.indexKeyword(command);

        assertThat(mapper.keywords.values()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getStatus()).isEqualTo("OBSERVED");
            assertThat(keyword.getIntentTagsJson()).contains("COMPETITOR_TRACK");
            assertThat(keyword.getKeywordNorm()).isEqualTo("milk bottle");
        });
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getSourceType()).isEqualTo("COMPETITOR_KEYWORD");
            assertThat(event.getSourceRefType()).isEqualTo("operations_competitor_keyword");
            assertThat(event.getSourceRefId()).isEqualTo(190001L);
            assertThat(event.getEventNaturalKey())
                    .isEqualTo("COMPETITOR_KEYWORD|operations_competitor_keyword|190001|SA|PSKU-1|milk bottle|OBSERVED");
        });
    }

    @Test
    void inactiveCompetitorKeywordDoesNotWriteProductKeywordEvidence() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordCompetitorIndexer indexer = new ProductKeywordCompetitorIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexKeyword(new ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand(
                99L,
                180001L,
                190002L,
                "STR108065-NSA",
                "SA",
                "PSKU-1",
                "Milk Bottle",
                "PAUSED",
                LocalDateTime.parse("2026-07-04T12:00:00"),
                7L
        ));

        assertThat(mapper.keywords).isEmpty();
        assertThat(mapper.events).isEmpty();
    }

    @Test
    void competitorEvidenceDoesNotDowngradeManualActiveKeyword() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordRecord active = new ProductKeywordRecord();
        active.setId(300900L);
        active.setOwnerUserId(99L);
        active.setStoreCode("STR108065-NSA");
        active.setSiteCode("SA");
        active.setPartnerSku("PSKU-1");
        active.setKeyword("Milk Bottle");
        active.setKeywordNorm("milk bottle");
        active.setStatus("ACTIVE");
        active.setIntentTagsJson("[\"CORE\"]");
        mapper.upsertKeyword(active);
        ProductKeywordCompetitorIndexer indexer = new ProductKeywordCompetitorIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexKeyword(new ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand(
                99L,
                180001L,
                190001L,
                "STR108065-NSA",
                "SA",
                "PSKU-1",
                "Milk Bottle",
                "ACTIVE",
                LocalDateTime.parse("2026-07-04T12:00:00"),
                7L
        ));

        assertThat(mapper.keywords.values()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getId()).isEqualTo(300900L);
            assertThat(keyword.getStatus()).isEqualTo("ACTIVE");
            assertThat(keyword.getIntentTagsJson()).contains("CORE", "COMPETITOR_TRACK");
        });
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getKeywordId()).isEqualTo(300900L);
            assertThat(event.getSourceType()).isEqualTo("COMPETITOR_KEYWORD");
            assertThat(event.getEventStatus()).isEqualTo("OBSERVED");
        });
    }

    @Test
    void deletedCompetitorKeywordWritesRemovedUsageEventWithoutArchivingKeywordAsset() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordRecord active = new ProductKeywordRecord();
        active.setId(300900L);
        active.setOwnerUserId(99L);
        active.setStoreCode("STR108065-NSA");
        active.setSiteCode("SA");
        active.setPartnerSku("PSKU-1");
        active.setKeyword("Milk Bottle");
        active.setKeywordNorm("milk bottle");
        active.setStatus("ACTIVE");
        active.setIntentTagsJson("[\"CORE\",\"COMPETITOR_TRACK\"]");
        mapper.upsertKeyword(active);
        ProductKeywordCompetitorIndexer indexer = new ProductKeywordCompetitorIndexer(mapper, new ProductKeywordNormalizer());

        indexer.indexKeyword(new ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand(
                99L,
                180001L,
                190001L,
                "STR108065-NSA",
                "SA",
                "PSKU-1",
                "Milk Bottle",
                "DELETED",
                LocalDateTime.parse("2026-07-04T13:00:00"),
                7L
        ));

        assertThat(mapper.keywords.values()).singleElement().satisfies(keyword -> {
            assertThat(keyword.getId()).isEqualTo(300900L);
            assertThat(keyword.getStatus()).isEqualTo("ACTIVE");
        });
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getKeywordId()).isEqualTo(300900L);
            assertThat(event.getSourceType()).isEqualTo("COMPETITOR_KEYWORD");
            assertThat(event.getEventStatus()).isEqualTo("REMOVED");
            assertThat(event.getEventNaturalKey())
                    .isEqualTo("COMPETITOR_KEYWORD|operations_competitor_keyword|190001|SA|PSKU-1|milk bottle|REMOVED");
        });
    }

    private static final class FakeProductKeywordMapper implements ProductKeywordMapper {

        @Override
        public int archiveKeyword(Long ownerUserId, String storeCode, String siteCode, String partnerSku, Long keywordId, Long updatedBy) {
            return 0;
        }

        @Override
        public int archiveKeywordEvents(Long ownerUserId, String storeCode, String siteCode, String partnerSku, Long keywordId, Long updatedBy) {
            return 0;
        }
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
