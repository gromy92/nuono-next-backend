package com.nuono.next.versioning;

public class VersionPublishOperator {

    private final Long operatorUserId;
    private final String operatorRole;

    public VersionPublishOperator(Long operatorUserId, String operatorRole) {
        this.operatorUserId = operatorUserId;
        this.operatorRole = operatorRole;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getOperatorRole() {
        return operatorRole;
    }
}
