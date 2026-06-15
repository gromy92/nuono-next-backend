package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonOrderReportAdapterTest {

    @Test
    void shouldMapSalesExportRowsToIdempotentOrderLineFacts() {
        InMemoryOrderFactWriter writer = new InMemoryOrderFactWriter();
        NoonOrderReportAdapter adapter = new NoonOrderReportAdapter(
                writer,
                Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC)
        );
        NoonReportDownloadedFile file = file(
                "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                        + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                        + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                        + "Processing,65.8,65.8,SAR,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-19 23:29:16,,\n"
                        + "108065,SA,SA,SA,,NSAI50094671190-2,PAPERSAYSB219,Z7AE0C82BCC231122ABBFZ-1,"
                        + "Delivered,39.5,39.5,SAR,papersay,stationery,Supermall,"
                        + "2026-05-19 22:10:00,2026-05-19 23:00:00,2026-05-20 03:00:00\n"
        );

        NoonReportProcessResult first = adapter.process(file);
        NoonReportProcessResult second = adapter.process(file);

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, first.getCode());
        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, second.getCode());
        assertEquals(2, writer.facts.size());

        NoonOrderLineFact firstLine = writer.facts.get("noon_order_report|108065|SA|NSAI50094671190-1");
        assertEquals(10002L, firstLine.getOwnerUserId());
        assertEquals("STR245027-NAE", firstLine.getStoreCode());
        assertEquals("AE", firstLine.getSiteCode());
        assertEquals("NSAI50094671190", firstLine.getOrderIdentity());
        assertEquals("NSAI50094671190-1", firstLine.getOrderLineIdentity());
        assertEquals("PAPERSAYSB359", firstLine.getPartnerSku());
        assertEquals("Z02AD5F198C0C2E813C30Z-1", firstLine.getSku());
        assertEquals("Processing", firstLine.getStatus());
        assertEquals(new BigDecimal("65.8"), firstLine.getGmvLcy());
        assertEquals("SAR", firstLine.getCurrencyCode());
        assertEquals("noon-report-order-1001-abcdef12", firstLine.getSourceBatchId());
        assertEquals(LocalDate.of(2026, 5, 19), firstLine.getReportDateFrom());
        assertNull(firstLine.getDeliveredTimestamp());
    }

    @Test
    void shouldRejectOrderReportWhenAllRowsAreOutsideRequestedWindow() {
        InMemoryOrderFactWriter writer = new InMemoryOrderFactWriter();
        NoonOrderReportAdapter adapter = new NoonOrderReportAdapter(
                writer,
                Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC)
        );

        NoonReportProcessResult result = adapter.process(fileForWindow(
                LocalDate.of(2025, 11, 28),
                LocalDate.of(2025, 12, 27),
                "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                        + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                        + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                        + "Processing,65.8,65.8,SAR,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-19 23:29:16,,\n"
        ));

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(0, result.getImportedCount());
        assertTrue(writer.facts.isEmpty());
        assertTrue(result.getDiagnosticMessage().contains("provider_reused_latest_export"));
        assertTrue(result.getDiagnosticMessage().contains("requested=2025-11-28..2025-12-27"));
        assertTrue(result.getDiagnosticMessage().contains("actual=2026-05-19..2026-05-19"));
    }

    @Test
    void shouldImportRequestedWindowRowsFromMixedWindowOrderReport() {
        InMemoryOrderFactWriter writer = new InMemoryOrderFactWriter();
        NoonOrderReportAdapter adapter = new NoonOrderReportAdapter(
                writer,
                Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC)
        );

        NoonReportProcessResult result = adapter.process(fileForWindow(
                LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 19),
                "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                        + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                        + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                        + "Processing,65.8,65.8,SAR,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-19 23:29:16,,\n"
                        + "108065,SA,SA,SA,,NSAI50094671191-1,PAPERSAYSB360,Z02AD5F198C0C2E813C31Z-1,"
                        + "Processing,65.8,65.8,SAR,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-20 00:01:00,,\n"
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(1, writer.facts.size());
        NoonOrderLineFact imported = writer.facts.get("noon_order_report|108065|SA|NSAI50094671190-1");
        assertEquals(LocalDate.of(2026, 5, 19), imported.getOrderTimestamp().toLocalDate());
        assertEquals(LocalDate.of(2026, 5, 19), imported.getReportDateFrom());
        assertEquals(LocalDate.of(2026, 5, 19), imported.getReportDateTo());
        assertNull(writer.facts.get("noon_order_report|108065|SA|NSAI50094671191-1"));
    }

    @Test
    void shouldClassifyMissingColumnsAndReadinessAwareEmptyReports() {
        NoonOrderReportAdapter afterReady = new NoonOrderReportAdapter(
                new InMemoryOrderFactWriter(),
                Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC)
        );
        NoonOrderReportAdapter beforeReady = new NoonOrderReportAdapter(
                new InMemoryOrderFactWriter(),
                Clock.fixed(Instant.parse("2026-05-21T23:59:00Z"), ZoneOffset.UTC)
        );

        assertEquals(
                NoonReportProcessResult.Code.MISSING_COLUMNS,
                afterReady.process(file("id_partner,item_nr,status\n108065,NSAI1,Delivered\n")).getCode()
        );
        assertEquals(
                NoonReportProcessResult.Code.EMPTY_REPORT,
                afterReady.process(file("")).getCode()
        );
        assertEquals(
                NoonReportProcessResult.Code.REPORT_NOT_READY,
                beforeReady.process(file("")).getCode()
        );
    }

    @Test
    void shouldPersistMissingColumnDiagnosticsWithActualHeaderSummary() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService = new NoonPullFoundationService(
                repository,
                clock,
                new NoonPullFailurePolicy(clock)
        );
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .scheduleExpression("manual")
                .build());
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.REPORT)
                .dataDomain(NoonPullDataDomain.ORDER)
                .triggerMode(NoonPullTriggerMode.MANUAL_BACKFILL)
                .targetIdentity("orders:2026-05-19..2026-05-19")
                .targetDateFrom(LocalDate.of(2026, 5, 19))
                .targetDateTo(LocalDate.of(2026, 5, 19))
                .build()).orElseThrow();
        NoonReportPuller puller = new NoonReportPuller(foundationService);
        NoonOrderReportAdapter adapter = new NoonOrderReportAdapter(
                new InMemoryOrderFactWriter(),
                clock
        );

        puller.execute(
                task.getId(),
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR245027-NAE")
                        .siteCode("AE")
                        .dataDomain(NoonPullDataDomain.ORDER)
                        .reportType(NoonOrderReportDescriptor.REPORT_TYPE)
                        .dateFrom(LocalDate.of(2026, 5, 19))
                        .dateTo(LocalDate.of(2026, 5, 19))
                        .maxPollAttempts(1)
                        .build(),
                StaticOrderReportProvider.ready("id_partner,status,partner_sku\n108065,Delivered,PAPERSAYSB359\n"),
                adapter::process
        );

        NoonPullTaskRecord persisted = repository.selectTask(task.getId());
        assertEquals("missing_columns", persisted.getFailureType());
        assertTrue(persisted.getDiagnosticSummary().contains("missing columns"));
        assertTrue(persisted.getDiagnosticSummary().contains("missing="));
        assertTrue(persisted.getDiagnosticSummary().contains("item_nr"));
        assertTrue(persisted.getDiagnosticSummary().contains("actual_headers=id_partner,status,partner_sku"));
    }

    private NoonReportDownloadedFile file(String content) {
        return fileForWindow(LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 19), content);
    }

    private NoonReportDownloadedFile fileForWindow(LocalDate dateFrom, LocalDate dateTo, String content) {
        return new NoonReportDownloadedFile(
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR245027-NAE")
                        .siteCode("AE")
                        .dataDomain(NoonPullDataDomain.ORDER)
                        .reportType(NoonOrderReportDescriptor.REPORT_TYPE)
                        .dateFrom(dateFrom)
                        .dateTo(dateTo)
                        .build(),
                "EXP-ORDER-1",
                "noon-report-order-1001-abcdef12",
                "abcdef123456",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static final class InMemoryOrderFactWriter implements NoonOrderFactWriter {
        private final Map<String, NoonOrderLineFact> facts = new LinkedHashMap<>();

        @Override
        public void upsertLine(NoonOrderLineFact fact) {
            facts.put(fact.naturalKey(), fact);
        }
    }

    private static final class StaticOrderReportProvider implements NoonReportProvider {
        private final String content;

        private StaticOrderReportProvider(String content) {
            this.content = content;
        }

        private static StaticOrderReportProvider ready(String content) {
            return new StaticOrderReportProvider(content);
        }

        @Override
        public String createExport(NoonReportPullRequest request) {
            return "EXP-ORDER-1";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            return NoonReportExportStatus.ready("https://download.test/orders.csv");
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            return content.getBytes(StandardCharsets.UTF_8);
        }
    }
}
