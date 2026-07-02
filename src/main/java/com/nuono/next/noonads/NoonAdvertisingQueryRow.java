package com.nuono.next.noonads;

import java.math.BigDecimal;

public class NoonAdvertisingQueryRow {
    private String campaignCode;
    private String campaignName;
    private String storeCode;
    private String siteCode;
    private String adSkuCode;
    private String partnerSku;
    private String queryText;
    private String queryKind;
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

    public String getCampaignCode() { return campaignCode; }
    public void setCampaignCode(String campaignCode) { this.campaignCode = campaignCode; }
    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String campaignName) { this.campaignName = campaignName; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getAdSkuCode() { return adSkuCode; }
    public void setAdSkuCode(String adSkuCode) { this.adSkuCode = adSkuCode; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getProductIdentityKey() { return NoonAdvertisingProductIdentity.key(getStoreCode(), getSiteCode(), getPartnerSku()); }
    public String getAdvertisingIdentityKey() { return NoonAdvertisingProductIdentity.advertisingKey(getStoreCode(), getSiteCode(), getPartnerSku(), getAdSkuCode()); }
    public boolean isProductIdentityResolved() { return NoonAdvertisingProductIdentity.resolved(getPartnerSku()); }
    /** Compatibility alias only. Business identity must use partnerSku. */
    @Deprecated
    public String getSku() { return partnerSku != null && !partnerSku.isBlank() ? partnerSku : adSkuCode; }
    /** Compatibility alias only. Business identity must use partnerSku. */
    @Deprecated
    public void setSku(String sku) { this.adSkuCode = sku; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getQueryKind() { return queryKind; }
    public void setQueryKind(String queryKind) { this.queryKind = queryKind; }
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

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
