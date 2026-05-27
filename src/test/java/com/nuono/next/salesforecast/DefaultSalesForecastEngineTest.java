package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSalesForecastEngineTest {

    @Test
    void defaultV1ComputesDeterministicForecastWindowsFromHistoryWindows() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();
        SalesForecastFeatureSnapshot snapshot = snapshot("growth", "增长");

        SalesForecastFormulaResult result = engine.forecast30(snapshot, "DEFAULT_V1");

        assertEquals("DEFAULT_V1", result.getCalculationVersion());
        assertEquals("DEFAULT_V1", result.getConfigVersion());
        assertEquals(106, result.getForecastUnits30());
        assertEquals(211, result.getForecastUnits60());
        assertEquals(316, result.getForecastUnits90());
        assertEquals("0.5000", result.getRecentDailyTrendRate().toPlainString());
        assertEquals("1.2500", result.getTrendFactor().toPlainString());
        assertEquals("1.2200", result.getLifecycleFactor().toPlainString());
        assertTrue(result.getShortReason().contains("近7日 21"));
        assertTrue(result.getShortReason().contains("生命周期：增长"));
        assertTrue(result.getShortReason().contains("30/60/90天"));
    }

    @Test
    void defaultV1ExplainsLifecycleFactorForSupportedLifecycleCases() {
        DefaultSalesForecastEngine engine = new DefaultSalesForecastEngine();

        assertLifecycleFactor(engine, "new", "新品", "1.0500", "新品");
        assertLifecycleFactor(engine, "growth", "增长", "1.2200", "增长");
        assertLifecycleFactor(engine, "stable", "稳定", "1.0000", "稳定");
        assertLifecycleFactor(engine, "decline", "衰退", "0.8200", "衰退");
        assertLifecycleFactor(engine, "longTail", "长尾期", "0.6200", "长尾期");
        assertLifecycleFactor(engine, "data_insufficient", "数据不足", "1.0000", "样本不足");
    }

    private void assertLifecycleFactor(
            DefaultSalesForecastEngine engine,
            String lifecycleCode,
            String lifecycleLabel,
            String expectedFactor,
            String expectedExplanation
    ) {
        SalesForecastFormulaResult result = engine.forecast30(snapshot(lifecycleCode, lifecycleLabel), "DEFAULT_V1");

        assertEquals(expectedFactor, result.getLifecycleFactor().toPlainString());
        assertTrue(result.getLifecycleExplanation().contains(expectedExplanation));
    }

    private SalesForecastFeatureSnapshot snapshot(String lifecycleCode, String lifecycleLabel) {
        return new SalesForecastFeatureSnapshot(
                10002L,
                "STR245027-SAU",
                "SA",
                "PAPERSAYSB359",
                "Z02AD5F198C0C2E813C30Z-1",
                "Paper notebook",
                LocalDate.of(2026, 5, 20),
                21,
                60,
                90,
                90,
                90,
                12,
                null,
                lifecycleCode,
                lifecycleLabel,
                List.of()
        );
    }
}
