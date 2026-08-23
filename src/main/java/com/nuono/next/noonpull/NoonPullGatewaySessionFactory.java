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

    /**
     * Opens a read-only session after selecting an egress route that can reach the target.
     * The gateway may use a read-only authentication probe while rotating unusable routes.
     */
    default NoonPullGatewaySession openPinnedReadOnly(
            NoonPullStoreBinding binding,
            String targetHost,
            int targetPort
    ) {
        return login(binding);
    }
}
