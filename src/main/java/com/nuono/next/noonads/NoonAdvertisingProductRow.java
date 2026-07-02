package com.nuono.next.noonads;

import com.nuono.next.productselection.NoonImageUrlNormalizer;
import java.math.BigDecimal;

public class NoonAdvertisingProductRow {
    private String storeCode;
    private String siteCode;
    private String adSkuCode;
    private String partnerSku;
    private String imageUrl;
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

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getAdSkuCode() { return adSkuCode; }
    public void setAdSkuCode(String adSkuCode) { this.adSkuCode = adSkuCode; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = NoonImageUrlNormalizer.normalize(imageUrl); }
    public String getProductIdentityKey() { return NoonAdvertisingProductIdentity.key(getStoreCode(), getSiteCode(), getPartnerSku()); }
    public String getAdvertisingIdentityKey() { return NoonAdvertisingProductIdentity.advertisingKey(getStoreCode(), getSiteCode(), getPartnerSku(), getAdSkuCode()); }
    public boolean isProductIdentityResolved() { return NoonAdvertisingProductIdentity.resolved(getPartnerSku()); }
    /** Compatibility alias only. Business identity must use partnerSku. */
    @Deprecated
    public String getSku() { return partnerSku != null && !partnerSku.isBlank() ? partnerSku : adSkuCode; }
    /** Compatibility alias only. Business identity must use partnerSku. */
    @Deprecated
    public void setSku(String sku) { this.adSkuCode = sku; }
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
