package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReplenishmentProductCoverageAssemblerTest {
    @Test
    void everyMaintainedProductCanBeForecastedWhileActiveStateRemainsInformational() {
        Map<String, ReplenishmentProductStockRow> stock =
                ReplenishmentProductCoverageAssembler.index(List.of(
                        stock("PSKU-ACTIVE", Boolean.TRUE, "NOON_PRICING_INFO"),
                        stock("PSKU-INACTIVE", Boolean.FALSE, "NOON_PRICING_INFO"),
                        stock("PSKU-UNKNOWN", null, null)
                ));
        Set<String> forecasted = Set.of("PSKU-ACTIVE", "PSKU-INACTIVE", "PSKU-UNKNOWN");

        ReplenishmentProductCoverageView coverage =
                ReplenishmentProductCoverageAssembler.summarize(stock, forecasted);

        assertEquals(3, coverage.getTotalProductCount());
        assertEquals(3, coverage.getForecastedProductCount());
        assertEquals(1, coverage.getActiveProductCount());
        assertEquals(1, coverage.getInactiveProductCount());
        assertEquals(1, coverage.getUnknownProductCount());
    }

    @Test
    void missingForecastBlocksForMissingForecastRatherThanActiveState() {
        Map<String, ReplenishmentProductStockRow> stock =
                ReplenishmentProductCoverageAssembler.index(List.of(
                        stock("PSKU-INACTIVE", Boolean.FALSE, "NOON_PRICING_INFO")
                ));
        List<PlanItemView> blocked = new ArrayList<>();

        ReplenishmentProductCoverageAssembler.appendBlocked(
                blocked,
                ReplenishmentPlanConfig.defaultBasicV1(),
                stock,
                Set.of()
        );

        assertEquals(1, blocked.size());
        assertEquals("INACTIVE", blocked.get(0).getActiveState());
        assertTrue(blocked.get(0).getWarnings().contains("forecast_missing"));
        assertFalse(blocked.get(0).getWarnings().contains("product_inactive"));
    }

    private ReplenishmentProductStockRow stock(String partnerSku, Boolean active, String source) {
        return new ReplenishmentProductStockRow(
                partnerSku,
                "SKU-" + partnerSku,
                "Product " + partnerSku,
                null,
                LocalDate.of(2026, 3, 12),
                active,
                source,
                LocalDateTime.of(2026, 7, 6, 10, 0),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

}
