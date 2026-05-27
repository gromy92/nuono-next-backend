package com.nuono.next.noonsync;

import com.nuono.next.sales.SalesDataQualityState;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class NoonSalesSurfaceSyncInput {

    private final NoonSyncScope scope;
    private final LocalDate latestAvailableSalesDate;
    private final LocalDate analysisDate;
    private final List<SalesDataQualityState> qualityStates;
    private final boolean correctionOverdue;

    public NoonSalesSurfaceSyncInput(
            NoonSyncScope scope,
            LocalDate latestAvailableSalesDate,
            LocalDate analysisDate,
            List<SalesDataQualityState> qualityStates,
            boolean correctionOverdue
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.latestAvailableSalesDate = latestAvailableSalesDate;
        this.analysisDate = analysisDate;
        this.qualityStates = qualityStates == null ? List.of() : List.copyOf(qualityStates);
        this.correctionOverdue = correctionOverdue;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public LocalDate getLatestAvailableSalesDate() {
        return latestAvailableSalesDate;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public List<SalesDataQualityState> getQualityStates() {
        return qualityStates;
    }

    public boolean isCorrectionOverdue() {
        return correctionOverdue;
    }
}
