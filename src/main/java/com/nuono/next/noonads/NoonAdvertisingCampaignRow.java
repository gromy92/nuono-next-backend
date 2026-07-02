package com.nuono.next.noonads;

import java.math.BigDecimal;

public class NoonAdvertisingCampaignRow {
    private String campaignCode;
    private String campaignName;
    private String storeCode;
    private String siteCode;
    private String primaryAdSkuCode;
    private String primaryPartnerSku;
    private String campaignStatus;
    private String qcStatus;
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

    public String getCampaignCode() { return campaignCode; }
    public void setCampaignCode(String campaignCode) { this.campaignCode = campaignCode; }
    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String campaignName) { this.campaignName = campaignName; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getPrimaryAdSkuCode() { return primaryAdSkuCode; }
    public void setPrimaryAdSkuCode(String primaryAdSkuCode) { this.primaryAdSkuCode = primaryAdSkuCode; }
    public String getPrimaryPartnerSku() { return primaryPartnerSku; }
    public void setPrimaryPartnerSku(String primaryPartnerSku) { this.primaryPartnerSku = primaryPartnerSku; }
    public String getProductIdentityKey() { return NoonAdvertisingProductIdentity.key(getStoreCode(), getSiteCode(), getPrimaryPartnerSku()); }
    public String getAdvertisingIdentityKey() { return NoonAdvertisingProductIdentity.advertisingKey(getStoreCode(), getSiteCode(), getPrimaryPartnerSku(), getPrimaryAdSkuCode()); }
    public boolean isProductIdentityResolved() { return NoonAdvertisingProductIdentity.resolved(getPrimaryPartnerSku()); }
    /** Compatibility alias only. Business identity must use primaryPartnerSku. */
    @Deprecated
    public String getPrimarySku() { return primaryPartnerSku != null && !primaryPartnerSku.isBlank() ? primaryPartnerSku : primaryAdSkuCode; }
    /** Compatibility alias only. Business identity must use primaryPartnerSku. */
    @Deprecated
    public void setPrimarySku(String primarySku) { this.primaryAdSkuCode = primarySku; }
    public String getCampaignStatus() { return campaignStatus; }
    public void setCampaignStatus(String campaignStatus) { this.campaignStatus = campaignStatus; }
    public String getQcStatus() { return qcStatus; }
    public void setQcStatus(String qcStatus) { this.qcStatus = qcStatus; }
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
