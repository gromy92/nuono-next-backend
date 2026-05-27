package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NoonSalesCsvImportServiceTest {

    @Test
    void importsCsvRowsIntoIdempotentDailySalesFactsAndBatchSummary() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService service = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                repository
        );
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-04-30,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Sanitized sample product,SA,,,1,1,,19.8,,,19.8",
                "2026-05-04,SAMPLE-SKU-002,noon,Z96CFE5673CDC22E224F3Z,Z96CFE5673CDC22E224F3Z-1,electronic_accessories,accessories,card_readers,papersay,SAR,Sanitized sample product 2,SA,10,10,3,2,1,56.400000000000006,100,50,28.2"
        );
        NoonSalesCsvImportCommand command = new NoonSalesCsvImportCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                "confirmed-sanitized.csv",
                csv
        );

        NoonSalesCsvImportResult firstResult = service.importCsv(command);
        NoonSalesCsvImportResult secondResult = service.importCsv(command);

        assertEquals(2, firstResult.getTotalRows());
        assertEquals(2, firstResult.getSuccessRows());
        assertEquals(0, firstResult.getFailureRows());
        assertEquals(LocalDate.of(2026, 4, 30), firstResult.getReportDateFrom());
        assertEquals(LocalDate.of(2026, 5, 4), firstResult.getReportDateTo());
        assertEquals(2, secondResult.getSuccessRows());
        assertEquals(2, repository.batchCount());
        assertEquals(2, repository.count());

        DailySalesFact firstFact = repository.find("SAMPLE-SKU-001", "Z57C90A4184D0CFD75218Z-1", LocalDate.of(2026, 4, 30)).orElseThrow();
        assertEquals(9002L, firstFact.getSourceBatchId());
        assertEquals(10002L, firstFact.getOwnerUserId());
        assertEquals(245027L, firstFact.getLogicalStoreId());
        assertEquals("STR245027-SAU", firstFact.getStoreCode());
        assertEquals("SA", firstFact.getSiteCode());
        assertEquals("noon_productviewsandsalesdata", firstFact.getSourceSystem());
        assertNull(firstFact.getYourVisitors());
        assertNull(firstFact.getCancelledUnits());
        assertEquals(1, firstFact.getNetUnits());
        assertEquals(new BigDecimal("19.8"), firstFact.getRevenueShipped());

        DailySalesFact secondFact = repository.find("SAMPLE-SKU-002", "Z96CFE5673CDC22E224F3Z-1", LocalDate.of(2026, 5, 4)).orElseThrow();
        assertEquals(3, secondFact.getGrossUnits());
        assertEquals(2, secondFact.getShippedUnits());
        assertEquals(1, secondFact.getCancelledUnits());
        assertEquals(2, secondFact.getNetUnits());
        assertEquals(new BigDecimal("56.400000000000006"), secondFact.getRevenueShipped());
        assertEquals(new BigDecimal("50"), secondFact.getConversionVisitorsPercentage());
    }

    @Test
    void importsHeaderOnlyReportAsExplicitEmptyBatchWithoutFacts() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService service = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                repository
        );
        String csv = "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,"
                + "Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,"
                + "Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,"
                + "Conversion_Visitors_Percentage,ASP_shipped_Percentage";

        NoonSalesCsvImportResult result = service.importCsv(new NoonSalesCsvImportCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                "header-only.csv",
                csv
        ));

        assertEquals("empty", result.getStatus());
        assertEquals(0, result.getTotalRows());
        assertEquals(0, result.getSuccessRows());
        assertEquals(0, result.getFailureRows());
        assertEquals(1, repository.batchCount());
        assertEquals("empty", repository.latestBatch().getStatus());
        assertEquals(0, repository.count());
    }

    @Test
    void recordsMissingRequiredHeadersAsFailedBatchWithoutFacts() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService service = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                repository
        );
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-05-18,PAPERSAYSB359,SA,Z02AD5F198C0C2E813C30Z,Z02AD5F198C0C2E813C30Z-1,stationery,office,paper,papersay,SAR,Paper notebook,SA,120,450,8,3,128.50,64.20%,6.67%,16.06"
        );

        NoonSalesCsvImportResult result = service.importCsv(new NoonSalesCsvImportCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                "missing-header.csv",
                csv
        ));

        assertEquals("failed", result.getStatus());
        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessRows());
        assertEquals(1, result.getFailureRows());
        assertEquals("Missing Noon product views and sales report columns: Gross_Units", result.getFailureSummary());
        assertEquals("failed", repository.latestBatch().getStatus());
        assertEquals("Missing Noon product views and sales report columns: Gross_Units", repository.latestBatch().getFailureSummary());
        assertEquals(0, repository.count());
    }

    @Test
    void recordsMalformedRowAsExceptionAndImportsUsableRows() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService service = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                repository
        );
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-05-04,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Good row,SA,10,10,1,1,,19.8,100,10,19.8",
                "2026-05-05,SAMPLE-SKU-002,noon,Z96CFE5673CDC22E224F3Z,Z96CFE5673CDC22E224F3Z-1,electronic_accessories,accessories,card_readers,papersay,SAR,Bad row,SA,10,10,not-a-number,1,,19.8,100,10,19.8"
        );

        NoonSalesCsvImportResult result = service.importCsv(new NoonSalesCsvImportCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                "one-bad-row.csv",
                csv
        ));

        assertEquals("imported_with_exceptions", result.getStatus());
        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getSuccessRows());
        assertEquals(1, result.getFailureRows());
        assertEquals(1, repository.count());
        assertEquals(1, repository.exceptionCount());
        SalesImportExceptionRecord exception = result.getExceptions().get(0);
        assertEquals(3, exception.getRowNumber());
        assertEquals("malformed_number", exception.getExceptionType());
        assertEquals("Gross_Units", exception.getFieldName());
        assertEquals("not-a-number", exception.getSourceValue());
        assertEquals("one-bad-row.csv", exception.getSourceFilename());
    }

    @Test
    void recordsSiteMappingMismatchAsExceptionAndExcludesTheRow() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        NoonSalesCsvImportService service = new NoonSalesCsvImportService(
                new NoonProductViewsSalesReportParser(),
                repository
        );
        String csv = String.join("\n",
                "Visit_Date,Partner_SKU,Mp_Code,SKU_CONFIG,SKU,Family,Product_Type,Product_Subtype,Brand,Currency_Code,Product_Title,Country_Code,Your_Visitors,Total_Visitors,Gross_Units,Shipped_Units,Cancelled_Units,Revenue_Shipped,Buy_Box_Visitor_Percentage,Conversion_Visitors_Percentage,ASP_shipped_Percentage",
                "2026-05-04,SAMPLE-SKU-001,noon,Z57C90A4184D0CFD75218Z,Z57C90A4184D0CFD75218Z-1,bags_luggage,handbag,shopper_tote,papersay,SAR,Good row,SA,10,10,1,1,,19.8,100,10,19.8",
                "2026-05-05,SAMPLE-SKU-002,noon,Z96CFE5673CDC22E224F3Z,Z96CFE5673CDC22E224F3Z-1,electronic_accessories,accessories,card_readers,papersay,SAR,Wrong site row,AE,10,10,1,1,,19.8,100,10,19.8"
        );

        NoonSalesCsvImportResult result = service.importCsv(new NoonSalesCsvImportCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                "wrong-site-row.csv",
                csv
        ));

        assertEquals("imported_with_exceptions", result.getStatus());
        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getSuccessRows());
        assertEquals(1, result.getFailureRows());
        assertEquals(1, repository.count());
        SalesImportExceptionRecord exception = result.getExceptions().get(0);
        assertEquals(3, exception.getRowNumber());
        assertEquals("mapping_failed", exception.getExceptionType());
        assertEquals("Country_Code", exception.getFieldName());
        assertEquals("AE", exception.getSourceValue());
    }

    private static class InMemorySalesFactRepository implements SalesFactRepository {
        private final Map<String, DailySalesFact> facts = new LinkedHashMap<>();
        private final Map<Long, SalesImportBatch> batches = new LinkedHashMap<>();
        private final Map<Long, List<SalesImportExceptionRecord>> exceptions = new LinkedHashMap<>();
        private long nextBatchId = 9001L;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            long batchId = nextBatchId++;
            batches.put(batchId, batch);
            return batchId;
        }

        @Override
        public void upsert(DailySalesFact fact) {
            facts.put(key(fact.getPartnerSku(), fact.getSku(), fact.getFactDate()), fact);
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return List.copyOf(facts.values());
        }

        @Override
        public void saveExceptions(long sourceBatchId, List<SalesImportExceptionRecord> records) {
            exceptions.put(sourceBatchId, List.copyOf(records));
        }

        int batchCount() {
            return batches.size();
        }

        int count() {
            return facts.size();
        }

        int exceptionCount() {
            return exceptions.values().stream().mapToInt(List::size).sum();
        }

        Optional<DailySalesFact> find(String partnerSku, String sku, LocalDate factDate) {
            return Optional.ofNullable(facts.get(key(partnerSku, sku, factDate)));
        }

        SalesImportBatch latestBatch() {
            return batches.values().stream().reduce((first, second) -> second).orElseThrow();
        }

        private String key(String partnerSku, String sku, LocalDate factDate) {
            return partnerSku + "|" + sku + "|" + factDate;
        }
    }
}
