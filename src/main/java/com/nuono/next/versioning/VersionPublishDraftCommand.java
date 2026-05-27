package com.nuono.next.versioning;

public class VersionPublishDraftCommand {

    private final String domainType;
    private final Long domainRefId;
    private final String versionNo;
    private final String scopeSummary;
    private final VersionPublishOperator operator;

    public VersionPublishDraftCommand(
            String domainType,
            Long domainRefId,
            String versionNo,
            String scopeSummary,
            VersionPublishOperator operator
    ) {
        this.domainType = domainType;
        this.domainRefId = domainRefId;
        this.versionNo = versionNo;
        this.scopeSummary = scopeSummary;
        this.operator = operator;
    }

    public String getDomainType() {
        return domainType;
    }

    public Long getDomainRefId() {
        return domainRefId;
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
}
