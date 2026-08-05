package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonOrderReportAdapterAtomicityTest {
    private static final String HEADER =
            "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                    + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                    + "order_timestamp,shipment_timestamp,delivered_timestamp\n";

    @Test
    void parsesQuotedCommaWithoutShiftingOrderColumns() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,SKU-1,Processing,"
                        + "65.8,65.8,SAR,papersay,\"stationery,office\",FBN,"
                        + "2026-05-19 23:29:16,,\n"
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals("stationery,office", writer.facts
                .get("noon_order_report|108065|SA|NSAI50094671190-1")
                .getFamily());
    }

    @Test
    void keepsFirstValidatedOrderLineWhenLaterRowReusesIdentity() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,SKU-FIRST,Processing,"
                        + "65.8,65.8,SAR,papersay,stationery,FBN,2026-05-19 23:29:16,,\n"
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,SKU-LATER,Delivered,"
                        + "65.8,65.8,SAR,papersay,stationery,FBN,2026-05-19 23:29:16,,\n"
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(1, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        NoonOrderLineFact fact = writer.facts.get("noon_order_report|108065|SA|NSAI50094671190-1");
        assertEquals("SKU-FIRST", fact.getSku());
        assertEquals("Processing", fact.getStatus());
    }

    @Test
    void keepsDistinctRowsWhenItemNumberIsReusedAcrossDatabaseNaturalIdentities() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER
                        + row("NSAI50094671190-1", "SKU-SA", "65.8", "65.8",
                        "2026-05-19 23:29:16")
                        + row("NSAI50094671190-1", "SKU-OTHER-PARTNER", "65.8", "65.8",
                        "2026-05-19 23:29:16").replaceFirst("108065", "208065")
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED, result.getCode());
        assertEquals(2, result.getImportedCount());
        assertEquals(2, writer.facts.size());
    }

    @Test
    void skipsBlankRequiredScalarsOrderTimestampAndPricesButWritesValidatedRowsOnce() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER
                        + row("NSAI50094671190-1", "SKU-FIRST", "65.8", "65.8",
                        "2026-05-19 23:29:16")
                        + row("", "SKU-NO-IDENTITY", "65.8", "65.8", "2026-05-19 23:29:16")
                        + row("NSAI50094671191-1", "SKU-NO-TIME", "65.8", "65.8", "")
                        + row("NSAI50094671192-1", "SKU-NO-PRICE", "", "65.8",
                        "2026-05-19 23:29:16")
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(3, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        assertEquals(1, writer.facts.size());
    }

    @Test
    void skipsLateNumericAndTimestampBusinessErrorsButWritesValidatedRowsOnce() {
        RecordingWriter writer = new RecordingWriter();
        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER
                        + row("NSAI50094671190-1", "SKU-FIRST", "65.8", "65.8",
                        "2026-05-19 23:29:16")
                        + row("NSAI50094671191-1", "SKU-BAD-NUMBER", "not-a-number", "65.8",
                        "2026-05-19 23:29:16")
                        + row("NSAI50094671192-1", "SKU-BAD-TIME", "65.8", "65.8",
                        "not-a-time")
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(2, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        assertEquals(1, writer.facts.size());
    }

    @Test
    void skipsOnlyRowsThatExceedOrderFactColumnBounds() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER
                        + row("NSAI50094671190-1", "SKU-FIRST", "65.8", "65.8",
                        "2026-05-19 23:29:16")
                        + row("X".repeat(161), "SKU-LONG-IDENTITY", "65.8", "65.8",
                        "2026-05-19 23:29:16")
                        + row("NSAI50094671192-1", "SKU-AMOUNT", "1000000000000", "65.8",
                        "2026-05-19 23:29:16")
                        + row("NSAI50094671193-1", "SKU-PRECISION", "1.0000001", "65.8",
                        "2026-05-19 23:29:16")
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(3, result.getExceptionCount());
        assertEquals(1, writer.facts.size());
    }

    @Test
    void rejectsWindowWidthAndHeaderErrorsBeforeAnyBatchWrite() {
        String valid = row(
                "NSAI50094671190-1",
                "SKU-FIRST",
                "65.8",
                "65.8",
                "2026-05-19 23:29:16"
        );

        assertWholeFileMappingFailure(HEADER + valid
                + row("NSAI50094671191-1", "SKU-BAD", "not-a-number", "65.8",
                "2026-05-20 00:00:00"));
        assertWholeFileMappingFailure(HEADER + valid
                + row("NSAI50094671191-1", "SKU-BAD", "not-a-number", "65.8",
                "2026-05-19 23:29:16").replace("108065,SA,SA,SA", "108065,AE,AE,AE"));
        assertWholeFileMappingFailure(HEADER + valid.substring(0, valid.length() - 1) + ",extra\n");
        assertWholeFileMappingFailure("id_partner,item_nr,status\n108065,ORDER-1,Delivered\n");
    }

    @Test
    void allBusinessRejectedRowsStillCompleteWithoutCallingTheWriter() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(
                HEADER + row("", "SKU-NO-IDENTITY", "65.8", "65.8", "2026-05-19 23:29:16")
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getExceptionCount());
        assertEquals(0, writer.batchCalls);
    }

    @Test
    void malformedUtf8FailsBeforeCallingTheWriter() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(
                file(new byte[]{(byte) 0xc3, (byte) 0x28})
        );

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(0, writer.batchCalls);
    }

    private void assertWholeFileMappingFailure(String content) {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = adapter(writer).process(file(content));

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(0, writer.batchCalls);
        assertEquals(0, writer.facts.size());
    }

    private String row(String item, String sku, String offerPrice, String gmv, String orderTimestamp) {
        return "108065,SA,SA,SA,," + item + ",PAPERSAYSB359," + sku + ",Processing,"
                + offerPrice + "," + gmv + ",SAR,papersay,stationery,FBN,"
                + orderTimestamp + ",,\n";
    }

    private NoonOrderReportAdapter adapter(RecordingWriter writer) {
        return new NoonOrderReportAdapter(
                writer,
                Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC)
        );
    }

    private NoonReportDownloadedFile file(String content) {
        return file(content.getBytes(StandardCharsets.UTF_8));
    }

    private NoonReportDownloadedFile file(byte[] content) {
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.ORDER)
                .reportType(NoonOrderReportDescriptor.REPORT_TYPE)
                .dateFrom(LocalDate.of(2026, 5, 19))
                .dateTo(LocalDate.of(2026, 5, 19))
                .build();
        return new NoonReportDownloadedFile(
                request,
                "EXP-ORDER-1",
                "noon-report-order-1001-abcdef12",
                "abcdef123456",
                content
        );
    }

    private static final class RecordingWriter implements NoonOrderFactWriter {
        private final Map<String, NoonOrderLineFact> facts = new LinkedHashMap<>();
        private int batchCalls;

        @Override
        public void upsertLine(NoonOrderLineFact fact) {
            facts.put(fact.naturalKey(), fact);
        }

        @Override
        public void upsertLines(List<NoonOrderLineFact> accepted) {
            batchCalls++;
            NoonOrderFactWriter.super.upsertLines(accepted);
        }
    }
}
