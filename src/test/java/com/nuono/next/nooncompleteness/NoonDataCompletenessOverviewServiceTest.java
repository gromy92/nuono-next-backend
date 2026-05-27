package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonDataCompletenessOverviewServiceTest {

    @Test
    void overviewShouldReturnSummaryDistributionsAndRows() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataCompletenessRecord product = record(
                1L,
                NoonDataCategory.PRODUCT_LIST,
                NoonDataLatestStatus.READY,
                NoonDataHistoryStatus.NOT_REQUIRED
        );
        product.setLatestDataDate(LocalDate.of(2026, 5, 24));
        repository.insertCompleteness(product);
        NoonDataCompletenessRecord sales = record(
                2L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataLatestStatus.PENDING_CONFIRMATION,
                NoonDataHistoryStatus.INCOMPLETE
        );
        sales.setHistoryCoveredFrom(LocalDate.of(2026, 1, 1));
        sales.setHistoryCoveredTo(LocalDate.of(2026, 5, 24));
        repository.insertCompleteness(sales);

        NoonDataCompletenessOverviewService service = new NoonDataCompletenessOverviewService(repository);

        NoonDataCompletenessOverviewView view = service.overview(new NoonDataCompletenessQuery());

        assertEquals(2, view.getRows().size());
        assertEquals(2, metric(view, "total_categories"));
        assertEquals(1, metric(view, "latest_ready"));
        assertEquals(1, metric(view, "history_incomplete"));
        assertEquals(1, distribution(view.getLatestStatusDistribution(), "pending_confirmation"));
        assertEquals(1, distribution(view.getCategoryDistribution(), "SALES_PRODUCT_VIEWS"));
        assertEquals(LocalDate.of(2026, 5, 24), view.getRows().get(0).getLatestDataDate());
    }

    @Test
    void overviewShouldApplyCategoryAndStatusFilters() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        repository.insertCompleteness(record(
                1L,
                NoonDataCategory.PRODUCT_LIST,
                NoonDataLatestStatus.READY,
                NoonDataHistoryStatus.NOT_REQUIRED
        ));
        repository.insertCompleteness(record(
                2L,
                NoonDataCategory.SALES_ORDER,
                NoonDataLatestStatus.INCOMPLETE,
                NoonDataHistoryStatus.INCOMPLETE
        ));
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setCategory(NoonDataCategory.SALES_ORDER);
        query.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);

        NoonDataCompletenessOverviewView view = new NoonDataCompletenessOverviewService(repository).overview(query);

        assertEquals(1, view.getRows().size());
        assertEquals(NoonDataCategory.SALES_ORDER, view.getRows().get(0).getCategory());
    }

    private NoonDataCompletenessRecord record(
            Long id,
            NoonDataCategory category,
            NoonDataLatestStatus latestStatus,
            NoonDataHistoryStatus historyStatus
    ) {
        NoonDataCompletenessRecord record = new NoonDataCompletenessRecord();
        record.setId(id);
        record.setOwnerUserId(307L);
        record.setStoreCode("STR245027-NAE");
        record.setSiteCode("AE");
        record.setCategory(category);
        record.setLatestStatus(latestStatus);
        record.setHistoryStatus(historyStatus);
        record.setPatrolEnabled(true);
        record.setCreatedAt(LocalDateTime.of(2026, 5, 25, 9, 0));
        record.setUpdatedAt(LocalDateTime.of(2026, 5, 25, 9, 0));
        return record;
    }

    private long metric(NoonDataCompletenessOverviewView view, String key) {
        return view.getMetrics().stream()
                .filter((metric) -> key.equals(metric.getKey()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private long distribution(List<NoonDataCompletenessOverviewView.DistributionItem> items, String key) {
        return items.stream()
                .filter((item) -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    private static final class InMemoryNoonDataCompletenessRepository implements NoonDataCompletenessRepository {
        private final List<NoonDataCompletenessRecord> completeness = new ArrayList<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return initialValue + completeness.size();
        }

        @Override
        public void insertCompleteness(NoonDataCompletenessRecord record) {
            completeness.add(record.copy());
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            return completeness.stream()
                    .filter((record) -> query == null || query.getCategory() == null || record.getCategory() == query.getCategory())
                    .filter((record) -> query == null || query.getLatestStatus() == null || record.getLatestStatus() == query.getLatestStatus())
                    .map(NoonDataCompletenessRecord::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public void insertGapWindow(NoonDataGapWindowRecord record) {
            throw new UnsupportedOperationException("not needed for overview tests");
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            return List.of();
        }
    }
}
