package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.nuono.next.noonpull.NoonPullRepository;
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
    private NoonPullRepository pullRepository;
    private StoreSyncMapper storeSyncMapper;
    private NoonAuthRecoveryProperties properties;
    private NoonAuthRecoveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        pullRepository = mock(NoonPullRepository.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(15);
        properties.setTrustedSenderDomains("noon.com");
        coordinator = new NoonAuthRecoveryCoordinator(
                recoveryRepository,
                pullRepository,
                storeSyncMapper,
                properties,
                " Shared@Example.COM ",
                "imap-secret",
                Clock.fixed(Instant.parse("2026-07-16T04:00:00Z"), ZoneOffset.UTC)
        );
        when(recoveryRepository.selectProjectBindingFingerprint(anyLong(), anyString()))
                .thenReturn("binding-fingerprint-v1");
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
        when(pullRepository.blockTaskForAuth(anyLong(), anyLong(), anyString(), any())).thenReturn(1);

        for (long taskId = 1; taskId <= 20; taskId++) {
            Optional<Long> recoveryId = coordinator.blockAndEnqueue(
                    task(taskId, 300L + taskId, "STORE-" + taskId),
                    "auth_required: Noon Cookie invalid"
            );
            assertEquals(Optional.of(91L), recoveryId);
        }

        verify(recoveryRepository, org.mockito.Mockito.times(20)).coalesceActiveRecovery(any());
        verify(pullRepository, org.mockito.Mockito.times(20))
                .blockTaskForAuth(anyLong(), org.mockito.ArgumentMatchers.eq(91L), anyString(), any());
    }

    @Test
    void refusesProjectAccessErrorsSoTheyCannotBurnOtpQuota() {
        Optional<Long> result = coordinator.blockAndEnqueue(
                task(1L, 308L, "STORE1"),
                "auth_required: account does not contain current project PRJ404"
        );

        assertTrue(result.isEmpty());
        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
        verify(pullRepository, never()).blockTaskForAuth(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void featureFlagDisablesQueueAndProjectGate() {
        properties.setEnabled(false);
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        when(recoveryRepository.selectProjectAuthState(308L, "PRJ1")).thenReturn(state);

        assertTrue(coordinator.blockAndEnqueue(
                task(1L, 308L, "STORE1"),
                "auth_required: cookie expired"
        ).isEmpty());
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
    void runningOrHeldBatchNeverAbsorbsLateTaskAndUsesOneSuccessor() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(anyLong(), anyString())).thenReturn(project);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setStatus(NoonAuthRecoveryStatus.AUTHENTICATING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        when(recoveryRepository.coalesceSuccessorRecovery(any())).thenReturn(92L);
        NoonAuthIdentityRecoveryRecord successor = new NoonAuthIdentityRecoveryRecord();
        successor.setId(92L);
        successor.setPredecessorRecoveryId(91L);
        successor.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        successor.setStatus(NoonAuthRecoveryStatus.WAITING_PREDECESSOR);
        when(recoveryRepository.selectRecovery(92L)).thenReturn(successor);
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setActiveRecoveryId(92L);
        state.setAuthVersion(8L);
        when(recoveryRepository.selectProjectAuthStateForUpdate(anyLong(), anyString()))
                .thenReturn(null, state);
        when(pullRepository.blockTaskForAuth(anyLong(), anyLong(), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(92L), coordinator.blockAndEnqueue(
                task(7L, 307L, "STORE-7"),
                "auth_required: cookie expired"
        ));

        verify(recoveryRepository).coalesceSuccessorRecovery(any());
        verify(recoveryRepository).promoteReadySuccessors(any(), any());
        verify(pullRepository).blockTaskForAuth(eq(7L), eq(92L), anyString(), any());
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
        when(pullRepository.blockTaskForAuth(anyLong(), anyLong(), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(91L), coordinator.blockAndEnqueue(
                task(8L, 307L, "STORE-8"),
                "auth_required: cookie expired"
        ));

        verify(recoveryRepository, never()).coalesceSuccessorRecovery(any());
        verify(pullRepository).blockTaskForAuth(eq(8L), eq(91L), anyString(), any());
    }

    @Test
    void terminalProjectManualHoldRejectsNewRecoveryWhileBindingAndCredentialsAreUnchanged() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-9")).thenReturn(project);
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
        when(recoveryRepository.selectProjectAuthState(307L, "PRJ1")).thenReturn(held);

        assertTrue(coordinator.blockAndEnqueue(
                task(9L, 307L, "STORE-9"),
                "auth_required: cookie expired"
        ).isEmpty());

        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
        verify(pullRepository, never()).blockTaskForAuth(anyLong(), anyLong(), anyString(), any());
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
        when(pullRepository.blockTaskForAuth(10L, 91L, "auth expiry queued; task=10; domain=ORDER", null))
                .thenReturn(0);
        when(pullRepository.blockTaskForAuth(eq(10L), eq(91L), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(91L), coordinator.blockAndEnqueue(
                task(10L, 307L, "STORE-10"),
                "auth_required: cookie expired"
        ));

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
        when(pullRepository.blockTaskForAuth(eq(11L), eq(91L), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(91L), coordinator.blockAndEnqueue(
                task(11L, 307L, "STORE-11"),
                "auth_required: cookie expired"
        ));
    }

    @Test
    void manualUnbindOrAuthorizationOffBeforeEnqueueFailsClosed() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-12")).thenReturn(project);
        when(recoveryRepository.selectProjectBindingFingerprint(307L, "PRJ1")).thenReturn(null);

        assertTrue(coordinator.blockAndEnqueue(
                task(12L, 307L, "STORE-12"),
                "auth_required: cookie expired"
        ).isEmpty());

        verify(recoveryRepository, never()).coalesceActiveRecovery(any());
        verify(pullRepository, never()).blockTaskForAuth(eq(12L), anyLong(), anyString(), any());
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
        when(pullRepository.blockTaskForAuth(eq(13L), eq(91L), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(91L), coordinator.blockAndEnqueue(
                task(13L, 307L, "STORE-13"),
                "auth_required: missing_cookie"
        ));
    }

    @Test
    void bindingWithoutPullTaskEntersTheSameDurableQueue() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        project.setStoreCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "PRJ1")).thenReturn(project);
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

        assertEquals(Optional.of(91L), coordinator.enqueueProject(307L, "PRJ1", "STORE-1"));

        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(recoveryRepository);
        lockOrder.verify(recoveryRepository).coalesceActiveRecovery(any());
        lockOrder.verify(recoveryRepository).selectActiveRecoveryForUpdate(anyString());
        lockOrder.verify(recoveryRepository).selectWaitingSuccessorForUpdate(anyString());
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
                        && item.getSourceTaskId() == null
                        && "STORE_BINDING".equals(item.getSourceDomain())
        ));
        verify(pullRepository, never()).blockTaskForAuth(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void explicitBindAfterCookieCommitRebasesRecoveredSlotZeroInTheSameActiveRecovery() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        project.setStoreCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "PRJ1")).thenReturn(project);
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

        assertEquals(Optional.of(91L), coordinator.enqueueProject(307L, "PRJ1", "STORE-1"));

        verify(recoveryRepository, never()).coalesceSuccessorRecovery(any());
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
    void explicitBindAlreadyBoundToWaitingSuccessorRebasesOnlyThatSuccessor() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "PRJ1")).thenReturn(project);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        NoonAuthIdentityRecoveryRecord active = new NoonAuthIdentityRecoveryRecord();
        active.setId(91L);
        active.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        active.setStatus(NoonAuthRecoveryStatus.AUTHENTICATING);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        NoonAuthIdentityRecoveryRecord successor = new NoonAuthIdentityRecoveryRecord();
        successor.setId(92L);
        successor.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        successor.setPredecessorRecoveryId(91L);
        successor.setStatus(NoonAuthRecoveryStatus.WAITING_PREDECESSOR);
        when(recoveryRepository.selectWaitingSuccessorForUpdate(anyString())).thenReturn(successor);
        NoonProjectAuthStateRecord bound = new NoonProjectAuthStateRecord();
        bound.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        bound.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        bound.setActiveRecoveryId(92L);
        bound.setAuthVersion(8L);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ1")).thenReturn(bound);
        when(recoveryRepository.rebaseProjectBindingEpoch(
                eq(92L), eq(307L), eq("PRJ1"), anyString(), anyString(), anyString(),
                any(), any(), any()
        )).thenReturn(9L);

        assertEquals(Optional.of(92L), coordinator.enqueueProject(307L, "PRJ1", "STORE-1"));

        verify(recoveryRepository, never()).coalesceSuccessorRecovery(any());
        verify(recoveryRepository).rebaseProjectBindingEpoch(
                eq(92L), eq(307L), eq("PRJ1"), anyString(), anyString(), anyString(),
                any(), any(), any()
        );
    }

    @Test
    void explicitBindFromTerminalProjectStateUsesANewLiveRecovery() {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("PRJ1");
        when(storeSyncMapper.selectOwnerProject(307L, "PRJ1")).thenReturn(project);
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

        assertEquals(Optional.of(91L), coordinator.enqueueProject(307L, "PRJ1", "STORE-1"));

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
        when(pullRepository.blockTaskForAuth(eq(15L), eq(95L), anyString(), any())).thenReturn(1);

        NoonPullTaskRecord lateTask = task(15L, 307L, "STORE-15");
        lateTask.setStartedAt(LocalDateTime.parse("2026-07-16T03:59:59"));

        assertEquals(Optional.of(95L), coordinator.blockAndEnqueue(
                lateTask,
                "auth_required: stale request completed with old cookie"
        ));

        verify(recoveryRepository, never()).upsertProjectAuthRequired(
                anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(),
                anyString(), anyLong(), any()
        );
        verify(recoveryRepository).coalesceRecoveryItem(org.mockito.ArgumentMatchers.argThat(item ->
                Long.valueOf(95L).equals(item.getRecoveryId())
                        && Long.valueOf(7L).equals(item.getExpectedAuthVersion())
                        && Long.valueOf(15L).equals(item.getSourceTaskId())
        ));
        verify(pullRepository).blockTaskForAuth(eq(15L), eq(95L), anyString(), any());
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
        when(pullRepository.blockTaskForAuth(eq(14L), eq(91L), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(91L), coordinator.blockAndEnqueue(
                task(14L, 307L, "STORE-14"),
                "auth_required: stale worker observed old cookie"
        ));

        verify(recoveryRepository, never()).coalesceSuccessorRecovery(any());
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
}
