package com.nuono.next.noonauth;

import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Business-neutral request for joining a Project to the shared Noon authorization queue.
 *
 * <p>The durable source identity is {@code sourceDomain + sourceTaskId}. A source task must also
 * provide a checkpoint and an explicit resume policy so the recovery worker never guesses whether
 * replay is safe.</p>
 */
public final class NoonAuthWaitRequest {
    public static final String STORE_BINDING_DOMAIN = "STORE_BINDING";
    public static final String IDENTITY_BATCH_DOMAIN = "ACCOUNT_SESSION_REFRESH";

    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String sourceDomain;
    private final Long sourceTaskId;
    private final String checkpoint;
    private final NoonAuthResumePolicy resumePolicy;
    private final LocalDateTime sourceStartedAt;

    private NoonAuthWaitRequest(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String sourceDomain,
            Long sourceTaskId,
            String checkpoint,
            NoonAuthResumePolicy resumePolicy,
            LocalDateTime sourceStartedAt
    ) {
        this.ownerUserId = ownerUserId;
        this.projectCode = normalize(projectCode);
        this.storeCode = normalize(storeCode);
        this.siteCode = normalize(siteCode);
        this.sourceDomain = normalize(sourceDomain);
        this.sourceTaskId = sourceTaskId;
        this.checkpoint = normalize(checkpoint);
        this.resumePolicy = resumePolicy;
        this.sourceStartedAt = sourceStartedAt;
        validate();
    }

    public static NoonAuthWaitRequest binding(
            Long ownerUserId,
            String projectCode,
            String storeCode
    ) {
        return new NoonAuthWaitRequest(
                ownerUserId,
                projectCode,
                storeCode,
                null,
                STORE_BINDING_DOMAIN,
                null,
                "PROJECT_BINDING",
                NoonAuthResumePolicy.NONE,
                null
        );
    }

    static NoonAuthWaitRequest identityBatch(
            Long ownerUserId,
            String projectCode,
            String storeCode
    ) {
        return new NoonAuthWaitRequest(
                ownerUserId,
                projectCode,
                storeCode,
                null,
                IDENTITY_BATCH_DOMAIN,
                null,
                "IDENTITY_BATCH",
                NoonAuthResumePolicy.NONE,
                null
        );
    }

    public static NoonAuthWaitRequest task(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String sourceDomain,
            Long sourceTaskId,
            String checkpoint,
            NoonAuthResumePolicy resumePolicy
    ) {
        return task(
                ownerUserId,
                projectCode,
                storeCode,
                siteCode,
                sourceDomain,
                sourceTaskId,
                checkpoint,
                resumePolicy,
                null
        );
    }

    public static NoonAuthWaitRequest task(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String sourceDomain,
            Long sourceTaskId,
            String checkpoint,
            NoonAuthResumePolicy resumePolicy,
            LocalDateTime sourceStartedAt
    ) {
        return new NoonAuthWaitRequest(
                ownerUserId,
                projectCode,
                storeCode,
                siteCode,
                sourceDomain,
                sourceTaskId,
                checkpoint,
                resumePolicy,
                sourceStartedAt
        );
    }

    private void validate() {
        if (ownerUserId == null || !StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("Noon auth wait request requires owner and storeCode.");
        }
        if (sourceTaskId == null) {
            if ((!STORE_BINDING_DOMAIN.equals(sourceDomain)
                    && !IDENTITY_BATCH_DOMAIN.equals(sourceDomain))
                    || resumePolicy != NoonAuthResumePolicy.NONE) {
                throw new IllegalArgumentException(
                        "A source-less Noon auth wait request must be a STORE_BINDING request."
                );
            }
            return;
        }
        if (!StringUtils.hasText(sourceDomain)
                || !StringUtils.hasText(checkpoint)
                || resumePolicy == null
                || resumePolicy == NoonAuthResumePolicy.NONE) {
            throw new IllegalArgumentException(
                    "A task-backed Noon auth wait request requires domain, checkpoint and resume policy."
            );
        }
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public Long getSourceTaskId() {
        return sourceTaskId;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public NoonAuthResumePolicy getResumePolicy() {
        return resumePolicy;
    }

    public LocalDateTime getSourceStartedAt() {
        return sourceStartedAt;
    }

    public boolean hasSourceTask() {
        return sourceTaskId != null;
    }

    boolean isIdentityBatch() {
        return IDENTITY_BATCH_DOMAIN.equals(sourceDomain);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NoonAuthWaitRequest)) {
            return false;
        }
        NoonAuthWaitRequest that = (NoonAuthWaitRequest) other;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(projectCode, that.projectCode)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode)
                && Objects.equals(sourceDomain, that.sourceDomain)
                && Objects.equals(sourceTaskId, that.sourceTaskId)
                && Objects.equals(checkpoint, that.checkpoint)
                && resumePolicy == that.resumePolicy
                && Objects.equals(sourceStartedAt, that.sourceStartedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ownerUserId,
                projectCode,
                storeCode,
                siteCode,
                sourceDomain,
                sourceTaskId,
                checkpoint,
                resumePolicy,
                sourceStartedAt
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
