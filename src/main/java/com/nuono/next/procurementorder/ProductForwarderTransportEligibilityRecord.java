package com.nuono.next.procurementorder;

import java.time.LocalDate;

public class ProductForwarderTransportEligibilityRecord {
    public Long id;
    public Long ownerUserId;
    public Long productMasterId;
    public Long productVariantId;
    public Long logicalStoreId;
    public String sourceStoreCode;
    public String partnerSku;
    public String siteCode;
    public String forwarderCode;
    public String transportMode;
    public String eligibilityStatus;
    public LocalDate effectiveFrom;
    public LocalDate effectiveTo;
    public Integer version;
    public String createdAt;
    public String updatedAt;
}
