package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonReportPullerTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonReportPuller puller;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
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
    void shouldFailWithTypedFailureForMissingDownloadUrlAndFailedExportButKeepPendingRunning() {
        NoonPullTaskRecord missingUrlTask = createSalesTask("sales:missing-url");
        NoonReportPullResult missingUrl = puller.execute(
                missingUrlTask.getId(),
                salesRequest(),
                FakeReportProvider.missingDownloadUrl(),
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );

        assertEquals(NoonPullTaskStatus.FAILED, missingUrl.getStatus());
        assertEquals("mapping_failed", repository.selectTask(missingUrlTask.getId()).getFailureType());

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
        assertEquals("risk_backoff", stringProperty(persisted, "readinessState"));
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
    void shouldDelayOrderReportWithoutCallingProviderWhileReportScopeIsInRiskBackoff() {
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

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertTrue(orderProvider.calls.isEmpty());
        assertEquals("blocked_by_risk_control", persisted.getFailureType());
        assertEquals(NoonPullRetryAction.DELAY.name(), persisted.getRetryAction());
        assertEquals("risk_backoff", stringProperty(persisted, "readinessState"));
        assertEquals(LocalDateTime.of(2026, 5, 22, 9, 2), property(persisted, "reportNextPollAt"));
    }

    @Test
    void shouldRecordProofForEmptySalesReportPendingConfirmation() {
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
        assertEquals("empty_report_pending_confirmation", persisted.getFailureType());
        assertTrue(persisted.getDiagnosticSummary().contains("exportStatus=READY"));
        assertTrue(persisted.getDiagnosticSummary().contains("download=true"));
        assertTrue(persisted.getDiagnosticSummary().contains("totalRows=0"));
        assertTrue(persisted.getDiagnosticSummary().contains("csvHeader=valid"));
        assertTrue(persisted.getDiagnosticSummary().contains("importedRows=0"));
        assertTrue(persisted.getDiagnosticSummary().contains("confirmationDeadline="));
    }

    @Test
    void shouldMarkConfirmedEmptyAfterSalesConfirmationWindow() {
        NoonPullTaskRecord task = createSalesTask("sales:confirmed-empty");
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
                        Clock.fixed(Instant.parse("2026-05-26T09:00:00Z"), ZoneOffset.UTC)
                )::process
        );
        NoonPullTaskRecord persisted = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("confirmed_empty", persisted.getFailureType());
        assertEquals(Boolean.FALSE, persisted.getRetryable());
        assertTrue(persisted.getDiagnosticSummary().contains("exportStatus=READY"));
        assertTrue(persisted.getDiagnosticSummary().contains("totalRows=0"));
        assertTrue(persisted.getDiagnosticSummary().contains("confirmed_empty"));
    }

    @Test
    void shouldAcceptEmptyFinanceTransactionReportAsSuccessfulSettlementObservation() {
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

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, persisted.getStatus());
        assertEquals(null, persisted.getFailureType());
        assertEquals("confirmed_empty", persisted.getReadinessState());
        assertTrue(persisted.getDiagnosticSummary().contains("confirmed_empty"));
        assertTrue(persisted.getDiagnosticSummary().contains("accepted_empty_settlement_window"));
        assertNotNull(plan.getLatestSuccessAt());
        assertEquals(null, plan.getLatestFailureAt());
        assertEquals(null, plan.getLatestFailureType());
    }

    @Test
    void shouldPersistMappingFailedDiagnosticFromReportHandler() {
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

        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("mapping_failed", persisted.getFailureType());
        assertTrue(persisted.getDiagnosticSummary().contains("provider_reused_latest_export"));
        assertTrue(persisted.getDiagnosticSummary().contains("requested=2025-11-28..2025-12-27"));
        assertTrue(persisted.getDiagnosticSummary().contains("actual=2026-05-19..2026-05-19"));
    }

    private NoonReportPullRequest salesRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.SALES)
                .reportType("productviewsandsalesdata")
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .maxPollAttempts(2)
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
                salesRequest(),
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

    private static final class FakeReportProvider implements NoonReportProvider {
        private final List<String> calls = new ArrayList<>();
        private final List<NoonReportExportStatus> pollStatuses;
        private final byte[] content;
        private final RuntimeException pollException;

        private FakeReportProvider(NoonReportExportStatus pollStatus, String content) {
            this(List.of(pollStatus), content, null);
        }

        private FakeReportProvider(List<NoonReportExportStatus> pollStatuses, String content, RuntimeException pollException) {
            this.pollStatuses = new ArrayList<>(pollStatuses);
            this.content = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            this.pollException = pollException;
        }

        private static FakeReportProvider ready(String content) {
            return new FakeReportProvider(NoonReportExportStatus.ready("https://download.test/sales.csv"), content);
        }

        private static FakeReportProvider missingDownloadUrl() {
            return new FakeReportProvider(NoonReportExportStatus.ready(null), "");
        }

        private static FakeReportProvider pending() {
            return new FakeReportProvider(NoonReportExportStatus.pending(), "");
        }

        private static FakeReportProvider failedExport() {
            return new FakeReportProvider(NoonReportExportStatus.failed("export failed"), "");
        }

        private static FakeReportProvider sequence(NoonReportExportStatus... statuses) {
            return new FakeReportProvider(Arrays.asList(statuses),
                    "date,sku_parent,units_sold,sales_amount,currency\n2026-05-21,Z1,2,39.90,AED\n",
                    null);
        }

        private static FakeReportProvider throwingOnPoll(String message) {
            return new FakeReportProvider(List.of(NoonReportExportStatus.pending()), "", new RuntimeException(message));
        }

        @Override
        public String createExport(NoonReportPullRequest request) {
            calls.add("create");
            return "EXP-1";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            calls.add("poll:" + exportId);
            if (pollException != null) {
                throw pollException;
            }
            if (pollStatuses.isEmpty()) {
                return NoonReportExportStatus.pending();
            }
            if (pollStatuses.size() == 1) {
                return pollStatuses.get(0);
            }
            return pollStatuses.remove(0);
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            calls.add("download");
            return content;
        }
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

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
