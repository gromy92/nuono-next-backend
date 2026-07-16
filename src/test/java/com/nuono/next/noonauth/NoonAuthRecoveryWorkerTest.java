package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureCode;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.InOrder;

class NoonAuthRecoveryWorkerTest {
    private NoonAuthRecoveryRepository repository;
    private NoonAuthRecoveryGateway gateway;
    private NoonAuthRecoveryProperties properties;
    private NoonAuthRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(NoonAuthRecoveryRepository.class);
        gateway = mock(NoonAuthRecoveryGateway.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(0);
        properties.setMinResendSeconds(60);
        properties.setTrustedSenderDomains("noon.com");
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                Clock.fixed(Instant.parse("2026-07-16T05:00:00Z"), ZoneOffset.UTC),
                "worker-test",
                "shared@example.com",
                "imap-secret"
        );
        when(repository.tryClaimRecovery(
                anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.transitionRecovery(
                anyLong(), any(), any(), anyLong(), anyString(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any()
        )).thenReturn(true);
        when(repository.recordSendIntent(anyLong(), any(), anyLong(), anyString(), any(), any())).thenReturn(true);
        when(repository.renewLease(anyLong(), any(), anyLong(), anyString(), any(), any())).thenReturn(true);
        when(repository.recordMailboxCorrelation(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any()
        )).thenReturn(true);
        when(repository.completeRecoveryIfDrained(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any(), any(), any()
        )).thenReturn(true);
        when(repository.persistRecoveredProjectCookieCas(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                anyString(), anyLong(), any()
        )).thenReturn(true);
        when(repository.markProjectRecovering(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        )).thenReturn(true);
        when(repository.markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        )).thenReturn(1);
        when(repository.failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.requeueBlockedTaskAfterRecoveryCas(
                anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        )).thenReturn(true);
        when(repository.transitionRecoveryItem(
                anyLong(), anyLong(), any(), any(), any(), anyLong(), anyString(),
                any(), any(), any(), any()
        )).thenReturn(true);
        when(repository.countIdentitySendsSince(anyString(), any())).thenReturn(0);
    }

