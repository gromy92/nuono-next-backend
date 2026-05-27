package com.nuono.next.sales;

public class NoonSalesCsvImportCommand {

    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final String sourceFilename;
    private final String csv;

    public NoonSalesCsvImportCommand(
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            String sourceFilename,
            String csv
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.sourceFilename = sourceFilename;
        this.csv = csv;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getCsv() {
        return csv;
    }
}
