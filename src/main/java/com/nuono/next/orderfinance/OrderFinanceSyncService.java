package com.nuono.next.orderfinance;

import com.nuono.next.infrastructure.mapper.NoonFinanceTransactionMapper;
import com.nuono.next.noonpull.NoonFinanceTransactionReportDescriptor;
import com.nuono.next.noonpull.NoonFinanceTransactionReportProvider;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullPlanDraft;
import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullTaskDraft;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.noonpull.NoonPullTriggerMode;
import com.nuono.next.noonpull.NoonPullType;
import com.nuono.next.noonpull.NoonReportPullRequest;
import com.nuono.next.noonpull.NoonReportPullResult;
import com.nuono.next.noonpull.NoonReportPuller;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class OrderFinanceSyncService {
    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final ObjectProvider<NoonFinanceTransactionReportProvider> provider;
    private final NoonFinanceTransactionReportAdapter adapter;
    private final NoonFinanceTransactionMapper mapper;

    public OrderFinanceSyncService(
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            ObjectProvider<NoonFinanceTransactionReportProvider> provider,
            NoonFinanceTransactionReportAdapter adapter,
            NoonFinanceTransactionMapper mapper
    ) {
        this.foundationService = foundationService;
        this.reportPuller = reportPuller;
        this.provider = provider;
        this.adapter = adapter;
        this.mapper = mapper;
    }

    public boolean isProviderAvailable() {
        return provider.getIfAvailable() != null;
    }

    public OrderFinanceSyncResult sync(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        NoonFinanceTransactionReportProvider reportProvider = provider.getIfAvailable();
        if (reportProvider == null) {
            throw new IllegalStateException("Noon finance transaction report provider is not configured.");
        }
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .siteCode(siteCode)
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .maxRequestsPerRun(1)
                .build());
        Optional<NoonPullTaskRecord> taskOptional = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .siteCode(siteCode)
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .targetIdentity("finance-transactions:" + dateFrom + ".." + dateTo)
                .targetDateFrom(dateFrom)
                .targetDateTo(dateTo)
                .build());
        NoonPullTaskRecord task = taskOptional.orElseThrow(() -> new IllegalStateException("Unable to create Noon finance sync task."));
        if (task.getStatus() == NoonPullTaskStatus.RUNNING) {
            return new OrderFinanceSyncResult(task.getId(), task.getStatus().name(), task.getSourceBatchId(), 0, 0, "已有订单财务同步任务正在运行。", dateFrom, dateTo, false);
        }
        NoonReportPullResult result = reportPuller.execute(
                task.getId(),
                NoonReportPullRequest.builder()
                        .ownerUserId(ownerUserId)
                        .storeCode(storeCode)
                        .siteCode(siteCode)
                        .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                        .reportType(NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE)
                        .dateFrom(dateFrom)
                        .dateTo(dateTo)
                        .maxPollAttempts(18)
                        .build(),
                reportProvider,
                adapter::process
        );
        String status = result.getStatus() == null ? null : result.getStatus().name();
        String message = syncMessage(status, result.getExceptionCount());
        return new OrderFinanceSyncResult(
                task.getId(),
                status,
                result.getSourceBatchId(),
                result.getImportedCount(),
                result.getExceptionCount(),
                message,
                dateFrom,
                dateTo,
                false
        );
    }

    public OrderFinanceSyncResult syncMissingWindow(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        LocalDate latestCompleteDate = LocalDate.now().minusDays(1);
        LocalDate latestTransactionDate = mapper.selectLatestOrderFinanceTransactionDate(ownerUserId, storeCode, siteCode);
        LocalDate dateFrom = latestTransactionDate == null
                ? latestCompleteDate.minusYears(1).plusDays(1)
                : latestTransactionDate.plusDays(1);
        if (dateFrom.isAfter(latestCompleteDate)) {
            return new OrderFinanceSyncResult(
                    null,
                    "SKIPPED",
                    null,
                    0,
                    0,
                    "订单财务数据已是最新。",
                    dateFrom,
                    latestCompleteDate,
                    true
            );
        }
        return sync(ownerUserId, storeCode, siteCode, dateFrom, latestCompleteDate);
    }

    private String syncMessage(String status, int exceptionCount) {
        String normalizedStatus = status == null ? "" : status.toUpperCase();
        if (normalizedStatus.contains("FAIL")) {
            return "Noon 订单财务同步失败。";
        }
        if (exceptionCount > 0 || normalizedStatus.contains("PARTIAL")) {
            return "Noon 订单财务部分导入完成。";
        }
        return "Noon 订单财务补齐完成。";
    }
}
