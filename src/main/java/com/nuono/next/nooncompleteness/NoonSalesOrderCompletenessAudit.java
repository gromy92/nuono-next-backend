package com.nuono.next.nooncompleteness;

import java.time.LocalDate;

public class NoonSalesOrderCompletenessAudit {
    private boolean integrated = true;
    private LocalDate latestOrderDate;
    private LocalDate historyCoveredFrom;
    private LocalDate historyCoveredTo;
    private long orderLineCount;
    private String failureType;

    public NoonSalesOrderCompletenessAudit() {
    }

    public static NoonSalesOrderCompletenessAudit factsPresent(
            LocalDate latestOrderDate,
            LocalDate historyCoveredFrom,
            LocalDate historyCoveredTo,
            long orderLineCount
    ) {
        NoonSalesOrderCompletenessAudit audit = new NoonSalesOrderCompletenessAudit();
        audit.latestOrderDate = latestOrderDate;
        audit.historyCoveredFrom = historyCoveredFrom;
        audit.historyCoveredTo = historyCoveredTo;
        audit.orderLineCount = Math.max(orderLineCount, 0);
        return audit;
    }

    public static NoonSalesOrderCompletenessAudit missing() {
        return new NoonSalesOrderCompletenessAudit();
    }

    public static NoonSalesOrderCompletenessAudit notIntegrated(String failureType) {
        NoonSalesOrderCompletenessAudit audit = new NoonSalesOrderCompletenessAudit();
        audit.integrated = false;
        audit.failureType = failureType;
        return audit;
    }

    public NoonDataLatestStatus latestStatus() {
        if (!integrated) {
            return NoonDataLatestStatus.NOT_INTEGRATED;
        }
        return latestOrderDate == null || orderLineCount <= 0 ? NoonDataLatestStatus.INCOMPLETE : NoonDataLatestStatus.READY;
    }

    public NoonDataHistoryStatus historyStatus() {
        if (!integrated) {
            return NoonDataHistoryStatus.NOT_INTEGRATED;
        }
        return historyCoveredFrom == null || historyCoveredTo == null ? NoonDataHistoryStatus.INCOMPLETE : NoonDataHistoryStatus.COMPLETE;
    }

    public boolean isIntegrated() {
        return integrated;
    }

    public void setIntegrated(boolean integrated) {
        this.integrated = integrated;
    }

    public LocalDate getLatestOrderDate() {
        return latestOrderDate;
    }

    public void setLatestOrderDate(LocalDate latestOrderDate) {
        this.latestOrderDate = latestOrderDate;
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

    public long getOrderLineCount() {
        return orderLineCount;
    }

    public void setOrderLineCount(long orderLineCount) {
        this.orderLineCount = Math.max(orderLineCount, 0);
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }
}
