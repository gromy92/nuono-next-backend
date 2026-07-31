package com.nuono.next.product;

import com.nuono.next.system.task.OperationalTaskService;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductActiveStateBackfillHandler {
    static final String REASON = "daily-active-state-reconciliation";

    private ProductActiveStateBackfillHandler() {
    }

    static boolean handle(
            OperationalTaskService taskService,
            Long taskId,
            ProductMasterFetchCommand command,
            String reason,
            ProductDetailBaselineBackfillService.DetailBaselineBackfillRunner runner
    ) {
        if (!REASON.equals(reason)) {
            return false;
        }
        taskService.progress(taskId, 50, "正在从 Noon 商家价格接口核对商品在售状态。");
        ProductMasterSnapshotView snapshot = runner.fetch(command, "detail-baseline-backfill." + REASON);
        if (snapshot != null && snapshot.isReady() && hasExplicitState(snapshot, command)) {
            taskService.complete(
                    taskId,
                    "{\"ready\":true,\"activeStateEvidence\":true,\"source\":\"NOON_PRICING_INFO\"}",
                    "商品在售状态已核对。"
            );
        } else {
            taskService.fail(
                    taskId,
                    "ACTIVE_STATE_EVIDENCE_MISSING",
                    "Noon 商家价格接口未返回明确在售状态，商品继续保持待核实。"
            );
        }
        return true;
    }

    private static boolean hasExplicitState(
            ProductMasterSnapshotView snapshot,
            ProductMasterFetchCommand command
    ) {
        if (snapshot.getSiteOffers() == null || command == null) {
            return false;
        }
        for (Map<String, Object> offer : snapshot.getSiteOffers()) {
            if (offer == null || !sameStore(offer.get("storeCode"), command.getStoreCode())) {
                continue;
            }
            Object active = offer.get("isActive");
            if (active instanceof Boolean) {
                return true;
            }
            if (active != null && ("true".equalsIgnoreCase(String.valueOf(active))
                    || "false".equalsIgnoreCase(String.valueOf(active)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameStore(Object actual, String expected) {
        return actual != null
                && StringUtils.hasText(expected)
                && String.valueOf(actual).trim().equalsIgnoreCase(expected.trim());
    }
}
