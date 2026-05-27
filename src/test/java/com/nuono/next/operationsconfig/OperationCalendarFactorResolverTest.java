package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesProductDimensionRepository;
import com.nuono.next.sales.SalesProductDimensionSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationCalendarFactorResolverTest {

    @Test
    void resolvesAllBrandFulltypeAndPskuTargetsWithDeterministicPriority() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, "All Products", "all_products", null, "1.10", 80001L));
        calendarRepository.rules.add(rule(82002L, "Acme Brand", "brand", "Acme", "1.20", 80002L));
        calendarRepository.rules.add(rule(82003L, "Bedding Fulltype", "product_fulltype", "home-bedding-duvet", "1.25", 80003L));
        calendarRepository.rules.add(rule(82004L, "Specific PSKU", "psku", "PSKU-1", "1.40", 80004L));
        InMemorySalesProductDimensionRepository productRepository = new InMemorySalesProductDimensionRepository();
        productRepository.snapshots.add(new SalesProductDimensionSnapshot(
                "PARTNER-1",
                "PSKU-1",
                "Acme",
                "home-bedding-duvet",
                12
        ));
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(calendarRepository, productRepository);

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.40"), result.getAppliedFactor());
        assertEquals(82004L, result.getSelectedRuleId());
        assertEquals("Specific PSKU", result.getSelectedRuleName());
        assertTrue(result.getEvidence().stream().anyMatch(evidence -> evidence.getRuleId().equals(82002L)
                && "matched_lower_priority".equals(evidence.getMatchState())));
        assertTrue(result.getEvidence().stream().anyMatch(evidence -> evidence.getRuleId().equals(82003L)
                && "matched_lower_priority".equals(evidence.getMatchState())));
    }

    @Test
    void latestPublishedVersionWinsForIdenticalTargetScope() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, "Acme Brand v1", "brand", "Acme", "1.10", 80001L));
        calendarRepository.rules.add(rule(82002L, "Acme Brand v2", "brand", "Acme", "1.30", 80005L));
        InMemorySalesProductDimensionRepository productRepository = new InMemorySalesProductDimensionRepository();
        productRepository.snapshots.add(new SalesProductDimensionSnapshot(
                "PARTNER-1",
                "PSKU-1",
                "Acme",
                "home-bedding-duvet",
                12
        ));
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(calendarRepository, productRepository);

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.30"), result.getAppliedFactor());
        assertEquals(82002L, result.getSelectedRuleId());
    }

    @Test
    void resolvesActivityFactorFromCurrentPublishedBundleWithoutRequiringRuleLevelPublish() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, "Legacy Active", "brand", "Acme", "1.10", 80001L));
        calendarRepository.rules.add(rule(82002L, "Bundle Draft Not Current", "brand", "Acme", "1.50", 80002L)
                .withPublishStatus(OperationConfigPublishStatus.DRAFT, 601L, LocalDateTime.of(2026, 1, 1, 0, 0))
                .withBundleVersionId(86000L));
        calendarRepository.currentBundleVersionId = 86001L;
        calendarRepository.rules.add(rule(82003L, "Bundle Current", "brand", "Acme", "1.35", 80003L)
                .withPublishStatus(OperationConfigPublishStatus.DRAFT, 601L, LocalDateTime.of(2026, 1, 1, 0, 0))
                .withBundleVersionId(86001L));
        InMemorySalesProductDimensionRepository productRepository = new InMemorySalesProductDimensionRepository();
        productRepository.snapshots.add(new SalesProductDimensionSnapshot(
                "PARTNER-1",
                "PSKU-1",
                "Acme",
                "home-bedding-duvet",
                12
        ));
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(calendarRepository, productRepository);

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.35"), result.getAppliedFactor());
        assertEquals(82003L, result.getSelectedRuleId());
        assertTrue(result.getEvidence().stream().noneMatch(evidence -> evidence.getRuleId().equals(82002L)));
    }

    @Test
    void currentBundleRuleWinsOverLegacyFallbackAndExposesBundleEvidence() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.currentBundleVersionId = 86010L;
        calendarRepository.rules.add(rule(82001L, "Legacy Later Publish", "all_products", null, "1.90", 90000L));
        calendarRepository.rules.add(rule(82002L, "Current Store Bundle", "all_products", null, "1.15", 80002L)
                .withPublishStatus(OperationConfigPublishStatus.DRAFT, 601L, LocalDateTime.of(2026, 1, 1, 0, 0))
                .withBundleVersionId(86010L));
        OperationCalendarFactorResolver resolver =
                new OperationCalendarFactorResolver(calendarRepository, new InMemorySalesProductDimensionRepository());

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.15"), result.getAppliedFactor());
        assertEquals(82002L, result.getSelectedRuleId());
        assertEquals(86010L, result.getSelectedBundleVersionId());
        assertEquals("OPS_CONFIG_86010", result.getSelectedBundleVersionNo());
        assertEquals("运营发布", result.getSelectedBundleSourceLabel());
        OperationCalendarFactorEvidence selectedEvidence = result.getEvidence().stream()
                .filter(evidence -> evidence.getRuleId().equals(82002L))
                .findFirst()
                .orElseThrow();
        assertEquals("selected", selectedEvidence.getMatchState());
        assertEquals(86010L, selectedEvidence.getBundleVersionId());
        assertEquals("OPS_CONFIG_86010", selectedEvidence.getBundleVersionNo());
        assertEquals("运营发布", selectedEvidence.getBundleSourceLabel());
    }

    @Test
    void recordsCurrentCalendarTypedVersionOnResolutionAndEvidence() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, "Store calendar rule", "all_products", null, "1.18", 80001L));
        InMemoryOperationConfigTypedVersionRepository typedVersionRepository =
                new InMemoryOperationConfigTypedVersionRepository();
        typedVersionRepository.insert(typedVersion(
                "CALENDAR_CONFIG_RAMADAN_2026",
                "2026 斋月日历",
                OperationConfigVersionType.BUSINESS_CALENDAR,
                "501/STR-X-NAE/AE"
        ));
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(
                calendarRepository,
                new InMemorySalesProductDimensionRepository(),
                null,
                typedVersionRepository
        );

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals("CALENDAR_CONFIG_RAMADAN_2026", result.getCalendarVersionNo());
        assertEquals("2026 斋月日历", result.getCalendarVersionName());
        assertEquals("运营发布", result.getCalendarVersionSourceLabel());
        OperationCalendarFactorEvidence selectedEvidence = result.getEvidence().stream()
                .filter(evidence -> evidence.getRuleId().equals(82001L))
                .findFirst()
                .orElseThrow();
        assertEquals("selected", selectedEvidence.getMatchState());
        assertEquals("CALENDAR_CONFIG_RAMADAN_2026", selectedEvidence.getCalendarVersionNo());
        assertEquals("2026 斋月日历", selectedEvidence.getCalendarVersionName());
        assertEquals("运营发布", selectedEvidence.getCalendarVersionSourceLabel());
    }

    @Test
    void currentCalendarTypedVersionContentDrivesFactorResolutionWhenItContainsFactorRows() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        InMemoryOperationConfigTypedVersionRepository typedVersionRepository =
                new InMemoryOperationConfigTypedVersionRepository();
        typedVersionRepository.insert(typedVersion(
                "CALENDAR_CONFIG_TYPED_FACTOR",
                "Typed 斋月日历",
                OperationConfigVersionType.BUSINESS_CALENDAR,
                "501/STR-X-NAE/AE",
                "[{\"groupName\":\"业务日历\",\"itemName\":\"Typed Ramadan Factor\",\"cadence\":\"随时\",\"valueType\":\"日期范围\",\"defaultValue\":\"2026-03-01 ~ 2026-03-31 / 1.33\",\"resultShape\":\"all_products\",\"note\":null}]"
        ));
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(
                calendarRepository,
                new InMemorySalesProductDimensionRepository(),
                null,
                typedVersionRepository
        );

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.33"), result.getAppliedFactor());
        assertEquals("Typed Ramadan Factor", result.getSelectedRuleName());
        assertEquals("CALENDAR_CONFIG_TYPED_FACTOR", result.getCalendarVersionNo());
        assertTrue(result.getEvidence().stream().anyMatch(evidence ->
                "selected".equals(evidence.getMatchState())
                        && "Typed Ramadan Factor".equals(evidence.getRuleName())
                        && "CALENDAR_CONFIG_TYPED_FACTOR".equals(evidence.getCalendarVersionNo())
        ));
    }

    @Test
    void currentBundlePresenceSuppressesLegacyFallbackEvenWhenBundleRuleDoesNotMatchDate() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.currentBundleVersionId = 86010L;
        calendarRepository.rules.add(rule(82001L, "Legacy March Factor", "all_products", null, "1.90", 90000L));
        calendarRepository.rules.add(ruleWithDate(
                82002L,
                501L,
                "STR-X-NAE",
                "AE",
                "Current Bundle April Factor",
                "all_products",
                null,
                "1.15",
                80002L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30)
        ).withPublishStatus(OperationConfigPublishStatus.DRAFT, 601L, LocalDateTime.of(2026, 1, 1, 0, 0))
                .withBundleVersionId(86010L));
        OperationCalendarFactorResolver resolver =
                new OperationCalendarFactorResolver(calendarRepository, new InMemorySalesProductDimensionRepository());

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(BigDecimal.ONE, result.getAppliedFactor());
        assertEquals(null, result.getSelectedRuleId());
        assertTrue(result.getEvidence().stream().noneMatch(evidence -> evidence.getRuleId().equals(82001L)));
    }

    @Test
    void exactStoreSiteRuleWinsOverOwnerWideRuleForSameTargetPriority() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, 501L, "*", "*", "Owner wide all", "all_products", null, "1.80", 90000L));
        calendarRepository.rules.add(rule(82002L, 501L, "STR-X-NAE", "AE", "Store all", "all_products", null, "1.10", 80001L));
        OperationCalendarFactorResolver resolver =
                new OperationCalendarFactorResolver(calendarRepository, new InMemorySalesProductDimensionRepository());

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.10"), result.getAppliedFactor());
        assertEquals(82002L, result.getSelectedRuleId());
    }

    @Test
    void categoryTargetMatchesProductFulltypePrefix() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, "Home Bedding Category", "category", "home-bedding", "1.22", 80001L));
        InMemorySalesProductDimensionRepository productRepository = new InMemorySalesProductDimensionRepository();
        productRepository.snapshots.add(new SalesProductDimensionSnapshot(
                "PARTNER-1",
                "PSKU-1",
                "Acme",
                "home-bedding-duvet",
                12
        ));
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(calendarRepository, productRepository);

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(new BigDecimal("1.22"), result.getAppliedFactor());
        assertEquals(82001L, result.getSelectedRuleId());
    }

    @Test
    void doesNotMatchRulesFromAnotherOwnerOrStore() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, 502L, "STR-OTHER", "AE", "Other owner all", "all_products", null, "1.80", 80001L));
        InMemorySalesProductDimensionRepository productRepository = new InMemorySalesProductDimensionRepository();
        OperationCalendarFactorResolver resolver = new OperationCalendarFactorResolver(calendarRepository, productRepository);

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "PARTNER-1", "PSKU-1"));

        assertEquals(BigDecimal.ONE, result.getAppliedFactor());
        assertEquals(null, result.getSelectedRuleId());
        assertTrue(result.getEvidence().isEmpty());
    }

    @Test
    void exposesNoProductProjectionEvidenceForDimensionTargets() {
        InMemoryOperationCalendarRuleRepository calendarRepository = new InMemoryOperationCalendarRuleRepository();
        calendarRepository.rules.add(rule(82001L, "Acme Brand", "brand", "Acme", "1.20", 80001L));
        OperationCalendarFactorResolver resolver =
                new OperationCalendarFactorResolver(calendarRepository, new InMemorySalesProductDimensionRepository());

        OperationCalendarFactorMatchResult result = resolver.resolve(query(501L, "UNKNOWN", "UNKNOWN"));

        assertEquals(BigDecimal.ONE, result.getAppliedFactor());
        assertEquals(null, result.getSelectedRuleId());
        assertTrue(result.getEvidence().stream().anyMatch(evidence -> evidence.getRuleId().equals(82001L)
                && "product_projection_missing".equals(evidence.getMatchState())));
    }

    private static OperationCalendarFactorQuery query(Long ownerUserId, String partnerSku, String sku) {
        return new OperationCalendarFactorQuery(
                ownerUserId,
                "STR-X-NAE",
                "AE",
                LocalDate.of(2026, 3, 5),
                partnerSku,
                sku
        );
    }

    private static OperationCalendarRule rule(
            Long id,
            String ruleName,
            String targetScopeType,
            String targetScopeValue,
            String factor,
            Long publishRecordId
    ) {
        return rule(id, 501L, "STR-X-NAE", "AE", ruleName, targetScopeType, targetScopeValue, factor, publishRecordId);
    }

    private static OperationCalendarRule rule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String targetScopeType,
            String targetScopeValue,
            String factor,
            Long publishRecordId
    ) {
        return ruleWithDate(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleName,
                targetScopeType,
                targetScopeValue,
                factor,
                publishRecordId,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );
    }

    private static OperationConfigTypedVersion typedVersion(
            String versionNo,
            String displayName,
            OperationConfigVersionType configType,
            String scopeSummary
    ) {
        return typedVersion(versionNo, displayName, configType, scopeSummary, "[]");
    }

    private static OperationConfigTypedVersion typedVersion(
            String versionNo,
            String displayName,
            OperationConfigVersionType configType,
            String scopeSummary,
            String contentJson
    ) {
        return new OperationConfigTypedVersion(
                91001L,
                versionNo,
                displayName,
                configType.name(),
                "CURRENT",
                OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO,
                "运营发布",
                "1 条日历配置",
                1,
                scopeSummary,
                contentJson,
                601L,
                601L,
                LocalDateTime.of(2026, 5, 20, 9, 0),
                LocalDateTime.of(2026, 5, 20, 9, 0)
        );
    }

    private static OperationCalendarRule ruleWithDate(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String targetScopeType,
            String targetScopeValue,
            String factor,
            Long publishRecordId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        return new OperationCalendarRule(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleName,
                "holiday",
                dateFrom,
                dateTo,
                null,
                targetScopeType,
                targetScopeValue,
                new BigDecimal(factor),
                "demand_uplift",
                true,
                publishRecordId,
                OperationConfigPublishStatus.PUBLISHED,
                601L,
                601L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(publishRecordId)
        );
    }

    private static class InMemoryOperationCalendarRuleRepository implements OperationCalendarRuleRepository {
        private final List<OperationCalendarRule> rules = new ArrayList<>();
        private Long currentBundleVersionId;

        @Override
        public Long nextRuleId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertRule(OperationCalendarRule rule) {
            rules.add(rule);
        }

        @Override
        public void updateRule(OperationCalendarRule rule) {
            rules.removeIf(current -> current.getId().equals(rule.getId()));
            rules.add(rule);
        }

        @Override
        public OperationCalendarRule findRule(Long id) {
            return rules.stream().filter(rule -> id.equals(rule.getId())).findFirst().orElse(null);
        }

        @Override
        public List<OperationCalendarRule> listActiveRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .filter(OperationCalendarRule::isEnabled)
                    .filter(rule -> OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus()))
                    .sorted(Comparator.comparing(OperationCalendarRule::getId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationCalendarRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
            return listActiveRules(ownerUserId, storeCode, siteCode);
        }

        @Override
        public List<OperationCalendarRule> listRulesByBundleVersion(Long bundleVersionId) {
            return rules.stream()
                    .filter(rule -> bundleVersionId.equals(rule.getBundleVersionId()))
                    .collect(Collectors.toList());
        }

        @Override
        public int countRulesByBundleVersion(Long bundleVersionId) {
            return (int) rules.stream()
                    .filter(rule -> bundleVersionId.equals(rule.getBundleVersionId()))
                    .count();
        }

        @Override
        public List<OperationCalendarRule> listActiveRulesForFactorResolution(Long ownerUserId, String storeCode, String siteCode) {
            return rules.stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()) || "*".equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()) || "*".equals(rule.getSiteCode()))
                    .filter(OperationCalendarRule::isEnabled)
                    .filter(rule -> rule.getBundleVersionId() == null
                            ? OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus())
                            : rule.getBundleVersionId().equals(currentBundleVersionId))
                    .sorted(Comparator.comparing(OperationCalendarRule::getId))
                    .collect(Collectors.toList());
        }
    }

    private static class InMemorySalesProductDimensionRepository implements SalesProductDimensionRepository {
        private final List<SalesProductDimensionSnapshot> snapshots = new ArrayList<>();

        @Override
        public List<SalesProductDimensionSnapshot> list(SalesFactQuery query) {
            return snapshots.stream()
                    .filter(snapshot -> query.getPartnerSku() == null || query.getPartnerSku().equals(snapshot.getPartnerSku()))
                    .filter(snapshot -> query.getSku() == null || query.getSku().equals(snapshot.getSku()))
                    .collect(Collectors.toList());
        }
    }
}
