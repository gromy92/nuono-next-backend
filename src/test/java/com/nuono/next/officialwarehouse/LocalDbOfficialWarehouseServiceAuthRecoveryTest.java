package com.nuono.next.officialwarehouse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalDbOfficialWarehouseServiceAuthRecoveryTest {
    private OfficialWarehouseMapper mapper;
    private NoonSessionGateway sessionGateway;
    private NoonSalesReportBindingResolver bindingResolver;
    private NoonProjectAuthRecoveryQueue recoveryQueue;
    private NoonPullProjectAuthGate authGate;
    private LocalDbOfficialWarehouseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        sessionGateway = mock(NoonSessionGateway.class);
        bindingResolver = mock(NoonSalesReportBindingResolver.class);
        recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        authGate = mock(NoonPullProjectAuthGate.class);
        service = new LocalDbOfficialWarehouseService(
                mapper,
                sessionGateway,
                bindingResolver,
                mock(NoonHttpCallLogService.class),
                mock(OfficialWarehouseNoonInboundClient.class),
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
    void expiredCookieQueuesSharedRecoveryAndKeepsSameAppointmentPending() {
        when(recoveryQueue.enqueueProject(
                eq(308L),
                eq("PRJ512183"),
                eq("STR512183-NSA")
        )).thenReturn(Optional.of(139L));
        when(sessionGateway.loginWithPersistedCookiePinnedEgress(
                eq(308L),
                eq("merchant@example.com"),
                eq("expired-cookie"),
                eq("PRJ512183"),
                eq("STR512183-NSA"),
                eq("fbn.noon.partners"),
                eq(443)
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

        verify(sessionGateway, never()).loginWithPersistedCookiePinnedEgress(
                any(), any(), any(), any(), any(), any(), anyInt()
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
        when(sessionGateway.loginWithPersistedCookiePinnedEgress(
                any(), any(), any(), any(), any(), any(), anyInt()
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
        when(sessionGateway.loginWithPersistedCookiePinnedEgress(
                any(), any(), any(), any(), any(), any(), anyInt()
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
}
