package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonSalesCorrectionPlan {
    private final String type;
    private final String frequency;
    private final int windowDays;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;

    public NoonSalesCorrectionPlan(String type, String frequency, int windowDays, LocalDate dateFrom, LocalDate dateTo) {
        this.type = type;
        this.frequency = frequency;
        this.windowDays = windowDays;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public String getType() {
        return type;
    }

    public String getFrequency() {
        return frequency;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }
}
