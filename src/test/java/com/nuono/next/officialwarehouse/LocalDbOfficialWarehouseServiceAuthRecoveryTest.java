package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnListSyncThrottleRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import com.nuono.next.web.ApiProblemException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalDbOfficialWarehouseServiceAuthRecoveryTest {
    private OfficialWarehouseMapper mapper;
    private NoonSessionGateway sessionGateway;
    private NoonSalesReportBindingResolver bindingResolver;
    private NoonProjectAuthRecoveryQueue recoveryQueue;
    private NoonPullProjectAuthGate authGate;
    private OfficialWarehouseNoonInboundClient noonInboundClient;
    private LocalDbOfficialWarehouseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        sessionGateway = mock(NoonSessionGateway.class);
        bindingResolver = mock(NoonSalesReportBindingResolver.class);
        recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        authGate = mock(NoonPullProjectAuthGate.class);
        noonInboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        service = new LocalDbOfficialWarehouseService(
                mapper,
                sessionGateway,
                bindingResolver,
                mock(NoonHttpCallLogService.class),
                noonInboundClient,
                new ObjectMapper(),
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(),
                new OfficialWarehouseAppointmentAuthRecovery(recoveryQueue, authGate)
        );
        AppointmentRecord appointment = appointment();
        when(mapper.selectAppointment(308L, 611402L)).thenReturn(appointment);
        when(mapper.markAppointmentRunning(611402L, 901L)).thenReturn(1);
        when(bindingResolver.resolve(any())).thenReturn(binding());
    }

    @Test
    void manualAsnListSyncQueuesSharedRecoveryAndReturnsStructuredPendingProblem() {
        StoreSiteRecord site = storeSite();
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(site);
        when(recoveryQueue.enqueueProject(
                eq(308L),
                eq("PRJ512183"),
                eq("STR512183-NSA")
        )).thenReturn(Optional.of(139L));
        when(sessionGateway.loginWithPersistedCookie(
                eq(308L),
                eq("merchant@example.com"),
                eq("expired-cookie"),
                eq("PRJ512183"),
                eq("STR512183-NSA")
        )).thenThrow(new IllegalStateException(
                "auth_required: Noon Cookie invalid or expired; HTTP 401"
        ));

        assertThatThrownBy(() -> service.syncNoonAsnList(
                access(),
                "STR512183-NSA",
                "SA"
        )).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus().value()).isEqualTo(409);
            assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING");
            assertThat(problem.getCategory()).isEqualTo("AUTH_REQUIRED");
            assertThat(problem.getOperation()).isEqualTo("SYNC_ASN_LIST");
            assertThat(problem.isRetryable()).isTrue();
            assertThat(problem.getDetails())
                    .containsEntry("recoveryId", 139L)
                    .containsEntry("retryAfterSeconds", 60)
                    .containsEntry("storeCode", "STR512183-NSA")
                    .containsEntry("siteCode", "SA");
        });

        verify(mapper, never()).claimOfficialWarehouseAsnListSync(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void targetedAsnSyncUsesSameAuthRecoveryBoundary() {
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(storeSite());
        when(recoveryQueue.enqueueProject(
                308L,
                "PRJ512183",
                "STR512183-NSA"
        )).thenReturn(Optional.of(140L));
        when(sessionGateway.loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        )).thenThrow(new IllegalStateException(
                "auth_required: Noon Cookie invalid or expired; HTTP 401"
        ));

        assertThatThrownBy(() -> service.syncNoonAsnNumbers(
                access(),
                "STR512183-NSA",
                "SA",
                Set.of("A05776177PN"),
                true
        )).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING");
            assertThat(problem.getOperation()).isEqualTo("SYNC_ASN_NUMBERS");
            assertThat(problem.getDetails()).containsEntry("recoveryId", 140L);
        });
    }

    @Test
    void blockedManualAsnSyncWaitsWithoutOpeningAnotherNoonSession() {
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(storeSite());
        when(authGate.isBlocked(308L, "PRJ512183")).thenReturn(true);

        assertThatThrownBy(() -> service.syncNoonAsnList(
                access(),
                "STR512183-NSA",
                "SA"
        )).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING");
            assertThat(problem.getDetails())
                    .containsEntry("authRecoveryStatus", "PENDING")
                    .containsEntry("retryAfterSeconds", 60)
                    .doesNotContainKey("recoveryId");
        });

        verify(sessionGateway, never()).loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        );
        verify(recoveryQueue, never()).enqueueProject(any(), any(), any());
    }

    @Test
    void unavailableRecoveryReturnsExplicitManualAuthorizationProblem() {
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(storeSite());
        when(recoveryQueue.enqueueProject(any(), any(), any())).thenReturn(Optional.empty());
        when(sessionGateway.loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        )).thenThrow(new IllegalStateException(
                "auth_required: Noon Cookie invalid or expired; HTTP 401"
        ));

        assertThatThrownBy(() -> service.syncNoonAsnList(
                access(),
                "STR512183-NSA",
                "SA"
        )).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getStatus().value()).isEqualTo(409);
            assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_AUTH_REQUIRED");
            assertThat(problem.getMessage()).contains("自动恢复暂未启动");
            assertThat(problem.getDetails())
                    .containsEntry("authRecoveryStatus", "MANUAL_ACTION_REQUIRED")
                    .containsEntry("manualActionRequired", true);
        });
    }

    @Test
    void projectAccessMismatchIsNotMisclassifiedAsRecoverableManualSyncAuth() {
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(storeSite());
        IllegalStateException mismatch = new IllegalStateException(
                "auth_required: account does not contain current project PRJ512183"
        );
        when(sessionGateway.loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        )).thenThrow(mismatch);

        assertThatThrownBy(() -> service.syncNoonAsnList(
                access(),
                "STR512183-NSA",
                "SA"
        )).isSameAs(mismatch);

        verify(recoveryQueue, never()).enqueueProject(any(), any(), any());
    }

    @Test
    void ordinaryUpstream502IsNotMisclassifiedAsRecoverableAuth() {
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(storeSite());
        IllegalStateException upstream = new IllegalStateException(
                "Noon upstream unavailable; HTTP 502"
        );
        when(sessionGateway.loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        )).thenThrow(upstream);

        assertThatThrownBy(() -> service.syncNoonAsnList(
                access(),
                "STR512183-NSA",
                "SA"
        )).isSameAs(upstream);

        verify(recoveryQueue, never()).enqueueProject(any(), any(), any());
    }

    @Test
    void providerAuthFailureReleasesOnlyCurrentThrottleClaimBeforeRecovery() {
        when(mapper.selectStoreSite(308L, "STR512183-NSA", "SA")).thenReturn(storeSite());
        AtomicReference<String> claimToken = new AtomicReference<>();
        when(mapper.claimOfficialWarehouseAsnListSync(
                eq(308L),
                eq("STR512183-NSA"),
                eq("SA"),
                any(),
                eq(901L)
        )).thenAnswer(invocation -> {
            claimToken.set(invocation.getArgument(3));
            return 1;
        });
        when(mapper.selectOfficialWarehouseAsnListSyncThrottle(
                308L,
                "STR512183-NSA",
                "SA"
        )).thenAnswer(invocation -> throttle(claimToken.get()));
        when(noonInboundClient.syncAsnList(
                nullable(NoonSessionGateway.NoonSession.class),
                any(),
                any(),
                any()
        ))
                .thenThrow(new IllegalStateException(
                        "auth_required: Noon Cookie invalid or expired; HTTP 401"
                ));
        when(recoveryQueue.enqueueProject(
                308L,
                "PRJ512183",
                "STR512183-NSA"
        )).thenReturn(Optional.of(141L));

        assertThatThrownBy(() -> service.syncNoonAsnList(
                access(),
                "STR512183-NSA",
                "SA"
        )).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
            assertThat(problem.getCode()).isEqualTo("OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING");
            assertThat(problem.getDetails()).containsEntry("recoveryId", 141L);
        });

        verify(mapper).releaseOfficialWarehouseAsnListSync(
                308L,
                "STR512183-NSA",
                "SA",
                claimToken.get()
        );
    }

    @Test
    void expiredCookieQueuesSharedRecoveryAndKeepsSameAppointmentPending() {
        when(recoveryQueue.enqueueProject(
                eq(308L),
                eq("PRJ512183"),
                eq("STR512183-NSA")
        )).thenReturn(Optional.of(139L));
        when(sessionGateway.loginWithPersistedCookie(
                eq(308L),
                eq("merchant@example.com"),
                eq("expired-cookie"),
                eq("PRJ512183"),
                eq("STR512183-NSA")
        )).thenThrow(new IllegalStateException(
                "auth_required: Noon Cookie invalid or expired; HTTP 401"
        ));

        service.runAppointmentOnce(access(), "611402");

        verify(mapper).markAppointmentPendingRetry(
                eq(611402L),
                eq(60),
                eq("AUTH_RECOVERY"),
                eq("AUTH_RECOVERY_PENDING"),
                contains("recoveryId=139"),
                eq(901L)
        );
        verify(mapper, never()).markAppointmentFailed(
                eq(611402L), any(), any(), any(), eq(901L)
        );
    }

    @Test
    void blockedProjectWaitsWithoutOpeningAnotherNoonSession() {
        when(authGate.isBlocked(308L, "PRJ512183")).thenReturn(true);

        service.runAppointmentOnce(access(), "611402");

        verify(sessionGateway, never()).loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        );
        verify(recoveryQueue, never()).enqueueProject(
                any(), any(), any()
        );
        verify(mapper).markAppointmentPendingRetry(
                eq(611402L),
                eq(60),
                eq("AUTH_RECOVERY"),
                eq("AUTH_RECOVERY_PENDING"),
                contains("自动继续原约仓"),
                eq(901L)
        );
    }

    @Test
    void unavailableRecoveryKeepsExistingTerminalAuthFailureBehavior() {
        when(recoveryQueue.enqueueProject(
                any(), any(), any()
        )).thenReturn(Optional.empty());
        when(sessionGateway.loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        )).thenThrow(new IllegalStateException(
                "auth_required: Noon Cookie invalid or expired; HTTP 401"
        ));

        service.runAppointmentOnce(access(), "611402");

        verify(mapper).markAppointmentFailed(
                eq(611402L),
                eq("NOON_CALL"),
                eq("IllegalStateException"),
                contains("auth_required"),
                eq(901L)
        );
    }

    @Test
    void projectAccessMismatchDoesNotQueueAutomaticRecovery() {
        when(sessionGateway.loginWithPersistedCookie(
                any(), any(), any(), any(), any()
        )).thenThrow(new IllegalStateException(
                "auth_required: account does not contain current project PRJ512183"
        ));

        service.runAppointmentOnce(access(), "611402");

        verify(recoveryQueue, never()).enqueueProject(any(), any(), any());
        verify(mapper).markAppointmentFailed(
                eq(611402L),
                eq("NOON_CALL"),
                eq("IllegalStateException"),
                contains("does not contain current project"),
                eq(901L)
        );
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(308L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR512183-NSA"))
                .build();
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                308L,
                512183L,
                "PRJ512183",
                "STR512183-NSA",
                "SA",
                "PARTNER",
                "merchant@example.com",
                null,
                "mail-auth-code",
                "expired-cookie"
        );
    }

    private static AppointmentRecord appointment() {
        AppointmentRecord record = new AppointmentRecord();
        record.id = 611402L;
        record.asnId = 501669L;
        record.ownerUserId = 308L;
        record.logicalStoreId = 512183L;
        record.storeCode = "STR512183-NSA";
        record.siteCode = "SA";
        record.projectCode = "PRJ512183";
        record.localAsnNo = "OWA-501669";
        record.noonAsnNr = "A05776177PN";
        record.totalUnits = 499;
        record.warehouseToPartnerCode = "RUH01S";
        record.warehouseToCode = "W00105371A";
        record.apStartDateValue = LocalDate.now().plusDays(1);
        record.apEndDateValue = LocalDate.now().plusDays(3);
        record.status = "PENDING";
        record.attemptCount = 37;
        return record;
    }

    private static StoreSiteRecord storeSite() {
        StoreSiteRecord site = new StoreSiteRecord();
        site.ownerUserId = 308L;
        site.logicalStoreId = 512183L;
        site.storeCode = "STR512183-NSA";
        site.siteCode = "SA";
        site.projectCode = "PRJ512183";
        return site;
    }

    private static AsnListSyncThrottleRecord throttle(String claimToken) {
        AsnListSyncThrottleRecord throttle = new AsnListSyncThrottleRecord();
        throttle.ownerUserId = 308L;
        throttle.storeCode = "STR512183-NSA";
        throttle.siteCode = "SA";
        throttle.claimToken = claimToken;
        throttle.lastStartedAt = LocalDateTime.now();
        throttle.operatorUserId = 901L;
        return throttle;
    }
}
