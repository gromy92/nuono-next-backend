package com.nuono.next.versioning;

import java.time.LocalDateTime;

public class VersionPublishAuditLog {

    private final Long id;
    private final Long publishRecordId;
    private final String domainType;
    private final Long domainRefId;
    private final VersionPublishAction action;
    private final Long operatorUserId;
    private final String operatorRole;
    private final String beforeSnapshot;
    private final String afterSnapshot;
    private final String message;
    private final LocalDateTime createdAt;

    public VersionPublishAuditLog(
            Long id,
            Long publishRecordId,
            String domainType,
            Long domainRefId,
            VersionPublishAction action,
            Long operatorUserId,
            String operatorRole,
            String beforeSnapshot,
            String afterSnapshot,
            String message,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.publishRecordId = publishRecordId;
        this.domainType = domainType;
        this.domainRefId = domainRefId;
        this.action = action;
        this.operatorUserId = operatorUserId;
        this.operatorRole = operatorRole;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPublishRecordId() {
        return publishRecordId;
    }

    public String getDomainType() {
        return domainType;
    }

    public Long getDomainRefId() {
        return domainRefId;
    }

    public VersionPublishAction getAction() {
        return action;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
