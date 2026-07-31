package com.nuono.next.replenishmentplan;

import com.nuono.next.replenishmentplan.ReplenishmentPlanRecords.PlanItemView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReplenishmentPlanOverviewView {
    private final String state;
    private final String storeCode;
    private final String siteCode;
    private final String calculationVersion;
    private final ReplenishmentPlanConfig configSnapshot;
    private final LocalDate anchorDate;
    private final ReplenishmentProductCoverageView coverage;
    private final List<PlanItemView> rows;

    public ReplenishmentPlanOverviewView(
            String state,
            String storeCode,
            String siteCode,
            String calculationVersion,
            ReplenishmentPlanConfig configSnapshot,
            LocalDate anchorDate,
            List<PlanItemView> rows
    ) {
        this(state, storeCode, siteCode, calculationVersion, configSnapshot, anchorDate,
                ReplenishmentProductCoverageView.empty(), rows);
    }

    public ReplenishmentPlanOverviewView(
            String state,
            String storeCode,
            String siteCode,
            String calculationVersion,
            ReplenishmentPlanConfig configSnapshot,
            LocalDate anchorDate,
            ReplenishmentProductCoverageView coverage,
            List<PlanItemView> rows
    ) {
        this.state = state;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.calculationVersion = calculationVersion;
        this.configSnapshot = configSnapshot;
        this.anchorDate = anchorDate;
        this.coverage = coverage == null ? ReplenishmentProductCoverageView.empty() : coverage;
        this.rows = rows == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public String getState() { return state; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getCalculationVersion() { return calculationVersion; }
    public ReplenishmentPlanConfig getConfigSnapshot() { return configSnapshot; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public ReplenishmentProductCoverageView getCoverage() { return coverage; }
    public List<PlanItemView> getRows() { return rows; }
}
