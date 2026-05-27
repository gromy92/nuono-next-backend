package com.nuono.next.noonpull;

import com.nuono.next.nooncompleteness.NoonGapTaskOutcomeReducer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NoonPullScheduledExecutionService {
    private static final int REPORT_MAX_POLL_ATTEMPTS = 18;

    private final NoonPullScheduler scheduler;
    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final NoonInterfacePuller interfacePuller;
    private final NoonSalesReportAdapter salesReportAdapter;
    private final NoonOrderReportAdapter orderReportAdapter;
    private final Supplier<? extends NoonReportProvider> salesProvider;
    private final Supplier<? extends NoonReportProvider> orderProvider;
    private final Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider;
    private final Supplier<? extends NoonGapTaskOutcomeReducer> gapTaskOutcomeReducer;
    private final boolean enabled;

    @Autowired
    public NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            ObjectProvider<NoonSalesReportSmokeProvider> salesProvider,
            ObjectProvider<NoonOrderReportSmokeProvider> orderProvider,
            ObjectProvider<NoonSalesPageQueryProvider> salesPageQueryProvider,
            ObjectProvider<NoonGapTaskOutcomeReducer> gapTaskOutcomeReducer,
            @Value("${nuono.noon.pull.scheduler.enabled:false}") boolean enabled
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                salesReportAdapter,
                orderReportAdapter,
                () -> salesProvider == null ? null : salesProvider.getIfAvailable(),
                () -> orderProvider == null ? null : orderProvider.getIfAvailable(),
                () -> salesPageQueryProvider == null ? null : salesPageQueryProvider.getIfAvailable(),
                enabled,
                () -> gapTaskOutcomeReducer == null ? null : gapTaskOutcomeReducer.getIfAvailable()
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            boolean enabled
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                salesReportAdapter,
                orderReportAdapter,
                salesProvider,
                orderProvider,
                salesPageQueryProvider,
                enabled,
                () -> null
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            boolean enabled,
            Supplier<? extends NoonGapTaskOutcomeReducer> gapTaskOutcomeReducer
    ) {
        this.scheduler = scheduler;
        this.foundationService = foundationService;
        this.reportPuller = reportPuller;
        this.interfacePuller = interfacePuller;
        this.salesReportAdapter = salesReportAdapter;
        this.orderReportAdapter = orderReportAdapter;
        this.salesProvider = salesProvider;
        this.orderProvider = orderProvider;
        this.salesPageQueryProvider = salesPageQueryProvider;
        this.gapTaskOutcomeReducer = gapTaskOutcomeReducer;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${nuono.noon.pull.scheduler.initial-delay-ms:60000}",
            fixedDelayString = "${nuono.noon.pull.scheduler.fixed-delay-ms:300000}"
    )
    public void runScheduledTick() {
        if (enabled) {
            runOnce();
        }
    }

    public NoonPullScheduledExecutionResult runOnce() {
        NoonPullScheduledExecutionResult result = new NoonPullScheduledExecutionResult();
        if (!enabled) {
            result.setEnabled(false);
            return result;
        }
        NoonPullSchedulerResult schedulerResult = scheduler.runDuePlans();
        result.created(schedulerResult.getCreatedTaskCount());
        Map<Long, NoonPullTaskRecord> executableTasks = new LinkedHashMap<>();
        for (NoonPullTaskRecord task : schedulerResult.getCreatedTasks()) {
            if (task.getId() != null) {
                executableTasks.put(task.getId(), task);
            }
        }
        for (NoonPullTaskRecord task : foundationService.listTasks()) {
            if (task.getId() != null && task.getStatus() == NoonPullTaskStatus.QUEUED) {
                executableTasks.putIfAbsent(task.getId(), task);
            }
        }
        for (NoonPullTaskRecord task : executableTasks.values().stream()
                .sorted(Comparator.comparingInt(this::executionPriority)
                        .thenComparing(NoonPullTaskRecord::getId))
                .collect(Collectors.toList())) {
            executeTask(task, result);
        }
        reduceTerminalGapBackfillOutcomes();
        return result;
    }

    private int executionPriority(NoonPullTaskRecord task) {
        if (task == null) {
            return 99;
        }
        if (task.getDataDomain() == NoonPullDataDomain.SALES && task.getPullType() == NoonPullType.PAGE_QUERY) {
            return 0;
        }
        if (task.getDataDomain() == NoonPullDataDomain.SALES && task.getPullType() == NoonPullType.REPORT) {
            return 1;
        }
        return 10;
    }

    private void executeTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
        if (task == null) {
            result.skipped();
            return;
        }
        if (task.getPullType() == NoonPullType.PAGE_QUERY && task.getDataDomain() == NoonPullDataDomain.SALES) {
            executeSalesPageQueryTask(task, result);
            reduceGapOutcome(task);
            return;
        }
        if (task.getPullType() != NoonPullType.REPORT) {
            result.skipped();
            return;
        }
        if (task.getDataDomain() == NoonPullDataDomain.SALES) {
            executeReportTask(task, providerOrFail(task, salesProvider), salesReportAdapter::process, result);
            reduceGapOutcome(task);
            return;
        }
        if (task.getDataDomain() == NoonPullDataDomain.ORDER) {
            executeReportTask(task, providerOrFail(task, orderProvider), orderReportAdapter::process, result);
            reduceGapOutcome(task);
            return;
        }
        result.skipped();
    }

    private void reduceGapOutcome(NoonPullTaskRecord task) {
        NoonGapTaskOutcomeReducer reducer = gapTaskOutcomeReducer == null ? null : gapTaskOutcomeReducer.get();
        if (reducer == null || task == null || task.getId() == null) {
            return;
        }
        NoonPullTaskRecord latestTask = latestTask(task.getId());
        if (latestTask == null || latestTask.getStatus() == NoonPullTaskStatus.QUEUED
                || latestTask.getStatus() == NoonPullTaskStatus.RUNNING) {
            return;
        }
        try {
            reducer.reduce(latestTask);
        } catch (IllegalArgumentException ignored) {
            // Scheduled daily tasks are not always linked to a gap window.
        }
    }

    private void reduceTerminalGapBackfillOutcomes() {
        NoonGapTaskOutcomeReducer reducer = gapTaskOutcomeReducer == null ? null : gapTaskOutcomeReducer.get();
        if (reducer == null) {
            return;
        }
        for (NoonPullTaskRecord task : foundationService.listTasks()) {
            if (task.getTriggerMode() == NoonPullTriggerMode.GAP_BACKFILL && isTerminal(task.getStatus())) {
                reduceGapOutcome(task);
            }
        }
    }

    private boolean isTerminal(NoonPullTaskStatus status) {
        return status == NoonPullTaskStatus.SUCCEEDED
                || status == NoonPullTaskStatus.PARTIAL
                || status == NoonPullTaskStatus.FAILED
                || status == NoonPullTaskStatus.CANCELLED
                || status == NoonPullTaskStatus.SKIPPED;
    }

    private NoonPullTaskRecord latestTask(Long taskId) {
        for (NoonPullTaskRecord task : foundationService.listTasks()) {
            if (taskId.equals(task.getId())) {
                return task;
            }
        }
        return null;
    }

    private void executeSalesPageQueryTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
        NoonSalesPageQueryProvider provider = salesPageQueryProvider == null ? null : salesPageQueryProvider.get();
        if (provider == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled sales page query provider is disabled",
                    1
            );
            result.failed();
            return;
        }
        NoonInterfacePullResult pullResult = interfacePuller.execute(
                task.getId(),
                NoonInterfacePullRequest.builder()
                        .ownerUserId(task.getOwnerUserId())
                        .storeCode(task.getStoreCode())
                        .siteCode(task.getSiteCode())
                        .dataDomain(NoonPullDataDomain.SALES)
                        .requestName("sales-page-query")
                        .targetIdentity(task.getTargetIdentity())
                        .dateFrom(task.getTargetDateFrom())
                        .dateTo(task.getTargetDateTo())
                        .requestSummary("scheduled daily page-query")
                        .build(),
                provider
        );
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED || pullResult.getStatus() == NoonPullTaskStatus.PARTIAL) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private NoonReportProvider providerOrFail(
            NoonPullTaskRecord task,
            Supplier<? extends NoonReportProvider> providerSupplier
    ) {
        NoonReportProvider provider = providerSupplier == null ? null : providerSupplier.get();
        if (provider == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled " + task.getDataDomain().name().toLowerCase() + " report provider is disabled",
                    1
            );
        }
        return provider;
    }

    private void executeReportTask(
            NoonPullTaskRecord task,
            NoonReportProvider provider,
            NoonReportDownloadedFileHandler handler,
            NoonPullScheduledExecutionResult result
    ) {
        if (provider == null) {
            result.failed();
            return;
        }
        NoonReportPullResult pullResult = reportPuller.execute(
                task.getId(),
                NoonReportPullRequest.builder()
                        .ownerUserId(task.getOwnerUserId())
                        .storeCode(task.getStoreCode())
                        .siteCode(task.getSiteCode())
                        .dataDomain(task.getDataDomain())
                        .reportType(reportType(task))
                        .dateFrom(task.getTargetDateFrom())
                        .dateTo(task.getTargetDateTo())
                        .maxPollAttempts(REPORT_MAX_POLL_ATTEMPTS)
                        .build(),
                provider,
                handler
        );
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED || pullResult.getStatus() == NoonPullTaskStatus.PARTIAL) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private String reportType(NoonPullTaskRecord task) {
        if (task.getDataDomain() == NoonPullDataDomain.ORDER) {
            return NoonOrderReportDescriptor.REPORT_TYPE;
        }
        return "productviewsandsalesdata";
    }
}
