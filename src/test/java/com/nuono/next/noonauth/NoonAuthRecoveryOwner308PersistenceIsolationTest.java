package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryOwner308PersistenceIsolationTest {
    private static final Long RECOVERY_ID = 308001L;

    private NoonAuthRecoveryRepository repository;
    private NoonAuthRecoveryGateway gateway;
    private NoonAuthRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(NoonAuthRecoveryRepository.class);
        gateway = mock(NoonAuthRecoveryGateway.class);
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(0);
        properties.setTrustedSenderDomains("noon.com");
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                Clock.fixed(Instant.parse("2026-07-23T07:00:00Z"), ZoneOffset.UTC),
                "owner-308-isolation-test",
                "owner308@example.com",
                "test-only-secret"
        );
        stubRecoveryFences();
    }

    @Test
    void persistsOnlyTheMatchingOwner308ProjectCookieWhenOneProjectFailsValidation() {
        List<NoonAuthRecoveryItemRecord> items = List.of(
                item(1L, "PRJ100085", "STR100085-NAE", 1001L, 2L),
                item(2L, "PRJ101128", "STR101128-NAE", 1002L, 5L),
                item(3L, "PRJ102858", "STR102858-NAE", 1003L, 8L)
        );
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setId(RECOVERY_ID);
        recovery.setIdentityKey(NoonAuthIdentityKey.fromEmail("owner308@example.com"));
        recovery.setStatus(NoonAuthRecoveryStatus.COALESCING);
        recovery.setVersionNo(0L);
        recovery.setSendAttemptCount(0);
        recovery.setGenerationNo(0);
        when(repository.listDueRecoveries(any(), anyInt())).thenReturn(List.of(recovery));
        when(repository.listPendingItems(RECOVERY_ID, Integer.MAX_VALUE)).thenReturn(items);
        when(repository.selectProjectAuthState(308L, "PRJ100085"))
                .thenReturn(blockedState("PRJ100085", 2L));
        when(repository.selectProjectAuthState(308L, "PRJ101128"))
                .thenReturn(blockedState("PRJ101128", 5L));
        when(repository.selectProjectAuthState(308L, "PRJ102858"))
                .thenReturn(blockedState("PRJ102858", 8L));
        when(gateway.attempt(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));

        assertEquals(1, worker.runOnce());

        verifyPersisted("PRJ100085", 2L, "sid=PRJ100085; projectCode=PRJ100085");
        verifyPersisted("PRJ102858", 8L, "sid=PRJ102858; projectCode=PRJ102858");
        verify(repository, never()).persistRecoveredProjectCookieCas(
                eq(308L), eq("PRJ101128"), eq(RECOVERY_ID), anyLong(), any(), anyLong(),
                anyString(), anyString(), eq(308L), any()
        );
        verify(repository).markProjectRecoveryFailed(
                eq(308L), eq("PRJ101128"), eq(RECOVERY_ID), eq(5L),
                eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS), anyLong(), anyString(),
                eq(NoonProjectAuthStatus.MANUAL_HOLD),
                eq("COOKIE_VALIDATION_FAILED"), anyString(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(1001L), eq(RECOVERY_ID), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository).requeueBlockedTaskAfterRecoveryCas(
                eq(1003L), eq(RECOVERY_ID), eq(NoonAuthRecoveryStatus.RECOVERING_PULLS),
                anyLong(), anyString(), any()
        );
        verify(repository, never()).requeueBlockedTaskAfterRecoveryCas(
                eq(1002L), anyLong(), any(), anyLong(), anyString(), any()
        );
        verify(repository).failBlockedTaskAfterRecovery(
                eq(1002L), eq(RECOVERY_ID), eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS),
                anyLong(), anyString(), eq("COOKIE_VALIDATION_FAILED"), anyString(), any()
        );
    }

    private NoonAuthRecoveryAttemptResult result(NoonAuthRecoveryAttemptCommand command) {
        command.beforeOtpSendOrThrow();
        return NoonAuthRecoveryAttemptResult.authenticated(
                "owner-308-message-hash",
                List.of(
                        NoonAuthRecoveryProjectResult.recovered(
                                command.getProjectTargets().get(0),
                                "sid=PRJ100085; projectCode=PRJ100085"
                        ),
                        NoonAuthRecoveryProjectResult.failed(
                                command.getProjectTargets().get(1),
                                NoonAuthRecoveryProjectResult.Code.COOKIE_VALIDATION_FAILED,
                                "project cookie validation: target project not confirmed"
                        ),
                        NoonAuthRecoveryProjectResult.recovered(
                                command.getProjectTargets().get(2),
                                "sid=PRJ102858; projectCode=PRJ102858"
                        )
                )
        );
    }

    private void verifyPersisted(String projectCode, Long authVersion, String cookie) {
        verify(repository).persistRecoveredProjectCookieCas(
                eq(308L), eq(projectCode), eq(RECOVERY_ID), eq(authVersion),
                eq(NoonAuthRecoveryStatus.APPLYING_PROJECTS), anyLong(), anyString(),
                eq(cookie), eq(308L), any()
        );
    }

    private NoonAuthRecoveryItemRecord item(
            Long id,
            String projectCode,
            String storeCode,
            Long taskId,
            Long authVersion
    ) {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setId(id);
        item.setRecoveryId(RECOVERY_ID);
        item.setOwnerUserId(308L);
        item.setProjectCode(projectCode);
        item.setStoreCode(storeCode);
        item.setSourceTaskId(taskId);
        item.setExpectedAuthVersion(authVersion);
        item.setStatus(NoonAuthRecoveryItemStatus.PENDING);
        return item;
    }

    private NoonProjectAuthStateRecord blockedState(String projectCode, Long authVersion) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setOwnerUserId(308L);
        state.setProjectCode(projectCode);
        state.setActiveRecoveryId(RECOVERY_ID);
        state.setAuthVersion(authVersion);
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        return state;
    }

    private void stubRecoveryFences() {
        when(repository.tryClaimRecovery(
                anyLong(), any(), anyLong(), anyString(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.transitionRecovery(
                anyLong(), any(), any(), anyLong(), anyString(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any()
        )).thenReturn(true);
        when(repository.renewLease(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.recordMailboxCorrelation(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any()
        )).thenReturn(true);
        when(repository.markProjectRecovering(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        )).thenReturn(true);
        when(repository.persistRecoveredProjectCookieCas(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                anyString(), anyLong(), any()
        )).thenReturn(true);
        when(repository.markProjectRecoveryFailed(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                any(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.failBlockedTaskAfterRecovery(
                anyLong(), anyLong(), any(), anyLong(), anyString(), anyString(), anyString(), any()
        )).thenReturn(true);
        when(repository.transitionRecoveryItem(
                anyLong(), anyLong(), any(), any(), any(), anyLong(), anyString(),
                any(), any(), any(), any()
        )).thenReturn(true);
        when(repository.requeueBlockedTaskAfterRecoveryCas(
                anyLong(), anyLong(), any(), anyLong(), anyString(), any()
        )).thenReturn(true);
        when(repository.completeRecoveryIfDrained(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any(), any(), any()
        )).thenReturn(true);
    }
}
