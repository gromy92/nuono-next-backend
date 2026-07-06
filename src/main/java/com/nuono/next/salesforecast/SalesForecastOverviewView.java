package com.nuono.next.salesforecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SalesForecastOverviewView {

    private final String state;
    private final Long runId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate sourceDataDate;
    private final LocalDateTime calculatedAt;
    private final String calculationVersion;
    private final String configVersion;
    private final SalesForecastEmptyState emptyState;
    private final List<SalesForecastOverviewRow> rows;

    public SalesForecastOverviewView(
            String state,
            Long runId,
            String storeCode,
            String siteCode,
            LocalDate sourceDataDate,
            LocalDateTime calculatedAt,
            String calculationVersion,
            String configVersion,
            SalesForecastEmptyState emptyState,
            List<SalesForecastOverviewRow> rows
    ) {
        this.state = state;
        this.runId = runId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.sourceDataDate = sourceDataDate;
        this.calculatedAt = calculatedAt;
        this.calculationVersion = calculationVersion;
        this.configVersion = configVersion;
        this.emptyState = emptyState;
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static SalesForecastOverviewView empty(String storeCode, String siteCode) {
        return empty(
                storeCode,
                siteCode,
                "no_forecast_run",
                "暂无销量预测结果",
                "当前店铺还没有预测运行结果。后续可在完成预测计算后查看 30/60/90 天预测。"
        );
    }

    public static SalesForecastOverviewView missingSalesData(String storeCode, String siteCode) {
        return empty(
                storeCode,
                siteCode,
                "missing_sales_data",
                "暂无销量预测结果",
                "当前店铺还没有可用销量事实。请先完成 Noon 销量数据同步或导入。"
        );
    }

    public static SalesForecastOverviewView ready(
            String storeCode,
            String siteCode,
            SalesForecastRunRecord run,
            List<SalesForecastResultRecord> results
    ) {
        return ready(storeCode, siteCode, run, results, List.of());
    }

    public static SalesForecastOverviewView ready(
            String storeCode,
            String siteCode,
            SalesForecastRunRecord run,
            List<SalesForecastResultRecord> results,
            List<SalesForecastFollowUpRecord> followUps
    ) {
        List<SalesForecastResultRecord> safeResults = results == null ? List.of() : results;
        Set<String> markedKeys = followUps == null
                ? Set.of()
                : followUps.stream()
                        .filter(SalesForecastFollowUpRecord::isMarked)
                        .map(record -> productKey(record.getPartnerSku()))
                        .collect(Collectors.toCollection(HashSet::new));
        return new SalesForecastOverviewView(
                "ready",
                run.getId(),
                storeCode,
                siteCode,
                run.getSourceDataDate(),
                run.getCalculatedAt(),
                run.getCalculationVersion(),
                run.getConfigVersion(),
                null,
                safeResults.stream()
                        .map(record -> SalesForecastOverviewRow.fromResult(
                                record,
                                markedKeys.contains(productKey(record.getPartnerSku()))
                        ))
                        .collect(Collectors.toList())
        );
    }

    private static String productKey(String partnerSku) {
        return partnerSku == null ? "" : partnerSku.trim().toUpperCase(Locale.ROOT);
    }

    private static SalesForecastOverviewView empty(
            String storeCode,
            String siteCode,
            String code,
            String title,
            String description
    ) {
        return new SalesForecastOverviewView(
                "empty",
                null,
                storeCode,
                siteCode,
                null,
                null,
                null,
                null,
                new SalesForecastEmptyState(code, title, description),
                List.of()
        );
    }

    public String getState() {
        return state;
    }

    public Long getRunId() {
        return runId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getSourceDataDate() {
        return sourceDataDate;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public SalesForecastEmptyState getEmptyState() {
        return emptyState;
    }

    public List<SalesForecastOverviewRow> getRows() {
        return rows;
    }
}
