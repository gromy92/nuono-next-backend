package com.nuono.next.salesforecast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesForecastDetailView {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SalesForecastFeatureValuesView featureValues;
    private final SalesForecastFactorBreakdownView factorBreakdown;
    private final List<SalesForecastCalendarFactorImpactView> calendarFactorImpacts;
    private final List<SalesForecastCalendarFactorImpactView> historyCalendarFactorImpacts;
    private final String calculationVersion;
    private final String configVersion;

    public SalesForecastDetailView(
            SalesForecastFeatureValuesView featureValues,
            SalesForecastFactorBreakdownView factorBreakdown,
            List<SalesForecastCalendarFactorImpactView> calendarFactorImpacts,
            List<SalesForecastCalendarFactorImpactView> historyCalendarFactorImpacts,
            String calculationVersion,
            String configVersion
    ) {
        this.featureValues = featureValues;
        this.factorBreakdown = factorBreakdown;
        this.calendarFactorImpacts = calendarFactorImpacts == null ? List.of() : List.copyOf(calendarFactorImpacts);
        this.historyCalendarFactorImpacts = historyCalendarFactorImpacts == null ? List.of() : List.copyOf(historyCalendarFactorImpacts);
        this.calculationVersion = calculationVersion;
        this.configVersion = configVersion;
    }

    public static SalesForecastDetailView fromResult(SalesForecastResultRecord record) {
        return fromResult(record, true);
    }

    public static SalesForecastDetailView fromResult(SalesForecastResultRecord record, boolean includeDailyForecasts) {
        return new SalesForecastDetailView(
                new SalesForecastFeatureValuesView(
                        record.getLatestFactDate(),
                        record.getHistoryUnits7(),
                        record.getHistoryUnits30(),
                        record.getHistoryUnits60(),
                        record.getHistoryUnits90(),
                        decimalField(record.getFeatureSnapshotJson(), "adjustedHistoryUnits7", BigDecimal.valueOf(record.getHistoryUnits7())),
                        decimalField(record.getFeatureSnapshotJson(), "adjustedHistoryUnits30", BigDecimal.valueOf(record.getHistoryUnits30())),
                        decimalField(record.getFeatureSnapshotJson(), "adjustedHistoryUnits60", BigDecimal.valueOf(record.getHistoryUnits60())),
                        decimalField(record.getFeatureSnapshotJson(), "adjustedHistoryUnits90", BigDecimal.valueOf(record.getHistoryUnits90())),
                        record.getObservedDays(),
                        record.getCurrentStock(),
                        record.getStockCoverDays()
                ),
                new SalesForecastFactorBreakdownView(
                        record.getBaseDailySales(),
                        record.getRecentDailyTrendRate(),
                        record.getTrendFactor(),
                        calendarFactor(record.getFeatureSnapshotJson(), "calendarFactor30", record.getFutureFactor()),
                        calendarFactor(record.getFeatureSnapshotJson(), "calendarFactor60", record.getFutureFactor()),
                        calendarFactor(record.getFeatureSnapshotJson(), "calendarFactor90", record.getFutureFactor()),
                        record.getForecastUnits30(),
                        record.getForecastUnits60(),
                        record.getForecastUnits90(),
                        includeDailyForecasts ? dailyForecasts(record.getFeatureSnapshotJson()) : List.of()
                ),
                calendarFactorImpacts(record.getFeatureSnapshotJson(), "calendarFactorImpacts"),
                calendarFactorImpacts(record.getFeatureSnapshotJson(), "historyCalendarFactorImpacts"),
                record.getCalculationVersion(),
                record.getConfigVersion()
        );
    }

    private static BigDecimal calendarFactor(String featureSnapshotJson, String fieldName, BigDecimal fallback) {
        return decimalField(featureSnapshotJson, fieldName, fallback);
    }

    private static BigDecimal decimalField(String featureSnapshotJson, String fieldName, BigDecimal fallback) {
        if (featureSnapshotJson == null || featureSnapshotJson.isBlank()) {
            return fallback;
        }
        try {
            JsonNode value = OBJECT_MAPPER.readTree(featureSnapshotJson).get(fieldName);
            if (value == null || value.isNull() || value.asText().isBlank()) {
                return fallback;
            }
            return new BigDecimal(value.asText());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public SalesForecastFeatureValuesView getFeatureValues() {
        return featureValues;
    }

    public SalesForecastFactorBreakdownView getFactorBreakdown() {
        return factorBreakdown;
    }

    public List<SalesForecastCalendarFactorImpactView> getCalendarFactorImpacts() {
        return calendarFactorImpacts;
    }

    public List<SalesForecastCalendarFactorImpactView> getHistoryCalendarFactorImpacts() {
        return historyCalendarFactorImpacts;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    private static List<SalesForecastCalendarFactorImpactView> calendarFactorImpacts(String featureSnapshotJson, String fieldName) {
        if (featureSnapshotJson == null || featureSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode impacts = OBJECT_MAPPER.readTree(featureSnapshotJson).get(fieldName);
            if (impacts == null || !impacts.isArray()) {
                return List.of();
            }
            List<SalesForecastCalendarFactorImpactView> result = new ArrayList<>();
            for (JsonNode impact : impacts) {
                result.add(new SalesForecastCalendarFactorImpactView(
                        text(impact, "ruleName"),
                        text(impact, "activityType"),
                        date(impact, "dateFrom"),
                        date(impact, "dateTo"),
                        text(impact, "targetScopeType"),
                        text(impact, "targetScopeValue"),
                        decimal(impact, "factorValue"),
                        text(impact, "matchedScopeLabel"),
                        intValue(impact, "affectedDays30"),
                        intValue(impact, "affectedDays60"),
                        intValue(impact, "affectedDays90"),
                        intValue(impact, "affectedDays120")
                ));
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<SalesForecastDailyForecastView> dailyForecasts(String featureSnapshotJson) {
        if (featureSnapshotJson == null || featureSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode forecasts = OBJECT_MAPPER.readTree(featureSnapshotJson).get("dailyForecasts");
            if (forecasts == null || !forecasts.isArray()) {
                return List.of();
            }
            List<SalesForecastDailyForecastView> result = new ArrayList<>();
            for (JsonNode forecast : forecasts) {
                result.add(new SalesForecastDailyForecastView(
                        intValue(forecast, "dayIndex"),
                        date(forecast, "forecastDate"),
                        decimal(forecast, "calendarFactor"),
                        decimal(forecast, "forecastUnits")
                ));
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static LocalDate date(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static int intValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? 0 : value.asInt();
    }
}
