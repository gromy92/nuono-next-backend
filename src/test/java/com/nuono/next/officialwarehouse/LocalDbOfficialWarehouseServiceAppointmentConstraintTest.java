package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.NoonAppointmentClient;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.UpsertAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LocalDbOfficialWarehouseServiceAppointmentConstraintTest {

    @Test
    void appointmentRequestUpdateCannotOverwriteRunningClaim() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "updateAppointmentRequest",
                AppointmentInsertRecord.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("status <> 'RUNNING'");
    }

    @Test
    void savingNewConstraintsFailsWhenSchedulerAlreadyClaimedAppointment() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        LocalDbOfficialWarehouseService service = service(
                mapper,
                mock(NoonSessionGateway.class),
                mock(NoonSalesReportBindingResolver.class),
                mock(OfficialWarehouseNoonInboundClient.class)
        );
        AsnRecord asn = asn();
        AppointmentRecord running = appointment(null);
        running.status = "RUNNING";
        when(mapper.selectAsn(307L, 501819L)).thenReturn(asn);
        when(mapper.selectLatestAppointmentByAsn(307L, 501819L)).thenReturn(running);
        when(mapper.selectAppointment(307L, 611517L)).thenReturn(running);
        when(mapper.listAsnShippingBatchLinks(501819L)).thenReturn(List.of());
        when(mapper.listAsnLines(501819L)).thenReturn(List.of());
        when(mapper.listAsnInboundReceipts(eq(307L), any())).thenReturn(List.of());
        when(mapper.updateAppointmentRequest(any())).thenReturn(0);

        assertThatThrownBy(() -> service.upsertAppointment(access(), "501819", command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正在执行")
                .hasMessageContaining("重新提交");
    }

    @Test
    void schedulerReloadsClaimedAppointmentConstraintsBeforeNoonScheduleWrite() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonSalesReportBindingResolver bindingResolver = mock(NoonSalesReportBindingResolver.class);
        OfficialWarehouseNoonInboundClient inboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        NoonAppointmentClient noonClient = mock(NoonAppointmentClient.class);
        LocalDbOfficialWarehouseService service = service(mapper, sessionGateway, bindingResolver, inboundClient);

        AppointmentRecord staleCandidate = appointment(null);
        AppointmentRecord authoritative = appointment("4am,5am,6am");
        authoritative.status = "RUNNING";
        LocalDate capacityDate = LocalDate.now().plusDays(1);
        when(mapper.listDueAppointments(1)).thenReturn(List.of(staleCandidate));
        when(mapper.claimDueAppointmentForRun(611517L, 307L)).thenReturn(1);
        when(mapper.selectAppointment(307L, 611517L)).thenReturn(authoritative);
        when(bindingResolver.resolve(any())).thenReturn(binding());
        when(inboundClient.appointmentClient(any(), any(), any(), any())).thenReturn(noonClient);
        when(noonClient.queryAsnDetail(any())).thenReturn(
                new AsnDetail("sealed"),
                new AsnDetail("sealed"),
                new AsnDetail("scheduled")
        );
        when(noonClient.setWarehouses(any())).thenReturn(true);
        when(noonClient.queryDayCapacity(any())).thenReturn(List.of(capacityDate.toString()));
        when(noonClient.querySlotCapacity(any(), eq(capacityDate)))
                .thenReturn(List.of(new SlotCapacity(7, "11am-2pm")));
        when(noonClient.schedule(any(), any(), any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "appointmentSchedulerEnabled", true);

        service.runAppointmentScheduler();

        verify(noonClient, never()).schedule(any(), any(), any());
        verify(mapper).markAppointmentPendingRetry(
                eq(611517L),
                anyInt(),
                eq("SCHEDULE"),
                eq("NO_CAPACITY"),
                contains("没有匹配"),
                eq(307L)
        );
    }

    private static LocalDbOfficialWarehouseService service(
            OfficialWarehouseMapper mapper,
            NoonSessionGateway sessionGateway,
            NoonSalesReportBindingResolver bindingResolver,
            OfficialWarehouseNoonInboundClient inboundClient
    ) {
        return new LocalDbOfficialWarehouseService(
                mapper,
                sessionGateway,
                bindingResolver,
                mock(NoonHttpCallLogService.class),
                inboundClient,
                new ObjectMapper(),
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(),
                OfficialWarehouseAppointmentAuthRecovery.disabled()
        );
    }

    private static AppointmentRecord appointment(String timeRange) {
        AppointmentRecord record = new AppointmentRecord();
        record.id = 611517L;
        record.asnId = 501819L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 69486L;
        record.storeCode = "STR69486-NSA";
        record.siteCode = "SA";
        record.projectCode = "PRJ69486";
        record.localAsnNo = "OWA-501819";
        record.noonAsnNr = "A05834975PN";
        record.totalUnits = 753;
        record.warehouseToPartnerCode = "RUH01S";
        record.warehouseToCode = "W00105371A";
        record.apStartDateValue = LocalDate.now().plusDays(1);
        record.apEndDateValue = record.apStartDateValue;
        record.apTimeRange = timeRange;
        record.availableToday = false;
        record.status = "PENDING";
        record.attemptCount = 126;
        return record;
    }

    private static AsnRecord asn() {
        AsnRecord record = new AsnRecord();
        record.id = 501819L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 69486L;
        record.storeCode = "STR69486-NSA";
        record.siteCode = "SA";
        record.projectCode = "PRJ69486";
        record.localAsnNo = "OWA-501819";
        record.noonAsnNr = "A05834975PN";
        record.status = "LINES_CREATED";
        record.totalQuantity = 753;
        record.selectedWarehousePartnerCode = "RUH01S";
        record.selectedWarehouseCode = "W00105371A";
        return record;
    }

    private static UpsertAppointmentCommand command() {
        UpsertAppointmentCommand command = new UpsertAppointmentCommand();
        command.warehouseToPartnerCode = "RUH01S";
        command.warehouseToCode = "W00105371A";
        command.apStartDate = LocalDate.now().plusDays(1).toString();
        command.apEndDate = command.apStartDate;
        command.apTimeRange = "4am,5am,6am";
        command.availableToday = false;
        return command;
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR69486-NSA"))
                .build();
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                307L,
                69486L,
                "PRJ69486",
                "STR69486-NSA",
                "SA",
                "69486",
                "merchant@example.com",
                null,
                "mail-auth-code",
                "persisted-cookie"
        );
    }
}
