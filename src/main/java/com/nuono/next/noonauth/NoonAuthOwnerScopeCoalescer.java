package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;

final class NoonAuthOwnerScopeCoalescer {
    private NoonAuthOwnerScopeCoalescer() {
    }

    static Long coalesce(
            NoonAuthRecoveryMapper mapper,
            NoonAuthIdentityRecoveryRecord recovery
    ) {
        if (mapper.selectActiveOwnerScopeManifestForUpdate(recovery.getIdentityKey()) != null) {
            return null;
        }
        mapper.coalesceActiveRecovery(recovery);
        return recovery.getId();
    }
}
