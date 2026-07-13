package com.nuono.next.replenishmentplan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ReplenishmentPlanConfig {

    public static final String CALCULATION_VERSION = "REPLENISHMENT_PLAN_BASIC_V1";
    public static final String DEFAULT_VERSION_NO = "DEFAULT_REPLENISHMENT_PLAN_BASIC_V1";

    private static final List<String> DEFAULT_INVENTORY_SOURCES = Collections.singletonList("FBN");
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
        validatePositive("airLeadDays", airLeadDays);
        validatePositive("airCoverDays", airCoverDays);
        validatePositive("seaLeadDays", seaLeadDays);
        validatePositive("seaCoverDays", seaCoverDays);
        validatePositive("forecastHorizonDays", forecastHorizonDays);
        if (!requireInboundEtaDate) {
            throw new IllegalArgumentException("requireInboundEtaDate must be true for basic V1");
        }
        String resolvedRoundingMode = roundingMode == null ? "ceil" : roundingMode.trim().toLowerCase(Locale.ROOT);
        if (!"ceil".equals(resolvedRoundingMode)) {
            throw new IllegalArgumentException("roundingMode only supports ceil");
        }
        List<String> resolvedInventorySources = normalizeInventorySources(inventorySources);

        this.versionNo = versionNo == null ? DEFAULT_VERSION_NO : versionNo;
        this.airLeadDays = airLeadDays;
        this.airCoverDays = airCoverDays;
        this.seaLeadDays = seaLeadDays;
        this.seaCoverDays = seaCoverDays;
        this.forecastHorizonDays = forecastHorizonDays;
        this.inventorySources = resolvedInventorySources;
        this.requireInboundEtaDate = requireInboundEtaDate;
        this.airEmergencyOnly = airEmergencyOnly;
        this.roundingMode = resolvedRoundingMode;
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

    private static void validatePositive(String fieldName, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be >= 1");
        }
    }

    private static List<String> normalizeInventorySources(List<String> inventorySources) {
        List<String> sources = inventorySources == null ? DEFAULT_INVENTORY_SOURCES : inventorySources;
        List<String> normalized = new ArrayList<>();
        for (String source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("inventorySources only supports FBN");
            }
            String normalizedSource = source.trim().toUpperCase(Locale.ROOT);
            if (!DEFAULT_INVENTORY_SOURCES.contains(normalizedSource)) {
                throw new IllegalArgumentException("inventorySources only supports FBN");
            }
            normalized.add(normalizedSource);
        }
        if (normalized.size() != 1 || !"FBN".equals(normalized.get(0))) {
            throw new IllegalArgumentException("inventorySources must include only FBN for basic V1");
        }
        return DEFAULT_INVENTORY_SOURCES;
    }
}
