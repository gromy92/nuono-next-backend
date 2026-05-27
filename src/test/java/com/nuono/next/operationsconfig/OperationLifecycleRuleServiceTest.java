package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.versioning.VersionPublishAuditLog;
import com.nuono.next.versioning.VersionPublishRecord;
import com.nuono.next.versioning.VersionPublishRepository;
import com.nuono.next.versioning.VersionPublishService;
import com.nuono.next.versioning.VersionPublishStatus;
import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleRuleSet;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OperationLifecycleRuleServiceTest {

    @Test
    void springContextCanCreateLifecycleRuleServiceWithRepositoryConstructor() {
        new ApplicationContextRunner()
                .withBean(OperationLifecycleRuleRepository.class, InMemoryOperationLifecycleRuleRepository::new)
                .withBean(OperationConfigScopeRepository.class, InMemoryOperationConfigScopeRepository::new)
                .withBean(OperationConfigBundleRepository.class, InMemoryOperationConfigBundleRepository::new)
                .withBean(VersionPublishRepository.class, InMemoryVersionPublishRepository::new)
                .withBean(OperationConfigScopeService.class)
                .withBean(VersionPublishService.class)
                .withBean(OperationLifecycleRuleService.class)
                .run(context -> assertTrue(
                        context.getStartupFailure() == null,
                        () -> String.valueOf(context.getStartupFailure())
                ));
    }

    @Test
    void currentStateFallsBackToExplicitDefaultV1WhenNoPublishedRuleExists() {
        TestHarness harness = new TestHarness();

        OperationLifecycleRuleStateView state = harness.service.currentState(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE"
        );

        assertEquals("DEFAULT_V1", state.getCurrent().getRuleVersion());
        assertEquals(true, state.getCurrent().isFallback());
        assertEquals(60, state.getCurrent().getThresholds().getNewMaxAgeDays());
        assertEquals(new BigDecimal("0.5000"), state.getCurrent().getThresholds().getGrowthMinSalesGrowthRate());
        assertEquals(null, state.getDraft());
        assertTrue(state.getDiffs().isEmpty());
    }

    @Test
    void createsEditsPublishesAndReadsLifecycleRuleVersionWithDiffAndHistory() {
        TestHarness harness = new TestHarness();

        OperationLifecycleRule draft = harness.service.createDraftFromCurrent(
                operatorContext(),
                new OperationLifecycleRuleCreateDraftCommand(List.of(), 501L, "STR-X-NAE", "AE")
        );
        OperationLifecycleRule edited = harness.service.saveDraft(
                operatorContext(),
                new OperationLifecycleRuleDraftCommand(
                        draft.getId(),
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        OperationLifecycleRuleThresholds.defaultV1()
                                .withGrowthMinSalesGrowthRate(new BigDecimal("0.6500"))
                                .withLongTailMaxMonthlySales(new BigDecimal("8.0000"))
                )
        );
        OperationLifecycleRuleStateView draftState = harness.service.currentState(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE"
        );
        OperationLifecycleRule published = harness.service.publish(
                operatorContext(),
                List.of(),
                edited.getId(),
                "publish lifecycle thresholds"
        );
        OperationLifecycleRuleStateView publishedState = harness.service.currentState(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE"
        );

        assertEquals(OperationConfigPublishStatus.DRAFT, draft.getPublishStatus());
        assertEquals(new BigDecimal("0.6500"), edited.getThresholds().getGrowthMinSalesGrowthRate());
        assertEquals("DEFAULT_V1", draftState.getCurrent().getRuleVersion());
        assertEquals(2, draftState.getDiffs().size());
        assertEquals("growthMinSalesGrowthRate", draftState.getDiffs().get(0).getField());
        assertEquals("0.5000", draftState.getDiffs().get(0).getBeforeValue());
        assertEquals("0.6500", draftState.getDiffs().get(0).getAfterValue());
        assertEquals(OperationConfigPublishStatus.PUBLISHED, published.getPublishStatus());
        assertEquals(published.getRuleVersion(), publishedState.getCurrent().getRuleVersion());
        assertEquals(false, publishedState.getCurrent().isFallback());
        assertEquals(1, publishedState.getHistory().size());
        assertEquals(published.getPublishRecordId(), harness.versionRepository
                .findRecord(published.getPublishRecordId())
                .getId());
        assertEquals(VersionPublishStatus.PUBLISHED, harness.versionRepository
                .findRecord(published.getPublishRecordId())
                .getStatus());
    }

    @Test
    void nextDraftCopiesCurrentPublishedThresholdsInsteadOfDefaultV1() {
        TestHarness harness = new TestHarness();
        OperationLifecycleRule firstDraft = harness.service.createDraftFromCurrent(
                operatorContext(),
                new OperationLifecycleRuleCreateDraftCommand(List.of(), 501L, "STR-X-NAE", "AE")
        );
        OperationLifecycleRule edited = harness.service.saveDraft(
                operatorContext(),
                new OperationLifecycleRuleDraftCommand(
                        firstDraft.getId(),
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        firstDraft.getThresholds().withGrowthMinMonthlySales(new BigDecimal("18.0000"))
                )
        );
        harness.service.publish(operatorContext(), List.of(), edited.getId(), "publish v1");

        OperationLifecycleRule secondDraft = harness.service.createDraftFromCurrent(
                operatorContext(),
                new OperationLifecycleRuleCreateDraftCommand(List.of(), 501L, "STR-X-NAE", "AE")
        );

        assertEquals(new BigDecimal("18.0000"), secondDraft.getThresholds().getGrowthMinMonthlySales());
        assertEquals(edited.getRuleVersion(), secondDraft.getSourceRuleVersion());
    }

    @Test
    void operationsConfigProviderReturnsDefaultThenPublishedLifecycleRuleSet() {
        TestHarness harness = new TestHarness();
        OperationsConfigProductLifecycleRuleProvider provider =
                new OperationsConfigProductLifecycleRuleProvider(harness.lifecycleRepository);
        ProductLifecycleCalculationScope scope = new ProductLifecycleCalculationScope(
                501L,
                "STR-X-NAE",
                "AE",
                java.time.LocalDate.of(2026, 5, 20),
                "DEFAULT_V1",
                false
        );

        ProductLifecycleRuleSet fallback = provider.resolve(scope);
        OperationLifecycleRule draft = harness.service.createDraftFromCurrent(
                operatorContext(),
                new OperationLifecycleRuleCreateDraftCommand(List.of(), 501L, "STR-X-NAE", "AE")
        );
        OperationLifecycleRule published = harness.service.publish(operatorContext(), List.of(), draft.getId(), "publish");
        ProductLifecycleRuleSet configured = provider.resolve(scope);

        assertEquals("DEFAULT_V1", fallback.getRuleVersion());
        assertEquals(true, fallback.isFallback());
        assertEquals(published.getRuleVersion(), configured.getRuleVersion());
        assertEquals(false, configured.isFallback());
    }

    @Test
    void bundleOwnedLifecycleDraftIsListedInBundleStateButNotCurrentUntilBundlePublish() {
        TestHarness harness = new TestHarness();
        OperationConfigBundleVersionView bundle = harness.createScopedBundle("2026 lifecycle suite");

        OperationLifecycleRule saved = harness.service.saveDraft(
                operatorContext(),
                new OperationLifecycleRuleDraftCommand(
                        null,
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        OperationLifecycleRuleThresholds.defaultV1()
                                .withGrowthMinMonthlySales(new BigDecimal("22.0000")),
                        bundle.getId()
                )
        );
        OperationLifecycleRuleStateView bundleState = harness.service.currentState(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE",
                bundle.getId()
        );
        OperationLifecycleRuleStateView legacyState = harness.service.currentState(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE"
        );
        harness.bundleService.publish(operatorContext(), bundle.getId(), "publish lifecycle suite");
        harness.lifecycleRepository.currentBundleVersionId = bundle.getId();
        ProductLifecycleRuleSet ruleSet = new OperationsConfigProductLifecycleRuleProvider(harness.lifecycleRepository)
                .resolve(new ProductLifecycleCalculationScope(
                        501L,
                        "STR-X-NAE",
                        "AE",
                        java.time.LocalDate.of(2026, 5, 20),
                        "DEFAULT_V1",
                        false
                ));

        assertEquals(bundle.getId(), saved.getBundleVersionId());
        assertEquals(OperationConfigPublishStatus.DRAFT, saved.getPublishStatus());
        assertEquals("DEFAULT_V1", bundleState.getCurrent().getRuleVersion());
        assertEquals(saved.getRuleVersion(), bundleState.getDraft().getRuleVersion());
        assertEquals(List.of(saved.getRuleVersion()), bundleState.getHistory().stream()
                .map(OperationLifecycleRuleView::getRuleVersion)
                .collect(Collectors.toList()));
        assertEquals(null, legacyState.getDraft());
        assertEquals("生命周期规则 1 组", harness.bundleRepository.findBundle(bundle.getId())
                .orElseThrow()
                .getLifecycleRuleSummary());
        assertEquals(bundle.getVersionNo(), ruleSet.getRuleVersion());
        assertEquals(false, ruleSet.isFallback());
        assertEquals(OperationConfigPublishStatus.DRAFT, harness.lifecycleRepository.findRule(saved.getId()).getPublishStatus());
        assertTrue(harness.versionRepository
                .findCurrent(OperationLifecycleRuleService.DOMAIN_TYPE, saved.getId())
                .isEmpty());
    }

    @Test
    void currentBundleLifecycleRuleWinsOverNewerLegacyPublishedRule() {
        TestHarness harness = new TestHarness();
        OperationConfigBundleVersionView bundle = harness.createScopedBundle("2026 lifecycle suite");
        harness.lifecycleRepository.insertRule(lifecycleRule(
                83001L,
                "BUNDLE_RULE_v1",
                OperationConfigPublishStatus.DRAFT,
                bundle.getId(),
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        harness.lifecycleRepository.insertRule(lifecycleRule(
                83002L,
                "LEGACY_RULE_v9",
                OperationConfigPublishStatus.PUBLISHED,
                null,
                LocalDateTime.of(2026, 2, 1, 0, 0)
        ));
        harness.lifecycleRepository.currentBundleVersionId = bundle.getId();
        OperationsConfigProductLifecycleRuleProvider provider =
                new OperationsConfigProductLifecycleRuleProvider(
                        harness.lifecycleRepository,
                        harness.bundleRepository
                );

        ProductLifecycleRuleSet ruleSet = provider.resolve(new ProductLifecycleCalculationScope(
                501L,
                "STR-X-NAE",
                "AE",
                java.time.LocalDate.of(2026, 5, 20),
                "DEFAULT_V1",
                false
        ));

        assertEquals(bundle.getVersionNo(), ruleSet.getRuleVersion());
        assertEquals(false, ruleSet.isFallback());
    }

    @Test
    void lifecycleProviderRecordsCurrentTypedLifecycleVersionEvidence() {
        TestHarness harness = new TestHarness();
        harness.lifecycleRepository.insertRule(lifecycleRule(
                83001L,
                "LEGACY_RULE_v1",
                OperationConfigPublishStatus.PUBLISHED,
                null,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        InMemoryOperationConfigTypedVersionRepository typedVersionRepository =
                new InMemoryOperationConfigTypedVersionRepository();
        typedVersionRepository.insert(typedVersion(
                "LIFECYCLE_CONFIG_RAMADAN_2026",
                "2026 生命周期阈值",
                OperationConfigVersionType.PRODUCT_LIFECYCLE,
                "501/STR-X-NAE/AE"
        ));
        OperationsConfigProductLifecycleRuleProvider provider =
                new OperationsConfigProductLifecycleRuleProvider(
                        harness.lifecycleRepository,
                        harness.bundleRepository,
                        typedVersionRepository
                );

        ProductLifecycleRuleSet ruleSet = provider.resolve(new ProductLifecycleCalculationScope(
                501L,
                "STR-X-NAE",
                "AE",
                java.time.LocalDate.of(2026, 5, 20),
                "DEFAULT_V1",
                false
        ));

        assertEquals("LIFECYCLE_CONFIG_RAMADAN_2026", ruleSet.getRuleVersion());
        assertEquals(false, ruleSet.isFallback());
        assertEquals("LIFECYCLE_CONFIG_RAMADAN_2026", ruleSet.getLifecycleVersionNo());
        assertEquals("2026 生命周期阈值", ruleSet.getLifecycleVersionName());
        assertEquals("运营发布", ruleSet.getLifecycleVersionSourceLabel());
    }

    @Test
    void lifecycleProviderUsesCurrentTypedLifecycleVersionContentAsThresholds() {
        TestHarness harness = new TestHarness();
        InMemoryOperationConfigTypedVersionRepository typedVersionRepository =
                new InMemoryOperationConfigTypedVersionRepository();
        typedVersionRepository.insert(typedVersion(
                "LIFECYCLE_CONFIG_STRICT_LONGTAIL",
                "严格长尾阈值",
                OperationConfigVersionType.PRODUCT_LIFECYCLE,
                "501/STR-X-NAE/AE",
                "["
                        + "{\"groupName\":\"长尾期\",\"itemName\":\"长尾期最大月销红量\",\"cadence\":\"随时\",\"valueType\":\"数值\",\"defaultValue\":\"2\",\"resultShape\":null,\"note\":null},"
                        + "{\"groupName\":\"稳定期\",\"itemName\":\"稳定期最小浏览环比增长率\",\"cadence\":\"随时\",\"valueType\":\"数值\",\"defaultValue\":\"-0.15\",\"resultShape\":null,\"note\":null},"
                        + "{\"groupName\":\"衰退期\",\"itemName\":\"衰退最小销量环比增长率\",\"cadence\":\"随时\",\"valueType\":\"数值\",\"defaultValue\":\"-0.25\",\"resultShape\":null,\"note\":null}"
                        + "]"
        ));
        OperationsConfigProductLifecycleRuleProvider provider =
                new OperationsConfigProductLifecycleRuleProvider(
                        harness.lifecycleRepository,
                        harness.bundleRepository,
                        typedVersionRepository
                );

        ProductLifecycleRuleSet ruleSet = provider.resolve(new ProductLifecycleCalculationScope(
                501L,
                "STR-X-NAE",
                "AE",
                java.time.LocalDate.of(2026, 5, 20),
                "DEFAULT_V1",
                false
        ));

        assertEquals("LIFECYCLE_CONFIG_STRICT_LONGTAIL", ruleSet.getRuleVersion());
        assertEquals(false, ruleSet.isFallback());
        assertEquals("2.0000", ruleSet.getThresholds().getLongTailMaxMonthlySales().toPlainString());
        assertEquals("-0.1500", ruleSet.getThresholds().getStableMinPvGrowthRate().toPlainString());
        assertEquals("-0.2500", ruleSet.getThresholds().getDeclineMaxSalesGrowthRate().toPlainString());
    }

    @Test
    void rejectsRuleLevelPublishForBundleOwnedLifecycleDrafts() {
        TestHarness harness = new TestHarness();
        OperationConfigBundleVersionView bundle = harness.createScopedBundle("2026 lifecycle suite");
        OperationLifecycleRule saved = harness.service.saveDraft(
                operatorContext(),
                new OperationLifecycleRuleDraftCommand(
                        null,
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        OperationLifecycleRuleThresholds.defaultV1(),
                        bundle.getId()
                )
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> harness.service.publish(operatorContext(), List.of(), saved.getId(), "rule-level publish")
        );

        assertEquals(
                "Bundle-owned lifecycle rules are published by the enclosing operations config bundle",
                error.getMessage()
        );
    }

    @Test
    void systemAdminPublishesBossWideLifecycleRuleResolvedForConcreteStoreWithSystemSource() {
        TestHarness harness = new TestHarness();
        harness.scopeRepository.addStore(501L, 702L, "PRJ-X", "Xingyao", "STR-X-NSA", "SA");
        OperationsConfigProductLifecycleRuleProvider provider =
                new OperationsConfigProductLifecycleRuleProvider(harness.lifecycleRepository);

        OperationLifecycleRule draft = harness.service.createDraftFromCurrent(
                systemAdminContext(),
                new OperationLifecycleRuleCreateDraftCommand(List.of(501L), 501L, "*", "*")
        );
        OperationLifecycleRule saved = harness.service.saveDraft(
                systemAdminContext(),
                new OperationLifecycleRuleDraftCommand(
                        draft.getId(),
                        List.of(501L),
                        501L,
                        "*",
                        "*",
                        draft.getThresholds().withGrowthMinMonthlySales(new BigDecimal("16.0000"))
                )
        );
        OperationLifecycleRule published = harness.service.publish(
                systemAdminContext(),
                List.of(501L),
                saved.getId(),
                "publish boss-wide lifecycle thresholds"
        );

        OperationLifecycleRuleStateView storeState = harness.service.currentState(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE"
        );
        ProductLifecycleRuleSet ruleSet = provider.resolve(new ProductLifecycleCalculationScope(
                501L,
                "STR-X-NSA",
                "SA",
                java.time.LocalDate.of(2026, 5, 20),
                "DEFAULT_V1",
                false
        ));

        assertEquals("*", published.getStoreCode());
        assertEquals("*", published.getSiteCode());
        assertEquals("系统发布", published.getPublishSourceLabel());
        assertEquals(published.getRuleVersion(), storeState.getCurrent().getRuleVersion());
        assertEquals("系统发布", storeState.getCurrent().getPublishSourceLabel());
        assertEquals(published.getRuleVersion(), ruleSet.getRuleVersion());
        assertEquals(false, ruleSet.isFallback());
    }

    @Test
    void rejectsInvalidThresholdsBeforeSavingDraft() {
        TestHarness harness = new TestHarness();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> harness.service.saveDraft(
                        operatorContext(),
                        new OperationLifecycleRuleDraftCommand(
                                null,
                                List.of(),
                                501L,
                                "STR-X-NAE",
                                "AE",
                                OperationLifecycleRuleThresholds.defaultV1()
                                        .withNewMinAgeDays(90)
                        )
                )
        );

        assertEquals("newMinAgeDays must be less than or equal to newMaxAgeDays", error.getMessage());
        assertTrue(harness.lifecycleRepository.rules.isEmpty());
    }

    @Test
    void rejectsUnauthorizedScopeBeforeCreatingLifecycleDraft() {
        TestHarness harness = new TestHarness();

        BusinessAccessDeniedException error = assertThrows(
                BusinessAccessDeniedException.class,
                () -> harness.service.createDraftFromCurrent(
                        operatorContext(),
                        new OperationLifecycleRuleCreateDraftCommand(List.of(), 999L, "STR-Z-NAE", "AE")
                )
        );

        assertTrue(error.getMessage().contains("当前账号不能操作该店铺"));
        assertTrue(harness.lifecycleRepository.rules.isEmpty());
    }

    private static OperationLifecycleRule lifecycleRule(
            Long id,
            String ruleVersion,
            OperationConfigPublishStatus publishStatus,
            Long bundleVersionId,
            LocalDateTime updatedAt
    ) {
        return new OperationLifecycleRule(
                id,
                501L,
                "STR-X-NAE",
                "AE",
                ruleVersion,
                "DEFAULT_V1",
                OperationLifecycleRuleThresholds.defaultV1(),
                80000L + id,
                publishStatus,
                "operations",
                "运营发布",
                601L,
                601L,
                updatedAt,
                updatedAt,
                bundleVersionId
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
                OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO,
                "运营发布",
                "14 条生命周期配置",
                14,
                scopeSummary,
                contentJson,
                601L,
                601L,
                LocalDateTime.of(2026, 5, 20, 9, 0),
                LocalDateTime.of(2026, 5, 20, 9, 0)
        );
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR-X-NAE"))
                .storeOwnerUserIds(Map.of("STR-X-NAE", 501L))
                .menuPaths(Set.of("/operations/config/lifecycle-rules"))
                .build();
    }

    private static BusinessAccessContext systemAdminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(1L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/operations/config/lifecycle-rules"))
                .build();
    }

    private static class TestHarness {
        private final InMemoryOperationConfigScopeRepository scopeRepository =
                new InMemoryOperationConfigScopeRepository();
        private final InMemoryOperationLifecycleRuleRepository lifecycleRepository =
                new InMemoryOperationLifecycleRuleRepository();
        private final InMemoryOperationConfigBundleRepository bundleRepository =
                new InMemoryOperationConfigBundleRepository();
        private final InMemoryVersionPublishRepository versionRepository =
                new InMemoryVersionPublishRepository();
        private final OperationConfigBundleService bundleService;
        private final OperationLifecycleRuleService service;

        TestHarness() {
            scopeRepository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
            OperationConfigScopeService scopeService = new OperationConfigScopeService(scopeRepository);
            VersionPublishService publishService = new VersionPublishService(versionRepository);
            bundleService = new OperationConfigBundleService(
                    bundleRepository,
                    scopeService,
                    publishService
            );
            service = new OperationLifecycleRuleService(
                    lifecycleRepository,
                    bundleRepository,
                    scopeService,
                    publishService
            );
        }

        private OperationConfigBundleVersionView createScopedBundle(String displayName) {
            OperationConfigBundleVersionView bundle = bundleService.createEmptyDraft(
                    operatorContext(),
                    new OperationConfigBundleDraftCommand(displayName)
            );
            bundleService.updateScope(
                    operatorContext(),
                    bundle.getId(),
                    new OperationConfigBundleScopeCommand(
                            List.of(),
                            List.of(new OperationConfigBundleScopeStoreCommand(501L, "STR-X-NAE", "AE"))
                    )
            );
            return bundle;
        }
    }

    private static class InMemoryOperationConfigScopeRepository implements OperationConfigScopeRepository {
        private final Map<Long, List<OperationConfigStoreScope>> storesByBoss = new LinkedHashMap<>();

        void addStore(
                Long ownerUserId,
                Long logicalStoreId,
                String projectCode,
                String projectName,
                String storeCode,
                String siteCode
        ) {
            storesByBoss.computeIfAbsent(ownerUserId, ignored -> new ArrayList<>())
                    .add(new OperationConfigStoreScope(
                            ownerUserId,
                            logicalStoreId,
                            projectCode,
                            projectName,
                            storeCode,
                            siteCode
                    ));
        }

        @Override
        public List<OperationConfigBossOption> listBossOptions() {
            return List.of();
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds) {
            return bossUserIds.stream()
                    .flatMap(ownerUserId -> storesByBoss.getOrDefault(ownerUserId, List.of()).stream())
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes) {
            return storesByBoss.values().stream()
                    .flatMap(List::stream)
                    .filter(store -> storeCodes.contains(store.getStoreCode()))
                    .collect(Collectors.toList());
        }
    }

    private static class InMemoryOperationLifecycleRuleRepository implements OperationLifecycleRuleRepository {
        private long nextRuleId = 83000L;
        private final Map<Long, OperationLifecycleRule> rules = new LinkedHashMap<>();
        private Long currentBundleVersionId;

        @Override
        public Long nextRuleId() {
            nextRuleId += 1;
            return nextRuleId;
        }

        @Override
        public void insertRule(OperationLifecycleRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public void updateRule(OperationLifecycleRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public OperationLifecycleRule findRule(Long id) {
            return rules.get(id);
        }

        @Override
        public List<OperationLifecycleRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> rule.getBundleVersionId() == null)
                    .filter(rule -> (storeCode.equals(rule.getStoreCode()) && siteCode.equals(rule.getSiteCode()))
                            || ("*".equals(rule.getStoreCode())
                                    && "*".equals(rule.getSiteCode())
                                    && OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus())))
                    .sorted(Comparator.comparing(OperationLifecycleRule::getUpdatedAt).reversed())
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<OperationLifecycleRule> findLatestPublished(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> rule.getBundleVersionId() == null
                            ? OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus())
                            : rule.getBundleVersionId().equals(currentBundleVersionId))
                    .filter(rule -> (storeCode.equals(rule.getStoreCode()) && siteCode.equals(rule.getSiteCode()))
                            || ("*".equals(rule.getStoreCode()) && "*".equals(rule.getSiteCode())))
                    .sorted(Comparator
                            .comparingInt((OperationLifecycleRule rule) -> rule.getBundleVersionId() == null ? 0 : 1)
                            .reversed()
                            .thenComparing(Comparator
                                    .comparingInt((OperationLifecycleRule rule) -> storeCode.equals(rule.getStoreCode()) && siteCode.equals(rule.getSiteCode()) ? 2 : 1)
                                    .reversed())
                            .thenComparing(OperationLifecycleRule::getUpdatedAt, Comparator.reverseOrder()))
                    .findFirst();
        }

        @Override
        public Optional<OperationLifecycleRule> findLatestDraft(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> rule.getBundleVersionId() == null)
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .sorted(Comparator.comparing(OperationLifecycleRule::getUpdatedAt).reversed())
                    .filter(rule -> OperationConfigPublishStatus.DRAFT.equals(rule.getPublishStatus()))
                    .findFirst();
        }

        @Override
        public List<OperationLifecycleRule> listRulesByBundleVersion(Long bundleVersionId) {
            return rules.values().stream()
                    .filter(rule -> bundleVersionId.equals(rule.getBundleVersionId()))
                    .collect(Collectors.toList());
        }

        @Override
        public int countRulesByBundleVersion(Long bundleVersionId) {
            return (int) rules.values().stream()
                    .filter(rule -> bundleVersionId.equals(rule.getBundleVersionId()))
                    .count();
        }
    }

    private static class InMemoryOperationConfigBundleRepository implements OperationConfigBundleRepository {
        private long nextBundleId = 86000L;
        private final Map<Long, OperationConfigBundle> bundles = new LinkedHashMap<>();
        private final Map<Long, List<OperationConfigBundleScopeStore>> scopes = new LinkedHashMap<>();

        @Override
        public Long nextBundleId() {
            return nextBundleId++;
        }

        @Override
        public void insert(OperationConfigBundle bundle) {
            bundles.put(bundle.getId(), bundle);
        }

        @Override
        public List<OperationConfigBundle> listBundles() {
            return new ArrayList<>(bundles.values());
        }

        @Override
        public Optional<OperationConfigBundle> findBundle(Long bundleId) {
            return Optional.ofNullable(bundles.get(bundleId));
        }

        @Override
        public void replaceScopeStores(Long bundleId, List<OperationConfigBundleScopeStore> scopeStores) {
            scopes.put(bundleId, new ArrayList<>(scopeStores));
        }

        @Override
        public List<OperationConfigBundleScopeStore> listScopeStores(Long bundleId) {
            return new ArrayList<>(scopes.getOrDefault(bundleId, List.of()));
        }

        @Override
        public void updateScopeSummary(Long bundleId, String scopeSummary, Integer affectedStoreCount, Long updatedBy) {
            OperationConfigBundle bundle = bundles.get(bundleId);
            bundles.put(bundleId, new OperationConfigBundle(
                    bundle.getId(),
                    bundle.getPublishRecordId(),
                    bundle.getVersionNo(),
                    bundle.getDisplayName(),
                    bundle.getPublishSourceRole(),
                    bundle.getPublishSourceLabel(),
                    scopeSummary,
                    affectedStoreCount,
                    bundle.getActivityRuleCount(),
                    bundle.getLifecycleRuleSummary(),
                    bundle.getCreatedBy(),
                    updatedBy,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }

        @Override
        public void updateActivityRuleCount(Long bundleId, Integer activityRuleCount, Long updatedBy) {
            OperationConfigBundle bundle = bundles.get(bundleId);
            bundles.put(bundleId, new OperationConfigBundle(
                    bundle.getId(),
                    bundle.getPublishRecordId(),
                    bundle.getVersionNo(),
                    bundle.getDisplayName(),
                    bundle.getPublishSourceRole(),
                    bundle.getPublishSourceLabel(),
                    bundle.getScopeSummary(),
                    bundle.getAffectedStoreCount(),
                    activityRuleCount,
                    bundle.getLifecycleRuleSummary(),
                    bundle.getCreatedBy(),
                    updatedBy,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }

        @Override
        public void updateLifecycleRuleSummary(Long bundleId, String lifecycleRuleSummary, Long updatedBy) {
            OperationConfigBundle bundle = bundles.get(bundleId);
            bundles.put(bundleId, new OperationConfigBundle(
                    bundle.getId(),
                    bundle.getPublishRecordId(),
                    bundle.getVersionNo(),
                    bundle.getDisplayName(),
                    bundle.getPublishSourceRole(),
                    bundle.getPublishSourceLabel(),
                    bundle.getScopeSummary(),
                    bundle.getAffectedStoreCount(),
                    bundle.getActivityRuleCount(),
                    lifecycleRuleSummary,
                    bundle.getCreatedBy(),
                    updatedBy,
                    bundle.getCreatedAt(),
                    bundle.getUpdatedAt()
            ));
        }

        @Override
        public void deleteBundle(Long bundleId) {
            bundles.remove(bundleId);
            scopes.remove(bundleId);
        }
    }

    private static class InMemoryVersionPublishRepository implements VersionPublishRepository {
        private long nextRecordId = 80000L;
        private long nextAuditId = 81000L;
        private final Map<Long, VersionPublishRecord> records = new LinkedHashMap<>();
        private final List<VersionPublishAuditLog> auditLogs = new ArrayList<>();

        @Override
        public Long nextRecordId() {
            nextRecordId += 1;
            return nextRecordId;
        }

        @Override
        public Long nextAuditId() {
            nextAuditId += 1;
            return nextAuditId;
        }

        @Override
        public void insertRecord(VersionPublishRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public void updateRecord(VersionPublishRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public VersionPublishRecord findRecord(Long id) {
            return records.get(id);
        }

        @Override
        public Optional<VersionPublishRecord> findCurrent(String domainType, Long domainRefId) {
            return records.values().stream()
                    .filter(record -> domainType.equals(record.getDomainType()))
                    .filter(record -> domainRefId.equals(record.getDomainRefId()))
                    .filter(record -> VersionPublishStatus.PUBLISHED.equals(record.getStatus()))
                    .max(Comparator.comparing(VersionPublishRecord::getPublishedAt)
                            .thenComparing(VersionPublishRecord::getId));
        }

        @Override
        public List<VersionPublishRecord> listRecords(String domainType, Long domainRefId) {
            return records.values().stream()
                    .filter(record -> domainType.equals(record.getDomainType()))
                    .filter(record -> domainRefId.equals(record.getDomainRefId()))
                    .collect(Collectors.toList());
        }

        @Override
        public void insertAudit(VersionPublishAuditLog auditLog) {
            auditLogs.add(auditLog);
        }

        @Override
        public List<VersionPublishAuditLog> listAudit(String domainType, Long domainRefId) {
            return auditLogs.stream()
                    .filter(log -> domainType.equals(log.getDomainType()))
                    .filter(log -> domainRefId.equals(log.getDomainRefId()))
                    .collect(Collectors.toList());
        }
    }
}
