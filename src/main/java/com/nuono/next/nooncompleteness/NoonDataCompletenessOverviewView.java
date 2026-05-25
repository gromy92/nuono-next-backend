package com.nuono.next.nooncompleteness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoonDataCompletenessOverviewView {
    private String title = "数据完整度";
    private LocalDateTime generatedAt = LocalDateTime.now();
    private List<Metric> metrics = new ArrayList<>();
    private List<DistributionItem> categoryDistribution = new ArrayList<>();
    private List<DistributionItem> latestStatusDistribution = new ArrayList<>();
    private List<DistributionItem> historyStatusDistribution = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public List<Metric> getMetrics() { return metrics; }
    public void setMetrics(List<Metric> metrics) { this.metrics = metrics == null ? new ArrayList<>() : metrics; }
    public List<DistributionItem> getCategoryDistribution() { return categoryDistribution; }
    public void setCategoryDistribution(List<DistributionItem> categoryDistribution) { this.categoryDistribution = categoryDistribution == null ? new ArrayList<>() : categoryDistribution; }
    public List<DistributionItem> getLatestStatusDistribution() { return latestStatusDistribution; }
    public void setLatestStatusDistribution(List<DistributionItem> latestStatusDistribution) { this.latestStatusDistribution = latestStatusDistribution == null ? new ArrayList<>() : latestStatusDistribution; }
    public List<DistributionItem> getHistoryStatusDistribution() { return historyStatusDistribution; }
    public void setHistoryStatusDistribution(List<DistributionItem> historyStatusDistribution) { this.historyStatusDistribution = historyStatusDistribution == null ? new ArrayList<>() : historyStatusDistribution; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows == null ? new ArrayList<>() : rows; }

    public static class Metric {
        private String key;
        private String title;
        private long value;
        private String unit;
        private String state;

        public Metric() {
        }

        public Metric(String key, String title, long value, String unit, String state) {
            this.key = key;
            this.title = title;
            this.value = value;
            this.unit = unit;
            this.state = state;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    public static class DistributionItem {
        private String key;
        private String label;
        private long value;

        public DistributionItem() {
        }

        public DistributionItem(String key, String label, long value) {
            this.key = key;
            this.label = label;
            this.value = value;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
    }

    public static class Row {
        private Long id;
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;
        private NoonDataCategory category;
        private NoonDataLatestStatus latestStatus;
        private NoonDataHistoryStatus historyStatus;
        private LocalDate latestDataDate;
        private LocalDate historyCoveredFrom;
        private LocalDate historyCoveredTo;
        private boolean patrolEnabled;
        private LocalDateTime nextPatrolAt;
        private Integer activeGapCount;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public NoonDataCategory getCategory() { return category; }
        public void setCategory(NoonDataCategory category) { this.category = category; }
        public NoonDataLatestStatus getLatestStatus() { return latestStatus; }
        public void setLatestStatus(NoonDataLatestStatus latestStatus) { this.latestStatus = latestStatus; }
        public NoonDataHistoryStatus getHistoryStatus() { return historyStatus; }
        public void setHistoryStatus(NoonDataHistoryStatus historyStatus) { this.historyStatus = historyStatus; }
        public LocalDate getLatestDataDate() { return latestDataDate; }
        public void setLatestDataDate(LocalDate latestDataDate) { this.latestDataDate = latestDataDate; }
        public LocalDate getHistoryCoveredFrom() { return historyCoveredFrom; }
        public void setHistoryCoveredFrom(LocalDate historyCoveredFrom) { this.historyCoveredFrom = historyCoveredFrom; }
        public LocalDate getHistoryCoveredTo() { return historyCoveredTo; }
        public void setHistoryCoveredTo(LocalDate historyCoveredTo) { this.historyCoveredTo = historyCoveredTo; }
        public boolean isPatrolEnabled() { return patrolEnabled; }
        public void setPatrolEnabled(boolean patrolEnabled) { this.patrolEnabled = patrolEnabled; }
        public LocalDateTime getNextPatrolAt() { return nextPatrolAt; }
        public void setNextPatrolAt(LocalDateTime nextPatrolAt) { this.nextPatrolAt = nextPatrolAt; }
        public Integer getActiveGapCount() { return activeGapCount; }
        public void setActiveGapCount(Integer activeGapCount) { this.activeGapCount = activeGapCount; }
    }
}

