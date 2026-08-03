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

class OfficialWarehouseAppointmentRunnerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final OfficialWarehouseAppointmentRunner runner = new OfficialWarehouseAppointmentRunner(clock);

    @Test
    void schedulesFirstMatchingSlotInsideRequestedDateAndTimeRange() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
        client.dayCapacity = List.of("2026-06-16", "2026-06-17");
        client.slotsByDate.add(new DatedSlots("2026-06-16", List.of(
                new SlotCapacity(7, "7am-8am"),
                new SlotCapacity(9, "9am-10am")
        )));

        RunResult result = runner.runOnce(task("8am,9am,10am,11am"), client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.appointmentDate).isEqualTo(LocalDate.parse("2026-06-16"));
        assertThat(result.slotId).isEqualTo(9);
        assertThat(result.appointmentTime).isEqualTo("9am-10am");
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:JED01",
                "detail",
                "days",
                "slots:2026-06-16",
                "schedule:2026-06-16:9",
                "detail"
        );
    }

    @Test
    void skipsTodayWhenAppointmentDisallowsSameDayDelivery() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
        client.dayCapacity = List.of("2026-06-15", "2026-06-16");
        client.slotsByDate.add(new DatedSlots("2026-06-16", List.of(new SlotCapacity(4, "1pm-2pm"))));

        RunResult result = runner.runOnce(task(""), client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.appointmentDate).isEqualTo(LocalDate.parse("2026-06-16"));
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:JED01",
                "detail",
                "days",
                "slots:2026-06-16",
                "schedule:2026-06-16:4",
                "detail"
        );
    }

    @Test
    void reroutesSealedAsnToRequestedWarehouseBeforeCapacityQuery() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.currentWarehouseToPartnerCode = "JED01";
        client.currentWarehouseToCode = "W00000004A";
        client.dayCapacity = List.of("2026-06-16");
        client.slotsByDate.add(new DatedSlots(
                "2026-06-16",
                List.of(new SlotCapacity(9, "9am-10am"))
        ));
        AppointmentTask task = task("");
        task.warehouseTo = "RUH01S";
        task.warehouseToCode = "W00105371A";

        RunResult result = runner.runOnce(task, client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:RUH01S",
                "detail",
                "days",
                "slots:2026-06-16",
                "schedule:2026-06-16:9",
                "detail"
        );
    }

    @Test
    void availabilityQueryReturnsMatchingSlotsWithoutScheduling() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.dayCapacity = List.of("2026-06-16");
        client.slotsByDate.add(new DatedSlots("2026-06-16", List.of(
                new SlotCapacity(7, "7am-8am"),
                new SlotCapacity(9, "9am-10am")
        )));

        List<OfficialWarehouseAppointmentRunner.AvailableSlot> slots = runner.queryAvailability(task("9am,10am"), client);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).capacityDate).isEqualTo(LocalDate.parse("2026-06-16"));
        assertThat(slots.get(0).slotId).isEqualTo(9);
        assertThat(client.calls).containsExactly(
                "detail",
                "days",
                "slots:2026-06-16"
        );
    }

    @Test
    void availabilityQueryNeverMutatesCreatedAsn() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
        client.recordWarehouseConfirmation = true;

        List<OfficialWarehouseAppointmentRunner.AvailableSlot> slots =
                runner.queryAvailability(task(""), client);

        assertThat(slots).isEmpty();
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void availabilityQueryDoesNotAskNoonCapacityForDifferentCurrentWarehouse() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.currentWarehouseToPartnerCode = "JED01";
        client.currentWarehouseToCode = "W00000004A";
        AppointmentTask task = task("");
        task.warehouseTo = "RUH01S";
        task.warehouseToCode = "W00105371A";

        List<OfficialWarehouseAppointmentRunner.AvailableSlot> slots =
                runner.queryAvailability(task, client);

        assertThat(slots).isEmpty();
        assertThat(client.calls).containsExactly("detail");
    }

    @Test
    void availabilityQueryForScheduledAsnIsAlsoReadOnly() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.recordWarehouseConfirmation = true;

        runner.queryAvailability(task(""), client);

        assertThat(client.calls).containsExactly("detail", "days");
    }

    @Test
    void selectedSlotDoesNotNeedDepartureWarehouse() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
        AppointmentTask task = task("");

        RunResult result = runner.scheduleSelectedSlot(task, client, LocalDate.parse("2026-06-16"), new SlotCapacity(9, "9am-10am"));

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:JED01",
                "detail",
                "schedule:2026-06-16:9",
                "detail"
        );
    }

    @Test
    void selectedSlotReroutesSealedAsnToRequestedWarehouseBeforeScheduling() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.currentWarehouseToPartnerCode = "JED01";
        client.currentWarehouseToCode = "W00000004A";
        AppointmentTask task = task("");
        task.warehouseTo = "RUH01S";
        task.warehouseToCode = "W00105371A";

        RunResult result = runner.scheduleSelectedSlot(
                task,
                client,
                LocalDate.parse("2026-06-16"),
                new SlotCapacity(9, "9am-10am")
        );

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:RUH01S",
                "detail",
                "schedule:2026-06-16:9",
                "detail"
        );
    }

    @Test
    void doesNotReadCapacityUntilNoonConfirmsRequestedWarehouse() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.currentWarehouseToPartnerCode = "JED01";
        client.currentWarehouseToCode = "W00000004A";
        client.updateWarehouseAfterSet = false;
        AppointmentTask task = task("");
        task.warehouseTo = "RUH01S";
        task.warehouseToCode = "W00105371A";

        RunResult result = runner.runOnce(task, client);

        assertThat(result.status).isEqualTo("FAILED");
        assertThat(result.failureType).isEqualTo("ASN_WAREHOUSE_NOT_CONFIRMED");
        assertThat(result.reconciliationRequired).isTrue();
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:RUH01S",
                "detail",
                "detail",
                "detail",
                "detail",
                "detail"
        );
    }

    @Test
    void selectedSlotDoesNotMarkScheduledUntilNoonDetailConfirmsSchedule() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.asnStatusAfterSchedule = "sealed";

        RunResult result = runner.scheduleSelectedSlot(task(""), client, LocalDate.parse("2026-06-16"), new SlotCapacity(43, "9pm-10pm"));

        assertThat(result.status).isEqualTo("FAILED");
        assertThat(result.failureType).isEqualTo("SCHEDULE_NOT_CONFIRMED");
        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.errorMessage).contains("Noon");
        assertThat(client.calls).containsExactly(
                "detail",
                "schedule:2026-06-16:43",
                "detail"
        );
    }

    @Test
    void rejectedScheduleStopsAndRequiresReconciliation() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "sealed";
        client.scheduleAccepted = false;

        RunResult result = runner.scheduleSelectedSlot(
                task(""),
                client,
                LocalDate.parse("2026-06-16"),
                new SlotCapacity(9, "9am-10am")
        );

        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo("SCHEDULE_APPOINTMENT");
        assertThat(client.calls).containsExactly("detail", "schedule:2026-06-16:9");
    }

}
