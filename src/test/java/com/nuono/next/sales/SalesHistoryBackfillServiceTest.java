package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.nooncompleteness.NoonDataCategory;
import com.nuono.next.nooncompleteness.NoonDataCompletenessQuery;
import com.nuono.next.nooncompleteness.NoonDataCompletenessRecord;
import com.nuono.next.nooncompleteness.NoonDataCompletenessRepository;
import com.nuono.next.nooncompleteness.NoonDataGapQuery;
import com.nuono.next.nooncompleteness.NoonDataGapStatus;
import com.nuono.next.nooncompleteness.NoonDataGapWindowRecord;
import com.nuono.next.nooncompleteness.NoonDataGapWindowType;
import com.nuono.next.nooncompleteness.NoonGapPatrolActionResult;
import com.nuono.next.nooncompleteness.NoonGapPatrolActionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SalesHistoryBackfillServiceTest {

    @Test
    void coverageMarksSelectedRangeIncompleteAndShowsQueuedBackfillFromGapWindow() {
        InMemoryCompletenessRepository repository = new InMemoryCompletenessRepository();
        repository.insertGapWindow(gap(
                910001L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataGapStatus.PENDING,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25),
                10001L
        ));
        SalesHistoryBackfillService service = new SalesHistoryBackfillService(repository, null);

        SalesHistoryCoverage coverage = service.coverage(
                query(),
                List.of(
                        fact(LocalDate.of(2026, 5, 1)),
                        fact(LocalDate.of(2026, 5, 19))
                ),
                SalesPriceTrendResult.ready(List.of(new SalesPriceTrendBucket(
                        LocalDate.of(2026, 5, 6),
                        "05-06",
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00"),
                        1,
                        "AED"
                )))
        );

        assertEquals(LocalDate.of(2025, 11, 26), coverage.getRequestedDateFrom());
        assertEquals(LocalDate.of(2026, 5, 1), coverage.getSalesFactDateFrom());
        assertEquals(LocalDate.of(2026, 5, 19), coverage.getSalesFactDateTo());
        assertEquals(LocalDate.of(2026, 5, 6), coverage.getPriceDateFrom());
        assertEquals(false, coverage.isSalesFactsFullyCovered());
        assertEquals(false, coverage.isPriceFactsFullyCovered());
        assertEquals("backfill_queued", coverage.getBackfill().getState());
        assertEquals(false, coverage.getBackfill().isActionAvailable());
        assertEquals(List.of(910001L), coverage.getBackfill().getGapIds());
        assertEquals(List.of(10001L), coverage.getBackfill().getTaskIds());
    }

    @Test
    void coverageWithoutOverlappingGapAllowsManualBackfill() {
        SalesHistoryBackfillService service = new SalesHistoryBackfillService(new InMemoryCompletenessRepository(), null);

        SalesHistoryCoverage coverage = service.coverage(
                query(),
                List.of(fact(LocalDate.of(2026, 5, 19))),
                SalesPriceTrendResult.empty()
        );

        assertEquals("needs_backfill", coverage.getBackfill().getState());
        assertEquals(true, coverage.getBackfill().isActionAvailable());
    }

    @Test
    void coverageTreatsCompletedHistoryBackfillAsCoveredEvenWhenOlderStaleGapsRemain() {
        InMemoryCompletenessRepository repository = new InMemoryCompletenessRepository();
        repository.insertGapWindow(gap(
                910152L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataGapStatus.SUCCEEDED,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25),
                130007L
        ));
        repository.insertGapWindow(gap(
                910153L,
                NoonDataCategory.SALES_ORDER,
                NoonDataGapStatus.SUCCEEDED,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25),
                130008L
        ));
        repository.insertGapWindow(gap(
                910129L,
                NoonDataCategory.SALES_ORDER,
                NoonDataGapStatus.PENDING,
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 5, 24),
                null
        ));
        NoonDataGapWindowRecord latestDaily = gap(
                910097L,
                NoonDataCategory.SALES_ORDER,
                NoonDataGapStatus.PENDING,
                LocalDate.of(2026, 5, 24),
                LocalDate.of(2026, 5, 24),
                null
        );
        latestDaily.setWindowType(NoonDataGapWindowType.LATEST_DAILY);
        repository.insertGapWindow(latestDaily);
        SalesHistoryBackfillService service = new SalesHistoryBackfillService(repository, null);

        SalesHistoryCoverage coverage = service.coverage(
                query(),
                List.of(fact(LocalDate.of(2026, 3, 25)), fact(LocalDate.of(2026, 5, 24))),
                SalesPriceTrendResult.ready(List.of(new SalesPriceTrendBucket(
                        LocalDate.of(2026, 4, 28),
                        "04-28",
                        new BigDecimal("42.00"),
                        new BigDecimal("42.00"),
                        new BigDecimal("42.00"),
                        1,
                        "AED"
                )))
        );

        assertEquals("covered", coverage.getBackfill().getState());
        assertEquals(false, coverage.getBackfill().isActionAvailable());
        assertEquals(List.of(), coverage.getBackfill().getGapIds());
    }

    @Test
    void requestBackfillCreatesSalesAndOrderHistoryGapsThroughPatrolActions() {
        NoonGapPatrolActionService actionService = mock(NoonGapPatrolActionService.class);
        NoonGapPatrolActionResult salesResult = result(910001L, 10001L);
        NoonGapPatrolActionResult orderResult = result(910002L, 10002L);
        when(actionService.syncHistoryRange(
                10002L,
                "STR245027-NAE",
                "AE",
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25)
        )).thenReturn(salesResult);
        when(actionService.syncHistoryRange(
                10002L,
                "STR245027-NAE",
                "AE",
                NoonDataCategory.SALES_ORDER,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25)
        )).thenReturn(orderResult);
        SalesHistoryBackfillService service = new SalesHistoryBackfillService(new InMemoryCompletenessRepository(), actionService);

        SalesHistoryBackfillResult result = service.requestBackfill(new SalesHistoryBackfillCommand(
                10002L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25)
        ));

        assertEquals(2, result.getPlannedTaskCount());
        assertEquals(List.of(10001L, 10002L), result.getPlannedTaskIds());
        assertEquals(List.of("SALES_PRODUCT_VIEWS", "SALES_ORDER"), result.getCategories());
        verify(actionService).syncHistoryRange(
                10002L,
                "STR245027-NAE",
                "AE",
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25)
        );
        verify(actionService).syncHistoryRange(
                10002L,
                "STR245027-NAE",
                "AE",
                NoonDataCategory.SALES_ORDER,
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25)
        );
    }

    private static NoonGapPatrolActionResult result(Long gapId, Long taskId) {
        NoonGapPatrolActionResult result = new NoonGapPatrolActionResult();
        result.setGapId(gapId);
        result.setStatus(NoonDataGapStatus.PENDING);
        result.setPlannedTaskCount(1);
        result.setPlannedTaskIds(List.of(taskId));
        result.setMessage("ok");
        return result;
    }

    private static SalesFactQuery query() {
        return new SalesFactQuery(
                10002L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2025, 11, 26),
                LocalDate.of(2026, 5, 25),
                "MILKYWAYA09",
                "Z580978E7ED8F9491B50BZ-1"
        );
    }

    private static DailySalesFact fact(LocalDate date) {
        return new DailySalesFact(
                "noon_productviewsandsalesdata",
                10001L,
                10002L,
                245027L,
                "STR245027-NAE",
                "AE",
                date,
                "MILKYWAYA09",
                "Z580978E7ED8F9491B50BZ-1",
                "Z580978E7ED8F9491B50BZ",
                "AE",
                "AED",
                "Galaxy Star Projector",
                10,
                20,
                1,
                1,
                0,
                1,
                new BigDecimal("50.00"),
                null,
                null,
                null
        );
    }

    private static NoonDataGapWindowRecord gap(
            Long id,
            NoonDataCategory category,
            NoonDataGapStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long taskId
    ) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(id);
        gap.setCompletenessId(900001L);
        gap.setOwnerUserId(10002L);
        gap.setStoreCode("STR245027-NAE");
        gap.setSiteCode("AE");
        gap.setCategory(category);
        gap.setWindowType(NoonDataGapWindowType.HISTORY_BACKFILL);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(status);
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setLinkedPullTaskId(taskId);
        gap.setCreatedAt(LocalDateTime.of(2026, 5, 25, 0, 0));
        gap.setUpdatedAt(LocalDateTime.of(2026, 5, 25, 0, 0));
        return gap;
    }

    private static final class InMemoryCompletenessRepository implements NoonDataCompletenessRepository {
        private final AtomicLong ids = new AtomicLong(900000L);
        private final List<NoonDataGapWindowRecord> gaps = new ArrayList<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return ids.incrementAndGet();
        }

        @Override
        public void insertCompleteness(NoonDataCompletenessRecord record) {
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            return List.of();
        }

        @Override
        public void insertGapWindow(NoonDataGapWindowRecord record) {
            gaps.add(record.copy());
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            return gaps.stream()
                    .filter((gap) -> query.getOwnerUserId() == null || query.getOwnerUserId().equals(gap.getOwnerUserId()))
                    .filter((gap) -> query.getStoreCode() == null || query.getStoreCode().equals(gap.getStoreCode()))
                    .filter((gap) -> query.getSiteCode() == null || query.getSiteCode().equals(gap.getSiteCode()))
                    .filter((gap) -> query.getCategory() == null || query.getCategory() == gap.getCategory())
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }
    }
}
