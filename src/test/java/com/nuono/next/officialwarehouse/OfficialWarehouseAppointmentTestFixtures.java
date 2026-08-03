package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.NoonAppointmentClient;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class OfficialWarehouseAppointmentTestFixtures {

    private OfficialWarehouseAppointmentTestFixtures() {
    }

    static AppointmentTask task(String timeRange) {
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

    static class FakeNoonAppointmentClient implements NoonAppointmentClient {
        String asnStatus;
        String asnStatusAfterSchedule = "scheduled";
        String currentWarehouseToPartnerCode = "JED01";
        String currentWarehouseToCode = "W00000004A";
        LocalDate appointmentDate;
        String appointmentTime;
        boolean setWarehousesAccepted = true;
        boolean updateWarehouseAfterSet = true;
        boolean rescheduleAccepted = true;
        boolean scheduleAccepted = true;
        boolean retainAppointmentAfterSchedule;
        boolean recordWarehouseConfirmation;
        List<String> dayCapacity = List.of();
        final List<DatedSlots> slotsByDate = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public AsnDetail queryAsnDetail(AppointmentTask task) {
            calls.add("detail");
            return new AsnDetail(
                    asnStatus,
                    appointmentDate,
                    appointmentTime,
                    currentWarehouseToPartnerCode,
                    currentWarehouseToCode
            );
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
            if (setWarehousesAccepted && updateWarehouseAfterSet) {
                currentWarehouseToPartnerCode = task.warehouseTo;
                currentWarehouseToCode = task.warehouseToCode;
                if ("created".equals(asnStatus)) {
                    asnStatus = "sealed";
                }
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
            if (rescheduleAccepted && "scheduled".equals(asnStatus)) {
                asnStatus = "sealed";
            }
            return rescheduleAccepted;
        }

        @Override
        public boolean schedule(AppointmentTask task, LocalDate capacityDate, SlotCapacity slot) {
            calls.add("schedule:" + capacityDate + ":" + slot.idSlot);
            if (scheduleAccepted) {
                asnStatus = asnStatusAfterSchedule;
                if (!retainAppointmentAfterSchedule) {
                    appointmentDate = capacityDate;
                    appointmentTime = slot.name;
                }
            }
            return scheduleAccepted;
        }
    }

    static class DatedSlots {
        final String date;
        final List<SlotCapacity> slots;

        DatedSlots(String date, List<SlotCapacity> slots) {
            this.date = date;
            this.slots = slots;
        }
    }
}
