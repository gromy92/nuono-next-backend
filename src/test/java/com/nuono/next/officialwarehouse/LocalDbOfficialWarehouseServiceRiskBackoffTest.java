package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.InMemoryNoonRiskBackoffRepository;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LocalDbOfficialWarehouseServiceRiskBackoffTest {

    @Test
    void classifiedNoCapacityRemainsNormalAppointmentWaitingState() {
        String failureType = LocalDbOfficialWarehouseService.appointmentRetryFailureType(
                "NOON_CALL",
                "NOON_NO_CAPACITY",
                "Noon 当前没有匹配的可约仓日期或时段。"
        );

        assertThat(failureType).isEqualTo("NO_CAPACITY");
    }

    @Test
    void appointmentEmailOtpRateLimitRecordsAccountWideNoonBackoffAndQueuesRetry() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        NoonSessionGateway noonSessionGateway = mock(NoonSessionGateway.class);
        NoonSalesReportBindingResolver bindingResolver = mock(NoonSalesReportBindingResolver.class);
        NoonHttpCallLogService noonHttpCallLogService = mock(NoonHttpCallLogService.class);
        OfficialWarehouseNoonInboundClient noonInboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        InMemoryNoonRiskBackoffRepository riskRepository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard riskBackoffGuard = new NoonRiskBackoffGuard(riskRepository);
        LocalDbOfficialWarehouseService service = new LocalDbOfficialWarehouseService(
                mapper,
                noonSessionGateway,
                bindingResolver,
                noonHttpCallLogService,
                noonInboundClient,
                new ObjectMapper(),
                riskBackoffGuard,
                new NoonPullFailurePolicy()
        );

        AppointmentRecord appointment = appointment();
        when(mapper.selectAppointment(307L, 611049L)).thenReturn(appointment);
        when(mapper.markAppointmentRunning(611049L, 901L)).thenReturn(1);
        when(bindingResolver.resolve(any())).thenReturn(binding());
        when(noonSessionGateway.loginWithEmailAuthCode(
                eq(307L),
                eq("merchant@example.com"),
                eq("mail-auth-code"),
                eq(null),
                eq("PRJ108065"),
                eq("STR108065-NSA")
        )).thenThrow(new IllegalStateException(
                "Noon emailotp 发送失败：Too many requests, please try again later. | Error Reference: ERR-RYBQ5479"
        ));

        service.runAppointmentOnce(access(), "611049");

        NoonRiskBackoffHold hold = riskRepository.selectLatestHold(
                NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA").getScopeKey()
        );
        assertThat(hold).isNotNull();
        assertThat(hold.getRiskType()).isEqualTo("rate_limited");
        assertThat(hold.getSourceDomain()).isEqualTo("OFFICIAL_WAREHOUSE_APPOINTMENT");
        assertThat(hold.getSourceTaskId()).isEqualTo(611049L);
        assertThat(hold.getDiagnosticSummary()).contains("Too many requests");
        verify(mapper).markAppointmentPendingRetry(
                eq(611049L),
                intThat(seconds -> seconds > 0),
                eq("NOON_RISK_BACKOFF"),
                eq("rate_limited"),
                contains("Too many requests"),
                eq(901L)
        );
    }

    @Test
    void manualRunSkipsNoonCallWhenAppointmentWasAlreadyClaimed() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        NoonSessionGateway noonSessionGateway = mock(NoonSessionGateway.class);
        NoonSalesReportBindingResolver bindingResolver = mock(NoonSalesReportBindingResolver.class);
        NoonHttpCallLogService noonHttpCallLogService = mock(NoonHttpCallLogService.class);
        OfficialWarehouseNoonInboundClient noonInboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        LocalDbOfficialWarehouseService service = new LocalDbOfficialWarehouseService(
                mapper,
                noonSessionGateway,
                bindingResolver,
                noonHttpCallLogService,
                noonInboundClient,
                new ObjectMapper(),
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy()
        );
        AppointmentRecord appointment = appointment();
        when(mapper.selectAppointment(307L, 611049L)).thenReturn(appointment);
        when(mapper.markAppointmentRunning(611049L, 901L)).thenReturn(0);

        service.runAppointmentOnce(access(), "611049");

        verify(bindingResolver, never()).resolve(any());
        verify(noonSessionGateway, never()).loginWithEmailAuthCode(any(), any(), any(), any(), any(), any());
    }

    @Test
    void schedulerSkipsNoonCallWhenDueAppointmentClaimIsLost() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        NoonSessionGateway noonSessionGateway = mock(NoonSessionGateway.class);
        NoonSalesReportBindingResolver bindingResolver = mock(NoonSalesReportBindingResolver.class);
        NoonHttpCallLogService noonHttpCallLogService = mock(NoonHttpCallLogService.class);
        OfficialWarehouseNoonInboundClient noonInboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        LocalDbOfficialWarehouseService service = new LocalDbOfficialWarehouseService(
                mapper,
                noonSessionGateway,
                bindingResolver,
                noonHttpCallLogService,
                noonInboundClient,
                new ObjectMapper(),
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy()
        );
        AppointmentRecord appointment = appointment();
        when(mapper.listDueAppointments(1)).thenReturn(List.of(appointment));
        when(mapper.claimDueAppointmentForRun(611049L, 307L)).thenReturn(0);
        ReflectionTestUtils.setField(service, "appointmentSchedulerEnabled", true);

        service.runAppointmentScheduler();

        verify(mapper).claimDueAppointmentForRun(611049L, 307L);
        verify(bindingResolver, never()).resolve(any());
        verify(noonSessionGateway, never()).loginWithEmailAuthCode(any(), any(), any(), any(), any(), any());
    }

    @Test
    void activeNoonBackoffQueuesAppointmentWithoutOpeningNoonSession() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        NoonSessionGateway noonSessionGateway = mock(NoonSessionGateway.class);
        NoonSalesReportBindingResolver bindingResolver = mock(NoonSalesReportBindingResolver.class);
        NoonHttpCallLogService noonHttpCallLogService = mock(NoonHttpCallLogService.class);
        OfficialWarehouseNoonInboundClient noonInboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        InMemoryNoonRiskBackoffRepository riskRepository = new InMemoryNoonRiskBackoffRepository();
        NoonRiskBackoffGuard riskBackoffGuard = new NoonRiskBackoffGuard(riskRepository);
        LocalDbOfficialWarehouseService service = new LocalDbOfficialWarehouseService(
                mapper,
                noonSessionGateway,
                bindingResolver,
                noonHttpCallLogService,
                noonInboundClient,
                new ObjectMapper(),
                riskBackoffGuard,
                new NoonPullFailurePolicy()
        );
        AppointmentRecord appointment = appointment();
        when(mapper.selectAppointment(307L, 611049L)).thenReturn(appointment);
        riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.allNoon(307L, "STR108065-NSA", "SA"),
                "rate_limited",
                "SALES",
                910001L,
                null,
                "existing emailotp rate limit"
        );

        service.runAppointmentOnce(access(), "611049");

        verify(mapper, never()).markAppointmentRunning(eq(611049L), eq(901L));
        verify(noonSessionGateway, never()).loginWithEmailAuthCode(any(), any(), any(), any(), any(), any());
        verify(mapper).markAppointmentPendingRetry(
                eq(611049L),
                intThat(seconds -> seconds > 0),
                eq("NOON_RISK_BACKOFF"),
                eq("rate_limited"),
                contains("existing emailotp rate limit"),
                eq(901L)
        );
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR108065-NSA"))
                .build();
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                307L,
                108065L,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "PARTNER",
                "merchant@example.com",
                null,
                "mail-auth-code",
                null
        );
    }

    private static AppointmentRecord appointment() {
        AppointmentRecord record = new AppointmentRecord();
        record.id = 611049L;
        record.asnId = 501249L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 108065L;
        record.storeCode = "STR108065-NSA";
        record.siteCode = "SA";
        record.projectCode = "PRJ108065";
        record.localAsnNo = "OWA-501249";
        record.noonAsnNr = "A05693177PN";
        record.totalUnits = 102;
        record.warehouseFrom = "CHIC";
        record.warehouseToPartnerCode = "RUH01S";
        record.warehouseToCode = "W00105371A";
        record.apStartDateValue = LocalDate.now().plusDays(1);
        record.apEndDateValue = LocalDate.now().plusDays(2);
        record.status = "PENDING";
        record.attemptCount = 7;
        return record;
    }
}
