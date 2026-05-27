package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonDataCompletenessSeedServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T02:30:00Z"), ZoneOffset.UTC);

    @Test
    void seedsFourFirstVersionCategoriesForNewScope() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataCompletenessSeedService service = new NoonDataCompletenessSeedService(repository, FIXED_CLOCK);

        service.seedNewScope(NoonDataCompletenessSeedCommand.builder()
                .ownerUserId(307L)
                .storeCode(" STR108065-NSA ")
                .siteCode(" sa ")
                .productProjectionPresent(true)
                .build());

        List<NoonDataCompletenessRecord> rows = repository.listCompleteness(new NoonDataCompletenessQuery());
        assertEquals(List.of(
                NoonDataCategory.PRODUCT_LIST,
                NoonDataCategory.PRODUCT_DETAIL,
                NoonDataCategory.SALES_ORDER,
                NoonDataCategory.SALES_PRODUCT_VIEWS
        ), rows.stream().map(NoonDataCompletenessRecord::getCategory).collect(Collectors.toList()));

        assertEquals(NoonDataLatestStatus.READY, row(rows, NoonDataCategory.PRODUCT_LIST).getLatestStatus());
        assertEquals(NoonDataHistoryStatus.NOT_REQUIRED, row(rows, NoonDataCategory.PRODUCT_DETAIL).getHistoryStatus());
        assertEquals(NoonDataLatestStatus.INCOMPLETE, row(rows, NoonDataCategory.SALES_ORDER).getLatestStatus());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, row(rows, NoonDataCategory.SALES_ORDER).getHistoryStatus());
        assertEquals(NoonDataLatestStatus.INCOMPLETE, row(rows, NoonDataCategory.SALES_PRODUCT_VIEWS).getLatestStatus());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, row(rows, NoonDataCategory.SALES_PRODUCT_VIEWS).getHistoryStatus());
        assertEquals("STR108065-NSA", rows.get(0).getStoreCode());
        assertEquals("SA", rows.get(0).getSiteCode());
    }

    @Test
    void marksProductCategoriesIncompleteWhenProductProjectionIsMissing() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataCompletenessSeedService service = new NoonDataCompletenessSeedService(repository, FIXED_CLOCK);

        service.seedNewScope(NoonDataCompletenessSeedCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .productProjectionPresent(false)
                .build());

        List<NoonDataCompletenessRecord> rows = repository.listCompleteness(new NoonDataCompletenessQuery());
        assertEquals(NoonDataLatestStatus.INCOMPLETE, row(rows, NoonDataCategory.PRODUCT_LIST).getLatestStatus());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, row(rows, NoonDataCategory.PRODUCT_DETAIL).getHistoryStatus());
        assertEquals(1, row(rows, NoonDataCategory.PRODUCT_LIST).getActiveGapCount());
        assertEquals(1, row(rows, NoonDataCategory.PRODUCT_DETAIL).getActiveGapCount());

        List<NoonDataGapWindowRecord> gaps = repository.listGapWindows(new NoonDataGapQuery());
        assertEquals(6, gaps.size());
        assertGap(gaps, NoonDataCategory.PRODUCT_LIST, NoonDataGapWindowType.PRODUCT_BASELINE, LocalDate.parse("2026-05-25"), LocalDate.parse("2026-05-25"));
        assertGap(gaps, NoonDataCategory.PRODUCT_DETAIL, NoonDataGapWindowType.PRODUCT_DETAIL_BASELINE, LocalDate.parse("2026-05-25"), LocalDate.parse("2026-05-25"));
        assertGap(gaps, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.LATEST_DAILY, LocalDate.parse("2026-05-24"), LocalDate.parse("2026-05-24"));
        assertGap(gaps, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.HISTORY_BACKFILL, LocalDate.parse("2025-11-25"), LocalDate.parse("2026-05-24"));
    }

    @Test
    void repeatedSeedingIsIdempotentForRowsAndInitialGaps() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataCompletenessSeedService service = new NoonDataCompletenessSeedService(repository, FIXED_CLOCK);
        NoonDataCompletenessSeedCommand command = NoonDataCompletenessSeedCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .productProjectionPresent(false)
                .build();

        service.seedNewScope(command);
        service.seedNewScope(command);

        assertEquals(4, repository.listCompleteness(new NoonDataCompletenessQuery()).size());
        assertEquals(6, repository.listGapWindows(new NoonDataGapQuery()).size());
    }

    @Test
    void overviewShowsSeededRowsAsIncompleteInsteadOfAbsent() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataCompletenessSeedService service = new NoonDataCompletenessSeedService(repository, FIXED_CLOCK);
        service.seedNewScope(NoonDataCompletenessSeedCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .productProjectionPresent(false)
                .build());

        NoonDataCompletenessOverviewView overview = new NoonDataCompletenessOverviewService(repository)
                .overview(new NoonDataCompletenessQuery());

        assertEquals(4, overview.getRows().size());
        assertEquals(4, metric(overview, "total_categories"));
        assertEquals(4, metric(overview, "history_incomplete"));
        assertEquals(4, distribution(overview.getLatestStatusDistribution(), "incomplete"));
    }

    private static NoonDataCompletenessRecord row(List<NoonDataCompletenessRecord> rows, NoonDataCategory category) {
        return rows.stream()
                .filter((row) -> row.getCategory() == category)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing row for " + category));
    }

    private static void assertGap(
            List<NoonDataGapWindowRecord> gaps,
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        NoonDataGapWindowRecord gap = gaps.stream()
                .filter((item) -> item.getCategory() == category && item.getWindowType() == windowType)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing gap for " + category + "/" + windowType));
        assertEquals(dateFrom, gap.getDateFrom());
        assertEquals(dateTo, gap.getDateTo());
        assertEquals(NoonDataGapStatus.PENDING, gap.getStatus());
        assertEquals(Boolean.TRUE, gap.getRetryable());
        assertEquals(Boolean.FALSE, gap.getRequiresManualAction());
        assertNotNull(gap.getCompletenessId());
    }

    private static long metric(NoonDataCompletenessOverviewView view, String key) {
        return view.getMetrics().stream()
                .filter((metric) -> key.equals(metric.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric " + key))
                .getValue();
    }

    private static long distribution(List<NoonDataCompletenessOverviewView.DistributionItem> items, String key) {
        return items.stream()
                .filter((item) -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing distribution " + key))
                .getValue();
    }

    private static final class InMemoryNoonDataCompletenessRepository implements NoonDataCompletenessRepository {
        private final AtomicLong ids = new AtomicLong(9000);
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
                if (sameCompletenessScope(existing, record)) {
                    NoonDataCompletenessRecord copy = record.copy();
                    copy.setId(existing.getId());
                    copy.setCreatedAt(existing.getCreatedAt());
                    completeness.set(index, copy);
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
            boolean exists = gaps.stream().anyMatch((gap) -> sameGapWindow(gap, record));
            if (!exists) {
                gaps.add(record.copy());
            }
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
                            .thenComparing(NoonDataGapWindowRecord::getWindowType))
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }

        private boolean sameCompletenessScope(NoonDataCompletenessRecord left, NoonDataCompletenessRecord right) {
            return left.getOwnerUserId().equals(right.getOwnerUserId())
                    && left.getStoreCode().equals(right.getStoreCode())
                    && left.getSiteCode().equals(right.getSiteCode())
                    && left.getCategory() == right.getCategory();
        }

        private boolean sameGapWindow(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
            return left.getCompletenessId().equals(right.getCompletenessId())
                    && left.getCategory() == right.getCategory()
                    && left.getWindowType() == right.getWindowType()
                    && left.getDateFrom().equals(right.getDateFrom())
                    && left.getDateTo().equals(right.getDateTo());
        }
    }
}
