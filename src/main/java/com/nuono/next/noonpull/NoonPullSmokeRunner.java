package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final NoonPullRequestBudget PRODUCT_SMOKE_BUDGET = NoonPullRequestBudget.builder()
            .maxPagesPerRun(1)
            .maxRequestsPerRun(1)
            .build();

    private final NoonPullFoundationService foundationService;
    private final NoonProductListInitializationService productService;
    private final NoonSalesReportPullService salesService;
    private final NoonSalesPageQueryPullService salesPageQueryService;
    private final NoonOrderReportPullService orderService;
    private final NoonProductInterfaceSmokeProvider productProvider;
    private final NoonSalesReportSmokeProvider salesProvider;
    private final NoonSalesPageQueryProvider salesPageQueryProvider;
    private final NoonOrderReportSmokeProvider orderProvider;
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
            ObjectProvider<NoonProductInterfaceSmokeProvider> productProvider,
            ObjectProvider<NoonSalesReportSmokeProvider> salesProvider,
            ObjectProvider<NoonSalesPageQueryProvider> salesPageQueryProvider,
            ObjectProvider<NoonOrderReportSmokeProvider> orderProvider,
            ObjectProvider<NoonPullSmokeRunRepository> smokeRunRepository,
            @Value("${nuono.noon.pull.real-provider.enabled:false}") boolean realProviderEnabled
    ) {
        this(
                foundationService,
                productService,
                salesService,
                salesPageQueryService,
                orderService,
                productProvider == null ? null : productProvider.getIfAvailable(),
                salesProvider == null ? null : salesProvider.getIfAvailable(),
                salesPageQueryProvider == null ? null : salesPageQueryProvider.getIfAvailable(),
                orderProvider == null ? null : orderProvider.getIfAvailable(),
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
                productProvider,
                salesProvider,
                salesPageQueryProvider,
                orderProvider,
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
                productProvider,
                salesProvider,
                salesPageQueryProvider,
                orderProvider,
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
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider,
            NoonOrderReportSmokeProvider orderProvider,
            boolean realProviderEnabled,
            Clock clock,
            NoonPullSmokeRunRepository smokeRunRepository
    ) {
        this.foundationService = foundationService;
        this.productService = productService;
        this.salesService = salesService;
        this.salesPageQueryService = salesPageQueryService;
        this.orderService = orderService;
        this.productProvider = productProvider;
        this.salesProvider = salesProvider;
        this.salesPageQueryProvider = salesPageQueryProvider;
        this.orderProvider = orderProvider;
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
