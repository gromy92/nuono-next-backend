package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private NoonReportDownloadedFile file(String content) {
        return new NoonReportDownloadedFile(
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR245027-NAE")
                        .siteCode("AE")
                        .dataDomain(NoonPullDataDomain.ORDER)
                        .reportType(NoonOrderReportDescriptor.EXPORT_CATEGORY_CODE)
                        .dateFrom(LocalDate.of(2026, 5, 19))
                        .dateTo(LocalDate.of(2026, 5, 19))
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
}