    @Test
    void oneIdentityAttemptRecoversMultipleProjectsAndTheirOriginalTasks() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(10L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 10L, 307L, "PRJ307", "STORE307", 1001L, 3L),
                item(2L, 10L, 308L, "PRJ308", "STORE308", 1002L, 9L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(10L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString())).thenAnswer(invocation -> blockedState(
                invocation.getArgument(0), invocation.getArgument(1), 10L,
                "PRJ307".equals(invocation.getArgument(1)) ? 3L : 9L
        ));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
            command.beforeOtpSendOrThrow();
            List<NoonAuthRecoveryProjectResult> results = new ArrayList<>();
            command.getProjectTargets().forEach(target -> results.add(
                    NoonAuthRecoveryProjectResult.recovered(target, "sid=" + target.getProjectCode())
            ));
            return NoonAuthRecoveryAttemptResult.authenticated("message-hash-1", results);
        });

        assertEquals(1, worker.runOnce());

        verify(gateway, times(1)).attempt(any());
        verify(repository, times(1)).recordSendIntent(
                eq(10L), eq(NoonAuthRecoveryStatus.AUTHENTICATING), anyLong(), anyString(), any(), any()
        );
        verify(repository, times(2)).persistRecoveredProjectCookieCas(
                anyLong(), anyString(), eq(10L), anyLong(), eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS),
                anyLong(), anyString(), anyString(), anyLong(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(1001L), eq(10L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(1002L), eq(10L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository).completeRecoveryIfDrained(
                eq(10L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS), anyLong(), anyString(),
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void projectBindingItemWithoutSourceTaskRecoversAndCompletesWithoutTaskMutation() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                11L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        NoonAuthRecoveryItemRecord bindingItem = item(
                11L,
                11L,
                307L,
                "PRJ307",
                "STORE307",
                null,
                3L
        );
        bindingItem.setSourceDomain("STORE_BINDING");
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(11L, Integer.MAX_VALUE)).thenReturn(List.of(bindingItem));
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 11L, 3L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "binding-message-hash",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=binding-recovered"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository).persistRecoveredProjectCookieCas(
                eq(307L), eq("PRJ307"), eq(11L), eq(3L),
                eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS), anyLong(), anyString(),
                eq("sid=binding-recovered"), eq(307L), any()
        );
        verify(repository, never()).requeueBlockedTaskAfterRecoveryCas(
                anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        );
        verify(repository).completeRecoveryIfDrained(
                eq(11L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS), anyLong(), anyString(),
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void failedProjectTerminatesBlockedTaskBeforeMakingItsItemInvisibleToRecovery() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                12L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(12L, 12L, 307L, "PRJ307", "STORE307", 1201L, 3L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(12L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 12L, 3L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "project-failure-message",
                    List.of(NoonAuthRecoveryProjectResult.failed(
                            command.getProjectTargets().get(0),
                            NoonAuthRecoveryProjectResult.Code.SESSION_CREATE_FAILED,
                            "project session failed"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        InOrder failureOrder = inOrder(repository);
        failureOrder.verify(repository).markProjectRecoveryFailed(
                eq(307L), eq("PRJ307"), eq(12L), eq(3L),
                eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD),
                eq("SESSION_CREATE_FAILED"), any(), any()
        );
        failureOrder.verify(repository).failBlockedTaskAfterRecovery(
                eq(1201L), eq(12L), eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS),
                anyLong(), anyString(), eq("SESSION_CREATE_FAILED"), any(), any()
        );
        failureOrder.verify(repository).transitionRecoveryItem(
                eq(12L), eq(12L), eq(NoonAuthRecoveryItemStatus.PENDING),
                eq(NoonAuthRecoveryItemStatus.FAILED),
                eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS), anyLong(), anyString(),
                eq("SESSION_CREATE_FAILED"), any(), eq(null), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
    }

    @Test
    void failedProjectTouchesOnlySnapshotItemsThenNextClaimClosesLateItemWithoutAnotherOtp() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                13L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        NoonAuthIdentityRecoveryRecord retry = recovery(
                13L,
                NoonAuthRecoveryStatus.WAITING_COOLDOWN,
                5L,
                1,
                1
        );
        NoonAuthRecoveryItemRecord snapshotItem = item(
                131L, 13L, 307L, "PRJ307", "STORE307", 1301L, 3L
        );
        NoonAuthRecoveryItemRecord lateItem = item(
                132L, 13L, 307L, "PRJ307", "STORE307", 1302L, 3L
        );
        when(repository.listDueRecoveries(any(), anyInt()))
                .thenReturn(List.of(recovery), List.of(retry));
        when(repository.listPendingItems(13L, Integer.MAX_VALUE))
                .thenReturn(
                        List.of(snapshotItem),
                        List.of(snapshotItem),
                        List.of(lateItem),
                        List.of()
                );
        NoonProjectAuthStateRecord held = blockedState(307L, "PRJ307", 13L, 3L);
        held.setStatus(NoonProjectAuthStatus.MANUAL_HOLD);
        held.setLastFailureCode("SESSION_CREATE_FAILED");
        held.setManualHoldReason("project session failed");
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 13L, 3L), held);
        when(repository.completeRecoveryIfDrained(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any(), any(), any()
        )).thenReturn(false, true);
        when(repository.hasPendingItems(13L)).thenReturn(true);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "project-failure-message",
                    List.of(NoonAuthRecoveryProjectResult.failed(
                            command.getProjectTargets().get(0),
                            NoonAuthRecoveryProjectResult.Code.SESSION_CREATE_FAILED,
                            "project session failed"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());
        assertEquals(1, worker.runOnce());

        verify(repository).failBlockedTaskAfterRecovery(
                eq(snapshotItem.getSourceTaskId()), eq(13L), any(), anyLong(), anyString(),
                eq("SESSION_CREATE_FAILED"), any(), any()
        );
        verify(repository).transitionRecoveryItem(
                eq(snapshotItem.getId()), eq(13L), eq(NoonAuthRecoveryItemStatus.PENDING),
                eq(NoonAuthRecoveryItemStatus.FAILED), any(), anyLong(), anyString(),
                eq("SESSION_CREATE_FAILED"), any(), eq(null), any()
        );
        verify(repository).failBlockedTaskAfterRecovery(
                eq(lateItem.getSourceTaskId()), eq(13L), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN),
                anyLong(), anyString(), eq("SESSION_CREATE_FAILED"), any(), any()
        );
        verify(repository).transitionRecoveryItem(
                eq(lateItem.getId()), eq(13L), eq(NoonAuthRecoveryItemStatus.PENDING),
                eq(NoonAuthRecoveryItemStatus.FAILED), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN),
                anyLong(), anyString(), eq("SESSION_CREATE_FAILED"), any(), eq(null), any()
        );
        verify(gateway, times(1)).attempt(any());
        verify(repository, times(1)).recordSendIntent(
                eq(13L), any(), anyLong(), anyString(), any(), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
    }

    @Test
    void sourceTaskFailureCasMissStillUsesGuardedItemCasAndKeepsRecoveryNonTerminalWhenRejected() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                14L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        NoonAuthRecoveryItemRecord snapshotItem = item(
                141L, 14L, 307L, "PRJ307", "STORE307", 1401L, 3L
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(14L, Integer.MAX_VALUE)).thenReturn(List.of(snapshotItem));
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 14L, 3L));
        when(repository.failBlockedTaskAfterRecovery(
                eq(1401L), eq(14L), any(), anyLong(), anyString(), anyString(), any(), any()
        )).thenReturn(false);
        when(repository.transitionRecoveryItem(
                eq(141L), eq(14L), eq(NoonAuthRecoveryItemStatus.PENDING),
                eq(NoonAuthRecoveryItemStatus.FAILED), any(), anyLong(), anyString(),
                any(), any(), eq(null), any()
        )).thenReturn(false);
        when(repository.hasPendingItems(14L)).thenReturn(true);
        when(gateway.attempt(any())).thenReturn(NoonAuthRecoveryAttemptResult.failed(
                NoonAuthRecoveryFailureCode.INTERNAL_FAILURE,
                null,
                "identity authentication failed"
        ));

        assertEquals(1, worker.runOnce());

        verify(repository).failBlockedTaskAfterRecovery(
                eq(1401L), eq(14L), any(), anyLong(), anyString(),
                eq("INTERNAL_FAILURE"), any(), any()
        );
        verify(repository).transitionRecoveryItem(
                eq(141L), eq(14L), eq(NoonAuthRecoveryItemStatus.PENDING),
                eq(NoonAuthRecoveryItemStatus.FAILED), any(), anyLong(), anyString(),
                eq("INTERNAL_FAILURE"), any(), eq(null), any()
        );
        verify(repository, never()).transitionRecovery(
                eq(14L), any(), eq(NoonAuthRecoveryStatus.FAILED_FINAL), anyLong(), anyString(),
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()
        );
        verify(repository).transitionRecovery(
                eq(14L), any(), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("PENDING_ITEMS_REMAIN"), any(), any(), eq(true), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
    }

    @Test
    void missingFirstOtpSchedulesExactlyOneNewGeneration() {
        NoonAuthIdentityRecoveryRecord first = recovery(20L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        NoonAuthIdentityRecoveryRecord second = recovery(20L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 4L, 1, 1);
        second.setLastMessageIdHash("old-message");
        NoonAuthIdentityRecoveryRecord exhausted = recovery(20L, NoonAuthRecoveryStatus.WAITING_COOLDOWN, 8L, 2, 2);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 20L, 308L, "PRJ308", "STORE308", 2001L, 4L)
        );
        when(repository.listDueRecoveries(any(), anyInt()))
                .thenReturn(List.of(first), List.of(second), List.of(exhausted));
        when(repository.listPendingItems(20L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString()))
                .thenReturn(blockedState(308L, "PRJ308", 20L, 4L));
        when(gateway.attempt(any()))
                .thenAnswer(invocation -> {
                    reserveOtpSend(invocation);
                    return NoonAuthRecoveryAttemptResult.failed(
                            NoonAuthRecoveryFailureCode.OTP_NOT_FOUND,
                            "old-message",
                            "no mail arrived"
                    );
                })
                .thenAnswer(invocation -> {
                    NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
                    return NoonAuthRecoveryAttemptResult.authenticated(
                            "new-message",
                            List.of(NoonAuthRecoveryProjectResult.recovered(
                                    command.getProjectTargets().get(0),
                                    "sid=recovered"
                            ))
                    );
                });

        assertEquals(1, worker.runOnce());
        assertEquals(1, worker.runOnce());
        assertEquals(1, worker.runOnce());

        verify(gateway, times(2)).attempt(any());
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(20L), any(), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("OTP_NOT_FOUND"), any(), any(), eq(true), any()
        );
    }

    @Test
    void rateLimitNeverTriggersAutomaticSecondSend() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(30L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 30L, 308L, "PRJ308", "STORE308", 3001L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery), List.of());
        when(repository.listPendingItems(30L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(anyLong(), anyString()))
                .thenReturn(blockedState(308L, "PRJ308", 30L, 5L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                    null,
                    "provider rate limited the send"
            );
        });

        worker.runOnce();
        worker.runOnce();

        verify(gateway, times(1)).attempt(any());
        verify(repository).markProjectRecoveryFailed(
                eq(308L), eq("PRJ308"), eq(30L), eq(5L), any(), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD), eq("SEND_RATE_LIMITED"), any(), any()
        );
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(30L), any(), eq(NoonAuthRecoveryStatus.MANUAL_HOLD), anyLong(), anyString(),
                any(), eq("SEND_RATE_LIMITED"), any(), any(), eq(true), any()
        );
    }

    @Test
    void mailboxSnapshotFailureDoesNotConsumeSendIntentOrQuota() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(33L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 33L, 308L, "PRJ308", "STORE308", 3301L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(33L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(308L, "PRJ308"))
                .thenReturn(blockedState(308L, "PRJ308", 33L, 5L));
        when(gateway.attempt(any())).thenReturn(NoonAuthRecoveryAttemptResult.failed(
                NoonAuthRecoveryFailureCode.MAILBOX_UNAVAILABLE,
                null,
                "mailbox snapshot: failed"
        ));

        assertEquals(1, worker.runOnce());

        verify(repository, never()).recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        );
        verify(repository).transitionRecovery(
                eq(33L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), eq("MAILBOX_UNAVAILABLE"), any(), any(), eq(true), any()
        );
    }

    @Test
    void sendIntentCasFailureAtBeforeSendCallbackStopsWithoutPublishingFailureState() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(34L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 34L, 308L, "PRJ308", "STORE308", 3401L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(34L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(308L, "PRJ308"))
                .thenReturn(blockedState(308L, "PRJ308", 34L, 5L));
        when(repository.recordSendIntent(anyLong(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(false);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
            command.beforeOtpSendOrThrow();
            throw new AssertionError("failed send intent CAS must abort before provider send");
        });

        assertEquals(1, worker.runOnce());

        verify(repository, times(1)).recordSendIntent(
                eq(34L), eq(NoonAuthRecoveryStatus.AUTHENTICATING), anyLong(), anyString(), any(), any()
        );
        verify(repository, never()).recordMailboxCorrelation(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any()
        );
        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
    }

    @Test
    void mailboxCorrelationFenceFailureStopsBeforeAnyProjectItemOrTaskSideEffect() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(31L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 31L, 308L, "PRJ308", "STORE308", 3101L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(31L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(308L, "PRJ308"))
                .thenReturn(blockedState(308L, "PRJ308", 31L, 5L));
        when(repository.recordMailboxCorrelation(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any()
        )).thenReturn(false);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                    "correlated-message",
                    "provider rate limited the send"
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(repository, never()).transitionRecovery(
                eq(31L), any(), eq(NoonAuthRecoveryStatus.MANUAL_HOLD), anyLong(), anyString(),
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()
        );
    }

    @Test
    void staleFailureWorkerCannotPublishManualHoldWhenFencedProjectWriteIsRejected() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(32L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, 32L, 308L, "PRJ308", "STORE308", 3201L, 5L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(32L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(308L, "PRJ308"))
                .thenReturn(blockedState(308L, "PRJ308", 32L, 5L));
        when(repository.markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        )).thenReturn(false);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.SEND_RATE_LIMITED,
                    null,
                    "provider rate limited the send"
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).transitionRecovery(
                eq(32L), any(), eq(NoonAuthRecoveryStatus.MANUAL_HOLD), anyLong(), anyString(),
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
    }

    @Test
    void losingDatabaseLeasePreventsAnyOtpSend() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(40L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.tryClaimRecovery(
                anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        )).thenReturn(false);

        assertEquals(0, worker.runOnce());

        verify(gateway, never()).attempt(any());
        verify(repository, never()).recordSendIntent(anyLong(), any(), anyLong(), anyString(), any(), any());
    }

    @Test
    void drainsMoreThanTwoHundredItemsBeforeCompletion() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(50L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        List<NoonAuthRecoveryItemRecord> items = new ArrayList<>();
        for (long index = 1; index <= 201; index++) {
            items.add(item(index, 50L, 307L, "PRJ307", "STORE307", 5000L + index, 6L));
        }
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(50L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 50L, 6L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "message-hash-201",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=recovered"
                    ))
            );
        });

        worker.runOnce();

        verify(repository, times(201)).requeueBlockedTaskAfterRecoveryCas(
                anyLong(), eq(50L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository).completeRecoveryIfDrained(
                eq(50L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS), anyLong(), anyString(),
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void oneBatchRecoversMultipleItemsForSameExactProject() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(92L, NoonAuthRecoveryStatus.COALESCING, 1L, 0, 0);
        recovery.setPredecessorRecoveryId(91L);
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(901L, 92L, 307L, "PRJ307", "STORE307", 9001L, 8L),
                item(902L, 92L, 307L, "PRJ307", "STORE307", 9002L, 8L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(92L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 92L, 8L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "successor-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=successor"
                    ))
            );
        });

        worker.runOnce();

        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(9001L), eq(92L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(9002L), eq(92L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(gateway, times(1)).attempt(any());
    }

    @Test
    void itemArrivingAfterSnapshotForSameProjectReconcilesWithoutAnotherOtpGeneration() {
        NoonAuthIdentityRecoveryRecord first = recovery(93L, NoonAuthRecoveryStatus.COALESCING, 0L, 0, 0);
        NoonAuthIdentityRecoveryRecord reconcile = recovery(
                93L,
                NoonAuthRecoveryStatus.WAITING_COOLDOWN,
                7L,
                1,
                1
        );
        NoonAuthRecoveryItemRecord firstItem = item(
                931L, 93L, 307L, "PRJ307", "STORE307", 9301L, 7L
        );
        NoonAuthRecoveryItemRecord lateItem = item(
                932L, 93L, 307L, "PRJ307", "STORE307", 9302L, 7L
        );
        NoonProjectAuthStateRecord blocked = blockedState(307L, "PRJ307", 93L, 7L);
        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setOwnerUserId(307L);
        healthy.setProjectCode("PRJ307");
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        healthy.setAuthVersion(8L);
        when(repository.listDueRecoveries(any(), anyInt()))
                .thenReturn(List.of(first), List.of(reconcile));
        when(repository.listPendingItems(93L, Integer.MAX_VALUE))
                .thenReturn(List.of(firstItem), List.of(firstItem), List.of(lateItem), List.of());
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blocked, healthy);
        when(repository.completeRecoveryIfDrained(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any(), any(), any()
        )).thenReturn(false, true);
        when(repository.hasPendingItems(93L)).thenReturn(true);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "single-generation-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=single-generation"
                    ))
            );
        });

        worker.runOnce();
        worker.runOnce();

        verify(gateway, times(1)).attempt(any());
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(9301L), eq(93L), any(), anyLong(), anyString(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(9302L), eq(93L), any(), anyLong(), anyString(), any()
        );
    }

    @Test
    void completedRecoveryLateTaskUsesCurrentCookieWithoutGatewayOrSendIntent() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                95L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        NoonAuthRecoveryItemRecord lateItem = item(
                951L, 95L, 307L, "PRJ307", "STORE307", 9501L, 7L
        );
        NoonProjectAuthStateRecord healthy = new NoonProjectAuthStateRecord();
        healthy.setOwnerUserId(307L);
        healthy.setProjectCode("PRJ307");
        healthy.setStatus(NoonProjectAuthStatus.HEALTHY);
        healthy.setActiveRecoveryId(null);
        healthy.setAuthVersion(8L);
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(95L, Integer.MAX_VALUE))
                .thenReturn(List.of(lateItem), List.of());
        when(repository.selectProjectAuthState(307L, "PRJ307")).thenReturn(healthy);

        assertEquals(1, worker.runOnce());

        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(9501L), eq(95L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                anyLong(), anyString(), any()
        );
        verify(repository).completeRecoveryIfDrained(
                eq(95L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                anyLong(), anyString(), any(), any(), any(), any(), any()
        );
        verify(repository, never()).recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        );
        verify(gateway, never()).attempt(any());
    }

    @Test
    void proactivelyReopensManualHoldOnlyWhenConfiguredCredentialFingerprintChanges() {
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                Clock.fixed(Instant.parse("2026-07-16T05:00:00Z"), ZoneOffset.UTC),
                "worker-test",
                "shared@example.com",
                "new-imap-secret"
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of());

        assertEquals(0, worker.runOnce());

        verify(repository).releaseChangedManualHolds(
                anyString(), anyString(), any(), any()
        );
        verify(repository).releaseTerminalProjectHoldsOnConfigChange(
                eq(NoonAuthIdentityKey.fromEmail("shared@example.com")),
                eq(NoonAuthIdentityKey.configFingerprint(
                        "shared@example.com",
                        "new-imap-secret",
                        properties.normalizedTrustedSenderDomains()
                )),
                any()
        );
        verify(repository).promoteReadySuccessors(any(), any());
    }

    @Test
    void configChangeAfterTwoSendsAllowsOneFreshGenerationWithoutErasingIdentityQuota() {
        NoonAuthIdentityRecoveryRecord reset = recovery(
                94L,
                NoonAuthRecoveryStatus.WAITING_COOLDOWN,
                5L,
                0,
                0
        );
        NoonAuthIdentityRecoveryRecord afterFreshSend = recovery(
                94L,
                NoonAuthRecoveryStatus.WAITING_COOLDOWN,
                9L,
                1,
                1
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(941L, 94L, 307L, "PRJ307", "STORE307", 9401L, 12L)
        );
        when(repository.releaseChangedManualHolds(anyString(), anyString(), any(), any())).thenReturn(1, 0);
        when(repository.listDueRecoveries(any(), anyInt()))
                .thenReturn(List.of(reset), List.of(afterFreshSend));
        when(repository.listPendingItems(94L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 94L, 12L));
        when(repository.countIdentitySendsSince(anyString(), any()))
                .thenReturn(2, 2, 3, 3);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
            command.beforeOtpSendOrThrow();
            return NoonAuthRecoveryAttemptResult.failed(
                    NoonAuthRecoveryFailureCode.OTP_NOT_FOUND,
                    null,
                    "fresh credential generation did not receive mail"
            );
        });

        assertEquals(1, worker.runOnce());
        assertEquals(1, worker.runOnce());

        verify(gateway, times(1)).attempt(any());
        verify(repository, times(1)).recordSendIntent(
                eq(94L), any(), anyLong(), anyString(), any(), any()
        );
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(94L), any(), eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                any(), any(), any(), any(), eq(true), any()
        );
    }

    @Test
    void configuredQuotaCannotRaiseSafetyCeilings() {
        properties.setMaxSendsPerHour(99);
        properties.setMaxSendsPerDay(99);

        assertEquals(3, properties.getMaxSendsPerHour());
        assertEquals(6, properties.getMaxSendsPerDay());
    }

    @Test
    void enabledRecoveryRejectsMissingTrustedSenderAllowlistBeforeTouchingQueue() {
        properties.setTrustedSenderDomains(null);
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                Clock.fixed(Instant.parse("2026-07-16T05:00:00Z"), ZoneOffset.UTC),
                "worker-test",
                "shared@example.com",
                "imap-secret"
        );

        assertThrows(IllegalStateException.class, worker::runOnce);

        verify(repository, never()).listDueRecoveries(any(), anyInt());
        verify(gateway, never()).attempt(any());
    }

    @Test
    void interruptedGenerationWaitsOneLeaseThenUsesOnlyRemainingGeneration() {
        NoonAuthIdentityRecoveryRecord interrupted = recovery(
                60L,
                NoonAuthRecoveryStatus.AUTHENTICATING,
                4L,
                1,
                1
        );
        NoonAuthIdentityRecoveryRecord cooled = recovery(
                60L,
                NoonAuthRecoveryStatus.WAITING_COOLDOWN,
                6L,
                1,
                1
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(601L, 60L, 307L, "PRJ307", "STORE307", 6001L, 9L)
        );
        when(repository.listDueRecoveries(any(), anyInt()))
                .thenReturn(List.of(interrupted), List.of(cooled));
        when(repository.listPendingItems(60L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 60L, 9L));
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "remaining-generation-message",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=remaining-generation"
                    ))
            );
        });

        worker.runOnce();
        worker.runOnce();

        verify(gateway, times(1)).attempt(any());
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(60L), eq(NoonAuthRecoveryStatus.AUTHENTICATING),
                eq(NoonAuthRecoveryStatus.WAITING_COOLDOWN), anyLong(), anyString(),
                eq(LocalDateTime.ofInstant(
                        Instant.parse("2026-07-16T05:10:00Z"),
                        ZoneOffset.UTC
                )), eq("SEND_RESULT_UNKNOWN"), any(), any(), eq(true), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(6001L), eq(60L), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
    }

    @Test
    void disabledFeatureAtomicallyDrainsPersistedRecoveriesWithoutCallingGateway() {
        properties.setEnabled(false);

        assertEquals(0, worker.runOnce());

        verify(repository).drainDisabledRecoveries(LocalDateTime.ofInstant(
                Instant.parse("2026-07-16T05:00:00Z"),
                ZoneOffset.UTC
        ));
        verify(repository, never()).listDueRecoveries(any(), anyInt());
        verify(gateway, never()).attempt(any());
    }

    @Test
    void recoveryForOldEmailIdentityIsDrainedWithoutClaimOrGatewayCall() {
        when(repository.listUndrainedIdentityKeysExcept(
                NoonAuthIdentityKey.fromEmail("shared@example.com")
        )).thenReturn(List.of(NoonAuthIdentityKey.fromEmail("old@example.com")));
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of());

        assertEquals(0, worker.runOnce());

        verify(repository).drainIdentityRecoveries(
                NoonAuthIdentityKey.fromEmail("old@example.com"),
                LocalDateTime.ofInstant(Instant.parse("2026-07-16T05:00:00Z"), ZoneOffset.UTC)
        );
        verify(repository, never()).tryClaimRecovery(
                anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
        verify(gateway, never()).attempt(any());
    }

    @Test
    void staleWorkerLosingLeaseCannotPersistCookieOrResumeSourceTask() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                71L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(711L, 71L, 307L, "PRJ307", "STORE307", 7101L, 11L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(71L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 71L, 11L));
        when(repository.renewLease(anyLong(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(true, true, false);
        when(repository.persistRecoveredProjectCookieCas(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                anyString(), anyLong(), any()
        )).thenReturn(false);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = reserveOtpSend(invocation);
            return NoonAuthRecoveryAttemptResult.authenticated(
                    "message-before-lease-loss",
                    List.of(NoonAuthRecoveryProjectResult.recovered(
                            command.getProjectTargets().get(0),
                            "sid=must-not-persist"
                    ))
            );
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
        verify(repository, never()).requeueBlockedTaskAfterRecoveryCas(
                anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        );
        verify(repository, never()).completeRecoveryIfDrained(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void gatewayHeartbeatLeaseLossStopsWithoutPublishingFailureState() {
        NoonAuthIdentityRecoveryRecord recovery = recovery(
                72L,
                NoonAuthRecoveryStatus.COALESCING,
                0L,
                0,
                0
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(721L, 72L, 307L, "PRJ307", "STORE307", 7201L, 11L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(72L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 72L, 11L));
        when(repository.renewLease(anyLong(), any(), anyLong(), anyString(), any(), any()))
                .thenReturn(true, false);
        when(gateway.attempt(any())).thenAnswer(invocation -> {
            NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
            command.heartbeatOrThrow();
            throw new AssertionError("lease heartbeat must throw before provider work");
        });

        assertEquals(1, worker.runOnce());

        verify(repository, never()).recordMailboxCorrelation(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any()
        );
        verify(repository, never()).markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        );
        verify(repository, never()).transitionProjectItems(
                anyLong(), anyLong(), anyString(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any(), any()
        );
        verify(repository, never()).failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        );
    }

    @Test
    void interruptedGenerationWithBothSendIntentsUsedStaysManualHold() {
        NoonAuthIdentityRecoveryRecord exhausted = recovery(
                61L,
                NoonAuthRecoveryStatus.VALIDATING,
                7L,
                2,
                2
        );
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(611L, 61L, 307L, "PRJ307", "STORE307", 6101L, 10L)
        );
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(exhausted));
        when(repository.listPendingItems(61L, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(307L, "PRJ307"))
                .thenReturn(blockedState(307L, "PRJ307", 61L, 10L));

        worker.runOnce();

        verify(gateway, never()).attempt(any());
        verify(repository, atLeastOnce()).transitionRecovery(
                eq(61L), eq(NoonAuthRecoveryStatus.VALIDATING),
                eq(NoonAuthRecoveryStatus.MANUAL_HOLD), anyLong(), anyString(),
                any(), eq("SEND_RESULT_UNKNOWN"), any(), any(), eq(true), any()
        );
    }

    @Test
    void leaseSafetyFloorCoversMailboxPollingAndProjectValidation() {
        properties.setLeaseSeconds(120);

        assertEquals(600L, properties.leaseDuration().getSeconds());
    }

    private NoonAuthRecoveryAttemptCommand reserveOtpSend(InvocationOnMock invocation) {
        NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
        command.beforeOtpSendOrThrow();
        return command;
    }

    private NoonAuthIdentityRecoveryRecord recovery(
            Long id,
            NoonAuthRecoveryStatus status,
            Long version,
            int sendCount,
            int generation
    ) {
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setId(id);
        recovery.setIdentityKey(NoonAuthIdentityKey.fromEmail("shared@example.com"));
        recovery.setStatus(status);
        recovery.setVersionNo(version);
        recovery.setSendAttemptCount(sendCount);
        recovery.setGenerationNo(generation);
        return recovery;
    }

    private NoonAuthRecoveryItemRecord item(
            Long id,
            Long recoveryId,
            Long ownerUserId,
            String projectCode,
            String storeCode,
            Long sourceTaskId,
            Long authVersion
    ) {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(id);
        item.setRecoveryId(recoveryId);
        item.setOwnerUserId(ownerUserId);
        item.setProjectCode(projectCode);
        item.setStoreCode(storeCode);
        item.setSourceTaskId(sourceTaskId);
        item.setExpectedAuthVersion(authVersion);
        item.setStatus(NoonAuthRecoveryItemStatus.PENDING);
        return item;
    }

    private NoonProjectAuthStateRecord blockedState(
            Long ownerUserId,
            String projectCode,
            Long recoveryId,
            Long authVersion
    ) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setOwnerUserId(ownerUserId);
        state.setProjectCode(projectCode);
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(authVersion);
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        return state;
    }
}
