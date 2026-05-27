package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.versioning.VersionPublishActionCommand;
import com.nuono.next.versioning.VersionPublishAuditLog;
import com.nuono.next.versioning.VersionPublishDraftCommand;
import com.nuono.next.versioning.VersionPublishRecord;
import com.nuono.next.versioning.VersionPublishRepository;
import com.nuono.next.versioning.VersionPublishService;
import com.nuono.next.versioning.VersionPublishStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
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

class OperationCalendarRuleServiceTest {

    @Test
    void springContextCanCreateCalendarRuleServiceWithRepositoryConstructor() {
        new ApplicationContextRunner()
                .withBean(OperationCalendarRuleRepository.class, InMemoryOperationCalendarRuleRepository::new)
                .withBean(OperationConfigScopeRepository.class, InMemoryOperationConfigScopeRepository::new)
                .withBean(OperationConfigBundleRepository.class, InMemoryOperationConfigBundleRepository::new)
                .withBean(VersionPublishRepository.class, InMemoryVersionPublishRepository::new)
                .withBean(OperationConfigScopeService.class)
                .withBean(VersionPublishService.class)
                .withBean(OperationCalendarRuleService.class)
                .run(context -> assertTrue(
                        context.getStartupFailure() == null,
                        () -> String.valueOf(context.getStartupFailure())
                ));
    }

    @Test
    void authorizedUserCanCreateEditPublishAndReadActiveCalendarRule() {
        TestHarness harness = new TestHarness();
        OperationCalendarRuleDraftCommand draftCommand = baseCommand();

        OperationCalendarRule draft = harness.service.saveDraft(operatorContext(), draftCommand);
        OperationCalendarRule updated = harness.service.saveDraft(
                operatorContext(),
                draftCommand.withIdAndFactor(draft.getId(), new BigDecimal("1.40"))
        );
        OperationCalendarRule published = harness.service.publish(
                operatorContext(),
                draft.getId(),
                "publish Ramadan factor"
        );
        List<OperationCalendarRule> active = harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        );

