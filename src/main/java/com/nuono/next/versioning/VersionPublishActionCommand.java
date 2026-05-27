package com.nuono.next.versioning;

public class VersionPublishActionCommand {

    private final Long recordId;
    private final VersionPublishOperator operator;
    private final String afterSnapshot;
    private final String message;

    public VersionPublishActionCommand(
            Long recordId,
            VersionPublishOperator operator,
            String afterSnapshot,
            String message
    ) {
        this.recordId = recordId;
        this.operator = operator;
        this.afterSnapshot = afterSnapshot;
        this.message = message;
    }

    public Long getRecordId() {
        return recordId;
    }

    public VersionPublishOperator getOperator() {
        return operator;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getMessage() {
        return message;
    }
}
