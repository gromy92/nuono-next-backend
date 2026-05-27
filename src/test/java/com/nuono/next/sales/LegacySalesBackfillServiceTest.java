package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegacySalesBackfillServiceTest {

    @Test
    void backfillsLegacyRowsIntoSameDailySalesFactsWithSourceIdentity() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        LegacySalesBackfillRowProvider provider = command -> List.of(new LegacySalesBackfillRow(
                "legacy-row-001",
                525241L,
                "SA_525241",
                "SA",
                LocalDate.of(2026, 5, 18),
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                "Z02AD5F198C0C2E813C30Z",
                "Paper notebook",
                "SAR",
                5,
                4,
                1,
                new BigDecimal("128.50")
        ));
        LegacySalesBackfillService service = new LegacySalesBackfillService(provider, repository);

        LegacySalesBackfillResult result = service.backfill(new LegacySalesBackfillCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                525241L,
                "SA_525241",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                10002L
        ));

        assertEquals("imported", result.getStatus());
        assertEquals(1, result.getSuccessRows());
        assertEquals(1, repository.batchCount());
        assertEquals(1, repository.count());
        assertEquals("legacy_product_sales_data", repository.latestBatch().getSourceSystem());
        assertEquals("legacy_owner=525241;legacy_store=SA_525241", repository.latestBatch().getSourceFilename());

        DailySalesFact fact = repository.find("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", LocalDate.of(2026, 5, 18)).orElseThrow();
        assertEquals("legacy_product_sales_data", fact.getSourceSystem());
        assertEquals(9001L, fact.getSourceBatchId());
        assertEquals(10002L, fact.getOwnerUserId());
        assertEquals(245027L, fact.getLogicalStoreId());
        assertEquals("STR245027-SAU", fact.getStoreCode());
        assertEquals("SA", fact.getSiteCode());
        assertEquals(5, fact.getGrossUnits());
        assertEquals(4, fact.getShippedUnits());
        assertEquals(1, fact.getCancelledUnits());
        assertEquals(4, fact.getNetUnits());
        assertEquals(new BigDecimal("128.50"), fact.getRevenueShipped());
    }

    @Test
    void excludesUnmappedLegacyRowsIntoImportExceptions() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        LegacySalesBackfillRowProvider provider = command -> List.of(
                legacyRow("legacy-row-001", 525241L, "SA_525241", "SA", "PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1"),
                legacyRow("legacy-row-002", 525241L, "OTHER_STORE", "SA", "PAPERSAYSB360", "Z02AD5F198C0C2E813C30Z-2")
        );
        LegacySalesBackfillService service = new LegacySalesBackfillService(provider, repository);

        LegacySalesBackfillResult result = service.backfill(defaultCommand());

        assertEquals("imported_with_exceptions", result.getStatus());
        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getSuccessRows());
        assertEquals(1, result.getFailureRows());
        assertEquals(1, repository.count());
        assertEquals(1, repository.exceptionCount());
        SalesImportExceptionRecord exception = result.getExceptions().get(0);
        assertEquals(3, exception.getRowNumber());
        assertEquals("mapping_failed", exception.getExceptionType());
        assertEquals("legacyStoreCode", exception.getFieldName());
        assertEquals("OTHER_STORE", exception.getSourceValue());
        assertEquals("legacy-row-002", exception.getSourceContext());
    }

    @Test
    void excludesLegacyRowsOutsideRequestedBackfillDateRange() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        LegacySalesBackfillRowProvider provider = command -> List.of(
                legacyRow("legacy-row-001", 525241L, "SA_525241", "SA", "PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1"),
                new LegacySalesBackfillRow(
                        "legacy-row-old",
                        525241L,
                        "SA_525241",
                        "SA",
                        LocalDate.of(2026, 4, 30),
                        "PAPERSAYSB360",
                        "Z02AD5F198C0C2E813C30Z-2",
                        "Z02AD5F198C0C2E813C30Z",
                        "Old row",
                        "SAR",
                        2,
                        2,
                        0,
                        new BigDecimal("19.80")
                )
        );
        LegacySalesBackfillService service = new LegacySalesBackfillService(provider, repository);

        LegacySalesBackfillResult result = service.backfill(defaultCommand());

        assertEquals("imported_with_exceptions", result.getStatus());
        assertEquals(1, result.getSuccessRows());
        assertEquals(1, result.getFailureRows());
        assertEquals(1, repository.count());
        SalesImportExceptionRecord exception = result.getExceptions().get(0);
        assertEquals("factDate", exception.getFieldName());
        assertEquals("2026-04-30", exception.getSourceValue());
        assertEquals("legacy-row-old", exception.getSourceContext());
    }

    @Test
    void duplicateBackfillKeepsOneFactPerLegacyNaturalKey() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        LegacySalesBackfillRowProvider provider = command -> List.of(
                legacyRow("legacy-row-001", 525241L, "SA_525241", "SA", "PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1")
        );
        LegacySalesBackfillService service = new LegacySalesBackfillService(provider, repository);

        service.backfill(defaultCommand());
        LegacySalesBackfillResult secondResult = service.backfill(defaultCommand());

        assertEquals("imported", secondResult.getStatus());
        assertEquals(2, repository.batchCount());
        assertEquals(1, repository.count());
        DailySalesFact fact = repository.find("PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1", LocalDate.of(2026, 5, 18)).orElseThrow();
        assertEquals(9002L, fact.getSourceBatchId());
    }

    @Test
    void analyticsReadsNoonAndLegacyFactsThroughTheSameQuery() {
        InMemorySalesFactRepository repository = new InMemorySalesFactRepository();
        LegacySalesBackfillService legacyService = new LegacySalesBackfillService(
                command -> List.of(legacyRow("legacy-row-001", 525241L, "SA_525241", "SA", "PAPERSAYSB359", "Z02AD5F198C0C2E813C30Z-1")),
                repository
        );
        legacyService.backfill(defaultCommand());
        repository.upsert(new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                9101L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 18),
                "NOON-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Z57C90A4184D0CFD75218Z",
                "SA",
                "SAR",
                "Noon row",
                10,
                10,
                3,
                2,
                1,
                2,
                new BigDecimal("56.40"),
                null,
                null,
                null
        ));
        SalesAnalyticsService analyticsService = new SalesAnalyticsService(repository);

        SalesDailyFactsView view = analyticsService.listDailyFacts(new SalesFactQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null
        ));

        assertEquals(2, view.getTotal());
        assertEquals(1, view.getItems().stream().filter(fact -> LegacySalesBackfillService.SOURCE_SYSTEM.equals(fact.getSourceSystem())).count());
        assertEquals(1, view.getItems().stream().filter(fact -> NoonSalesCsvImportService.SOURCE_SYSTEM.equals(fact.getSourceSystem())).count());
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
            facts.put(key(fact.getSourceSystem(), fact.getPartnerSku(), fact.getSku(), fact.getFactDate()), fact);
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

        SalesImportBatch latestBatch() {
            return batches.values().stream().reduce((first, second) -> second).orElseThrow();
        }

        Optional<DailySalesFact> find(String partnerSku, String sku, LocalDate factDate) {
            return Optional.ofNullable(facts.get(key(LegacySalesBackfillService.SOURCE_SYSTEM, partnerSku, sku, factDate)));
        }

        private String key(String sourceSystem, String partnerSku, String sku, LocalDate factDate) {
            return sourceSystem + "|" + partnerSku + "|" + sku + "|" + factDate;
        }
    }

    private LegacySalesBackfillCommand defaultCommand() {
        return new LegacySalesBackfillCommand(
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                525241L,
                "SA_525241",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                10002L
        );
    }

    private LegacySalesBackfillRow legacyRow(
            String sourceRowId,
            Long legacyOwnerUserId,
            String legacyStoreCode,
            String siteCode,
            String partnerSku,
            String sku
    ) {
        return new LegacySalesBackfillRow(
                sourceRowId,
                legacyOwnerUserId,
                legacyStoreCode,
                siteCode,
                LocalDate.of(2026, 5, 18),
                partnerSku,
                sku,
                "Z02AD5F198C0C2E813C30Z",
                "Paper notebook",
                "SAR",
                5,
                4,
                1,
                new BigDecimal("128.50")
        );
    }
}
