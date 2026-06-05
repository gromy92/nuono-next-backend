package com.nuono.next.procurement.aliorder;

import org.springframework.util.StringUtils;

public class Ali1688SkuPurchaseHistoryQuery {

    private String storeCode;
    private String siteCode;
    private String keyword;
    private String linkStatus = "all";
    private String purchaseTimeFrom;
    private String purchaseTimeTo;
    private int page = 1;
    private int pageSize = 20;

    public static Ali1688SkuPurchaseHistoryQuery fromRequest(
            String storeCode,
            String siteCode,
            String keyword,
            Integer page,
            Integer pageSize
    ) {
        return fromRequest(storeCode, siteCode, keyword, null, null, null, page, pageSize);
    }

    public static Ali1688SkuPurchaseHistoryQuery fromRequest(
            String storeCode,
            String siteCode,
            String keyword,
            String linkStatus,
            String purchaseTimeFrom,
            String purchaseTimeTo,
            Integer page,
            Integer pageSize
    ) {
        Ali1688SkuPurchaseHistoryQuery query = new Ali1688SkuPurchaseHistoryQuery();
        query.setStoreCode(trimToNull(storeCode));
        query.setSiteCode(trimToNull(siteCode));
        query.setKeyword(trimToNull(keyword));
        query.setLinkStatus(trimToNull(linkStatus) == null ? "all" : trimToNull(linkStatus));
        query.setPurchaseTimeFrom(trimToNull(purchaseTimeFrom));
        query.setPurchaseTimeTo(trimToNull(purchaseTimeTo));
        query.setPage(page == null || page < 1 ? 1 : page);
        query.setPageSize(pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100));
        return query;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(String linkStatus) {
        this.linkStatus = linkStatus;
    }

    public String getPurchaseTimeFrom() {
        return purchaseTimeFrom;
    }

    public void setPurchaseTimeFrom(String purchaseTimeFrom) {
        this.purchaseTimeFrom = purchaseTimeFrom;
    }

    public String getPurchaseTimeTo() {
        return purchaseTimeTo;
    }

    public void setPurchaseTimeTo(String purchaseTimeTo) {
        this.purchaseTimeTo = purchaseTimeTo;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
