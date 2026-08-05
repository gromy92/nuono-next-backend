package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDate;

/** Set-based rank/title evidence for one sealed reference and task date. */
public class Dp08EvidenceResultRow {
    private String scopeKey;
    private LocalDate factDate;
    private Long watchProductId;
    private Long competitorProductId;
    private Boolean ranked;
    private Boolean completeTitles;

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public LocalDate getFactDate() { return factDate; }
    public void setFactDate(LocalDate value) { factDate = value; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long value) { watchProductId = value; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long value) { competitorProductId = value; }
    public Boolean getRanked() { return ranked; }
    public void setRanked(Boolean value) { ranked = value; }
    public Boolean getCompleteTitles() { return completeTitles; }
    public void setCompleteTitles(Boolean value) { completeTitles = value; }
}
