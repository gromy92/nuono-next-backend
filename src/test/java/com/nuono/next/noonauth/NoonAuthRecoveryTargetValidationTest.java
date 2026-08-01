package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullRepository;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryTargetValidationTest {
    private NoonAuthRecoveryRepository recoveryRepository;
    private NoonPullRepository pullRepository;
    private StoreSyncMapper storeSyncMapper;
    private NoonAuthRecoveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        pullRepository = mock(NoonPullRepository.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setTrustedSenderDomains("noon.com");
        coordinator = new NoonAuthRecoveryCoordinator(
                recoveryRepository,
                pullRepository,
                storeSyncMapper,
                properties,
                "shared@example.com",
                "imap-secret",
                Clock.fixed(Instant.parse("2026-08-01T04:00:00Z"), ZoneOffset.UTC)
        );
        when(recoveryRepository.selectProjectBindingFingerprint(anyLong(), anyString()))
                .thenReturn("binding-fingerprint");
    }

    @Test
    void refusesProjectCodeFallbackAndIncompleteStoreMappingBeforeAllocatingRecovery() {
        assertTrue(coordinator.enqueueProject(307L, "PRJ1", "PRJ1").isEmpty());

        StoreSyncStoreRecord incomplete = new StoreSyncStoreRecord();
        incomplete.setProjectCode("PRJ1");
        incomplete.setStoreCode("STORE-1");
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1")).thenReturn(incomplete);

        assertTrue(coordinator.enqueueProject(307L, "PRJ1", "STORE-1").isEmpty());
        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }

    @Test
    void refusesPullRecoveryWithoutSiteBeforeAllocatingRecovery() {
        NoonPullTaskRecord incomplete = new NoonPullTaskRecord();
        incomplete.setId(99L);
        incomplete.setOwnerUserId(307L);
        incomplete.setStoreCode("STORE-99");
        incomplete.setSiteCode(null);
        incomplete.setDataDomain(NoonPullDataDomain.ORDER);
        incomplete.setStatus(NoonPullTaskStatus.RUNNING);

        assertTrue(coordinator.blockAndEnqueue(
                incomplete,
                "auth_required: cookie expired"
        ).isEmpty());
        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }
}
