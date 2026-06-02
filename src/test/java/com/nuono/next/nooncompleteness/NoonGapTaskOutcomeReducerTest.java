package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.noonpull.NoonPullTriggerMode;
import com.nuono.next.noonpull.NoonPullType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonGapTaskOutcomeReducerTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryCompletenessRepository repository;
    private NoonGapTaskOutcomeReducer reducer;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCompletenessRepository();
        reducer = new NoonGapTaskOutcomeReducer(repository, FIXED_CLOCK);
    }

    @Test
    void succeededTaskWithRowsMarksGapSucceededAndUpdatesLatestCompleteness() {
        repository.insertCompleteness(completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS));
        repository.insertGapWindow(gap(1L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.LATEST_DAILY, LocalDate.parse("2026-05-24")));

        reducer.reduce(task(
                NoonPullTaskStatus.SUCCEEDED,
                130001L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-24"),
                12,
                "batch-sales-001",
                "ok cookie=abc Authorization: Bearer token-123 https://download.noon.test/raw.csv"
        ));

        NoonDataGapWindowRecord gap = gap(NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataGapStatus.SUCCEEDED, gap.getStatus());
        assertEquals(12, gap.getRowOrItemCount());
        assertEquals("batch-sales-001", gap.getLinkedSourceBatchId());
        assertFalse(gap.getDiagnosticSummary().contains("abc"));
        assertFalse(gap.getDiagnosticSummary().contains("token-123"));
        assertFalse(gap.getDiagnosticSummary().contains("download.noon.test"));

        NoonDataCompletenessRecord row = row(NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataLatestStatus.READY, row.getLatestStatus());
        assertEquals(LocalDate.parse("2026-05-24"), row.getLatestDataDate());
        assertEquals("batch-sales-001", row.getLastSourceBatchId());
    }

    @Test
    void zeroRowLatestStaysPendingConfirmationButOlderHistoryCanBeConfirmedEmpty() {
        repository.insertCompleteness(completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS));
        repository.insertGapWindow(gap(1L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.LATEST_DAILY, LocalDate.parse("2026-05-24")));

        reducer.reduce(task(
                NoonPullTaskStatus.SUCCEEDED,
                130001L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-24"),
                0,
                "batch-empty-latest",
                "0 rows"
        ));

        NoonDataGapWindowRecord latest = gap(NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataGapStatus.PENDING_CONFIRMATION, latest.getStatus());
        assertEquals("empty_report_pending_confirmation", latest.getFailureType());
        assertEquals(Boolean.TRUE, latest.getRetryable());

        InMemoryCompletenessRepository historyRepository = new InMemoryCompletenessRepository();
        NoonGapTaskOutcomeReducer historyReducer = new NoonGapTaskOutcomeReducer(historyRepository, FIXED_CLOCK);
        historyRepository.insertCompleteness(completenessRow(NoonDataCategory.SALES_ORDER));
        historyRepository.insertGapWindow(gap(
                2L,
                NoonDataCategory.SALES_ORDER,
                NoonDataGapWindowType.HISTORY_BACKFILL,
                LocalDate.parse("2025-12-01"),
                LocalDate.parse("2025-12-31")
        ));

        historyReducer.reduce(task(
                NoonPullTaskStatus.SUCCEEDED,
                130002L,
                NoonDataCategory.SALES_ORDER,
                LocalDate.parse("2025-12-01"),
                LocalDate.parse("2025-12-31"),
                0,
                "batch-empty-history",
                "confirmed empty"
        ));

        NoonDataGapWindowRecord history = historyRepository.listGapWindows(new NoonDataGapQuery()).get(0);
        assertEquals(NoonDataGapStatus.CONFIRMED_EMPTY, history.getStatus());
        assertEquals(Boolean.FALSE, history.getRetryable());
        assertTrue(history.getCompletedEmptyEvidenceSummary().contains("2025-12-01..2025-12-31"));
        assertEquals(NoonDataHistoryStatus.CONFIRMED_EMPTY, historyRepository.listCompleteness(new NoonDataCompletenessQuery()).get(0).getHistoryStatus());
    }

    @Test
    void retryableManualAndRetentionFailuresStayDistinct() {
        repository.insertCompleteness(completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS));
        repository.insertGapWindow(gap(1L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.HISTORY_BACKFILL, LocalDate.parse("2026-02-24")));
        reducer.reduce(failedTask(130001L, NoonDataCategory.SALES_PRODUCT_VIEWS, "timeout", true, false));
        NoonDataGapWindowRecord retryable = gap(NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataGapStatus.WAITING_RETRY, retryable.getStatus());
        assertEquals(1, retryable.getAttempts());
        assertNotNull(retryable.getNextRetryAt());
        assertEquals(Boolean.TRUE, retryable.getRetryable());

        InMemoryCompletenessRepository manualRepository = new InMemoryCompletenessRepository();
        NoonGapTaskOutcomeReducer manualReducer = new NoonGapTaskOutcomeReducer(manualRepository, FIXED_CLOCK);
        manualRepository.insertCompleteness(completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS));
        manualRepository.insertGapWindow(gap(2L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.HISTORY_BACKFILL, LocalDate.parse("2026-02-24")));
        manualReducer.reduce(failedTask(130002L, NoonDataCategory.SALES_PRODUCT_VIEWS, "missing_columns", false, true));
        NoonDataGapWindowRecord manual = manualRepository.listGapWindows(new NoonDataGapQuery()).get(0);
        assertEquals(NoonDataGapStatus.FAILED, manual.getStatus());
        assertEquals(Boolean.FALSE, manual.getRetryable());
        assertEquals(Boolean.TRUE, manual.getRequiresManualAction());

        InMemoryCompletenessRepository retentionRepository = new InMemoryCompletenessRepository();
        NoonGapTaskOutcomeReducer retentionReducer = new NoonGapTaskOutcomeReducer(retentionRepository, FIXED_CLOCK);
        retentionRepository.insertCompleteness(completenessRow(NoonDataCategory.SALES_ORDER));
        retentionRepository.insertGapWindow(gap(3L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, LocalDate.parse("2025-11-25")));
        retentionReducer.reduce(failedTask(130003L, NoonDataCategory.SALES_ORDER, "provider_retention_limit", false, false));
        NoonDataGapWindowRecord retention = retentionRepository.listGapWindows(new NoonDataGapQuery()).get(0);
        assertEquals(NoonDataGapStatus.PROVIDER_RETENTION_LIMIT, retention.getStatus());
        assertEquals(Boolean.FALSE, retention.getRetryable());
        assertEquals(NoonDataHistoryStatus.PROVIDER_RETENTION_LIMIT, retentionRepository.listCompleteness(new NoonDataCompletenessQuery()).get(0).getHistoryStatus());
    }

    @Test
    void partialSuccessRecordsCountsAndLeavesGapRetryable() {
        repository.insertCompleteness(completenessRow(NoonDataCategory.SALES_ORDER));
        repository.insertGapWindow(gap(1L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, LocalDate.parse("2026-02-24")));

        reducer.reduce(task(
                NoonPullTaskStatus.PARTIAL,
                130001L,
                NoonDataCategory.SALES_ORDER,
                LocalDate.parse("2026-02-24"),
                LocalDate.parse("2026-03-26"),
                7,
                "batch-partial",
                "partial success after 7 rows"
        ));

        NoonDataGapWindowRecord gap = gap(NoonDataCategory.SALES_ORDER);
        assertEquals(NoonDataGapStatus.WAITING_RETRY, gap.getStatus());
        assertEquals(7, gap.getRowOrItemCount());
        assertEquals(Boolean.TRUE, gap.getRetryable());
        assertEquals("batch-partial", gap.getLinkedSourceBatchId());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, row(NoonDataCategory.SALES_ORDER).getHistoryStatus());
    }

    @Test
    void productDetailInterfaceTaskReducesToProductDetailGap() {
        repository.insertCompleteness(completenessRow(NoonDataCategory.PRODUCT_LIST));
        repository.insertCompleteness(completenessRow(NoonDataCategory.PRODUCT_DETAIL));
        repository.insertGapWindow(gap(1L, NoonDataCategory.PRODUCT_LIST, NoonDataGapWindowType.PRODUCT_BASELINE, LocalDate.parse("2026-05-25")));
        repository.insertGapWindow(gap(2L, NoonDataCategory.PRODUCT_DETAIL, NoonDataGapWindowType.PRODUCT_DETAIL_BASELINE, LocalDate.parse("2026-05-25")));

        reducer.reduce(productTask(
                NoonPullTaskStatus.PARTIAL,
                130002L,
                "product-detail:2026-05-25..2026-05-25",
                6,
                "product-detail-batch",
                "product detail baseline sync attempted=374; succeeded=6; failed=368"
        ));

        NoonDataGapWindowRecord detailGap = gap(NoonDataCategory.PRODUCT_DETAIL);
        assertEquals(NoonDataGapStatus.WAITING_RETRY, detailGap.getStatus());
        assertEquals(6, detailGap.getRowOrItemCount());
        assertEquals(130002L, detailGap.getLinkedPullTaskId());
        assertEquals(130002L, row(NoonDataCategory.PRODUCT_DETAIL).getLastTaskId());

        NoonDataGapWindowRecord listGap = gap(NoonDataCategory.PRODUCT_LIST);
        assertEquals(NoonDataGapStatus.PENDING, listGap.getStatus());
        assertEquals(130001L, listGap.getLinkedPullTaskId());
    }

    @Test
    void productListBaselineSuccessMarksLatestReady() {
        NoonDataCompletenessRecord row = completenessRow(NoonDataCategory.PRODUCT_LIST);
        row.setHistoryStatus(NoonDataHistoryStatus.NOT_REQUIRED);
        repository.insertCompleteness(row);
        repository.insertGapWindow(gap(1L, NoonDataCategory.PRODUCT_LIST, NoonDataGapWindowType.PRODUCT_BASELINE, LocalDate.parse("2026-05-25")));

        reducer.reduce(productTask(
                NoonPullTaskStatus.SUCCEEDED,
                130001L,
                "product-list:2026-05-25..2026-05-25",
                408,
                "product-list-batch",
                "pages=5; requests=5"
        ));

        NoonDataCompletenessRecord updated = row(NoonDataCategory.PRODUCT_LIST);
        assertEquals(NoonDataLatestStatus.READY, updated.getLatestStatus());
        assertEquals(LocalDate.parse("2026-05-25"), updated.getLatestDataDate());
        assertEquals(0, updated.getActiveGapCount());
        assertEquals(Boolean.FALSE, updated.isPatrolEnabled());
    }

    private NoonPullTaskRecord failedTask(
            Long taskId,
            NoonDataCategory category,
            String failureType,
            boolean retryable,
            boolean manual
    ) {
        NoonPullTaskRecord task = task(
                NoonPullTaskStatus.FAILED,
                taskId,
                category,
                LocalDate.parse("2026-02-24"),
                LocalDate.parse("2026-03-26"),
                null,
                null,
                "failed password=secret"
        );
        task.setFailureType(failureType);
        task.setRetryable(retryable);
        task.setRequiresManualAction(manual);
        return task;
    }

    private NoonPullTaskRecord task(
            NoonPullTaskStatus status,
            Long taskId,
            NoonDataCategory category,
            LocalDate dateFrom,
            LocalDate dateTo,
            Integer processedCount,
            String sourceBatchId,
            String diagnosticSummary
    ) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(taskId);
        task.setPlanId(120001L);
        task.setOwnerUserId(307L);
        task.setStoreCode("STR108065-NAE");
        task.setSiteCode("AE");
        task.setPullType(NoonPullType.REPORT);
        task.setDataDomain(category == NoonDataCategory.SALES_ORDER ? NoonPullDataDomain.ORDER : NoonPullDataDomain.SALES);
        task.setTriggerMode(NoonPullTriggerMode.GAP_BACKFILL);
        task.setTargetIdentity(category.name() + ":" + dateFrom + ".." + dateTo);
        task.setTargetDateFrom(dateFrom);
        task.setTargetDateTo(dateTo);
        task.setStatus(status);
        task.setProcessedItemCount(processedCount);
        task.setSourceBatchId(sourceBatchId);
        task.setDiagnosticSummary(diagnosticSummary);
        return task;
    }

    private NoonPullTaskRecord productTask(
            NoonPullTaskStatus status,
            Long taskId,
            String targetIdentity,
            Integer processedCount,
            String sourceBatchId,
            String diagnosticSummary
    ) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(taskId);
        task.setPlanId(120001L);
        task.setOwnerUserId(307L);
        task.setStoreCode("STR108065-NAE");
        task.setSiteCode("AE");
        task.setPullType(NoonPullType.INTERFACE);
        task.setDataDomain(NoonPullDataDomain.PRODUCT);
        task.setTriggerMode(NoonPullTriggerMode.MANUAL_REFRESH);
        task.setTargetIdentity(targetIdentity);
        task.setTargetDateFrom(LocalDate.parse("2026-05-25"));
        task.setTargetDateTo(LocalDate.parse("2026-05-25"));
        task.setStatus(status);
        task.setProcessedItemCount(processedCount);
        task.setSourceBatchId(sourceBatchId);
        task.setDiagnosticSummary(diagnosticSummary);
        return task;
    }

    private NoonDataCompletenessRecord row(NoonDataCategory category) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setCategory(category);
        return repository.listCompleteness(query).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing row for " + category));
    }

    private NoonDataGapWindowRecord gap(NoonDataCategory category) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setCategory(category);
        return repository.listGapWindows(query).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing gap for " + category));
    }

    private static NoonDataCompletenessRecord completenessRow(NoonDataCategory category) {
        NoonDataCompletenessRecord row = new NoonDataCompletenessRecord();
        row.setId(900001L + category.ordinal());
        row.setOwnerUserId(307L);
        row.setStoreCode("STR108065-NAE");
        row.setSiteCode("AE");
        row.setCategory(category);
        row.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(1);
        row.setCreatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        row.setUpdatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        return row;
    }

    private static NoonDataGapWindowRecord gap(
            Long id,
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            LocalDate date
    ) {
        return gap(id, category, windowType, date, date);
    }

    private static NoonDataGapWindowRecord gap(
            Long id,
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(910000L + id);
        gap.setCompletenessId(900001L + category.ordinal());
        gap.setOwnerUserId(307L);
        gap.setStoreCode("STR108065-NAE");
        gap.setSiteCode("AE");
        gap.setCategory(category);
        gap.setWindowType(windowType);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(NoonDataGapStatus.PENDING);
        gap.setAttempts(0);
        gap.setLinkedPullTaskId(130000L + id);
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setCreatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        gap.setUpdatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        return gap;
    }

    private static final class InMemoryCompletenessRepository implements NoonDataCompletenessRepository {
        private final AtomicLong ids = new AtomicLong(900000);
        private final List<NoonDataCompletenessRecord> completeness = new ArrayList<>();
        private final List<NoonDataGapWindowRecord> gaps = new ArrayList<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return ids.incrementAndGet();
        }

        @Override
        public void insertCompleteness(NoonDataCompletenessRecord record) {
            for (int index = 0; index < completeness.size(); index++) {
                NoonDataCompletenessRecord existing = completeness.get(index);
                if (sameCompleteness(existing, record)) {
                    completeness.set(index, record.copy());
                    return;
                }
            }
            completeness.add(record.copy());
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            return completeness.stream()
                    .filter((row) -> query.getOwnerUserId() == null || query.getOwnerUserId().equals(row.getOwnerUserId()))
                    .filter((row) -> query.getStoreCode() == null || query.getStoreCode().equals(row.getStoreCode()))
                    .filter((row) -> query.getSiteCode() == null || query.getSiteCode().equals(row.getSiteCode()))
                    .filter((row) -> query.getCategory() == null || query.getCategory() == row.getCategory())
                    .filter((row) -> query.getLatestStatus() == null || query.getLatestStatus() == row.getLatestStatus())
                    .filter((row) -> query.getHistoryStatus() == null || query.getHistoryStatus() == row.getHistoryStatus())
                    .sorted(Comparator
                            .comparing(NoonDataCompletenessRecord::getOwnerUserId)
                            .thenComparing(NoonDataCompletenessRecord::getStoreCode)
                            .thenComparing(NoonDataCompletenessRecord::getSiteCode)
                            .thenComparing(NoonDataCompletenessRecord::getCategory))
                    .map(NoonDataCompletenessRecord::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public void insertGapWindow(NoonDataGapWindowRecord record) {
            for (int index = 0; index < gaps.size(); index++) {
                NoonDataGapWindowRecord existing = gaps.get(index);
                if (sameGap(existing, record)) {
                    gaps.set(index, record.copy());
                    return;
                }
            }
            gaps.add(record.copy());
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            return gaps.stream()
                    .filter((gap) -> query.getOwnerUserId() == null || query.getOwnerUserId().equals(gap.getOwnerUserId()))
                    .filter((gap) -> query.getStoreCode() == null || query.getStoreCode().equals(gap.getStoreCode()))
                    .filter((gap) -> query.getSiteCode() == null || query.getSiteCode().equals(gap.getSiteCode()))
                    .filter((gap) -> query.getCategory() == null || query.getCategory() == gap.getCategory())
                    .filter((gap) -> query.getStatus() == null || query.getStatus() == gap.getStatus())
                    .filter((gap) -> query.getFailureType() == null || query.getFailureType().equals(gap.getFailureType()))
                    .filter((gap) -> query.getRetryable() == null || query.getRetryable().equals(gap.getRetryable()))
                    .sorted(Comparator
                            .comparing(NoonDataGapWindowRecord::getOwnerUserId)
                            .thenComparing(NoonDataGapWindowRecord::getStoreCode)
                            .thenComparing(NoonDataGapWindowRecord::getSiteCode)
                            .thenComparing(NoonDataGapWindowRecord::getCategory)
                            .thenComparing(NoonDataGapWindowRecord::getWindowType)
                            .thenComparing(NoonDataGapWindowRecord::getDateFrom))
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }

        private boolean sameCompleteness(NoonDataCompletenessRecord left, NoonDataCompletenessRecord right) {
            return Objects.equals(left.getOwnerUserId(), right.getOwnerUserId())
                    && Objects.equals(left.getStoreCode(), right.getStoreCode())
                    && Objects.equals(left.getSiteCode(), right.getSiteCode())
                    && left.getCategory() == right.getCategory();
        }

        private boolean sameGap(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
            return Objects.equals(left.getCompletenessId(), right.getCompletenessId())
                    && left.getCategory() == right.getCategory()
                    && left.getWindowType() == right.getWindowType()
                    && Objects.equals(left.getDateFrom(), right.getDateFrom())
                    && Objects.equals(left.getDateTo(), right.getDateTo());
        }
    }
}
