package com.nuono.next.systemreports;

public class StoreDataReportMetric {

    private String key;
    private String title;
    private long value;
    private String unit;
    private String state;

    public StoreDataReportMetric() {
    }

    public StoreDataReportMetric(String key, String title, long value, String unit, String state) {
        this.key = key;
        this.title = title;
        this.value = value;
        this.unit = unit;
        this.state = state;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
