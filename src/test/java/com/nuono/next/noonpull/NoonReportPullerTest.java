package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonReportPullerTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonReportPuller puller;
    private NoonReportMutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new NoonReportMutableClock(
                Instant.parse("2026-05-22T09:00:00Z"),
                ZoneOffset.UTC
        );
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        puller = new NoonReportPuller(
                foundationService,
                new NoonRiskBackoffGuard(new InMemoryNoonRiskBackoffRepository(), clock),
                new NoonPullFailurePolicy(clock)
        );
    }

    @Test
    void shouldCreatePollDownloadDigestAndRecordSourceBatch() {
        NoonPullTaskRecord task = createSalesTask();
        FakeReportProvider provider = FakeReportProvider.ready("date,sku_parent,units_sold,sales_amount,currency\n2026-05-21,Z1,2,39.90,AED\n");

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord persistedTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(List.of("create", "poll:EXP-1", "download"), provider.calls);
        assertNotNull(result.getFileDigestSha256());
        assertTrue(persistedTask.getSourceBatchId().startsWith("noon-report-sales-"));
        assertTrue(persistedTask.getDiagnosticSummary().contains("productviewsandsalesdata"));
        assertTrue(persistedTask.getDiagnosticSummary().contains("digest="));
    }

    @Test
    void shouldCompleteWhenAdapterExplicitlyReportsBusinessRowSkips() {
        NoonPullTaskRecord task = createSalesTask("sales:business-skips");

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                FakeReportProvider.ready(
                        "date,sku_parent,units_sold,sales_amount,currency\n2026-05-21,Z1,2,39.90,AED\n"
                ),
                (file) -> NoonReportProcessResult.succeededWithBusinessSkips(1, 2)
        );

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(1, result.getImportedCount());
        assertEquals(2, result.getExceptionCount());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, repository.selectTask(task.getId()).getStatus());
    }

    @Test
    void shouldRetryMissingDownloadUrlAndFailedExportButKeepPendingRunning() {
        NoonPullTaskRecord missingUrlTask = createSalesTask("sales:missing-url");
        NoonReportPullResult missingUrl = puller.execute(
                missingUrlTask.getId(),
                salesRequest(),
                FakeReportProvider.missingDownloadUrl(),
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );

        assertEquals(NoonPullTaskStatus.RUNNING, missingUrl.getStatus());
        assertEquals("provider_unavailable", repository.selectTask(missingUrlTask.getId()).getFailureType());

        NoonPullTaskRecord failedExportTask = createSalesTask("sales:failed-export");
        NoonReportPullResult failedExport = puller.execute(
                failedExportTask.getId(),
                salesRequest(),
                FakeReportProvider.failedExport(),
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );

        assertEquals(NoonPullTaskStatus.FAILED, failedExport.getStatus());
        assertEquals("provider_unavailable", repository.selectTask(failedExportTask.getId()).getFailureType());

        NoonPullTaskRecord timeoutTask = createSalesTask("sales:timeout");
        NoonReportPullResult timeout = puller.execute(
                timeoutTask.getId(),
                salesRequest(),
                FakeReportProvider.pending(),
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );

        assertEquals(NoonPullTaskStatus.RUNNING, timeout.getStatus());
        assertEquals("EXP-1", stringProperty(repository.selectTask(timeoutTask.getId()), "reportExportId"));
        assertEquals("PENDING", stringProperty(repository.selectTask(timeoutTask.getId()), "reportExportStatus"));
    }

    @Test
    void shouldPersistPendingExportAndResumeSameExportWithoutCreatingAgain() {
        NoonPullTaskRecord task = createSalesTask("sales:resume-export");
        FakeReportProvider provider = FakeReportProvider.sequence(
                NoonReportExportStatus.pending(),
                NoonReportExportStatus.ready("https://download.test/sales.csv")
        );

        NoonReportPullResult first = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord pending = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, first.getStatus());
        assertEquals(List.of("create", "poll:EXP-1"), provider.calls);
        assertEquals("EXP-1", stringProperty(pending, "reportExportId"));
        assertEquals("PENDING", stringProperty(pending, "reportExportStatus"));
        assertEquals(1, intProperty(pending, "reportPollAttempts"));
        assertNotNull(property(pending, "reportLastPollAt"));
        assertNotNull(property(pending, "reportNextPollAt"));
        Duration nextPollDelay = Duration.between(
                (LocalDateTime) property(pending, "reportLastPollAt"),
                (LocalDateTime) property(pending, "reportNextPollAt")
        );
        assertTrue(nextPollDelay.compareTo(Duration.ofMinutes(20)) >= 0);
        assertTrue(nextPollDelay.compareTo(Duration.ofMinutes(24)) < 0);

        NoonReportPullResult second = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord succeeded = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.SUCCEEDED, second.getStatus());
        assertEquals(List.of("create", "poll:EXP-1", "poll:EXP-1", "download"), provider.calls);
        assertEquals("EXP-1", stringProperty(succeeded, "reportExportId"));
        assertEquals("READY", stringProperty(succeeded, "reportExportStatus"));
        assertEquals("https://download.test/sales.csv", stringProperty(succeeded, "reportDownloadUrl"));
        assertEquals(2, intProperty(succeeded, "reportPollAttempts"));
    }

    @Test
    void shouldKeepExportContextAndBackOffWhenPollHitsProviderJitter() {
        NoonPullTaskRecord task = createSalesTask("sales:provider-jitter");
        FakeReportProvider provider = FakeReportProvider.throwingOnPoll("HTTP header parser received no bytes");

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals(List.of("create", "poll:EXP-1"), provider.calls);
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertEquals("provider_unavailable", persisted.getFailureType());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertNotNull(property(persisted, "reportNextPollAt"));
        assertTrue(persisted.getDiagnosticSummary().contains("HTTP header parser received no bytes"));
    }

    @Test
    void shouldContinueTheSameExportBeyondTheLegacyPollAttemptLimit() {
        NoonPullTaskRecord task = createSalesTask("sales:poll-limit");
        FakeReportProvider provider = FakeReportProvider.pending();

        puller.execute(task.getId(), salesRequest(), provider, (file) -> NoonReportProcessResult.succeeded(0, 0));
        puller.execute(task.getId(), salesRequest(), provider, (file) -> NoonReportProcessResult.succeeded(0, 0));
        NoonReportPullResult continued = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, continued.getStatus());
        assertEquals(null, persisted.getFailureType());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertEquals(3, intProperty(persisted, "reportPollAttempts"));
        assertEquals(
                List.of("create", "poll:EXP-1", "poll:EXP-1", "poll:EXP-1"),
                provider.calls
        );
    }

    @Test
    void shouldContinueTheSameExportAfterTwoDaysWithoutInferringADeadLifecycle() {
        NoonPullTaskRecord task = createSalesTask("sales:age-limit");
        FakeReportProvider provider = FakeReportProvider.pending();

        puller.execute(task.getId(), salesRequest(18), provider, (file) -> NoonReportProcessResult.succeeded(0, 0));
        clock.setInstant(Instant.parse("2026-05-24T09:00:01Z"));
        NoonReportPullResult continued = puller.execute(
                task.getId(),
                salesRequest(18),
                provider,
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, continued.getStatus());
        assertEquals(null, persisted.getFailureType());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertEquals(List.of("create", "poll:EXP-1", "poll:EXP-1"), provider.calls);
    }

    @Test
    void shouldTerminateMissingProjectAccessInsteadOfKeepingTaskRunning() {
        NoonPullTaskRecord task = createSalesTask("sales:missing-project-access");
        FakeReportProvider provider = FakeReportProvider.throwingOnPoll("Noon 账号不包含当前项目：PRJ67811");

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("auth_required", persisted.getFailureType());
        assertEquals(Boolean.FALSE, persisted.getRetryable());
        assertEquals(Boolean.TRUE, persisted.getRequiresManualAction());
    }

    @Test
    void shouldRecordReportRiskBackoffWhenSalesPollHitsNoonRiskControl() {
        NoonPullTaskRecord task = createSalesTask("sales:risk-control");
        FakeReportProvider provider = FakeReportProvider.throwingOnPoll("blocked by risk control");

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals(List.of("create", "poll:EXP-1"), provider.calls);
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertEquals("blocked_by_risk_control", persisted.getFailureType());
        assertEquals(NoonPullRetryAction.DELAY.name(), persisted.getRetryAction());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertEquals(Boolean.FALSE, persisted.getRequiresManualAction());
        assertEquals(1, intProperty(persisted, "reportPollAttempts"));
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 2), property(persisted, "reportNextPollAt"));
        assertTrue(persisted.getDiagnosticSummary().contains("blocked by risk control"));
    }

    @Test
    void shouldEscalateReportRiskBackoffByScopeFromTwoToFourToEightToSixteenMinutes() {
        NoonPullTaskRecord task = createSalesTask("sales:risk-escalation");

        executeRiskFailure(task, "blocked by risk control");
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 2), property(repository.selectTask(task.getId()), "reportNextPollAt"));

        clock.setInstant(Instant.parse("2026-05-22T09:02:01Z"));
        executeRiskFailure(task, "blocked by risk control");
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 6, 1), property(repository.selectTask(task.getId()), "reportNextPollAt"));

        clock.setInstant(Instant.parse("2026-05-22T09:06:02Z"));
        executeRiskFailure(task, "blocked by risk control");
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 14, 2), property(repository.selectTask(task.getId()), "reportNextPollAt"));

        clock.setInstant(Instant.parse("2026-05-22T09:14:03Z"));
        executeRiskFailure(task, "blocked by risk control");
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 30, 3), property(repository.selectTask(task.getId()), "reportNextPollAt"));

        clock.setInstant(Instant.parse("2026-05-22T09:30:04Z"));
        executeRiskFailure(task, "blocked by risk control");
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 46, 4), property(repository.selectTask(task.getId()), "reportNextPollAt"));
    }

    @Test
    void shouldKeepSalesRiskBackoffIsolatedFromAnOrderScope() {
        NoonPullTaskRecord salesTask = createSalesTask("sales:risk-blocks-order");
        executeRiskFailure(salesTask, "blocked by risk control");

        NoonPullTaskRecord orderTask = createOrderTask("orders:2026-05-21..2026-05-21");
        FakeReportProvider orderProvider = FakeReportProvider.ready("order_nr,item_nr\nO-1,I-1\n");

        NoonReportPullResult result = puller.execute(
                orderTask.getId(),
                orderRequest(),
                orderProvider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord persisted = repository.selectTask(orderTask.getId());

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(List.of("create", "poll:EXP-1", "download"), orderProvider.calls);
        assertEquals(null, persisted.getFailureType());
    }

    @Test
    void shouldKeepSalesExportPendingWhenOnlyTheDownloadedFileAppearsEmpty() {
        NoonPullTaskRecord task = createSalesTask("sales:empty-report");
        FakeReportProvider provider = FakeReportProvider.ready(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
        );

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                new NoonSalesReportAdapter(
                        (fact) -> {
                        },
                        Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC)
                )::process
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals("report_not_ready", persisted.getFailureType());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertTrue(persisted.getDiagnosticSummary().contains("exportStatus=READY"));
        assertTrue(persisted.getDiagnosticSummary().contains("download=true"));
        assertTrue(persisted.getDiagnosticSummary().contains("totalRows=unknown"));
        assertTrue(persisted.getDiagnosticSummary().contains("csvHeader=valid"));
        assertTrue(persisted.getDiagnosticSummary().contains("importedRows=0"));
        assertTrue(persisted.getDiagnosticSummary().contains(
                "authoritative_empty_proof_unavailable"
        ));
    }

    @Test
    void shouldNeverInferSalesEmptyFromTimeOrRepeatedLocalContent() {
        NoonPullTaskRecord task = createSalesTask("sales:confirmed-empty");
        FakeReportProvider provider = FakeReportProvider.ready(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
        );

        NoonReportPullResult first = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                new NoonSalesReportAdapter(
                        (fact) -> {
                        },
                        Clock.fixed(Instant.parse("2026-05-26T09:00:00Z"), ZoneOffset.UTC)
                )::process
        );
        clock.setInstant(Instant.parse("2026-05-26T09:00:00Z"));
        NoonReportPullResult second = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                new NoonSalesReportAdapter(
                        (fact) -> {
                        },
                        Clock.fixed(Instant.parse("2026-05-26T09:00:00Z"), ZoneOffset.UTC)
                )::process
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, first.getStatus());
        assertEquals(NoonPullTaskStatus.RUNNING, second.getStatus());
        assertEquals("report_not_ready", persisted.getFailureType());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertEquals(2, intProperty(persisted, "reportPollAttempts"));
        assertEquals(
                List.of("create", "poll:EXP-1", "download", "poll:EXP-1", "download"),
                provider.calls
        );
    }

    @Test
    void shouldNotTreatALocallyEmptyFinanceFileAsAuthoritativeEmpty() {
        NoonPullTaskRecord task = createFinanceTask("finance:accepted-empty");
        FakeReportProvider provider = FakeReportProvider.ready(
                String.join(",", NoonFinanceTransactionReportDescriptor.requiredColumns()) + "\n"
        );

        NoonReportPullResult result = puller.execute(
                task.getId(),
                financeRequest(),
                provider,
                (file) -> NoonReportProcessResult.emptyReport("finance transaction report has no settlement rows")
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());
        NoonPullPlanRecord plan = repository.selectPlan(task.getPlanId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals(NoonPullTaskStatus.RUNNING, persisted.getStatus());
        assertEquals("report_not_ready", persisted.getFailureType());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertEquals(null, plan.getLatestSuccessAt());
        assertNotNull(plan.getLatestFailureAt());
        assertEquals("report_not_ready", plan.getLatestFailureType());
    }

    @Test
    void shouldRetryTheSameExportWhenTheOrderWindowContractIsRejected() {
        NoonPullTaskRecord task = createOrderTask("orders:2026-05-21..2026-05-21");
        FakeReportProvider provider = FakeReportProvider.ready(
                "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                        + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                        + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                        + "108065,AE,AE,AE,,NAEI50094671190-1,PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                        + "Processing,65.8,65.8,AED,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-20 23:29:16,,\n"
                        + "108065,AE,AE,AE,,NAEI50094671191-1,PAPERSAYSB360,Z02AD5F198C0C2E813C31Z-1,"
                        + "Processing,65.8,65.8,AED,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-22 00:01:00,,\n"
        );

        NoonReportPullResult result = puller.execute(
                task.getId(),
                orderRequest(),
                provider,
                new NoonOrderReportAdapter(
                        (fact) -> {
                        },
                        clock
                )::process
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals("provider_unavailable", persisted.getFailureType());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertEquals(Boolean.FALSE, persisted.getRequiresManualAction());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertTrue(persisted.getDiagnosticSummary().contains(
                "report_payload_contract_rejected"
        ));
    }

    @Test
    void shouldPersistRejectedPayloadDiagnosticWhileRetryingTheSameExport() {
        NoonPullTaskRecord task = createSalesTask("sales:reused-latest-export");

        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(),
                FakeReportProvider.ready("date,sku_parent,units_sold,sales_amount,currency\n2026-05-21,Z1,2,39.90,AED\n"),
                (file) -> NoonReportProcessResult.mappingFailed(
                        1,
                        "provider_reused_latest_export: requested=2025-11-28..2025-12-27; actual=2026-05-19..2026-05-19"
                )
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals("provider_unavailable", persisted.getFailureType());
        assertEquals(Boolean.TRUE, persisted.getRetryable());
        assertEquals("EXP-1", stringProperty(persisted, "reportExportId"));
        assertTrue(persisted.getDiagnosticSummary().contains("provider_reused_latest_export"));
        assertTrue(persisted.getDiagnosticSummary().contains("requested=2025-11-28..2025-12-27"));
        assertTrue(persisted.getDiagnosticSummary().contains("actual=2026-05-19..2026-05-19"));
    }

    private NoonReportPullRequest salesRequest() {
        return salesRequest(2);
    }

    private NoonReportPullRequest salesRequest(int maxPollAttempts) {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.SALES)
                .reportType("productviewsandsalesdata")
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .maxPollAttempts(maxPollAttempts)
                .build();
    }

    private NoonReportPullRequest orderRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.ORDER)
                .reportType("orderreport")
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .maxPollAttempts(2)
                .build();
    }

    private NoonReportPullRequest financeRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR69486")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .reportType(NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE)
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .maxPollAttempts(2)
                .build();
    }

    private void executeRiskFailure(NoonPullTaskRecord task, String message) {
        NoonReportPullResult result = puller.execute(
                task.getId(),
                salesRequest(18),
                FakeReportProvider.throwingOnPoll(message),
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
    }

    private NoonPullTaskRecord createSalesTask() {
        return createSalesTask("sales:2026-05-21");
    }

    private NoonPullTaskRecord createSalesTask(String target) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.SALES)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity(target)
                .targetDateFrom(LocalDate.of(2026, 5, 21))
                .targetDateTo(LocalDate.of(2026, 5, 21))
                .build()).orElseThrow();
    }

    private NoonPullTaskRecord createFinanceTask(String target) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR69486")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR69486")
                .siteCode("SA")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity(target)
                .targetDateFrom(LocalDate.of(2026, 5, 21))
                .targetDateTo(LocalDate.of(2026, 5, 21))
                .build()).orElseThrow();
    }

    private NoonPullTaskRecord createOrderTask(String target) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity(target)
                .targetDateFrom(LocalDate.of(2026, 5, 21))
                .targetDateTo(LocalDate.of(2026, 5, 21))
                .build()).orElseThrow();
    }

    private Object property(NoonPullTaskRecord task, String propertyName) {
        try {
            Method method = NoonPullTaskRecord.class.getMethod(
                    "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1)
            );
            return method.invoke(task);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing property: " + propertyName, exception);
        }
    }

    private String stringProperty(NoonPullTaskRecord task, String propertyName) {
        Object value = property(task, propertyName);
        return value == null ? null : value.toString();
    }

    private int intProperty(NoonPullTaskRecord task, String propertyName) {
        Object value = property(task, propertyName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new AssertionError("Expected numeric property: " + propertyName + ", got " + value);
    }

}
