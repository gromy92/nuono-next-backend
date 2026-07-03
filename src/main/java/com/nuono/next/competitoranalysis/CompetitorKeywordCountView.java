package com.nuono.next.competitoranalysis;

public class CompetitorKeywordCountView {
    private String keyword;
    private int monitoredCount;
    private String previousRankStatus;
    private Integer previousRankNo;
    private String previousDate;
    private String rankStatus;
    private Integer rankNo;
    private String currentDate;
    private Integer rankDelta;

    public static CompetitorKeywordCountView of(String keyword, int monitoredCount) {
        return of(keyword, monitoredCount, null, null, null, null, null, null);
    }

    public static CompetitorKeywordCountView of(
            String keyword,
            int monitoredCount,
            String previousRankStatus,
            Integer previousRankNo,
            String previousDate,
            String rankStatus,
            Integer rankNo,
            String currentDate
    ) {
        CompetitorKeywordCountView view = new CompetitorKeywordCountView();
        view.setKeyword(keyword);
        view.setMonitoredCount(monitoredCount);
        view.setPreviousRankStatus(blankToNull(previousRankStatus));
        view.setPreviousRankNo(previousRankNo);
        view.setPreviousDate(blankToNull(previousDate));
        view.setRankStatus(blankToNull(rankStatus));
        view.setRankNo(rankNo);
        view.setCurrentDate(blankToNull(currentDate));
        view.setRankDelta(calculateRankDelta(previousRankStatus, previousRankNo, rankStatus, rankNo));
        return view;
    }

    private static Integer calculateRankDelta(
            String previousRankStatus,
            Integer previousRankNo,
            String rankStatus,
            Integer rankNo
    ) {
        if (!"RANKED".equalsIgnoreCase(previousRankStatus)
                || !"RANKED".equalsIgnoreCase(rankStatus)
                || previousRankNo == null
                || rankNo == null) {
            return null;
        }
        return previousRankNo - rankNo;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public int getMonitoredCount() { return monitoredCount; }
    public void setMonitoredCount(int monitoredCount) { this.monitoredCount = monitoredCount; }
    public String getPreviousRankStatus() { return previousRankStatus; }
    public void setPreviousRankStatus(String previousRankStatus) { this.previousRankStatus = previousRankStatus; }
    public Integer getPreviousRankNo() { return previousRankNo; }
    public void setPreviousRankNo(Integer previousRankNo) { this.previousRankNo = previousRankNo; }
    public String getPreviousDate() { return previousDate; }
    public void setPreviousDate(String previousDate) { this.previousDate = previousDate; }
    public String getRankStatus() { return rankStatus; }
    public void setRankStatus(String rankStatus) { this.rankStatus = rankStatus; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public String getCurrentDate() { return currentDate; }
    public void setCurrentDate(String currentDate) { this.currentDate = currentDate; }
    public Integer getRankDelta() { return rankDelta; }
    public void setRankDelta(Integer rankDelta) { this.rankDelta = rankDelta; }
}
