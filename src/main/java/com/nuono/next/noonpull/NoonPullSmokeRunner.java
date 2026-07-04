package com.nuono.next.noonpull;

import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import com.nuono.next.noonads.NoonAdvertisingReportProvider;
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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullSmokeRunner {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String PRODUCT_TARGET = "catalog:list";
    private static final int OFFICIAL_WAREHOUSE_INVENTORY_SMOKE_MAX_PAGES = 1;
    private static final String FBN_RECEIVED_REPORT_TYPE = "fbn_inbound_fbnreceivedreport";
    private static final NoonPullRequestBudget PRODUCT_SMOKE_BUDGET = NoonPullRequestBudget.builder()
            .maxPagesPerRun(1)
            .maxRequestsPerRun(1)
            .build();

    private final NoonPullFoundationService foundationService;
    private final NoonProductListInitializationService productService;
    private final NoonSalesReportPullService salesService;
    private final NoonSalesPageQueryPullService salesPageQueryService;
    private final NoonOrderReportPullService orderService;
    private final NoonReportPuller reportPuller;
    private final NoonFinanceTransactionReportAdapter financeReportAdapter;
    private final NoonAdvertisingReportAdapter advertisingReportAdapter;
    private final OfficialWarehouseInventorySyncService officialWarehouseInventorySyncService;
    private final OfficialWarehouseFbnExportQueryService officialWarehouseFbnExportQueryService;
    private final OfficialWarehouseFbnReceivedReportImportService officialWarehouseFbnReceivedReportImportService;
    private final NoonProductInterfaceSmokeProvider productProvider;
    private final NoonSalesReportSmokeProvider salesProvider;
    private final NoonSalesPageQueryProvider salesPageQueryProvider;
    private final NoonOrderReportSmokeProvider orderProvider;
    private final NoonFinanceTransactionReportProvider financeProvider;
    private final NoonAdvertisingReportProvider advertisingProvider;
    private final NoonPullSmokeRunRepository smokeRunRepository;
    private final boolean realProviderEnabled;
    private final Clock clock;

    @Autowired
    public NoonPullSmokeRunner(
            NoonPullFoundationService foundationService,
            NoonProductListInitializationService productService,
            NoonSalesReportPullService salesService,
            NoonSalesPageQueryPullService salesPageQueryService,
            NoonOrderReportPullService orderService,
            NoonReportPuller reportPuller,
            ObjectProvider<NoonFinanceTransactionReportAdapter> financeReportAdapter,
            ObjectProvider<NoonAdvertisingReportAdapter> advertisingReportAdapter,
            ObjectProvider<NoonProductInterfaceSmokeProvider> productProvider,
            ObjectProvider<NoonSalesReportSmokeProvider> salesProvider,
            ObjectProvider<NoonSalesPageQueryProvider> salesPageQueryProvider,
            ObjectProvider<NoonOrderReportSmokeProvider> orderProvider,
            ObjectProvider<NoonFinanceTransactionReportProvider> financeProvider,
            ObjectProvider<NoonAdvertisingReportProvider> advertisingProvider,
            ObjectProvider<OfficialWarehouseInventorySyncService> officialWarehouseInventorySyncService,
            ObjectProvider<OfficialWarehouseFbnExportQueryService> officialWarehouseFbnExportQueryService,
            ObjectProvider<OfficialWarehouseFbnReceivedReportImportService> officialWarehouseFbnReceivedReportImportService,
            ObjectProvider<NoonPullSmokeRunRepository> smokeRunRepository,
            @Value("${nuono.noon.pull.real-provider.enabled:false}") boolean realProviderEnabled
    ) {
        this(
                foundationService,
                productService,
                salesService,
                salesPageQueryService,
                orderService,
                reportPuller,
                financeReportAdapter == null ? null : financeReportAdapter.getIfAvailable(),
                advertisingReportAdapter == null ? null : advertisingReportAdapter.getIfAvailable(),
                productProvider == null ? null : productProvider.getIfAvailable(),
                salesProvider == null ? null : salesProvider.getIfAvailable(),
                salesPageQueryProvider == null ? null : salesPageQueryProvider.getIfAvailable(),
                orderProvider == null ? null : orderProvider.getIfAvailable(),
                financeProvider == null ? null : financeProvider.getIfAvailable(),
                advertisingProvider == null ? null : advertisingProvider.getIfAvailable(),
                officialWarehouseInventorySyncService == null ? null : officialWarehouseInventorySyncService.getIfAvailable(),
                officialWarehouseFbnExportQueryService == null ? null : officialWarehouseFbnExportQueryService.getIfAvailable(),
                officialWarehouseFbnReceivedReportImportService == null
                        ? null
                        : officialWarehouseFbnReceivedReportImportService.getIfAvailable(),
                realProviderEnabled,
                Clock.system(SHANGHAI),
                smokeRunRepository == null ? null : smokeRunRepository.getIfAvailable()
        );
    }

    NoonPullSmokeRunner(
            NoonPullFoundationService foundationService,
            NoonProductListInitializationService productService,
            NoonSalesReportPullService salesService,
            NoonSalesPageQueryPullService salesPageQueryService,
            NoonOrderReportPullService orderService,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider,
            NoonOrderReportSmokeProvider orderProvider,
            boolean realProviderEnabled
    ) {
        this(
                foundationService,
                productService,
                salesService,
                salesPageQueryService,
                orderService,
                new NoonReportPuller(foundationService),
                null,
                null,
                productProvider,
                salesProvider,
                salesPageQueryProvider,
                orderProvider,
                null,
                null,
                null,
                null,
                null,
                realProviderEnabled,
                Clock.system(SHANGHAI),
                null
        );
    }

    NoonPullSmokeRunner(
            NoonPullFoundationService foundationService,
            NoonProductListInitializationService productService,
            NoonSalesReportPullService salesService,
            NoonSalesPageQueryPullService salesPageQueryService,
            NoonOrderReportPullService orderService,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider,
            NoonOrderReportSmokeProvider orderProvider,
            boolean realProviderEnabled,
            Clock clock
    ) {
        this(
                foundationService,
                productService,
                salesService,
                salesPageQueryService,
                orderService,
                new NoonReportPuller(foundationService),
                null,
                null,
                productProvider,
                salesProvider,
                salesPageQueryProvider,
                orderProvider,
                null,
                null,
                null,
                null,
                null,
                realProviderEnabled,
                clock,
                null
        );
    }

    NoonPullSmokeRunner(
            NoonPullFoundationService foundationService,
            NoonProductListInitializationService productService,
            NoonSalesReportPullService salesService,
            NoonSalesPageQueryPullService salesPageQueryService,
            NoonOrderReportPullService orderService,
            NoonReportPuller reportPuller,
            NoonFinanceTransactionReportAdapter financeReportAdapter,
            NoonAdvertisingReportAdapter advertisingReportAdapter,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider,
            NoonOrderReportSmokeProvider orderProvider,
            NoonFinanceTransactionReportProvider financeProvider,
            NoonAdvertisingReportProvider advertisingProvider,
            OfficialWarehouseInventorySyncService officialWarehouseInventorySyncService,
            OfficialWarehouseFbnExportQueryService officialWarehouseFbnExportQueryService,
            OfficialWarehouseFbnReceivedReportImportService officialWarehouseFbnReceivedReportImportService,
            boolean realProviderEnabled,
            Clock clock,
            NoonPullSmokeRunRepository smokeRunRepository
    ) {
        this.foundationService = foundationService;
        this.productService = productService;
        this.salesService = salesService;
        this.salesPageQueryService = salesPageQueryService;
        this.orderService = orderService;
        this.reportPuller = reportPuller == null ? new NoonReportPuller(foundationService) : reportPuller;
        this.financeReportAdapter = financeReportAdapter;
        this.advertisingReportAdapter = advertisingReportAdapter;
        this.officialWarehouseInventorySyncService = officialWarehouseInventorySyncService;
        this.officialWarehouseFbnExportQueryService = officialWarehouseFbnExportQueryService;
        this.officialWarehouseFbnReceivedReportImportService = officialWarehouseFbnReceivedReportImportService;
        this.productProvider = productProvider;
        this.salesProvider = salesProvider;
        this.salesPageQueryProvider = salesPageQueryProvider;
        this.orderProvider = orderProvider;
        this.financeProvider = financeProvider;
        this.advertisingProvider = advertisingProvider;
        this.smokeRunRepository = smokeRunRepository;
        this.realProviderEnabled = realProviderEnabled;
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
    }

    public NoonPullSmokeRunResult run(NoonPullSmokeRunCommand command) {
        requireCommand(command);
        LocalDate salesDate = command.getSalesDate() == null ? LocalDate.now(clock).minusDays(1) : command.getSalesDate();
        LocalDate salesDateFrom = command.getSalesDateFrom() == null ? salesDate : command.getSalesDateFrom();
        LocalDate salesDateTo = command.getSalesDateTo() == null ? salesDateFrom : command.getSalesDateTo();
        LocalDate orderDateFrom = command.getOrderDateFrom() == null ? salesDate : command.getOrderDateFrom();
        LocalDate orderDateTo = command.getOrderDateTo() == null ? orderDateFrom : command.getOrderDateTo();
        LocalDate financeDateFrom = command.getFinanceDateFrom() == null ? salesDate : command.getFinanceDateFrom();
        LocalDate financeDateTo = command.getFinanceDateTo() == null ? financeDateFrom : command.getFinanceDateTo();
        LocalDate advertisingDateFrom = command.getAdvertisingDateFrom() == null
                ? salesDate
                : command.getAdvertisingDateFrom();
        LocalDate advertisingDateTo = command.getAdvertisingDateTo() == null
                ? advertisingDateFrom
                : command.getAdvertisingDateTo();
        LocalDate officialWarehouseFbnDateFrom = command.getOfficialWarehouseFbnDateFrom() == null
                ? salesDate
                : command.getOfficialWarehouseFbnDateFrom();
        LocalDate officialWarehouseFbnDateTo = command.getOfficialWarehouseFbnDateTo() == null
                ? officialWarehouseFbnDateFrom
                : command.getOfficialWarehouseFbnDateTo();

        List<NoonPullSmokeEvidenceView> evidence = new ArrayList<>();
        List<NoonPullDataDomain> requestedDomains = command.requestedDataDomains();
        if (requestedDomains.contains(NoonPullDataDomain.PRODUCT)) {
            evidence.add(runProductSmoke(command));
        }
        if (requestedDomains.contains(NoonPullDataDomain.SALES)) {
            evidence.add(runSalesSmoke(command, salesDate, salesDateFrom, salesDateTo));
        }
        if (requestedDomains.contains(NoonPullDataDomain.ORDER)) {
            evidence.add(runOrderSmoke(command, orderDateFrom, orderDateTo));
        }
        if (requestedDomains.contains(NoonPullDataDomain.FINANCE_TRANSACTION)) {
            evidence.add(runFinanceTransactionSmoke(command, financeDateFrom, financeDateTo));
        }
        if (requestedDomains.contains(NoonPullDataDomain.NOON_ADVERTISING)) {
            evidence.add(runAdvertisingSmoke(command, advertisingDateFrom, advertisingDateTo));
        }
        if (requestedDomains.contains(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY)) {
            evidence.add(runOfficialWarehouseInventorySmoke(command));
        }
        if (requestedDomains.contains(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED)) {
            evidence.add(runOfficialWarehouseFbnReceivedSmoke(
                    command,
                    officialWarehouseFbnDateFrom,
                    officialWarehouseFbnDateTo
            ));
        }

        NoonPullSmokeGate gate = new NoonPullSmokeGate();
        for (NoonPullSmokeEvidenceView view : evidence) {
            gate.record(toEvidence(command, view));
        }
        if (StringUtils.hasText(command.getRollbackOrGlobalPauseStrategy())) {
            gate.confirmRollbackOrGlobalPause(command.getTargetEnvironment(), command.getRollbackOrGlobalPauseStrategy());
        }
        NoonPullSmokeGateResult gateResult = gate.evaluate(command.getTargetEnvironment());

        NoonPullSmokeRunResult result = new NoonPullSmokeRunResult();
        result.setTargetEnvironment(command.getTargetEnvironment());
        result.setOwnerUserId(command.getOwnerUserId());
        result.setStoreCode(command.getStoreCode());
        result.setSiteCode(command.getSiteCode());
        result.setEvidence(evidence);
        result.setMissingRequirements(gateResult.getMissingRequirements());
        result.setEvidenceGateSatisfied(gateResult.isReleaseAllowed());
        result.setProductionSchedulingAllowed(gateResult.isReleaseAllowed()
                && allReady(evidence)
                && command.getSalesSource() == NoonSalesSmokeSource.REPORT);
        persistSmokeRun(command, requestedDomains, result);
        return result;
    }

    private NoonPullSmokeEvidenceView runProductSmoke(NoonPullSmokeRunCommand command) {
        long start = System.nanoTime();
        NoonProductListInitializationResult result = productService.initialize(
                NoonProductListInitializationCommand.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .projectCode(command.getProjectCode())
                        .projectName(command.getProjectName())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .requestBudget(PRODUCT_SMOKE_BUDGET)
                        .requestSummary("controlled admin smoke run")
                        .build(),
                productProvider()
        );
        NoonPullTaskRecord task = latestTask(command, NoonPullDataDomain.PRODUCT);
        return evidenceView(
                NoonPullDataDomain.PRODUCT,
                PRODUCT_TARGET,
                null,
                null,
                result.getAcceptedProductCount(),
                task,
                elapsedMillis(start)
        );
    }

    private NoonPullSmokeEvidenceView runSalesSmoke(
            NoonPullSmokeRunCommand command,
            LocalDate salesDate,
            LocalDate salesDateFrom,
            LocalDate salesDateTo
    ) {
        if (command.getSalesSource() == NoonSalesSmokeSource.PAGE_QUERY) {
            return runSalesPageQuerySmoke(command, salesDateFrom, salesDateTo);
        }
        return runSalesReportSmoke(command, salesDate);
    }

    private NoonPullSmokeEvidenceView runSalesReportSmoke(NoonPullSmokeRunCommand command, LocalDate salesDate) {
        long start = System.nanoTime();
        NoonReportPullResult result = salesService.pullLatestDay(
                NoonSalesReportPullCommand.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .date(salesDate)
                        .build(),
                salesProvider()
        );
        NoonPullTaskRecord task = latestTask(command, NoonPullDataDomain.SALES);
        NoonPullSmokeEvidenceView view = evidenceView(
                NoonPullDataDomain.SALES,
                "sales:" + salesDate,
                salesDate,
                salesDate,
                result.getImportedCount(),
                task,
                elapsedMillis(start)
        );
        if (NoonPullTaskStatus.SUCCEEDED.name().equals(view.getStatus())) {
            view.setLatestFactDate(salesDate);
        }
        view.setFileDigestSha256(result.getFileDigestSha256());
        return view;
    }

    private NoonPullSmokeEvidenceView runSalesPageQuerySmoke(
            NoonPullSmokeRunCommand command,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        long start = System.nanoTime();
        NoonInterfacePullResult result = salesPageQueryService.pullWindow(
                NoonSalesPageQueryPullCommand.builder()
                        .ownerUserId(command.getOwnerUserId())
                        .storeCode(command.getStoreCode())
                        .siteCode(command.getSiteCode())
                        .dateFrom(dateFrom)
                        .dateTo(dateTo)
                        .requestSummary("controlled admin smoke run")
                        .build(),
                salesPageQueryProvider()
        );
        NoonPullTaskRecord task = latestTask(command, NoonPullDataDomain.SALES);
        NoonPullSmokeEvidenceView view = evidenceView(
                NoonPullDataDomain.SALES,
                "sales-page-query:" + dateFrom + ".." + dateTo,
                dateFrom,
                dateTo,
                result.getItems().size(),
                task,
                elapsedMillis(start)
        );
        if (NoonPullTaskStatus.SUCCEEDED.name().equals(view.getStatus())) {
            view.setLatestFactDate(dateTo);
        }
        return view;
    }

    private NoonPullSmokeEvidenceView runOrderSmoke(
            NoonPullSmokeRunCommand command,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        long start = System.nanoTime();
        NoonReportPullResult result = orderService.pullWindow(
                new NoonOrderReportPullCommand(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSiteCode(),
                        dateFrom,
                        dateTo,
                        NoonPullTriggerMode.MANUAL_BACKFILL
                ),
                orderProvider()
        );
        NoonPullTaskRecord task = latestTask(command, NoonPullDataDomain.ORDER);
        NoonPullSmokeEvidenceView view = evidenceView(
                NoonPullDataDomain.ORDER,
                "orders:" + dateFrom + ".." + dateTo,
                dateFrom,
                dateTo,
                result.getImportedCount(),
                task,
                elapsedMillis(start)
        );
        if (NoonPullTaskStatus.SUCCEEDED.name().equals(view.getStatus())) {
            view.setLatestFactDate(dateTo);
        }
        view.setFileDigestSha256(result.getFileDigestSha256());
        return view;
    }

    private NoonPullSmokeEvidenceView runFinanceTransactionSmoke(
            NoonPullSmokeRunCommand command,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        long start = System.nanoTime();
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .maxRequestsPerRun(1)
                .build());
        String targetIdentity = "finance-transactions:" + dateFrom + ".." + dateTo;
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .targetIdentity(targetIdentity)
                .targetDateFrom(dateFrom)
                .targetDateTo(dateTo)
                .build()).orElse(null);
        NoonReportPullResult result = null;
        if (task != null && financeReportAdapter != null) {
            result = reportPuller.execute(
                    task.getId(),
                    NoonReportPullRequest.builder()
                            .ownerUserId(command.getOwnerUserId())
                            .storeCode(command.getStoreCode())
                            .siteCode(command.getSiteCode())
                            .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                            .reportType(NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE)
                            .dateFrom(dateFrom)
                            .dateTo(dateTo)
                            .maxPollAttempts(18)
                            .build(),
                    financeProvider(),
                    financeReportAdapter::process
            );
        } else if (task != null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "handler not configured: finance transaction report adapter is disabled",
                    1
            );
        }
        NoonPullTaskRecord latestTask = latestTask(command, NoonPullDataDomain.FINANCE_TRANSACTION);
        NoonPullSmokeEvidenceView view = evidenceView(
                NoonPullDataDomain.FINANCE_TRANSACTION,
                targetIdentity,
                dateFrom,
                dateTo,
                result == null ? 0 : result.getImportedCount(),
                latestTask,
                elapsedMillis(start)
        );
        if (NoonPullTaskStatus.SUCCEEDED.name().equals(view.getStatus())) {
            view.setLatestFactDate(dateTo);
        }
        if (result != null) {
            view.setFileDigestSha256(result.getFileDigestSha256());
        }
        return view;
    }

    private NoonPullSmokeEvidenceView runAdvertisingSmoke(
            NoonPullSmokeRunCommand command,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        long start = System.nanoTime();
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .maxRequestsPerRun(1)
                .build());
        String targetIdentity = "ads:" + dateFrom + ".." + dateTo;
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .targetIdentity(targetIdentity)
                .targetDateFrom(dateFrom)
                .targetDateTo(dateTo)
                .build()).orElse(null);
        NoonReportPullResult result = null;
        if (task != null && advertisingReportAdapter != null) {
            result = reportPuller.execute(
                    task.getId(),
                    NoonReportPullRequest.builder()
                            .ownerUserId(command.getOwnerUserId())
                            .storeCode(command.getStoreCode())
                            .siteCode(command.getSiteCode())
                            .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                            .reportType(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE)
                            .dateFrom(dateFrom)
                            .dateTo(dateTo)
                            .maxPollAttempts(18)
                            .build(),
                    advertisingProvider(),
                    advertisingReportAdapter::process
            );
        } else if (task != null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "handler not configured: noon advertising report adapter is disabled",
                    1
            );
        }
        NoonPullTaskRecord latestTask = latestTask(command, NoonPullDataDomain.NOON_ADVERTISING);
        NoonPullSmokeEvidenceView view = evidenceView(
                NoonPullDataDomain.NOON_ADVERTISING,
                targetIdentity,
                dateFrom,
                dateTo,
                result == null ? 0 : result.getImportedCount(),
                latestTask,
                elapsedMillis(start)
        );
        if (NoonPullTaskStatus.SUCCEEDED.name().equals(view.getStatus())) {
            view.setLatestFactDate(dateTo);
        }
        if (result != null) {
            view.setFileDigestSha256(result.getFileDigestSha256());
        }
        return view;
    }

    private NoonPullSmokeEvidenceView runOfficialWarehouseInventorySmoke(NoonPullSmokeRunCommand command) {
        long start = System.nanoTime();
        LocalDate targetDate = LocalDate.now(clock);
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .maxRequestsPerRun(1)
                .build());
        String targetIdentity = "official-warehouse-inventory:" + targetDate;
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .targetIdentity(targetIdentity)
                .targetDateFrom(targetDate)
                .targetDateTo(targetDate)
                .build()).orElse(null);
        InventorySyncResultView syncResult = null;
        if (task != null && officialWarehouseInventorySyncService != null) {
            try {
                foundationService.markRunning(task.getId(), "official-warehouse-inventory-smoke");
                InventorySyncCommand inventoryCommand = new InventorySyncCommand();
                inventoryCommand.storeCode = command.getStoreCode();
                inventoryCommand.siteCode = command.getSiteCode();
                inventoryCommand.maxPages = OFFICIAL_WAREHOUSE_INVENTORY_SMOKE_MAX_PAGES;
                syncResult = officialWarehouseInventorySyncService.sync(accessForCommand(command), inventoryCommand);
                String sourceBatchId = "official-warehouse-inventory-" + task.getId()
                        + "-" + valueOrUnknown(syncResult == null ? null : syncResult.syncBatchId);
                foundationService.markSucceeded(
                        task.getId(),
                        sourceBatchId,
                        "official warehouse inventory smoke synced; fetched="
                                + (syncResult == null ? 0 : syncResult.fetchedRows)
                                + "; inserted=" + (syncResult == null ? 0 : syncResult.insertedRows)
                );
            } catch (RuntimeException exception) {
                foundationService.markFailedWithPolicy(task.getId(), safeMessage(exception), 1);
            }
        } else if (task != null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: official warehouse inventory sync smoke service is disabled",
                    1
            );
        }
        NoonPullTaskRecord latestTask = latestTask(command, NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY);
        return evidenceView(
                NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY,
                targetIdentity,
                targetDate,
                targetDate,
                syncResult == null ? 0 : syncResult.insertedRows,
                latestTask,
                elapsedMillis(start)
        );
    }

    private NoonPullSmokeEvidenceView runOfficialWarehouseFbnReceivedSmoke(
            NoonPullSmokeRunCommand command,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        long start = System.nanoTime();
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .maxRequestsPerRun(1)
                .build());
        String targetIdentity = "official-warehouse-fbn-received:" + dateFrom + ".." + dateTo;
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .targetIdentity(targetIdentity)
                .targetDateFrom(dateFrom)
                .targetDateTo(dateTo)
                .build()).orElse(null);
        FbnReceivedImportResultView importResult = null;
        if (task != null
                && officialWarehouseFbnExportQueryService != null
                && officialWarehouseFbnReceivedReportImportService != null) {
            try {
                BusinessAccessContext access = accessForCommand(command);
                foundationService.markRunning(task.getId(), "official-warehouse-fbn-received-smoke");
                FbnExportCreateCommand createCommand = new FbnExportCreateCommand();
                createCommand.storeCode = command.getStoreCode();
                createCommand.siteCode = command.getSiteCode();
                createCommand.exportCategoryCode = FBN_RECEIVED_REPORT_TYPE;
                createCommand.fromDate = dateFrom.toString();
                createCommand.toDate = dateTo.toString();
                FbnExportCreateView createView = officialWarehouseFbnExportQueryService.createExport(access, createCommand);
                String exportCode = createView == null ? null : createView.exportCode;
                if (!StringUtils.hasText(exportCode)) {
                    foundationService.markFailedWithPolicy(task.getId(), "mapping failed: missing FBN export code", 1);
                } else {
                    foundationService.recordReportExportCreated(
                            task.getId(),
                            exportCode,
                            "official warehouse FBN received smoke export created; exportCode=" + exportCode
                    );
                    FbnExportStatusView statusView = officialWarehouseFbnExportQueryService.exportStatus(
                            access,
                            command.getStoreCode(),
                            command.getSiteCode(),
                            exportCode,
                            false
                    );
                    NoonReportExportStatus exportStatus = fbnExportStatus(statusView);
                    foundationService.recordReportExportPollResult(
                            task.getId(),
                            exportCode,
                            exportStatus,
                            1,
                            null,
                            "official warehouse FBN received smoke export; status=" + exportStatus.getStatus()
                    );
                    if (exportStatus.isReady()) {
                        FbnReceivedImportCommand importCommand = new FbnReceivedImportCommand();
                        importCommand.storeCode = command.getStoreCode();
                        importCommand.siteCode = command.getSiteCode();
                        importCommand.logStatus = false;
                        importResult = officialWarehouseFbnReceivedReportImportService.importByExportCode(
                                access,
                                exportCode,
                                importCommand
                        );
                        String sourceBatchId = "official-warehouse-fbn-received-" + task.getId()
                                + "-" + valueOrUnknown(importResult == null ? null : importResult.importId);
                        foundationService.markSucceeded(
                                task.getId(),
                                sourceBatchId,
                                "official warehouse FBN received smoke imported; rows="
                                        + (importResult == null || importResult.insertedReceiptLines == null
                                        ? 0
                                        : importResult.insertedReceiptLines)
                        );
                    } else if (exportStatus.isFailed()) {
                        foundationService.markFailedWithPolicy(
                                task.getId(),
                                "provider unavailable: FBN received smoke export failed " + exportStatus.getMessage(),
                                1
                        );
                    }
                }
            } catch (RuntimeException exception) {
                foundationService.markFailedWithPolicy(task.getId(), safeMessage(exception), 1);
            }
        } else if (task != null) {
            foundationService.markFailedWithPolicy(
                    task.getId(),
                    "provider not configured: official warehouse FBN received smoke service is disabled",
                    1
            );
        }
        NoonPullTaskRecord latestTask = latestTask(command, NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED);
        return evidenceView(
                NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                targetIdentity,
                dateFrom,
                dateTo,
                importResult == null ? 0 : safeInteger(importResult.insertedReceiptLines),
                latestTask,
                elapsedMillis(start)
        );
    }

    private NoonProductInterfaceSmokeProvider productProvider() {
        if (realProviderEnabled && productProvider != null) {
            return productProvider;
        }
        return (request, pageNumber) -> {
            throw new NoonInterfacePullException("provider not configured: real Noon product interface smoke provider is disabled");
        };
    }

    private NoonSalesReportSmokeProvider salesProvider() {
        if (realProviderEnabled && salesProvider != null) {
            return salesProvider;
        }
        return disabledReportProvider("sales report");
    }

    private NoonSalesPageQueryProvider salesPageQueryProvider() {
        if (realProviderEnabled && salesPageQueryProvider != null) {
            return salesPageQueryProvider;
        }
        return (request, pageNumber) -> {
            throw new NoonInterfacePullException("provider not configured: real Noon sales page query provider is disabled");
        };
    }

    private NoonOrderReportSmokeProvider orderProvider() {
        if (realProviderEnabled && orderProvider != null) {
            return orderProvider;
        }
        NoonReportProvider disabled = disabledReportProvider("order report");
        return new NoonOrderReportSmokeProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return disabled.createExport(request);
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return disabled.pollExport(request, exportId);
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return disabled.download(request, downloadUrl);
            }
        };
    }

    private NoonFinanceTransactionReportProvider financeProvider() {
        if (realProviderEnabled && financeProvider != null) {
            return financeProvider;
        }
        NoonReportProvider disabled = disabledReportProvider("finance transaction report");
        return new NoonFinanceTransactionReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return disabled.createExport(request);
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return disabled.pollExport(request, exportId);
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return disabled.download(request, downloadUrl);
            }
        };
    }

    private NoonAdvertisingReportProvider advertisingProvider() {
        if (realProviderEnabled && advertisingProvider != null) {
            return advertisingProvider;
        }
        NoonReportProvider disabled = disabledReportProvider("noon advertising report");
        return new NoonAdvertisingReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return disabled.createExport(request);
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return disabled.pollExport(request, exportId);
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return disabled.download(request, downloadUrl);
            }
        };
    }

    private NoonSalesReportSmokeProvider disabledReportProvider(String label) {
        return new NoonSalesReportSmokeProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                throw new NoonInterfacePullException("provider not configured: real Noon " + label + " smoke provider is disabled");
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.pending();
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return new byte[0];
            }
        };
    }

    private BusinessAccessContext accessForCommand(NoonPullSmokeRunCommand command) {
        return BusinessAccessContext.builder()
                .sessionUserId(command.getOwnerUserId())
                .businessOwnerUserId(command.getOwnerUserId())
                .storeCodes(Set.of(command.getStoreCode()))
                .storeOwnerUserIds(Map.of(command.getStoreCode(), command.getOwnerUserId()))
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

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String valueOrUnknown(String value) {
        return StringUtils.hasText(value) ? value : "UNKNOWN";
    }

    private String safeMessage(RuntimeException exception) {
        if (exception == null) {
            return "unknown failure";
        }
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private NoonPullTaskRecord latestTask(NoonPullSmokeRunCommand command, NoonPullDataDomain domain) {
        return foundationService.listTasks().stream()
                .filter((task) -> command.getOwnerUserId().equals(task.getOwnerUserId()))
                .filter((task) -> domain == task.getDataDomain())
                .filter((task) -> safeEquals(command.getStoreCode(), task.getStoreCode()))
                .filter((task) -> safeEquals(command.getSiteCode(), task.getSiteCode()))
                .max(Comparator.comparing(NoonPullTaskRecord::getId))
                .orElse(null);
    }

    private NoonPullSmokeEvidenceView evidenceView(
            NoonPullDataDomain domain,
            String targetIdentity,
            LocalDate dateFrom,
            LocalDate dateTo,
            int rowOrItemCount,
            NoonPullTaskRecord task,
            long elapsedMillis
    ) {
        NoonPullSmokeEvidenceView view = new NoonPullSmokeEvidenceView();
        view.setDataDomain(domain.name());
        view.setTargetIdentity(targetIdentity);
        view.setDateFrom(dateFrom);
        view.setDateTo(dateTo);
        view.setRowOrItemCount(rowOrItemCount);
        view.setElapsedMillis(elapsedMillis);
        if (task == null) {
            view.setStatus(NoonPullTaskStatus.FAILED.name());
            view.setFailureClassification("mapping_failed");
            return view;
        }
        view.setTaskId(task.getId());
        view.setSourceBatchId(task.getSourceBatchId());
        view.setRequestCount(task.getRequestCount());
        view.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        view.setFailureClassification(task.getFailureType());
        if (rowOrItemCount == 0 && task.getProcessedItemCount() != null) {
            view.setRowOrItemCount(task.getProcessedItemCount());
        }
        if (task.getStatus() == NoonPullTaskStatus.SUCCEEDED) {
            view.setQualityState("ready");
        } else if (StringUtils.hasText(task.getReadinessState())) {
            view.setQualityState(task.getReadinessState());
        }
        return view;
    }

    private NoonPullSmokeEvidence toEvidence(NoonPullSmokeRunCommand command, NoonPullSmokeEvidenceView view) {
        NoonPullDataDomain domain = NoonPullDataDomain.valueOf(view.getDataDomain());
        return NoonPullSmokeEvidence.builder()
                .targetEnvironment(command.getTargetEnvironment())
                .ownerUserId(command.getOwnerUserId())
                .storeCode(command.getStoreCode())
                .siteCode(command.getSiteCode())
                .dataDomain(domain)
                .targetIdentity(view.getTargetIdentity())
                .dateFrom(view.getDateFrom())
                .dateTo(view.getDateTo())
                .rowOrItemCount(view.getRowOrItemCount())
                .taskId(view.getTaskId())
                .sourceBatchId(view.getSourceBatchId())
                .elapsed(Duration.ofMillis(view.getElapsedMillis()))
                .latestFactDate(view.getLatestFactDate())
                .qualityState(view.getQualityState())
                .failureClassification(view.getFailureClassification())
                .build();
    }

    private boolean allReady(List<NoonPullSmokeEvidenceView> evidence) {
        return evidence.stream().allMatch((view) -> "ready".equals(view.getQualityState()));
    }

    private void persistSmokeRun(
            NoonPullSmokeRunCommand command,
            List<NoonPullDataDomain> requestedDomains,
            NoonPullSmokeRunResult result
    ) {
        if (smokeRunRepository == null) {
            return;
        }
        NoonPullSmokeRunRecord record = new NoonPullSmokeRunRecord();
        record.setTargetEnvironment(command.getTargetEnvironment());
        record.setOwnerUserId(command.getOwnerUserId());
        record.setProjectCode(command.getProjectCode());
        record.setProjectName(command.getProjectName());
        record.setStoreCode(command.getStoreCode());
        record.setSiteCode(command.getSiteCode());
        record.setRollbackOrGlobalPauseStrategy(NoonPullSafeText.redact(command.getRollbackOrGlobalPauseStrategy()));
        record.setRequestedDataDomains(requestedDomains.stream().map(Enum::name).collect(Collectors.toList()));
        record.setMissingRequirements(result.getMissingRequirements());
        record.setEvidenceGateSatisfied(result.isEvidenceGateSatisfied());
        record.setProductionSchedulingAllowed(result.isProductionSchedulingAllowed());
        LocalDateTime now = LocalDateTime.now(clock);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        List<NoonPullSmokeEvidenceRecord> evidenceRecords = new ArrayList<>();
        int sequence = 1;
        for (NoonPullSmokeEvidenceView view : result.getEvidence()) {
            NoonPullSmokeEvidenceRecord evidenceRecord = new NoonPullSmokeEvidenceRecord();
            evidenceRecord.setSequenceNo(sequence++);
            evidenceRecord.setDataDomain(view.getDataDomain());
            evidenceRecord.setTargetIdentity(view.getTargetIdentity());
            evidenceRecord.setDateFrom(view.getDateFrom());
            evidenceRecord.setDateTo(view.getDateTo());
            evidenceRecord.setRowOrItemCount(view.getRowOrItemCount());
            evidenceRecord.setTaskId(view.getTaskId());
            evidenceRecord.setSourceBatchId(view.getSourceBatchId());
            evidenceRecord.setFileDigestSha256(view.getFileDigestSha256());
            evidenceRecord.setRequestCount(view.getRequestCount());
            evidenceRecord.setElapsedMillis(view.getElapsedMillis());
            evidenceRecord.setLatestFactDate(view.getLatestFactDate());
            evidenceRecord.setStatus(view.getStatus());
            evidenceRecord.setQualityState(view.getQualityState());
            evidenceRecord.setFailureClassification(view.getFailureClassification());
            evidenceRecord.setCreatedAt(now);
            evidenceRecord.setUpdatedAt(now);
            evidenceRecords.add(evidenceRecord);
        }
        record.setEvidence(evidenceRecords);
        NoonPullSmokeRunRecord saved = smokeRunRepository.save(record);
        result.setSmokeRunId(saved.getId());
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
    }

    private void requireCommand(NoonPullSmokeRunCommand command) {
        if (command == null
                || !StringUtils.hasText(command.getTargetEnvironment())
                || command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())) {
            throw new IllegalArgumentException("Noon pull smoke target environment, owner, store and site are required.");
        }
    }

    private boolean safeEquals(String expected, String actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }
}
