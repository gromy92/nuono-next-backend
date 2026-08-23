package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.InMemoryNoonRiskBackoffRepository;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAppointmentTemporaryBackoffTest {

    @Test
    void firstEofAfterManyCapacityPollsUsesTwoMinuteStoreAndFailureTypeBackoff() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        NoonSalesReportBindingResolver bindingResolver = mock(NoonSalesReportBindingResolver.class);
        OfficialWarehouseNoonInboundClient inboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        InMemoryNoonRiskBackoffRepository repository = new InMemoryNoonRiskBackoffRepository();
        LocalDbOfficialWarehouseService service = new LocalDbOfficialWarehouseService(
                mapper, mock(NoonSessionGateway.class), bindingResolver, mock(NoonHttpCallLogService.class),
                inboundClient, new ObjectMapper(), new NoonRiskBackoffGuard(repository),
                new NoonPullFailurePolicy(), OfficialWarehouseAppointmentAuthRecovery.disabled()
        );
        AppointmentRecord appointment = appointment("PENDING", 22, 0L);
        AppointmentRecord running = appointment("RUNNING", 23, 1L);
        OfficialWarehouseAppointmentRunner.NoonAppointmentClient client = mock(
                OfficialWarehouseAppointmentRunner.NoonAppointmentClient.class
        );
        when(mapper.selectAuthorizedAppointment(Map.of("STR108065-NSA", 307L), 611049L)).thenReturn(appointment);
        when(mapper.selectAppointment(307L, 611049L)).thenReturn(running);
        when(mapper.markAppointmentRunning(307L, 611049L, 0L, 901L)).thenReturn(1);
        when(bindingResolver.resolve(any())).thenReturn(binding());
        when(inboundClient.appointmentClient(any(), any(), any(), any(), any())).thenReturn(client);
        when(client.queryAsnDetail(any())).thenThrow(new IllegalStateException(
                "HTTP/1.1 header parser received no bytes"
        ));

        service.runAppointmentOnce(access(), "611049");

        verify(mapper).markAppointmentPendingRetry(
                eq(307L), eq(611049L), eq(1L), eq(120), eq("NOON_ACCESS"),
                eq("NOON_ACCESS_FAILURE"), contains("header parser received no bytes"), eq(901L)
        );
        assertThat(repository.selectLatestHold(NoonRiskBackoffScope.officialWarehouseTemporaryFailure(
                307L, "STR108065-NSA", "SA", "NOON_ACCESS_FAILURE").getScopeKey()).getAttemptCount())
                .isEqualTo(1);
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder().sessionUserId(901L).businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS).storeCodes(Set.of("STR108065-NSA")).build();
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                307L, 108065L, "PRJ108065", "STR108065-NSA", "SA", "PARTNER",
                "merchant@example.com", "persisted-cookie"
        );
    }

    private static AppointmentRecord appointment(String status, int attempts, long version) {
        AppointmentRecord record = new AppointmentRecord();
        record.id = 611049L;
        record.asnId = 501249L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 108065L;
        record.storeCode = "STR108065-NSA";
        record.siteCode = "SA";
        record.projectCode = "PRJ108065";
        record.noonAsnNr = "A05693177PN";
        record.totalUnits = 102;
        record.warehouseToPartnerCode = "RUH01S";
        record.warehouseToCode = "W00105371A";
        record.apStartDateValue = LocalDate.now().plusDays(1);
        record.apEndDateValue = LocalDate.now().plusDays(2);
        record.status = status;
        record.attemptCount = attempts;
        record.executionVersion = version;
        return record;
    }
}
