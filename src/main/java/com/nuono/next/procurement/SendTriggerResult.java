package com.nuono.next.procurement;

class SendTriggerResult {
    public boolean ok;
    public String triggerType;
    public String locator;
    public String sendControlLocator;
    public String beforeText;
    public String failureCode;
    public String failureMessage;

    static SendTriggerResult failure(String failureCode, String failureMessage) {
        SendTriggerResult result = new SendTriggerResult();
        result.ok = false;
        result.failureCode = failureCode;
        result.failureMessage = failureMessage;
        return result;
    }
}
