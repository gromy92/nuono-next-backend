package com.nuono.next.noonlog;

import java.time.LocalDateTime;

public class NoonHttpCallLogRecord {

    public Long id;
    public LocalDateTime occurredAt;
    public String sourceModule;
    public String operation;
    public Long ownerUserId;
    public String storeCode;
    public String siteCode;
    public String projectCode;
    public String partnerId;
    public String businessType;
    public String businessId;
    public String businessRef;
    public String httpMethod;
    public String host;
    public String path;
    public String queryHash;
    public String requestSummaryJson;
    public String requestHash;
    public Integer responseStatusCode;
    public String responseSummaryJson;
    public String responseHash;
    public Long elapsedMs;
    public String status;
    public String failureType;
    public String errorMessage;
}
