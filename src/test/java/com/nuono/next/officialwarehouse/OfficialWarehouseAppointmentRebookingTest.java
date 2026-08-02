package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentTestFixtures.DatedSlots;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentTestFixtures.FakeNoonAppointmentClient;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentTestFixtures.task;
import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.RunResult;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAppointmentRebookingTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final OfficialWarehouseAppointmentRunner runner = new OfficialWarehouseAppointmentRunner(clock);

    @Test
    void automaticRebookingReleasesOldScheduleBeforeSchedulingRequestedDate() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-01");
        client.appointmentTime = "11am-2pm";
        client.dayCapacity = List.of("2026-08-02");
        client.slotsByDate.add(new DatedSlots("2026-08-02", List.of(new SlotCapacity(36, "11am-2pm"))));
        AppointmentTask task = rebookingTask();

        RunResult result = runner.runOnce(task, client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.alreadyScheduled).isFalse();
        assertThat(result.appointmentDate).isEqualTo(LocalDate.parse("2026-08-02"));
        assertThat(result.appointmentTime).isEqualTo("11am-2pm");
        assertThat(client.calls).containsExactly(
                "detail", "reschedule:A05531714PN", "detail", "days",
                "slots:2026-08-02", "schedule:2026-08-02:36", "detail"
        );
    }

    @Test
    void recoveredScheduleForCurrentRequestIsNotReleasedAgain() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-02");
        client.appointmentTime = "11am-2pm";

        RunResult result = runner.runOnce(rebookingTask(), client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.alreadyScheduled).isTrue();
        assertThat(result.appointmentDate).isEqualTo(LocalDate.parse("2026-08-02"));
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void automaticRebookingDoesNotReleaseScheduleThatMatchesNeitherOldNorNew() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-03");
        client.appointmentTime = "3pm-4pm";

        RunResult result = runner.runOnce(rebookingTask(), client);

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("NOON_SCHEDULED_APPOINTMENT_MISMATCH");
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void automaticRebookingDoesNotReleaseScheduleWithMissingRemoteTime() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-01");

        RunResult result = runner.runOnce(rebookingTask(), client);

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("NOON_SCHEDULED_APPOINTMENT_MISMATCH");
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void automaticRebookingRequiresCompletePersistedOldAppointmentEvidence() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-01");
        client.appointmentTime = "11am-2pm";
        AppointmentTask task = rebookingTask();
        task.previousAppointmentTime = null;

        RunResult result = runner.runOnce(task, client);

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("NOON_SCHEDULED_APPOINTMENT_MISMATCH");
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void currentRequestMatchWinsWhenItAlsoMatchesPersistedOldAppointment() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-01");
        client.appointmentTime = "11am-2pm";
        AppointmentTask task = rebookingTask();
        task.apStartDate = LocalDate.parse("2026-08-01");

        RunResult result = runner.runOnce(task, client);

        assertThat(result.alreadyScheduled).isTrue();
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void scheduledAsnWithoutExplicitRebookingOnlyRepairsLocalProjection() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-06-16");
        client.appointmentTime = "9am-10am";

        RunResult result = runner.runOnce(task(""), client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.alreadyScheduled).isTrue();
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void scheduledAsnWithoutExplicitRebookingRequiresCompleteRequestedFacts() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-06-16");

        RunResult result = runner.runOnce(task(""), client);

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("NOON_SCHEDULED_APPOINTMENT_MISMATCH");
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void selectedSlotReschedulesBeforeSubmittingNewSlot() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-01");
        client.appointmentTime = "11am-2pm";

        RunResult result = runner.scheduleSelectedSlot(
                rebookingTask(), client, LocalDate.parse("2026-08-02"), new SlotCapacity(36, "11am-2pm")
        );

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(client.calls).containsExactly(
                "detail", "reschedule:A05531714PN", "detail",
                "schedule:2026-08-02:36", "detail"
        );
    }

    @Test
    void selectedSlotRepairsLocalProjectionWhenRemoteAlreadyMatchesTarget() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-02");
        client.appointmentTime = "11am-2pm";

        RunResult result = runner.scheduleSelectedSlot(
                rebookingTask(), client, LocalDate.parse("2026-08-02"), new SlotCapacity(36, "11am-2pm")
        );

        assertThat(result.alreadyScheduled).isTrue();
        assertThat(result.slotId).isEqualTo(36);
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void selectedSlotDoesNotReleaseScheduleThatMatchesNeitherOldNorTarget() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-03");
        client.appointmentTime = "3pm-4pm";

        RunResult result = runner.scheduleSelectedSlot(
                rebookingTask(), client, LocalDate.parse("2026-08-02"), new SlotCapacity(36, "11am-2pm")
        );

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("NOON_SCHEDULED_APPOINTMENT_MISMATCH");
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void selectedSlotDoesNotReleaseScheduleWithMissingRemoteAppointmentFacts() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";

        RunResult result = runner.scheduleSelectedSlot(
                rebookingTask(), client, LocalDate.parse("2026-08-02"), new SlotCapacity(36, "11am-2pm")
        );

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("NOON_SCHEDULED_APPOINTMENT_MISMATCH");
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void automaticRebookingStopsWhenNoonDoesNotConfirmRelease() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.appointmentDate = LocalDate.parse("2026-08-01");
        client.appointmentTime = "11am-2pm";
        client.rescheduleAccepted = false;
        AppointmentTask task = rebookingTask();

        RunResult result = runner.runOnce(task, client);

        assertThat(result.status).isEqualTo("FAILED");
        assertThat(result.failureType).isEqualTo("RESCHEDULE_ASN");
        assertThat(result.reconciliationRequired).isTrue();
        assertThat(client.calls).containsExactly("detail", "reschedule:A05531714PN");
    }

    @Test
    void scheduledReadbackWithDifferentDateDoesNotMarkNewAppointmentSuccessful() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.retainAppointmentAfterSchedule = true;
        client.appointmentDate = LocalDate.parse("2026-06-15");
        client.appointmentTime = "8am-9am";

        RunResult result = runner.scheduleSelectedSlot(
                task(""), client, LocalDate.parse("2026-06-16"), new SlotCapacity(9, "9am-10am")
        );

        assertThat(result.status).isEqualTo("FAILED");
        assertThat(result.failureType).isEqualTo("SCHEDULE_CONFIRMATION_MISMATCH");
        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.appointmentDate).isNull();
        assertThat(client.calls).containsExactly("detail", "schedule:2026-06-16:9", "detail");
    }

    private static AppointmentTask rebookingTask() {
        AppointmentTask task = task("");
        task.apStartDate = LocalDate.parse("2026-08-02");
        task.apEndDate = LocalDate.parse("2026-08-02");
        task.availableToday = true;
        task.rebookingRequested = true;
        task.previousAppointmentDate = LocalDate.parse("2026-08-01");
        task.previousAppointmentTime = "11am-2pm";
        return task;
    }
}
