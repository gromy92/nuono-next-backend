package com.nuono.next.systemreports;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class StoreDataReportOverview {

    private String title;
    private LocalDateTime generatedAt;
    private List<StoreDataReportMetric> metrics = new ArrayList<>();
    private List<StoreDataReportRow> rows = new ArrayList<>();

    public StoreDataReportOverview() {
    }

    public StoreDataReportOverview(
            String title,
            LocalDateTime generatedAt,
            List<StoreDataReportMetric> metrics,
            List<StoreDataReportRow> rows
    ) {
        this.title = title;
        this.generatedAt = generatedAt;
        this.metrics = metrics == null ? new ArrayList<>() : new ArrayList<>(metrics);
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<StoreDataReportMetric> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<StoreDataReportMetric> metrics) {
        this.metrics = metrics == null ? new ArrayList<>() : new ArrayList<>(metrics);
    }

    public List<StoreDataReportRow> getRows() {
        return rows;
    }

    public void setRows(List<StoreDataReportRow> rows) {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }
}
