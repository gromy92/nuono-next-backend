package com.nuono.next.datapull.checkpoint;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Durable, monotonic progress owned by one operation and one immutable pull scope. */
public final class DataPullScopeProgress {

    private static final int SCOPE_KEY_MAX_LENGTH = 96;
    private static final int BUSINESS_WINDOW_KEY_MAX_LENGTH = 160;

    private OperationCode operationCode;
    private String scopeKey;
    private boolean initialFullCompleted;
    private LocalDateTime officialModifiedHighWaterUtc;
    private String lastAppliedBusinessWindowKey;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DataPullScopeProgress() {
        // MyBatis bean constructor.
    }

    public static DataPullScopeProgress initial(
            OperationCode operationCode,
            String scopeKey,
            LocalDateTime nowUtc
    ) {
        DataPullScopeProgress progress = new DataPullScopeProgress();
        progress.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        progress.scopeKey = requireIdentity(scopeKey, "scopeKey", SCOPE_KEY_MAX_LENGTH);
        progress.initialFullCompleted = false;
        progress.version = 0L;
        progress.createdAt = Objects.requireNonNull(nowUtc, "nowUtc");
        progress.updatedAt = nowUtc;
        return progress;
    }

    public void validate() {
        Objects.requireNonNull(operationCode, "operationCode");
        requireIdentity(scopeKey, "scopeKey", SCOPE_KEY_MAX_LENGTH);
        if (version == null || version < 0L) {
            throw new IllegalStateException("scope progress version must be non-negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (lastAppliedBusinessWindowKey != null) {
            requireIdentity(
                    lastAppliedBusinessWindowKey,
                    "lastAppliedBusinessWindowKey",
                    BUSINESS_WINDOW_KEY_MAX_LENGTH
            );
        }
    }

    static String requireIdentity(String value, String field) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be a stable non-blank identity");
        }
        return value;
    }

    private static String requireIdentity(String value, String field, int maxLength) {
        String identity = requireIdentity(value, field);
        if (identity.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds its persistence column");
        }
        return identity;
    }

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public boolean isInitialFullCompleted() { return initialFullCompleted; }
    public void setInitialFullCompleted(boolean initialFullCompleted) {
        this.initialFullCompleted = initialFullCompleted;
    }
    public LocalDateTime getOfficialModifiedHighWaterUtc() { return officialModifiedHighWaterUtc; }
    public void setOfficialModifiedHighWaterUtc(LocalDateTime value) {
        this.officialModifiedHighWaterUtc = value;
    }
    public String getLastAppliedBusinessWindowKey() { return lastAppliedBusinessWindowKey; }
    public void setLastAppliedBusinessWindowKey(String value) {
        this.lastAppliedBusinessWindowKey = value;
    }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
