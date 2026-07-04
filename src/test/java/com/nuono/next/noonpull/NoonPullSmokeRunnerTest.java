package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingImportRepository;
import com.nuono.next.noonads.NoonAdvertisingImportService;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import com.nuono.next.noonads.NoonAdvertisingReportAdapter;
import com.nuono.next.noonads.NoonAdvertisingReportBatch;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import com.nuono.next.noonads.NoonAdvertisingReportProvider;
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
import com.nuono.next.orderfinance.NoonFinanceTransactionFact;
import com.nuono.next.orderfinance.NoonFinanceTransactionFactWriter;
import com.nuono.next.orderfinance.NoonFinanceTransactionReportAdapter;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonPullSmokeRunnerTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private RecordingProductWriter productWriter;
    private RecordingSalesFactWriter salesWriter;
    private RecordingOrderFactWriter orderWriter;
    private RecordingFinanceTransactionFactWriter financeWriter;
    private RecordingNoonAdvertisingImportRepository advertisingRepository;
    private RecordingSmokeRunRepository smokeRunRepository;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T02:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        productWriter = new RecordingProductWriter();
        salesWriter = new RecordingSalesFactWriter();
        orderWriter = new RecordingOrderFactWriter();
        financeWriter = new RecordingFinanceTransactionFactWriter();
        advertisingRepository = new RecordingNoonAdvertisingImportRepository();
        smokeRunRepository = new RecordingSmokeRunRepository();
    }

    @Test
    void shouldCreateControlledFailureEvidenceWhenRealProviderIsDisabled() {
        NoonPullSmokeRunner runner = runner(false, null, null, null);

        NoonPullSmokeRunResult result = runner.run(command("global pause is the rollback switch"));

        assertEquals("test", result.getTargetEnvironment());
        assertEquals(3, result.getEvidence().size());
        assertTrue(result.isEvidenceGateSatisfied());
        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(3, repository.listTasks().size());
        for (NoonPullSmokeEvidenceView evidence : result.getEvidence()) {
            assertNotNull(evidence.getTaskId());
            assertEquals(NoonPullTaskStatus.FAILED.name(), evidence.getStatus());
            assertEquals("provider_not_configured", evidence.getFailureClassification());
            assertEquals(0, evidence.getRowOrItemCount());
        }
    }

    @Test
    void shouldPersistControlledFailureSmokeEvidenceWithoutSourceBatch() {
        NoonPullSmokeRunner runner = runner(false, null, null, null);

        NoonPullSmokeRunResult result = runner.run(command(
                "global pause cookie=abc password=secret api_key=key authorization: bearer token-abc "
                        + "https://download.noon.test/raw.csv"
        ));

        assertNotNull(result.getSmokeRunId());
        assertEquals(1, smokeRunRepository.savedRuns.size());
        NoonPullSmokeRunRecord saved = smokeRunRepository.savedRuns.get(0);
        assertEquals(result.getSmokeRunId(), saved.getId());
        assertEquals("test", saved.getTargetEnvironment());
        assertEquals(10002L, saved.getOwnerUserId());
        assertEquals("PRJ245027", saved.getProjectCode());
        assertEquals("xingyao", saved.getProjectName());
        assertEquals("STR245027-NAE", saved.getStoreCode());
        assertEquals("AE", saved.getSiteCode());
        assertTrue(saved.isEvidenceGateSatisfied());
        assertFalse(saved.isProductionSchedulingAllowed());
        assertEquals(List.of("PRODUCT", "SALES", "ORDER"), saved.requestedDataDomains());
        assertFalse(saved.getRollbackOrGlobalPauseStrategy().contains("abc"));
        assertFalse(saved.getRollbackOrGlobalPauseStrategy().contains("secret"));
        assertFalse(saved.getRollbackOrGlobalPauseStrategy().contains("token-abc"));
        assertFalse(saved.getRollbackOrGlobalPauseStrategy().contains("https://download.noon.test/raw.csv"));
        assertEquals(3, saved.getEvidence().size());
        for (NoonPullSmokeEvidenceRecord evidence : saved.getEvidence()) {
            assertNotNull(evidence.getTaskId());
            assertNull(evidence.getSourceBatchId());
            assertEquals(NoonPullTaskStatus.FAILED.name(), evidence.getStatus());
            assertEquals("provider_not_configured", evidence.getFailureClassification());
            assertEquals(0, evidence.getRowOrItemCount());
        }
    }

    @Test
    void shouldRunAllThreeDomainsWhenProvidersAreEnabledAndAvailable() {
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider()
        );

        NoonPullSmokeRunResult result = runner.run(command("global pause is the rollback switch"));

        assertTrue(result.isEvidenceGateSatisfied());
        assertTrue(result.isProductionSchedulingAllowed());
        assertEquals(3, result.getEvidence().size());
        assertEquals(1, productWriter.writeCount);
        assertEquals(1, salesWriter.facts.size());
        assertEquals(1, orderWriter.facts.size());
        for (NoonPullSmokeEvidenceView evidence : result.getEvidence()) {
            assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
            assertEquals("ready", evidence.getQualityState());
            assertNotNull(evidence.getSourceBatchId());
        }
    }

    @Test
    void shouldRunOnlyRequestedProductDomainForProductSmoke() {
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider()
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setDataDomains(List.of(NoonPullDataDomain.PRODUCT));

        NoonPullSmokeRunResult result = runner.run(command);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("PRODUCT", result.getEvidence().get(0).getDataDomain());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), result.getEvidence().get(0).getStatus());
        assertEquals("ready", result.getEvidence().get(0).getQualityState());
        assertEquals(1, result.getEvidence().get(0).getRequestCount());
        assertEquals(1, productWriter.writeCount);
        assertEquals(0, salesWriter.facts.size());
        assertEquals(0, orderWriter.facts.size());
        assertEquals(1, repository.listTasks().size());
        assertTrue(result.getMissingRequirements().contains("SALES_SMOKE"));
        assertTrue(result.getMissingRequirements().contains("ORDER_SMOKE"));
    }

    @Test
    void shouldUseBoundedProductSmokeBudgetForLargeStore() {
        RecordingPagedProductProvider provider = new RecordingPagedProductProvider(5);
        NoonPullSmokeRunner runner = runner(true, provider, successSalesProvider(), successOrderProvider());
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setDataDomains(List.of(NoonPullDataDomain.PRODUCT));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertEquals(List.of(1), provider.requestedPages);
        assertEquals(1, result.getEvidence().size());
        assertEquals(NoonPullTaskStatus.PARTIAL.name(), result.getEvidence().get(0).getStatus());
        assertEquals("large_store_backfill_in_progress", result.getEvidence().get(0).getQualityState());
        assertEquals(1, result.getEvidence().get(0).getRowOrItemCount());
        assertEquals(1, result.getEvidence().get(0).getRequestCount());
        assertEquals("page:2", task.getNextResumePosition());
        assertEquals(1, task.getRequestCount());
    }

    @Test
    void shouldRunOnlyRequestedSalesDomainAndExposeReportDigest() {
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider()
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setDataDomains(List.of(NoonPullDataDomain.SALES));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullSmokeEvidenceView evidence = result.getEvidence().get(0);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("SALES", evidence.getDataDomain());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
        assertEquals("ready", evidence.getQualityState());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getLatestFactDate());
        assertEquals(1, evidence.getRowOrItemCount());
        assertEquals(salesCsvDigest(), evidence.getFileDigestSha256());
        assertTrue(evidence.getSourceBatchId().contains(salesCsvDigest().substring(0, 8)));
        assertEquals(0, productWriter.writeCount);
        assertEquals(1, salesWriter.facts.size());
        assertEquals(0, orderWriter.facts.size());
        assertEquals(1, repository.listTasks().size());
        assertTrue(result.getMissingRequirements().contains("PRODUCT_SMOKE"));
        assertTrue(result.getMissingRequirements().contains("ORDER_SMOKE"));
    }

    @Test
    void shouldRunSalesSmokeThroughPageQueryWhenRequested() {
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider(),
                successSalesPageQueryProvider()
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setDataDomains(List.of(NoonPullDataDomain.SALES));
        command.setSalesSource(NoonSalesSmokeSource.PAGE_QUERY);
        command.setSalesDateFrom(LocalDate.of(2026, 5, 1));
        command.setSalesDateTo(LocalDate.of(2026, 5, 22));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullSmokeEvidenceView evidence = result.getEvidence().get(0);
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("SALES", evidence.getDataDomain());
        assertEquals("sales-page-query:2026-05-01..2026-05-22", evidence.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 1), evidence.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 22), evidence.getDateTo());
        assertEquals(2, evidence.getRowOrItemCount());
        assertEquals(1, evidence.getRequestCount());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
        assertEquals("ready", evidence.getQualityState());
        assertEquals(LocalDate.of(2026, 5, 22), evidence.getLatestFactDate());
        assertNull(evidence.getFileDigestSha256());
        assertEquals(0, salesWriter.facts.size());
        assertEquals(NoonPullType.PAGE_QUERY, task.getPullType());
        assertEquals(NoonPullDataDomain.SALES, task.getDataDomain());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, task.getTriggerMode());
    }

    @Test
    void shouldRunOnlyRequestedFinanceTransactionDomainAndExposeReportDigest() {
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider(),
                null,
                successFinanceProvider()
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setStoreCode("STR108065-NSA");
        command.setSiteCode("SA");
        command.setDataDomains(List.of(NoonPullDataDomain.FINANCE_TRANSACTION));
        command.setFinanceDateFrom(LocalDate.of(2026, 5, 21));
        command.setFinanceDateTo(LocalDate.of(2026, 5, 21));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullSmokeEvidenceView evidence = result.getEvidence().get(0);
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("FINANCE_TRANSACTION", evidence.getDataDomain());
        assertEquals("finance-transactions:2026-05-21..2026-05-21", evidence.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getDateTo());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
        assertEquals("ready", evidence.getQualityState());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getLatestFactDate());
        assertEquals(1, evidence.getRowOrItemCount());
        assertEquals(financeCsvDigest(), evidence.getFileDigestSha256());
        assertTrue(evidence.getSourceBatchId().contains(financeCsvDigest().substring(0, 8)));
        assertEquals(1, financeWriter.facts.size());
        assertEquals("PAPERSAYSB359", financeWriter.facts.get(0).getPartnerSku());
        assertEquals(NoonPullDataDomain.FINANCE_TRANSACTION, task.getDataDomain());
        assertEquals(NoonPullType.REPORT, task.getPullType());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, task.getTriggerMode());
    }

    @Test
    void shouldRunOnlyRequestedNoonAdvertisingDomainAndExposeReportDigest() {
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider(),
                null,
                null,
                successAdvertisingProvider(),
                null,
                null,
                null
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setStoreCode("STR69486-NSA");
        command.setSiteCode("SA");
        command.setDataDomains(List.of(NoonPullDataDomain.NOON_ADVERTISING));
        command.setAdvertisingDateFrom(LocalDate.of(2026, 5, 21));
        command.setAdvertisingDateTo(LocalDate.of(2026, 5, 21));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullSmokeEvidenceView evidence = result.getEvidence().get(0);
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("NOON_ADVERTISING", evidence.getDataDomain());
        assertEquals("ads:2026-05-21..2026-05-21", evidence.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getDateTo());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
        assertEquals("ready", evidence.getQualityState());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getLatestFactDate());
        assertEquals(2, evidence.getRowOrItemCount());
        assertEquals(advertisingCsvDigest(), evidence.getFileDigestSha256());
        assertTrue(evidence.getSourceBatchId().contains(advertisingCsvDigest().substring(0, 8)));
        assertEquals(1, advertisingRepository.campaignFacts.size());
        assertEquals(1, advertisingRepository.queryFacts.size());
        assertEquals("notebook", advertisingRepository.queryFacts.get(0).getQueryText());
        assertEquals(NoonPullDataDomain.NOON_ADVERTISING, task.getDataDomain());
        assertEquals(NoonPullType.REPORT, task.getPullType());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, task.getTriggerMode());
    }

    @Test
    void shouldRunOnlyRequestedOfficialWarehouseInventoryDomain() {
        CapturingOfficialWarehouseInventorySyncService inventorySyncService =
                new CapturingOfficialWarehouseInventorySyncService();
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider(),
                null,
                null,
                null,
                inventorySyncService,
                null,
                null
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setStoreCode("STR108065-NSA");
        command.setSiteCode("SA");
        command.setDataDomains(List.of(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullSmokeEvidenceView evidence = result.getEvidence().get(0);
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("OFFICIAL_WAREHOUSE_INVENTORY", evidence.getDataDomain());
        assertEquals("official-warehouse-inventory:2026-05-22", evidence.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 22), evidence.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 22), evidence.getDateTo());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
        assertEquals("ready", evidence.getQualityState());
        assertEquals(3, evidence.getRowOrItemCount());
        assertEquals("official-warehouse-inventory-" + task.getId() + "-INV-SMOKE", evidence.getSourceBatchId());
        assertEquals(NoonPullDataDomain.OFFICIAL_WAREHOUSE_INVENTORY, task.getDataDomain());
        assertEquals(NoonPullType.INTERFACE, task.getPullType());
        assertEquals(NoonPullTriggerMode.MANUAL_REFRESH, task.getTriggerMode());
        assertEquals(10002L, inventorySyncService.access.getBusinessOwnerUserId());
        assertEquals("STR108065-NSA", inventorySyncService.command.storeCode);
        assertEquals("SA", inventorySyncService.command.siteCode);
        assertEquals(1, inventorySyncService.command.maxPages);
    }

    @Test
    void shouldRunOnlyRequestedOfficialWarehouseFbnReceivedDomain() {
        CapturingOfficialWarehouseFbnExportQueryService fbnExportQueryService =
                new CapturingOfficialWarehouseFbnExportQueryService();
        CapturingOfficialWarehouseFbnReceivedImportService fbnReceivedImportService =
                new CapturingOfficialWarehouseFbnReceivedImportService();
        NoonPullSmokeRunner runner = runner(
                true,
                successProductProvider(),
                successSalesProvider(),
                successOrderProvider(),
                null,
                null,
                null,
                null,
                fbnExportQueryService,
                fbnReceivedImportService
        );
        NoonPullSmokeRunCommand command = command("global pause is the rollback switch");
        command.setStoreCode("STR108065-NSA");
        command.setSiteCode("SA");
        command.setDataDomains(List.of(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED));
        command.setOfficialWarehouseFbnDateFrom(LocalDate.of(2026, 5, 21));
        command.setOfficialWarehouseFbnDateTo(LocalDate.of(2026, 5, 21));

        NoonPullSmokeRunResult result = runner.run(command);
        NoonPullSmokeEvidenceView evidence = result.getEvidence().get(0);
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertFalse(result.isProductionSchedulingAllowed());
        assertEquals(1, result.getEvidence().size());
        assertEquals("OFFICIAL_WAREHOUSE_FBN_RECEIVED", evidence.getDataDomain());
        assertEquals("official-warehouse-fbn-received:2026-05-21..2026-05-21", evidence.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 21), evidence.getDateTo());
        assertEquals(NoonPullTaskStatus.SUCCEEDED.name(), evidence.getStatus());
        assertEquals("ready", evidence.getQualityState());
        assertEquals(2, evidence.getRowOrItemCount());
        assertEquals("EXP-FBN-SMOKE", task.getReportExportId());
        assertEquals("READY", task.getReportExportStatus());
        assertEquals("official-warehouse-fbn-received-" + task.getId() + "-IMP-SMOKE", evidence.getSourceBatchId());
        assertEquals(NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED, task.getDataDomain());
        assertEquals(NoonPullType.REPORT, task.getPullType());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, task.getTriggerMode());
        assertEquals("fbn_inbound_fbnreceivedreport", fbnExportQueryService.createCommand.exportCategoryCode);
        assertEquals("2026-05-21", fbnExportQueryService.createCommand.fromDate);
        assertEquals("2026-05-21", fbnExportQueryService.createCommand.toDate);
        assertEquals("EXP-FBN-SMOKE", fbnReceivedImportService.exportCode);
        assertEquals("STR108065-NSA", fbnReceivedImportService.command.storeCode);
        assertEquals("SA", fbnReceivedImportService.command.siteCode);
    }

    private NoonPullSmokeRunner runner(
            boolean providerEnabled,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonOrderReportSmokeProvider orderProvider
    ) {
        return runner(providerEnabled, productProvider, salesProvider, orderProvider, null);
    }

    private NoonPullSmokeRunner runner(
            boolean providerEnabled,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonOrderReportSmokeProvider orderProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider
    ) {
        return runner(providerEnabled, productProvider, salesProvider, orderProvider, salesPageQueryProvider, null);
    }

    private NoonPullSmokeRunner runner(
            boolean providerEnabled,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonOrderReportSmokeProvider orderProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider,
            NoonFinanceTransactionReportProvider financeProvider
    ) {
        return runner(
                providerEnabled,
                productProvider,
                salesProvider,
                orderProvider,
                salesPageQueryProvider,
                financeProvider,
                null,
                null,
                null,
                null
        );
    }

    private NoonPullSmokeRunner runner(
            boolean providerEnabled,
            NoonProductInterfaceSmokeProvider productProvider,
            NoonSalesReportSmokeProvider salesProvider,
            NoonOrderReportSmokeProvider orderProvider,
            NoonSalesPageQueryProvider salesPageQueryProvider,
            NoonFinanceTransactionReportProvider financeProvider,
            NoonAdvertisingReportProvider advertisingProvider,
            OfficialWarehouseInventorySyncService officialWarehouseInventorySyncService,
            OfficialWarehouseFbnExportQueryService officialWarehouseFbnExportQueryService,
            OfficialWarehouseFbnReceivedReportImportService officialWarehouseFbnReceivedReportImportService
    ) {
        NoonProductListInitializationService productService = new NoonProductListInitializationService(
                foundationService,
                new NoonInterfacePuller(foundationService),
                new NoonProductListPullAdapter(productWriter)
        );
        NoonInterfacePuller interfacePuller = new NoonInterfacePuller(foundationService);
        NoonSalesReportPullService salesService = new NoonSalesReportPullService(
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonSalesReportAdapter(salesWriter)
        );
        NoonSalesPageQueryPullService salesPageQueryService = new NoonSalesPageQueryPullService(
                foundationService,
                interfacePuller
        );
        NoonOrderReportPullService orderService = new NoonOrderReportPullService(
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonOrderReportAdapter(orderWriter, Clock.fixed(Instant.parse("2026-05-22T02:00:00Z"), ZoneOffset.UTC))
        );
        return new NoonPullSmokeRunner(
                foundationService,
                productService,
                salesService,
                salesPageQueryService,
                orderService,
                new NoonReportPuller(foundationService),
                new NoonFinanceTransactionReportAdapter(financeWriter),
                new NoonAdvertisingReportAdapter(new NoonAdvertisingImportService(advertisingRepository)),
                productProvider,
                salesProvider,
                salesPageQueryProvider,
                orderProvider,
                financeProvider,
                advertisingProvider,
                officialWarehouseInventorySyncService,
                officialWarehouseFbnExportQueryService,
                officialWarehouseFbnReceivedReportImportService,
                providerEnabled,
                Clock.fixed(Instant.parse("2026-05-22T02:00:00Z"), ZoneOffset.UTC),
                smokeRunRepository
        );
    }

    private NoonPullSmokeRunCommand command(String rollbackStrategy) {
        NoonPullSmokeRunCommand command = new NoonPullSmokeRunCommand();
        command.setTargetEnvironment("test");
        command.setOwnerUserId(10002L);
        command.setProjectCode("PRJ245027");
        command.setProjectName("xingyao");
        command.setStoreCode("STR245027-NAE");
        command.setSiteCode("AE");
        command.setSalesDate(LocalDate.of(2026, 5, 21));
        command.setOrderDateFrom(LocalDate.of(2026, 5, 21));
        command.setOrderDateTo(LocalDate.of(2026, 5, 21));
        command.setRollbackOrGlobalPauseStrategy(rollbackStrategy);
        return command;
    }

    private NoonProductInterfaceSmokeProvider successProductProvider() {
        return (request, pageNumber) -> NoonInterfacePullPage.builder()
                .items(List.of(Map.of("sku_parent", "Z-PRODUCT-1", "sku", "Z-PRODUCT-1-1", "title", "Smoke Product")))
                .pageNumber(pageNumber)
                .totalItems(1)
                .hasNextPage(false)
                .requestCount(1)
                .build();
    }

    private static final class RecordingPagedProductProvider implements NoonProductInterfaceSmokeProvider {
        private final int totalPages;
        private final List<Integer> requestedPages = new java.util.ArrayList<>();

        private RecordingPagedProductProvider(int totalPages) {
            this.totalPages = totalPages;
        }

        @Override
        public NoonInterfacePullPage fetchPage(NoonInterfacePullRequest request, int pageNumber) {
            requestedPages.add(pageNumber);
            return NoonInterfacePullPage.builder()
                    .items(List.of(Map.of(
                            "sku_parent", "Z-PRODUCT-" + pageNumber,
                            "sku", "Z-PRODUCT-" + pageNumber + "-1",
                            "title", "Smoke Product " + pageNumber
                    )))
                    .pageNumber(pageNumber)
                    .totalItems(totalPages)
                    .hasNextPage(pageNumber < totalPages)
                    .requestCount(1)
                    .build();
        }
    }

    private NoonSalesReportSmokeProvider successSalesProvider() {
        return reportProvider(salesCsv());
    }

    private NoonSalesPageQueryProvider successSalesPageQueryProvider() {
        return (request, pageNumber) -> NoonInterfacePullPage.builder()
                .items(List.of(
                        Map.of("item_nr", "NAEI50000000001-1", "partner_sku", "PSKU-1"),
                        Map.of("item_nr", "NAEI50000000002-1", "partner_sku", "PSKU-2")
                ))
                .pageNumber(pageNumber)
                .totalItems(2)
                .hasNextPage(false)
                .requestCount(1)
                .build();
    }

    private NoonOrderReportSmokeProvider successOrderProvider() {
        String csv = "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,Z-PRODUCT-1-1,"
                + "Processing,65.8,65.8,SAR,papersay,stationery,Fulfilled by Noon (FBN),"
                + "2026-05-21 23:29:16,,\n";
        return new NoonOrderReportSmokeProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return "EXP-ORDER-SMOKE";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/orders-smoke.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private NoonFinanceTransactionReportProvider successFinanceProvider() {
        String csv = financeCsv();
        return new NoonFinanceTransactionReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                assertEquals(NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE, request.getReportType());
                return "EXP-FINANCE-SMOKE";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/finance-smoke.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private NoonAdvertisingReportProvider successAdvertisingProvider() {
        String csv = advertisingCsv();
        return new NoonAdvertisingReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                assertEquals(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE, request.getReportType());
                return "EXP-ADS-SMOKE";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/ads-smoke.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private NoonSalesReportSmokeProvider reportProvider(String csv) {
        return new NoonSalesReportSmokeProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return "EXP-SMOKE";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/smoke.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private String salesCsv() {
        return "date,sku_parent,units_sold,sales_amount,currency\n"
                + "2026-05-21,Z-PRODUCT-1,2,39.90,AED\n";
    }

    private String salesCsvDigest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(salesCsv().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String financeCsv() {
        return "Contract,Contract Title,Reference Nr,Order Nr,Item Nr,Order Date,Transaction Date,Title,SKUs,Partner SKUs,Transaction Type,Currency,Net Proceeds,Referral Fee including VAT,Fullfilment & Logistics Fees including VAT,Shipping Credits including VAT,Other Order Fees including VAT,Order Subsidies including VAT,Non-Order Fees including VAT,Non-Order Subsidies including VAT,Others including VAT,Total\n"
                + "NOON-SA,Saudi Contract,REF-1,ORDER-1,ITEM-1,2026-05-21,2026-05-21,Paper,SKU-1,PAPERSAYSB359,Order,SAR,12.34,-1.23,-2.34,0,0,0,0,0,0,8.77\n";
    }

    private String financeCsvDigest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(financeCsv().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String advertisingCsv() {
        return "row_type,project_code,campaign_code,campaign_name,ad_sku_code,partner_sku,query_text,"
                + "query_kind,campaign_status,views,clicks,orders_count,spend_amount,ad_revenue\n"
                + "campaign,PRJ69486,C_ADS_001,Summer core exact,ZADS001-1,PAPER001,,,active,"
                + "1200,80,12,150.59,450.10\n"
                + "query,PRJ69486,C_ADS_001,Summer core exact,ZADS001-1,PAPER001,notebook,exact,,"
                + "90,10,2,21.57,82.30\n";
    }

    private String advertisingCsvDigest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(advertisingCsv().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class RecordingProductWriter implements NoonProductProjectionWriter {
        private int writeCount;

        @Override
        public void write(NoonProductProjectionWriteCommand command) {
            writeCount++;
        }
    }

    private static final class RecordingSalesFactWriter implements NoonSalesFactWriter {
        private final Map<String, NoonSalesDailyFact> facts = new LinkedHashMap<>();

        @Override
        public void upsert(NoonSalesDailyFact fact) {
            facts.put(fact.key(), fact);
        }
    }

    private static final class RecordingOrderFactWriter implements NoonOrderFactWriter {
        private final Map<String, NoonOrderLineFact> facts = new LinkedHashMap<>();

        @Override
        public void upsertLine(NoonOrderLineFact fact) {
            facts.put(fact.naturalKey(), fact);
        }
    }

    private static final class RecordingFinanceTransactionFactWriter implements NoonFinanceTransactionFactWriter {
        private final List<NoonFinanceTransactionFact> facts = new java.util.ArrayList<>();

        @Override
        public void upsert(NoonFinanceTransactionFact fact) {
            facts.add(fact);
        }
    }

    private static final class RecordingNoonAdvertisingImportRepository implements NoonAdvertisingImportRepository {
        private final List<NoonAdvertisingReportBatch> insertedBatches = new java.util.ArrayList<>();
        private final List<NoonAdvertisingCampaignFact> campaignFacts = new java.util.ArrayList<>();
        private final List<NoonAdvertisingQueryFact> queryFacts = new java.util.ArrayList<>();

        @Override
        public Long nextReportBatchId() {
            return 200001L + insertedBatches.size();
        }

        @Override
        public Long nextCampaignFactId() {
            return 210001L + campaignFacts.size();
        }

        @Override
        public Long nextQueryFactId() {
            return 220001L + queryFacts.size();
        }

        @Override
        public Long findReportBatchId(NoonAdvertisingReportBatch batch) {
            return null;
        }

        @Override
        public void insertReportBatch(NoonAdvertisingReportBatch batch) {
            insertedBatches.add(batch);
        }

        @Override
        public void upsertCampaignFact(NoonAdvertisingCampaignFact fact) {
            campaignFacts.add(fact);
        }

        @Override
        public void upsertQueryFact(NoonAdvertisingQueryFact fact) {
            queryFacts.add(fact);
        }
    }

    private static final class CapturingOfficialWarehouseInventorySyncService
            extends OfficialWarehouseInventorySyncService {
        private BusinessAccessContext access;
        private InventorySyncCommand command;

        private CapturingOfficialWarehouseInventorySyncService() {
            super(null, null, null);
        }

        @Override
        public InventorySyncResultView sync(BusinessAccessContext access, InventorySyncCommand command) {
            this.access = access;
            this.command = command;
            InventorySyncResultView result = new InventorySyncResultView();
            result.syncBatchId = "INV-SMOKE";
            result.storeCode = command.storeCode;
            result.siteCode = command.siteCode;
            result.pageCount = 1;
            result.fetchedRows = 3;
            result.insertedRows = 3;
            result.sourceType = "FBN_INVENTORY_API";
            return result;
        }
    }

    private static final class CapturingOfficialWarehouseFbnExportQueryService
            extends OfficialWarehouseFbnExportQueryService {
        private FbnExportCreateCommand createCommand;
        private String statusExportCode;

        private CapturingOfficialWarehouseFbnExportQueryService() {
            super(null);
        }

        @Override
        public FbnExportCreateView createExport(BusinessAccessContext access, FbnExportCreateCommand command) {
            this.createCommand = command;
            FbnExportCreateView view = new FbnExportCreateView();
            view.storeCode = command.storeCode;
            view.siteCode = command.siteCode;
            view.exportCode = "EXP-FBN-SMOKE";
            view.status = "CREATED";
            view.reportType = command.exportCategoryCode;
            view.fromDate = command.fromDate;
            view.toDate = command.toDate;
            view.sourceType = "FBN_REPORT_EXPORT_API";
            return view;
        }

        @Override
        public FbnExportStatusView exportStatus(
                BusinessAccessContext access,
                String storeCode,
                String siteCode,
                String exportCode,
                Boolean log
        ) {
            this.statusExportCode = exportCode;
            FbnExportStatusView view = new FbnExportStatusView();
            view.storeCode = storeCode;
            view.siteCode = siteCode;
            view.exportCode = exportCode;
            view.status = "COMPLETE";
            view.fileName = "fbn-received-smoke.csv";
            view.downloadUrl = "https://download.test/fbn-received-smoke.csv";
            view.totalRows = 2;
            view.sourceType = "FBN_REPORT_EXPORT_API";
            return view;
        }
    }

    private static final class CapturingOfficialWarehouseFbnReceivedImportService
            extends OfficialWarehouseFbnReceivedReportImportService {
        private String exportCode;
        private FbnReceivedImportCommand command;

        private CapturingOfficialWarehouseFbnReceivedImportService() {
            super(null, null, null, null);
        }

        @Override
        public FbnReceivedImportResultView importByExportCode(
                BusinessAccessContext access,
                String exportCode,
                FbnReceivedImportCommand command
        ) {
            this.exportCode = exportCode;
            this.command = command;
            FbnReceivedImportResultView view = new FbnReceivedImportResultView();
            view.importId = "IMP-SMOKE";
            view.storeCode = command.storeCode;
            view.siteCode = command.siteCode;
            view.exportCode = exportCode;
            view.reportType = OfficialWarehouseFbnReceivedReportImportService.REPORT_TYPE;
            view.status = "IMPORTED";
            view.totalRows = 2;
            view.validRows = 2;
            view.warningRows = 0;
            view.errorRows = 0;
            view.insertedReceiptLines = 2;
            view.sourceType = "FBN_REPORT_EXPORT_API";
            return view;
        }
    }

    private static final class RecordingSmokeRunRepository implements NoonPullSmokeRunRepository {
        private long nextId = 140000L;
        private long nextEvidenceId = 141000L;
        private final List<NoonPullSmokeRunRecord> savedRuns = new java.util.ArrayList<>();

        @Override
        public NoonPullSmokeRunRecord save(NoonPullSmokeRunRecord run) {
            NoonPullSmokeRunRecord copy = run.copy();
            copy.setId(nextId++);
            List<NoonPullSmokeEvidenceRecord> evidence = new java.util.ArrayList<>();
            for (NoonPullSmokeEvidenceRecord item : copy.getEvidence()) {
                NoonPullSmokeEvidenceRecord evidenceCopy = item.copy();
                evidenceCopy.setId(nextEvidenceId++);
                evidenceCopy.setRunId(copy.getId());
                evidence.add(evidenceCopy);
            }
            copy.setEvidence(evidence);
            savedRuns.add(copy.copy());
            return copy;
        }

        @Override
        public List<NoonPullSmokeRunRecord> listRecent(int limit) {
            return savedRuns.stream()
                    .map(NoonPullSmokeRunRecord::copy)
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }
}
