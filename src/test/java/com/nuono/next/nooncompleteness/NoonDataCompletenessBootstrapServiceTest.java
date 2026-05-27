package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonDataCompletenessBootstrapServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void auditsExistingScopesAndKeepsRepeatedBootstrapIdempotent() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService auditService = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(2, 2, 1),
                (command) -> NoonSalesProductViewsCompletenessAudit.factsPresent(
                        LocalDate.parse("2026-05-24"),
                        LocalDate.parse("2026-05-01"),
                        LocalDate.parse("2026-05-24"),
                        18
                ),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );
        NoonDataCompletenessBootstrapService bootstrapService = new NoonDataCompletenessBootstrapService(
                () -> List.of(
                        new NoonDataCompletenessAuditScope(307L, "STR108065-NAE", "AE"),
                        new NoonDataCompletenessAuditScope(307L, "STR108065-NSA", "SA")
                ),
                auditService,
                FIXED_CLOCK
        );

        NoonDataCompletenessBootstrapResult firstRun = bootstrapService.auditExistingScopes();
        NoonDataCompletenessBootstrapResult secondRun = bootstrapService.auditExistingScopes();

        assertEquals(2, firstRun.getScopeCount());
        assertEquals(8, firstRun.getCompletenessCategoryCount());
        assertEquals(2, secondRun.getScopeCount());
        assertEquals(8, repository.listCompleteness(new NoonDataCompletenessQuery()).size());
        assertEquals(2, repository.listGapWindows(new NoonDataGapQuery()).size());
        assertEquals(1, gaps(repository, "STR108065-NAE", NoonDataCategory.PRODUCT_DETAIL).size());
        assertEquals(0, gaps(repository, "STR108065-NAE", NoonDataCategory.SALES_PRODUCT_VIEWS).size());
        assertEquals(NoonDataLatestStatus.NOT_INTEGRATED, row(repository, "STR108065-NSA", NoonDataCategory.SALES_ORDER).getLatestStatus());
    }

    private static NoonDataCompletenessRecord row(
            InMemoryNoonDataCompletenessRepository repository,
            String storeCode,
            NoonDataCategory category
    ) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setStoreCode(storeCode);
        query.setCategory(category);
        return repository.listCompleteness(query).get(0);
    }

    private static List<NoonDataGapWindowRecord> gaps(
            InMemoryNoonDataCompletenessRepository repository,
            String storeCode,
            NoonDataCategory category
    ) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setStoreCode(storeCode);
        query.setCategory(category);
        return repository.listGapWindows(query);
    }

    private static final class InMemoryNoonDataCompletenessRepository implements NoonDataCompletenessRepository {
        private final AtomicLong completenessId = new AtomicLong(900000L);
        private final AtomicLong gapId = new AtomicLong(910000L);
        private final List<NoonDataCompletenessRecord> completeness = new ArrayList<>();
        private final List<NoonDataGapWindowRecord> gaps = new ArrayList<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            if ("noon_data_gap_window".equals(sequenceName)) {
                return gapId.incrementAndGet();
            }
            return completenessId.incrementAndGet();
        }

        @Override
        public void insertCompleteness(NoonDataCompletenessRecord record) {
            int index = -1;
            for (int i = 0; i < completeness.size(); i++) {
                if (sameCompletenessScope(completeness.get(i), record)) {
                    index = i;
                    break;
                }
            }
            NoonDataCompletenessRecord copy = record.copy();
            if (index >= 0) {
                copy.setId(completeness.get(index).getId());
                completeness.set(index, copy);
            } else {
                completeness.add(copy);
            }
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            NoonDataCompletenessQuery safeQuery = query == null ? new NoonDataCompletenessQuery() : query;
            return completeness.stream()
                    .filter((row) -> safeQuery.getOwnerUserId() == null || Objects.equals(row.getOwnerUserId(), safeQuery.getOwnerUserId()))
                    .filter((row) -> safeQuery.getStoreCode() == null || Objects.equals(row.getStoreCode(), safeQuery.getStoreCode()))
                    .filter((row) -> safeQuery.getSiteCode() == null || Objects.equals(row.getSiteCode(), safeQuery.getSiteCode()))
                    .filter((row) -> safeQuery.getCategory() == null || row.getCategory() == safeQuery.getCategory())
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
            int index = -1;
            for (int i = 0; i < gaps.size(); i++) {
                if (sameGapWindow(gaps.get(i), record)) {
                    index = i;
                    break;
                }
            }
            NoonDataGapWindowRecord copy = record.copy();
            if (index >= 0) {
                copy.setId(gaps.get(index).getId());
                gaps.set(index, copy);
            } else {
                gaps.add(copy);
            }
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            NoonDataGapQuery safeQuery = query == null ? new NoonDataGapQuery() : query;
            return gaps.stream()
                    .filter((gap) -> safeQuery.getOwnerUserId() == null || Objects.equals(gap.getOwnerUserId(), safeQuery.getOwnerUserId()))
                    .filter((gap) -> safeQuery.getStoreCode() == null || Objects.equals(gap.getStoreCode(), safeQuery.getStoreCode()))
                    .filter((gap) -> safeQuery.getSiteCode() == null || Objects.equals(gap.getSiteCode(), safeQuery.getSiteCode()))
                    .filter((gap) -> safeQuery.getCategory() == null || gap.getCategory() == safeQuery.getCategory())
                    .filter((gap) -> safeQuery.getStatus() == null || gap.getStatus() == safeQuery.getStatus())
                    .filter((gap) -> safeQuery.getFailureType() == null || Objects.equals(gap.getFailureType(), safeQuery.getFailureType()))
                    .filter((gap) -> safeQuery.getRetryable() == null || Objects.equals(gap.getRetryable(), safeQuery.getRetryable()))
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
            return Objects.equals(left.getOwnerUserId(), right.getOwnerUserId())
                    && Objects.equals(left.getStoreCode(), right.getStoreCode())
                    && Objects.equals(left.getSiteCode(), right.getSiteCode())
                    && left.getCategory() == right.getCategory();
        }

        private boolean sameGapWindow(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
            return Objects.equals(left.getCompletenessId(), right.getCompletenessId())
                    && left.getCategory() == right.getCategory()
                    && left.getWindowType() == right.getWindowType()
                    && Objects.equals(left.getDateFrom(), right.getDateFrom())
                    && Objects.equals(left.getDateTo(), right.getDateTo());
        }
    }
}
