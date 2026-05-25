package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NoonSalesLatestDayPlan {
    private final LocalDate date;
    private final String qualityState;
    private final boolean retryable;
    private final List<String> correctionTypes;

    public NoonSalesLatestDayPlan(LocalDate date, String qualityState, boolean retryable, List<String> correctionTypes) {
        this.date = date;
        this.qualityState = qualityState;
        this.retryable = retryable;
        this.correctionTypes = correctionTypes == null ? new ArrayList<>() : new ArrayList<>(correctionTypes);
    }

    public LocalDate getDate() {
        return date;
    }

    public String getQualityState() {
        return qualityState;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public List<String> getCorrectionTypes() {
        return correctionTypes;
    }
}
