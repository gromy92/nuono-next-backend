package com.nuono.next.logisticsquote;

import java.util.ArrayList;
import java.util.List;

public class LogisticsQuoteOperationPriceItemsView {

    private String mode;

    private boolean ready;

    private String message;

    private LogisticsQuoteOperationPriceItemsSummaryView summary = new LogisticsQuoteOperationPriceItemsSummaryView();

    private List<LogisticsQuoteOperationPriceItemView> items = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LogisticsQuoteOperationPriceItemsSummaryView getSummary() {
        return summary;
    }

    public void setSummary(LogisticsQuoteOperationPriceItemsSummaryView summary) {
        this.summary = summary;
    }

    public List<LogisticsQuoteOperationPriceItemView> getItems() {
        return items;
    }

    public void setItems(List<LogisticsQuoteOperationPriceItemView> items) {
        this.items = items;
    }
}
