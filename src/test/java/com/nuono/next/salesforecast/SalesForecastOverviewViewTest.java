package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesForecastOverviewViewTest {

    @Test
    void followUpMarkedUsesPartnerSkuAsStableIdentity() {
        SalesForecastRunRecord run = new SalesForecastRunRecord(
                90001L,
                10002L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 6, 23),
                DefaultSalesForecastEngine.CALCULATION_VERSION,
                "CALENDAR_FACTOR_CURRENT",
                "succeeded",
                1,
                LocalDateTime.of(2026, 6, 29, 10, 0)
        );
        SalesForecastResultRecord result = resultRecord("PAPERSAYS065", "SKU-NEW");
        SalesForecastFollowUpRecord followUp = new SalesForecastFollowUpRecord(
                10002L,
                "STR108065-NSA",
                "SA",
                "PAPERSAYS065",
                "SKU-OLD",
                true,
                307L
        );

        SalesForecastOverviewView overview = SalesForecastOverviewView.ready(
                "STR108065-NSA",
                "SA",
                run,
                List.of(result),
                List.of(followUp)
        );

        assertEquals(true, overview.getRows().get(0).isFollowUpMarked());
    }

    private SalesForecastResultRecord resultRecord(String partnerSku, String sku) {
        return new SalesForecastResultRecord(
                1L,
                90001L,
                10002L,
                "STR108065-NSA",
                "SA",
                partnerSku,
                sku,
                "Paper",
                LocalDate.of(2026, 6, 23),
                7,
                30,
                60,
                90,
                90,
                100,
                new BigDecimal("100.0"),
                31,
                62,
                93,
                DefaultSalesForecastEngine.CALCULATION_VERSION,
                "CALENDAR_FACTOR_CURRENT",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "medium",
                "中",
                "样本满足",
                "",
                "",
                "",
                "",
                "日历因子30/60/90天：1.0000 / 1.0000 / 1.0000",
                "{}"
        );
    }
}
