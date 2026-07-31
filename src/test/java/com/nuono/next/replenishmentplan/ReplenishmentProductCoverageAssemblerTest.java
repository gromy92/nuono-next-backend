package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void accountsForActiveInactiveAndUnknownWithoutDroppingBlockedRows() {
        Map<String, ReplenishmentProductStockRow> stock =
                ReplenishmentProductCoverageAssembler.index(List.of(
                        stock("PSKU-ACTIVE", Boolean.TRUE, "NOON_PRICING_INFO"),
                        stock("PSKU-INACTIVE", Boolean.FALSE, "NOON_PRICING_INFO"),
                        stock("PSKU-UNKNOWN", null, null)
                ));
        Set<String> forecasted = Set.of("PSKU-ACTIVE");

        ReplenishmentProductCoverageView coverage =
                ReplenishmentProductCoverageAssembler.summarize(stock, forecasted);
        List<PlanItemView> blocked = new ArrayList<>();
        ReplenishmentProductCoverageAssembler.appendBlocked(
                blocked,
                ReplenishmentPlanConfig.defaultBasicV1(),
                stock,
                forecasted
        );

        assertEquals(3, coverage.getTotalProductCount());
        assertEquals(1, coverage.getForecastedProductCount());
        assertEquals(1, coverage.getActiveProductCount());
        assertEquals(1, coverage.getInactiveProductCount());
        assertEquals(1, coverage.getUnknownProductCount());
        assertEquals(2, blocked.size());
        PlanItemView inactive = row(blocked, "PSKU-INACTIVE");
        assertEquals("INACTIVE", inactive.getActiveState());
        assertTrue(inactive.getWarnings().contains("product_inactive"));
        PlanItemView unknown = row(blocked, "PSKU-UNKNOWN");
        assertEquals("UNKNOWN", unknown.getActiveState());
        assertTrue(unknown.getWarnings().contains("active_state_unknown"));
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

    private PlanItemView row(List<PlanItemView> rows, String partnerSku) {
        return rows.stream()
                .filter(item -> partnerSku.equals(item.getPartnerSku()))
                .findFirst()
                .orElseThrow();
    }
}
