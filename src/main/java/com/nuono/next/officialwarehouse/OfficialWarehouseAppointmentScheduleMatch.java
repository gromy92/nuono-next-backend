package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.inRange;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.matchesTimeRange;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.parseAcceptedHours;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.sameAppointmentTime;

import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.RunResult;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import java.time.LocalDate;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAppointmentScheduleMatch {

    private OfficialWarehouseAppointmentScheduleMatch() {
    }

    static RunResult automaticDecision(AppointmentTask task, AsnDetail detail) {
        if (matchesAutomaticTarget(task, detail)) {
            return RunResult.alreadyScheduled(detail);
        }
        if (matchesCompletePreviousAppointment(task, detail)) {
            return null;
        }
        return mismatch();
    }

    static RunResult selectedDecision(
            AppointmentTask task,
            AsnDetail detail,
            LocalDate appointmentDate,
            SlotCapacity slot
    ) {
        if (matchesExactTarget(detail, appointmentDate, slot)) {
            RunResult result = RunResult.alreadyScheduled(detail);
            result.slotId = slot.idSlot;
            return result;
        }
        if (matchesCompletePreviousAppointment(task, detail)) {
            return null;
        }
        return mismatch();
    }

    private static boolean matchesAutomaticTarget(AppointmentTask task, AsnDetail detail) {
        return detail != null
                && detail.appointmentDate != null
                && StringUtils.hasText(detail.appointmentTime)
                && inRange(task, detail.appointmentDate)
                && matchesTimeRange(
                        new SlotCapacity(null, detail.appointmentTime),
                        parseAcceptedHours(task.apTimeRange)
                );
    }

    private static boolean matchesExactTarget(
            AsnDetail detail,
            LocalDate appointmentDate,
            SlotCapacity slot
    ) {
        return detail != null
                && appointmentDate != null
                && appointmentDate.equals(detail.appointmentDate)
                && slot != null
                && sameAppointmentTime(slot.name, detail.appointmentTime);
    }

    private static boolean matchesCompletePreviousAppointment(AppointmentTask task, AsnDetail detail) {
        return task.rebookingRequested
                && task.previousAppointmentDate != null
                && StringUtils.hasText(task.previousAppointmentTime)
                && detail != null
                && task.previousAppointmentDate.equals(detail.appointmentDate)
                && sameAppointmentTime(task.previousAppointmentTime, detail.appointmentTime);
    }

    private static RunResult mismatch() {
        return RunResult.reconciliationRequired(
                "NOON_SCHEDULED_APPOINTMENT_MISMATCH",
                "Noon 当前预约无法确认是本次新目标或已保存的旧预约；为避免重复取消，请先核对。"
        );
    }
}
