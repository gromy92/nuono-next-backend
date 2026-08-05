package com.nuono.next.datapull.runtime;

/**
 * Proven scope over which one risk-control signal may pause calls.
 *
 * <p>The absence of evidence is always {@link #EXACT}.</p>
 */
public enum RiskShareLevel {
    EXACT,
    ACCOUNT,
    EXIT
}