        assertEquals(draft.getId(), updated.getId());
        assertEquals(new BigDecimal("1.40"), published.getFactorValue());
        assertEquals(OperationConfigPublishStatus.PUBLISHED, published.getPublishStatus());
        assertEquals(1, active.size());
        assertEquals("Ramadan 2026", active.get(0).getRuleName());
        assertEquals(published.getPublishRecordId(), harness.versionRepository
                .findCurrent(OperationCalendarRuleService.DOMAIN_TYPE, published.getId())
                .orElseThrow()
                .getId());
    }

    @Test
    void systemAdminPublishesBossWideCalendarRuleVisibleToConcreteStoreWithSystemSource() {
        TestHarness harness = new TestHarness();
        harness.scopeRepository.addStore(501L, 702L, "PRJ-X", "Xingyao", "STR-X-NSA", "SA");
        OperationCalendarRuleDraftCommand command = new OperationCalendarRuleDraftCommand(
                null,
                List.of(501L),
                501L,
                "*",
                "*",
                "System Ramadan 2026",
                "holiday",
                LocalDate.of(2026, 2, 18),
                LocalDate.of(2026, 3, 19),
                "YEARLY:RAMADAN",
                "all_products",
                null,
                new BigDecimal("1.30"),
                "demand_uplift",
                true
        );

        OperationCalendarRule draft = harness.service.saveDraft(systemAdminContext(), command);
        OperationCalendarRule published = harness.service.publish(
                systemAdminContext(),
                List.of(501L),
                draft.getId(),
                "publish boss-wide system calendar"
        );
        List<OperationCalendarRule> activeForUaeStore = harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        );
        List<OperationCalendarRule> activeForSaudiStore = harness.service.listActive(
                systemAdminContext(),
                List.of(501L),
                501L,
                "STR-X-NSA",
                "SA"
        );

        assertEquals(OperationConfigPublishStatus.PUBLISHED, published.getPublishStatus());
        assertEquals("*", published.getStoreCode());
        assertEquals("*", published.getSiteCode());
        assertEquals("系统发布", published.getPublishSourceLabel());
        assertEquals(1, activeForUaeStore.size());
        assertEquals("System Ramadan 2026", activeForUaeStore.get(0).getRuleName());
        assertEquals("系统发布", activeForUaeStore.get(0).getPublishSourceLabel());
        assertEquals(1, activeForSaudiStore.size());
        assertEquals("System Ramadan 2026", activeForSaudiStore.get(0).getRuleName());
    }

    @Test
    void rejectsUnauthorizedStoreScopeBeforeSavingDraft() {
        TestHarness harness = new TestHarness();
        OperationCalendarRuleDraftCommand command = new OperationCalendarRuleDraftCommand(
                null,
                List.of(),
                999L,
                "STR-Z-NAE",
                "AE",
                "White Friday",
                "marketplace_event",
                LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 30),
                null,
                "all_products",
                null,
                new BigDecimal("1.25"),
                "demand_uplift",
                true
        );

        BusinessAccessDeniedException error = assertThrows(
                BusinessAccessDeniedException.class,
                () -> harness.service.saveDraft(operatorContext(), command)
        );

        assertTrue(error.getMessage().contains("当前账号不能操作该店铺"));
        assertTrue(harness.calendarRepository.rules.isEmpty());
    }

    @Test
    void activeReadExcludesDraftAndDisabledRules() {
        TestHarness harness = new TestHarness();
        OperationCalendarRule draft = harness.service.saveDraft(operatorContext(), baseCommand());
        OperationCalendarRule disabledPublished = harness.service.saveDraft(
                operatorContext(),
                baseCommand().withNameAndEnabled("Disabled Eid", false)
        );
        harness.service.publish(operatorContext(), disabledPublished.getId(), "publish disabled rule");

        List<OperationCalendarRule> active = harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        );

        assertEquals(OperationConfigPublishStatus.DRAFT, draft.getPublishStatus());
        assertTrue(active.isEmpty());
    }

    @Test
    void bundleOwnedCalendarDraftIsListedInBundleHistoryButNotActiveUntilBundlePublish() {
        TestHarness harness = new TestHarness();
        OperationConfigBundleVersionView bundle = harness.bundleService.createEmptyDraft(
                operatorContext(),
                new OperationConfigBundleDraftCommand("2026 Ramadan suite")
        );
        harness.bundleService.updateScope(
                operatorContext(),
                bundle.getId(),
                new OperationConfigBundleScopeCommand(
                        List.of(),
                        List.of(new OperationConfigBundleScopeStoreCommand(501L, "STR-X-NAE", "AE"))
                )
        );

        OperationCalendarRule saved = harness.service.saveDraft(
                operatorContext(),
                baseCommand().withBundleVersionId(bundle.getId())
        );
        List<OperationCalendarRule> bundleHistory = harness.service.history(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE",
                bundle.getId()
        );
        List<OperationCalendarRule> activeBeforePublish = harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        );
        harness.bundleService.publish(operatorContext(), bundle.getId(), "publish suite");
        harness.calendarRepository.currentBundleVersionId = bundle.getId();
        List<OperationCalendarRule> activeAfterPublish = harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        );

        assertEquals(bundle.getId(), saved.getBundleVersionId());
        assertEquals(OperationConfigPublishStatus.DRAFT, saved.getPublishStatus());
        assertEquals(List.of(saved.getId()), bundleHistory.stream()
                .map(OperationCalendarRule::getId)
                .collect(Collectors.toList()));
        assertTrue(activeBeforePublish.isEmpty());
        assertEquals(List.of(saved.getId()), activeAfterPublish.stream()
                .map(OperationCalendarRule::getId)
                .collect(Collectors.toList()));
        assertEquals(OperationConfigPublishStatus.PUBLISHED, activeAfterPublish.get(0).getPublishStatus());
    }

    @Test
    void copiesPreviousYearPublishedRulesIntoDraftsThenBatchAdjustsBeforePublishing() {
        TestHarness harness = new TestHarness();
        OperationCalendarRule sourceDraft = harness.service.saveDraft(operatorContext(), new OperationCalendarRuleDraftCommand(
                null,
                List.of(),
                501L,
                "STR-X-NAE",
                "AE",
                "Ramadan 2025",
                "holiday",
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 30),
                "YEARLY:RAMADAN",
                "all_products",
                null,
                new BigDecimal("1.20"),
                "demand_uplift",
                true
        ));
        harness.service.publish(operatorContext(), sourceDraft.getId(), "publish 2025 baseline");

        List<OperationCalendarRule> copied = harness.service.copyPreviousYear(
                operatorContext(),
                new OperationCalendarRuleCopyPreviousYearCommand(
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        2025,
                        2026
                )
        );
        List<OperationCalendarRule> adjusted = harness.service.batchUpdateDrafts(
                operatorContext(),
                new OperationCalendarRuleBatchUpdateCommand(
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        copied.stream().map(OperationCalendarRule::getId).collect(Collectors.toList()),
                        true,
                        new BigDecimal("1.35")
                )
        );
        OperationCalendarRule publishedCopy = harness.service.publish(operatorContext(), copied.get(0).getId(), "publish 2026 factor");

        assertEquals(1, copied.size());
        assertEquals(OperationConfigPublishStatus.DRAFT, copied.get(0).getPublishStatus());
        assertEquals("Ramadan 2026", copied.get(0).getRuleName());
        assertEquals(LocalDate.of(2026, 3, 1), copied.get(0).getDateFrom());
        assertEquals(LocalDate.of(2026, 3, 30), copied.get(0).getDateTo());
        assertEquals(new BigDecimal("1.35"), adjusted.get(0).getFactorValue());
        assertEquals(OperationConfigPublishStatus.PUBLISHED, publishedCopy.getPublishStatus());
        assertEquals(new BigDecimal("1.35"), harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        ).stream().filter(rule -> rule.getId().equals(publishedCopy.getId())).findFirst().orElseThrow().getFactorValue());
    }

    @Test
    void batchDisableKeepsPublishedRuleInHistoryButExcludesItFromActiveCompatibility() {
        TestHarness harness = new TestHarness();
        OperationCalendarRule copied = harness.service.saveDraft(operatorContext(), baseCommand());
        List<OperationCalendarRule> disabledDrafts = harness.service.batchUpdateDrafts(
                operatorContext(),
                new OperationCalendarRuleBatchUpdateCommand(
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        List.of(copied.getId()),
                        false,
                        null
                )
        );
        OperationCalendarRule disabledPublished = harness.service.publish(operatorContext(), copied.getId(), "publish disabled audit row");

        List<OperationCalendarRule> history = harness.service.history(operatorContext(), 501L, "STR-X-NAE", "AE");

        assertEquals(false, disabledDrafts.get(0).isEnabled());
        assertEquals(false, disabledPublished.isEnabled());
        assertTrue(harness.service.listActive(operatorContext(), 501L, "STR-X-NAE", "AE").isEmpty());
        assertEquals(1, history.size());
        assertEquals(disabledPublished.getId(), history.get(0).getId());
        assertEquals(OperationConfigPublishStatus.PUBLISHED, history.get(0).getPublishStatus());
    }

    @Test
    void disablesPublishedCalendarRuleAndRemovesItFromActiveRead() {
        TestHarness harness = new TestHarness();
        OperationCalendarRule draft = harness.service.saveDraft(operatorContext(), baseCommand());
        OperationCalendarRule published = harness.service.publish(operatorContext(), draft.getId(), "publish Ramadan factor");

        OperationCalendarRule disabled = harness.service.disable(
                operatorContext(),
                List.of(),
                published.getId(),
                "disable Ramadan factor"
        );
        List<OperationCalendarRule> active = harness.service.listActive(
                operatorContext(),
                501L,
                "STR-X-NAE",
                "AE"
        );
        List<OperationCalendarRule> history = harness.service.history(operatorContext(), 501L, "STR-X-NAE", "AE");

        assertEquals(OperationConfigPublishStatus.DISABLED, disabled.getPublishStatus());
        assertEquals(false, disabled.isEnabled());
        assertTrue(active.isEmpty());
        assertEquals(OperationConfigPublishStatus.DISABLED, history.get(0).getPublishStatus());
        assertEquals(com.nuono.next.versioning.VersionPublishStatus.DISABLED, harness.versionRepository
                .findRecord(disabled.getPublishRecordId())
                .getStatus());
    }

    @Test
    void publishedCalendarRulesExposeSalesActivityWindowCompatibilitySnapshot() {
        TestHarness harness = new TestHarness();
        OperationCalendarRule draft = harness.service.saveDraft(operatorContext(), baseCommand());
        OperationCalendarRule published = harness.service.publish(operatorContext(), draft.getId(), "publish Ramadan factor");
        OperationCalendarSalesActivityWindowAdapter adapter =
                new OperationCalendarSalesActivityWindowAdapter(harness.calendarRepository);

        List<com.nuono.next.sales.SalesActivityWindowRecord> windows = adapter.listActive(
                new com.nuono.next.sales.SalesActivityWindowScope(
                        501L,
                        "STR-X-NAE",
                        "AE",
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 31)
                )
        );

        assertEquals(1, windows.size());
        assertEquals(published.getId(), windows.get(0).getId());
        assertEquals("Ramadan 2026", windows.get(0).getName());
        assertEquals(new BigDecimal("1.35"), windows.get(0).getFactor());
        assertEquals("all_products", windows.get(0).getCategoryScope());
    }

    private static OperationCalendarRuleDraftCommand baseCommand() {
        return new OperationCalendarRuleDraftCommand(
                null,
                List.of(),
                501L,
                "STR-X-NAE",
                "AE",
                "Ramadan 2026",
                "holiday",
                LocalDate.of(2026, 2, 18),
                LocalDate.of(2026, 3, 19),
                "YEARLY:RAMADAN",
                "all_products",
                null,
                new BigDecimal("1.35"),
                "demand_uplift",
                true
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
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();
    }

    private static BusinessAccessContext systemAdminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(1L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();
    }

    private static class TestHarness {
        private final InMemoryOperationConfigScopeRepository scopeRepository =
                new InMemoryOperationConfigScopeRepository();
        private final InMemoryOperationCalendarRuleRepository calendarRepository =
                new InMemoryOperationCalendarRuleRepository();
        private final InMemoryOperationConfigBundleRepository bundleRepository =
                new InMemoryOperationConfigBundleRepository();
        private final InMemoryVersionPublishRepository versionRepository =
                new InMemoryVersionPublishRepository();
        private final OperationConfigBundleService bundleService;
        private final OperationCalendarRuleService service;

        TestHarness() {
            scopeRepository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
            OperationConfigScopeService scopeService = new OperationConfigScopeService(scopeRepository);
            VersionPublishService publishService = new VersionPublishService(versionRepository);
            bundleService = new OperationConfigBundleService(
                    bundleRepository,
                    scopeService,
                    publishService
            );
            service = new OperationCalendarRuleService(
                    calendarRepository,
                    bundleRepository,
                    scopeService,
                    publishService
            );
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

    private static class InMemoryOperationCalendarRuleRepository implements OperationCalendarRuleRepository {
        private long nextRuleId = 12000L;
        private final Map<Long, OperationCalendarRule> rules = new LinkedHashMap<>();
        private Long currentBundleVersionId;

        @Override
        public Long nextRuleId() {
            nextRuleId += 1;
            return nextRuleId;
        }

        @Override
        public void insertRule(OperationCalendarRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public void updateRule(OperationCalendarRule rule) {
            rules.put(rule.getId(), rule);
        }

        @Override
        public OperationCalendarRule findRule(Long id) {
            return rules.get(id);
        }

        @Override
        public List<OperationCalendarRule> listActiveRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(OperationCalendarRule::isEnabled)
                    .filter(rule -> rule.getBundleVersionId() == null
                            ? OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus())
                            : rule.getBundleVersionId().equals(currentBundleVersionId))
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()) || "*".equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()) || "*".equals(rule.getSiteCode()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationCalendarRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.values().stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> (storeCode.equals(rule.getStoreCode()) && siteCode.equals(rule.getSiteCode()))
                            || ("*".equals(rule.getStoreCode())
                                    && "*".equals(rule.getSiteCode())
                                    && OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus())))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationCalendarRule> listRulesByBundleVersion(Long bundleVersionId) {
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
