package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.time.LocalDateTime;

/** Persistence command for one canonical backoff hold. */
public final class DataPullBackoffHoldRow {

    private String holdKey;
    private RiskShareLevel shareLevel;
    private String providerChannel;
    private String accountKey;
    private OperationCode operationCode;
    private String scopeKey;
    private String egressKey;
    private LocalDateTime blockedUntil;
    private String sanitizedCode;
    private LocalDateTime observedAt;

    public static DataPullBackoffHoldRow from(
            RiskShareLevel shareLevel,
            DataPullBackoffIdentity identity,
            LocalDateTime blockedUntil,
            String sanitizedCode,
            LocalDateTime observedAt
    ) {
        DataPullBackoffHoldRow row = new DataPullBackoffHoldRow();
        row.holdKey = DataPullBackoffHoldKey.from(shareLevel, identity);
        row.shareLevel = shareLevel;
        row.providerChannel = identity.getProviderChannel();
        row.accountKey = identity.getAccountKey();
        row.operationCode = identity.getOperationCode();
        row.scopeKey = identity.getScopeKey();
        row.egressKey = identity.getEgressKey();
        row.blockedUntil = blockedUntil;
        row.sanitizedCode = sanitizedCode;
        row.observedAt = observedAt;
        return row;
    }

    public String getHoldKey() {
        return holdKey;
    }

    public RiskShareLevel getShareLevel() {
        return shareLevel;
    }

    public String getProviderChannel() {
        return providerChannel;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public OperationCode getOperationCode() {
        return operationCode;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public String getEgressKey() {
        return egressKey;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }
}
