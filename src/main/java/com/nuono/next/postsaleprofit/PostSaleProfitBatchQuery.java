package com.nuono.next.postsaleprofit;

import java.time.LocalDate;

public class PostSaleProfitBatchQuery {
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String keyword;
    private final String quality;
    private final boolean onlyLoss;
    private final boolean onlyLowMargin;
    private final boolean onlyMissing;
    private final int page;
    private final int pageSize;

    public PostSaleProfitBatchQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword,
            String quality,
            boolean onlyLoss,
            boolean onlyLowMargin,
            boolean onlyMissing,
            int page,
            int pageSize
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.keyword = keyword;
        this.quality = quality;
        this.onlyLoss = onlyLoss;
        this.onlyLowMargin = onlyLowMargin;
        this.onlyMissing = onlyMissing;
        this.page = page;
        this.pageSize = pageSize;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getKeyword() { return keyword; }
    public String getQuality() { return quality; }
    public boolean isOnlyLoss() { return onlyLoss; }
    public boolean isOnlyLowMargin() { return onlyLowMargin; }
    public boolean isOnlyMissing() { return onlyMissing; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}
