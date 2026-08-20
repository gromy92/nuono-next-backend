package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import com.nuono.next.noon.NoonAccountSessionProjectTarget;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryIdentityBatchTest {
    private NoonAuthRecoveryRepository recoveryRepository;
    private StoreSyncMapper storeSyncMapper;
    private NoonAuthRecoveryProperties properties;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(15);
        properties.setProjectAllowlist("PRJ-A, PRJ-B");
        properties.setTrustedSenderDomains("noon.com");
        when(recoveryRepository.selectProjectBindingFingerprint(anyLong(), anyString()))
                .thenReturn("binding-fingerprint");
    }

    @Test
    void firstExpiredProjectMustNotInvalidateAnotherHealthyProjectOnTheSameEmail() {
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-A")).thenReturn(project("PRJ-A"));
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode("PRJ-A");
        store.setStoreCode("STORE-A");
        store.setSite("AE");
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-A")).thenReturn(store);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString()))
                .thenReturn(recovery(91L, NoonAuthRecoveryStatus.COALESCING));
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ-A"))
                .thenReturn(null, requiredState(91L, 7L));
        Optional<Long> recoveryId = coordinator().enqueue(NoonAuthWaitRequest.task(
                307L,
                null,
                "STORE-A",
                "AE",
                "ORDER",
                41L,
                "ORDER",
                NoonAuthResumePolicy.AUTO_RESUME
        ));

        assertEquals(Optional.of(91L), recoveryId);
        verify(recoveryRepository).coalesceRecoveryItem(anyItem(
                91L, 307L, "PRJ-A", "STORE-A", "AE", 41L, "ORDER", 7L
        ));
        verify(recoveryRepository, never()).upsertProjectAuthRequired(
                eq(308L), eq("PRJ-B"), anyString(), anyLong(), anyString(), anyString(),
                anyString(), eq(null), any()
        );
    }

    @Test
    void firstExpiredProjectShouldAttachEveryBoundProjectToTheSameIdentityRecovery() {
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-A")).thenReturn(project("PRJ-A"));
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-A")).thenReturn(store("PRJ-A", "STORE-A"));
        when(storeSyncMapper.selectOwnerStore(308L, "STORE-B")).thenReturn(store("PRJ-B", "STORE-B"));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString()))
                .thenReturn(recovery(91L, NoonAuthRecoveryStatus.COALESCING));
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ-A"))
                .thenReturn(null, requiredState(91L, 7L));
        when(recoveryRepository.rebaseProjectBindingEpoch(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any()
        )).thenReturn(7L);

        NoonAccountSessionMapper accountMapper = mock(NoonAccountSessionMapper.class);
        when(accountMapper.listBoundProjects()).thenReturn(List.of(
                binding(307L, "PRJ-A", "STORE-A"),
                binding(308L, "PRJ-B", "STORE-B")
        ));
        NoonAuthRecoveryCoordinator coordinator = coordinator();
        coordinator.setIdentityProjectFanout(new NoonAuthIdentityProjectFanout(
                accountMapper, storeSyncMapper, properties
        ));

        Optional<Long> recoveryId = coordinator.enqueue(NoonAuthWaitRequest.task(
                307L, null, "STORE-A", "AE", "ORDER", 41L, "ORDER",
                NoonAuthResumePolicy.AUTO_RESUME
        ));

        assertEquals(Optional.of(91L), recoveryId);
        verify(recoveryRepository).coalesceRecoveryItem(org.mockito.ArgumentMatchers.argThat(item ->
                Long.valueOf(91L).equals(item.getRecoveryId())
                        && Long.valueOf(308L).equals(item.getOwnerUserId())
                        && "PRJ-B".equals(item.getProjectCode())
                        && item.getSourceTaskId() == null
                        && item.getResumePolicy() == NoonAuthResumePolicy.NONE
        ));
    }

    private NoonAuthRecoveryCoordinator coordinator() {
        return new NoonAuthRecoveryCoordinator(
                recoveryRepository,
                storeSyncMapper,
                properties,
                "shared@example.com",
                "imap-secret",
                Clock.fixed(Instant.parse("2026-07-30T04:00:00Z"), ZoneOffset.UTC)
        );
    }

    private NoonAuthRecoveryItemRecord anyItem(
            Long recoveryId,
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            Long taskId,
            String sourceDomain,
            Long authVersion
    ) {
        return org.mockito.ArgumentMatchers.argThat(item ->
                recoveryId.equals(item.getRecoveryId())
                        && ownerUserId.equals(item.getOwnerUserId())
                        && projectCode.equals(item.getProjectCode())
                        && storeCode.equals(item.getStoreCode())
                        && siteCode.equals(item.getSiteCode())
                        && java.util.Objects.equals(taskId, item.getSourceTaskId())
                        && sourceDomain.equals(item.getSourceDomain())
                        && sourceDomain.equals(item.getSourceCheckpoint())
                        && item.getResumePolicy() == NoonAuthResumePolicy.AUTO_RESUME
                        && authVersion.equals(item.getExpectedAuthVersion())
        );
    }

    private NoonProjectAuthStateRecord requiredState(Long recoveryId, Long authVersion) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(authVersion);
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        return state;
    }

    private NoonAuthIdentityRecoveryRecord recovery(Long id, NoonAuthRecoveryStatus status) {
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setId(id);
        recovery.setStatus(status);
        return recovery;
    }

    private StoreSyncStoreRecord project(String projectCode) {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode(projectCode);
        return project;
    }

    private StoreSyncStoreRecord store(String projectCode, String storeCode) {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode(projectCode);
        store.setStoreCode(storeCode);
        store.setSite("AE");
        return store;
    }

    private NoonAccountSessionProjectTarget binding(Long owner, String project, String store) {
        NoonAccountSessionProjectTarget target = new NoonAccountSessionProjectTarget();
        target.setOwnerUserId(owner);
        target.setProjectCode(project);
        target.setStoreCode(store);
        return target;
    }

    private NoonPullTaskRecord task(Long id, Long ownerUserId, String storeCode) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(id);
        task.setOwnerUserId(ownerUserId);
        task.setStoreCode(storeCode);
        task.setSiteCode("AE");
        task.setDataDomain(NoonPullDataDomain.ORDER);
        task.setStatus(NoonPullTaskStatus.RUNNING);
        return task;
    }
}
