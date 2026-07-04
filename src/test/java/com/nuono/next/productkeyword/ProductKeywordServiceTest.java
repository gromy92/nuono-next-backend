package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductKeywordServiceTest {

    @Test
    void manualKeywordCreatesActiveAssetAndManualUsageEvent() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordService service = new ProductKeywordService(mapper, new ProductKeywordNormalizer());

        ProductKeywordRecord added = service.addManualKeyword(
                context(),
                command("STR108065-NSA", "SA", "PSKU-1", "Milk Bottle", List.of("CORE", "TITLE_TARGET"))
        );

        assertThat(added.getStatus()).isEqualTo("ACTIVE");
        assertThat(added.getOwnerUserId()).isEqualTo(99L);
        assertThat(added.getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(added.getSiteCode()).isEqualTo("SA");
        assertThat(added.getPartnerSku()).isEqualTo("PSKU-1");
        assertThat(added.getKeywordNorm()).isEqualTo("milk bottle");
        assertThat(added.getIntentTagsJson()).contains("CORE", "TITLE_TARGET");
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getSourceType()).isEqualTo("MANUAL");
            assertThat(event.getEventStatus()).isEqualTo("ADDED");
            assertThat(event.getEventNaturalKey()).contains("MANUAL").contains("PSKU-1").contains("milk bottle");
        });
    }

    @Test
    void duplicateManualKeywordReturnsSameAssetAndKeepsSingleNaturalEvent() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordService service = new ProductKeywordService(mapper, new ProductKeywordNormalizer());

        ProductKeywordRecord first = service.addManualKeyword(
                context(),
                command("STR108065-NSA", "SA", "PSKU-1", "Milk Bottle", List.of("CORE"))
        );
        ProductKeywordRecord duplicate = service.addManualKeyword(
                context(),
                command(" str108065-nsa ", " sa ", " PSKU-1 ", " milk   bottle ", List.of("CORE"))
        );

        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(mapper.keywords).hasSize(1);
        assertThat(mapper.events).hasSize(1);
        assertThat(mapper.nextKeywordId).isEqualTo(300002L);
    }

    @Test
    void adsObservedKeywordNeverCreatesActiveAsset() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordService service = new ProductKeywordService(mapper, new ProductKeywordNormalizer());

        ProductKeywordRecord observed = service.recordObservedKeyword(
                context(),
                command("STR108065-NSA", "SA", "PSKU-1", "Milk Bottle", List.of("ADS_CANDIDATE")),
                ProductKeywordSourceType.ADS_QUERY
        );

        assertThat(observed.getStatus()).isEqualTo("OBSERVED");
        assertThat(mapper.events.values()).singleElement().satisfies(event -> {
            assertThat(event.getSourceType()).isEqualTo("ADS_QUERY");
            assertThat(event.getEventStatus()).isEqualTo("OBSERVED");
        });
    }

    @Test
    void observedEvidenceDoesNotDowngradeExistingActiveKeyword() {
        FakeProductKeywordMapper mapper = new FakeProductKeywordMapper();
        ProductKeywordService service = new ProductKeywordService(mapper, new ProductKeywordNormalizer());

        ProductKeywordRecord active = service.addManualKeyword(
                context(),
                command("STR108065-NSA", "SA", "PSKU-1", "Milk Bottle", List.of("CORE"))
        );
        ProductKeywordRecord afterAdsEvidence = service.recordObservedKeyword(
                context(),
                command("STR108065-NSA", "SA", "PSKU-1", "milk   bottle", List.of("ADS_CANDIDATE")),
                ProductKeywordSourceType.ADS_QUERY
        );

        assertThat(afterAdsEvidence.getId()).isEqualTo(active.getId());
        assertThat(afterAdsEvidence.getStatus()).isEqualTo("ACTIVE");
        assertThat(afterAdsEvidence.getKeyword()).isEqualTo("Milk Bottle");
        assertThat(afterAdsEvidence.getIntentTagsJson()).contains("CORE").doesNotContain("ADS_CANDIDATE");
        assertThat(mapper.keywords).hasSize(1);
        assertThat(mapper.events).hasSize(2);
        assertThat(mapper.events.values())
                .anySatisfy(event -> {
                    assertThat(event.getSourceType()).isEqualTo("ADS_QUERY");
                    assertThat(event.getKeyword()).isEqualTo("milk   bottle");
                    assertThat(event.getKeywordNorm()).isEqualTo("milk bottle");
                });
    }

    @Test
    void productLevelWildcardScopeIsOnlyAllowedForTitleTargets() {
        ProductKeywordService service = new ProductKeywordService(new FakeProductKeywordMapper(), new ProductKeywordNormalizer());

        assertThatThrownBy(() -> service.addManualKeyword(
                context(),
                command("STR108065-NSA", "*", "PSKU-1", "Milk Bottle", List.of("ADS_CANDIDATE"))
        )).isInstanceOf(IllegalArgumentException.class);

        ProductKeywordRecord titleTarget = service.addManualKeyword(
                context(),
                command("STR108065-NSA", "*", "PSKU-1", "Milk Bottle", List.of("TITLE_TARGET"))
        );
        assertThat(titleTarget.getSiteCode()).isEqualTo("*");
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(7L)
                .businessOwnerUserId(99L)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 99L))
                .menuPaths(Set.of("/operations/product-keywords"))
                .build();
    }

    private static ProductKeywordCommand command(
            String storeCode,
            String siteCode,
            String partnerSku,
            String keyword,
            List<String> tags
    ) {
        ProductKeywordCommand command = new ProductKeywordCommand();
        command.setStoreCode(storeCode);
        command.setSiteCode(siteCode);
        command.setPartnerSku(partnerSku);
        command.setKeyword(keyword);
        command.setIntentTags(tags);
        return command;
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
        public int upsertKeyword(ProductKeywordRecord record) {
            String key = scopeKey(
                    record.getOwnerUserId(),
                    record.getStoreCode(),
                    record.getSiteCode(),
                    record.getPartnerSku(),
                    record.getKeywordNorm()
            );
            ProductKeywordRecord existing = keywords.get(key);
            if (existing != null) {
                record.setId(existing.getId());
            }
            keywords.put(key, copy(record));
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
        public List<ProductKeywordUsageEventRecord> listEvents(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                Integer limit
        ) {
            return new ArrayList<>(events.values());
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

        private static ProductKeywordRecord copy(ProductKeywordRecord source) {
            ProductKeywordRecord target = new ProductKeywordRecord();
            target.setId(source.getId());
            target.setOwnerUserId(source.getOwnerUserId());
            target.setStoreCode(source.getStoreCode());
            target.setSiteCode(source.getSiteCode());
            target.setPartnerSku(source.getPartnerSku());
            target.setKeyword(source.getKeyword());
            target.setKeywordNorm(source.getKeywordNorm());
            target.setLocale(source.getLocale());
            target.setStatus(source.getStatus());
            target.setIntentTagsJson(source.getIntentTagsJson());
            target.setSourceSummaryJson(source.getSourceSummaryJson());
            target.setFirstSeenAt(source.getFirstSeenAt());
            target.setLastSeenAt(source.getLastSeenAt());
            target.setCreatedBy(source.getCreatedBy());
            target.setUpdatedBy(source.getUpdatedBy());
            return target;
        }
    }
}
