package com.nuono.next.orderfinance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportProcessResult;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonFinanceTransactionReportAdapterAtomicityTest {

    @Test
    void writesFirstStableIdentityAndSkipsOnlyBlankBusinessFieldsInOneBatch() {
        RecordingWriter writer = new RecordingWriter();
        NoonFinanceTransactionReportAdapter adapter = new NoonFinanceTransactionReportAdapter(writer);
        String valid = row("REF-1", "ORDER-1", "2026-05-21");
        String blankReference = row("", "ORDER-2", "2026-05-21");
        String blankAmount = row("REF-3", "ORDER-3", "2026-05-21")
                .replace(",12.34,", ",,");

        NoonReportProcessResult result = adapter.process(file(
                header() + "\n" + valid + "\n" + valid + "\n" + blankReference + "\n"
                        + blankAmount + "\n"
        ));

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(3, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        assertEquals(1, writer.facts.size());
        assertEquals("REF-1", writer.facts.get(0).getReferenceNr());
    }

    @Test
    void malformedNumericAndDateRowsAreBusinessSkips() {
        RecordingWriter writer = new RecordingWriter();
        String valid = row("REF-1", "ORDER-1", "2026-05-21");
        String invalidAmount = row("REF-2", "ORDER-2", "2026-05-21")
                .replace(",12.34,", ",not-a-number,");

        NoonReportProcessResult result = new NoonFinanceTransactionReportAdapter(writer).process(
                file(header() + "\n" + valid + "\n" + invalidAmount + "\n"
                        + row("REF-3", "ORDER-3", "not-a-date") + "\n")
        );

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(2, result.getExceptionCount());
        assertEquals(1, writer.batchCalls);
        assertEquals(1, writer.facts.size());
    }

    @Test
    void factColumnOverflowSkipsOnlyTheOffendingFinanceRows() {
        RecordingWriter writer = new RecordingWriter();
        String valid = row("REF-1", "ORDER-1", "2026-05-21");
        String amountOverflow = row("REF-2", "ORDER-2", "2026-05-21")
                .replace(",12.34,", ",1000000000000,");

        NoonReportProcessResult result = new NoonFinanceTransactionReportAdapter(writer).process(
                file(header() + "\n" + valid + "\n"
                        + row("R".repeat(161), "ORDER-3", "2026-05-21") + "\n"
                        + amountOverflow + "\n")
        );

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(1, result.getImportedCount());
        assertEquals(2, result.getExceptionCount());
        assertEquals("REF-1", writer.facts.get(0).getReferenceNr());
    }

    @Test
    void windowWidthHeaderAndSiteMismatchFailuresWriteNothing() {
        String valid = row("REF-1", "ORDER-1", "2026-05-21");

        assertWholeFileMappingFailure(valid, row("REF-2", "ORDER-2", "2026-05-20")
                .replace(",12.34,", ",not-a-number,"));
        assertWholeFileMappingFailure(valid, row("REF-2", "ORDER-2", "2026-05-21") + ",extra");
        assertWholeFileMappingFailure(valid, row("REF-2", "ORDER-2", "2026-05-21")
                .replace(",SAR,12.34,", ",AED,not-a-number,"));
        assertWholeFileMappingFailure(valid, row("REF-2", "ORDER-2", "2026-05-20")
                .replace(",SAR,12.34,", ",,not-a-number,"));

        RecordingWriter missingHeaderWriter = new RecordingWriter();
        NoonReportProcessResult missingHeader = new NoonFinanceTransactionReportAdapter(missingHeaderWriter)
                .process(file("Contract\nNOON-SA\n"));
        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, missingHeader.getCode());
        assertEquals(0, missingHeaderWriter.batchCalls);
    }

    @Test
    void allBusinessRejectedRowsStillCompleteWithoutCallingTheWriter() {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = new NoonFinanceTransactionReportAdapter(writer).process(
                file(header() + "\n" + row("", "ORDER-1", "2026-05-21") + "\n")
        );

        assertEquals(NoonReportProcessResult.Code.SUCCEEDED_WITH_BUSINESS_SKIPS, result.getCode());
        assertEquals(0, result.getImportedCount());
        assertEquals(1, result.getExceptionCount());
        assertEquals(0, writer.batchCalls);
    }

    @Test
    void malformedUtf8OrIncompleteContainerWritesNothing() {
        RecordingWriter writer = new RecordingWriter();
        NoonFinanceTransactionReportAdapter adapter = new NoonFinanceTransactionReportAdapter(writer);
        byte[] malformed = new byte[]{(byte) 0xc3, (byte) 0x28};

        NoonReportProcessResult malformedResult = adapter.process(file(malformed));
        NoonReportProcessResult missingColumns = adapter.process(file("Contract\nNOON-SA\n"));

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, malformedResult.getCode());
        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, missingColumns.getCode());
        assertEquals(0, writer.batchCalls);
        assertEquals(0, writer.facts.size());
    }

    @Test
    void localWriterFailureIsNotMisclassifiedAsOneBadBusinessRow() {
        NoonFinanceTransactionReportAdapter adapter = new NoonFinanceTransactionReportAdapter(
                new NoonFinanceTransactionFactWriter() {
                    @Override
                    public void upsert(NoonFinanceTransactionFact fact) {
                        throw new AssertionError("batch path expected");
                    }

                    @Override
                    public void upsertAll(List<NoonFinanceTransactionFact> facts) {
                        throw new IllegalStateException("database unavailable");
                    }
                }
        );

        assertThrows(
                IllegalStateException.class,
                () -> adapter.process(file(header() + "\n" + row("REF-1", "ORDER-1", "2026-05-21") + "\n"))
        );
    }

    private NoonReportDownloadedFile file(String csv) {
        return file(csv.getBytes(StandardCharsets.UTF_8));
    }

    private void assertWholeFileMappingFailure(String valid, String invalid) {
        RecordingWriter writer = new RecordingWriter();

        NoonReportProcessResult result = new NoonFinanceTransactionReportAdapter(writer).process(
                file(header() + "\n" + valid + "\n" + invalid + "\n")
        );

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(0, writer.batchCalls);
        assertEquals(0, writer.facts.size());
    }

    private NoonReportDownloadedFile file(byte[] content) {
        NoonReportPullRequest request = NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NSA")
                .siteCode("SA")
                .dataDomain(NoonPullDataDomain.FINANCE_TRANSACTION)
                .reportType("finance")
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .build();
        return new NoonReportDownloadedFile(request, "EXP-1", "BATCH-1", "digest", content);
    }

    private String header() {
        return "Contract,Contract Title,Reference Nr,Order Nr,Item Nr,Order Date,Transaction Date,Title,SKUs,Partner SKUs,Transaction Type,Currency,Net Proceeds,Referral Fee including VAT,Fullfilment & Logistics Fees including VAT,Shipping Credits including VAT,Other Order Fees including VAT,Order Subsidies including VAT,Non-Order Fees including VAT,Non-Order Subsidies including VAT,Others including VAT,Total";
    }

    private String row(String reference, String order, String transactionDate) {
        return "NOON-SA,Saudi Contract," + reference + "," + order
                + ",ITEM-1,2026-05-21," + transactionDate
                + ",Paper,SKU-1,PAPERSAYSB359,Order,SAR,12.34,-1.23,-2.34,0,0,0,0,0,0,8.77";
    }

    private static final class RecordingWriter implements NoonFinanceTransactionFactWriter {
        private int batchCalls;
        private final List<NoonFinanceTransactionFact> facts = new ArrayList<>();

        @Override
        public void upsert(NoonFinanceTransactionFact fact) {
            facts.add(fact);
        }

        @Override
        public void upsertAll(List<NoonFinanceTransactionFact> facts) {
            batchCalls++;
            this.facts.addAll(facts);
        }
    }
}
