package com.nuono.next.competitoranalysis;

import java.util.ArrayList;
import java.util.List;

public class CompetitorBrowserObservationCommand {
    private String sourceUrl;
    private String keyword;
    private List<CompetitorBrowserObservationItem> items = new ArrayList<>();

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public List<CompetitorBrowserObservationItem> getItems() { return items; }
    public void setItems(List<CompetitorBrowserObservationItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
