package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDateTime;

/** One current watch-product reference plus its daily rank/title evidence flags. */
public class Dp08ListTargetRow {
    private Long ownerUserId;
    private Long logicalStoreId;
    private String storeCode;
    private String siteCode;
    private String noonProductCode;
    private Long watchProductId;
    private Long competitorProductId;
    private Boolean rankedToday;
    private Boolean completeTitlesToday;
    private LocalDateTime sourceUpdatedAtUtc;
    private LocalDateTime rankEvidenceUpdatedAtUtc;
    private LocalDateTime titleEvidenceUpdatedAtUtc;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long logicalStoreId) { this.logicalStoreId = logicalStoreId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long competitorProductId) { this.competitorProductId = competitorProductId; }
    public Boolean getRankedToday() { return rankedToday; }
    public void setRankedToday(Boolean rankedToday) { this.rankedToday = rankedToday; }
    public Boolean getCompleteTitlesToday() { return completeTitlesToday; }
    public void setCompleteTitlesToday(Boolean completeTitlesToday) { this.completeTitlesToday = completeTitlesToday; }
    public LocalDateTime getSourceUpdatedAtUtc() { return sourceUpdatedAtUtc; }
    public void setSourceUpdatedAtUtc(LocalDateTime value) { sourceUpdatedAtUtc = value; }
    public LocalDateTime getRankEvidenceUpdatedAtUtc() { return rankEvidenceUpdatedAtUtc; }
    public void setRankEvidenceUpdatedAtUtc(LocalDateTime value) { rankEvidenceUpdatedAtUtc = value; }
    public LocalDateTime getTitleEvidenceUpdatedAtUtc() { return titleEvidenceUpdatedAtUtc; }
    public void setTitleEvidenceUpdatedAtUtc(LocalDateTime value) { titleEvidenceUpdatedAtUtc = value; }
}
