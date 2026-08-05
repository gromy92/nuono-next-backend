package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.Objects;

/** Stable identities required to query exact, account, and verified-egress holds. */
public final class DataPullBackoffIdentity {

    private final String providerChannel;
    private final String accountKey;
    private final OperationCode operationCode;
    private final String scopeKey;
    private final String egressKey;

    private DataPullBackoffIdentity(
            String providerChannel,
            String accountKey,
            OperationCode operationCode,
            String scopeKey,
            String egressKey
    ) {
        this.providerChannel = Objects.requireNonNull(providerChannel, "providerChannel");
        this.accountKey = Objects.requireNonNull(accountKey, "accountKey");
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey");
        this.egressKey = egressKey;
    }

    public static DataPullBackoffIdentity from(DataPullTask task) {
        return from(task, null);
    }

    public static DataPullBackoffIdentity from(
            DataPullTask task,
            String providerChannelOverride
    ) {
        DataPullTask nonNull = Objects.requireNonNull(task, "task");
        return new DataPullBackoffIdentity(
                providerChannelOverride == null
                        ? nonNull.getProviderChannel()
                        : providerChannelOverride,
                nonNull.getAccountKey(),
                nonNull.getOperationCode(),
                nonNull.getScopeKey(),
                nonNull.getEgressKey()
        );
    }

    public String requireEgressKey() {
        if (egressKey == null) {
            throw new IllegalStateException("EXIT hold lookup requires a verified stable egress identity");
        }
        return egressKey;
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
}
