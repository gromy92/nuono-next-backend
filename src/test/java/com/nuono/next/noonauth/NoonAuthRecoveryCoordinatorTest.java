package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryCoordinatorTest {
    private NoonAuthRecoveryRepository recoveryRepository;
    private StoreSyncMapper storeSyncMapper;
    private NoonAuthRecoveryProperties properties;
    private NoonAuthRecoveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(15);
        properties.setTrustedSenderDomains("noon.com");
        coordinator = new NoonAuthRecoveryCoordinator(
                recoveryRepository,
                storeSyncMapper,
                properties,
                " Shared@Example.COM ",
                "imap-secret",
                Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneOffset.UTC)
        );
        when(recoveryRepository.selectProjectBindingFingerprint(anyLong(), anyString()))
                .thenReturn("binding-fingerprint-v1");
        when(recoveryRepository.reopenLegacyManualHoldForRenewal(anyString(), any()))
                .thenReturn(null);
        when(storeSyncMapper.selectOwnerStore(anyLong(), anyString())).thenAnswer(invocation ->
                store("PRJ1", invocation.getArgument(1))
        );
    }

    @Test
    void coalescesSameEmailFailuresAndBlocksEachOriginalTaskInOneTransactionBoundary() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(anyLong(), anyString())).thenReturn(project);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setActiveRecoveryId(91L);
        state.setAuthVersion(7L);
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthStateForUpdate(anyLong(), anyString())).thenReturn(state);
        when(recoveryRepository.coalesceRecoveryItem(any())).thenReturn(501L);
        for (long taskId = 1; taskId <= 20; taskId++) {
            Optional<Long> recoveryId = enqueue(task(taskId, 300L + taskId, "STORE-" + taskId));
            assertEquals(Optional.of(91L), recoveryId);
        }

        verify(recoveryRepository, org.mockito.Mockito.times(20)).coalesceActiveRecovery(any());
    }

    @Test
    void featureFlagDisablesQueueAndProjectGate() {
        properties.setEnabled(false);
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthState(308L, "PRJ1")).thenReturn(state);

        assertTrue(enqueue(task(1L, 308L, "STORE1")).isEmpty());
        assertFalse(coordinator.isBlocked(308L, "PRJ1"));
        verify(recoveryRepository, never()).selectProjectAuthState(anyLong(), anyString());
    }

    @Test
    void onlyBlockedProjectIsGated() {
        NoonProjectAuthStateRecord blocked = new NoonProjectAuthStateRecord();
        blocked.setStatus(NoonProjectAuthStatus.RECOVERING);
        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        when(recoveryRepository.selectProjectAuthState(308L, "PRJ1")).thenReturn(blocked);
        when(recoveryRepository.selectProjectAuthState(308L, "PRJ2")).thenReturn(healthy);

        assertTrue(coordinator.isBlocked(308L, "PRJ1"));
        assertFalse(coordinator.isBlocked(308L, "PRJ2"));
    }

    @Test
    void lateTaskForProjectAlreadyInRunningBatchJoinsSameGeneration() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(anyLong(), anyString())).thenReturn(project);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.AUTHENTICATING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        state.setActiveRecoveryId(91L);
        state.setAuthVersion(7L);
        state.setStatus(NoonProjectAuthStatus.RECOVERING);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(state);
        assertEquals(Optional.of(91L), enqueue(task(8L, 307L, "STORE-8")));

    }

    @Test
    void changedProjectBindingReleasesTerminalProjectHoldIntoANewRecovery() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-10")).thenReturn(project);
        when(recoveryRepository.selectProjectBindingFingerprint(307L, "PRJ1"))
                .thenReturn("binding-fingerprint-v2");
        NoonProjectAuthStateRecord held = heldState(
                307L,
                "PRJ1",
                80L,
                "binding-fingerprint-v1",
                NoonAuthIdentityKey.configFingerprint(
                        "shared@example.com",
                        "imap-secret",
                        properties.normalizedTrustedSenderDomains()
                )
        );
        NoonAuthIdentityRecoveryRecord completed = new NoonAuthIdentityRecoveryRecord();
        completed.setId(80L);
        completed.setStatus(NoonAuthRecoveryStatus.COMPLETED);
        when(recoveryRepository.selectRecovery(80L)).thenReturn(completed);
        NoonProjectAuthStateRecord enqueued = new NoonProjectAuthStateRecord();
        enqueued.setActiveRecoveryId(91L);
        enqueued.setAuthVersion(8L);
        enqueued.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1"))
                .thenReturn(held, enqueued);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        assertEquals(Optional.of(91L), enqueue(task(10L, 307L, "STORE-10")));

        verify(recoveryRepository).upsertProjectAuthRequired(
                eq(307L), eq("PRJ1"), anyString(), eq(91L),
                eq("binding-fingerprint-v2"), anyString(), eq("AUTH_REQUIRED"), eq(10L), any()
        );
    }

    @Test
    void changedMailboxCredentialReleasesTerminalProjectHoldIntoANewRecovery() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-11")).thenReturn(project);
        NoonProjectAuthStateRecord held = heldState(
                307L,
                "PRJ1",
                80L,
                "binding-fingerprint-v1",
                NoonAuthIdentityKey.configFingerprint(
                        "shared@example.com",
                        "old-imap-secret",
                        properties.normalizedTrustedSenderDomains()
                )
        );
        NoonProjectAuthStateRecord enqueued = new NoonProjectAuthStateRecord();
        enqueued.setActiveRecoveryId(91L);
        enqueued.setAuthVersion(8L);
        enqueued.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1"))
                .thenReturn(held, enqueued);
        NoonAuthIdentityRecoveryRecord completed = new NoonAuthIdentityRecoveryRecord();
        completed.setId(80L);
        completed.setStatus(NoonAuthRecoveryStatus.COMPLETED);
        when(recoveryRepository.selectRecovery(80L)).thenReturn(completed);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        assertEquals(Optional.of(91L), enqueue(task(11L, 307L, "STORE-11")));
    }

    @Test
    void manualUnbindOrAuthorizationOffBeforeEnqueueFailsClosed() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-12")).thenReturn(project);
        when(recoveryRepository.selectProjectBindingFingerprint(307L, "PRJ1")).thenReturn(null);

        assertTrue(enqueue(task(12L, 307L, "STORE-12")).isEmpty());

        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }

    @Test
    void explicitlyBoundAndAuthorizedProvisionalProjectCanRecoverWithEmptyCookie() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        project.setBindStatus(1);
        project.setOwnerAuthorized(true);
        project.setNoonPartnerCookie(null);
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-13")).thenReturn(project);
        when(recoveryRepository.selectProjectBindingFingerprint(307L, "PRJ1"))
                .thenReturn("bound-authorized-empty-cookie-fingerprint");
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord enqueued = new NoonProjectAuthStateRecord();
        enqueued.setActiveRecoveryId(91L);
        enqueued.setAuthVersion(1L);
        enqueued.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1"))
                .thenReturn(null, enqueued);
        assertEquals(Optional.of(91L), enqueue(task(13L, 307L, "STORE-13")));
    }

    @Test
    void bindingWithoutPullTaskEntersTheSameDurableQueue() {
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1")).thenReturn(store("PRJ1", "STORE-1"));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        when(recoveryRepository.rebaseProjectBindingEpoch(
                eq(91L),
                eq(307L),
                eq("PRJ1"),
                anyString(),
                eq("binding-fingerprint-v1"),
                anyString(),
                any(),
                any(),
                any()
        )).thenReturn(1L);

        assertEquals(Optional.of(91L), coordinator.enqueue(NoonAuthWaitRequest.binding(307L, "PRJ1", "STORE-1")));

        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(recoveryRepository);
        lockOrder.verify(recoveryRepository).coalesceActiveRecovery(any());
        lockOrder.verify(recoveryRepository).selectActiveRecoveryForUpdate(anyString());
        lockOrder.verify(recoveryRepository).selectProjectAuthStateForUpdate(307L, "PRJ1");
        lockOrder.verify(recoveryRepository).rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), eq("binding-fingerprint-v1"),
                anyString(), any(), any(), any()
        );
        verify(recoveryRepository).rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), eq("binding-fingerprint-v1"),
                anyString(), any(), any(), any()
        );
        verify(recoveryRepository, never()).upsertProjectAuthRequired(
                anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.<Long>isNull(), any()
        );
        verify(recoveryRepository).coalesceRecoveryItem(org.mockito.ArgumentMatchers.argThat(item ->
                Long.valueOf(91L).equals(item.getRecoveryId())
                        && Long.valueOf(307L).equals(item.getOwnerUserId())
                        && "PRJ1".equals(item.getProjectCode())
                        && "STORE-1".equals(item.getStoreCode())
                        && "AE".equals(item.getSiteCode())
                        && item.getSourceTaskId() == null
                        && "STORE_BINDING".equals(item.getSourceDomain())
        ));
    }

    @Test
    void sameTaskCannotStartAnotherOtpUnderTheAuthVersionAlreadyRecoveredForIt() {
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1"))
                .thenReturn(store("PRJ1", "STORE-1"));
        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        healthy.setAuthVersion(8L);
        when(recoveryRepository.selectProjectAuthState(307L, "PRJ1")).thenReturn(healthy);
        when(recoveryRepository.hasRecoveredSourceTaskAtCurrentAuthVersion(
                307L, "PRJ1", "PRODUCT_DELETE", 77001L, 8L
        )).thenReturn(true);

        NoonAuthRetrySuppressedException exception = assertThrows(
                NoonAuthRetrySuppressedException.class,
                () -> coordinator.enqueue(NoonAuthWaitRequest.task(
                        307L,
                        "PRJ1",
                        "STORE-1",
                        "AE",
                        "PRODUCT_DELETE",
                        77001L,
                        "PROVIDER_CALL",
                        NoonAuthResumePolicy.AUTO_RESUME
                ))
        );

        assertTrue(exception.getMessage().contains("停止重复认证"));
        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
    }

    @Test
    void explicitBindAfterCookieCommitRebasesRecoveredSlotZeroInTheSameActiveRecovery() {
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1")).thenReturn(store("PRJ1", "STORE-1"));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.RECOVERING_PULLS);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        healthy.setAuthVersion(8L);
        healthy.setBindingFingerprint("binding-fingerprint-v1");
        healthy.setConfigFingerprint(NoonAuthIdentityKey.configFingerprint(
                "shared@example.com",
                "imap-secret",
                properties.normalizedTrustedSenderDomains()
        ));
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(healthy);
        NoonAuthRecoveryItemRecord recoveredSlotZero = new NoonAuthRecoveryItemRecord();
        recoveredSlotZero.setRecoveryId(91L);
        recoveredSlotZero.setOwnerUserId(307L);
        recoveredSlotZero.setProjectCode("PRJ1");
        recoveredSlotZero.setExpectedAuthVersion(7L);
        recoveredSlotZero.setStatus(NoonAuthRecoveryItemStatus.RECOVERED);
        when(recoveryRepository.selectProjectRecoveryItem(91L, 307L, "PRJ1"))
                .thenReturn(recoveredSlotZero);
        when(recoveryRepository.rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), eq("binding-fingerprint-v1"),
                anyString(), any(), any(), any()
        )).thenReturn(9L);

        assertEquals(Optional.of(91L), coordinator.enqueue(NoonAuthWaitRequest.binding(307L, "PRJ1", "STORE-1")));

        verify(recoveryRepository).rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), eq("binding-fingerprint-v1"),
                anyString(), any(), any(), any()
        );
        verify(recoveryRepository).coalesceRecoveryItem(org.mockito.ArgumentMatchers.argThat(item ->
                Long.valueOf(91L).equals(item.getRecoveryId())
                        && item.getSourceTaskId() == null
                        && Long.valueOf(9L).equals(item.getExpectedAuthVersion())
        ));
    }

    @Test
    void explicitBindRebasesTheSingleActiveRenewalInsteadOfAnOldWaitingSuccessor() {
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1")).thenReturn(store("PRJ1", "STORE-1"));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.AUTHENTICATING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord bound = new NoonProjectAuthStateRecord();
        bound.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        bound.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        bound.setActiveRecoveryId(91L);
        bound.setAuthVersion(8L);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(bound);
        when(recoveryRepository.rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), anyString(), anyString(),
                any(), any(), any()
        )).thenReturn(9L);

        assertEquals(Optional.of(91L), coordinator.enqueue(NoonAuthWaitRequest.binding(307L, "PRJ1", "STORE-1")));

        verify(recoveryRepository).rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), anyString(), anyString(),
                any(), any(), any()
        );
    }

    @Test
    void explicitBindFromTerminalProjectStateUsesANewLiveRecovery() {
        when(storeSyncMapper.selectOwnerStore(307L, "STORE-1")).thenReturn(store("PRJ1", "STORE-1"));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord terminalBound = new NoonProjectAuthStateRecord();
        terminalBound.setStatus(NoonProjectAuthStatus.MANUAL_HOLD);
        terminalBound.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        terminalBound.setActiveRecoveryId(80L);
        terminalBound.setAuthVersion(7L);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1"))
                .thenReturn(terminalBound);
        when(recoveryRepository.rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), anyString(), anyString(),
                any(), any(), any()
        )).thenReturn(8L);

        assertEquals(Optional.of(91L), coordinator.enqueue(NoonAuthWaitRequest.binding(307L, "PRJ1", "STORE-1")));

        verify(recoveryRepository).rebaseProjectBindingEpoch(
                eq(91L), eq(307L), eq("PRJ1"), anyString(), anyString(), anyString(),
                any(), any(), any()
        );
        verify(recoveryRepository, never()).rebaseProjectBindingEpoch(
                eq(80L), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any()
        );
    }

    @Test
    void lateFailureFromTaskStartedBeforeCompletedAuthUsesCurrentCookieWithoutReblockingProject() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-15")).thenReturn(project);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(95L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(95L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);

        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setOwnerUserId(307L);
        healthy.setProjectCode("PRJ1");
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        healthy.setActiveRecoveryId(null);
        healthy.setAuthVersion(8L);
        healthy.setLastSuccessAt(LocalDateTime.parse("2026-07-16T04:00:00"));
        NoonProjectAuthStateRecord incorrectlyReblocked = new NoonProjectAuthStateRecord();
        incorrectlyReblocked.setActiveRecoveryId(95L);
        incorrectlyReblocked.setAuthVersion(9L);
        incorrectlyReblocked.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1"))
                .thenReturn(healthy, healthy, incorrectlyReblocked);
        NoonPullTaskRecord lateTask = task(15L, 307L, "STORE-15");
        lateTask.setStartedAt(LocalDateTime.parse("2026-07-16T03:59:59"));

        assertEquals(Optional.of(95L), enqueue(lateTask));

        verify(recoveryRepository, never()).upsertProjectAuthRequired(
                anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyLong(), any()
        );
        verify(recoveryRepository).coalesceRecoveryItem(org.mockito.ArgumentMatchers.argThat(item ->
                Long.valueOf(95L).equals(item.getRecoveryId())
                        && Long.valueOf(7L).equals(item.getExpectedAuthVersion())
                        && Long.valueOf(15L).equals(item.getSourceTaskId())
        ));
    }

    @Test
    void staleLateFailureAfterProjectCookieCommitJoinsCurrentRecoveryWithoutAnotherOtpBatch() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-14")).thenReturn(project);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.RECOVERING_PULLS);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setOwnerUserId(307L);
        healthy.setProjectCode("PRJ1");
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        healthy.setAuthVersion(8L);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(healthy);
        NoonAuthRecoveryItemRecord committed = new NoonAuthRecoveryItemRecord();
        committed.setRecoveryId(91L);
        committed.setOwnerUserId(307L);
        committed.setProjectCode("PRJ1");
        committed.setExpectedAuthVersion(7L);
        committed.setStatus(NoonAuthRecoveryItemStatus.RECOVERED);
        when(recoveryRepository.selectProjectRecoveryItem(91L, 307L, "PRJ1")).thenReturn(committed);
        assertEquals(Optional.of(91L), enqueue(task(14L, 307L, "STORE-14")));

        verify(recoveryRepository, never()).upsertProjectAuthRequired(
                anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyLong(), any()
        );
        verify(recoveryRepository).coalesceRecoveryItem(org.mockito.ArgumentMatchers.argThat(item ->
                Long.valueOf(91L).equals(item.getRecoveryId())
                        && Long.valueOf(7L).equals(item.getExpectedAuthVersion())
                        && Long.valueOf(14L).equals(item.getSourceTaskId())
        ));
    }

    private NoonProjectAuthStateRecord heldState(
            Long ownerUserId,
            String projectCode,
            Long recoveryId,
            String bindingFingerprint,
            String configFingerprint
    ) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setOwnerUserId(ownerUserId);
        state.setProjectCode(projectCode);
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(7L);
        state.setStatus(NoonProjectAuthStatus.MANUAL_HOLD);
        state.setBindingFingerprint(bindingFingerprint);
        state.setConfigFingerprint(configFingerprint);
        return state;
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

    private Optional<Long> enqueue(NoonPullTaskRecord task) {
        return coordinator.enqueue(NoonAuthWaitRequest.task(
                task.getOwnerUserId(),
                null,
                task.getStoreCode(),
                task.getSiteCode(),
                task.getDataDomain() == null ? "NOON_PULL" : task.getDataDomain().name(),
                task.getId(),
                task.getDataDomain() == null ? "PULL" : task.getDataDomain().name(),
                NoonAuthResumePolicy.AUTO_RESUME,
                task.getStartedAt()
        ));
    }

    private StoreSyncStoreRecord store(String projectCode, String storeCode) {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode(projectCode);
        store.setStoreCode(storeCode);
        store.setSite("AE");
        return store;
    }
}
