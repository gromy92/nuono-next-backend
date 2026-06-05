package com.nuono.next.procurement.aliorder;

public class Ali1688HistoricalOrderLogisticsRow {
    private Long id;
    private Long orderId;
    private Long itemId;
    private String logisticsNaturalKey;
    private String logisticsCompany;
    private String trackingNo;
    private String rawSnapshotJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getLogisticsNaturalKey() {
        return logisticsNaturalKey;
    }

    public void setLogisticsNaturalKey(String logisticsNaturalKey) {
        this.logisticsNaturalKey = logisticsNaturalKey;
    }

    public String getLogisticsCompany() {
        return logisticsCompany;
    }

    public void setLogisticsCompany(String logisticsCompany) {
        this.logisticsCompany = logisticsCompany;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getRawSnapshotJson() {
        return rawSnapshotJson;
    }

    public void setRawSnapshotJson(String rawSnapshotJson) {
        this.rawSnapshotJson = rawSnapshotJson;
    }
}
