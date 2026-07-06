package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SalesForecastDetailViewTest {

    @Test
    void exposesCalendarFactorImpactsFromFeatureSnapshotJson() {
        SalesForecastResultRecord record = new SalesForecastResultRecord(
                1L,
                2L,
                10002L,
                "STR108065-NSA",
                "SA",
                "PAPERSAYS065",
                "SKU-1",
                "Paper",
                LocalDate.of(2026, 6, 23),
                12,
                77,
                210,
                259,
                90,
                182,
                new BigDecimal("70.9"),
                56,
                111,
                166,
                DefaultSalesForecastEngine.CALCULATION_VERSION,
                "CALENDAR_FACTOR_CURRENT",
                new BigDecimal("1.3200"),
                BigDecimal.ZERO,
                BigDecimal.ONE,
                new BigDecimal("1.1200"),
                "medium",
                "中",
                "样本满足",
                "",
                "",
                "",
                "",
                "日历因子30/60/90天：1.0000 / 1.1200 / 1.1200",
                "{"
                        + "\"calendarFactorImpacts\":["
                        + "{"
                        + "\"ruleName\":\"Eid stationery\","
                        + "\"activityType\":\"holiday\","
                        + "\"dateFrom\":\"2026-06-30\","
                        + "\"dateTo\":\"2026-07-02\","
                        + "\"targetScopeType\":\"site\","
                        + "\"targetScopeValue\":\"SA|family:paper\","
                        + "\"factorValue\":\"1.1200\","
                        + "\"matchedScopeLabel\":\"站点+大类目\","
                        + "\"affectedDays30\":3,"
                        + "\"affectedDays60\":3,"
                        + "\"affectedDays90\":3"
                        + "}"
                        + "],"
                        + "\"historyCalendarFactorImpacts\":["
                        + "{"
                        + "\"ruleName\":\"Eid history\","
                        + "\"activityType\":\"holiday\","
                        + "\"dateFrom\":\"2026-05-26\","
                        + "\"dateTo\":\"2026-05-29\","
                        + "\"targetScopeType\":\"site\","
                        + "\"targetScopeValue\":\"SA\","
                        + "\"factorValue\":\"0.7000\","
                        + "\"matchedScopeLabel\":\"站点全品\","
                        + "\"affectedDays30\":4,"
                        + "\"affectedDays60\":4,"
                        + "\"affectedDays90\":4"
                        + "}"
                        + "],"
                        + "\"adjustedHistoryUnits7\":\"70.0000\","
                        + "\"adjustedHistoryUnits30\":\"70.0000\","
                        + "\"adjustedHistoryUnits60\":\"70.0000\","
                        + "\"adjustedHistoryUnits90\":\"70.0000\","
                        + "\"calendarFactor30\":\"1.0000\","
                        + "\"calendarFactor60\":\"1.1200\","
                        + "\"calendarFactor90\":\"1.1200\""
                        + "}"
        );

        SalesForecastDetailView detail = SalesForecastDetailView.fromResult(record);

        assertEquals("1.0000", detail.getFactorBreakdown().getFutureFactor30().setScale(4).toPlainString());
        assertEquals("1.1200", detail.getFactorBreakdown().getFutureFactor60().setScale(4).toPlainString());
        assertEquals("1.1200", detail.getFactorBreakdown().getFutureFactor90().setScale(4).toPlainString());
        assertEquals("70.0000", detail.getFeatureValues().getAdjustedHistoryUnits7().setScale(4).toPlainString());
        assertEquals("70.0000", detail.getFeatureValues().getAdjustedHistoryUnits30().setScale(4).toPlainString());
        assertEquals(1, detail.getCalendarFactorImpacts().size());
        SalesForecastCalendarFactorImpactView impact = detail.getCalendarFactorImpacts().get(0);
        assertEquals("Eid stationery", impact.getRuleName());
        assertEquals("2026-06-30", String.valueOf(impact.getDateFrom()));
        assertEquals("1.1200", impact.getFactorValue().setScale(4).toPlainString());
        assertEquals("站点+大类目", impact.getMatchedScopeLabel());
        assertEquals(3, impact.getAffectedDays30());
        assertEquals(1, detail.getHistoryCalendarFactorImpacts().size());
        SalesForecastCalendarFactorImpactView historyImpact = detail.getHistoryCalendarFactorImpacts().get(0);
        assertEquals("Eid history", historyImpact.getRuleName());
        assertEquals("2026-05-26", String.valueOf(historyImpact.getDateFrom()));
        assertEquals("0.7000", historyImpact.getFactorValue().setScale(4).toPlainString());
    }
}
