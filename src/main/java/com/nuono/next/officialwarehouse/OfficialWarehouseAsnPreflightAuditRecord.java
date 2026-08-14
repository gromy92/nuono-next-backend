package com.nuono.next.officialwarehouse;

/** Immutable failure evidence for a create attempt rejected before an ASN is persisted. */
public class OfficialWarehouseAsnPreflightAuditRecord {
    public Long id;
    public Long ownerUserId;
    public Long operatorUserId;
    public Long logicalStoreId;
    public String projectCode;
    public String storeCode;
    public String siteCode;
    public String partnerId;
    public Long attemptAsnId;
    public String attemptRef;
    public String operation;
    public Integer requestLineCount;
    public Integer invalidLineCount;
    public String failureCode;
    public String failureMessage;
    public String reasonSummary;
    public String invalidLinesJson;
}
