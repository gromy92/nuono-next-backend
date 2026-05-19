package com.nuono.next.procurement;

class ServiceEntryResult {
    public boolean ok;
    public String locator;
    public String matchedText;
    public String failureCode;
    public String failureMessage;

    static ServiceEntryResult failure(String failureCode, String failureMessage) {
        ServiceEntryResult result = new ServiceEntryResult();
        result.ok = false;
        result.failureCode = failureCode;
        result.failureMessage = failureMessage;
        return result;
    }
}
