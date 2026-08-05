package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.util.Objects;

/** Immutable business identity used to schedule and isolate one DP scope. */
public final class DataPullScope {

    private static final int ACCOUNT_KEY_MAX_LENGTH = 160;
    private static final int EGRESS_KEY_MAX_LENGTH = 160;
    private static final int NAMESPACE_MAX_LENGTH = 32;
    private static final int SCOPE_KEY_MAX_LENGTH = 96;

    private final String namespace;
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final String accountKey;
    private final String egressKey;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String stableScopeKey;

    static DataPullScope fromTaskSnapshot(DataPullTask task) {
        DataPullTask nonNull = Objects.requireNonNull(task, "task");
        Long ownerUserId = Objects.requireNonNull(nonNull.getOwnerUserId(), "task.ownerUserId");
        return new DataPullScope(
                namespaceFrom(nonNull.getScopeKey()),
                ownerUserId,
                nonNull.getLogicalStoreId(),
                nonNull.getAccountKey(),
                nonNull.getEgressKey(),
                nonNull.getProjectCode(),
                nonNull.getStoreCode(),
                nonNull.getSiteCode(),
                nonNull.getScopeKey()
        );
    }

    public DataPullScope(
            long ownerUserId,
            Long logicalStoreId,
            String accountKey,
            String projectCode,
            String storeCode,
            String siteCode,
            String stableScopeKey
    ) {
        this(
                namespaceFrom(stableScopeKey),
                ownerUserId,
                logicalStoreId,
                accountKey,
                null,
                projectCode,
                storeCode,
                siteCode,
                stableScopeKey
        );
    }

    public DataPullScope(
            long ownerUserId,
            Long logicalStoreId,
            String accountKey,
            String egressKey,
            String projectCode,
            String storeCode,
            String siteCode,
            String stableScopeKey
    ) {
        this(
                namespaceFrom(stableScopeKey),
                ownerUserId,
                logicalStoreId,
                accountKey,
                egressKey,
                projectCode,
                storeCode,
                siteCode,
                stableScopeKey
        );
    }

    public DataPullScope(
            String namespace,
            long ownerUserId,
            Long logicalStoreId,
            String accountKey,
            String projectCode,
            String storeCode,
            String siteCode,
            String stableScopeKey
    ) {
        this(
                namespace,
                ownerUserId,
                logicalStoreId,
                accountKey,
                null,
                projectCode,
                storeCode,
                siteCode,
                stableScopeKey
        );
    }

    public DataPullScope(
            String namespace,
            long ownerUserId,
            Long logicalStoreId,
            String accountKey,
            String egressKey,
            String projectCode,
            String storeCode,
            String siteCode,
            String stableScopeKey
    ) {
        if (ownerUserId <= 0L) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (logicalStoreId != null && logicalStoreId <= 0L) {
            throw new IllegalArgumentException("logicalStoreId must be positive when present");
        }
        this.namespace = requireText(namespace, "namespace", NAMESPACE_MAX_LENGTH);
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.accountKey = requireText(accountKey, "accountKey", ACCOUNT_KEY_MAX_LENGTH);
        this.egressKey = optionalText(egressKey, "egressKey", EGRESS_KEY_MAX_LENGTH);
        this.projectCode = optionalText(projectCode, "projectCode", 100);
        this.storeCode = optionalText(storeCode, "storeCode", 100);
        this.siteCode = optionalText(siteCode, "siteCode", 20);
        this.stableScopeKey = requireText(stableScopeKey, "stableScopeKey", SCOPE_KEY_MAX_LENGTH);
    }

    private static String requireText(String value, String name) {
        return requireText(value, name, Integer.MAX_VALUE);
    }

    private static String requireText(String value, String name, int maxLength) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())
                || nonNull.length() > maxLength || nonNull.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must fit its stable identity column");
        }
        return nonNull;
    }

    private static String optionalText(String value) {
        return optionalText(value, "optional scope identity", Integer.MAX_VALUE);
    }

    private static String optionalText(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty() || !value.equals(value.trim())
                || value.length() > maxLength || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must fit its stable identity column");
        }
        return value;
    }

    private static String namespaceFrom(String stableScopeKey) {
        String scope = requireText(stableScopeKey, "stableScopeKey", SCOPE_KEY_MAX_LENGTH);
        int separator = scope.lastIndexOf('-');
        String inferred = separator > 0
                && scope.substring(separator + 1).matches("[0-9a-f]{64}")
                ? scope.substring(0, separator)
                : "LEGACY";
        return requireText(inferred, "namespace", NAMESPACE_MAX_LENGTH);
    }

    public String getNamespace() {
        return namespace;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getEgressKey() {
        return egressKey;
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

    public String getStableScopeKey() {
        return stableScopeKey;
    }
}
