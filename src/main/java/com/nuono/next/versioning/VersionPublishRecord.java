package com.nuono.next.versioning;

import java.time.LocalDateTime;

public class VersionPublishRecord {

    private final Long id;
    private final String domainType;
    private final Long domainRefId;
    private final String versionNo;
    private final VersionPublishStatus status;
    private final String scopeSummary;
    private final Long previousVersionId;
    private final Long publishedBy;
    private final LocalDateTime publishedAt;
    private final String changeSummary;
    private final Long createdBy;
    private final Long updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public VersionPublishRecord(
            Long id,
            String domainType,
            Long domainRefId,
            String versionNo,
            VersionPublishStatus status,
            String scopeSummary,
            Long previousVersionId,
            Long publishedBy,
            LocalDateTime publishedAt,
            String changeSummary,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.domainType = domainType;
        this.domainRefId = domainRefId;
        this.versionNo = versionNo;
        this.status = status;
        this.scopeSummary = scopeSummary;
        this.previousVersionId = previousVersionId;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
        this.changeSummary = changeSummary;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public VersionPublishRecord withDraftEdit(String nextScopeSummary, Long nextUpdatedBy, LocalDateTime nextUpdatedAt) {
        return new VersionPublishRecord(
                id,
                domainType,
                domainRefId,
                versionNo,
                status,
                nextScopeSummary,
                previousVersionId,
                publishedBy,
                publishedAt,
                changeSummary,
                createdBy,
                nextUpdatedBy,
                createdAt,
                nextUpdatedAt
        );
    }

    public VersionPublishRecord withPublished(
            Long nextPublishedBy,
            LocalDateTime nextPublishedAt,
            String nextChangeSummary,
            Long nextPreviousVersionId
    ) {
        return new VersionPublishRecord(
                id,
                domainType,
                domainRefId,
                versionNo,
                VersionPublishStatus.PUBLISHED,
                scopeSummary,
                nextPreviousVersionId,
                nextPublishedBy,
                nextPublishedAt,
                nextChangeSummary,
                createdBy,
                nextPublishedBy,
                createdAt,
                nextPublishedAt
        );
    }

    public VersionPublishRecord withHistorical(Long nextUpdatedBy, LocalDateTime nextUpdatedAt) {
        return withStatus(VersionPublishStatus.HISTORICAL, nextUpdatedBy, nextUpdatedAt, changeSummary);
    }

    public VersionPublishRecord withDisabled(Long nextUpdatedBy, LocalDateTime nextUpdatedAt, String nextChangeSummary) {
        return withStatus(VersionPublishStatus.DISABLED, nextUpdatedBy, nextUpdatedAt, nextChangeSummary);
    }

    private VersionPublishRecord withStatus(
            VersionPublishStatus nextStatus,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt,
            String nextChangeSummary
    ) {
        return new VersionPublishRecord(
                id,
                domainType,
                domainRefId,
                versionNo,
                nextStatus,
                scopeSummary,
                previousVersionId,
                publishedBy,
                publishedAt,
                nextChangeSummary,
                createdBy,
                nextUpdatedBy,
                createdAt,
                nextUpdatedAt
        );
    }

    public Long getId() {
        return id;
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

    public VersionPublishStatus getStatus() {
        return status;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public Long getPreviousVersionId() {
        return previousVersionId;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
