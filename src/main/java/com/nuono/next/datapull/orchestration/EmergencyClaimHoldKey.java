package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.Objects;

/** Canonical, auditable identity for one emergency claim hold target. */
public final class EmergencyClaimHoldKey {

    private EmergencyClaimHoldKey() {
    }

    public static String from(
            EmergencyClaimHoldScope holdScope,
            OperationCode operationCode,
            String scopeKey
    ) {
        EmergencyClaimHoldScope target = Objects.requireNonNull(holdScope, "holdScope");
        switch (target) {
            case GLOBAL:
                requireAbsent(operationCode, scopeKey);
                return "GLOBAL";
            case OPERATION:
                requireOperation(operationCode);
                if (scopeKey != null) {
                    throw new IllegalArgumentException("OPERATION hold must not carry a scope key");
                }
                return "OPERATION:" + operationCode.name();
            case SCOPE:
                requireOperation(operationCode);
                return "SCOPE:" + operationCode.name() + ":" + requireScopeKey(scopeKey);
            default:
                throw new IllegalArgumentException("unsupported emergency claim hold scope");
        }
    }

    static String requireScopeKey(String scopeKey) {
        String value = Objects.requireNonNull(scopeKey, "scopeKey");
        if (value.isEmpty() || !value.equals(value.trim()) || value.length() > 96) {
            throw new IllegalArgumentException("scopeKey must fit the stable DP scope identity");
        }
        return value;
    }

    private static void requireOperation(OperationCode operationCode) {
        Objects.requireNonNull(operationCode, "operationCode");
    }

    private static void requireAbsent(OperationCode operationCode, String scopeKey) {
        if (operationCode != null || scopeKey != null) {
            throw new IllegalArgumentException("GLOBAL hold must not carry operation or scope identity");
        }
    }
}
