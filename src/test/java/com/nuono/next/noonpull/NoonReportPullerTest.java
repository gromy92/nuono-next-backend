package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonReportPullerTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonReportPuller puller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        puller = new NoonReportPuller(foundationService);
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
        assertEquals(List.of("create", "poll", "download"), provider.calls);
        assertNotNull(result.getFileDigestSha256());
        assertTrue(persistedTask.getSourceBatchId().startsWith("noon-report-sales-"));
        assertEquals(1, persistedTask.getProcessedItemCount());
        assertEquals(1, persistedTask.getRequestCount());
        assertTrue(persistedTask.getDiagnosticSummary().contains("productviewsandsalesdata"));
        assertTrue(persistedTask.getDiagnosticSummary().contains("digest="));
    }

    @Test
    void shouldFailWithTypedFailureForMissingDownloadUrlAndFailedExport() {
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

    }

    @Test
    void shouldKeepPendingReportExportRunningAndResumeFromCheckpoint() {
        NoonPullTaskRecord task = createSalesTask("sales:resume-export");
        FakeReportProvider provider = FakeReportProvider.pending();

        NoonReportPullResult pending = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(0, 0)
        );
        NoonPullTaskRecord pendingTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.RUNNING, pending.getStatus());
        assertEquals(NoonPullTaskStatus.RUNNING, pendingTask.getStatus());
        assertEquals("report-export:EXP-1", pendingTask.getCheckpointCursor());
        assertEquals("export_pending", pendingTask.getReadinessState());
        assertEquals(List.of("create", "poll", "poll"), provider.calls);

        provider.calls.clear();
        provider.pollStatus = NoonReportExportStatus.ready("https://download.test/sales.csv");
        NoonReportPullResult resumed = puller.execute(
                task.getId(),
                salesRequest(),
                provider,
                (file) -> NoonReportProcessResult.succeeded(1, 0)
        );
        NoonPullTaskRecord completedTask = repository.selectTask(task.getId());

        assertEquals(NoonPullTaskStatus.SUCCEEDED, resumed.getStatus());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, completedTask.getStatus());
        assertEquals(List.of("poll", "download"), provider.calls);
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

    private static final class FakeReportProvider implements NoonReportProvider {
        private final List<String> calls = new ArrayList<>();
        private NoonReportExportStatus pollStatus;
        private final byte[] content;

        private FakeReportProvider(NoonReportExportStatus pollStatus, String content) {
            this.pollStatus = pollStatus;
            this.content = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
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

        @Override
        public String createExport(NoonReportPullRequest request) {
            calls.add("create");
            return "EXP-1";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            calls.add("poll");
            return pollStatus;
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            calls.add("download");
            return content;
        }
    }
}
