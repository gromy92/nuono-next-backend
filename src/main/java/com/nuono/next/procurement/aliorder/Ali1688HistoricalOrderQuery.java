package com.nuono.next.procurement.aliorder;

import java.util.Objects;

public class Ali1688HistoricalOrderQuery {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private String placedTimeFrom;
    private String placedTimeTo;
    private String orderStatus;
    private String supplierKeyword;
    private String keyword;
    private String storeCode;
    private String siteCode;
    private String assignmentState;
    private String assignmentTargetStoreCode;
    private String assignmentTargetSiteCode;
    private String productLinkState;
    private int page;
    private int pageSize;

    public static Ali1688HistoricalOrderQuery defaultQuery() {
        return fromRequest(null, null, null, null, null, null, null);
    }

    public static Ali1688HistoricalOrderQuery fromRequest(
            String placedTimeFrom,
            String placedTimeTo,
            String orderStatus,
            String supplierKeyword,
            String keyword,
            Integer page,
            Integer pageSize
    ) {
        return fromRequest(
                placedTimeFrom,
                placedTimeTo,
                orderStatus,
                supplierKeyword,
                keyword,
                null,
                null,
                null,
                null,
                null,
                null,
                page,
                pageSize
        );
    }

    public static Ali1688HistoricalOrderQuery fromRequest(
            String placedTimeFrom,
            String placedTimeTo,
            String orderStatus,
            String supplierKeyword,
            String keyword,
            String storeCode,
            String siteCode,
            Integer page,
            Integer pageSize
    ) {
        return fromRequest(
                placedTimeFrom,
                placedTimeTo,
                orderStatus,
                supplierKeyword,
                keyword,
                storeCode,
                siteCode,
                null,
                null,
                null,
                null,
                page,
                pageSize
        );
    }

    public static Ali1688HistoricalOrderQuery fromRequest(
            String placedTimeFrom,
            String placedTimeTo,
            String orderStatus,
            String supplierKeyword,
            String keyword,
            String storeCode,
            String siteCode,
            String assignmentState,
            String assignmentTargetStoreCode,
            String assignmentTargetSiteCode,
            Integer page,
            Integer pageSize
    ) {
        return fromRequest(
                placedTimeFrom,
                placedTimeTo,
                orderStatus,
                supplierKeyword,
                keyword,
                storeCode,
                siteCode,
                assignmentState,
                assignmentTargetStoreCode,
                assignmentTargetSiteCode,
                null,
                page,
                pageSize
        );
    }

    public static Ali1688HistoricalOrderQuery fromRequest(
            String placedTimeFrom,
            String placedTimeTo,
            String orderStatus,
            String supplierKeyword,
            String keyword,
            String storeCode,
            String siteCode,
            String assignmentState,
            String assignmentTargetStoreCode,
            String assignmentTargetSiteCode,
            String productLinkState,
            Integer page,
            Integer pageSize
    ) {
        Ali1688HistoricalOrderQuery query = new Ali1688HistoricalOrderQuery();
        query.setPlacedTimeFrom(blankToNull(placedTimeFrom));
        query.setPlacedTimeTo(blankToNull(placedTimeTo));
        query.setOrderStatus(blankToNull(orderStatus));
        query.setSupplierKeyword(blankToNull(supplierKeyword));
        query.setKeyword(blankToNull(keyword));
        query.setStoreCode(blankToNull(storeCode));
        query.setSiteCode(blankToNull(siteCode));
        query.setAssignmentState(blankToNull(assignmentState));
        query.setAssignmentTargetStoreCode(blankToNull(assignmentTargetStoreCode));
        query.setAssignmentTargetSiteCode(blankToNull(assignmentTargetSiteCode));
        query.setProductLinkState(blankToNull(productLinkState));
        query.setPage(page == null || page < 1 ? DEFAULT_PAGE : page);
        query.setPageSize(normalizePageSize(pageSize));
        return query;
    }

    private static String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static int normalizePageSize(Integer value) {
        if (value == null || value < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }

    public int getOffset() {
        return (page - 1) * pageSize;
    }

    public String getPlacedTimeFrom() {
        return placedTimeFrom;
    }

    public void setPlacedTimeFrom(String placedTimeFrom) {
        this.placedTimeFrom = placedTimeFrom;
    }

    public String getPlacedTimeTo() {
        return placedTimeTo;
    }

    public void setPlacedTimeTo(String placedTimeTo) {
        this.placedTimeTo = placedTimeTo;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getSupplierKeyword() {
        return supplierKeyword;
    }

    public void setSupplierKeyword(String supplierKeyword) {
        this.supplierKeyword = supplierKeyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    public String getAssignmentState() {
        return assignmentState;
    }

    public void setAssignmentState(String assignmentState) {
        this.assignmentState = blankToNull(assignmentState);
    }

    public String getAssignmentTargetStoreCode() {
        return assignmentTargetStoreCode;
    }

    public void setAssignmentTargetStoreCode(String assignmentTargetStoreCode) {
        this.assignmentTargetStoreCode = blankToNull(assignmentTargetStoreCode);
    }

    public String getAssignmentTargetSiteCode() {
        return assignmentTargetSiteCode;
    }

    public void setAssignmentTargetSiteCode(String assignmentTargetSiteCode) {
        this.assignmentTargetSiteCode = blankToNull(assignmentTargetSiteCode);
    }

    public String getProductLinkState() {
        return productLinkState;
    }

    public void setProductLinkState(String productLinkState) {
        this.productLinkState = blankToNull(productLinkState);
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Ali1688HistoricalOrderQuery)) {
            return false;
        }
        Ali1688HistoricalOrderQuery that = (Ali1688HistoricalOrderQuery) other;
        return page == that.page
                && pageSize == that.pageSize
                && Objects.equals(placedTimeFrom, that.placedTimeFrom)
                && Objects.equals(placedTimeTo, that.placedTimeTo)
                && Objects.equals(orderStatus, that.orderStatus)
                && Objects.equals(supplierKeyword, that.supplierKeyword)
                && Objects.equals(keyword, that.keyword)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode)
                && Objects.equals(assignmentState, that.assignmentState)
                && Objects.equals(assignmentTargetStoreCode, that.assignmentTargetStoreCode)
                && Objects.equals(assignmentTargetSiteCode, that.assignmentTargetSiteCode)
                && Objects.equals(productLinkState, that.productLinkState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                placedTimeFrom,
                placedTimeTo,
                orderStatus,
                supplierKeyword,
                keyword,
                storeCode,
                siteCode,
                assignmentState,
                assignmentTargetStoreCode,
                assignmentTargetSiteCode,
                productLinkState,
                page,
                pageSize
        );
    }
}
