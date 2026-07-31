package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanQuery;
import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.StockSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ReplenishmentProductCoverageAssembler {
    private ReplenishmentProductCoverageAssembler() {
    }

    static Map<String, ReplenishmentProductStockRow> index(List<ReplenishmentProductStockRow> rows) {
        Map<String, ReplenishmentProductStockRow> indexed = new LinkedHashMap<>();
        if (rows == null) return indexed;
        for (ReplenishmentProductStockRow row : rows) {
            if (row == null || key(row.getPartnerSku()) == null) continue;
            indexed.putIfAbsent(key(row.getPartnerSku()), row);
        }
        return indexed;
    }

    static ReplenishmentPlanOverviewView coverageOnly(
            PlanQuery query,
            ReplenishmentPlanConfig config,
            LocalDate anchorDate,
            Map<String, ReplenishmentProductStockRow> stockByPartnerSku
    ) {
        List<PlanItemView> rows = new ArrayList<>();
        appendBlocked(rows, config, stockByPartnerSku, Set.of());
        return new ReplenishmentPlanOverviewView(
                "coverage_only",
                query.getStoreCode(),
                query.getSiteCode(),
                ReplenishmentPlanConfig.CALCULATION_VERSION,
                config,
                anchorDate,
                summarize(stockByPartnerSku, Set.of()),
                rows
        );
    }

    static void appendBlocked(
            List<PlanItemView> target,
            ReplenishmentPlanConfig config,
            Map<String, ReplenishmentProductStockRow> stockByPartnerSku,
            Set<String> forecastedProductKeys
    ) {
        for (Map.Entry<String, ReplenishmentProductStockRow> entry : stockByPartnerSku.entrySet()) {
            if (forecastedProductKeys.contains(entry.getKey())) continue;
            ReplenishmentProductStockRow row = entry.getValue();
            target.add(ReplenishmentBlockedProductFactory.create(
                    config,
                    row.getPartnerSku(),
                    row.getSku(),
                    row.getProductTitle(),
                    row.getImageUrl(),
                    row.getListingAt(),
                    row.getIsActive(),
                    row.getActiveStateSource(),
                    row.getActiveStateSyncedAt(),
                    row.getCurrentStockUnits(),
                    row.getFbnStockUnits(),
                    row.getSupermallStockUnits(),
                    Boolean.TRUE.equals(row.getIsActive())
            ));
        }
    }

    static ReplenishmentProductCoverageView summarize(
            Map<String, ReplenishmentProductStockRow> stockByPartnerSku,
            Set<String> forecastedProductKeys
    ) {
        Set<String> forecasted = forecastedProductKeys == null ? Set.of() : forecastedProductKeys;
        Set<String> allKeys = new HashSet<>(stockByPartnerSku.keySet());
        allKeys.addAll(forecasted);
        int active = 0;
        int inactive = 0;
        int unknown = 0;
        for (String productKey : allKeys) {
            ReplenishmentProductStockRow row = stockByPartnerSku.get(productKey);
            if (forecasted.contains(productKey) || row != null && Boolean.TRUE.equals(row.getIsActive())) {
                active++;
            } else if (row != null && Boolean.FALSE.equals(row.getIsActive())) {
                inactive++;
            } else {
                unknown++;
            }
        }
        return new ReplenishmentProductCoverageView(
                allKeys.size(), forecasted.size(), active, inactive, unknown);
    }

    static StockSnapshot stockSnapshot(ReplenishmentProductStockRow row) {
        if (row == null) return new StockSnapshot(null, null, null);
        boolean fbnMissing = row.getFbnStockUnits() == null;
        return new StockSnapshot(
                row.getFbnStockUnits(),
                row.getFbnStockUnits(),
                row.getSupermallStockUnits(),
                fbnMissing
        );
    }

    static String imageUrl(ReplenishmentProductStockRow row) {
        return row == null ? null : row.getImageUrl();
    }

    static LocalDate listingAt(ReplenishmentProductStockRow row) {
        return row == null ? null : row.getListingAt();
    }

    private static String key(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim().toUpperCase();
    }
}
