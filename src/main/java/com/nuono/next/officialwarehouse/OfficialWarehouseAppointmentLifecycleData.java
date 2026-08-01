package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.time.LocalDate;

final class OfficialWarehouseAppointmentReconciliationPolicy {
    static final String STALE_EXECUTION =
            "STALE_EXECUTION_RECONCILIATION_REQUIRED";
    static final String NOON_WRITE =
            "NOON_WRITE_RECONCILIATION_REQUIRED";

    private OfficialWarehouseAppointmentReconciliationPolicy() {
    }

    static boolean requiresReconciliation(String failureType) {
        return STALE_EXECUTION.equals(failureType)
                || NOON_WRITE.equals(failureType);
    }

    static boolean isUnknownNoonWrite(Exception exception) {
        if (!(exception instanceof NoonOperationException)) {
            return false;
        }
        String operation = ((NoonOperationException) exception)
                .getClassification()
                .getOperation();
        return "SET_WAREHOUSES".equals(operation)
                || "RESCHEDULE_ASN".equals(operation)
                || "SCHEDULE_APPOINTMENT".equals(operation);
    }

    static void ensureCancellable(AppointmentRecord appointment) {
        if (requiresReconciliation(appointment.failureType)) {
            throw new OfficialWarehouseAppointmentStateConflictException(
                    "该记录存在未知 Noon 写结果，不能取消；请先完成远端对账。"
            );
        }
    }

    static void ensureCorrectable(
            AppointmentRecord appointment,
            boolean reconciliationConfirmed
    ) {
        if ("RUNNING".equals(appointment.status)) {
            throw new OfficialWarehouseAppointmentStateConflictException(
                    "约仓正在执行，不能订正；请等待执行结束或先完成远端对账。"
            );
        }
        if (requiresReconciliation(appointment.failureType)
                && !reconciliationConfirmed) {
            throw new OfficialWarehouseAppointmentStateConflictException(
                    "该记录存在未知 Noon 写结果；请完成远端对账并明确确认后再订正。"
            );
        }
    }

    static void ensureClaimable(AppointmentRecord appointment) {
        if (appointment == null
                || (!"PENDING".equals(appointment.status)
                && !"FAILED".equals(appointment.status))) {
            throw new OfficialWarehouseAppointmentStateConflictException(
                    "只有待执行或失败的约仓可以再次运行。"
            );
        }
        if (requiresReconciliation(appointment.failureType)) {
            throw new OfficialWarehouseAppointmentStateConflictException(
                    "上次执行结果未知，请先与 Noon 对账并订正后再运行。"
            );
        }
    }
}

final class OfficialWarehouseAppointmentRunClaim {
    private final AppointmentRecord appointment;
    private final long executionVersion;

    OfficialWarehouseAppointmentRunClaim(
            AppointmentRecord appointment,
            long executionVersion
    ) {
        this.appointment = appointment;
        this.executionVersion = executionVersion;
    }

    AppointmentRecord appointment() {
        return appointment;
    }

    long executionVersion() {
        return executionVersion;
    }
}

final class OfficialWarehouseAppointmentCorrection {
    private final String status;
    private final LocalDate appointmentDate;
    private final Integer slotId;
    private final String appointmentTime;
    private final String gate;
    private final String docks;
    private final String failureType;
    private final String errorStage;
    private final String errorMessage;

    OfficialWarehouseAppointmentCorrection(
            String status,
            LocalDate appointmentDate,
            Integer slotId,
            String appointmentTime,
            String gate,
            String docks,
            String failureType,
            String errorStage,
            String errorMessage
    ) {
        this.status = status;
        this.appointmentDate = appointmentDate;
        this.slotId = slotId;
        this.appointmentTime = appointmentTime;
        this.gate = gate;
        this.docks = docks;
        this.failureType = failureType;
        this.errorStage = errorStage;
        this.errorMessage = errorMessage;
    }

    String status() {
        return status;
    }

    LocalDate appointmentDate() {
        return appointmentDate;
    }

    Integer slotId() {
        return slotId;
    }

    String appointmentTime() {
        return appointmentTime;
    }

    String gate() {
        return gate;
    }

    String docks() {
        return docks;
    }

    String failureType() {
        return failureType;
    }

    String errorStage() {
        return errorStage;
    }

    String errorMessage() {
        return errorMessage;
    }
}

final class OfficialWarehouseAppointmentReconcileOutcome {
    private final AppointmentRecord appointment;
    private final boolean changed;

    OfficialWarehouseAppointmentReconcileOutcome(
            AppointmentRecord appointment,
            boolean changed
    ) {
        this.appointment = appointment;
        this.changed = changed;
    }

    AppointmentRecord appointment() {
        return appointment;
    }

    boolean changed() {
        return changed;
    }
}

final class OfficialWarehouseAppointmentPreparedRequest {
    final Long ownerUserId;
    final Long appointmentId;
    final long executionVersion;

    OfficialWarehouseAppointmentPreparedRequest(
            Long ownerUserId,
            Long appointmentId,
            long executionVersion
    ) {
        this.ownerUserId = ownerUserId;
        this.appointmentId = appointmentId;
        this.executionVersion = executionVersion;
    }
}
