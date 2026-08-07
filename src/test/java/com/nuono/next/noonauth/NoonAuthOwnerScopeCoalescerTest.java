package com.nuono.next.noonauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import org.junit.jupiter.api.Test;

class NoonAuthOwnerScopeCoalescerTest {
    @Test
    void activeManifestBlocksNewRecoveryBeforeInsert() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setIdentityKey("identity-hash");
        when(mapper.selectActiveOwnerScopeManifestForUpdate("identity-hash")).thenReturn(51L);

        assertThat(NoonAuthOwnerScopeCoalescer.coalesce(mapper, recovery)).isNull();

        verify(mapper, never()).coalesceActiveRecovery(recovery);
    }

    @Test
    void noManifestKeepsTheOriginalUniqueActiveRecoveryPath() {
        NoonAuthRecoveryMapper mapper = mock(NoonAuthRecoveryMapper.class);
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setIdentityKey("identity-hash");
        recovery.setId(91L);
        when(mapper.selectActiveOwnerScopeManifestForUpdate("identity-hash")).thenReturn(null);

        assertThat(NoonAuthOwnerScopeCoalescer.coalesce(mapper, recovery)).isEqualTo(91L);

        verify(mapper).coalesceActiveRecovery(recovery);
    }
}
