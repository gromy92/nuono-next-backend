package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationLifecycleRulePublishCommand {

    private List<Long> bossUserIds;
    private String message;

    public List<Long> getBossUserIds() { return bossUserIds; }
    public void setBossUserIds(List<Long> bossUserIds) { this.bossUserIds = bossUserIds; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
