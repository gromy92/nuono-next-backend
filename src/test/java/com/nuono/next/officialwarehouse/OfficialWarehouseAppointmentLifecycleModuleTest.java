package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseAppointmentLifecycleModuleTest {

    @Mock
    private OfficialWarehouseMapper mapper;

    private OfficialWarehouseAppointmentLifecycleModule module;

    @BeforeEach
    void setUp() {
        module = new OfficialWarehouseAppointmentLifecycleModule(mapper);
    }

    @Test
    void createsFirstActiveAppointmentWhileHoldingTheParentAsnLock() {
        AppointmentInsertRecord request = request();
        AppointmentRecord saved = appointment("PENDING", 0L);
        when(mapper.lockAsnForAppointment(307L, 500001L)).thenReturn(500001L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 500001L)).thenReturn(null);
        when(mapper.nextAppointmentId()).thenReturn(610001L);
        when(mapper.insertAppointment(any())).thenReturn(1);
        when(mapper.selectAppointment(307L, 610001L)).thenReturn(saved);

        assertThat(module.saveRequest(request)).isSameAs(saved);

        InOrder writes = inOrder(mapper);
        writes.verify(mapper).lockAsnForAppointment(307L, 500001L);
        writes.verify(mapper).selectActiveAppointmentByAsnForUpdate(307L, 500001L);
        writes.verify(mapper).nextAppointmentId();
        writes.verify(mapper).insertAppointment(request);
        writes.verify(mapper).selectAppointment(307L, 610001L);
    }

    @Test
    void ordinaryUpsertCannotResetRunningAppointment() {
        AppointmentInsertRecord request = request();
        when(mapper.lockAsnForAppointment(307L, 500001L)).thenReturn(500001L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 500001L))
                .thenReturn(appointment("RUNNING", 7L));

        assertThatThrownBy(() -> module.saveRequest(request))
                .isInstanceOf(OfficialWarehouseAppointmentStateConflictException.class)
                .hasMessageContaining("执行中");

        verify(mapper, never()).updateAppointmentRequest(any(), any(Long.class), any(Boolean.class));
        verify(mapper, never()).insertAppointment(any());
    }

    @Test
    void automaticRebookingCanReplaceScheduledRequestWithoutClaimingItEarly() {
        AppointmentInsertRecord request = request();
        AppointmentRecord scheduled = appointment("SCHEDULED", 7L);
        AppointmentRecord pending = appointment("PENDING", 8L);
        when(mapper.lockAsnForAppointment(307L, 500001L)).thenReturn(500001L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 500001L))
                .thenReturn(scheduled);
        when(mapper.updateAppointmentRequest(request, 7L, true)).thenReturn(1);
        when(mapper.selectAppointment(307L, 610001L)).thenReturn(pending);

        assertThat(module.saveRequest(request)).isSameAs(pending);

        verify(mapper).updateAppointmentRequest(request, 7L, true);
        verify(mapper, never()).markAppointmentRunning(any(), any(), any(), any());
    }

    @Test
    void selectedSlotRequestIsSavedAndClaimedAsOneLifecycleOperation() {
        AppointmentInsertRecord request = request();
        AppointmentRecord failed = appointment("FAILED", 2L);
        AppointmentRecord running = appointment("RUNNING", 4L);
        when(mapper.lockAsnForAppointment(307L, 500001L)).thenReturn(500001L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 500001L)).thenReturn(failed);
        when(mapper.updateAppointmentRequest(request, 2L, true)).thenReturn(1);
        when(mapper.markAppointmentRunning(307L, 610001L, 3L, 900L)).thenReturn(1);
        when(mapper.selectAppointment(307L, 610001L)).thenReturn(running);

        OfficialWarehouseAppointmentRunClaim claim =
                module.saveAndClaimSelected(request);

        assertThat(claim.appointment()).isSameAs(running);
        assertThat(claim.executionVersion()).isEqualTo(4L);
    }

    @Test
    void staleWorkerCompletionCannotOverwriteTheCurrentState() {
        AppointmentRecord running = appointment("RUNNING", 8L);
        OfficialWarehouseAppointmentRunClaim claim =
                new OfficialWarehouseAppointmentRunClaim(running, 8L);
        LocalDate date = LocalDate.of(2026, 7, 31);
        when(mapper.markAppointmentScheduled(
                307L, 610001L, 8L, date, 22, "10:00-11:00", 900L
        )).thenReturn(0);

        assertThat(module.completeScheduled(claim, date, 22, "10:00-11:00", 900L))
                .isFalse();
    }

    @Test
    void staleUnknownExecutionRequiresReconciliationBeforeAnotherRun() {
        AppointmentRecord failed = appointment("FAILED", 9L);
        failed.failureType =
                OfficialWarehouseAppointmentReconciliationPolicy.STALE_EXECUTION;

        assertThatThrownBy(() -> module.claimManual(failed, 900L))
                .isInstanceOf(OfficialWarehouseAppointmentStateConflictException.class)
                .hasMessageContaining("对账");

        verify(mapper, never()).markAppointmentRunning(any(), any(), any(), any());
    }

    @Test
    void unknownNoonWriteCannotBeCanceledOrCorrectedWithoutConfirmation() {
        AppointmentRecord failed = appointment("FAILED", 9L);
        failed.failureType =
                OfficialWarehouseAppointmentReconciliationPolicy.NOON_WRITE;
        OfficialWarehouseAppointmentCorrection correction =
                correction("SCHEDULED");

        assertThatThrownBy(() -> module.cancel(failed, 900L))
                .isInstanceOf(OfficialWarehouseAppointmentStateConflictException.class)
                .hasMessageContaining("对账");
        assertThatThrownBy(() -> module.correct(failed, correction, 900L, false))
                .isInstanceOf(OfficialWarehouseAppointmentStateConflictException.class)
                .hasMessageContaining("对账");

        verify(mapper, never()).cancelAppointment(any(), any(), any(), any());
        verify(mapper, never()).correctAppointment(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void noonReconciliationNeverOverwritesRunningExecution() {
        AppointmentInsertRecord seed = request();
        AppointmentRecord running = appointment("RUNNING", 8L);
        when(mapper.lockAsnForAppointment(307L, 500001L)).thenReturn(500001L);
        when(mapper.selectActiveAppointmentByAsnForUpdate(307L, 500001L))
                .thenReturn(running);

        OfficialWarehouseAppointmentReconcileOutcome outcome =
                module.reconcileFromNoon(seed, correction("SCHEDULED"), true);

        assertThat(outcome.appointment()).isSameAs(running);
        assertThat(outcome.changed()).isFalse();
        verify(mapper, never()).correctAppointment(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    private static OfficialWarehouseAppointmentCorrection correction(String status) {
        return new OfficialWarehouseAppointmentCorrection(
                status,
                LocalDate.of(2026, 7, 31),
                22,
                "10:00-11:00",
                null,
                null,
                null,
                null,
                null
        );
    }

    private static AppointmentInsertRecord request() {
        AppointmentInsertRecord row = new AppointmentInsertRecord();
        row.asnId = 500001L;
        row.ownerUserId = 307L;
        row.operatorUserId = 900L;
        row.status = "PENDING";
        return row;
    }

    private static AppointmentRecord appointment(String status, Long version) {
        AppointmentRecord row = new AppointmentRecord();
        row.id = 610001L;
        row.asnId = 500001L;
        row.ownerUserId = 307L;
        row.status = status;
        row.executionVersion = version;
        return row;
    }
}
