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
