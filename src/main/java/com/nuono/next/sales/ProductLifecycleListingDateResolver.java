package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleListingDateResolver {

    private static final long NEW_PRODUCT_WINDOW_DAYS = 30L;

    public ProductLifecycleListingDateResolution resolve(
            ProductLifecycleListingSignals signals,
            LocalDate analysisDate
    ) {
        SelectedListingDate selected = select(signals);
        boolean historicalOldProduct = signals.hasSixtyDayHistoricalSignals();
        boolean leftTruncatedHistoricalWindow = isLeftTruncatedHistoricalWindow(signals, selected);
        boolean recentEnoughForNew = selected.listingDate != null
                && analysisDate != null
                && ChronoUnit.DAYS.between(selected.listingDate, analysisDate) <= NEW_PRODUCT_WINDOW_DAYS;
        boolean eligibleForNew = recentEnoughForNew && !historicalOldProduct && !leftTruncatedHistoricalWindow;
        return new ProductLifecycleListingDateResolution(
                selected.listingDate,
                selected.source,
                selected.confidence,
                historicalOldProduct,
                eligibleForNew,
                evidenceJson(signals, selected, historicalOldProduct, eligibleForNew, leftTruncatedHistoricalWindow)
        );
    }

    private boolean isLeftTruncatedHistoricalWindow(
            ProductLifecycleListingSignals signals,
            SelectedListingDate selected
    ) {
        if (!"sales".equals(selected.source) && !"pv".equals(selected.source)) {
            return false;
        }
        return signals.getHistoricalSignalDays() > 0 && signals.getHistoricalSignalDays() < 60;
    }

    private SelectedListingDate select(ProductLifecycleListingSignals signals) {
        if (signals.getOfficialListingDate() != null) {
            return new SelectedListingDate(signals.getOfficialListingDate(), "official", "high");
        }
        if (signals.getEarliestInventoryDate() != null) {
            return new SelectedListingDate(signals.getEarliestInventoryDate(), "inventory", "high");
        }
        if (signals.getEarliestPvDate() != null) {
            return new SelectedListingDate(signals.getEarliestPvDate(), "pv", "medium");
        }
        if (signals.getEarliestSalesDate() != null) {
            return new SelectedListingDate(signals.getEarliestSalesDate(), "sales", "medium");
        }
        if (signals.getProductPulledDate() != null) {
            return new SelectedListingDate(signals.getProductPulledDate(), "pulled", "low");
        }
        return new SelectedListingDate(null, "missing", "none");
    }

    private String evidenceJson(
            ProductLifecycleListingSignals signals,
            SelectedListingDate selected,
            boolean historicalOldProduct,
            boolean eligibleForNew,
            boolean leftTruncatedHistoricalWindow
    ) {
        return "{"
                + "\"source\":\"" + selected.source + "\","
                + "\"confidence\":\"" + selected.confidence + "\","
                + "\"listingDate\":" + jsonDate(selected.listingDate) + ","
                + "\"officialListingDate\":" + jsonDate(signals.getOfficialListingDate()) + ","
                + "\"earliestInventoryDate\":" + jsonDate(signals.getEarliestInventoryDate()) + ","
                + "\"earliestPvDate\":" + jsonDate(signals.getEarliestPvDate()) + ","
                + "\"earliestSalesDate\":" + jsonDate(signals.getEarliestSalesDate()) + ","
                + "\"productPulledDate\":" + jsonDate(signals.getProductPulledDate()) + ","
                + "\"historicalSignalDays\":" + signals.getHistoricalSignalDays() + ","
                + "\"salesSignalDays\":" + signals.getSalesSignalDays() + ","
                + "\"pvSignalDays\":" + signals.getPvSignalDays() + ","
                + "\"inventorySignalDays\":" + signals.getInventorySignalDays() + ","
                + "\"historicalOldProduct\":" + historicalOldProduct + ","
                + "\"leftTruncatedHistoricalWindow\":" + leftTruncatedHistoricalWindow + ","
                + "\"eligibleForNewInitialization\":" + eligibleForNew
                + "}";
    }

    private String jsonDate(LocalDate date) {
        if (date == null) {
            return "null";
        }
        return "\"" + date + "\"";
    }

    private static class SelectedListingDate {
        private final LocalDate listingDate;
        private final String source;
        private final String confidence;

        private SelectedListingDate(LocalDate listingDate, String source, String confidence) {
            this.listingDate = listingDate;
            this.source = source;
            this.confidence = confidence;
        }
    }
}
