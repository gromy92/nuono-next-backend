package com.nuono.next.noonpull;

import java.util.function.Supplier;

/** Executes legacy Sales page-query and Product interface tasks. */
final class LegacyNoonProductTaskExecutor implements LegacyNoonTaskExecutor {
    private final NoonPullFoundationService foundationService;
    private final NoonInterfacePuller interfacePuller;
    private final NoonProductListPullAdapter productListAdapter;
    private final Supplier<? extends NoonSalesPageQueryProvider> salesProvider;
    private final Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider;

    LegacyNoonProductTaskExecutor(
            NoonPullFoundationService foundationService,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            Supplier<? extends NoonSalesPageQueryProvider> salesProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider
    ) {
        this.foundationService = foundationService;
        this.interfacePuller = interfacePuller;
        this.productListAdapter = productListAdapter;
        this.salesProvider = salesProvider;
        this.productProvider = productProvider;
    }

    @Override
    public boolean accepts(NoonPullTaskRecord task) {
        return isSalesPageQuery(task) || isProductInterface(task);
    }

    @Override
    public void execute(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (isSalesPageQuery(task)) {
            executeSales(task, result);
        } else {
            executeProduct(task, result);
        }
    }

    private void executeSales(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        NoonSalesPageQueryProvider provider = salesProvider == null
                ? null : salesProvider.get();
        if (provider == null) {
            fail(task, "provider not configured: scheduled sales page query provider is disabled", result);
            return;
        }
        NoonInterfacePullResult pullResult = interfacePuller.execute(
                task.getId(), interfaceRequest(task, NoonPullDataDomain.SALES,
                        "sales-page-query", "scheduled daily page-query"), provider
        );
        recordInterfaceOutcome(pullResult, result);
    }

    private void executeProduct(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        NoonProductInterfaceSmokeProvider provider = productProvider == null
                ? null : productProvider.get();
        if (provider == null) {
            fail(task, "provider not configured: scheduled product interface provider is disabled", result);
            return;
        }
        NoonInterfacePullResult pullResult = interfacePuller.execute(
                task.getId(), interfaceRequest(task, NoonPullDataDomain.PRODUCT,
                        "product-list", "scheduled daily product interface"), provider
        );
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            if (productListAdapter != null && !NoonProductListTaskProjectionSupport.apply(
                    productListAdapter, task, pullResult, foundationService
            )) {
                result.failed();
                return;
            }
            result.executed();
            return;
        }
        recordInterfaceOutcome(pullResult, result);
    }

    private NoonInterfacePullRequest interfaceRequest(
            NoonPullTaskRecord task,
            NoonPullDataDomain domain,
            String requestName,
            String summary
    ) {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .dataDomain(domain)
                .requestName(requestName)
                .targetIdentity(task.getTargetIdentity())
                .dateFrom(task.getTargetDateFrom())
                .dateTo(task.getTargetDateTo())
                .requestSummary(summary)
                .build();
    }

    private void recordInterfaceOutcome(
            NoonInterfacePullResult pullResult,
            NoonPullScheduledExecutionResult result
    ) {
        NoonPullTaskStatus status = pullResult.getStatus();
        if (status == NoonPullTaskStatus.SUCCEEDED
                || status == NoonPullTaskStatus.PARTIAL
                || status == NoonPullTaskStatus.RUNNING) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private void fail(
            NoonPullTaskRecord task,
            String message,
            NoonPullScheduledExecutionResult result
    ) {
        foundationService.markFailedWithPolicy(task.getId(), message, 1);
        result.failed();
    }

    private boolean isSalesPageQuery(NoonPullTaskRecord task) {
        return task != null
                && task.getPullType() == NoonPullType.PAGE_QUERY
                && task.getDataDomain() == NoonPullDataDomain.SALES;
    }

    private boolean isProductInterface(NoonPullTaskRecord task) {
        return task != null
                && task.getPullType() == NoonPullType.INTERFACE
                && task.getDataDomain() == NoonPullDataDomain.PRODUCT;
    }
}
