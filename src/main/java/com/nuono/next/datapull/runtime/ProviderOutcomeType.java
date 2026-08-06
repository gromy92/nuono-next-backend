package com.nuono.next.datapull.runtime;

/** Exhaustive classifications returned by a provider Adapter. */
public enum ProviderOutcomeType {
    SUCCESS,
    NOT_FOUND,
    RISK_CONTROL,
    TRANSIENT,
    AUTH_REQUIRED,
    CONTRACT_ERROR,
    UNKNOWN_OUTCOME
}
