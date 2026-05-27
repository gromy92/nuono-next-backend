package com.nuono.next.versioning;

public class VersionPublishCopyCommand {

    private final Long sourceRecordId;
    private final Long targetDomainRefId;
    private final String versionNo;
    private final String scopeSummary;
    private final VersionPublishOperator operator;
    private final String message;

    public VersionPublishCopyCommand(
            Long sourceRecordId,
            String versionNo,
            String scopeSummary,
            VersionPublishOperator operator,
            String message
    ) {
        this(sourceRecordId, null, versionNo, scopeSummary, operator, message);
    }

    public VersionPublishCopyCommand(
            Long sourceRecordId,
            Long targetDomainRefId,
            String versionNo,
            String scopeSummary,
            VersionPublishOperator operator,
            String message
    ) {
        this.sourceRecordId = sourceRecordId;
        this.targetDomainRefId = targetDomainRefId;
        this.versionNo = versionNo;
        this.scopeSummary = scopeSummary;
        this.operator = operator;
        this.message = message;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public Long getTargetDomainRefId() {
        return targetDomainRefId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public VersionPublishOperator getOperator() {
        return operator;
    }

    public String getMessage() {
        return message;
    }
}
