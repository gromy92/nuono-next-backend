package com.nuono.next.noonpull;

import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import com.nuono.next.noonads.NoonAdvertisingReportProvider;
import com.nuono.next.noonmaintenance.StoreSiteMaintenanceGate;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportQueryService;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportService;
import com.nuono.next.officialwarehouse.OfficialWarehouseInventorySyncService;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportCreateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportStatusView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InventorySyncResultView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Comparator;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullScheduledExecutionService {
    private static final int REPORT_MAX_POLL_ATTEMPTS = 18;
    private static final int DEFAULT_SALES_REPORT_EXECUTIONS_PER_TICK = 4;
    private static final int DEFAULT_PRODUCT_INTERFACE_EXECUTIONS_PER_TICK = 2;
    private static final Duration OFFICIAL_WAREHOUSE_FBN_EXPORT_POLL_DELAY = Duration.ofMinutes(20);
    private static final String FBN_RECEIVED_REPORT_TYPE = "fbn_inbound_fbnreceivedreport";

    private final NoonPullScheduler scheduler;
    private final NoonPullFoundationService foundationService;
    private final NoonReportPuller reportPuller;
    private final NoonInterfacePuller interfacePuller;
    private final NoonProductListPullAdapter productListAdapter;
    private final NoonSalesReportAdapter salesReportAdapter;
    private final NoonOrderReportAdapter orderReportAdapter;
    private final NoonFinanceTransactionReportAdapter financeReportAdapter;
    private final NoonAdvertisingReportAdapter advertisingReportAdapter;
    private final OfficialWarehouseInventorySyncService officialWarehouseInventorySyncService;
    private final OfficialWarehouseFbnExportQueryService officialWarehouseFbnExportQueryService;
    private final OfficialWarehouseFbnReceivedReportImportService officialWarehouseFbnReceivedReportImportService;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final NoonPullFailurePolicy failurePolicy;
    private final Supplier<? extends NoonReportProvider> salesProvider;
    private final Supplier<? extends NoonReportProvider> orderProvider;
    private final Supplier<? extends NoonReportProvider> financeProvider;
    private final Supplier<? extends NoonAdvertisingReportProvider> advertisingProvider;
    private final Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider;
    private final Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider;
    private final boolean enabled;
    private final int salesReportExecutionsPerTick;
    private final int productInterfaceExecutionsPerTick;
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
            ObjectProvider<OfficialWarehouseInventorySyncService> officialWarehouseInventorySyncService,
            ObjectProvider<OfficialWarehouseFbnExportQueryService> officialWarehouseFbnExportQueryService,
            ObjectProvider<OfficialWarehouseFbnReceivedReportImportService> officialWarehouseFbnReceivedReportImportService,
            ObjectProvider<NoonRiskBackoffGuard> riskBackoffGuard,
            ObjectProvider<NoonPullFailurePolicy> failurePolicy,
            ObjectProvider<NoonSalesReportSmokeProvider> salesProvider,
            ObjectProvider<NoonOrderReportSmokeProvider> orderProvider,
            ObjectProvider<NoonFinanceTransactionReportProvider> financeProvider,
            ObjectProvider<NoonAdvertisingReportProvider> advertisingProvider,
            ObjectProvider<NoonSalesPageQueryProvider> salesPageQueryProvider,
            ObjectProvider<NoonProductInterfaceSmokeProvider> productProvider,
            @Value("${nuono.noon.pull.scheduler.enabled:false}") boolean enabled,
            @Value("${nuono.noon.pull.scheduler.sales-report-executions-per-tick:4}") int salesReportExecutionsPerTick,
            @Value("${nuono.noon.pull.scheduler.product-interface-executions-per-tick:2}") int productInterfaceExecutionsPerTick
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                financeReportAdapter == null ? null : financeReportAdapter.getIfAvailable(),
                advertisingReportAdapter == null ? null : advertisingReportAdapter.getIfAvailable(),
                officialWarehouseInventorySyncService == null ? null : officialWarehouseInventorySyncService.getIfAvailable(),
                officialWarehouseFbnExportQueryService == null ? null : officialWarehouseFbnExportQueryService.getIfAvailable(),
                officialWarehouseFbnReceivedReportImportService == null ? null : officialWarehouseFbnReceivedReportImportService.getIfAvailable(),
                riskBackoffGuard == null
                        ? NoonRiskBackoffGuard.disabled()
                        : riskBackoffGuard.getIfAvailable(NoonRiskBackoffGuard::disabled),
                failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy.getIfAvailable(NoonPullFailurePolicy::new),
                () -> salesProvider == null ? null : salesProvider.getIfAvailable(),
                () -> orderProvider == null ? null : orderProvider.getIfAvailable(),
                () -> financeProvider == null ? null : financeProvider.getIfAvailable(),
                () -> advertisingProvider == null ? null : advertisingProvider.getIfAvailable(),
                () -> salesPageQueryProvider == null ? null : salesPageQueryProvider.getIfAvailable(),
                () -> productProvider == null ? null : productProvider.getIfAvailable(),
                enabled,
                salesReportExecutionsPerTick,
                productInterfaceExecutionsPerTick
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                null,
                salesProvider,
                orderProvider,
                null,
                salesPageQueryProvider,
                productProvider,
                enabled
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            NoonFinanceTransactionReportAdapter financeReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonReportProvider> financeProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                financeReportAdapter,
                null,
                null,
                null,
                null,
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(),
                salesProvider,
                orderProvider,
                financeProvider,
                null,
                salesPageQueryProvider,
                productProvider,
                enabled,
                DEFAULT_SALES_REPORT_EXECUTIONS_PER_TICK,
                DEFAULT_PRODUCT_INTERFACE_EXECUTIONS_PER_TICK
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
                null,
                salesReportAdapter,
                orderReportAdapter,
                salesProvider,
                orderProvider,
                salesPageQueryProvider,
                null,
                enabled,
                DEFAULT_SALES_REPORT_EXECUTIONS_PER_TICK,
                DEFAULT_PRODUCT_INTERFACE_EXECUTIONS_PER_TICK
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled,
            int salesReportExecutionsPerTick,
            int productInterfaceExecutionsPerTick
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                null,
                salesProvider,
                orderProvider,
                null,
                salesPageQueryProvider,
                productProvider,
                enabled,
                salesReportExecutionsPerTick,
                productInterfaceExecutionsPerTick
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            NoonFinanceTransactionReportAdapter financeReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonReportProvider> financeProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled,
            int salesReportExecutionsPerTick,
            int productInterfaceExecutionsPerTick
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                financeReportAdapter,
                null,
                null,
                null,
                null,
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(),
                salesProvider,
                orderProvider,
                financeProvider,
                null,
                salesPageQueryProvider,
                productProvider,
                enabled,
                salesReportExecutionsPerTick,
                productInterfaceExecutionsPerTick
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            NoonFinanceTransactionReportAdapter financeReportAdapter,
            NoonAdvertisingReportAdapter advertisingReportAdapter,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonReportProvider> financeProvider,
            Supplier<? extends NoonAdvertisingReportProvider> advertisingProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                financeReportAdapter,
                advertisingReportAdapter,
                null,
                null,
                null,
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(),
                salesProvider,
                orderProvider,
                financeProvider,
                advertisingProvider,
                salesPageQueryProvider,
                productProvider,
                enabled,
                DEFAULT_SALES_REPORT_EXECUTIONS_PER_TICK,
                DEFAULT_PRODUCT_INTERFACE_EXECUTIONS_PER_TICK
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            NoonFinanceTransactionReportAdapter financeReportAdapter,
            OfficialWarehouseInventorySyncService officialWarehouseInventorySyncService,
            OfficialWarehouseFbnExportQueryService officialWarehouseFbnExportQueryService,
            OfficialWarehouseFbnReceivedReportImportService officialWarehouseFbnReceivedReportImportService,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonReportProvider> financeProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                productListAdapter,
                salesReportAdapter,
                orderReportAdapter,
                financeReportAdapter,
                null,
                officialWarehouseInventorySyncService,
                officialWarehouseFbnExportQueryService,
                officialWarehouseFbnReceivedReportImportService,
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(),
                salesProvider,
                orderProvider,
                financeProvider,
                null,
                salesPageQueryProvider,
                productProvider,
                enabled,
                DEFAULT_SALES_REPORT_EXECUTIONS_PER_TICK,
                DEFAULT_PRODUCT_INTERFACE_EXECUTIONS_PER_TICK
        );
    }

    NoonPullScheduledExecutionService(
            NoonPullScheduler scheduler,
            NoonPullFoundationService foundationService,
            NoonReportPuller reportPuller,
            NoonInterfacePuller interfacePuller,
            NoonProductListPullAdapter productListAdapter,
            NoonSalesReportAdapter salesReportAdapter,
            NoonOrderReportAdapter orderReportAdapter,
            NoonFinanceTransactionReportAdapter financeReportAdapter,
            NoonAdvertisingReportAdapter advertisingReportAdapter,
            OfficialWarehouseInventorySyncService officialWarehouseInventorySyncService,
            OfficialWarehouseFbnExportQueryService officialWarehouseFbnExportQueryService,
            OfficialWarehouseFbnReceivedReportImportService officialWarehouseFbnReceivedReportImportService,
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy,
            Supplier<? extends NoonReportProvider> salesProvider,
            Supplier<? extends NoonReportProvider> orderProvider,
            Supplier<? extends NoonReportProvider> financeProvider,
            Supplier<? extends NoonAdvertisingReportProvider> advertisingProvider,
            Supplier<? extends NoonSalesPageQueryProvider> salesPageQueryProvider,
            Supplier<? extends NoonProductInterfaceSmokeProvider> productProvider,
            boolean enabled,
            int salesReportExecutionsPerTick,
            int productInterfaceExecutionsPerTick
    ) {
        this.scheduler = scheduler;
        this.foundationService = foundationService;
        this.reportPuller = reportPuller;
        this.interfacePuller = interfacePuller;
        this.productListAdapter = productListAdapter;
        this.salesReportAdapter = salesReportAdapter;
        this.orderReportAdapter = orderReportAdapter;
        this.financeReportAdapter = financeReportAdapter;
        this.advertisingReportAdapter = advertisingReportAdapter;
        this.officialWarehouseInventorySyncService = officialWarehouseInventorySyncService;
        this.officialWarehouseFbnExportQueryService = officialWarehouseFbnExportQueryService;
        this.officialWarehouseFbnReceivedReportImportService = officialWarehouseFbnReceivedReportImportService;
        this.riskBackoffGuard = riskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failurePolicy = failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy;
        this.salesProvider = salesProvider;
        this.orderProvider = orderProvider;
        this.financeProvider = financeProvider;
        this.advertisingProvider = advertisingProvider;
        this.salesPageQueryProvider = salesPageQueryProvider;
        this.productProvider = productProvider;
        this.enabled = enabled;
        this.salesReportExecutionsPerTick = Math.max(1, salesReportExecutionsPerTick);
        this.productInterfaceExecutionsPerTick = Math.max(1, productInterfaceExecutionsPerTick);
    }

    @Autowired(required = false)
    void setMaintenanceGate(StoreSiteMaintenanceGate maintenanceGate) {
        this.maintenanceGate = maintenanceGate == null ? StoreSiteMaintenanceGate.allowAll() : maintenanceGate;
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
            int salesReportExecutionsPerTick
    ) {
        this(
                scheduler,
                foundationService,
                reportPuller,
                interfacePuller,
                null,
                salesReportAdapter,
                orderReportAdapter,
                salesProvider,
                orderProvider,
                salesPageQueryProvider,
                null,
                enabled,
                salesReportExecutionsPerTick,
                DEFAULT_PRODUCT_INTERFACE_EXECUTIONS_PER_TICK
        );
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
        NoonPullSchedulerResult schedulerResult = scheduler.runDuePlans();
        result.created(schedulerResult.getCreatedTaskCount());
        int salesReportExecutions = 0;
        int productInterfaceExecutions = 0;
        for (NoonPullTaskRecord task : executableQueuedTasks(schedulerResult)) {
            if (isScheduledMaintenance(task)) {
                result.skipped();
                continue;
            }
            if (isSalesReportTask(task)) {
                if (salesReportExecutions >= salesReportExecutionsPerTick) {
                    result.skipped();
                    continue;
                }
                salesReportExecutions++;
            }
            if (isProductInterfaceTask(task)) {
                if (productInterfaceExecutions >= productInterfaceExecutionsPerTick) {
                    result.skipped();
                    continue;
                }
                productInterfaceExecutions++;
            }
            executeTask(task, result);
        }
        return result;
    }

    private boolean isScheduledMaintenance(NoonPullTaskRecord task) {
        return task != null
                && task.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY
                && maintenanceGate.isUnderMaintenance(
                        task.getOwnerUserId(),
                        task.getStoreCode(),
                        task.getSiteCode()
                );
    }

    private List<NoonPullTaskRecord> executableQueuedTasks(NoonPullSchedulerResult schedulerResult) {
        Map<Long, NoonPullTaskRecord> tasksById = new LinkedHashMap<>();
        if (schedulerResult != null) {
            for (NoonPullTaskRecord task : schedulerResult.getCreatedTasks()) {
                addExecutableQueuedTask(tasksById, task);
            }
        }
        for (NoonPullTaskRecord task : foundationService.listActiveTasks()) {
            addExecutableQueuedTask(tasksById, task);
        }
        return tasksById.values().stream()
                .sorted(Comparator.comparingInt(this::executionPriority)
                        .thenComparing(NoonPullTaskRecord::getId))
                .collect(Collectors.toList());
    }

    private void addExecutableQueuedTask(Map<Long, NoonPullTaskRecord> tasksById, NoonPullTaskRecord task) {
        if (task == null || task.getId() == null || !isExecutableTaskStatus(task)) {
            return;
        }
        if (!foundationService.isTaskPlanActive(task) || !isExecutableByScheduledWorker(task)) {
            return;
        }
        if (task.getStatus() == NoonPullTaskStatus.RUNNING && !isReportPollDue(task)) {
            return;
        }
        tasksById.putIfAbsent(task.getId(), task);
    }

    private boolean isExecutableTaskStatus(NoonPullTaskRecord task) {
        if (task.getStatus() == NoonPullTaskStatus.QUEUED) {
            return true;
        }
        if (task.getStatus() != NoonPullTaskStatus.RUNNING || task.getPullType() != NoonPullType.REPORT) {
            return false;
        }
        return task.getReportExportId() != null
                || ("risk_backoff".equals(task.getReadinessState()) && task.getReportNextPollAt() != null);
    }

    private boolean isReportPollDue(NoonPullTaskRecord task) {
        return task.getReportNextPollAt() == null
                || !task.getReportNextPollAt().isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    private boolean isExecutableByScheduledWorker(NoonPullTaskRecord task) {
        return NoonPullAuthRecoveryTaskPolicy.canAutomaticallyRecover(task);
    }

    private int executionPriority(NoonPullTaskRecord task) {
        if (task == null) {
            return 99;
        }
        if (task.getDataDomain() == NoonPullDataDomain.SALES && task.getPullType() == NoonPullType.PAGE_QUERY) {
            return 0;
        }
        if (task.getDataDomain() == NoonPullDataDomain.PRODUCT && task.getPullType() == NoonPullType.INTERFACE) {
            return 1;
        }
        if (task.getDataDomain() == NoonPullDataDomain.SALES && task.getPullType() == NoonPullType.REPORT) {
            return 2;
        }
        if (task.getDataDomain() == NoonPullDataDomain.ORDER && task.getPullType() == NoonPullType.REPORT) {
            return 3;
        }
        if (task.getDataDomain() == NoonPullDataDomain.FINANCE_TRANSACTION && task.getPullType() == NoonPullType.REPORT) {
            return 4;
        }
        if (task.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY
                && task.getPullType() == NoonPullType.INTERFACE) {
            return 5;
        }
        if (task.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED
                && task.getPullType() == NoonPullType.REPORT) {
            return 6;
        }
        if (task.getDataDomain() == NoonPullDataDomain.NOON_ADVERTISING && task.getPullType() == NoonPullType.REPORT) {
            return 7;
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
        if (task.getPullType() == NoonPullType.INTERFACE
                && task.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY) {
            executeOfficialWarehouseInventoryTask(task, result);
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
        if (task.getDataDomain() == NoonPullDataDomain.FINANCE_TRANSACTION) {
            executeFinanceReportTask(task, result);
            return;
        }
        if (task.getDataDomain() == NoonPullDataDomain.NOON_ADVERTISING) {
            executeAdvertisingReportTask(task, result);
            return;
        }
        if (task.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED) {
            executeOfficialWarehouseFbnReceivedReportTask(task, result);
            return;
        }
        result.skipped();
    }

    private void executeFinanceReportTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
        if (financeReportAdapter == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "handler not configured: scheduled finance transaction report adapter is disabled",
                    1
            );
            result.failed();
            return;
        }
        executeReportTask(task, providerOrFail(task, financeProvider), financeReportAdapter::process, result);
    }

    private void executeAdvertisingReportTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
        if (advertisingReportAdapter == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "handler not configured: scheduled noon advertising report adapter is disabled",
                    1
            );
            result.failed();
            return;
        }
        executeReportTask(task, providerOrFail(task, advertisingProvider), advertisingReportAdapter::process, result);
    }

    private void executeOfficialWarehouseInventoryTask(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (officialWarehouseInventorySyncService == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled official warehouse inventory sync service is disabled",
                    1
            );
            result.failed();
            return;
        }
        NoonInterfacePullRequest request = officialWarehouseInterfaceRequest(task, "official-warehouse-fbn-inventory");
        Optional<NoonRiskBackoffHold> activeHold = riskBackoffGuard.currentHold(NoonRiskBackoffScope.interfacePull(request));
        if (activeHold.isPresent()) {
            foundationService.recordInterfaceRiskBackoffDelay(
                    task.getId(),
                    activeHold.get(),
                    "official warehouse inventory"
            );
            result.failed();
            return;
        }
        NoonPullTaskRecord running = foundationService.markRunning(
                task.getId(),
                "official-warehouse-inventory-sync"
        );
        if (running.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            result.skipped();
            return;
        }
        try {
            InventorySyncCommand command = new InventorySyncCommand();
            command.storeCode = task.getStoreCode();
            command.siteCode = task.getSiteCode();
            InventorySyncResultView syncResult = officialWarehouseInventorySyncService.sync(accessForTask(task), command);
            String sourceBatchId = "official-warehouse-inventory-" + task.getId() + "-" + valueOrUnknown(syncResult.syncBatchId);
            foundationService.markSucceeded(task.getId(),
                    sourceBatchId,
                    "official warehouse inventory synced; fetched=" + syncResult.fetchedRows
                            + "; inserted=" + syncResult.insertedRows
            );
            riskBackoffGuard.recordSuccess(NoonRiskBackoffScope.interfacePull(request), task.getDataDomain().name());
            result.executed();
        } catch (RuntimeException exception) {
            markInterfaceFailureOrRiskBackoff(task, request, safeMessage(exception));
            result.failed();
        }
    }

    private void executeOfficialWarehouseFbnReceivedReportTask(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (officialWarehouseFbnExportQueryService == null || officialWarehouseFbnReceivedReportImportService == null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: scheduled official warehouse FBN received report service is disabled",
                    1
            );
            result.failed();
            return;
        }
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .dataDomain(task.getDataDomain())
                .reportType(FBN_RECEIVED_REPORT_TYPE)
                .dateFrom(task.getTargetDateFrom())
                .dateTo(task.getTargetDateTo())
                .maxPollAttempts(REPORT_MAX_POLL_ATTEMPTS)
                .build();
        Optional<NoonRiskBackoffHold> activeHold = riskBackoffGuard.currentHold(NoonRiskBackoffScope.report(request));
        if (activeHold.isPresent()) {
            foundationService.recordReportRiskBackoffDelay(task.getId(), activeHold.get(), request.descriptor());
            result.failed();
            return;
        }

        NoonPullTaskRecord running = foundationService.markRunning(task.getId(), "official-warehouse-fbn-received-report");
        if (running.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            result.skipped();
            return;
        }
        String exportCode = running.getReportExportId();
        int pollAttempts = running.getReportPollAttempts() == null ? 0 : running.getReportPollAttempts();
        try {
            BusinessAccessContext access = accessForTask(task);
            if (!StringUtils.hasText(exportCode)) {
                FbnExportCreateCommand createCommand = new FbnExportCreateCommand();
                createCommand.storeCode = task.getStoreCode();
                createCommand.siteCode = task.getSiteCode();
                createCommand.exportCategoryCode = FBN_RECEIVED_REPORT_TYPE;
                createCommand.fromDate = task.getTargetDateFrom().toString();
                createCommand.toDate = task.getTargetDateTo().toString();
                FbnExportCreateView createView = officialWarehouseFbnExportQueryService.createExport(access, createCommand);
                exportCode = createView == null ? null : createView.exportCode;
                if (!StringUtils.hasText(exportCode)) {
                    foundationService.markFailedWithPolicy(task.getId(), "mapping failed: missing FBN export code", 1);
                    result.failed();
                    return;
                }
                running = foundationService.recordReportExportCreated(
                        task.getId(),
                        exportCode,
                        request.descriptor() + "; exportCreated=true; exportCode=" + exportCode
                );
                pollAttempts = running.getReportPollAttempts() == null ? 0 : running.getReportPollAttempts();
            }

            pollAttempts++;
            FbnExportStatusView statusView = officialWarehouseFbnExportQueryService.exportStatus(
                    access,
                    task.getStoreCode(),
                    task.getSiteCode(),
                    exportCode,
                    false
            );
            NoonReportExportStatus exportStatus = fbnExportStatus(statusView);
            foundationService.recordReportExportPollResult(
                    task.getId(),
                    exportCode,
                    exportStatus,
                    pollAttempts,
                    exportStatus.isReady() || exportStatus.isFailed() ? null : OFFICIAL_WAREHOUSE_FBN_EXPORT_POLL_DELAY,
                    "official warehouse FBN received export; status=" + exportStatus.getStatus()
            );
            if (exportStatus.isFailed()) {
                foundationService.markFailedWithPolicy(
                        task.getId(),
                        "provider unavailable: FBN received export failed " + exportStatus.getMessage(),
                        pollAttempts
                );
                result.failed();
                return;
            }
            if (!exportStatus.isReady()) {
                result.failed();
                return;
            }

            FbnReceivedImportCommand importCommand = new FbnReceivedImportCommand();
            importCommand.storeCode = task.getStoreCode();
            importCommand.siteCode = task.getSiteCode();
            importCommand.logStatus = false;
            FbnReceivedImportResultView importResult =
                    officialWarehouseFbnReceivedReportImportService.importByExportCode(access, exportCode, importCommand);
            String importId = importResult == null ? null : importResult.importId;
            String sourceBatchId = "official-warehouse-fbn-received-" + task.getId() + "-" + valueOrUnknown(importId);
            foundationService.markSucceeded(task.getId(),
                    sourceBatchId,
                    "official warehouse FBN received imported; rows="
                            + (importResult == null ? 0 : importResult.insertedReceiptLines)
            );
            riskBackoffGuard.recordSuccess(NoonRiskBackoffScope.report(request), task.getDataDomain().name());
            result.executed();
        } catch (RuntimeException exception) {
            markReportFailureOrRiskBackoff(task, request, exportCode, Math.max(1, pollAttempts), safeMessage(exception));
            result.failed();
        }
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
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED
                || pullResult.getStatus() == NoonPullTaskStatus.PARTIAL
                || pullResult.getStatus() == NoonPullTaskStatus.RUNNING) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private void executeProductInterfaceTask(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result) {
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
        NoonInterfacePullResult pullResult = interfacePuller.execute(
                task.getId(),
                NoonInterfacePullRequest.builder()
                        .ownerUserId(task.getOwnerUserId())
                        .storeCode(task.getStoreCode())
                        .siteCode(task.getSiteCode())
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .requestName("product-list")
                        .targetIdentity(task.getTargetIdentity())
                        .dateFrom(task.getTargetDateFrom())
                        .dateTo(task.getTargetDateTo())
                        .requestSummary("scheduled daily product interface")
                        .build(),
                provider
        );
        if (pullResult.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            if (productListAdapter != null) {
                productListAdapter.apply(NoonProductListApplyCommand.builder()
                        .ownerUserId(task.getOwnerUserId())
                        .projectCode(deriveProjectCode(task.getStoreCode()))
                        .storeCode(task.getStoreCode())
                        .siteCode(task.getSiteCode())
                        .sourceBatchId(pullResult.getSourceBatchId())
                        .automaticDetailBackfill(task.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY)
                        .items(pullResult.getItems())
                        .build());
            }
            result.executed();
            return;
        }
        if (pullResult.getStatus() == NoonPullTaskStatus.PARTIAL
                || pullResult.getStatus() == NoonPullTaskStatus.RUNNING) {
            result.executed();
        } else {
            result.failed();
        }
    }

    private boolean isSalesReportTask(NoonPullTaskRecord task) {
        return task != null
                && task.getPullType() == NoonPullType.REPORT
                && task.getDataDomain() == NoonPullDataDomain.SALES;
    }
    private boolean isProductInterfaceTask(NoonPullTaskRecord task) {
        return task != null
                && task.getPullType() == NoonPullType.INTERFACE
                && task.getDataDomain() == NoonPullDataDomain.PRODUCT;
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
        if (task.getDataDomain() == NoonPullDataDomain.FINANCE_TRANSACTION) {
            return NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE;
        }
        if (task.getDataDomain() == NoonPullDataDomain.NOON_ADVERTISING) {
            return NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE;
        }
        return "productviewsandsalesdata";
    }

    private BusinessAccessContext accessForTask(NoonPullTaskRecord task) {
        return BusinessAccessContext.builder()
                .sessionUserId(task.getOwnerUserId())
                .businessOwnerUserId(task.getOwnerUserId())
                .storeCodes(Set.of(task.getStoreCode()))
                .storeOwnerUserIds(Map.of(task.getStoreCode(), task.getOwnerUserId()))
                .build();
    }

    private NoonInterfacePullRequest officialWarehouseInterfaceRequest(NoonPullTaskRecord task, String requestName) {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .dataDomain(task.getDataDomain())
                .requestName(requestName)
                .targetIdentity(task.getTargetIdentity())
                .dateFrom(task.getTargetDateFrom())
                .dateTo(task.getTargetDateTo())
                .requestSummary("scheduled daily official warehouse pull")
                .build();
    }

    private NoonReportExportStatus fbnExportStatus(FbnExportStatusView statusView) {
        if (statusView == null) {
            return NoonReportExportStatus.pending();
        }
        String status = statusView.status;
        if (isFbnExportComplete(status)) {
            return NoonReportExportStatus.ready(statusView.downloadUrl, statusView.totalRows);
        }
        if (isFbnExportFailed(status)) {
            return NoonReportExportStatus.failed(statusView.message);
        }
        return NoonReportExportStatus.pending(status);
    }

    private boolean isFbnExportComplete(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "COMPLETE".equals(normalized)
                || "COMPLETED".equals(normalized)
                || "SUCCESS".equals(normalized)
                || "READY".equals(normalized)
                || "DONE".equals(normalized);
    }

    private boolean isFbnExportFailed(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "FAILED".equals(normalized)
                || "FAILURE".equals(normalized)
                || "ERROR".equals(normalized)
                || "CANCELLED".equals(normalized)
                || "CANCELED".equals(normalized);
    }

    private void markInterfaceFailureOrRiskBackoff(
            NoonPullTaskRecord task,
            NoonInterfacePullRequest request,
            String rawFailure
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isRiskBackoffFailure(failureType)) {
            foundationService.markFailedWithPolicy(task.getId(), rawFailure, 1);
            return;
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.interfacePull(request),
                failureType.code(),
                task.getDataDomain().name(),
                task.getId(),
                null,
                rawFailure
        );
        foundationService.recordInterfaceRiskBackoffDelay(task.getId(), hold, request.getRequestName());
    }

    private void markReportFailureOrRiskBackoff(
            NoonPullTaskRecord task,
            NoonReportPullRequest request,
            String exportCode,
            int attempt,
            String rawFailure
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isRiskBackoffFailure(failureType)) {
            if (StringUtils.hasText(exportCode)) {
                foundationService.recordReportExportTransientFailure(task.getId(), exportCode, null, attempt, rawFailure);
            } else {
                foundationService.markFailedWithPolicy(task.getId(), rawFailure, attempt);
            }
            return;
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.report(request),
                failureType.code(),
                task.getDataDomain().name(),
                task.getId(),
                null,
                rawFailure
        );
        foundationService.recordReportRiskBackoffDelay(task.getId(), hold, request.descriptor());
    }

    private boolean isRiskBackoffFailure(NoonPullFailureType failureType) {
        return failureType == NoonPullFailureType.RATE_LIMITED
                || failureType == NoonPullFailureType.CAPTCHA_REQUIRED
                || failureType == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
    }

    private String safeMessage(RuntimeException exception) {
        if (exception == null) {
            return "unknown failure";
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private String valueOrUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }

    private String deriveProjectCode(String storeCode) {
        if (storeCode == null) {
            return null;
        }
        String normalized = storeCode.trim().toUpperCase();
        if (normalized.startsWith("PRJ")) {
            return normalized;
        }
        if (normalized.startsWith("STR")) {
            int dashIndex = normalized.indexOf('-');
            String partnerId = dashIndex > 3 ? normalized.substring(3, dashIndex) : normalized.substring(3);
            return partnerId.isBlank() ? null : "PRJ" + partnerId;
        }
        return null;
    }
}
