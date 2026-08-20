package com.nuono.next.noonauth;

import java.time.LocalDateTime;

public final class NoonAuthRecoveryStatusView {
    private final boolean enabled;
    private final String status;
    private final Long recoveryId;
    private final Integer generationNo;
    private final Integer sendAttemptCount;
    private final LocalDateTime nextAttemptAt;
    private final String failureCode;

    NoonAuthRecoveryStatusView(
            boolean enabled,
            String status,
            Long recoveryId,
            Integer generationNo,
            Integer sendAttemptCount,
            LocalDateTime nextAttemptAt,
            String failureCode
    ) {
        this.enabled = enabled;
        this.status = status;
        this.recoveryId = recoveryId;
        this.generationNo = generationNo;
        this.sendAttemptCount = sendAttemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.failureCode = failureCode;
    }

    public boolean isEnabled() { return enabled; }
    public String getStatus() { return status; }
    public Long getRecoveryId() { return recoveryId; }
    public Integer getGenerationNo() { return generationNo; }
    public Integer getSendAttemptCount() { return sendAttemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getFailureCode() { return failureCode; }
}
