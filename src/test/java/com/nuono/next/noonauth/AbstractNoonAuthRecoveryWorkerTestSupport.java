package com.nuono.next.noonauth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptCommand;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.invocation.InvocationOnMock;

abstract class AbstractNoonAuthRecoveryWorkerTestSupport {
    protected NoonAuthRecoveryRepository repository;
    protected NoonAuthTransientBackoffRepository transientBackoffRepository;
    protected NoonAuthRecoveryGateway gateway;
    protected NoonAuthRecoveryProperties properties;
    protected NoonAuthRecoveryWorker worker;

    @BeforeEach
    void setUpWorker() {
        repository = mock(NoonAuthRecoveryRepository.class);
        transientBackoffRepository = mock(NoonAuthTransientBackoffRepository.class);
        gateway = mock(NoonAuthRecoveryGateway.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setAllProjectsEnabled(true);
        properties.setStartupAuditEnabled(true);
        properties.setCoalesceSeconds(0);
        properties.setMinResendSeconds(60);
        properties.setTrustedSenderDomains("noon.com");
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-16T05:00:00Z"),
                ZoneOffset.UTC
        );
        when(transientBackoffRepository.resolveLogicalStoreId(anyLong(), anyString()))
                .thenAnswer(invocation -> (Long) invocation.getArgument(0) + 6694L);
        when(transientBackoffRepository.listActiveHolds(anyLong(), any())).thenReturn(List.of());
        when(transientBackoffRepository.incrementFailure(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transientBackoffRepository.resetForRecovery(anyLong(), anyLong(), any(), any()))
                .thenReturn(true);
        worker = new NoonAuthRecoveryWorker(
                repository,
                properties,
                gateway,
                new NoonAuthTransientBackoffGuard(transientBackoffRepository, clock),
                clock,
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
        when(repository.recordSendIntent(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.renewLease(
                anyLong(), any(), anyLong(), anyString(), any(), any()
        )).thenReturn(true);
        when(repository.recordMailboxCorrelation(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any()
        )).thenReturn(true);
        when(repository.completeRecoveryIfDrained(
                anyLong(), any(), anyLong(), anyString(), any(), any(), any(), any(), any()
        )).thenReturn(true);
        when(repository.persistRecoveredProjectCookieCas(
                anyLong(), anyString(), anyLong(), anyLong(), any(), anyLong(), anyString(),
                anyString(), anyString(), anyLong(), any()
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
    }

    protected NoonAuthRecoveryAttemptCommand reserveOtpSend(InvocationOnMock invocation) {
        NoonAuthRecoveryAttemptCommand command = invocation.getArgument(0);
        command.beforeOtpSendOrThrow();
        return command;
    }

    protected NoonAuthIdentityRecoveryRecord recovery(
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

    protected NoonAuthRecoveryItemRecord item(
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

    protected NoonProjectAuthStateRecord blockedState(
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

    protected NoonAuthTransientBackoffState transientHold(
            Long logicalStoreId,
            NoonTransientErrorType errorType,
            LocalDateTime blockedUntil,
            Long recoveryId
    ) {
        NoonAuthTransientBackoffState hold = new NoonAuthTransientBackoffState();
        hold.setLogicalStoreId(logicalStoreId);
        hold.setErrorType(errorType);
        hold.setAttemptCount(1);
        hold.setBlockedUntil(blockedUntil);
        hold.setSourceRecoveryId(recoveryId);
        return hold;
    }

    protected static final class MutableClock extends Clock {
        private Instant current;
        private final ZoneId zone;

        protected MutableClock(Instant current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId targetZone) {
            return new MutableClock(current, targetZone);
        }

        @Override
        public Instant instant() {
            return current;
        }

        protected void setInstant(Instant current) {
            this.current = current;
        }
    }
}
