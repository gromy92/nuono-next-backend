package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonProductionSchedulerEnablementGateTest {
    private InMemoryNoonPullRepository pullRepository;
    private NoonPullFoundationService foundationService;
    private RecordingSmokeRunRepository smokeRunRepository;
    private RecordingEnablementRepository enablementRepository;
    private NoonProductionSchedulerEnablementGate gate;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneOffset.UTC);
        pullRepository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(pullRepository, clock, new NoonPullFailurePolicy(clock));
        smokeRunRepository = new RecordingSmokeRunRepository();
        enablementRepository = new RecordingEnablementRepository();
        gate = new NoonProductionSchedulerEnablementGate(
                foundationService,
                smokeRunRepository,
                enablementRepository,
                clock
        );
    }

    @Test
    void shouldRejectAutoSchedulingWhenSmokeEvidenceIsControlledProviderFailure() {
        smokeRunRepository.savedRuns.add(smokeRun(140000L, false, failureEvidence()));

        NoonProductionSchedulerEnablementResult result = gate.enable(command(), 10001L);

        assertFalse(result.isEnabled());
        assertEquals(140000L, result.getSmokeRunId());
        assertTrue(result.getRejectionReasons().contains("SALES_SMOKE_NOT_READY"));
        assertTrue(result.getRejectionReasons().contains("ORDER_SMOKE_NOT_READY"));
        assertFalse(result.getRejectionReasons().contains("PRODUCT_SMOKE_NOT_READY"));
        assertEquals(0, scheduledPlans().size());
        assertEquals(1, enablementRepository.savedRecords.size());
        NoonProductionSchedulerEnablementRecord record = enablementRepository.savedRecords.get(0);
        assertEquals("REJECTED", record.getDecision());
        assertEquals(10001L, record.getOperatorUserId());
        assertEquals(140000L, record.getSmokeRunId());
        assertTrue(record.getRejectionReasons().contains("ORDER_SMOKE_NOT_READY"));
    }

    @Test
    void shouldEnableSalesAndOrderSchedulesWhenAllSmokeEvidenceIsReadyAndApproved() {
        smokeRunRepository.savedRuns.add(smokeRun(140100L, true, readyEvidence()));

        NoonProductionSchedulerEnablementResult result = gate.enable(command(), 10001L);

        assertTrue(result.isEnabled());
        assertEquals(140100L, result.getSmokeRunId());
        assertEquals(List.of(), result.getRejectionReasons());
        List<NoonPullPlanRecord> scheduledPlans = scheduledPlans();
        assertEquals(2, scheduledPlans.size());
        assertTrue(scheduledPlans.stream().anyMatch((plan) ->
                plan.getDataDomain() == NoonPullDataDomain.SALES
                        && plan.getPullType() == NoonPullType.REPORT
                        && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY));
        assertFalse(scheduledPlans.stream().anyMatch((plan) ->
                plan.getDataDomain() == NoonPullDataDomain.SALES
                        && plan.getPullType() == NoonPullType.PAGE_QUERY
                        && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY));
        assertTrue(scheduledPlans.stream().anyMatch((plan) ->
                plan.getDataDomain() == NoonPullDataDomain.ORDER
                        && plan.getPullType() == NoonPullType.REPORT
                        && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY));
        assertFalse(scheduledPlans.stream().anyMatch((plan) ->
                plan.getDataDomain() == NoonPullDataDomain.PRODUCT
                        && plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY));
        assertEquals(2, result.getPlanIds().size());
        NoonProductionSchedulerEnablementRecord record = enablementRepository.savedRecords.get(0);
        assertEquals("ENABLED", record.getDecision());
        assertEquals("SALES,ORDER", record.getEnabledDomainsText());
        assertEquals("latest-day after 08:30 Asia/Shanghai; order T-1 after 08:30 Asia/Shanghai", record.getScheduleBoundaries());
        assertEquals(10001L, record.getOperatorUserId());
    }

    @Test
    void shouldEnableProductDailyScheduleWhenProductSmokeEvidenceIsReadyAndApproved() {
        smokeRunRepository.savedRuns.add(smokeRun(140103L, true, readyEvidence()));
        NoonProductionSchedulerEnablementCommand productOnly = command();
        productOnly.setEnabledDomains(List.of(NoonPullDataDomain.PRODUCT));
        productOnly.setScheduleBoundaries("product list daily, one interface task per scheduler tick");

        NoonProductionSchedulerEnablementResult result = gate.enable(productOnly, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(List.of("PRODUCT"), result.getEnabledDomains());
        List<NoonPullPlanRecord> scheduledPlans = scheduledPlans();
        assertEquals(1, scheduledPlans.size());
        NoonPullPlanRecord productPlan = scheduledPlans.get(0);
        assertEquals(NoonPullDataDomain.PRODUCT, productPlan.getDataDomain());
        assertEquals(NoonPullType.INTERFACE, productPlan.getPullType());
        assertEquals(NoonPullTriggerMode.SCHEDULED_DAILY, productPlan.getTriggerMode());
        assertEquals(1, result.getPlanIds().size());
    }

    @Test
    void shouldEnableFinanceTransactionDailyScheduleWhenFinanceSmokeEvidenceIsReadyAndApproved() {
        smokeRunRepository.savedRuns.add(smokeRun(
                140104L,
                true,
                List.of(evidence("FINANCE_TRANSACTION", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null))
        ));
        NoonProductionSchedulerEnablementCommand financeOnly = command();
        financeOnly.setEnabledDomains(List.of(NoonPullDataDomain.FINANCE_TRANSACTION));
        financeOnly.setScheduleBoundaries("finance transaction T-1 daily after 22:30 Asia/Shanghai");

        NoonProductionSchedulerEnablementResult result = gate.enable(financeOnly, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(List.of("FINANCE_TRANSACTION"), result.getEnabledDomains());
        List<NoonPullPlanRecord> scheduledPlans = scheduledPlans();
        assertEquals(1, scheduledPlans.size());
        NoonPullPlanRecord financePlan = scheduledPlans.get(0);
        assertEquals(NoonPullDataDomain.FINANCE_TRANSACTION, financePlan.getDataDomain());
        assertEquals(NoonPullType.REPORT, financePlan.getPullType());
        assertEquals(NoonPullTriggerMode.SCHEDULED_DAILY, financePlan.getTriggerMode());
        assertEquals("finance transaction T-1 daily after 22:30 Asia/Shanghai", financePlan.getScheduleExpression());
        assertEquals(1, result.getPlanIds().size());
    }

    @Test
    void shouldEnableNoonAdvertisingDailyScheduleWhenSmokeEvidenceIsReadyAndApproved() {
        smokeRunRepository.savedRuns.add(smokeRun(
                140107L,
                true,
                List.of(evidence("NOON_ADVERTISING", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null))
        ));
        NoonProductionSchedulerEnablementCommand advertisingOnly = command();
        advertisingOnly.setEnabledDomains(List.of(NoonPullDataDomain.NOON_ADVERTISING));
        advertisingOnly.setScheduleBoundaries("noon ads T-1 daily after 08:00 Asia/Shanghai");

        NoonProductionSchedulerEnablementResult result = gate.enable(advertisingOnly, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(List.of("NOON_ADVERTISING"), result.getEnabledDomains());
        List<NoonPullPlanRecord> scheduledPlans = scheduledPlans();
        assertEquals(1, scheduledPlans.size());
        NoonPullPlanRecord advertisingPlan = scheduledPlans.get(0);
        assertEquals(NoonPullDataDomain.NOON_ADVERTISING, advertisingPlan.getDataDomain());
        assertEquals(NoonPullType.REPORT, advertisingPlan.getPullType());
        assertEquals(NoonPullTriggerMode.SCHEDULED_DAILY, advertisingPlan.getTriggerMode());
        assertEquals("noon ads T-1 daily after 08:00 Asia/Shanghai", advertisingPlan.getScheduleExpression());
        assertEquals(1, result.getPlanIds().size());
    }

    @Test
    void shouldEnableOfficialWarehouseInventoryDailyScheduleWhenSmokeEvidenceIsReadyAndApproved() {
        smokeRunRepository.savedRuns.add(smokeRun(
                140105L,
                true,
                List.of(evidence("OFFICIAL_WAREHOUSE_INVENTORY", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null))
        ));
        NoonProductionSchedulerEnablementCommand inventoryOnly = command();
        inventoryOnly.setEnabledDomains(List.of(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY));
        inventoryOnly.setScheduleBoundaries("official warehouse inventory daily after 23:00 Asia/Shanghai");

        NoonProductionSchedulerEnablementResult result = gate.enable(inventoryOnly, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(List.of("OFFICIAL_WAREHOUSE_INVENTORY"), result.getEnabledDomains());
        List<NoonPullPlanRecord> scheduledPlans = scheduledPlans();
        assertEquals(1, scheduledPlans.size());
        NoonPullPlanRecord inventoryPlan = scheduledPlans.get(0);
        assertEquals(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY, inventoryPlan.getDataDomain());
        assertEquals(NoonPullType.INTERFACE, inventoryPlan.getPullType());
        assertEquals(NoonPullTriggerMode.SCHEDULED_DAILY, inventoryPlan.getTriggerMode());
        assertEquals("official warehouse inventory daily after 23:00 Asia/Shanghai", inventoryPlan.getScheduleExpression());
        assertEquals(1, result.getPlanIds().size());
    }

    @Test
    void shouldEnableOfficialWarehouseFbnReceivedDailyScheduleWhenSmokeEvidenceIsReadyAndApproved() {
        smokeRunRepository.savedRuns.add(smokeRun(
                140106L,
                true,
                List.of(evidence("OFFICIAL_WAREHOUSE_FBN_RECEIVED", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null))
        ));
        NoonProductionSchedulerEnablementCommand fbnReceivedOnly = command();
        fbnReceivedOnly.setEnabledDomains(List.of(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED));
        fbnReceivedOnly.setScheduleBoundaries("official warehouse FBN received report T-1 daily after 23:30 Asia/Shanghai");

        NoonProductionSchedulerEnablementResult result = gate.enable(fbnReceivedOnly, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(List.of("OFFICIAL_WAREHOUSE_FBN_RECEIVED"), result.getEnabledDomains());
        List<NoonPullPlanRecord> scheduledPlans = scheduledPlans();
        assertEquals(1, scheduledPlans.size());
        NoonPullPlanRecord fbnReceivedPlan = scheduledPlans.get(0);
        assertEquals(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED, fbnReceivedPlan.getDataDomain());
        assertEquals(NoonPullType.REPORT, fbnReceivedPlan.getPullType());
        assertEquals(NoonPullTriggerMode.SCHEDULED_DAILY, fbnReceivedPlan.getTriggerMode());
        assertEquals(
                "official warehouse FBN received report T-1 daily after 23:30 Asia/Shanghai",
                fbnReceivedPlan.getScheduleExpression()
        );
        assertEquals(1, result.getPlanIds().size());
    }

    @Test
    void shouldEnableProductionTargetWhenSmokeEvidenceIsReadyAndApproved() {
        NoonPullSmokeRunRecord smokeRun = smokeRun(140101L, true, readyEvidence());
        smokeRun.setTargetEnvironment("production");
        smokeRunRepository.savedRuns.add(smokeRun);
        NoonProductionSchedulerEnablementCommand production = command();
        production.setTargetEnvironment("production");

        NoonProductionSchedulerEnablementResult result = gate.enable(production, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(140101L, result.getSmokeRunId());
        assertEquals(List.of(), result.getRejectionReasons());
    }

    @Test
    void shouldRequireSmokeEvidenceOnlyForRequestedSchedulableDomains() {
        smokeRunRepository.savedRuns.add(smokeRun(
                140102L,
                false,
                List.of(evidence("SALES", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null))
        ));
        NoonProductionSchedulerEnablementCommand salesOnly = command();
        salesOnly.setEnabledDomains(List.of(NoonPullDataDomain.SALES));

        NoonProductionSchedulerEnablementResult result = gate.enable(salesOnly, 10001L);

        assertTrue(result.isEnabled());
        assertEquals(List.of("SALES"), result.getEnabledDomains());
        assertFalse(result.getRejectionReasons().contains("PRODUCT_SMOKE_NOT_READY"));
        assertFalse(result.getRejectionReasons().contains("ORDER_SMOKE_NOT_READY"));
        assertFalse(result.getRejectionReasons().contains("SMOKE_RUN_GATE_NOT_READY"));
    }

    @Test
    void shouldReuseExistingSalesReportScheduleWhenEnablingProductionPlans() {
        smokeRunRepository.savedRuns.add(smokeRun(140100L, true, readyEvidence()));
        NoonPullPlanRecord existingSalesReport = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("latest-day report after 08:00 Asia/Shanghai")
                .build());

        NoonProductionSchedulerEnablementResult result = gate.enable(command(), 10001L);

        List<NoonPullPlanRecord> salesReportPlans = scheduledPlans().stream()
                .filter((plan) -> plan.getDataDomain() == NoonPullDataDomain.SALES)
                .filter((plan) -> plan.getPullType() == NoonPullType.REPORT)
                .collect(Collectors.toList());
        assertEquals(1, salesReportPlans.size());
        assertEquals(existingSalesReport.getId(), salesReportPlans.get(0).getId());
        assertTrue(result.getPlanIds().contains(existingSalesReport.getId()));
        assertEquals(2, scheduledPlans().size());
    }

    @Test
    void shouldRequireHitlApprovalAndRollbackStrategy() {
        smokeRunRepository.savedRuns.add(smokeRun(140100L, true, readyEvidence()));
        NoonProductionSchedulerEnablementCommand command = command();
        command.setHitlApproved(false);
        command.setRollbackOrGlobalPauseStrategy("");

        NoonProductionSchedulerEnablementResult result = gate.enable(command, 10001L);

        assertFalse(result.isEnabled());
        assertTrue(result.getRejectionReasons().contains("HITL_APPROVAL_REQUIRED"));
        assertTrue(result.getRejectionReasons().contains("ROLLBACK_OR_GLOBAL_PAUSE_REQUIRED"));
        assertEquals(0, scheduledPlans().size());
    }

    private NoonProductionSchedulerEnablementCommand command() {
        NoonProductionSchedulerEnablementCommand command = new NoonProductionSchedulerEnablementCommand();
        command.setTargetEnvironment("test");
        command.setOwnerUserId(10002L);
        command.setProjectCode("PRJ245027");
        command.setProjectName("xingyao");
        command.setStoreCode("STR245027-NAE");
        command.setSiteCode("AE");
        command.setEnabledDomains(List.of(NoonPullDataDomain.SALES, NoonPullDataDomain.ORDER));
        command.setScheduleBoundaries("latest-day after 08:30 Asia/Shanghai; order T-1 after 08:30 Asia/Shanghai");
        command.setRollbackOrGlobalPauseStrategy("global pause confirmed");
        command.setHitlApproved(true);
        return command;
    }

    private List<NoonPullPlanRecord> scheduledPlans() {
        return foundationService.listPlans().stream()
                .filter((plan) -> plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY)
                .collect(Collectors.toList());
    }

    private NoonPullSmokeRunRecord smokeRun(
            Long id,
            boolean productionSchedulingAllowed,
            List<NoonPullSmokeEvidenceRecord> evidence
    ) {
        NoonPullSmokeRunRecord run = new NoonPullSmokeRunRecord();
        run.setId(id);
        run.setTargetEnvironment("test");
        run.setOwnerUserId(10002L);
        run.setProjectCode("PRJ245027");
        run.setProjectName("xingyao");
        run.setStoreCode("STR245027-NAE");
        run.setSiteCode("AE");
        run.setRollbackOrGlobalPauseStrategy("global pause confirmed");
        run.setRequestedDataDomains(List.of("PRODUCT", "SALES", "ORDER"));
        run.setEvidenceGateSatisfied(true);
        run.setProductionSchedulingAllowed(productionSchedulingAllowed);
        run.setEvidence(evidence);
        run.setCreatedAt(LocalDateTime.of(2026, 5, 22, 18, 0));
        run.setUpdatedAt(LocalDateTime.of(2026, 5, 22, 18, 0));
        return run;
    }

    private List<NoonPullSmokeEvidenceRecord> failureEvidence() {
        return List.of(
                evidence("PRODUCT", NoonPullTaskStatus.FAILED.name(), null, "provider_not_configured"),
                evidence("SALES", NoonPullTaskStatus.FAILED.name(), null, "provider_not_configured"),
                evidence("ORDER", NoonPullTaskStatus.FAILED.name(), null, "provider_not_configured")
        );
    }

    private List<NoonPullSmokeEvidenceRecord> readyEvidence() {
        return List.of(
                evidence("PRODUCT", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null),
                evidence("SALES", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null),
                evidence("ORDER", NoonPullTaskStatus.SUCCEEDED.name(), "ready", null)
        );
    }

    private NoonPullSmokeEvidenceRecord evidence(
            String domain,
            String status,
            String qualityState,
            String failureClassification
    ) {
        NoonPullSmokeEvidenceRecord evidence = new NoonPullSmokeEvidenceRecord();
        evidence.setDataDomain(domain);
        evidence.setStatus(status);
        evidence.setQualityState(qualityState);
        evidence.setFailureClassification(failureClassification);
        evidence.setTaskId(130000L);
        evidence.setSourceBatchId("ready".equals(qualityState) ? "batch-" + domain.toLowerCase() : null);
        evidence.setTargetIdentity(domain.toLowerCase() + ":smoke");
        evidence.setDateFrom(LocalDate.of(2026, 5, 21));
        evidence.setDateTo(LocalDate.of(2026, 5, 21));
        evidence.setRowOrItemCount("ready".equals(qualityState) ? 1 : 0);
        return evidence;
    }

    private static final class RecordingSmokeRunRepository implements NoonPullSmokeRunRepository {
        private final List<NoonPullSmokeRunRecord> savedRuns = new ArrayList<>();

        @Override
        public NoonPullSmokeRunRecord save(NoonPullSmokeRunRecord run) {
            savedRuns.add(run.copy());
            return run.copy();
        }

        @Override
        public List<NoonPullSmokeRunRecord> listRecent(int limit) {
            return savedRuns.stream()
                    .map(NoonPullSmokeRunRecord::copy)
                    .collect(Collectors.toList());
        }
    }

    private static final class RecordingEnablementRepository implements NoonProductionSchedulerEnablementRepository {
        private final List<NoonProductionSchedulerEnablementRecord> savedRecords = new ArrayList<>();

        @Override
        public NoonProductionSchedulerEnablementRecord save(NoonProductionSchedulerEnablementRecord record) {
            NoonProductionSchedulerEnablementRecord copy = record.copy();
            copy.setId(142000L + savedRecords.size());
            savedRecords.add(copy.copy());
            return copy;
        }

        @Override
        public List<NoonProductionSchedulerEnablementRecord> listRecent(int limit) {
            return savedRecords.stream()
                    .map(NoonProductionSchedulerEnablementRecord::copy)
                    .collect(Collectors.toList());
        }
    }
}
