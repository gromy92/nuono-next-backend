package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.NoonAppointmentClient;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.RunResult;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
    void alreadyScheduledNoonAsnStopsAutomaticRunWithoutRescheduling() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        client.dayCapacity = List.of("2026-06-16");
        client.slotsByDate.add(new DatedSlots("2026-06-16", List.of(new SlotCapacity(9, "9am-10am"))));

        RunResult result = runner.runOnce(task(""), client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.alreadyScheduled).isTrue();
        assertThat(result.failureType).isNull();
        assertThat(client.calls).containsExactly("detail");
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
    void availabilityQueryReturnsMatchingSlotsWithoutScheduling() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
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
                "set-warehouses:JED01",
                "detail",
                "days",
                "slots:2026-06-16"
        );
    }

    @Test
    void confirmsWarehouseProjectionOnlyAfterNoonAcceptsSetWarehouses() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
        client.recordWarehouseConfirmation = true;

        runner.queryAvailability(task(""), client);

        assertThat(client.calls).containsSubsequence(
                "set-warehouses:JED01",
                "warehouse-confirmed:JED01"
        );
    }

    @Test
    void doesNotConfirmWarehouseProjectionWhenNoonRejectsSetWarehouses() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "created";
        client.setWarehousesAccepted = false;
        client.recordWarehouseConfirmation = true;

        runner.queryAvailability(task(""), client);

        assertThat(client.calls).contains("set-warehouses:JED01");
        assertThat(client.calls).doesNotContain("warehouse-confirmed:JED01");
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
    void selectedSlotReschedulesWithDestinationWarehouseOnly() {
        FakeNoonAppointmentClient client = new FakeNoonAppointmentClient();
        client.asnStatus = "scheduled";
        AppointmentTask task = task("");

        RunResult result = runner.scheduleSelectedSlot(task, client, LocalDate.parse("2026-06-16"), new SlotCapacity(9, "9am-10am"));

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(client.calls).containsExactly(
                "detail",
                "reschedule:A05531714PN",
                "set-warehouses:JED01",
                "detail",
                "schedule:2026-06-16:9",
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
        assertThat(result.errorMessage).contains("Noon");
        assertThat(client.calls).containsExactly(
                "detail",
                "set-warehouses:JED01",
                "detail",
                "schedule:2026-06-16:43",
                "detail"
        );
    }

    private static AppointmentTask task(String timeRange) {
        AppointmentTask task = new AppointmentTask();
        task.appointmentId = 610001L;
        task.asnId = 500002L;
        task.noonAsnNr = "A05531714PN";
        task.totalUnits = 10;
        task.warehouseTo = "JED01";
        task.apStartDate = LocalDate.parse("2026-06-15");
        task.apEndDate = LocalDate.parse("2026-06-18");
        task.apTimeRange = timeRange;
        task.availableToday = false;
        return task;
    }

    private static class FakeNoonAppointmentClient implements NoonAppointmentClient {
        private String asnStatus;
        private String asnStatusAfterSchedule = "scheduled";
        private boolean setWarehousesAccepted = true;
        private boolean recordWarehouseConfirmation;
        private List<String> dayCapacity = List.of();
        private final List<DatedSlots> slotsByDate = new ArrayList<>();
        private final List<String> calls = new ArrayList<>();

        @Override
        public AsnDetail queryAsnDetail(AppointmentTask task) {
            calls.add("detail");
            return new AsnDetail(asnStatus);
        }

        @Override
        public List<String> queryDayCapacity(AppointmentTask task) {
            calls.add("days");
            return dayCapacity;
        }

        @Override
        public List<SlotCapacity> querySlotCapacity(AppointmentTask task, LocalDate capacityDate) {
            calls.add("slots:" + capacityDate);
            return slotsByDate.stream()
                    .filter(entry -> entry.date.equals(capacityDate.toString()))
                    .findFirst()
                    .map(entry -> entry.slots)
                    .orElse(List.of());
        }

        @Override
        public boolean setWarehouses(AppointmentTask task) {
            calls.add("set-warehouses:" + task.warehouseTo);
            if (setWarehousesAccepted && "created".equals(asnStatus)) {
                asnStatus = "sealed";
            }
            return setWarehousesAccepted;
        }

        @Override
        public void onWarehousesSet(AppointmentTask task) {
            if (recordWarehouseConfirmation) {
                calls.add("warehouse-confirmed:" + task.warehouseTo);
            }
        }

        @Override
        public boolean reschedule(AppointmentTask task) {
            calls.add("reschedule:" + task.noonAsnNr);
            if ("scheduled".equals(asnStatus)) {
                asnStatus = "sealed";
            }
            return true;
        }

        @Override
        public boolean schedule(AppointmentTask task, LocalDate capacityDate, SlotCapacity slot) {
            calls.add("schedule:" + capacityDate + ":" + slot.idSlot);
            asnStatus = asnStatusAfterSchedule;
            return true;
        }
    }

    private static class DatedSlots {
        private final String date;
        private final List<SlotCapacity> slots;

        private DatedSlots(String date, List<SlotCapacity> slots) {
            this.date = date;
            this.slots = slots;
        }
    }
}
