package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.ProductLifecycleCalculationJobService;
import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleJobRecord;
import com.nuono.next.sales.ProductLifecycleResult;
import com.nuono.next.sales.ProductLifecycleRuleProvider;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationConfigLifecycleRecalculationServiceTest {

    @Test
    void controlledLifecycleRecalculationRecordsScopeVersionAndTriggeringOperator() {
        RecordingLifecycleJobService jobService = new RecordingLifecycleJobService();
        OperationConfigLifecycleRecalculationService service = new OperationConfigLifecycleRecalculationService(
                new OperationConfigScopeService(scopeRepository()),
                jobService,
                lifecycleRuleRepository(true)
        );

        ProductLifecycleJobRecord job = service.recalculate(
                operatorContext(),
                new OperationConfigLifecycleRecalculationCommand(
                        List.of(),
                        501L,
                        "STR-X-NAE",
                        "AE",
                        LocalDate.of(2026, 5, 21),
                        "LIFECYCLE_CONFIG_v1"
                )
        );

        assertEquals("succeeded", job.getStatus());
        assertEquals("LIFECYCLE_CONFIG_v1", job.getRuleVersion());
        assertEquals(601L, job.getTriggeredByUserId());
        assertEquals("operations_config", job.getTriggerSource());
        assertEquals(501L, jobService.capturedScope.getOwnerUserId());
        assertEquals("STR-X-NAE", jobService.capturedScope.getStoreCode());
        assertEquals("AE", jobService.capturedScope.getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 21), jobService.capturedScope.getAnchorDate());
        assertEquals("LIFECYCLE_CONFIG_v1", jobService.capturedScope.getRuleVersion());
        assertEquals(true, jobService.capturedScope.isExplicitRuleVersion());
        assertEquals(601L, jobService.capturedScope.getTriggeredByUserId());
        assertEquals("operations_config", jobService.capturedScope.getTriggerSource());
    }

    @Test
    void controlledLifecycleRecalculationRejectsUnauthorizedScopeBeforeRunningJob() {
        RecordingLifecycleJobService jobService = new RecordingLifecycleJobService();
        OperationConfigLifecycleRecalculationService service = new OperationConfigLifecycleRecalculationService(
                new OperationConfigScopeService(scopeRepository()),
                jobService,
                lifecycleRuleRepository(true)
        );

        BusinessAccessDeniedException error = assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.recalculate(
                        operatorContext(),
                        new OperationConfigLifecycleRecalculationCommand(
                                List.of(),
                                999L,
                                "STR-Z-NAE",
                                "AE",
                                LocalDate.of(2026, 5, 21),
                                "LIFECYCLE_CONFIG_v1"
                        )
                )
        );

        assertTrue(error.getMessage().contains("当前账号不能操作该店铺"));
        assertEquals(0, jobService.runCount);
    }

    @Test
    void controlledLifecycleRecalculationRejectsUnknownSelectedRuleVersionBeforeRunningJob() {
        RecordingLifecycleJobService jobService = new RecordingLifecycleJobService();
        OperationConfigLifecycleRecalculationService service = new OperationConfigLifecycleRecalculationService(
                new OperationConfigScopeService(scopeRepository()),
                jobService,
                lifecycleRuleRepository(true)
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.recalculate(
                        operatorContext(),
                        new OperationConfigLifecycleRecalculationCommand(
                                List.of(),
                                501L,
                                "STR-X-NAE",
                                "AE",
                                LocalDate.of(2026, 5, 21),
                                "LIFECYCLE_CONFIG_not_found"
                        )
                )
        );

        assertTrue(error.getMessage().contains("selectedRuleVersion"));
        assertEquals(0, jobService.runCount);
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

    private static InMemoryOperationConfigScopeRepository scopeRepository() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        repository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
        return repository;
    }

    private static InMemoryOperationLifecycleRuleRepository lifecycleRuleRepository(boolean withPublishedRule) {
        InMemoryOperationLifecycleRuleRepository repository = new InMemoryOperationLifecycleRuleRepository();
        if (withPublishedRule) {
            repository.rules.add(new OperationLifecycleRule(
                    83001L,
                    501L,
                    "STR-X-NAE",
                    "AE",
                    "LIFECYCLE_CONFIG_v1",
                    ProductLifecycleResult.DEFAULT_RULE_VERSION,
                    OperationLifecycleRuleThresholds.defaultV1(),
                    91001L,
                    OperationConfigPublishStatus.PUBLISHED,
                    601L,
                    601L,
                    LocalDateTime.of(2026, 5, 22, 9, 0),
                    LocalDateTime.of(2026, 5, 22, 9, 0)
            ));
        }
        return repository;
    }

    private static class RecordingLifecycleJobService extends ProductLifecycleCalculationJobService {
        private ProductLifecycleCalculationScope capturedScope;
        private int runCount;

        RecordingLifecycleJobService() {
            super(null, null, null, null, null, null, null, ProductLifecycleRuleProvider.defaultV1());
        }

        @Override
        public ProductLifecycleJobRecord run(ProductLifecycleCalculationScope scope) {
            runCount++;
            capturedScope = scope;
            return new ProductLifecycleJobRecord(
                    72001L,
                    scope.getOwnerUserId(),
                    scope.getStoreCode(),
                    scope.getSiteCode(),
                    scope.getAnchorDate(),
                    scope.getRuleVersion(),
                    "succeeded",
                    3,
                    1,
                    0,
                    0,
                    null,
                    LocalDateTime.of(2026, 5, 22, 10, 0),
                    LocalDateTime.of(2026, 5, 22, 10, 1),
                    scope.getTriggeredByUserId(),
                    scope.getTriggerSource()
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

    private static class InMemoryOperationLifecycleRuleRepository implements OperationLifecycleRuleRepository {
        private final List<OperationLifecycleRule> rules = new ArrayList<>();

        @Override
        public Long nextRuleId() {
            return 83001L + rules.size();
        }

        @Override
        public void insertRule(OperationLifecycleRule rule) {
            rules.add(rule);
        }

        @Override
        public void updateRule(OperationLifecycleRule rule) {
            rules.removeIf(existing -> existing.getId().equals(rule.getId()));
            rules.add(rule);
        }

        @Override
        public OperationLifecycleRule findRule(Long id) {
            return rules.stream()
                    .filter(rule -> rule.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<OperationLifecycleRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
            return rules.stream()
                    .filter(rule -> ownerUserId.equals(rule.getOwnerUserId()))
                    .filter(rule -> storeCode.equals(rule.getStoreCode()))
                    .filter(rule -> siteCode.equals(rule.getSiteCode()))
                    .collect(Collectors.toList());
        }

        @Override
        public java.util.Optional<OperationLifecycleRule> findLatestPublished(
                Long ownerUserId,
                String storeCode,
                String siteCode
        ) {
            return listRules(ownerUserId, storeCode, siteCode).stream()
                    .filter(rule -> OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus()))
                    .findFirst();
        }

        @Override
        public java.util.Optional<OperationLifecycleRule> findLatestDraft(
                Long ownerUserId,
                String storeCode,
                String siteCode
        ) {
            return listRules(ownerUserId, storeCode, siteCode).stream()
                    .filter(rule -> OperationConfigPublishStatus.DRAFT.equals(rule.getPublishStatus()))
                    .findFirst();
        }

        @Override
        public List<OperationLifecycleRule> listRulesByBundleVersion(Long bundleVersionId) {
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
    }
}
