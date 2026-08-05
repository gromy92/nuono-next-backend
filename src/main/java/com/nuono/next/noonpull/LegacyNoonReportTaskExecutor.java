package com.nuono.next.noonpull;

import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import java.util.function.Supplier;

/** Executes the legacy Sales, Order, Finance and Advertising report variants. */
final class LegacyNoonReportTaskExecutor implements LegacyNoonTaskExecutor {
    private static final int MAX_POLL_ATTEMPTS = 18;

    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final NoonSalesReportAdapter salesAdapter;
    private final NoonOrderReportAdapter orderAdapter;
    private final NoonFinanceTransactionReportAdapter financeAdapter;
    private final NoonAdvertisingReportAdapter advertisingAdapter;
    private final Supplier<? extends NoonReportProvider> salesProvider;
    private final Supplier<? extends NoonReportProvider> orderProvider;
    private final Supplier<? extends NoonReportProvider> financeProvider;
    private final Supplier<? extends NoonReportProvider> advertisingProvider;

    LegacyNoonReportTaskExecutor(
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonSalesReportAdapter salesAdapter,
            NoonOrderReportAdapter orderAdapter,
            NoonFinanceTransactionReportAdapter financeAdapter,
            NoonAdvertisingReportAdapter advertisingAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonReportProvider> financeProvider,
            Supplier<? extends NoonReportProvider> advertisingProvider
    ) {
        this.foundationService = foundationService;
        this.reportPuller = reportPuller;
        this.salesAdapter = salesAdapter;
        this.orderAdapter = orderAdapter;
        this.financeAdapter = financeAdapter;
        this.advertisingAdapter = advertisingAdapter;
        this.salesProvider = salesProvider;
        this.orderProvider = orderProvider;
        this.financeProvider = financeProvider;
        this.advertisingProvider = advertisingProvider;
    }

    @Override
    public boolean accepts(NoonPullTaskRecord task) {
        if (task == null || task.getPullType() != NoonPullType.REPORT) {
            return false;
        }
        NoonPullDataDomain domain = task.getDataDomain();
        return domain == NoonPullDataDomain.SALES
                || domain == NoonPullDataDomain.ORDER
                || domain == NoonPullDataDomain.FINANCE_TRANSACTION
                || domain == NoonPullDataDomain.NOON_ADVERTISING;
    }

    @Override
    public void execute(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (task.getDataDomain() == NoonPullDataDomain.SALES) {
            execute(task, salesProvider, salesAdapter::process, result);
        } else if (task.getDataDomain() == NoonPullDataDomain.ORDER) {
            execute(task, orderProvider, orderAdapter::process, result);
        } else if (task.getDataDomain() == NoonPullDataDomain.FINANCE_TRANSACTION) {
            executeFinance(task, result);
        } else {
            executeAdvertising(task, result);
        }
    }

    private void executeFinance(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (financeAdapter == null) {
            fail(task, "handler not configured: scheduled finance transaction report adapter is disabled", result);
            return;
        }
        execute(task, financeProvider, financeAdapter::process, result);
    }

    private void executeAdvertising(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (advertisingAdapter == null) {
            fail(task, "handler not configured: scheduled noon advertising report adapter is disabled", result);
            return;
        }
        execute(task, advertisingProvider, advertisingAdapter::process, result);
    }

    private void execute(
            NoonPullTaskRecord task,
            Supplier<? extends NoonReportProvider> providerSupplier,
            NoonReportDownloadedFileHandler handler,
            NoonPullScheduledExecutionResult result
    ) {
        NoonReportProvider provider = providerSupplier == null
                ? null : providerSupplier.get();
        if (provider == null) {
            fail(task, "provider not configured: scheduled "
                    + task.getDataDomain().name().toLowerCase()
                    + " report provider is disabled", result);
            return;
        }
        NoonReportPullResult pullResult = reportPuller.execute(
                task.getId(), request(task), provider, handler
        );
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED
                || pullResult.getStatus() == NoonPullTaskStatus.PARTIAL) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private NoonReportPullRequest request(NoonPullTaskRecord task) {
        return NoonReportPullRequest.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .dataDomain(task.getDataDomain())
                .reportType(reportType(task.getDataDomain()))
                .dateFrom(task.getTargetDateFrom())
                .dateTo(task.getTargetDateTo())
                .maxPollAttempts(MAX_POLL_ATTEMPTS)
                .build();
    }

    private String reportType(NoonPullDataDomain domain) {
        if (domain == NoonPullDataDomain.ORDER) {
            return NoonOrderReportDescriptor.REPORT_TYPE;
        }
        if (domain == NoonPullDataDomain.FINANCE_TRANSACTION) {
            return NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE;
        }
        if (domain == NoonPullDataDomain.NOON_ADVERTISING) {
            return NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE;
        }
        return "productviewsandsalesdata";
    }

    private void fail(
            NoonPullTaskRecord task,
            String message,
            NoonPullScheduledExecutionResult result
    ) {
        foundationService.markFailedWithPolicy(task.getId(), message, 1);
        result.failed();
    }
}
