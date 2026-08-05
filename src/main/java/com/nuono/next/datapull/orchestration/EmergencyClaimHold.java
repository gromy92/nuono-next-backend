package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.SanitizedCode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Durable technical hold that blocks only new task claims until its UTC expiry. */
public final class EmergencyClaimHold {

    private String holdKey;
    private EmergencyClaimHoldScope holdScope;
    private OperationCode operationCode;
    private String scopeKey;
    private LocalDateTime blockedUntil;
    private String sanitizedReason;
    private Long version;
    private LocalDateTime updatedAt;

    public EmergencyClaimHold() {
        // MyBatis bean constructor.
    }

    public static EmergencyClaimHold global(
            LocalDateTime blockedUntilUtc,
            String sanitizedReason,
            LocalDateTime updatedAtUtc
    ) {
        return create(
                EmergencyClaimHoldScope.GLOBAL,
                null,
                null,
                blockedUntilUtc,
                sanitizedReason,
                updatedAtUtc
        );
    }

    public static EmergencyClaimHold operation(
            OperationCode operationCode,
            LocalDateTime blockedUntilUtc,
            String sanitizedReason,
            LocalDateTime updatedAtUtc
    ) {
        return create(
                EmergencyClaimHoldScope.OPERATION,
                operationCode,
                null,
                blockedUntilUtc,
                sanitizedReason,
                updatedAtUtc
        );
    }

    public static EmergencyClaimHold scope(
            OperationCode operationCode,
            String scopeKey,
            LocalDateTime blockedUntilUtc,
            String sanitizedReason,
            LocalDateTime updatedAtUtc
    ) {
        return create(
                EmergencyClaimHoldScope.SCOPE,
                operationCode,
                scopeKey,
                blockedUntilUtc,
                sanitizedReason,
                updatedAtUtc
        );
    }

    private static EmergencyClaimHold create(
            EmergencyClaimHoldScope holdScope,
            OperationCode operationCode,
            String scopeKey,
            LocalDateTime blockedUntilUtc,
            String sanitizedReason,
            LocalDateTime updatedAtUtc
    ) {
        EmergencyClaimHold hold = new EmergencyClaimHold();
        hold.holdScope = Objects.requireNonNull(holdScope, "holdScope");
        hold.operationCode = operationCode;
        hold.scopeKey = scopeKey;
        hold.holdKey = EmergencyClaimHoldKey.from(holdScope, operationCode, scopeKey);
        hold.blockedUntil = Objects.requireNonNull(blockedUntilUtc, "blockedUntilUtc");
        hold.sanitizedReason = SanitizedCode.require(sanitizedReason);
        hold.version = 0L;
        hold.updatedAt = Objects.requireNonNull(updatedAtUtc, "updatedAtUtc");
        hold.validateForPlacement();
        return hold;
    }

    public void validateForPlacement() {
        String expectedKey = EmergencyClaimHoldKey.from(holdScope, operationCode, scopeKey);
        if (!expectedKey.equals(holdKey)) {
            throw new IllegalArgumentException("emergency claim hold key does not match its target");
        }
        LocalDateTime updated = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!Objects.requireNonNull(blockedUntil, "blockedUntil").isAfter(updated)) {
            throw new IllegalArgumentException("blockedUntilUtc must be after updatedAtUtc");
        }
        SanitizedCode.require(sanitizedReason);
        if (version == null || version < 0L) {
            throw new IllegalArgumentException("emergency claim hold version must be non-negative");
        }
    }

    boolean blocks(OperationCode operation, String taskScopeKey, LocalDateTime nowUtc) {
        Objects.requireNonNull(operation, "operationCode");
        EmergencyClaimHoldKey.requireScopeKey(taskScopeKey);
        if (!blockedUntil.isAfter(Objects.requireNonNull(nowUtc, "nowUtc"))) {
            return false;
        }
        if (holdScope == EmergencyClaimHoldScope.GLOBAL) {
            return true;
        }
        if (operationCode != operation) {
            return false;
        }
        return holdScope == EmergencyClaimHoldScope.OPERATION || scopeKey.equals(taskScopeKey);
    }

    EmergencyClaimHold persistedCopy(
            LocalDateTime effectiveBlockedUntil,
            String effectiveReason,
            long effectiveVersion,
            LocalDateTime effectiveUpdatedAt
    ) {
        EmergencyClaimHold copy = create(
                holdScope,
                operationCode,
                scopeKey,
                effectiveBlockedUntil,
                effectiveReason,
                effectiveUpdatedAt
        );
        copy.version = effectiveVersion;
        return copy;
    }

    public String getHoldKey() { return holdKey; }
    public void setHoldKey(String holdKey) { this.holdKey = holdKey; }
    public EmergencyClaimHoldScope getHoldScope() { return holdScope; }
    public void setHoldScope(EmergencyClaimHoldScope holdScope) { this.holdScope = holdScope; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode operationCode) { this.operationCode = operationCode; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public LocalDateTime getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(LocalDateTime blockedUntil) { this.blockedUntil = blockedUntil; }
    public String getSanitizedReason() { return sanitizedReason; }
    public void setSanitizedReason(String sanitizedReason) { this.sanitizedReason = sanitizedReason; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
