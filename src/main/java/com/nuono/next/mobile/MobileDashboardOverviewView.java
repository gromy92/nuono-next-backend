package com.nuono.next.mobile;

import java.math.BigDecimal;

public class MobileDashboardOverviewView {

    private String storeCode;

    private String storeName;

    private BigDecimal yesterdaySalesAmount;

    private Integer yesterdayOrderCount;

    private Integer taskFailedCount;

    private Integer unreadMessageCount;

    private String lastUpdatedAt;

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public BigDecimal getYesterdaySalesAmount() {
        return yesterdaySalesAmount;
    }

    public void setYesterdaySalesAmount(BigDecimal yesterdaySalesAmount) {
        this.yesterdaySalesAmount = yesterdaySalesAmount;
    }

    public Integer getYesterdayOrderCount() {
        return yesterdayOrderCount;
    }

    public void setYesterdayOrderCount(Integer yesterdayOrderCount) {
        this.yesterdayOrderCount = yesterdayOrderCount;
    }

    public Integer getTaskFailedCount() {
        return taskFailedCount;
    }

    public void setTaskFailedCount(Integer taskFailedCount) {
        this.taskFailedCount = taskFailedCount;
    }

    public Integer getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public void setUnreadMessageCount(Integer unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    }

    public String getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(String lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }
}
