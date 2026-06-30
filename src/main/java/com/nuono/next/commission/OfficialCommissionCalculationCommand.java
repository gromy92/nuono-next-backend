package com.nuono.next.commission;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;

public class OfficialCommissionCalculationCommand {
    private Long ownerUserId;
    private String storeCode;
    private String skuId;
    private String site;
    private BigDecimal salePrice;
    private LocalDate calculationDate;
    private Long operatorUserId;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSkuId() { return skuId; }
    public void setSkuId(String skuId) { this.skuId = skuId == null ? null : skuId.trim(); }
    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public LocalDate getCalculationDate() { return calculationDate; }
    public void setCalculationDate(LocalDate calculationDate) { this.calculationDate = calculationDate; }
    @JsonIgnore
    public Long getOperatorUserId() { return operatorUserId; }
    @JsonIgnore
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }

    @JsonIgnore
    public boolean hasSkuId() {
        return skuId != null && !skuId.trim().isEmpty();
    }
}
