package com.nuono.next.noonauth;

final class NoonAuthOwnerScopeGuard {
    private NoonAuthOwnerScopeGuard() {
    }

    static void requireValid(
            NoonAuthRecoveryRepository repository,
            NoonAuthIdentityRecoveryRecord candidate
    ) {
        if (candidate.getScopeOwnerUserId() != null
                && !repository.isOwnerScopeManifestValid(candidate.getId())) {
            throw new IllegalStateException(
                    "Noon owner-scoped auth recovery manifest drifted before lease claim."
            );
        }
    }
}
