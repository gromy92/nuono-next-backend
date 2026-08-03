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
            BigDecimal supermallStockUnits
    ) {
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
                List.of("forecast_missing"),
                "本次运行缺少对应销量预测，当前无法生成补货建议。"
        ).withActiveState(isActive, activeStateSource, activeStateSyncedAt);
    }
}
