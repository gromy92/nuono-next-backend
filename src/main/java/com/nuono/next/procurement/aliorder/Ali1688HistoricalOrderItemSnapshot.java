package com.nuono.next.procurement.aliorder;

/** Provider-neutral order-line payload. */
public class Ali1688HistoricalOrderItemSnapshot {
    private String providerSubOrderId;
    private String providerItemId;
    private String offerId;
    private String skuId;
    private String title;
    private String skuText;
    private String modelText;
    private String productCode;
    private String singleProductCode;
    private Integer quantity;
    private String unit;
    private String unitPriceText;
    private String amountText;
    private String imageUrl;
    private String logisticsCompany;
    private String trackingNo;
    private String rawSnapshotJson;

    public String getProviderSubOrderId() { return providerSubOrderId; }
    public void setProviderSubOrderId(String value) { providerSubOrderId = value; }
    public String getProviderItemId() { return providerItemId; }
    public void setProviderItemId(String value) { providerItemId = value; }
    public String getOfferId() { return offerId; }
    public void setOfferId(String value) { offerId = value; }
    public String getSkuId() { return skuId; }
    public void setSkuId(String value) { skuId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getSkuText() { return skuText; }
    public void setSkuText(String value) { skuText = value; }
    public String getModelText() { return modelText; }
    public void setModelText(String value) { modelText = value; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String value) { productCode = value; }
    public String getSingleProductCode() { return singleProductCode; }
    public void setSingleProductCode(String value) { singleProductCode = value; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer value) { quantity = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public String getUnitPriceText() { return unitPriceText; }
    public void setUnitPriceText(String value) { unitPriceText = value; }
    public String getAmountText() { return amountText; }
    public void setAmountText(String value) { amountText = value; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String value) { imageUrl = value; }
    public String getLogisticsCompany() { return logisticsCompany; }
    public void setLogisticsCompany(String value) { logisticsCompany = value; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String value) { trackingNo = value; }
    public String getRawSnapshotJson() { return rawSnapshotJson; }
    public void setRawSnapshotJson(String value) { rawSnapshotJson = value; }
}
