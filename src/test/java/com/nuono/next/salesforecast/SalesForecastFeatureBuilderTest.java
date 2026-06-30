package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportService;
import com.nuono.next.sales.ProductLifecycleClassifier;
import com.nuono.next.sales.ProductLifecycleCurrentState;
import com.nuono.next.sales.ProductLifecycleHistoryRecord;
import com.nuono.next.sales.ProductLifecycleJobQuery;
import com.nuono.next.sales.ProductLifecycleJobRecord;
import com.nuono.next.sales.ProductLifecycleStateQuery;
import com.nuono.next.sales.ProductLifecycleStateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesForecastFeatureBuilderTest {

    @Test
    void buildsProductSnapshotsFromDailySalesFactsAndLifecycleClassifier() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder(new ProductLifecycleClassifier());

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(factsForGrowthProduct(latestFactDate), latestFactDate);

        assertEquals(1, snapshots.size());
        SalesForecastFeatureSnapshot snapshot = snapshots.get(0);
        assertEquals(10002L, snapshot.getOwnerUserId());
        assertEquals("STR245027-SAU", snapshot.getStoreCode());
        assertEquals("SA", snapshot.getSiteCode());
        assertEquals("PAPERSAYSB359", snapshot.getPartnerSku());
        assertEquals("Z02AD5F198C0C2E813C30Z-1", snapshot.getSku());
        assertEquals("Paper notebook", snapshot.getProductTitle());
        assertEquals(latestFactDate, snapshot.getLatestFactDate());
        assertEquals(21, snapshot.getHistoryUnits7());
        assertEquals(60, snapshot.getHistoryUnits30());
        assertEquals(90, snapshot.getHistoryUnits60());
        assertEquals(90, snapshot.getHistoryUnits90());
        assertEquals(90, snapshot.getObservedDays());
        assertEquals("growth", snapshot.getLifecycleCode());
        assertEquals("增长", snapshot.getLifecycleLabel());
    }

    @Test
    void groupsForecastFeaturesByPartnerSkuWhenExternalSkuChanges() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder(new ProductLifecycleClassifier());
        List<DailySalesFact> facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "Z-OLD-1", 2),
                fact(LocalDate.of(2026, 5, 19), "Z-OLD-1", 3),
                fact(LocalDate.of(2026, 5, 20), "Z-NEW-1", 5)
        );

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(facts, latestFactDate);

        assertEquals(1, snapshots.size());
        SalesForecastFeatureSnapshot snapshot = snapshots.get(0);
        assertEquals("PAPERSAYSB359", snapshot.getPartnerSku());
        assertEquals("Z-NEW-1", snapshot.getSku());
        assertEquals(10, snapshot.getHistoryUnits7());
        assertEquals(10, snapshot.getHistoryUnits30());
    }

    @Test
    void excludesFactsWithoutBusinessPskuFromForecastFeatures() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder(new ProductLifecycleClassifier());
        List<DailySalesFact> facts = List.of(
                fact(LocalDate.of(2026, 5, 18), "-", "Z-MISSING-1", 2),
                fact(LocalDate.of(2026, 5, 19), "-", "Z-MISSING-2", 3),
                fact(LocalDate.of(2026, 5, 20), "PAPERSAYSB359", "Z-VALID-1", 5)
        );

        List<SalesForecastFeatureSnapshot> snapshots = builder.build(facts, latestFactDate);

        assertEquals(1, snapshots.size());
        assertEquals("PAPERSAYSB359", snapshots.get(0).getPartnerSku());
        assertEquals(5, snapshots.get(0).getHistoryUnits30());
    }

    @Test
    void consumesPersistedLifecycleStateInsteadOfIndependentForecastFormula() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        RecordingLifecycleStateRepository lifecycleRepository = new RecordingLifecycleStateRepository();
        lifecycleRepository.put(state("growth", "增长", "ready", "{\"reason\":\"growth\"}"));
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder(
                new ProductLifecycleClassifier(),
                lifecycleRepository
        );

        SalesForecastFeatureSnapshot snapshot = builder.build(factsForGrowthProduct(latestFactDate), latestFactDate).get(0);

        assertEquals("growth", snapshot.getLifecycleCode());
        assertEquals("增长", snapshot.getLifecycleLabel());
        assertEquals("DEFAULT_V1", snapshot.getLifecycleRuleVersion());
        assertEquals("ready", snapshot.getLifecycleQualityState());
        assertEquals("{\"reason\":\"growth\"}", snapshot.getLifecycleEvidenceJson());
    }

    @Test
    void exposesDataInsufficientAndStockoutHoldLifecycleWarningsForForecast() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        RecordingLifecycleStateRepository lifecycleRepository = new RecordingLifecycleStateRepository();
        lifecycleRepository.put(state("data_insufficient", "数据不足", "pv_unresolvable", "{\"reason\":\"pv_unresolvable\"}"));
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder(
                new ProductLifecycleClassifier(),
                lifecycleRepository
        );

        SalesForecastFeatureSnapshot dataInsufficient = builder.build(factsForGrowthProduct(latestFactDate), latestFactDate).get(0);

        assertEquals("data_insufficient", dataInsufficient.getLifecycleCode());
        assertEquals("pv_unresolvable", dataInsufficient.getLifecycleQualityState());
        assertEquals(List.of("lifecycle_data_insufficient"), dataInsufficient.getWarningCodes());

        lifecycleRepository.put(state("stable", "稳定", "stockout_hold", "{\"reason\":\"stockout_hold\"}"));
        SalesForecastFeatureSnapshot stockoutHold = builder.build(factsForGrowthProduct(latestFactDate), latestFactDate).get(0);

        assertEquals("stable", stockoutHold.getLifecycleCode());
        assertEquals("stockout_hold", stockoutHold.getLifecycleQualityState());
        assertEquals(List.of("lifecycle_stockout_hold"), stockoutHold.getWarningCodes());
    }

    @Test
    void exposesPendingLifecycleWhenForecastRunsBeforeLifecycleJob() {
        LocalDate latestFactDate = LocalDate.of(2026, 5, 20);
        SalesForecastFeatureBuilder builder = new SalesForecastFeatureBuilder(
                new ProductLifecycleClassifier(),
                new RecordingLifecycleStateRepository()
        );

        SalesForecastFeatureSnapshot snapshot = builder.build(factsForGrowthProduct(latestFactDate), latestFactDate).get(0);

        assertEquals("pending", snapshot.getLifecycleCode());
        assertEquals("待计算", snapshot.getLifecycleLabel());
        assertEquals("pending", snapshot.getLifecycleQualityState());
        assertEquals(List.of("lifecycle_pending"), snapshot.getWarningCodes());
    }

    private List<DailySalesFact> factsForGrowthProduct(LocalDate latestFactDate) {
        List<DailySalesFact> facts = new ArrayList<>();
        for (int dayOffset = 89; dayOffset >= 0; dayOffset--) {
            int netUnits = unitsForOffset(dayOffset);
            facts.add(fact(latestFactDate.minusDays(dayOffset), netUnits));
        }
        return facts;
    }

    private int unitsForOffset(int dayOffset) {
        if (dayOffset <= 6) {
            return 3;
        }
        if (dayOffset <= 13) {
            return dayOffset == 13 ? 4 : 1;
        }
        if (dayOffset <= 27) {
            return dayOffset <= 21 ? 2 : 1;
        }
        if (dayOffset <= 29) {
            return dayOffset == 28 ? 3 : 4;
        }
        return dayOffset <= 59 ? 1 : 0;
    }

    private DailySalesFact fact(LocalDate factDate, int netUnits) {
        return fact(factDate, "Z02AD5F198C0C2E813C30Z-1", netUnits);
    }

    private DailySalesFact fact(LocalDate factDate, String sku, int netUnits) {
        return fact(factDate, "PAPERSAYSB359", sku, netUnits);
    }

    private DailySalesFact fact(LocalDate factDate, String partnerSku, String sku, int netUnits) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                factDate,
                partnerSku,
                sku,
                "Z02AD5F198C0C2E813C30Z",
                "SA",
                "SAR",
                "Paper notebook",
                10,
                20,
                netUnits,
                netUnits,
                0,
                netUnits,
                BigDecimal.TEN,
                null,
                null,
                null
        );
    }

    private ProductLifecycleCurrentState state(
            String code,
            String label,
            String qualityState,
            String evidenceJson
    ) {
        return new ProductLifecycleCurrentState(
                70001L,
                10002L,
                "STR245027-SAU",
                "SA",
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                code,
                label,
                "DEFAULT_V1",
                LocalDate.of(2026, 5, 20),
                LocalDate.of(2026, 1, 1),
                "official",
                qualityState,
                "persisted lifecycle",
                evidenceJson,
                72001L,
                null
        );
    }

    private static class RecordingLifecycleStateRepository implements ProductLifecycleStateRepository {
        private final Map<ProductLifecycleStateQuery, ProductLifecycleCurrentState> states = new HashMap<>();

        private void put(ProductLifecycleCurrentState state) {
            states.put(new ProductLifecycleStateQuery(
                    state.getOwnerUserId(),
                    state.getStoreCode(),
                    state.getSiteCode(),
                    state.getPartnerSku(),
                    state.getSku()
            ), state);
        }

        @Override
        public void saveCurrentState(ProductLifecycleCurrentState state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveHistory(ProductLifecycleHistoryRecord historyRecord) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveJob(ProductLifecycleJobRecord job) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query) {
            return states.get(query);
        }

        @Override
        public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
            return List.of();
        }

        @Override
        public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
            return List.of();
        }
    }
}
