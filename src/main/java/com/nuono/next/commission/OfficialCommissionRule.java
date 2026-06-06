package com.nuono.next.commission;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OfficialCommissionRule {
    private String naturalKey;
    private String country;
    private String platform;
    private String fulfillmentType;
    private String parentCategoryName;
    private String categoryName;
    private String categoryPath;
    private String brandRestriction;
    private String amountRangeLabel;
    private BigDecimal amountMin;
    private Boolean amountMinInclusive;
    private BigDecimal amountMax;
    private Boolean amountMaxInclusive;
    private String amountCurrency;
    private String commissionRate;
    private LocalDate effectiveDate;
    private Long sourceVersionId;
    private Long sourceVersionItemId;

    public String getNaturalKey() { return naturalKey; }
    public void setNaturalKey(String naturalKey) { this.naturalKey = naturalKey; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(String fulfillmentType) { this.fulfillmentType = fulfillmentType; }
    public String getParentCategoryName() { return parentCategoryName; }
    public void setParentCategoryName(String parentCategoryName) { this.parentCategoryName = parentCategoryName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String categoryPath) { this.categoryPath = categoryPath; }
    public String getBrandRestriction() { return brandRestriction; }
    public void setBrandRestriction(String brandRestriction) { this.brandRestriction = brandRestriction; }
    public String getAmountRangeLabel() { return amountRangeLabel; }
    public void setAmountRangeLabel(String amountRangeLabel) { this.amountRangeLabel = amountRangeLabel; }
    public BigDecimal getAmountMin() { return amountMin; }
    public void setAmountMin(BigDecimal amountMin) { this.amountMin = amountMin; }
    public Boolean getAmountMinInclusive() { return amountMinInclusive; }
    public void setAmountMinInclusive(Boolean amountMinInclusive) { this.amountMinInclusive = amountMinInclusive; }
    public BigDecimal getAmountMax() { return amountMax; }
    public void setAmountMax(BigDecimal amountMax) { this.amountMax = amountMax; }
    public Boolean getAmountMaxInclusive() { return amountMaxInclusive; }
    public void setAmountMaxInclusive(Boolean amountMaxInclusive) { this.amountMaxInclusive = amountMaxInclusive; }
    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }
    public String getCommissionRate() { return commissionRate; }
    public void setCommissionRate(String commissionRate) { this.commissionRate = commissionRate; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public void setSourceVersionId(Long sourceVersionId) { this.sourceVersionId = sourceVersionId; }
    public Long getSourceVersionItemId() { return sourceVersionItemId; }
    public void setSourceVersionItemId(Long sourceVersionItemId) { this.sourceVersionItemId = sourceVersionItemId; }
}
