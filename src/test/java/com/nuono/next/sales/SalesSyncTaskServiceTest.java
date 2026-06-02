package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SalesSyncTaskServiceTest {

    @Test
    void shouldPersistPendingExportAndResumeSameExportWithoutCreatingDuplicate() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        RecordingReportProvider provider = new RecordingReportProvider();
        RecordingImportService importService = new RecordingImportService();
        SalesSyncTaskService service = new SalesSyncTaskService(repository, provider, importService);
        SalesSyncTaskCommand command = command();

        SalesSyncTaskRecord pending = service.triggerAndRun(command);

        assertEquals("running", pending.getStatus());
        assertEquals("EXP-1", pending.getExportCode());
        assertEquals("RUNNING", pending.getExportStatus());
        assertNull(pending.getSourceBatchId());
        assertEquals(List.of("create", "poll:EXP-1"), provider.calls);
        assertEquals(0, importService.importedCommands.size());

        provider.nextPollStatus = NoonSalesReportExportStatus.complete("EXP-1", "https://download.test/sales.csv", 1);
        SalesSyncTaskRecord completed = service.triggerAndRun(command);

        assertEquals(pending.getId(), completed.getId());
        assertEquals("succeeded", completed.getStatus());
        assertEquals("EXP-1", completed.getExportCode());
        assertEquals("COMPLETE", completed.getExportStatus());
        assertEquals(9001L, completed.getSourceBatchId());
        assertEquals(List.of("create", "poll:EXP-1", "poll:EXP-1", "download:EXP-1"), provider.calls);
        assertEquals(1, provider.createCalls);
        assertEquals(1, importService.importedCommands.size());
    }

    private SalesSyncTaskCommand command() {
        return new SalesSyncTaskCommand(
                307L,
                245027L,
                "STR245027-NSA",
                "SA",
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 24),
                307L,
                "manual"
        );
    }

    private static final class RecordingReportProvider implements NoonSalesReportProvider {
        private final List<String> calls = new ArrayList<>();
        private int createCalls;
        private NoonSalesReportExportStatus nextPollStatus =
                NoonSalesReportExportStatus.pending("EXP-1", "RUNNING");

        @Override
        public NoonSalesReportExportStatus createExport(NoonSalesReportRequest request) {
            calls.add("create");
            createCalls++;
            return NoonSalesReportExportStatus.pending("EXP-1", "CREATED");
        }

        @Override
        public NoonSalesReportExportStatus pollExport(NoonSalesReportRequest request, String exportCode) {
            calls.add("poll:" + exportCode);
            return nextPollStatus;
        }

        @Override
        public NoonSalesReportPayload download(NoonSalesReportRequest request, NoonSalesReportExportStatus status) {
            calls.add("download:" + status.getExportCode());
            return new NoonSalesReportPayload("sales.csv", "csv");
        }

        @Override
        public int maxPollAttempts() {
            return 1;
        }
    }

    private static final class RecordingImportService extends NoonSalesCsvImportService {
        private final List<NoonSalesCsvImportCommand> importedCommands = new ArrayList<>();

        private RecordingImportService() {
            super(new NoonProductViewsSalesReportParser(), new NoopSalesFactRepository());
        }

        @Override
        public NoonSalesCsvImportResult importCsv(NoonSalesCsvImportCommand command) {
            importedCommands.add(command);
            return new NoonSalesCsvImportResult(
                    NoonSalesCsvImportService.SOURCE_SYSTEM,
                    9001L,
                    command.getSourceFilename(),
                    1,
                    1,
                    0,
                    LocalDate.of(2026, 5, 24),
                    LocalDate.of(2026, 5, 24)
            );
        }
    }

    private static final class NoopSalesFactRepository implements SalesFactRepository {
        @Override
        public long saveBatch(SalesImportBatch batch) {
            return 9001L;
        }

        @Override
        public void upsert(DailySalesFact fact) {
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return List.of();
        }
    }

    private static final class InMemoryTaskRepository implements SalesSyncTaskRepository {
        private long nextId = 20000L;
        private final Map<Long, SalesSyncTaskRecord> tasks = new LinkedHashMap<>();

        @Override
        public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
            SalesSyncTaskRecord task = SalesSyncTaskRecord.queued(nextId++, command);
            tasks.put(task.getId(), task);
            return task;
        }

        @Override
        public Optional<SalesSyncTaskRecord> findReusableExportTask(SalesSyncTaskCommand command) {
            return tasks.values().stream()
                    .filter(task -> "running".equals(task.getStatus()))
                    .filter(task -> "EXP-1".equals(task.getExportCode()))
                    .findFirst();
        }

        @Override
        public SalesSyncTaskRecord markRunning(Long taskId) {
            return put(tasks.get(taskId).withStatus("running"));
        }

        @Override
        public SalesSyncTaskRecord markExportStatus(Long taskId, NoonSalesReportExportStatus status) {
            return put(tasks.get(taskId).withExportStatus(status));
        }

        @Override
        public SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result) {
            return put(tasks.get(taskId).succeeded(result).withExportStatus(
                    NoonSalesReportExportStatus.complete(
                            tasks.get(taskId).getExportCode(),
                            tasks.get(taskId).getExportDownloadUrl(),
                            result.getTotalRows()
                    )
            ));
        }

        @Override
        public SalesSyncTaskRecord markFailed(Long taskId, String failureReason) {
            return put(tasks.get(taskId).failed(failureReason));
        }

        @Override
        public SalesSyncTaskRecord findById(Long taskId) {
            return tasks.get(taskId);
        }

        private SalesSyncTaskRecord put(SalesSyncTaskRecord task) {
            tasks.put(task.getId(), task);
            return task;
        }
    }
}
