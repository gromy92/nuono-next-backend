package com.nuono.next.noonads;

import java.math.BigDecimal;

public class NoonAdvertisingSummaryView {
    private long campaignCount;
    private long queryCount;
    private long views;
    private long clicks;
    private long ordersCount;
    private long assistedOrders;
    private long atcCount;
    private BigDecimal spendAmount = BigDecimal.ZERO;
    private BigDecimal adRevenue = BigDecimal.ZERO;
    private BigDecimal ctrPercentage = BigDecimal.ZERO;
    private BigDecimal roas = BigDecimal.ZERO;
    private BigDecimal cpc = BigDecimal.ZERO;
    private BigDecimal cps = BigDecimal.ZERO;
    private BigDecimal cvrPercentage = BigDecimal.ZERO;
    private BigDecimal zeroOrderSpendAmount = BigDecimal.ZERO;
    private BigDecimal zeroOrderSpendShare = BigDecimal.ZERO;

    public long getCampaignCount() { return campaignCount; }
    public void setCampaignCount(long campaignCount) { this.campaignCount = campaignCount; }
    public long getQueryCount() { return queryCount; }
    public void setQueryCount(long queryCount) { this.queryCount = queryCount; }
    public long getViews() { return views; }
    public void setViews(long views) { this.views = views; }
    public long getClicks() { return clicks; }
    public void setClicks(long clicks) { this.clicks = clicks; }
    public long getOrdersCount() { return ordersCount; }
    public void setOrdersCount(long ordersCount) { this.ordersCount = ordersCount; }
    public long getAssistedOrders() { return assistedOrders; }
    public void setAssistedOrders(long assistedOrders) { this.assistedOrders = assistedOrders; }
    public long getAtcCount() { return atcCount; }
    public void setAtcCount(long atcCount) { this.atcCount = atcCount; }
    public BigDecimal getSpendAmount() { return spendAmount; }
    public void setSpendAmount(BigDecimal spendAmount) { this.spendAmount = valueOrZero(spendAmount); }
    public BigDecimal getAdRevenue() { return adRevenue; }
    public void setAdRevenue(BigDecimal adRevenue) { this.adRevenue = valueOrZero(adRevenue); }
    public BigDecimal getCtrPercentage() { return ctrPercentage; }
    public void setCtrPercentage(BigDecimal ctrPercentage) { this.ctrPercentage = valueOrZero(ctrPercentage); }
    public BigDecimal getRoas() { return roas; }
    public void setRoas(BigDecimal roas) { this.roas = valueOrZero(roas); }
    public BigDecimal getCpc() { return cpc; }
    public void setCpc(BigDecimal cpc) { this.cpc = valueOrZero(cpc); }
    public BigDecimal getCps() { return cps; }
    public void setCps(BigDecimal cps) { this.cps = valueOrZero(cps); }
    public BigDecimal getCvrPercentage() { return cvrPercentage; }
    public void setCvrPercentage(BigDecimal cvrPercentage) { this.cvrPercentage = valueOrZero(cvrPercentage); }
    public BigDecimal getZeroOrderSpendAmount() { return zeroOrderSpendAmount; }
    public void setZeroOrderSpendAmount(BigDecimal zeroOrderSpendAmount) { this.zeroOrderSpendAmount = valueOrZero(zeroOrderSpendAmount); }
    public BigDecimal getZeroOrderSpendShare() { return zeroOrderSpendShare; }
    public void setZeroOrderSpendShare(BigDecimal zeroOrderSpendShare) { this.zeroOrderSpendShare = valueOrZero(zeroOrderSpendShare); }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
