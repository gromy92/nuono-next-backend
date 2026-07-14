package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;

public class SalesFactQuery {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String partnerSku;
    private final String sku;
    private final String searchKeyword;
    private final String brand;
    private final String productFulltype;
    private final String dataQualityCode;
    private final List<String> partnerSkuList;

    public SalesFactQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String partnerSku,
            String sku
    ) {
        this(ownerUserId, storeCode, siteCode, dateFrom, dateTo, partnerSku, sku, null);
    }

    public SalesFactQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String partnerSku,
            String sku,
            String searchKeyword
    ) {
        this(ownerUserId, storeCode, siteCode, dateFrom, dateTo, partnerSku, sku, searchKeyword, null, null, null);
    }

    public SalesFactQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String partnerSku,
            String sku,
            String searchKeyword,
            String brand,
            String productFulltype,
            String dataQualityCode
    ) {
        this(ownerUserId, storeCode, siteCode, dateFrom, dateTo, partnerSku, sku, searchKeyword, brand, productFulltype, dataQualityCode, List.of());
    }

    public SalesFactQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String partnerSku,
            String sku,
            String searchKeyword,
            String brand,
            String productFulltype,
            String dataQualityCode,
            List<String> partnerSkuList
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.searchKeyword = searchKeyword;
        this.brand = brand;
        this.productFulltype = productFulltype;
        this.dataQualityCode = dataQualityCode;
        this.partnerSkuList = partnerSkuList == null ? List.of() : List.copyOf(partnerSkuList);
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public String getBrand() {
        return brand;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public String getDataQualityCode() {
        return dataQualityCode;
    }

    public List<String> getPartnerSkuList() {
        return partnerSkuList;
    }

    public SalesFactQuery withSku(String sku) {
        return new SalesFactQuery(
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                partnerSku,
                sku,
                searchKeyword,
                brand,
                productFulltype,
                dataQualityCode,
                partnerSkuList
        );
    }

}
