package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;

public class PostSaleProfitFxRateRequest {
    private String storeCode;
    private String siteCode;
    private String currency;
    private BigDecimal rateToCny;
    private String effectiveFrom;
    private String effectiveTo;
    private String sourceLabel;

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getRateToCny() { return rateToCny; }
    public void setRateToCny(BigDecimal rateToCny) { this.rateToCny = rateToCny; }
    public String getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public String getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getSourceLabel() { return sourceLabel; }
    public void setSourceLabel(String sourceLabel) { this.sourceLabel = sourceLabel; }
}
