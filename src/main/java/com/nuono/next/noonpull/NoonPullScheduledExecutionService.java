package com.nuono.next.noonpull;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final int DEFAULT_PRODUCT_DETAIL_FETCHES_PER_RUN = 10;

    private final NoonPullScheduler scheduler;
    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final NoonInterfacePuller interfacePuller;
    private final NoonSalesReportAdapter salesReportAdapter;
    private final NoonOrderReportAdapter orderReportAdapter;
    private final Supplier<? extends NoonReportProvider> salesProvider;
    private final Supplier<? extends NoonReportProvider> orderProvider;
    private final Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider;
    private final Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider;
    private final Supplier<? extends NoonProductDetailBaselineSyncer> productDetailBaselineSyncer;
    private final NoonProductListPullAdapter productListPullAdapter;
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
            ObjectProvider<NoonProductInterfaceSmokeProvider> productProvider,
            ObjectProvider<NoonProductDetailBaselineSyncer> productDetailBaselineSyncer,
            NoonProductListPullAdapter productListPullAdapter,
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
                () -> productProvider == null ? null : productProvider.getIfAvailable(),
                () -> productDetailBaselineSyncer == null ? null : productDetailBaselineSyncer.getIfAvailable(),
                productListPullAdapter,
                enabled
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
                null,
                null,
                null,
                enabled
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
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            Supplier<? extends NoonProductDetailBaselineSyncer> productDetailBaselineSyncer,
            NoonProductListPullAdapter productListPullAdapter,
            boolean enabled
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
        this.productProvider = productProvider;
        this.productDetailBaselineSyncer = productDetailBaselineSyncer;
        this.productListPullAdapter = productListPullAdapter;
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
        for (NoonPullTaskRecord task : schedulerResult.getCreatedTasks().stream()
                .sorted(Comparator.comparingInt(this::executionPriority)
                        .thenComparing(NoonPullTaskRecord::getId))
                .collect(Collectors.toList())) {
            executeTask(task, result);
            recordTaskOutcome(result, task.getId());
        }
        return result;
    }

    public NoonPullScheduledExecutionResult executeTaskIds(List<Long> taskIds) {
        NoonPullScheduledExecutionResult result = new NoonPullScheduledExecutionResult();
        if (taskIds == null || taskIds.isEmpty()) {
            return result;
        }
        Set<Long> requestedIds = taskIds.stream()
                .filter((taskId) -> taskId != null)
                .collect(Collectors.toCollection(HashSet::new));
        if (requestedIds.isEmpty()) {
            return result;
        }
        List<NoonPullTaskRecord> tasks = foundationService.listTasks().stream()
                .filter((task) -> requestedIds.contains(task.getId()))
                .sorted(Comparator.comparingInt(this::executionPriority)
                        .thenComparing(NoonPullTaskRecord::getId))
                .collect(Collectors.toList());
        for (NoonPullTaskRecord task : tasks) {
            if (task.getStatus() != NoonPullTaskStatus.QUEUED) {
                result.skipped();
                recordTaskOutcome(result, task.getId());
                continue;
            }
            executeTask(task, result);
            recordTaskOutcome(result, task.getId());
        }
        return result;
    }

    private void recordTaskOutcome(NoonPullScheduledExecutionResult result, Long taskId) {
        if (result == null || taskId == null) {
            return;
        }
        for (NoonPullTaskRecord task : foundationService.listTasks()) {
            if (taskId.equals(task.getId()) && isReducibleOutcome(task)) {
                result.addTaskOutcome(task);
                return;
            }
        }
    }

    private boolean isReducibleOutcome(NoonPullTaskRecord task) {
        return task != null
                && (task.getStatus() == NoonPullTaskStatus.SUCCEEDED
                || task.getStatus() == NoonPullTaskStatus.PARTIAL
                || task.getStatus() == NoonPullTaskStatus.FAILED
                || task.getStatus() == NoonPullTaskStatus.CANCELLED
                || task.getStatus() == NoonPullTaskStatus.SKIPPED);
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
            return;
        }
        if (task.getPullType() == NoonPullType.INTERFACE && task.getDataDomain() == NoonPullDataDomain.PRODUCT) {
            executeProductInterfaceTask(task, result);
            return;
        }
        if (task.getPullType() != NoonPullType.REPORT) {
            result.skipped();
            return;
        }
        if (task.getDataDomain() == NoonPullDataDomain.SALES) {
            executeReportTask(task, providerOrFail(task, salesProvider), salesReportAdapter::process, result);
            return;
        }
        if (task.getDataDomain() == NoonPullDataDomain.ORDER) {
            executeReportTask(task, providerOrFail(task, orderProvider), orderReportAdapter::process, result);
            return;
        }
        result.skipped();
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

    private void executeProductInterfaceTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
        if (isProductDetailTask(task)) {
            executeProductDetailTask(task, result);
            return;
        }
        NoonProductInterfaceSmokeProvider provider = productProvider == null ? null : productProvider.get();
        if (provider == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled product interface provider is disabled",
                    1
            );
            result.failed();
            return;
        }
        if (isProductListTask(task) && productListPullAdapter == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: product projection writer is disabled",
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
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .requestName(productRequestName(task))
                        .targetIdentity(task.getTargetIdentity())
                        .dateFrom(task.getTargetDateFrom())
                        .dateTo(task.getTargetDateTo())
                        .requestSummary("manual store-data product sync")
                        .build(),
                provider
        );
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            applyProductListIfNeeded(task, pullResult);
            result.executed();
        } else if (pullResult.getStatus() == NoonPullTaskStatus.PARTIAL) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private void executeProductDetailTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
        NoonProductDetailBaselineSyncer syncer =
                productDetailBaselineSyncer == null ? null : productDetailBaselineSyncer.get();
        if (syncer == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: product detail baseline syncer is disabled",
                    1
            );
            result.failed();
            return;
        }
        foundationService.markRunning(task.getId(), "noon-product-detail-syncer");
        try {
            NoonProductDetailBaselineSyncResult syncResult =
                    syncer.sync(productDetailRequest(task));
            if (syncResult == null) {
                foundationService.markFailedWithPolicy(
                        task.getId(),
                        "provider unavailable: empty product detail baseline sync result",
                        1
                );
                result.failed();
                return;
            }
            String sourceBatchId = "noon-product-detail-" + task.getId();
            if (syncResult.isSucceeded()) {
                foundationService.recordProgress(
                        task.getId(),
                        null,
                        syncResult.getSucceededCount(),
                        syncResult.getAttemptedCount(),
                        null,
                        syncResult.getDiagnosticSummary(),
                        "ready"
                );
                foundationService.markSucceeded(task.getId(), sourceBatchId, syncResult.getDiagnosticSummary());
                result.executed();
                return;
            }
            if (syncResult.isPartial()) {
                foundationService.markPartial(
                        task.getId(),
                        sourceBatchId,
                        syncResult.getDiagnosticSummary(),
                        null,
                        syncResult.getSucceededCount(),
                        syncResult.getAttemptedCount(),
                        syncResult.getNextResumePosition(),
                        syncResult.getFailureMessage(),
                        "partial_product_detail_baseline"
                );
                result.executed();
                return;
            }
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    productDetailFailure(syncResult),
                    1
            );
            result.failed();
        } catch (RuntimeException exception) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    1
            );
            result.failed();
        }
    }

    private NoonProductDetailBaselineSyncRequest productDetailRequest(NoonPullTaskRecord task) {
        NoonProductDetailBaselineSyncRequest request = new NoonProductDetailBaselineSyncRequest();
        request.setOwnerUserId(task.getOwnerUserId());
        request.setStoreCode(task.getStoreCode());
        request.setSiteCode(task.getSiteCode());
        request.setMaxDetailFetches(resolveProductDetailFetchLimit(task));
        request.setResumePosition(task.getNextResumePosition());
        return request;
    }

    private int resolveProductDetailFetchLimit(NoonPullTaskRecord task) {
        if (task != null && task.getPlanId() != null) {
            for (NoonPullPlanRecord plan : foundationService.listPlans()) {
                if (task.getPlanId().equals(plan.getId())
                        && plan.getMaxDetailFetchesPerRun() != null
                        && plan.getMaxDetailFetchesPerRun() > 0) {
                    return plan.getMaxDetailFetchesPerRun();
                }
            }
        }
        return DEFAULT_PRODUCT_DETAIL_FETCHES_PER_RUN;
    }

    private void applyProductListIfNeeded(NoonPullTaskRecord task, NoonInterfacePullResult pullResult) {
        if (!isProductListTask(task) || productListPullAdapter == null) {
            return;
        }
        productListPullAdapter.apply(NoonProductListApplyCommand.builder()
                .ownerUserId(task.getOwnerUserId())
                .projectCode(projectCode(task.getStoreCode()))
                .projectName(null)
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .sourceBatchId(pullResult.getSourceBatchId())
                .items(pullResult.getItems())
                .build());
    }

    private boolean isProductListTask(NoonPullTaskRecord task) {
        return task != null
                && task.getTargetIdentity() != null
                && task.getTargetIdentity().toLowerCase(Locale.ROOT).startsWith("product-list:");
    }

    private boolean isProductDetailTask(NoonPullTaskRecord task) {
        return task != null
                && task.getTargetIdentity() != null
                && task.getTargetIdentity().toLowerCase(Locale.ROOT).startsWith("product-detail:");
    }

    private String productRequestName(NoonPullTaskRecord task) {
        if (task != null
                && task.getTargetIdentity() != null
                && task.getTargetIdentity().toLowerCase(Locale.ROOT).startsWith("product-detail:")) {
            return "product-detail";
        }
        return "product-list";
    }

    private String projectCode(String storeCode) {
        if (storeCode == null) {
            return null;
        }
        String normalized = storeCode.trim();
        if (normalized.toUpperCase(Locale.ROOT).startsWith("STR")) {
            String suffix = normalized.substring(3).split("-", 2)[0];
            if (!suffix.isBlank()) {
                return "PRJ" + suffix;
            }
        }
        return normalized;
    }

    private String productDetailFailure(NoonProductDetailBaselineSyncResult result) {
        if (result == null) {
            return "provider unavailable: empty product detail baseline sync result";
        }
        String failureMessage = result.getFailureMessage();
        if (failureMessage != null && !failureMessage.isBlank()) {
            return failureMessage;
        }
        return "provider unavailable: product detail baseline sync failed";
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
