package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.noonpull.NoonOrderReportDescriptor;
import com.nuono.next.noonpull.NoonPullDataDomain;
import java.time.LocalDateTime;

final class ReportBridgeTestSupport {
    private ReportBridgeTestSupport() {
    }

    static ExportReportIntent intent(OperationCode operation, String channel) {
        return intent(9001L, operation, channel);
    }

    static ExportReportIntent intent(long taskId, OperationCode operation, String channel) {
        DataPullTask task = new DataPullTask();
        task.setId(taskId);
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(1L);
        task.setLeaseOwner("test-worker");
        task.setOperationCode(operation);
        task.setProviderChannel(channel);
        task.setOwnerUserId(307L);
        task.setLogicalStoreId(91L);
        task.setAccountKey("PRJ108065");
        task.setProjectCode("PRJ108065");
        task.setStoreCode("STR108065-NSA");
        task.setSiteCode("SA");
        task.setScopeKey("NOON:307:91:PRJ108065:STR108065-NSA:SA");
        task.setBusinessWindowKey(operation.name() + ":date-range:2026-08-01..2026-08-01");
        return ExportReportIntent.from(new ExecutionContext(
                task,
                LocalDateTime.of(2026, 8, 2, 0, 0)
        ));
    }

    static NoonReportDefinition dp02() {
        return new NoonReportDefinition(
                OperationCode.DP02,
                "NOON_REPORT_ORDER",
                NoonPullDataDomain.ORDER,
                NoonOrderReportDescriptor.REPORT_TYPE,
                "sales-dashboard-export"
        );
    }
}
