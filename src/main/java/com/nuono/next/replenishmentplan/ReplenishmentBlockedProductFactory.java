package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class ReplenishmentBlockedProductFactory {
    private ReplenishmentBlockedProductFactory() {
    }

    static PlanItemView create(
            ReplenishmentPlanConfig config,
            String partnerSku,
            String sku,
            String productTitle,
            String imageUrl,
            LocalDate listingAt,
            Boolean isActive,
            String activeStateSource,
            LocalDateTime activeStateSyncedAt,
            BigDecimal currentStockUnits,
            BigDecimal fbnStockUnits,
            BigDecimal supermallStockUnits,
            boolean forecastMissing
    ) {
        String warning;
        String explanation;
        if (Boolean.FALSE.equals(isActive)) {
            warning = "product_inactive";
            explanation = "Noon 明确返回商品已停用，当前不生成补货建议。";
        } else if (Boolean.TRUE.equals(isActive) && forecastMissing) {
            warning = "active_forecast_missing";
            explanation = "商品在售，但本次运行缺少对应销量预测，当前不生成补货建议。";
        } else {
            warning = "active_state_unknown";
            explanation = "商品在售状态正在通过 Noon 权威定价接口自动核实，完成前不生成补货建议。";
        }
        int airStart = config == null ? 0 : config.getAirLeadDays();
        int airEnd = config == null ? 0 : airStart + config.getAirCoverDays();
        int seaStart = config == null ? 0 : config.getSeaLeadDays();
        int seaEnd = config == null ? 0 : seaStart + config.getSeaCoverDays();
        return new PlanItemView(
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                config,
                partnerSku,
                sku,
                productTitle,
                imageUrl,
                listingAt,
                null,
                0, 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, 0,
                BigDecimal.ZERO,
                null,
                null,
                currentStockUnits,
                fbnStockUnits,
                supermallStockUnits,
                BigDecimal.ZERO,
                List.of(),
                null,
                BigDecimal.ZERO,
                0,
                null,
                null,
                airStart,
                airEnd,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                seaStart,
                seaEnd,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(),
                List.of(),
                true,
                List.of(warning),
                explanation
        ).withActiveState(isActive, activeStateSource, activeStateSyncedAt);
    }
}
