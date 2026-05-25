package com.nuono.next.nooncompleteness;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NoonDataAuditService {
    private static final int DEFAULT_HISTORY_GAP_WINDOW_DAYS = 31;
    private static final int DEFAULT_HISTORY_CORRECTION_MONTHS = 1;

    private final NoonDataCompletenessRepository repository;
    private final NoonProductCompletenessAuditSource productAuditSource;
    private final NoonSalesProductViewsCompletenessAuditSource salesProductViewsAuditSource;
    private final NoonSalesOrderCompletenessAuditSource salesOrderAuditSource;
    private final NoonHistoricalBackfillPolicy historicalBackfillPolicy;
    private final Clock clock;

    @Autowired
    public NoonDataAuditService(
            NoonDataCompletenessRepository repository,
            NoonProductCompletenessAuditSource productAuditSource,
            NoonSalesProductViewsCompletenessAuditSource salesProductViewsAuditSource,
            NoonSalesOrderCompletenessAuditSource salesOrderAuditSource
    ) {
        this(
                repository,
                productAuditSource,
                salesProductViewsAuditSource,
                salesOrderAuditSource,
                Clock.systemUTC(),
                new NoonHistoricalBackfillPolicy()
        );
    }

    public NoonDataAuditService(
            NoonDataCompletenessRepository repository,
            NoonProductCompletenessAuditSource productAuditSource,
            Clock clock
    ) {
        this(
                repository,
                productAuditSource,
                (command) -> NoonSalesProductViewsCompletenessAudit.missing(),
                (command) -> NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated"),
                clock,
                new NoonHistoricalBackfillPolicy(clock)
        );
    }

    public NoonDataAuditService(
            NoonDataCompletenessRepository repository,
            NoonProductCompletenessAuditSource productAuditSource,
            NoonSalesProductViewsCompletenessAuditSource salesProductViewsAuditSource,
            NoonSalesOrderCompletenessAuditSource salesOrderAuditSource,
            Clock clock
    ) {
        this(
                repository,
                productAuditSource,
                salesProductViewsAuditSource,
                salesOrderAuditSource,
                clock,
                new NoonHistoricalBackfillPolicy(clock)
        );
    }

    public NoonDataAuditService(
            NoonDataCompletenessRepository repository,
            NoonProductCompletenessAuditSource productAuditSource,
            NoonSalesProductViewsCompletenessAuditSource salesProductViewsAuditSource,
            NoonSalesOrderCompletenessAuditSource salesOrderAuditSource,
            Clock clock,
            NoonHistoricalBackfillPolicy historicalBackfillPolicy
    ) {
        this.repository = repository;
        this.productAuditSource = productAuditSource;
        this.salesProductViewsAuditSource = salesProductViewsAuditSource;
        this.salesOrderAuditSource = salesOrderAuditSource;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.historicalBackfillPolicy = historicalBackfillPolicy == null
                ? new NoonHistoricalBackfillPolicy(this.clock)
                : historicalBackfillPolicy;
    }

    public void auditProductCompleteness(NoonDataAuditCommand command) {
        AuditScope scope = scope(command);
        LocalDate auditDate = command.getAuditDate() == null ? LocalDate.now(clock) : command.getAuditDate();
        NoonProductCompletenessAudit audit = productAuditSource.audit(command);
        if (audit == null) {
            audit = NoonProductCompletenessAudit.empty();
        }

        ProductDecision productList = productListDecision(audit);
        NoonDataCompletenessRecord productListRow = upsertCompleteness(
                scope,
                NoonDataCategory.PRODUCT_LIST,
                productList,
                auditDate
        );
        if (productList.activeGapCount > 0) {
            upsertGap(productListRow, NoonDataGapWindowType.PRODUCT_BASELINE, auditDate, productList.diagnosticSummary);
        }

        ProductDecision productDetail = productDetailDecision(audit);
        NoonDataCompletenessRecord productDetailRow = upsertCompleteness(
                scope,
                NoonDataCategory.PRODUCT_DETAIL,
                productDetail,
                auditDate
        );
        if (productDetail.activeGapCount > 0) {
            upsertGap(productDetailRow, NoonDataGapWindowType.PRODUCT_DETAIL_BASELINE, auditDate, productDetail.diagnosticSummary);
        }
    }

    public void auditSalesCompleteness(NoonDataAuditCommand command) {
        AuditScope scope = scope(command);
        LocalDate auditDate = command.getAuditDate() == null ? LocalDate.now(clock) : command.getAuditDate();

        NoonSalesProductViewsCompletenessAudit salesAudit = salesProductViewsAuditSource.audit(command);
        if (salesAudit == null) {
            salesAudit = NoonSalesProductViewsCompletenessAudit.missing();
        }
        NoonHistoricalBackfillPolicy.SkuBaselineEvidence skuBaselineEvidence = skuBaselineEvidence(command, auditDate);
        ProductDecision salesDecision = salesProductViewsDecision(salesAudit);
        NoonDataCompletenessRecord salesRow = upsertCompleteness(
                scope,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                salesDecision,
                auditDate
        );
        if (salesAudit.isPendingConfirmation()) {
            LocalDate targetDate = salesAudit.getLatestFactDate() == null ? auditDate.minusDays(1) : salesAudit.getLatestFactDate();
            upsertGap(
                    salesRow,
                    NoonDataGapWindowType.LATEST_DAILY,
                    targetDate,
                    targetDate,
                    NoonDataGapStatus.PENDING_CONFIRMATION,
                    salesAudit.getFailureType(),
                    Boolean.TRUE,
                    Boolean.FALSE,
                    salesDecision.diagnosticSummary
            );
        } else if (salesDecision.activeGapCount > 0) {
            LocalDate latestDate = auditDate.minusDays(1);
            GapDecision gapDecision = salesProductViewsGapDecision(salesAudit);
            upsertGap(
                    salesRow,
                    NoonDataGapWindowType.LATEST_DAILY,
                    latestDate,
                    latestDate,
                    gapDecision.status,
                    gapDecision.failureType,
                    gapDecision.retryable,
                    gapDecision.requiresManualAction,
                    salesDecision.diagnosticSummary
            );
            if (salesAudit.isFailed()) {
                LocalDate historyFrom = auditDate.minusMonths(DEFAULT_HISTORY_CORRECTION_MONTHS);
                pruneObsoleteHistoryBackfillGaps(salesRow, historyFrom, latestDate);
                upsertGap(
                        salesRow,
                        NoonDataGapWindowType.HISTORY_BACKFILL,
                        historyFrom,
                        latestDate,
                        gapDecision.status,
                        gapDecision.failureType,
                        gapDecision.retryable,
                        gapDecision.requiresManualAction,
                        salesDecision.diagnosticSummary
                );
            } else {
                upsertHistoryBackfillGaps(salesRow, auditDate, skuBaselineEvidence);
            }
        }

        NoonSalesOrderCompletenessAudit orderAudit = salesOrderAuditSource.audit(command);
        if (orderAudit == null) {
            orderAudit = NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated");
        }
        ProductDecision orderDecision = salesOrderDecision(orderAudit);
        NoonDataCompletenessRecord orderRow = upsertCompleteness(
                scope,
                NoonDataCategory.SALES_ORDER,
                orderDecision,
                auditDate
        );
        if (orderAudit.isIntegrated() && orderDecision.activeGapCount > 0) {
            LocalDate latestDate = auditDate.minusDays(1);
            upsertGap(orderRow, NoonDataGapWindowType.LATEST_DAILY, latestDate, latestDate, orderDecision.diagnosticSummary);
            upsertHistoryBackfillGaps(orderRow, auditDate, skuBaselineEvidence);
        }
    }

    private ProductDecision productListDecision(NoonProductCompletenessAudit audit) {
        if (audit.hasProductListBaseline()) {
            return new ProductDecision(
                    NoonDataLatestStatus.READY,
                    NoonDataHistoryStatus.NOT_REQUIRED,
                    0,
                    "商品列表/offer 基线存在，offer 数 " + audit.getSiteOfferCount() + "。"
            );
        }
        return new ProductDecision(
                NoonDataLatestStatus.INCOMPLETE,
                NoonDataHistoryStatus.NOT_REQUIRED,
                1,
                "商品列表/offer 基线缺失，等待商品列表拉取。"
        );
    }

    private ProductDecision productDetailDecision(NoonProductCompletenessAudit audit) {
        if (audit.detailCoverage() == NoonProductCompletenessAudit.DetailCoverage.COMPLETE) {
            return new ProductDecision(
                    NoonDataLatestStatus.READY,
                    NoonDataHistoryStatus.NOT_REQUIRED,
                    0,
                    "商品详情 baseline 完整，覆盖 " + audit.getDetailBaselineCount() + "/" + audit.getProductMasterCount() + "。"
            );
        }
        String summary = "商品详情 baseline 覆盖 " + audit.getDetailBaselineCount() + "/" + audit.getProductMasterCount() + "，等待详情补齐。";
        return new ProductDecision(
                NoonDataLatestStatus.INCOMPLETE,
                NoonDataHistoryStatus.NOT_REQUIRED,
                1,
                summary
        );
    }

    private ProductDecision salesProductViewsDecision(NoonSalesProductViewsCompletenessAudit audit) {
        int activeGapCount = audit.latestStatus() == NoonDataLatestStatus.READY ? 0 : (audit.isPendingConfirmation() ? 1 : 2);
        return new ProductDecision(
                audit.latestStatus(),
                audit.historyStatus(),
                activeGapCount,
                "Product Views/Sales facts rows=" + audit.getFactRowCount(),
                audit.getLatestFactDate(),
                audit.getHistoryCoveredFrom(),
                audit.getHistoryCoveredTo()
        );
    }

    private ProductDecision salesOrderDecision(NoonSalesOrderCompletenessAudit audit) {
        int activeGapCount = audit.latestStatus() == NoonDataLatestStatus.INCOMPLETE ? 2 : 0;
        String summary = audit.isIntegrated()
                ? "Noon order facts rows=" + audit.getOrderLineCount()
                : "Noon order domain not integrated: " + audit.getFailureType();
        return new ProductDecision(
                audit.latestStatus(),
                audit.historyStatus(),
                activeGapCount,
                summary,
                audit.getLatestOrderDate(),
                audit.getHistoryCoveredFrom(),
                audit.getHistoryCoveredTo()
        );
    }

    private GapDecision salesProductViewsGapDecision(NoonSalesProductViewsCompletenessAudit audit) {
        if (!audit.isFailed()) {
            return new GapDecision(NoonDataGapStatus.PENDING, null, Boolean.TRUE, Boolean.FALSE);
        }

        String failureType = audit.getFailureType();
        if ("provider_retention_limit".equals(failureType)) {
            return new GapDecision(NoonDataGapStatus.PROVIDER_RETENTION_LIMIT, failureType, Boolean.FALSE, Boolean.FALSE);
        }
        if ("report_not_ready".equals(failureType)
                || "timeout".equals(failureType)
                || "provider_unavailable".equals(failureType)) {
            return new GapDecision(NoonDataGapStatus.WAITING_RETRY, failureType, Boolean.TRUE, Boolean.FALSE);
        }
        return new GapDecision(NoonDataGapStatus.FAILED, failureType, Boolean.FALSE, Boolean.TRUE);
    }

    private void upsertHistoryBackfillGaps(
            NoonDataCompletenessRecord row,
            LocalDate auditDate,
            NoonHistoricalBackfillPolicy.SkuBaselineEvidence skuBaselineEvidence
    ) {
        NoonHistoricalBackfillPolicy.Target target = historicalBackfillPolicy.defaultTarget(
                row.getCategory(),
                auditDate,
                null,
                skuBaselineEvidence
        );
        pruneObsoleteHistoryBackfillGaps(row, target.getRequestedFrom(), target.getRequestedTo());
        for (NoonDataGapWindowRecord gap : historicalBackfillPolicy.splitIntoGapWindows(
                row,
                target,
                DEFAULT_HISTORY_GAP_WINDOW_DAYS
        )) {
            upsertGap(
                    row,
                    gap.getWindowType(),
                    gap.getDateFrom(),
                    gap.getDateTo(),
                    gap.getStatus(),
                    gap.getFailureType(),
                    gap.getRetryable(),
                    gap.getRequiresManualAction(),
                    gap.getDiagnosticSummary()
            );
        }
    }

    private void pruneObsoleteHistoryBackfillGaps(
            NoonDataCompletenessRecord row,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (row == null || row.getId() == null || row.getCategory() == null || dateFrom == null || dateTo == null) {
            return;
        }
        repository.deleteHistoryBackfillGapsOutsideRange(row.getId(), row.getCategory(), dateFrom, dateTo, now());
    }

    private NoonHistoricalBackfillPolicy.SkuBaselineEvidence skuBaselineEvidence(
            NoonDataAuditCommand command,
            LocalDate auditDate
    ) {
        NoonProductCompletenessAudit audit = productAuditSource.audit(command);
        if (audit == null) {
            audit = NoonProductCompletenessAudit.empty();
        }
        return NoonHistoricalBackfillPolicy.SkuBaselineEvidence.of(
                safeInt(audit.getProductMasterCount()),
                safeInt(audit.getSiteOfferCount()),
                safeInt(audit.getDetailBaselineCount()),
                auditDate
        );
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(value, 0);
    }

    private NoonDataCompletenessRecord upsertCompleteness(
            AuditScope scope,
            NoonDataCategory category,
            ProductDecision decision,
            LocalDate auditDate
    ) {
        NoonDataCompletenessRecord existing = existingCompleteness(scope, category);
        LocalDateTime now = now();
        NoonDataCompletenessRecord row = existing == null ? new NoonDataCompletenessRecord() : existing.copy();
        if (row.getId() == null) {
            row.setId(repository.nextId("noon_data_completeness", 900000L));
        }
        row.setOwnerUserId(scope.ownerUserId);
        row.setStoreCode(scope.storeCode);
        row.setSiteCode(scope.siteCode);
        row.setCategory(category);
        row.setLatestStatus(decision.latestStatus);
        row.setHistoryStatus(decision.historyStatus);
        row.setLatestDataDate(decision.latestDataDate != null ? decision.latestDataDate :
                (decision.latestStatus == NoonDataLatestStatus.READY ? auditDate : null));
        row.setHistoryCoveredFrom(decision.historyCoveredFrom);
        row.setHistoryCoveredTo(decision.historyCoveredTo);
        row.setPatrolEnabled(decision.activeGapCount > 0);
        row.setActiveGapCount(decision.activeGapCount);
        row.setDiagnosticSummary(decision.diagnosticSummary);
        row.setCreatedAt(row.getCreatedAt() == null ? now : row.getCreatedAt());
        row.setUpdatedAt(now);
        repository.insertCompleteness(row);
        return row;
    }

    private void upsertGap(
            NoonDataCompletenessRecord row,
            NoonDataGapWindowType windowType,
            LocalDate auditDate,
            String diagnosticSummary
    ) {
        upsertGap(
                row,
                windowType,
                auditDate,
                auditDate,
                NoonDataGapStatus.PENDING,
                null,
                Boolean.TRUE,
                Boolean.FALSE,
                diagnosticSummary
        );
    }

    private void upsertGap(
            NoonDataCompletenessRecord row,
            NoonDataGapWindowType windowType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String diagnosticSummary
    ) {
        upsertGap(
                row,
                windowType,
                dateFrom,
                dateTo,
                NoonDataGapStatus.PENDING,
                null,
                Boolean.TRUE,
                Boolean.FALSE,
                diagnosticSummary
        );
    }

    private void upsertGap(
            NoonDataCompletenessRecord row,
            NoonDataGapWindowType windowType,
            LocalDate dateFrom,
            LocalDate dateTo,
            NoonDataGapStatus status,
            String failureType,
            Boolean retryable,
            Boolean requiresManualAction,
            String diagnosticSummary
    ) {
        boolean exists = repository.listGapWindows(gapQuery(row)).stream()
                .anyMatch((gap) -> gap.getWindowType() == windowType
                        && Objects.equals(gap.getDateFrom(), dateFrom)
                        && Objects.equals(gap.getDateTo(), dateTo));
        if (exists) {
            return;
        }

        LocalDateTime now = now();
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(repository.nextId("noon_data_gap_window", 910000L));
        gap.setCompletenessId(row.getId());
        gap.setOwnerUserId(row.getOwnerUserId());
        gap.setStoreCode(row.getStoreCode());
        gap.setSiteCode(row.getSiteCode());
        gap.setCategory(row.getCategory());
        gap.setWindowType(windowType);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(status);
        gap.setAttempts(0);
        gap.setFailureType(failureType);
        gap.setRetryable(retryable);
        gap.setRequiresManualAction(requiresManualAction);
        gap.setDiagnosticSummary(diagnosticSummary);
        gap.setCreatedAt(now);
        gap.setUpdatedAt(now);
        repository.insertGapWindow(gap);
    }

    private NoonDataCompletenessRecord existingCompleteness(AuditScope scope, NoonDataCategory category) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setOwnerUserId(scope.ownerUserId);
        query.setStoreCode(scope.storeCode);
        query.setSiteCode(scope.siteCode);
        query.setCategory(category);
        return repository.listCompleteness(query).stream().findFirst().orElse(null);
    }

    private NoonDataGapQuery gapQuery(NoonDataCompletenessRecord row) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(row.getOwnerUserId());
        query.setStoreCode(row.getStoreCode());
        query.setSiteCode(row.getSiteCode());
        query.setCategory(row.getCategory());
        return query;
    }

    private AuditScope scope(NoonDataAuditCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少完整性审计命令。");
        }
        Long ownerUserId = command.getOwnerUserId();
        String storeCode = normalize(command.getStoreCode(), false);
        String siteCode = normalize(command.getSiteCode(), true);
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少 ownerUserId，无法审计数据完整性。");
        }
        if (storeCode.isEmpty()) {
            throw new IllegalArgumentException("缺少 storeCode，无法审计数据完整性。");
        }
        if (siteCode.isEmpty()) {
            throw new IllegalArgumentException("缺少 siteCode，无法审计数据完整性。");
        }
        return new AuditScope(ownerUserId, storeCode, siteCode);
    }

    private String normalize(String value, boolean upperCase) {
        String normalized = value == null ? "" : value.trim();
        return upperCase ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static class AuditScope {
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;

        private AuditScope(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    private static class ProductDecision {
        private final NoonDataLatestStatus latestStatus;
        private final NoonDataHistoryStatus historyStatus;
        private final int activeGapCount;
        private final String diagnosticSummary;
        private final LocalDate latestDataDate;
        private final LocalDate historyCoveredFrom;
        private final LocalDate historyCoveredTo;

        private ProductDecision(
                NoonDataLatestStatus latestStatus,
                NoonDataHistoryStatus historyStatus,
                int activeGapCount,
                String diagnosticSummary
        ) {
            this(latestStatus, historyStatus, activeGapCount, diagnosticSummary, null, null, null);
        }

        private ProductDecision(
                NoonDataLatestStatus latestStatus,
                NoonDataHistoryStatus historyStatus,
                int activeGapCount,
                String diagnosticSummary,
                LocalDate latestDataDate,
                LocalDate historyCoveredFrom,
                LocalDate historyCoveredTo
        ) {
            this.latestStatus = latestStatus;
            this.historyStatus = historyStatus;
            this.activeGapCount = activeGapCount;
            this.diagnosticSummary = diagnosticSummary;
            this.latestDataDate = latestDataDate;
            this.historyCoveredFrom = historyCoveredFrom;
            this.historyCoveredTo = historyCoveredTo;
        }
    }

    private static class GapDecision {
        private final NoonDataGapStatus status;
        private final String failureType;
        private final Boolean retryable;
        private final Boolean requiresManualAction;

        private GapDecision(
                NoonDataGapStatus status,
                String failureType,
                Boolean retryable,
                Boolean requiresManualAction
        ) {
            this.status = status;
            this.failureType = failureType;
            this.retryable = retryable;
            this.requiresManualAction = requiresManualAction;
        }
    }
}
