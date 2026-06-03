package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.operationsconfig.OperationLifecycleRuleThresholds;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductLifecycleDefaultV1ClassifierTest {

    private final ProductLifecycleListingDateResolver listingResolver = new ProductLifecycleListingDateResolver();
    private final ProductLifecycleFeatureBuilder featureBuilder = new ProductLifecycleFeatureBuilder();
    private final ProductLifecycleSalesCorrectionService correctionService = new ProductLifecycleSalesCorrectionService();
    private final ProductLifecycleClassifier classifier = new ProductLifecycleClassifier();

    @Test
    void classifiesRecentListingAsNew() {
        ProductLifecycleResult result = classifier.classify(input(
                LocalDate.of(2026, 5, 20),
                listing(LocalDate.of(2026, 5, 5), null, null, null, null, 0),
                facts(LocalDate.of(2026, 5, 5), 16, 1, 10),
                List.of()
        ));

        assertEquals("new", result.getCode());
        assertEquals("新品", result.getLabel());
        assertEquals("ready", result.getQualityState());
    }

    @Test
    void classifiesNewByConfiguredLifecycleWindow() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        ProductLifecycleClassificationInput input = input(
                analysisDate,
                listing(analysisDate.minusDays(49), null, null, null, null, 0),
                facts(analysisDate.minusDays(59), 60, 5, 20),
                List.of()
        );

        ProductLifecycleResult defaultResult = classifier.classify(input);
        ProductLifecycleResult strictResult = classifier.classify(
                input,
                ruleSet(OperationLifecycleRuleThresholds.defaultV1().withNewMaxAgeDays(45))
        );

        assertEquals("new", defaultResult.getCode());
        assertEquals("stable", strictResult.getCode());
        assertTrue(defaultResult.getEvidenceJson().contains("\"reason\":\"new_listing_window\""));
    }

    @Test
    void classifiesStepGrowthWhenRecentSevenDayAverageJumps() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(29), 15, 2, 20));
        facts.addAll(facts(analysisDate.minusDays(14), 8, 2, 20));
        facts.addAll(facts(analysisDate.minusDays(6), 7, 5, 20));
        facts.addAll(facts(analysisDate.minusDays(59), 30, 2, 20));

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        ));

        assertEquals("growth", result.getCode());
        assertEquals("增长", result.getLabel());
        assertTrue(result.getEvidenceJson().contains("\"reason\":\"growth_shape_step\""));
        assertTrue(result.getEvidenceJson().contains("\"growthShape\":\"step\""));
    }

    @Test
    void classifiesExplosiveGrowthFromThirtyDayGrowthRate() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 30, 2, 20));
        facts.addAll(facts(analysisDate.minusDays(29), 30, 4, 20));

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        ));

        assertEquals("growth", result.getCode());
        assertTrue(result.getEvidenceJson().contains("\"reason\":\"growth_shape_explosive\""));
        assertTrue(result.getEvidenceJson().contains("\"growthShape\":\"explosive\""));
    }

    @Test
    void classifiesVolatileGrowthFromTrimmedMomentum() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 30, 2, 20));
        facts.addAll(facts(analysisDate.minusDays(29), 15, 2, 20));
        facts.addAll(facts(analysisDate.minusDays(14), 15, 4, 20));

        ProductLifecycleResult result = classifier.classify(
                input(
                        analysisDate,
                        listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                        facts,
                        List.of()
                ),
                ruleSet(OperationLifecycleRuleThresholds.defaultV1()
                        .withGrowthMinSalesGrowthRate(new BigDecimal("2.0000")))
        );

        assertEquals("growth", result.getCode());
        assertTrue(result.getEvidenceJson().contains("\"reason\":\"growth_shape_volatile\""));
        assertTrue(result.getEvidenceJson().contains("\"growthShape\":\"volatile\""));
    }

    @Test
    void volatileOutlierTrimRatioFromRuleSetAffectsGrowthShape() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 30, 10, 20));
        for (int i = 29; i >= 0; i--) {
            facts.add(fact(analysisDate.minusDays(i), i == 7 ? 100 : 10, 20));
        }
        ProductLifecycleClassificationInput input = input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        );
        OperationLifecycleRuleThresholds highGrowthThreshold = OperationLifecycleRuleThresholds.defaultV1()
                .withGrowthMinSalesGrowthRate(new BigDecimal("2.0000"));

        ProductLifecycleResult defaultTrimResult = classifier.classify(input, ruleSet(highGrowthThreshold));
        ProductLifecycleResult noTrimResult = classifier.classify(
                input,
                ruleSet(highGrowthThreshold.withVolatileOutlierTrimRatio(BigDecimal.ZERO))
        );

        assertEquals("stable", defaultTrimResult.getCode());
        assertEquals("growth", noTrimResult.getCode());
        assertTrue(noTrimResult.getEvidenceJson().contains("\"growthShape\":\"volatile\""));
    }

    @Test
    void classifiesSteadyGrowthWhenGrowthIsPositiveAndVolatilityIsLow() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 30, 10, 20));
        facts.addAll(facts(analysisDate.minusDays(29), 30, 11, 20));

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        ));

        assertEquals("growth", result.getCode());
        assertTrue(result.getEvidenceJson().contains("\"reason\":\"growth_shape_steady\""));
        assertTrue(result.getEvidenceJson().contains("\"growthShape\":\"steady\""));
    }

    @Test
    void stableEvidenceContainsDynamicWeightCombinedDailyForecast() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 30, 12, 20));
        facts.addAll(facts(analysisDate.minusDays(29), 30, 12, 20));

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        ));

        assertEquals("stable", result.getCode());
        assertEquals("稳定", result.getLabel());
        assertTrue(result.getEvidenceJson().contains("\"reason\":\"stable_dynamic_weight\""));
        assertTrue(result.getEvidenceJson().contains("\"stableShortWeight\":\"0.7000\""));
        assertTrue(result.getEvidenceJson().contains("\"stableCombinedDailyForecast\""));
    }

    @Test
    void partialImportWindowWithoutTrustedListingDateIsDataInsufficient() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(29), 15, 5, 20));
        facts.addAll(facts(analysisDate.minusDays(14), 15, 5, 20));

        ProductLifecycleListingDateResolution listing = listingResolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                analysisDate.minusDays(29),
                analysisDate.minusDays(1),
                30,
                30,
                0,
                0
        ), analysisDate);

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing,
                facts,
                List.of()
        ));

        assertEquals("data_insufficient", result.getCode());
        assertEquals("数据不足", result.getLabel());
        assertTrue(listing.getEvidenceJson().contains("\"leftTruncatedHistoricalWindow\":true"));
    }

    @Test
    void classifiesDeclineFromSevenDayDecayRatio() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 21, 10, 20));
        facts.addAll(facts(analysisDate.minusDays(38), 31, 10, 20));
        facts.addAll(facts(analysisDate.minusDays(7), 1, 10, 20));
        facts.addAll(facts(analysisDate.minusDays(6), 7, 4, 20));

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        ));

        assertEquals("decline", result.getCode());
        assertEquals("衰退", result.getLabel());
        assertTrue(result.getEvidenceJson().contains("\"reason\":\"decline_decay_ratio\""));
        assertTrue(result.getEvidenceJson().contains("\"decayRatio\":\"0.4000\""));
    }

    @Test
    void correctedSnapshotExposesFormulaWindowsForThirtyDayGrowthAndDecline() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(59), 30, 2, 20));
        facts.addAll(facts(analysisDate.minusDays(29), 30, 4, 20));

        ProductLifecycleFeatureSnapshot features = featureBuilder.build(query(), analysisDate, facts);
        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                features,
                facts,
                List.of()
        );

        assertEquals(new BigDecimal("28.0000"), corrected.getCorrectedRecent7Sales());
        assertEquals(new BigDecimal("120.0000"), corrected.getCorrectedRecent30Sales());
        assertEquals(new BigDecimal("60.0000"), corrected.getCorrectedPrevious30Sales());
        assertEquals(new BigDecimal("1.0000"), corrected.getCorrectedSalesGrowth30());
        assertEquals(new BigDecimal("106.0000"), corrected.getCorrectedHistoricalT38ToT8Sales());
        assertTrue(corrected.getEvidenceJson().contains("\"correctedRecent7Sales\":28.0000"));
        assertTrue(corrected.getEvidenceJson().contains("\"correctedPrevious30Sales\":60.0000"));
        assertTrue(corrected.getEvidenceJson().contains("\"correctedHistoricalT38ToT8Sales\":106.0000"));
        assertTrue(corrected.getEvidenceJson().contains("\"correctedSalesGrowth30\":1.0000"));
    }

    @Test
    void classifiesLowVolumeVolatileProductAsLongTailInsteadOfGrowthOrDecline() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            int units = i == 1 || i == 15 ? 1 : 0;
            facts.add(fact(analysisDate.minusDays(i), units, 10));
        }

        ProductLifecycleResult result = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        ));

        assertEquals("longTail", result.getCode());
        assertEquals("长尾期", result.getLabel());
        assertTrue(result.getEvidenceJson().contains("long_tail_formula"));
    }

    @Test
    void customLifecycleThresholdsFromRuleSetAffectClassification() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        List<DailySalesFact> facts = new ArrayList<>();
        facts.addAll(facts(analysisDate.minusDays(29), 15, 0, 20));
        facts.addAll(facts(analysisDate.minusDays(14), 8, 1, 20));
        facts.addAll(facts(analysisDate.minusDays(6), 7, 0, 20));
        ProductLifecycleClassificationInput classificationInput = input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts,
                List.of()
        );

        ProductLifecycleResult defaultResult = classifier.classify(classificationInput);
        ProductLifecycleResult strictResult = classifier.classify(
                classificationInput,
                new ProductLifecycleRuleSet(
                        "LIFECYCLE_CONFIG_STRICT_LONGTAIL",
                        false,
                        "LIFECYCLE_CONFIG_STRICT_LONGTAIL",
                        "严格长尾阈值",
                        "运营发布",
                        OperationLifecycleRuleThresholds.defaultV1().withLongTailMaxMonthlySales(new BigDecimal("2"))
                )
        );

        assertEquals("longTail", defaultResult.getCode());
        assertEquals("stable", strictResult.getCode());
        assertTrue(strictResult.getEvidenceJson().contains("\"lifecycleVersionNo\":\"LIFECYCLE_CONFIG_STRICT_LONGTAIL\""));
        assertTrue(strictResult.getEvidenceJson().contains("\"reason\":\"stable_dynamic_weight\""));
    }

    @Test
    void returnsDataInsufficientForExplicitUnrecoverableBranchesOnly() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        ProductLifecycleResult missingListing = classifier.classify(input(
                analysisDate,
                listing(null, null, null, null, null, 0),
                facts(analysisDate.minusDays(14), 15, 4, 10),
                List.of()
        ));

        assertEquals("data_insufficient", missingListing.getCode());
        assertTrue(missingListing.getEvidenceJson().contains("missing_listing_date"));

        ProductLifecycleResult missingPv = classifier.classify(input(
                analysisDate,
                listing(LocalDate.of(2026, 1, 1), null, null, null, null, 60),
                facts(analysisDate.minusDays(14), 15, 1, null),
                List.of()
        ));

        assertEquals("data_insufficient", missingPv.getCode());
        assertTrue(missingPv.getEvidenceJson().contains("pv_unresolvable"));
    }

    private ProductLifecycleClassificationInput input(
            LocalDate analysisDate,
            ProductLifecycleListingDateResolution listing,
            List<DailySalesFact> facts,
            List<SalesActivityWindowRecord> windows
    ) {
        ProductLifecycleFeatureSnapshot features = featureBuilder.build(query(), analysisDate, facts);
        ProductLifecycleCorrectedFeatureSnapshot corrected = correctionService.correct(
                query(),
                analysisDate,
                features,
                facts,
                windows
        );
        return new ProductLifecycleClassificationInput(query(), analysisDate, listing, features, corrected);
    }

    private ProductLifecycleRuleSet ruleSet(OperationLifecycleRuleThresholds thresholds) {
        return new ProductLifecycleRuleSet(
                "DEFAULT_V1_TEST",
                false,
                "DEFAULT_V1_TEST",
                "测试生命周期配置",
                "测试",
                thresholds
        );
    }

    private ProductLifecycleListingDateResolution listing(
            LocalDate official,
            LocalDate inventory,
            LocalDate pv,
            LocalDate sales,
            LocalDate pulled,
            int historicalSignalDays
    ) {
        return listingResolver.resolve(new ProductLifecycleListingSignals(
                query(),
                official,
                inventory,
                pv,
                sales,
                pulled,
                historicalSignalDays,
                historicalSignalDays,
                historicalSignalDays,
                0
        ), LocalDate.of(2026, 5, 20));
    }

    private List<DailySalesFact> facts(LocalDate startDate, int dayCount, int units, Integer pv) {
        List<DailySalesFact> facts = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            facts.add(fact(startDate.plusDays(i), units, pv));
        }
        return facts;
    }

    private DailySalesFact fact(LocalDate date, int netUnits, Integer pv) {
        return new DailySalesFact(
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                10001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                date,
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1",
                "Z57C90A4184D0CFD75218Z",
                "SA",
                "SAR",
                "Sanitized sample product",
                pv,
                null,
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

    private ProductLifecycleStateQuery query() {
        return new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1"
        );
    }
}
