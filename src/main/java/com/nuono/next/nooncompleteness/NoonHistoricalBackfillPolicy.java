package com.nuono.next.nooncompleteness;

import com.nuono.next.noonpull.NoonSalesRetentionCapability;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoonHistoricalBackfillPolicy {
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_CORRECTION_MONTHS = 1;
    private final Clock clock;

    public NoonHistoricalBackfillPolicy() {
        this(Clock.systemUTC());
    }

    public NoonHistoricalBackfillPolicy(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Target defaultTarget(
            NoonDataCategory category,
            LocalDate anchorDate,
            NoonSalesRetentionCapability retentionCapability,
            SkuBaselineEvidence skuBaselineEvidence
    ) {
        validateHistoricalCategory(category);
        LocalDate safeAnchor = anchorDate == null ? LocalDate.now(clock) : anchorDate;
        NoonSalesRetentionCapability capability = retentionCapability == null
                ? NoonSalesRetentionCapability.defaultCapability()
                : retentionCapability;
        LocalDate requestedFrom = safeAnchor.minusMonths(DEFAULT_CORRECTION_MONTHS);
        LocalDate requestedTo = safeAnchor.minusDays(1);
        LocalDate retainedFrom = LocalDate.now(clock).minusDays(capability.getRetentionDays());
        LocalDate effectiveFrom = requestedFrom.isBefore(retainedFrom) ? retainedFrom : requestedFrom;
        return new Target(
                category,
                requestedFrom,
                requestedTo,
                effectiveFrom,
                requestedTo,
                retainedFrom,
                requestedFrom.isBefore(retainedFrom),
                skuBaselineEvidence == null ? SkuBaselineEvidence.empty() : skuBaselineEvidence
        );
    }

    public List<NoonDataGapWindowRecord> splitIntoGapWindows(
            NoonDataCompletenessRecord row,
            Target target,
            int maxDaysPerWindow
    ) {
        if (row == null || target == null) {
            return List.of();
        }
        int windowDays = Math.max(1, maxDaysPerWindow);
        List<NoonDataGapWindowRecord> windows = new ArrayList<>();
        if (target.isRetentionLimited()) {
            windows.add(gapWindow(
                    row,
                    target.getRequestedFrom(),
                    target.getEffectiveFrom().minusDays(1),
                    NoonDataGapStatus.PROVIDER_RETENTION_LIMIT,
                    "provider_retention_limit",
                    Boolean.FALSE,
                    Boolean.FALSE,
                    target.diagnosticSummary()
            ));
        }
        LocalDate cursor = target.getEffectiveFrom();
        while (!cursor.isAfter(target.getEffectiveTo())) {
            LocalDate windowTo = cursor.plusDays(windowDays - 1L);
            if (windowTo.isAfter(target.getEffectiveTo())) {
                windowTo = target.getEffectiveTo();
            }
            windows.add(gapWindow(
                    row,
                    cursor,
                    windowTo,
                    NoonDataGapStatus.PENDING,
                    null,
                    Boolean.TRUE,
                    Boolean.FALSE,
                    target.diagnosticSummary()
            ));
            cursor = windowTo.plusDays(1);
        }
        return windows;
    }

    public NoonDataCompletenessRecord applyEarliestEmptyMonthEvidence(
            NoonDataCompletenessRecord row,
            EmptyMonthEvidence evidence
    ) {
        if (row == null) {
            return null;
        }
        NoonDataCompletenessRecord updated = row.copy();
        if (evidence == null || !evidence.isConfirmedEarliestEmptyFullMonth()) {
            updated.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
            updated.setPatrolEnabled(true);
            updated.setActiveGapCount(Math.max(1, valueOrZero(updated.getActiveGapCount())));
            return updated;
        }

        boolean hadHistoricalRows = row.getHistoryCoveredFrom() != null || row.getHistoryCoveredTo() != null;
        updated.setHistoryStatus(hadHistoricalRows
                ? NoonDataHistoryStatus.COMPLETE
                : NoonDataHistoryStatus.CONFIRMED_EMPTY);
        updated.setHistoryCoveredFrom(evidence.getMonthStart());
        updated.setHistoryCoveredTo(row.getHistoryCoveredTo() == null ? evidence.getMonthEnd() : row.getHistoryCoveredTo());
        int remainingGapCount = updated.getLatestStatus() == NoonDataLatestStatus.READY ? 0 : 1;
        updated.setActiveGapCount(remainingGapCount);
        updated.setPatrolEnabled(remainingGapCount > 0);
        updated.setDiagnosticSummary(evidence.confirmedSummary());
        return updated;
    }

    private NoonDataGapWindowRecord gapWindow(
            NoonDataCompletenessRecord row,
            LocalDate dateFrom,
            LocalDate dateTo,
            NoonDataGapStatus status,
            String failureType,
            Boolean retryable,
            Boolean requiresManualAction,
            String diagnosticSummary
    ) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setCompletenessId(row.getId());
        gap.setOwnerUserId(row.getOwnerUserId());
        gap.setStoreCode(row.getStoreCode());
        gap.setSiteCode(row.getSiteCode());
        gap.setCategory(row.getCategory());
        gap.setWindowType(NoonDataGapWindowType.HISTORY_BACKFILL);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(status);
        gap.setAttempts(0);
        gap.setFailureType(failureType);
        gap.setRetryable(retryable);
        gap.setRequiresManualAction(requiresManualAction);
        gap.setDiagnosticSummary(diagnosticSummary);
        gap.setCreatedAt(LocalDate.now(clock).atStartOfDay().atOffset(ZoneOffset.UTC).toLocalDateTime());
        gap.setUpdatedAt(gap.getCreatedAt());
        return gap;
    }

    private void validateHistoricalCategory(NoonDataCategory category) {
        if (category != NoonDataCategory.SALES_ORDER && category != NoonDataCategory.SALES_PRODUCT_VIEWS) {
            throw new IllegalArgumentException("历史回填仅适用于 SALES_ORDER 和 SALES_PRODUCT_VIEWS。");
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    public static class Target {
        private final NoonDataCategory category;
        private final LocalDate requestedFrom;
        private final LocalDate requestedTo;
        private final LocalDate effectiveFrom;
        private final LocalDate effectiveTo;
        private final LocalDate retainedFrom;
        private final boolean retentionLimited;
        private final SkuBaselineEvidence skuBaselineEvidence;

        private Target(
                NoonDataCategory category,
                LocalDate requestedFrom,
                LocalDate requestedTo,
                LocalDate effectiveFrom,
                LocalDate effectiveTo,
                LocalDate retainedFrom,
                boolean retentionLimited,
                SkuBaselineEvidence skuBaselineEvidence
        ) {
            this.category = category;
            this.requestedFrom = requestedFrom;
            this.requestedTo = requestedTo;
            this.effectiveFrom = effectiveFrom;
            this.effectiveTo = effectiveTo;
            this.retainedFrom = retainedFrom;
            this.retentionLimited = retentionLimited;
            this.skuBaselineEvidence = skuBaselineEvidence;
        }

        private String diagnosticSummary() {
            return "history target "
                    + requestedFrom
                    + ".."
                    + requestedTo
                    + ", effective "
                    + effectiveFrom
                    + ".."
                    + effectiveTo
                    + ", "
                    + skuBaselineEvidence.summary();
        }

        public NoonDataCategory getCategory() {
            return category;
        }

        public LocalDate getRequestedFrom() {
            return requestedFrom;
        }

        public LocalDate getRequestedTo() {
            return requestedTo;
        }

        public LocalDate getEffectiveFrom() {
            return effectiveFrom;
        }

        public LocalDate getEffectiveTo() {
            return effectiveTo;
        }

        public LocalDate getRetainedFrom() {
            return retainedFrom;
        }

        public boolean isRetentionLimited() {
            return retentionLimited;
        }

        public SkuBaselineEvidence getSkuBaselineEvidence() {
            return skuBaselineEvidence;
        }
    }

    public static class SkuBaselineEvidence {
        private final int productCount;
        private final int offerCount;
        private final int inventorySkuCount;
        private final LocalDate evidenceDate;

        private SkuBaselineEvidence(int productCount, int offerCount, int inventorySkuCount, LocalDate evidenceDate) {
            this.productCount = Math.max(productCount, 0);
            this.offerCount = Math.max(offerCount, 0);
            this.inventorySkuCount = Math.max(inventorySkuCount, 0);
            this.evidenceDate = evidenceDate;
        }

        public static SkuBaselineEvidence of(
                int productCount,
                int offerCount,
                int inventorySkuCount,
                LocalDate evidenceDate
        ) {
            return new SkuBaselineEvidence(productCount, offerCount, inventorySkuCount, evidenceDate);
        }

        public static SkuBaselineEvidence empty() {
            return new SkuBaselineEvidence(0, 0, 0, null);
        }

        private boolean hasSkuReference() {
            return productCount > 0 || offerCount > 0 || inventorySkuCount > 0;
        }

        private String summary() {
            return "SKU baseline products="
                    + productCount
                    + ", offers="
                    + offerCount
                    + ", inventorySkus="
                    + inventorySkuCount
                    + (evidenceDate == null ? "" : ", evidenceDate=" + evidenceDate);
        }
    }

    public static class EmptyMonthEvidence {
        private final LocalDate monthStart;
        private final int productCount;
        private final int offerCount;
        private final int inventorySkuCount;
        private final int salesOrderRows;
        private final int productViewsRows;
        private final boolean fullMonth;

        private EmptyMonthEvidence(
                LocalDate monthStart,
                int productCount,
                int offerCount,
                int inventorySkuCount,
                int salesOrderRows,
                int productViewsRows,
                boolean fullMonth
        ) {
            this.monthStart = monthStart == null ? null : monthStart.withDayOfMonth(1);
            this.productCount = Math.max(productCount, 0);
            this.offerCount = Math.max(offerCount, 0);
            this.inventorySkuCount = Math.max(inventorySkuCount, 0);
            this.salesOrderRows = Math.max(salesOrderRows, 0);
            this.productViewsRows = Math.max(productViewsRows, 0);
            this.fullMonth = fullMonth;
        }

        public static EmptyMonthEvidence confirmed(
                LocalDate monthStart,
                int productCount,
                int offerCount,
                int inventorySkuCount,
                int salesOrderRows,
                int productViewsRows
        ) {
            return new EmptyMonthEvidence(
                    monthStart,
                    productCount,
                    offerCount,
                    inventorySkuCount,
                    salesOrderRows,
                    productViewsRows,
                    true
            );
        }

        public static EmptyMonthEvidence observed(
                LocalDate monthStart,
                int productCount,
                int offerCount,
                int inventorySkuCount,
                int salesOrderRows,
                int productViewsRows
        ) {
            return new EmptyMonthEvidence(
                    monthStart,
                    productCount,
                    offerCount,
                    inventorySkuCount,
                    salesOrderRows,
                    productViewsRows,
                    true
            );
        }

        private boolean isConfirmedEarliestEmptyFullMonth() {
            return monthStart != null
                    && fullMonth
                    && (productCount > 0 || offerCount > 0 || inventorySkuCount > 0)
                    && salesOrderRows == 0
                    && productViewsRows == 0;
        }

        private String confirmedSummary() {
            return "confirmed earliest empty month "
                    + MONTH_FORMAT.format(monthStart)
                    + ", SKU baseline products="
                    + productCount
                    + ", offers="
                    + offerCount
                    + ", inventorySkus="
                    + inventorySkuCount
                    + ", salesOrderRows="
                    + salesOrderRows
                    + ", productViewsRows="
                    + productViewsRows;
        }

        public LocalDate getMonthStart() {
            return monthStart;
        }

        public LocalDate getMonthEnd() {
            return monthStart == null ? null : monthStart.plusMonths(1).minusDays(1);
        }
    }
}
