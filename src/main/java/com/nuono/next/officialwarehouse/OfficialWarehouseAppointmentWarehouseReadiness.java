package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.MAX_SEALED_CHECK_ATTEMPTS;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonFailureStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonReadyForScheduleStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonScheduledStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.normalize;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.sleepBeforeNextSealedCheck;

import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.NoonAppointmentClient;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.RunResult;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAppointmentWarehouseReadiness {

    private OfficialWarehouseAppointmentWarehouseReadiness() {
    }

    static boolean isWarehouseChangeRequired(AppointmentTask task, AsnDetail detail) {
        if (task == null || !StringUtils.hasText(task.warehouseTo) || detail == null) {
            return false;
        }
        boolean remoteWarehouseKnown = StringUtils.hasText(detail.warehouseToPartnerCode)
                || StringUtils.hasText(detail.warehouseToCode);
        if (!remoteWarehouseKnown) {
            return false;
        }
        return !sameWarehouse(task.warehouseTo, detail.warehouseToPartnerCode)
                && !sameWarehouse(task.warehouseTo, detail.warehouseToCode);
    }

    static RunResult setWarehousesAndWaitUntilReady(
            AppointmentTask task,
            NoonAppointmentClient client,
            boolean selectedSlot
    ) {
        try {
            if (!client.setWarehouses(task)) {
                return unknownSetWarehouseResult();
            }
            client.onWarehousesSet(task);
            RunResult readiness = waitUntilReadyForSchedule(task, client, selectedSlot);
            if (readiness != null && !readiness.alreadyScheduled) {
                readiness.reconciliationRequired = true;
            }
            return readiness;
        } catch (RuntimeException exception) {
            return unknownSetWarehouseResult();
        }
    }

    private static RunResult waitUntilReadyForSchedule(
            AppointmentTask task,
            NoonAppointmentClient client,
            boolean selectedSlot
    ) {
        boolean warehouseChangeStillPending = false;
        for (int attempt = 0; attempt < MAX_SEALED_CHECK_ATTEMPTS; attempt++) {
            AsnDetail detail = client.queryAsnDetail(task);
            String status = normalize(detail == null ? null : detail.status);
            if (isNoonFailureStatus(status)) {
                return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
            }
            if (isNoonReadyForScheduleStatus(status) && !isWarehouseChangeRequired(task, detail)) {
                return null;
            }
            warehouseChangeStillPending = isNoonReadyForScheduleStatus(status)
                    && isWarehouseChangeRequired(task, detail);
            if (isNoonScheduledStatus(status)) {
                return selectedSlot
                        ? RunResult.reconciliationRequired(
                                "NOON_ALREADY_SCHEDULED_DURING_PREPARATION",
                                "等待 Noon 仓库准备期间 ASN 已被约仓，请核对后再显式改约。"
                        )
                        : RunResult.alreadyScheduled(detail);
            }
            if (attempt + 1 < MAX_SEALED_CHECK_ATTEMPTS) {
                sleepBeforeNextSealedCheck();
            }
        }
        return warehouseChangeStillPending
                ? RunResult.failed(
                        "ASN_WAREHOUSE_NOT_CONFIRMED",
                        "Noon 已接受设置仓库，但 ASN 尚未回读为目标到达仓库，稍后再点立即约仓。"
                )
                : RunResult.failed("ASN_NOT_SEALED", "Noon 已设置仓库，但 ASN 尚未 sealed，稍后再点立即约仓。");
    }

    private static RunResult unknownSetWarehouseResult() {
        return RunResult.reconciliationRequired(
                "SET_WAREHOUSES",
                "Noon 设置仓库请求已发出，但结果未确认，请先在 Noon 后台核对。"
        );
    }

    private static boolean sameWarehouse(String requestedWarehouse, String remoteWarehouse) {
        return StringUtils.hasText(requestedWarehouse)
                && StringUtils.hasText(remoteWarehouse)
                && requestedWarehouse.trim().equalsIgnoreCase(remoteWarehouse.trim());
    }
}
