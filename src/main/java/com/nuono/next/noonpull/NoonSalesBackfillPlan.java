package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonSalesBackfillPlan {
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String qualityState;
    private final boolean retryable;

    public NoonSalesBackfillPlan(LocalDate dateFrom, LocalDate dateTo, String qualityState, boolean retryable) {
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.qualityState = qualityState;
        this.retryable = retryable;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getQualityState() {
        return qualityState;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
