package com.nuono.next.nooncompleteness;

import java.time.LocalDate;

public class NoonSalesProductViewsCompletenessAudit {
    private LocalDate latestFactDate;
    private LocalDate historyCoveredFrom;
    private LocalDate historyCoveredTo;
    private long factRowCount;
    private boolean pendingConfirmation;
    private boolean failed;
    private String failureType;

    public NoonSalesProductViewsCompletenessAudit() {
    }

    public static NoonSalesProductViewsCompletenessAudit factsPresent(
            LocalDate latestFactDate,
            LocalDate historyCoveredFrom,
            LocalDate historyCoveredTo,
            long factRowCount
    ) {
        NoonSalesProductViewsCompletenessAudit audit = new NoonSalesProductViewsCompletenessAudit();
        audit.latestFactDate = latestFactDate;
        audit.historyCoveredFrom = historyCoveredFrom;
        audit.historyCoveredTo = historyCoveredTo;
        audit.factRowCount = Math.max(factRowCount, 0);
        return audit;
    }

    public static NoonSalesProductViewsCompletenessAudit missing() {
        return new NoonSalesProductViewsCompletenessAudit();
    }

    public static NoonSalesProductViewsCompletenessAudit pendingEmptyConfirmation(LocalDate targetDate, String failureType) {
        NoonSalesProductViewsCompletenessAudit audit = new NoonSalesProductViewsCompletenessAudit();
        audit.latestFactDate = targetDate;
        audit.pendingConfirmation = true;
        audit.failureType = failureType;
        return audit;
    }

    public static NoonSalesProductViewsCompletenessAudit failed(String failureType) {
        NoonSalesProductViewsCompletenessAudit audit = new NoonSalesProductViewsCompletenessAudit();
        audit.failed = true;
        audit.failureType = failureType;
        return audit;
    }

    public NoonDataLatestStatus latestStatus() {
        if (failed) {
            return NoonDataLatestStatus.FAILED;
        }
        if (pendingConfirmation) {
            return NoonDataLatestStatus.PENDING_CONFIRMATION;
        }
        return latestFactDate == null || factRowCount <= 0 ? NoonDataLatestStatus.INCOMPLETE : NoonDataLatestStatus.READY;
    }

    public NoonDataHistoryStatus historyStatus() {
        if (failed) {
            return NoonDataHistoryStatus.FAILED;
        }
        return historyCoveredFrom == null || historyCoveredTo == null ? NoonDataHistoryStatus.INCOMPLETE : NoonDataHistoryStatus.COMPLETE;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public void setLatestFactDate(LocalDate latestFactDate) {
        this.latestFactDate = latestFactDate;
    }

    public LocalDate getHistoryCoveredFrom() {
        return historyCoveredFrom;
    }

    public void setHistoryCoveredFrom(LocalDate historyCoveredFrom) {
        this.historyCoveredFrom = historyCoveredFrom;
    }

    public LocalDate getHistoryCoveredTo() {
        return historyCoveredTo;
    }

    public void setHistoryCoveredTo(LocalDate historyCoveredTo) {
        this.historyCoveredTo = historyCoveredTo;
    }

    public long getFactRowCount() {
        return factRowCount;
    }

    public void setFactRowCount(long factRowCount) {
        this.factRowCount = Math.max(factRowCount, 0);
    }

    public boolean isPendingConfirmation() {
        return pendingConfirmation;
    }

    public void setPendingConfirmation(boolean pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }
}
