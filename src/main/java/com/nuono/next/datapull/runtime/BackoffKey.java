package com.nuono.next.datapull.runtime;

import java.util.Objects;

/** Exact default isolation identity for a provider backoff. */
public final class BackoffKey {

    private final String providerChannel;
    private final String account;
    private final OperationCode operation;
    private final String scope;
    private final String egressKey;

    public BackoffKey(
            String providerChannel,
            String account,
            OperationCode operation,
            String scope
    ) {
        this(providerChannel, account, operation, scope, null);
    }

    public BackoffKey(
            String providerChannel,
            String account,
            OperationCode operation,
            String scope,
            String egressKey
    ) {
        this.providerChannel = requireStablePart(providerChannel, "providerChannel");
        this.account = requireStablePart(account, "account");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.scope = requireStablePart(scope, "scope");
        this.egressKey = egressKey == null ? null : requireStablePart(egressKey, "egressKey");
    }

    private static String requireStablePart(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a non-blank stable identity");
        }
        return nonNull;
    }

    String stableIdentity() {
        return lengthPrefixed(providerChannel)
                + lengthPrefixed(account)
                + lengthPrefixed(operation.name())
                + lengthPrefixed(scope)
                + lengthPrefixed(egressKey == null ? "<NO_EGRESS>" : egressKey);
    }

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value + "|";
    }

    public String getProviderChannel() {
        return providerChannel;
    }

    public String getAccount() {
        return account;
    }

    public OperationCode getOperation() {
        return operation;
    }

    public String getScope() {
        return scope;
    }

    public String getEgressKey() {
        return egressKey;
    }

    public String requireEgressKey() {
        if (egressKey == null) {
            throw new IllegalStateException("EXIT sharing requires a verified stable egress identity");
        }
        return egressKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BackoffKey)) {
            return false;
        }
        BackoffKey that = (BackoffKey) other;
        return providerChannel.equals(that.providerChannel)
                && account.equals(that.account)
                && operation == that.operation
                && scope.equals(that.scope)
                && Objects.equals(egressKey, that.egressKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerChannel, account, operation, scope, egressKey);
    }
}
