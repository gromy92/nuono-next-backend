package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class LocalDbOfficialWarehouseServiceAppointmentConstraintTest {

    @Test
    void scheduledProjectionMakesClaimedTaskAnExplicitRebooking() {
        AppointmentRecord appointment = appointment("RUNNING", 8L, null);
        appointment.appointmentDate = "2026-08-01";
        appointment.appointmentTime = "11am-2pm";
        appointment.apSuccessTime = "2026-07-31 06:02:00";

        AppointmentTask task = LocalDbOfficialWarehouseService.toAppointmentTask(appointment);

        assertThat(task.rebookingRequested).isTrue();
        assertThat(task.previousAppointmentDate).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(task.previousAppointmentTime).isEqualTo("11am-2pm");
    }

    @Test
    void appointmentRequestUpdateUsesVersionAndAllowedStateFences() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "updateAppointmentRequest",
                AppointmentInsertRecord.class,
                Long.class,
                boolean.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("execution_version = #{expectedExecutionVersion}")
                .contains("status IN ('PENDING', 'FAILED')")
                .contains("#{allowScheduled} = TRUE AND status = 'SCHEDULED'")
                .doesNotContain("'RUNNING'");
    }

    @Test
    void appointmentRequestUpdatePreservesConfirmedScheduleProjection() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "updateAppointmentRequest",
                AppointmentInsertRecord.class,
                Long.class,
                boolean.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .doesNotContain("appointment_date = NULL")
                .doesNotContain("appointment_slot_id = NULL")
                .doesNotContain("appointment_time = NULL")
                .doesNotContain("ap_success_time = NULL");
    }

    @Test
    void scheduledPersistenceDoesNotReplaceConfirmedResultWithNullRemoteFields() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "markAppointmentScheduled",
                Long.class,
                Long.class,
                Long.class,
                LocalDate.class,
                Integer.class,
                String.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("appointment_date = COALESCE(#{appointmentDate}, appointment_date)")
                .contains("appointment_slot_id = COALESCE(#{slotId}, appointment_slot_id)")
                .contains("appointment_time = COALESCE(#{appointmentTime}, appointment_time)");
    }

    @Test
    void savingNewConstraintsCannotOverwriteAClaimedAppointment() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        OfficialWarehouseAppointmentLifecycleModule lifecycle =
                new OfficialWarehouseAppointmentLifecycleModule(mapper);
        AppointmentInsertRecord request = request();
        AppointmentRecord running = appointment("RUNNING", 7L, null);
        when(mapper.lockAsnForAppointment(307L, 501819L)).thenReturn(501819L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 501819L))
                .thenReturn(running);

        assertThatThrownBy(() -> lifecycle.saveRequest(request))
                .isInstanceOf(OfficialWarehouseAppointmentStateConflictException.class)
                .hasMessageContaining("执行中");

        verify(mapper, never()).updateAppointmentRequest(any(), anyLong(), anyBoolean());
        verify(mapper, never()).insertAppointment(any());
    }

    @Test
    void concurrentClaimVersionChangeRejectsConstraintUpdate() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        OfficialWarehouseAppointmentLifecycleModule lifecycle =
                new OfficialWarehouseAppointmentLifecycleModule(mapper);
        AppointmentInsertRecord request = request();
        AppointmentRecord pending = appointment("PENDING", 7L, null);
        when(mapper.lockAsnForAppointment(307L, 501819L)).thenReturn(501819L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 501819L))
                .thenReturn(pending);
        when(mapper.updateAppointmentRequest(request, 7L, true)).thenReturn(0);

        assertThatThrownBy(() -> lifecycle.saveRequest(request))
                .isInstanceOf(OfficialWarehouseAppointmentStateConflictException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    void schedulerClaimReloadsAuthoritativeTimeConstraints() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        OfficialWarehouseAppointmentLifecycleModule lifecycle =
                new OfficialWarehouseAppointmentLifecycleModule(mapper);
        AppointmentRecord candidate = appointment("PENDING", 5L, null);
        AppointmentRecord authoritative = appointment("RUNNING", 6L, "4am,5am,6am");
        when(mapper.claimDueAppointmentForRun(307L, 611517L, 5L, 900L))
                .thenReturn(1);
        when(mapper.selectAppointment(307L, 611517L)).thenReturn(authoritative);

        OfficialWarehouseAppointmentRunClaim claim =
                lifecycle.claimDue(candidate, 900L);

        assertThat(claim.appointment()).isSameAs(authoritative);
        assertThat(claim.appointment().apTimeRange).isEqualTo("4am,5am,6am");
        assertThat(claim.executionVersion()).isEqualTo(6L);
        InOrder reload = inOrder(mapper);
        reload.verify(mapper).claimDueAppointmentForRun(307L, 611517L, 5L, 900L);
        reload.verify(mapper).selectAppointment(307L, 611517L);
    }

    private static AppointmentInsertRecord request() {
        AppointmentInsertRecord row = new AppointmentInsertRecord();
        row.asnId = 501819L;
        row.ownerUserId = 307L;
        row.operatorUserId = 900L;
        row.status = "PENDING";
        return row;
    }

    private static AppointmentRecord appointment(
            String status,
            Long version,
            String timeRange
    ) {
        AppointmentRecord row = new AppointmentRecord();
        row.id = 611517L;
        row.asnId = 501819L;
        row.ownerUserId = 307L;
        row.status = status;
        row.executionVersion = version;
        row.apTimeRange = timeRange;
        return row;
    }
}
