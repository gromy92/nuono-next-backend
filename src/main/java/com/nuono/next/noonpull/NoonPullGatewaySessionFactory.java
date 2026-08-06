package com.nuono.next.noonpull;

public interface NoonPullGatewaySessionFactory {
    NoonPullGatewaySession login(NoonPullStoreBinding binding);

    /**
     * Opens a persisted-cookie session without an authentication probe so the caller can make
     * exactly one externally visible request. The request itself remains authoritative for auth.
     */
    default NoonPullGatewaySession openOneShot(NoonPullStoreBinding binding) {
        return login(binding);
    }
}
