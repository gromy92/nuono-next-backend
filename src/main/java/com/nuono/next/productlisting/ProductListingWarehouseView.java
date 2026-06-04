package com.nuono.next.productlisting;

public class ProductListingWarehouseView {
    private String warehouseCode;
    private String warehouseName;
    private String idPartnerWarehouse;
    private String countryCode;
    private Integer idProcessingTime;

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getIdPartnerWarehouse() {
        return idPartnerWarehouse;
    }

    public void setIdPartnerWarehouse(String idPartnerWarehouse) {
        this.idPartnerWarehouse = idPartnerWarehouse;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public Integer getIdProcessingTime() {
        return idProcessingTime;
    }

    public void setIdProcessingTime(Integer idProcessingTime) {
        this.idProcessingTime = idProcessingTime;
    }
}
