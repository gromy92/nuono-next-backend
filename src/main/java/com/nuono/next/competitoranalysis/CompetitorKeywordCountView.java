package com.nuono.next.competitoranalysis;

public class CompetitorKeywordCountView {
    private String keyword;
    private int monitoredCount;

    public static CompetitorKeywordCountView of(String keyword, int monitoredCount) {
        CompetitorKeywordCountView view = new CompetitorKeywordCountView();
        view.setKeyword(keyword);
        view.setMonitoredCount(monitoredCount);
        return view;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public int getMonitoredCount() { return monitoredCount; }
    public void setMonitoredCount(int monitoredCount) { this.monitoredCount = monitoredCount; }
}
