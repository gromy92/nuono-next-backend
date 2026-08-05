package com.nuono.next.noonpull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportProvider;
import com.nuono.next.noonmaintenance.StoreSiteMaintenanceGate;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportQueryService;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportService;
import com.nuono.next.officialwarehouse.OfficialWarehouseInventorySyncService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Conditional legacy rollback facade; domain execution lives in focused adapters. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class NoonPullScheduledExecutionService {
    private final NoonPullScheduler scheduler;
    private final LegacyNoonPullTickRunner tickRunner;
    private final boolean enabled;
    private NoonPullRetryExecutor retryExecutor;
    private StoreSiteMaintenanceGate maintenanceGate = StoreSiteMaintenanceGate.allowAll();

    @Autowired
    public NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            ObjectProvider<NoonFinanceTransactionReportAdapter> financeReportAdapter,
            ObjectProvider<NoonAdvertisingReportAdapter> advertisingReportAdapter,
            ObjectProvider<OfficialWarehouseInventorySyncService> inventorySyncService,
            ObjectProvider<OfficialWarehouseFbnExportQueryService> fbnExportService,
            ObjectProvider<OfficialWarehouseFbnReceivedReportImportService> fbnImportService,
            ObjectProvider<NoonRiskBackoffGuard> riskBackoffGuard,
            ObjectProvider<NoonPullFailurePolicy> failurePolicy,
            ObjectProvider<NoonSalesReportSmokeProvider> salesProvider,
            ObjectProvider<NoonOrderReportSmokeProvider> orderProvider,
            ObjectProvider<NoonFinanceTransactionReportProvider> financeProvider,
            ObjectProvider<NoonAdvertisingReportProvider> advertisingProvider,
            ObjectProvider<NoonSalesPageQueryProvider> salesPageQueryProvider,
            ObjectProvider<NoonProductInterfaceSmokeProvider> productProvider,
            @Value("${nuono.noon.pull.scheduler.enabled:false}") boolean enabled,
            @Value("${nuono.noon.pull.scheduler.sales-report-executions-per-tick:4}")
            int salesReportExecutionsPerTick,
            @Value("${nuono.noon.pull.scheduler.product-interface-executions-per-tick:2}")
            int productInterfaceExecutionsPerTick
    ) {
        this.scheduler = scheduler;
        this.enabled = enabled;
        NoonRiskBackoffGuard riskGuard = riskBackoffGuard == null
                ? NoonRiskBackoffGuard.disabled()
                : riskBackoffGuard.getIfAvailable(NoonRiskBackoffGuard::disabled);
        NoonPullFailurePolicy effectiveFailurePolicy = failurePolicy == null
                ? new NoonPullFailurePolicy()
                : failurePolicy.getIfAvailable(NoonPullFailurePolicy::new);
        LegacyNoonPullFailureRecorder failures = new LegacyNoonPullFailureRecorder(
                foundationService, riskGuard, effectiveFailurePolicy
        );
        LegacyNoonPullTaskDispatcher dispatcher = new LegacyNoonPullTaskDispatcher(
                List.of(
                        new LegacyNoonProductTaskExecutor(
                                foundationService, interfacePuller, productListAdapter,
                                () -> available(salesPageQueryProvider),
                                () -> available(productProvider)
                        ),
                        new LegacyNoonInventoryTaskExecutor(
                                foundationService, available(inventorySyncService),
                                riskGuard, failures
                        ),
                        new LegacyNoonFbnReceivedTaskExecutor(
                                foundationService, available(fbnExportService),
                                available(fbnImportService), riskGuard, failures
                        ),
                        new LegacyNoonReportTaskExecutor(
                                foundationService, reportPuller,
                                salesReportAdapter, orderReportAdapter,
                                available(financeReportAdapter),
                                available(advertisingReportAdapter),
                                () -> available(salesProvider),
                                () -> available(orderProvider),
                                () -> available(financeProvider),
                                () -> available(advertisingProvider)
                        )
                ),
                this::currentRetryExecutor
        );
        this.tickRunner = new LegacyNoonPullTickRunner(
                foundationService, dispatcher,
                salesReportExecutionsPerTick, productInterfaceExecutionsPerTick
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            LegacyNoonPullTickRunner tickRunner,
            boolean enabled
    ) {
        this.scheduler = scheduler;
        this.tickRunner = tickRunner;
        this.enabled = enabled;
    }

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
        if (retryExecutor != null) {
            result.created(retryExecutor.retryDueTasks());
        }
        NoonPullSchedulerResult schedulerResult = scheduler.runDuePlans();
        result.created(schedulerResult.getCreatedTaskCount());
        tickRunner.execute(schedulerResult, maintenanceGate, result);
        return result;
    }

    @Autowired(required = false)
    void setRetryExecutor(NoonPullRetryExecutor retryExecutor) {
        this.retryExecutor = retryExecutor;
    }

    @Autowired(required = false)
    void setMaintenanceGate(StoreSiteMaintenanceGate maintenanceGate) {
        this.maintenanceGate = maintenanceGate == null
                ? StoreSiteMaintenanceGate.allowAll() : maintenanceGate;
    }

    private NoonPullRetryExecutor currentRetryExecutor() {
        return retryExecutor;
    }

    private static <T> T available(ObjectProvider<T> provider) {
        return provider == null ? null : provider.getIfAvailable();
    }
}
