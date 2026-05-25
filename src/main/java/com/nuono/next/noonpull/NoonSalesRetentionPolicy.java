package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoonSalesRetentionPolicy {
    private final Clock clock;

    public NoonSalesRetentionPolicy() {
        this(Clock.systemUTC());
    }

    public NoonSalesRetentionPolicy(Clock clock) {
        this.clock = clock;
    }

    public LocalDate retainedFrom(NoonSalesRetentionCapability capability) {
        NoonSalesRetentionCapability safeCapability =
                capability == null ? NoonSalesRetentionCapability.defaultCapability() : capability;
        return LocalDate.now(clock).minusDays(safeCapability.getRetentionDays());
    }

    public NoonSalesBackfillPlan planBackfill(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            NoonSalesRetentionCapability capability
    ) {
        LocalDate retainedFrom = retainedFrom(capability);
        LocalDate dateFrom = requestedFrom == null || requestedFrom.isBefore(retainedFrom) ? retainedFrom : requestedFrom;
        String qualityState = requestedFrom != null && requestedFrom.isBefore(retainedFrom)
                ? "provider_retention_limit"
                : "ready";
        boolean retryable = !"provider_retention_limit".equals(qualityState);
        return new NoonSalesBackfillPlan(dateFrom, requestedTo, qualityState, retryable);
    }

    public NoonSalesCorrectionPlan weeklyCorrection(LocalDate latestDate) {
        return new NoonSalesCorrectionPlan(
                "weekly_45_day_correction",
                "weekly",
                45,
                latestDate.minusDays(44),
                latestDate
        );
    }

    public List<String> defaultScheduledCorrections() {
        return List.of("weekly_45_day_correction");
    }

    public NoonSalesReadinessView historyUnavailableView(String qualityState) {
        NoonSalesReadinessView view = new NoonSalesReadinessView();
        view.setQualityState(qualityState);
        view.setMetricsAllowed(false);
        view.setSalesAmount(null);
        view.setUnitsSold(null);
        return view;
    }

    public NoonSalesLatestDayPlan latestDayNotReady(LocalDate latestDate) {
        return new NoonSalesLatestDayPlan(latestDate, "report_not_ready", true, List.of());
    }
}
