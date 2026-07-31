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
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAppointmentUnknownWriteTest {

    private final OfficialWarehouseAppointmentRunner runner =
            new OfficialWarehouseAppointmentRunner(
                    Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC)
            );

    @Test
    void capacityReadFailureAfterSettingWarehousesRequiresReconciliation() {
        StubClient client = new StubClient("created") {
            @Override
            public boolean setWarehouses(AppointmentTask task) {
                status = "sealed";
                return true;
            }

            @Override
            public List<String> queryDayCapacity(AppointmentTask task) {
                throw new IllegalStateException("read timeout");
            }
        };

        RunResult result = runner.runOnce(task(), client);

        assertReconciliation(result, "SET_WAREHOUSES_FOLLOW_UP");
    }

    @Test
    void projectionFailureAfterSettingWarehousesRequiresReconciliation() {
        StubClient client = new StubClient("created") {
            @Override
            public boolean setWarehouses(AppointmentTask task) {
                return true;
            }

            @Override
            public void onWarehousesSet(AppointmentTask task) {
                throw new IllegalStateException("database unavailable");
            }
        };

        RunResult result = runner.runOnce(task(), client);

        assertReconciliation(result, "SET_WAREHOUSES");
    }

    @Test
    void scheduleTransportFailureRequiresReconciliation() {
        StubClient client = new StubClient("sealed") {
            @Override
            public boolean schedule(
                    AppointmentTask task,
                    LocalDate capacityDate,
                    SlotCapacity slot
            ) {
                throw new IllegalStateException("response lost");
            }
        };

        RunResult result = runner.scheduleSelectedSlot(
                task(),
                client,
                LocalDate.parse("2026-07-30"),
                new SlotCapacity(9, "9am-10am")
        );

        assertReconciliation(result, "SCHEDULE_APPOINTMENT");
    }

    @Test
    void confirmationReadFailureAfterScheduleRequiresReconciliation() {
        StubClient client = new StubClient("sealed") {
            private int detailCalls;

            @Override
            public AsnDetail queryAsnDetail(AppointmentTask task) {
                detailCalls += 1;
                if (detailCalls > 1) {
                    throw new IllegalStateException("confirmation timeout");
                }
                return super.queryAsnDetail(task);
            }
        };

        RunResult result = runner.scheduleSelectedSlot(
                task(),
                client,
                LocalDate.parse("2026-07-30"),
                new SlotCapacity(9, "9am-10am")
        );

        assertReconciliation(result, "SCHEDULE_CONFIRMATION");
    }

    @Test
    void automaticRunStopsWhenAnotherOperatorSchedulesDuringWarehousePreparation() {
        StubClient client = externallyScheduledAfterWarehouseWrite();

        RunResult result = runner.runOnce(task(), client);

        assertThat(result.status).isEqualTo("SCHEDULED");
        assertThat(result.alreadyScheduled).isTrue();
        assertThat(result.reconciliationRequired).isFalse();
    }

    @Test
    void selectedSlotRequiresReconciliationWhenAnotherOperatorSchedulesDuringPreparation() {
        StubClient client = externallyScheduledAfterWarehouseWrite();

        RunResult result = runner.scheduleSelectedSlot(
                task(),
                client,
                LocalDate.parse("2026-07-30"),
                new SlotCapacity(9, "9am-10am")
        );

        assertReconciliation(result, "NOON_ALREADY_SCHEDULED_DURING_PREPARATION");
    }

    private static StubClient externallyScheduledAfterWarehouseWrite() {
        return new StubClient("created") {
            @Override
            public void onWarehousesSet(AppointmentTask task) {
                status = "scheduled";
            }

            @Override
            public boolean schedule(
                    AppointmentTask task,
                    LocalDate capacityDate,
                    SlotCapacity slot
            ) {
                throw new AssertionError("must not overwrite the external appointment");
            }
        };
    }

    private static void assertReconciliation(RunResult result, String failureType) {
        assertThat(result.reconciliationRequired).isTrue();
        assertThat(result.failureType).isEqualTo(failureType);
    }

    private static AppointmentTask task() {
        AppointmentTask task = new AppointmentTask();
        task.noonAsnNr = "ASN-1";
        task.totalUnits = 10;
        task.warehouseTo = "JED01";
        task.apStartDate = LocalDate.parse("2026-07-30");
        task.apEndDate = LocalDate.parse("2026-08-01");
        return task;
    }

    private static class StubClient implements NoonAppointmentClient {
        protected String status;

        private StubClient(String status) {
            this.status = status;
        }

        @Override
        public AsnDetail queryAsnDetail(AppointmentTask task) {
            return new AsnDetail(status);
        }

        @Override
        public List<String> queryDayCapacity(AppointmentTask task) {
            return List.of("2026-07-30");
        }

        @Override
        public List<SlotCapacity> querySlotCapacity(
                AppointmentTask task,
                LocalDate capacityDate
        ) {
            return List.of(new SlotCapacity(9, "9am-10am"));
        }

        @Override
        public boolean setWarehouses(AppointmentTask task) {
            return true;
        }

        @Override
        public boolean reschedule(AppointmentTask task) {
            return true;
        }

        @Override
        public boolean schedule(
                AppointmentTask task,
                LocalDate capacityDate,
                SlotCapacity slot
        ) {
            status = "scheduled";
            return true;
        }
    }
}
