package com.nuono.next.procurement.aliorder.datapull;

/** Authorization was revoked or replaced between the provider read and the fact transaction. */
public final class Ali1688Dp10AuthorizationUnavailableException extends RuntimeException {

    public Ali1688Dp10AuthorizationUnavailableException() {
        super("DP10_AUTHORIZATION_UNAVAILABLE");
    }
}
