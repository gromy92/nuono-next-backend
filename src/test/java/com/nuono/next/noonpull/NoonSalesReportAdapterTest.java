package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonSalesReportAdapterTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void shouldTreatRecentEmptyProductViewsReportAsPendingConfirmation() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T05:30:00Z"), SHANGHAI);
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter((fact) -> {
        }, clock);

        NoonReportProcessResult result = adapter.process(file("Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n",
                LocalDate.of(2026, 5, 23)));

        assertEquals(NoonReportProcessResult.Code.EMPTY_REPORT_PENDING_CONFIRMATION, result.getCode());
    }

    @Test
    void olderHeaderOnlyReportStillRequiresAuthoritativeProviderZeroRowProof() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T05:30:00Z"), SHANGHAI);
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter((fact) -> {
        }, clock);

        NoonReportProcessResult result = adapter.process(file("Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n",
                LocalDate.of(2026, 5, 20)));

        assertEquals(NoonReportProcessResult.Code.EMPTY_REPORT_PENDING_CONFIRMATION, result.getCode());
        assertTrue(result.getDiagnosticMessage().contains("provider_poll_row_count_required"));
    }

    @Test
    void shouldKeepDistinctChildSkusAndSkipOnlyInvalidBusinessRows() {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(
                writer,
                Clock.fixed(Instant.parse("2026-05-24T05:30:00Z"), SHANGHAI)
        );

        NoonReportProcessResult result = adapter.process(file(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                        + "2026-05-20,PAPERSAYSB1,SKU-1,SAR,2,10.00\n"
                        + "2026-05-20,PAPERSAYSB1,SKU-LATER,SAR,9,99.00\n"
                        + "2026-05-20,,SKU-2,SAR,3,20.00\n"
                        + "2026-05-20,PAPERSAYSB3,SKU-3,,4,30.00\n"
                        + ",,,,,\n",
                LocalDate.of(2026, 5, 20)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(2, result.getImportedCount());
        assertEquals(3, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        assertEquals(2, writer.facts.size());
        assertEquals("SKU-1", writer.facts.get(0).getSku());
        assertEquals(2L, writer.facts.get(0).getUnitsSold());
        assertEquals("SKU-LATER", writer.facts.get(1).getSku());
    }

    @Test
    void shouldKeepFirstValidRowWhenTheExactDatabaseIdentityRepeats() {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(writer, Clock.system(SHANGHAI));

        NoonReportProcessResult result = adapter.process(file(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                        + "2026-05-20,PAPERSAYSB1,SKU-1,SAR,2,10.00\n"
                        + "2026-05-20,PAPERSAYSB1,SKU-1,SAR,9,99.00\n",
                LocalDate.of(2026, 5, 20)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(1, result.getExceptionCount());
        assertEquals(2L, writer.facts.get(0).getUnitsSold());
    }

    @Test
    void shouldSkipMalformedNumericAndDateRowsButWriteValidatedRowsOnce() {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(writer, Clock.system(SHANGHAI));
        String header = "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n";

        NoonReportProcessResult result = adapter.process(file(
                header
                        + "2026-05-20,PAPERSAYSB1,SKU-1,SAR,2,10.00\n"
                        + "2026-05-20,PAPERSAYSB2,SKU-2,SAR,not-a-number,20.00\n"
                        + "not-a-date,PAPERSAYSB3,SKU-3,SAR,2,20.00\n",
                LocalDate.of(2026, 5, 20)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(2, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        assertEquals("SKU-1", writer.facts.get(0).getSku());
    }

    @Test
    void shouldSkipOnlyRowsThatExceedFactColumnBounds() {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(writer, Clock.system(SHANGHAI));

        NoonReportProcessResult result = adapter.process(file(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                        + "2026-05-20,PAPERSAYSB1,SKU-1,SAR,2,10.00\n"
                        + "2026-05-20,PAPERSAYSB2,SKU-2,SAR,2147483648,20.00\n"
                        + "2026-05-20,PAPERSAYSB3,SKU-3,SAR,2,1000000000000\n"
                        + "2026-05-20,PAPERSAYSB4,SKU-4,SAR,2,1.0000001\n",
                LocalDate.of(2026, 5, 20)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(3, result.getExceptionCount());
        assertEquals("SKU-1", writer.facts.get(0).getSku());
    }

    @Test
    void shouldRejectWindowWidthAndHeaderErrorsWithoutWritingEarlierRows() {
        String header = "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n";
        String valid = "2026-05-20,PAPERSAYSB1,SKU-1,SAR,2,10.00\n";

        assertWholeFileMappingFailure(header + valid
                + "2026-05-19,PAPERSAYSB2,SKU-2,SAR,not-a-number,20.00\n");
        assertWholeFileMappingFailure(header + valid
                + "2026-05-20,PAPERSAYSB2,SKU-2,AED,not-a-number,20.00\n");
        assertWholeFileMappingFailure(header + valid
                + "2026-05-19,PAPERSAYSB2,SKU-2,,not-a-number,20.00\n");
        assertWholeFileMappingFailure(header + valid
                + "2026-05-20,PAPERSAYSB2,SKU-2,SAR,2,20.00,extra\n");
        assertWholeFileMappingFailure(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units\n"
                        + "2026-05-20,PAPERSAYSB1,SKU-1,SAR,2\n"
        );
    }

    @Test
    void allBusinessRejectedRowsStillCompleteWithoutCallingTheWriter() {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(writer, Clock.system(SHANGHAI));

        NoonReportProcessResult result = adapter.process(file(
                "Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                        + "2026-05-20,,SKU-1,SAR,2,10.00\n",
                LocalDate.of(2026, 5, 20)
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getExceptionCount());
        assertEquals(0, writer.batchCalls);
    }

    @Test
    void shouldRejectMalformedUtf8BeforeFactWrite() {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(writer, Clock.system(SHANGHAI));
        NoonReportDownloadedFile malformed = new NoonReportDownloadedFile(
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR108065-NSA")
                        .siteCode("SA")
                        .dataDomain(NoonPullDataDomain.SALES)
                        .reportType("productviewsandsalesdata")
                        .dateFrom(LocalDate.of(2026, 5, 20))
                        .dateTo(LocalDate.of(2026, 5, 20))
                        .build(),
                "EXP-SALES",
                "batch-1",
                "digest-1",
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );

        NoonReportProcessResult result = adapter.process(malformed);

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertTrue(writer.facts.isEmpty());
        assertEquals(0, writer.batchCalls);
    }

    private NoonReportDownloadedFile file(String csv, LocalDate date) {
        return new NoonReportDownloadedFile(
                NoonReportPullRequest.builder()
                        .ownerUserId(10002L)
                        .storeCode("STR108065-NSA")
                        .siteCode("SA")
                        .dataDomain(NoonPullDataDomain.SALES)
                        .reportType("productviewsandsalesdata")
                        .dateFrom(date)
                        .dateTo(date)
                        .build(),
                "EXP-SALES",
                "batch-1",
                "digest-1",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void assertWholeFileMappingFailure(String csv) {
        RecordingSalesWriter writer = new RecordingSalesWriter();
        NoonSalesReportAdapter adapter = new NoonSalesReportAdapter(
                writer,
                Clock.fixed(Instant.parse("2026-05-24T05:30:00Z"), SHANGHAI)
        );

        NoonReportProcessResult result = adapter.process(file(csv, LocalDate.of(2026, 5, 20)));

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(0, writer.batchCalls);
        assertTrue(writer.facts.isEmpty());
    }

    private static final class RecordingSalesWriter implements NoonSalesFactWriter {
        private final List<NoonSalesDailyFact> facts = new ArrayList<>();
        private int batchCalls;

        @Override
        public void upsert(NoonSalesDailyFact fact) {
            facts.add(fact);
        }

        @Override
        public void upsertAll(List<NoonSalesDailyFact> accepted) {
            batchCalls++;
            facts.addAll(accepted);
        }
    }
}
