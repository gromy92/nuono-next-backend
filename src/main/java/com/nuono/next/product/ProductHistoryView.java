package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductHistoryView {

    private boolean ready;
    private String source;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private ProductListSummaryView listSummary;
    private List<Map<String, Object>> historyItems = new ArrayList<>();
    private Integer pendingKeyContentHistoryCount;
    private Integer visibleKeyContentHistoryCount;
    private String pendingKeyContentHistoryVisibleAfter;
    private String note;

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public ProductListSummaryView getListSummary() {
        return listSummary;
    }

    public void setListSummary(ProductListSummaryView listSummary) {
        this.listSummary = listSummary;
    }

    public List<Map<String, Object>> getHistoryItems() {
        return historyItems;
    }

    public void setHistoryItems(List<Map<String, Object>> historyItems) {
        this.historyItems = historyItems;
    }

    public Integer getPendingKeyContentHistoryCount() {
        return pendingKeyContentHistoryCount;
    }

    public void setPendingKeyContentHistoryCount(Integer pendingKeyContentHistoryCount) {
        this.pendingKeyContentHistoryCount = pendingKeyContentHistoryCount;
    }

    public Integer getVisibleKeyContentHistoryCount() {
        return visibleKeyContentHistoryCount;
    }

    public void setVisibleKeyContentHistoryCount(Integer visibleKeyContentHistoryCount) {
        this.visibleKeyContentHistoryCount = visibleKeyContentHistoryCount;
    }

    public String getPendingKeyContentHistoryVisibleAfter() {
        return pendingKeyContentHistoryVisibleAfter;
    }

    public void setPendingKeyContentHistoryVisibleAfter(String pendingKeyContentHistoryVisibleAfter) {
        this.pendingKeyContentHistoryVisibleAfter = pendingKeyContentHistoryVisibleAfter;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
