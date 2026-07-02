package com.nuono.next.noonads;

import java.time.LocalDate;

public class NoonAdvertisingLatestReportWindowView {
    private boolean dataAvailable;
    private LocalDate dateFrom;
    private LocalDate dateTo;

    public NoonAdvertisingLatestReportWindowView() {
    }

    public NoonAdvertisingLatestReportWindowView(boolean dataAvailable, LocalDate dateFrom, LocalDate dateTo) {
        this.dataAvailable = dataAvailable;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
}
