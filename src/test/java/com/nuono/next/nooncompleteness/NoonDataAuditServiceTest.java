package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

class NoonDataAuditServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void marksProductListReadyWhenOfferBaselineExists() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(3, 3, 3),
                FIXED_CLOCK
        );

        service.auditProductCompleteness(command());

        NoonDataCompletenessRecord productList = row(repository, NoonDataCategory.PRODUCT_LIST);
        assertEquals(NoonDataLatestStatus.READY, productList.getLatestStatus());
        assertEquals(NoonDataHistoryStatus.NOT_REQUIRED, productList.getHistoryStatus());
        assertEquals(0, productList.getActiveGapCount());
        assertEquals(LocalDate.parse("2026-05-25"), productList.getLatestDataDate());
        assertEquals(0, gaps(repository, NoonDataCategory.PRODUCT_LIST).size());
    }

    @Test
    void marksProductListIncompleteAndCreatesGapWhenOfferBaselineIsMissing() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(0, 0, 0),
                FIXED_CLOCK
        );

        service.auditProductCompleteness(command());

        NoonDataCompletenessRecord productList = row(repository, NoonDataCategory.PRODUCT_LIST);
        assertEquals(NoonDataLatestStatus.INCOMPLETE, productList.getLatestStatus());
        assertEquals(1, productList.getActiveGapCount());
        NoonDataGapWindowRecord gap = gaps(repository, NoonDataCategory.PRODUCT_LIST).get(0);
        assertEquals(NoonDataGapWindowType.PRODUCT_BASELINE, gap.getWindowType());
        assertEquals(NoonDataGapStatus.PENDING, gap.getStatus());
        assertEquals(LocalDate.parse("2026-05-25"), gap.getDateFrom());
        assertEquals(LocalDate.parse("2026-05-25"), gap.getDateTo());
    }

    @Test
    void marksProductDetailReadyIncompleteAndPartialFromBaselineCoverage() {
        InMemoryNoonDataCompletenessRepository completeRepository = new InMemoryNoonDataCompletenessRepository();
        new NoonDataAuditService(completeRepository, (command) -> new NoonProductCompletenessAudit(4, 4, 4), FIXED_CLOCK)
                .auditProductCompleteness(command());
        assertEquals(NoonDataLatestStatus.READY, row(completeRepository, NoonDataCategory.PRODUCT_DETAIL).getLatestStatus());
        assertEquals(0, gaps(completeRepository, NoonDataCategory.PRODUCT_DETAIL).size());

        InMemoryNoonDataCompletenessRepository partialRepository = new InMemoryNoonDataCompletenessRepository();
        new NoonDataAuditService(partialRepository, (command) -> new NoonProductCompletenessAudit(4, 4, 2), FIXED_CLOCK)
                .auditProductCompleteness(command());
        NoonDataCompletenessRecord partial = row(partialRepository, NoonDataCategory.PRODUCT_DETAIL);
        assertEquals(NoonDataLatestStatus.INCOMPLETE, partial.getLatestStatus());
        assertTrue(partial.getDiagnosticSummary().contains("2/4"));
        assertEquals(1, gaps(partialRepository, NoonDataCategory.PRODUCT_DETAIL).size());

        InMemoryNoonDataCompletenessRepository missingRepository = new InMemoryNoonDataCompletenessRepository();
        new NoonDataAuditService(missingRepository, (command) -> new NoonProductCompletenessAudit(4, 4, 0), FIXED_CLOCK)
                .auditProductCompleteness(command());
        NoonDataCompletenessRecord missing = row(missingRepository, NoonDataCategory.PRODUCT_DETAIL);
        assertEquals(NoonDataLatestStatus.INCOMPLETE, missing.getLatestStatus());
        assertTrue(missing.getDiagnosticSummary().contains("0/4"));
        assertEquals(1, gaps(missingRepository, NoonDataCategory.PRODUCT_DETAIL).size());
    }

    @Test
    void repeatedProductAuditIsIdempotentForRowsAndGaps() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(4, 0, 2),
                FIXED_CLOCK
        );

        service.auditProductCompleteness(command());
        service.auditProductCompleteness(command());

        assertEquals(2, repository.listCompleteness(new NoonDataCompletenessQuery()).size());
        assertEquals(2, repository.listGapWindows(new NoonDataGapQuery()).size());
    }

    @Test
    void auditsSalesProductViewsFactsIntoLatestAndHistoryCoverage() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> NoonProductCompletenessAudit.empty(),
                (command) -> NoonSalesProductViewsCompletenessAudit.factsPresent(
                        LocalDate.parse("2026-05-24"),
                        LocalDate.parse("2026-05-01"),
                        LocalDate.parse("2026-05-24"),
                        12
                ),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );

        service.auditSalesCompleteness(command());

        NoonDataCompletenessRecord sales = row(repository, NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataLatestStatus.READY, sales.getLatestStatus());
        assertEquals(NoonDataHistoryStatus.COMPLETE, sales.getHistoryStatus());
        assertEquals(LocalDate.parse("2026-05-24"), sales.getLatestDataDate());
        assertEquals(LocalDate.parse("2026-05-01"), sales.getHistoryCoveredFrom());
        assertEquals(LocalDate.parse("2026-05-24"), sales.getHistoryCoveredTo());
        assertEquals(0, gaps(repository, NoonDataCategory.SALES_PRODUCT_VIEWS).size());
    }

    @Test
    void missingSalesProductViewsFactsCreateExplicitLatestAndHistoryGaps() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(12, 10, 9),
                (command) -> NoonSalesProductViewsCompletenessAudit.missing(),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );

        service.auditSalesCompleteness(command());

        NoonDataCompletenessRecord sales = row(repository, NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataLatestStatus.INCOMPLETE, sales.getLatestStatus());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, sales.getHistoryStatus());
        assertEquals(2, sales.getActiveGapCount());
        List<NoonDataGapWindowRecord> gaps = gaps(repository, NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(2, gaps.size());
        assertEquals(NoonDataGapWindowType.LATEST_DAILY, gaps.get(0).getWindowType());
        assertEquals(NoonDataGapWindowType.HISTORY_BACKFILL, gaps.get(1).getWindowType());
        assertEquals(LocalDate.parse("2026-04-25"), gaps.get(1).getDateFrom());
        assertEquals(LocalDate.parse("2026-05-24"), gaps.get(1).getDateTo());
        assertEquals(NoonDataGapStatus.PENDING, gaps.get(1).getStatus());
        assertEquals(Boolean.TRUE, gaps.get(1).getRetryable());
        assertTrue(gaps.get(1).getDiagnosticSummary().contains("products=12"));
        assertTrue(gaps.get(1).getDiagnosticSummary().contains("offers=10"));
        assertTrue(gaps.get(1).getDiagnosticSummary().contains("inventorySkus=9"));
    }

    @Test
    void reAuditDropsHistoricalGapsOlderThanOneMonthCorrectionWindow() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataCompletenessRecord existing = completenessRow(NoonDataCategory.SALES_PRODUCT_VIEWS);
        existing.setId(10001L);
        repository.insertCompleteness(existing);
        repository.insertGapWindow(gap(
                10001L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataGapWindowType.HISTORY_BACKFILL,
                LocalDate.parse("2025-11-25"),
                LocalDate.parse("2026-02-23"),
                NoonDataGapStatus.PROVIDER_RETENTION_LIMIT
        ));
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(12, 10, 9),
                (command) -> NoonSalesProductViewsCompletenessAudit.missing(),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );

        service.auditSalesCompleteness(command());

        List<NoonDataGapWindowRecord> gaps = gaps(repository, NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(2, gaps.size());
        assertTrue(gaps.stream().noneMatch((gap) -> gap.getDateTo().isBefore(LocalDate.parse("2026-04-25"))));
        assertTrue(gaps.stream().anyMatch((gap) ->
                gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL
                        && LocalDate.parse("2026-04-25").equals(gap.getDateFrom())
                        && LocalDate.parse("2026-05-24").equals(gap.getDateTo())));
    }

    @Test
    void pendingSalesProductViewsConfirmationCreatesPendingGapInsteadOfConfirmedZero() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> NoonProductCompletenessAudit.empty(),
                (command) -> NoonSalesProductViewsCompletenessAudit.pendingEmptyConfirmation(
                        LocalDate.parse("2026-05-24"),
                        "empty_report_pending_confirmation"
                ),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );

        service.auditSalesCompleteness(command());

        NoonDataCompletenessRecord sales = row(repository, NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals(NoonDataLatestStatus.PENDING_CONFIRMATION, sales.getLatestStatus());
        NoonDataGapWindowRecord gap = gaps(repository, NoonDataCategory.SALES_PRODUCT_VIEWS).get(0);
        assertEquals(NoonDataGapStatus.PENDING_CONFIRMATION, gap.getStatus());
        assertEquals("empty_report_pending_confirmation", gap.getFailureType());
    }

    @Test
    void auditsOrderFactsAndNotIntegratedOrderDomainWithoutPretendingEmptyOrders() {
        InMemoryNoonDataCompletenessRepository factsRepository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService factsService = new NoonDataAuditService(
                factsRepository,
                (command) -> NoonProductCompletenessAudit.empty(),
                (command) -> NoonSalesProductViewsCompletenessAudit.missing(),
                (command) -> NoonSalesOrderCompletenessAudit.factsPresent(
                        LocalDate.parse("2026-05-23"),
                        LocalDate.parse("2026-05-01"),
                        LocalDate.parse("2026-05-23"),
                        8
                ),
                FIXED_CLOCK
        );
        factsService.auditSalesCompleteness(command());
        assertEquals(NoonDataLatestStatus.READY, row(factsRepository, NoonDataCategory.SALES_ORDER).getLatestStatus());
        assertEquals(NoonDataHistoryStatus.COMPLETE, row(factsRepository, NoonDataCategory.SALES_ORDER).getHistoryStatus());

        InMemoryNoonDataCompletenessRepository notIntegratedRepository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService notIntegratedService = new NoonDataAuditService(
                notIntegratedRepository,
                (command) -> NoonProductCompletenessAudit.empty(),
                (command) -> NoonSalesProductViewsCompletenessAudit.missing(),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("provider_not_configured"),
                FIXED_CLOCK
        );
        notIntegratedService.auditSalesCompleteness(command());
        assertEquals(NoonDataLatestStatus.NOT_INTEGRATED, row(notIntegratedRepository, NoonDataCategory.SALES_ORDER).getLatestStatus());
        assertEquals(0, gaps(notIntegratedRepository, NoonDataCategory.SALES_ORDER).size());
    }

    @Test
    void keepsSalesFailureTypesDistinctInGapWindows() {
        InMemoryNoonDataCompletenessRepository missingColumnsRepository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService missingColumnsService = new NoonDataAuditService(
                missingColumnsRepository,
                (command) -> NoonProductCompletenessAudit.empty(),
                (command) -> NoonSalesProductViewsCompletenessAudit.failed("missing_columns"),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );
        missingColumnsService.auditSalesCompleteness(command());
        NoonDataGapWindowRecord missingColumnsGap = gaps(missingColumnsRepository, NoonDataCategory.SALES_PRODUCT_VIEWS).get(0);
        assertEquals(NoonDataGapStatus.FAILED, missingColumnsGap.getStatus());
        assertEquals(Boolean.FALSE, missingColumnsGap.getRetryable());
        assertEquals(Boolean.TRUE, missingColumnsGap.getRequiresManualAction());
        assertEquals("missing_columns", missingColumnsGap.getFailureType());

        InMemoryNoonDataCompletenessRepository retentionRepository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService retentionService = new NoonDataAuditService(
                retentionRepository,
                (command) -> NoonProductCompletenessAudit.empty(),
                (command) -> NoonSalesProductViewsCompletenessAudit.failed("provider_retention_limit"),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                FIXED_CLOCK
        );
        retentionService.auditSalesCompleteness(command());
        NoonDataGapWindowRecord retentionGap = gaps(retentionRepository, NoonDataCategory.SALES_PRODUCT_VIEWS).get(0);
        assertEquals(NoonDataGapStatus.PROVIDER_RETENTION_LIMIT, retentionGap.getStatus());
        assertEquals(Boolean.FALSE, retentionGap.getRetryable());
        assertEquals(Boolean.FALSE, retentionGap.getRequiresManualAction());
        assertEquals("provider_retention_limit", retentionGap.getFailureType());
    }

    @Test
    void missingIntegratedOrderFactsCreateSplitHistoryBackfillWindows() {
        InMemoryNoonDataCompletenessRepository repository = new InMemoryNoonDataCompletenessRepository();
        NoonDataAuditService service = new NoonDataAuditService(
                repository,
                (command) -> new NoonProductCompletenessAudit(12, 10, 9),
                (command) -> NoonSalesProductViewsCompletenessAudit.factsPresent(
                        LocalDate.parse("2026-05-24"),
                        LocalDate.parse("2026-02-24"),
                        LocalDate.parse("2026-05-24"),
                        12
                ),
                (command) -> NoonSalesOrderCompletenessAudit.missing(),
                FIXED_CLOCK
        );

        service.auditSalesCompleteness(command());

        NoonDataCompletenessRecord order = row(repository, NoonDataCategory.SALES_ORDER);
        assertEquals(NoonDataLatestStatus.INCOMPLETE, order.getLatestStatus());
        assertEquals(NoonDataHistoryStatus.INCOMPLETE, order.getHistoryStatus());
        List<NoonDataGapWindowRecord> gaps = gaps(repository, NoonDataCategory.SALES_ORDER);
        assertEquals(2, gaps.size());
        assertEquals(NoonDataGapWindowType.LATEST_DAILY, gaps.get(0).getWindowType());
        assertEquals(NoonDataGapStatus.PENDING, gaps.get(1).getStatus());
        assertEquals(LocalDate.parse("2026-04-25"), gaps.get(1).getDateFrom());
        assertEquals(LocalDate.parse("2026-05-24"), gaps.get(1).getDateTo());
        assertTrue(gaps.get(1).getDiagnosticSummary().contains("products=12"));
    }

    private static NoonDataAuditCommand command() {
        return NoonDataAuditCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .auditDate(LocalDate.parse("2026-05-25"))
                .build();
    }

    private static NoonDataCompletenessRecord row(
            InMemoryNoonDataCompletenessRepository repository,
            NoonDataCategory category
    ) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setCategory(category);
        return repository.listCompleteness(query).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing row for " + category));
    }

    private static List<NoonDataGapWindowRecord> gaps(
            InMemoryNoonDataCompletenessRepository repository,
            NoonDataCategory category
    ) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setCategory(category);
        return repository.listGapWindows(query);
    }

    private static NoonDataCompletenessRecord completenessRow(NoonDataCategory category) {
        NoonDataCompletenessRecord row = new NoonDataCompletenessRecord();
        row.setOwnerUserId(307L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setCategory(category);
        row.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(2);
        row.setCreatedAt(LocalDateTime.parse("2026-05-25T03:00:00"));
        row.setUpdatedAt(LocalDateTime.parse("2026-05-25T03:00:00"));
        return row;
    }

    private static NoonDataGapWindowRecord gap(
            Long completenessId,
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            LocalDate dateFrom,
            LocalDate dateTo,
            NoonDataGapStatus status
    ) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(20001L);
        gap.setCompletenessId(completenessId);
        gap.setOwnerUserId(307L);
        gap.setStoreCode("STR108065-NSA");
        gap.setSiteCode("SA");
        gap.setCategory(category);
        gap.setWindowType(windowType);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(status);
        gap.setAttempts(0);
        gap.setFailureType(status == NoonDataGapStatus.PROVIDER_RETENTION_LIMIT ? "provider_retention_limit" : null);
        gap.setRetryable(status != NoonDataGapStatus.PROVIDER_RETENTION_LIMIT);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setCreatedAt(LocalDateTime.parse("2026-05-25T03:00:00"));
        gap.setUpdatedAt(LocalDateTime.parse("2026-05-25T03:00:00"));
        return gap;
    }

    private static final class InMemoryNoonDataCompletenessRepository implements NoonDataCompletenessRepository {
        private final AtomicLong ids = new AtomicLong(10000);
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
            for (int index = 0; index < gaps.size(); index++) {
                NoonDataGapWindowRecord existing = gaps.get(index);
                if (sameGapWindow(existing, record)) {
                    NoonDataGapWindowRecord copy = record.copy();
                    copy.setId(existing.getId());
                    copy.setCreatedAt(existing.getCreatedAt());
                    gaps.set(index, copy);
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
                            .thenComparing(NoonDataGapWindowRecord::getWindowType))
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public void deleteHistoryBackfillGapsOutsideRange(
                Long completenessId,
                NoonDataCategory category,
                LocalDate dateFrom,
                LocalDate dateTo,
                LocalDateTime updatedAt
        ) {
            gaps.removeIf((gap) ->
                    Objects.equals(completenessId, gap.getCompletenessId())
                            && gap.getCategory() == category
                            && gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL
                            && (gap.getDateFrom().isBefore(dateFrom) || gap.getDateTo().isAfter(dateTo)));
        }

        private boolean sameCompletenessScope(NoonDataCompletenessRecord left, NoonDataCompletenessRecord right) {
            return left.getOwnerUserId().equals(right.getOwnerUserId())
                    && left.getStoreCode().equals(right.getStoreCode())
                    && left.getSiteCode().equals(right.getSiteCode())
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
