package com.nuono.next.versioning;

public class VersionPublishDraftUpdateCommand {

    private final Long recordId;
    private final String scopeSummary;
    private final VersionPublishOperator operator;
    private final String beforeSnapshot;
    private final String afterSnapshot;
    private final String message;

    public VersionPublishDraftUpdateCommand(
            Long recordId,
            String scopeSummary,
            VersionPublishOperator operator,
            String beforeSnapshot,
            String afterSnapshot,
            String message
    ) {
        this.recordId = recordId;
        this.scopeSummary = scopeSummary;
        this.operator = operator;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.message = message;
    }

    public Long getRecordId() {
        return recordId;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public VersionPublishOperator getOperator() {
        return operator;
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
}
