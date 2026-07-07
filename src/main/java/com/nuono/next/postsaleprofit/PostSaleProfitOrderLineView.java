package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;

public class PostSaleProfitOrderLineView {
    private String id;
    private String orderNo;
    private String itemNr;
    private String orderTime;
    private String partnerSku;
    private String sku;
    private BigDecimal attributedQuantity;
    private String attributionMethod;
    private boolean locked;
    private String manualReason;
    private BigDecimal netProceedsLcy;
    private BigDecimal referralFeeLcy;
    private BigDecimal fulfillmentFeeLcy;
    private BigDecimal otherFeeNetLcy;
    private String currency;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getItemNr() { return itemNr; }
    public void setItemNr(String itemNr) { this.itemNr = itemNr; }
    public String getOrderTime() { return orderTime; }
    public void setOrderTime(String orderTime) { this.orderTime = orderTime; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getAttributedQuantity() { return attributedQuantity; }
    public void setAttributedQuantity(BigDecimal attributedQuantity) { this.attributedQuantity = attributedQuantity; }
    public String getAttributionMethod() { return attributionMethod; }
    public void setAttributionMethod(String attributionMethod) { this.attributionMethod = attributionMethod; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public String getManualReason() { return manualReason; }
    public void setManualReason(String manualReason) { this.manualReason = manualReason; }
    public BigDecimal getNetProceedsLcy() { return netProceedsLcy; }
    public void setNetProceedsLcy(BigDecimal netProceedsLcy) { this.netProceedsLcy = netProceedsLcy; }
    public BigDecimal getReferralFeeLcy() { return referralFeeLcy; }
    public void setReferralFeeLcy(BigDecimal referralFeeLcy) { this.referralFeeLcy = referralFeeLcy; }
    public BigDecimal getFulfillmentFeeLcy() { return fulfillmentFeeLcy; }
    public void setFulfillmentFeeLcy(BigDecimal fulfillmentFeeLcy) { this.fulfillmentFeeLcy = fulfillmentFeeLcy; }
    public BigDecimal getOtherFeeNetLcy() { return otherFeeNetLcy; }
    public void setOtherFeeNetLcy(BigDecimal otherFeeNetLcy) { this.otherFeeNetLcy = otherFeeNetLcy; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
