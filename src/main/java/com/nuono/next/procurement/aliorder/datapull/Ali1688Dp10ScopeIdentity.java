package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.DataPullScopeKey;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import java.util.Objects;

/** Stable account and scope identities for one 1688 OpenAPI authorization. */
public final class Ali1688Dp10ScopeIdentity {

    public static final String PROVIDER_CHANNEL = "ALI1688_OPEN_API";
    public static final String SCOPE_NAMESPACE = "ALI1688_DP10";
    private static final String ACCOUNT_NAMESPACE = "ALI1688_ACCOUNT";

    private Ali1688Dp10ScopeIdentity() {
    }

    public static String accountKey(Ali1688HistoricalOrderAuthorizationRow authorization) {
        return DataPullScopeKey.from(
                ACCOUNT_NAMESPACE,
                ownerId(authorization),
                providerAccountId(authorization)
        );
    }

    public static String scopeKey(Ali1688HistoricalOrderAuthorizationRow authorization) {
        Long ownerUserId = Objects.requireNonNull(
                authorization.getOwnerUserId(),
                "authorization.ownerUserId"
        );
        if (ownerUserId <= 0L) {
            throw new IllegalArgumentException("authorization ownerUserId must be positive");
        }
        return DataPullScopeKey.from(
                SCOPE_NAMESPACE,
                String.valueOf(ownerUserId),
                providerAccountId(authorization)
        );
    }

    public static boolean isAccountKey(String accountKey) {
        return accountKey != null
                && accountKey.matches(ACCOUNT_NAMESPACE + "-[0-9a-f]{64}");
    }

    public static String providerAccountId(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        String identity = Objects.requireNonNull(
                authorization.getProviderAccountId(),
                "authorization.providerAccountId"
        );
        if (identity.isEmpty()
                || !identity.equals(identity.trim())
                || identity.length() > 120
                || identity.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("provider account identity is invalid");
        }
        return identity;
    }

    private static String ownerId(Ali1688HistoricalOrderAuthorizationRow authorization) {
        Long owner = Objects.requireNonNull(authorization.getOwnerUserId(), "authorization.ownerUserId");
        if (owner <= 0L) {
            throw new IllegalArgumentException("authorization ownerUserId must be positive");
        }
        return String.valueOf(owner);
    }
}
