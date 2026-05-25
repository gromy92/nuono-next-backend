package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonSalesReportPullServiceTest {

    private InMemoryNoonPullRepository repository;
    private InMemorySalesFactWriter writer;
    private NoonSalesReportPullService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T10:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService =
                new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        writer = new InMemorySalesFactWriter();
        service = new NoonSalesReportPullService(
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonSalesReportAdapter(writer)
        );
    }

    @Test
    void shouldImportSalesFactsIdempotentlyForSameDateRange() {
        String csv = "date,sku_parent,units_sold,sales_amount,currency\n"
                + "2026-05-21,Z1,2,39.90,AED\n";

        NoonReportPullResult first = service.pullLatestDay(command(), provider(csv));
        NoonReportPullResult second = service.pullLatestDay(command(), provider(csv));

        assertEquals(NoonPullTaskStatus.SUCCEEDED, first.getStatus());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, second.getStatus());
        assertEquals(1, writer.facts.size());
        assertEquals(2L, writer.facts.get("307|STR245027|AE|2026-05-21|Z1").unitsSold);
    }

    @Test
    void shouldTreatDirectSalesPullAsManualBackfillByDefault() {
        String csv = "date,sku_parent,units_sold,sales_amount,currency\n"
                + "2026-05-21,Z1,2,39.90,AED\n";

        service.pullLatestDay(command(), provider(csv));

        NoonPullTaskRecord task = repository.listTasks().get(0);
        NoonPullPlanRecord plan = repository.selectPlan(task.getPlanId());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, task.getTriggerMode());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, plan.getTriggerMode());
        assertEquals("manual", plan.getScheduleExpression());
    }

    @Test
    void shouldRepresentEmptyReportWithoutFakeZeroFacts() {
        NoonReportPullResult result = service.pullLatestDay(command(), provider("date,sku_parent,units_sold,sales_amount,currency\n"));
        NoonPullTaskRecord task = repository.listTasks().get(0);

        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("empty_report", task.getFailureType());
        assertEquals(0, writer.facts.size());
    }

    @Test
    void shouldPreserveMissingColumnsAndPartialSuccessQualityStates() {
        NoonReportPullResult missing = service.pullLatestDay(command(), provider("date,sku_parent,units_sold\n2026-05-21,Z1,2\n"));
        NoonPullTaskRecord missingTask = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.FAILED, missing.getStatus());
        assertEquals("missing_columns", missingTask.getFailureType());

        repository.tasks.clear();
        String partialCsv = "date,sku_parent,units_sold,sales_amount,currency\n"
                + "2026-05-21,Z1,2,39.90,AED\n"
                + "bad-date,Z2,not-a-number,10,AED\n";
        NoonReportPullResult partial = service.pullLatestDay(command(), provider(partialCsv));
        NoonPullTaskRecord partialTask = repository.listTasks().get(0);

        assertEquals(NoonPullTaskStatus.PARTIAL, partial.getStatus());
        assertEquals("partial_success", partialTask.getFailureType());
        assertEquals(1, writer.facts.size());
    }

    @Test
    void shouldImportRealProductViewsSalesReportColumns() {
        String csv = "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,"
                + "Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,"
                + "Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,"
                + "ASP_shipped_Percentage\n"
                + "2026-05-21,PSKU-1,MP-1,CFG-1,SKU-1,Family,Type,Subtype,Brand,AED,"
                + "\"Smoke, Product\",AE,10,20,8,7,1,120.50,50,35,17.21\n";

        NoonReportPullResult result = service.pullLatestDay(command(), provider(csv));
        NoonSalesDailyFact fact = writer.facts.get("307|STR245027|AE|2026-05-21|PSKU-1");

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(1, writer.facts.size());
        assertEquals(7L, fact.unitsSold);
        assertEquals("120.50", fact.salesAmount.toPlainString());
        assertEquals("AED", fact.currency);
    }

    @Test
    void shouldAllowRealNoonSalesExportsToStayPendingBeforeReady() {
        AtomicInteger pollCount = new AtomicInteger();
        String csv = "date,sku_parent,units_sold,sales_amount,currency\n"
                + "2026-05-21,Z1,2,39.90,AED\n";

        NoonReportPullResult result = service.pullLatestDay(command(), new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return "EXP-SLOW";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return pollCount.incrementAndGet() < 5
                        ? NoonReportExportStatus.pending()
                        : NoonReportExportStatus.ready("https://download.test/sales.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        });

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(5, pollCount.get());
        assertEquals(1, writer.facts.size());
    }

    private NoonSalesReportPullCommand command() {
        return NoonSalesReportPullCommand.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .date(LocalDate.of(2026, 5, 21))
                .build();
    }

    private NoonReportProvider provider(String csv) {
        return new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                return "EXP-1";
            }

            @Override
            public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
                return NoonReportExportStatus.ready("https://download.test/sales.csv");
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return csv.getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    private static final class InMemorySalesFactWriter implements NoonSalesFactWriter {
        private final Map<String, NoonSalesDailyFact> facts = new LinkedHashMap<>();

        @Override
        public void upsert(NoonSalesDailyFact fact) {
            facts.put(fact.key(), fact);
        }
    }
}
