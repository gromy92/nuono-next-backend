package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductLifecycleCalculationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisProductLifecycleCalculationSourceTest {

    @Test
    void productProjectionSourceFeedsLifecycleJobWithProductManagementSkuAndRealSignals() {
        RecordingCalculationMapper mapper = new RecordingCalculationMapper();
        RecordingSalesFactRepository salesFacts = new RecordingSalesFactRepository();
        RecordingActivityWindowRepository activityWindows = new RecordingActivityWindowRepository();
        MyBatisProductLifecycleCalculationSource source =
                new MyBatisProductLifecycleCalculationSource(mapper, salesFacts, activityWindows);

        ProductLifecycleCalculationScope scope = new ProductLifecycleCalculationScope(
                10002L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2026, 5, 21),
                ProductLifecycleResult.DEFAULT_RULE_VERSION,
                true
        );
        mapper.productScopes = List.of(new ProductLifecycleProductScopeRow(
                10002L,
                "STR245027-NAE",
                "AE",
                "MILKYWAYA05",
                "z2666f058ef551eb603a1z-1"
        ));
        mapper.listingSignal = new ProductLifecycleListingSignalRow(
                null,
                null,
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 5, 8),
                11,
                11,
                11,
                0
        );
        mapper.currentStock = 0;

        ProductLifecycleStateQuery query = source.listProductScopes(scope).get(0);
        assertEquals("MILKYWAYA05", query.getPartnerSku());
        assertEquals("z2666f058ef551eb603a1z-1", query.getSku());

        List<DailySalesFact> facts = source.loadFacts(query, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 21));
        assertEquals(1, facts.size());
        assertEquals("z2666f058ef551eb603a1z-1", salesFacts.lastQuery.getSku());

        ProductLifecycleListingSignals signals = source.loadListingSignals(query, LocalDate.of(2026, 5, 21));
        assertEquals(null, signals.getEarliestInventoryDate());
        assertEquals(LocalDate.of(2026, 5, 4), signals.getEarliestPvDate());
        assertEquals(LocalDate.of(2026, 5, 10), signals.getEarliestSalesDate());
        assertEquals(11, signals.getHistoricalSignalDays());

        List<SalesActivityWindowRecord> windows =
                source.loadActivityWindows(query, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 21));
        assertEquals(1, windows.size());
        assertEquals("STR245027-NAE", activityWindows.lastScope.getStoreCode());

        assertTrue(source.isStockoutDistorted(query, features(0, 5)));
        assertFalse(source.isStockoutDistorted(query, features(1, 5)));
    }

    @Test
    void scheduledScopesComeFromProductProjectionScopes() {
        RecordingCalculationMapper mapper = new RecordingCalculationMapper();
        mapper.scheduledScopes = List.of(new ProductLifecycleScheduledScopeRow(10002L, "STR245027-NAE", "AE"));
        MyBatisProductLifecycleCalculationSource source = new MyBatisProductLifecycleCalculationSource(
                mapper,
                new RecordingSalesFactRepository(),
                new RecordingActivityWindowRepository()
        );

        List<ProductLifecycleCalculationScope> scopes = source.listScheduledScopes(LocalDate.of(2026, 5, 21));

        assertEquals(1, scopes.size());
        assertEquals(10002L, scopes.get(0).getOwnerUserId());
        assertEquals("STR245027-NAE", scopes.get(0).getStoreCode());
        assertEquals("AE", scopes.get(0).getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 21), scopes.get(0).getAnchorDate());
        assertFalse(scopes.get(0).isRerun());
    }

    private ProductLifecycleFeatureSnapshot features(int recent7Sales, int recent30Sales) {
        ProductLifecycleFeatureWindow recent7 =
                new ProductLifecycleFeatureWindow(7, 7, recent7Sales, 10L, recent7Sales > 0 ? 1 : 0, 7, false);
        ProductLifecycleFeatureWindow recent30 =
                new ProductLifecycleFeatureWindow(30, 30, recent30Sales, 30L, recent30Sales > 0 ? 5 : 0, 30, false);
        ProductLifecycleFeatureWindow zero15 = new ProductLifecycleFeatureWindow(15, 15, 0, 15L, 0, 15, false);
        return new ProductLifecycleFeatureSnapshot(
                new ProductLifecycleStateQuery(10002L, "STR245027-NAE", "AE", "MILKYWAYA05", "z2666"),
                LocalDate.of(2026, 5, 21),
                recent7,
                zero15,
                zero15,
                recent30,
                recent30,
                recent30,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                recent30.getActiveSalesDays(),
                List.of(),
                "{}"
        );
    }

    private DailySalesFact fact(String partnerSku, String sku) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-NAE",
                "AE",
                LocalDate.of(2026, 5, 20),
                partnerSku,
                sku,
                sku,
                "AE",
                "AED",
                "Fixture",
                5,
                null,
                1,
                1,
                0,
                1,
                BigDecimal.TEN,
                null,
                null,
                null
        );
    }

    private class RecordingSalesFactRepository implements SalesFactRepository {
        private SalesFactQuery lastQuery;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            return 1;
        }

        @Override
        public void upsert(DailySalesFact fact) {
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            this.lastQuery = query;
            return List.of(fact(query.getPartnerSku(), query.getSku()));
        }
    }

    private static class RecordingActivityWindowRepository implements SalesActivityWindowRepository {
        private SalesActivityWindowScope lastScope;

        @Override
        public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
            return record;
        }

        @Override
        public SalesActivityWindowRecord find(Long id) {
            return null;
        }

        @Override
        public void setEnabled(Long id, boolean enabled, Long updatedBy) {
        }

        @Override
        public List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope) {
            return List.of();
        }

        @Override
        public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
            this.lastScope = scope;
            return List.of(new SalesActivityWindowRecord(
                    90001L,
                    scope.getOwnerUserId(),
                    scope.getStoreCode(),
                    scope.getSiteCode(),
                    "Ramadan",
                    "seasonal",
                    "all",
                    scope.getDateFrom(),
                    scope.getDateTo(),
                    BigDecimal.ONE,
                    true,
                    1,
                    10002L,
                    10002L
            ));
        }
    }

    private static class RecordingCalculationMapper implements ProductLifecycleCalculationMapper {
        private List<ProductLifecycleScheduledScopeRow> scheduledScopes = new ArrayList<>();
        private List<ProductLifecycleProductScopeRow> productScopes = new ArrayList<>();
        private ProductLifecycleListingSignalRow listingSignal;
        private Integer currentStock;

        @Override
        public List<ProductLifecycleScheduledScopeRow> selectScheduledScopes() {
            return scheduledScopes;
        }

        @Override
        public List<ProductLifecycleProductScopeRow> selectProductScopes(ProductLifecycleCalculationScope scope) {
            return productScopes;
        }

        @Override
        public ProductLifecycleListingSignalRow selectListingSignals(ProductLifecycleStateQuery query) {
            return listingSignal;
        }

        @Override
        public Integer selectCurrentStock(ProductLifecycleStateQuery query) {
            return currentStock;
        }
    }
}
