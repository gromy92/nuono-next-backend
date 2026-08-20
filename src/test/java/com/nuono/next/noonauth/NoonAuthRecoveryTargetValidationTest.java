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
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryTargetValidationTest {
    private NoonAuthRecoveryRepository recoveryRepository;
    private StoreSyncMapper storeSyncMapper;
    private NoonAuthRecoveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setAllProjectsEnabled(true);
        properties.setStartupAuditEnabled(true);
        properties.setTrustedSenderDomains("noon.com");
        coordinator = new NoonAuthRecoveryCoordinator(
                recoveryRepository,
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
        assertTrue(coordinator.enqueue(NoonAuthWaitRequest.binding(307L, "PRJ1", "PRJ1")).isEmpty());

        StoreSyncStoreRecord incomplete = new StoreSyncStoreRecord();
        incomplete.setProjectCode("PRJ1");
        incomplete.setStoreCode("STORE-1");
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1")).thenReturn(incomplete);

        assertTrue(coordinator.enqueue(NoonAuthWaitRequest.binding(307L, "PRJ1", "STORE-1")).isEmpty());
        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }

    @Test
    void refusesPullRecoveryWithoutSiteBeforeAllocatingRecovery() {
        StoreSyncStoreRecord incomplete = new StoreSyncStoreRecord();
        incomplete.setProjectCode("PRJ1");
        incomplete.setStoreCode("STORE-99");
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-99")).thenReturn(incomplete);

        assertTrue(coordinator.enqueue(NoonAuthWaitRequest.task(
                307L,
                "PRJ1",
                "STORE-99",
                null,
                "ORDER",
                99L,
                "ORDER",
                NoonAuthResumePolicy.AUTO_RESUME
        )).isEmpty());
        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }
}
