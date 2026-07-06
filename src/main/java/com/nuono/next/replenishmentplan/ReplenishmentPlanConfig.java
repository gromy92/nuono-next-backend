package com.nuono.next.replenishmentplan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ReplenishmentPlanConfig {

    public static final String CALCULATION_VERSION = "REPLENISHMENT_PLAN_BASIC_V1";
    public static final String DEFAULT_VERSION_NO = "DEFAULT_REPLENISHMENT_PLAN_BASIC_V1";

    private static final List<String> DEFAULT_INVENTORY_SOURCES = Collections.unmodifiableList(Arrays.asList("FBN", "SUPERMALL"));
    private static final ReplenishmentPlanConfig DEFAULT_BASIC_V1 = new ReplenishmentPlanConfig(
            DEFAULT_VERSION_NO,
            12,
            15,
            70,
            30,
            100,
            DEFAULT_INVENTORY_SOURCES,
            true,
            true,
            "ceil"
    );

    private final String versionNo;
    private final int airLeadDays;
    private final int airCoverDays;
    private final int seaLeadDays;
    private final int seaCoverDays;
    private final int forecastHorizonDays;
    private final List<String> inventorySources;
    private final boolean requireInboundEtaDate;
    private final boolean airEmergencyOnly;
    private final String roundingMode;

    public ReplenishmentPlanConfig(
            String versionNo,
            int airLeadDays,
            int airCoverDays,
            int seaLeadDays,
            int seaCoverDays,
            int forecastHorizonDays,
            List<String> inventorySources,
            boolean requireInboundEtaDate,
            boolean airEmergencyOnly,
            String roundingMode
    ) {
        this.versionNo = versionNo == null ? DEFAULT_VERSION_NO : versionNo;
        this.airLeadDays = airLeadDays;
        this.airCoverDays = airCoverDays;
        this.seaLeadDays = seaLeadDays;
        this.seaCoverDays = seaCoverDays;
        this.forecastHorizonDays = forecastHorizonDays;
        List<String> sources = inventorySources == null ? DEFAULT_INVENTORY_SOURCES : inventorySources;
        this.inventorySources = Collections.unmodifiableList(new ArrayList<>(sources));
        this.requireInboundEtaDate = requireInboundEtaDate;
        this.airEmergencyOnly = airEmergencyOnly;
        this.roundingMode = roundingMode == null ? "ceil" : roundingMode;
    }

    public static ReplenishmentPlanConfig defaultBasicV1() {
        return DEFAULT_BASIC_V1;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public int getAirLeadDays() {
        return airLeadDays;
    }

    public int getAirCoverDays() {
        return airCoverDays;
    }

    public int getSeaLeadDays() {
        return seaLeadDays;
    }

    public int getSeaCoverDays() {
        return seaCoverDays;
    }

    public int getForecastHorizonDays() {
        return forecastHorizonDays;
    }

    public List<String> getInventorySources() {
        return inventorySources;
    }

    public boolean isRequireInboundEtaDate() {
        return requireInboundEtaDate;
    }

    public boolean isAirEmergencyOnly() {
        return airEmergencyOnly;
    }

    public String getRoundingMode() {
        return roundingMode;
    }
}
