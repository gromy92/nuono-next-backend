package com.nuono.next.salesforecast;

import com.nuono.next.operationsconfig.OperationConfigTypedVersionEvidence;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.SalesActivityWindowRecord;
import com.nuono.next.sales.SalesActivityWindowRepository;
import com.nuono.next.sales.SalesActivityWindowScope;
import com.nuono.next.sales.SalesFactRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultSalesForecastService implements SalesForecastService {

    private final SalesFactRepository salesFactRepository;
    private final SalesForecastRunRepository forecastRunRepository;
    private final SalesForecastStockRepository stockRepository;
    private final SalesActivityWindowRepository activityWindowRepository;
    private final SalesForecastFollowUpRepository followUpRepository;
    private final SalesForecastFeatureBuilder featureBuilder;
    private final DefaultSalesForecastEngine forecastEngine;
    private final Clock clock;
    private final OperationConfigTypedVersionRepository typedVersionRepository;

    @Autowired
    public DefaultSalesForecastService(
            SalesFactRepository salesFactRepository,
            SalesForecastRunRepository forecastRunRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            SalesForecastFollowUpRepository followUpRepository,
            SalesForecastFeatureBuilder featureBuilder,
            DefaultSalesForecastEngine forecastEngine,
            ObjectProvider<OperationConfigTypedVersionRepository> typedVersionRepositoryProvider
    ) {
        this(
                salesFactRepository,
                forecastRunRepository,
                stockRepository,
                activityWindowRepository,
                followUpRepository,
                featureBuilder,
                forecastEngine,
                Clock.system(ZoneOffset.UTC),
                typedVersionRepositoryProvider == null ? null : typedVersionRepositoryProvider.getIfAvailable()
        );
    }

    DefaultSalesForecastService(
            SalesFactRepository salesFactRepository,
            SalesForecastRunRepository forecastRunRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            SalesForecastFollowUpRepository followUpRepository,
            SalesForecastFeatureBuilder featureBuilder,
            DefaultSalesForecastEngine forecastEngine,
            Clock clock
    ) {
        this(
                salesFactRepository,
                forecastRunRepository,
                stockRepository,
                activityWindowRepository,
                followUpRepository,
                featureBuilder,
                forecastEngine,
                clock,
                null
        );
    }

    DefaultSalesForecastService(
            SalesFactRepository salesFactRepository,
            SalesForecastRunRepository forecastRunRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            SalesForecastFollowUpRepository followUpRepository,
            SalesForecastFeatureBuilder featureBuilder,
            DefaultSalesForecastEngine forecastEngine,
            Clock clock,
            OperationConfigTypedVersionRepository typedVersionRepository
    ) {
        this.salesFactRepository = salesFactRepository;
        this.forecastRunRepository = forecastRunRepository;
        this.stockRepository = stockRepository;
        this.activityWindowRepository = activityWindowRepository;
        this.followUpRepository = followUpRepository;
        this.featureBuilder = featureBuilder;
        this.forecastEngine = forecastEngine;
        this.clock = clock;
        this.typedVersionRepository = typedVersionRepository;
    }

    @Override
    public SalesForecastOverviewView getOverview(SalesForecastQuery query) {
        LocalDate latestFactDate = salesFactRepository.findLatestFactDate(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        if (latestFactDate == null) {
            return SalesForecastOverviewView.missingSalesData(query.getStoreCode(), query.getSiteCode());
        }

        ActivityImpact activityImpact = resolveActivityImpact(query, latestFactDate);
        OperationConfigTypedVersionEvidence lifecycleVersion = resolveTypedVersion(
                query,
                OperationConfigVersionType.PRODUCT_LIFECYCLE
        );
        SalesForecastRunRecord existingRun = forecastRunRepository.findLatestCompleted(query);
        if (existingRun != null
                && latestFactDate.equals(existingRun.getSourceDataDate())
                && activityImpact.configVersion.equals(existingRun.getConfigVersion())
                && sameVersionEvidence(existingRun, activityImpact, lifecycleVersion)) {
            List<SalesForecastResultRecord> existingResults = forecastRunRepository.listResults(existingRun.getId());
            if (!hasDuplicatePartnerSkuResults(existingResults)) {
                return readyView(query, existingRun, existingResults);
            }
        }

        SalesForecastRunRecord savedRun = calculateAndSaveRun(query, latestFactDate, activityImpact, lifecycleVersion);
        if (savedRun == null) {
            return SalesForecastOverviewView.missingSalesData(query.getStoreCode(), query.getSiteCode());
        }
        return readyView(query, savedRun, forecastRunRepository.listResults(savedRun.getId()));
    }

    @Override
    public SalesForecastRunStatusView recalculate(SalesForecastQuery query) {
        LocalDate latestFactDate = salesFactRepository.findLatestFactDate(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        if (latestFactDate == null) {
            return SalesForecastRunStatusView.failed("当前店铺还没有可用销量事实，无法重算销量预测。");
        }
        try {
            ActivityImpact activityImpact = resolveActivityImpact(query, latestFactDate);
            OperationConfigTypedVersionEvidence lifecycleVersion = resolveTypedVersion(
                    query,
                    OperationConfigVersionType.PRODUCT_LIFECYCLE
            );
            SalesForecastRunRecord savedRun = calculateAndSaveRun(
                    query,
                    latestFactDate,
                    activityImpact,
                    lifecycleVersion
            );
            if (savedRun == null) {
                return SalesForecastRunStatusView.failed("当前店铺销量事实不足，无法生成预测结果。");
            }
            return SalesForecastRunStatusView.succeeded(savedRun);
        } catch (RuntimeException error) {
            return SalesForecastRunStatusView.failed("销量预测重算失败：" + error.getMessage());
        }
    }

    @Override
    public SalesForecastExportView exportCsv(SalesForecastQuery query, SalesForecastExportQuery exportQuery) {
        SalesForecastOverviewView overview = getOverview(query);
        int forecastWindow = normalizedForecastWindow(exportQuery == null ? 30 : exportQuery.getForecastWindow());
        List<SalesForecastOverviewRow> rows = overview.getRows().stream()
                .filter(row -> matchesExportFilter(row, exportQuery))
                .collect(Collectors.toList());
        StringBuilder content = new StringBuilder();
        content.append("storeCode,siteCode,sourceDataDate,calculatedAt,forecastWindow,partnerSku,sku,productTitle,")
                .append("historyUnits7,historyUnits30,historyUnits60,historyUnits90,forecastUnits,")
                .append("currentStock,stockCoverDays,lifecycle,confidence,risks,shortReason,calculationVersion,configVersion\n");
        for (SalesForecastOverviewRow row : rows) {
            appendCsvRow(content, List.of(
                    overview.getStoreCode(),
                    overview.getSiteCode(),
                    String.valueOf(overview.getSourceDataDate()),
                    String.valueOf(overview.getCalculatedAt()),
                    String.valueOf(forecastWindow),
                    row.getPartnerSku(),
                    row.getSku(),
                    safeText(row.getProductTitle()),
                    String.valueOf(row.getHistoryUnits7()),
                    String.valueOf(row.getHistoryUnits30()),
                    String.valueOf(row.getHistoryUnits60()),
                    String.valueOf(row.getHistoryUnits90()),
                    String.valueOf(forecastUnits(row, forecastWindow)),
                    String.valueOf(row.getCurrentStock()),
                    String.valueOf(row.getStockCoverDays()),
                    safeText(row.getLifecycleLabel()),
                    safeText(row.getConfidenceLabel()),
                    row.getRiskLabels().stream().map(SalesForecastRiskLabelView::getLabel).collect(Collectors.joining("|")),
                    safeText(row.getShortReason()),
                    safeText(row.getCalculationVersion()),
                    safeText(row.getConfigVersion())
            ));
        }
        String filename = "sales-forecast-" + query.getStoreCode() + "-" + query.getSiteCode() + ".csv";
        return new SalesForecastExportView(filename, "text/csv;charset=UTF-8", content.toString());
    }

    private SalesForecastRunRecord calculateAndSaveRun(
            SalesForecastQuery query,
            LocalDate latestFactDate,
            ActivityImpact activityImpact,
            OperationConfigTypedVersionEvidence lifecycleVersion
    ) {
        List<DailySalesFact> facts = salesFactRepository.list(new com.nuono.next.sales.SalesFactQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                latestFactDate.minusDays(89),
                latestFactDate,
                null,
                null
        ));
        List<SalesForecastStockSnapshot> stockSnapshots = stockRepository.listCurrentStock(query);
        List<SalesForecastFeatureSnapshot> snapshots = featureBuilder.build(facts, latestFactDate, stockSnapshots);
        if (snapshots.isEmpty()) {
            return null;
        }

        List<SalesForecastResultRecord> resultRecords = new ArrayList<>();
        for (SalesForecastFeatureSnapshot snapshot : snapshots) {
            SalesForecastFormulaResult formulaResult = forecastEngine.forecast30(
                    snapshot,
                    activityImpact.configVersion,
                    activityImpact.futureFactor
            );
            resultRecords.add(toResultRecord(snapshot, formulaResult, latestFactDate, activityImpact, lifecycleVersion));
        }

        SalesForecastRunRecord savedRun = forecastRunRepository.saveRun(SalesForecastRunRecord.succeeded(
                query,
                latestFactDate,
                DefaultSalesForecastEngine.CALCULATION_VERSION,
                activityImpact.configVersion,
                activityImpact.calendarVersion.getVersionNo(),
                activityImpact.calendarVersion.getVersionName(),
                activityImpact.calendarVersion.getSourceLabel(),
                lifecycleVersion.getVersionNo(),
                lifecycleVersion.getVersionName(),
                lifecycleVersion.getSourceLabel(),
                resultRecords.size()
        ));
        forecastRunRepository.saveResults(savedRun.getId(), resultRecords);
        return savedRun;
    }

    private SalesForecastOverviewView readyView(
            SalesForecastQuery query,
            SalesForecastRunRecord run,
            List<SalesForecastResultRecord> results
    ) {
        return SalesForecastOverviewView.ready(
                query.getStoreCode(),
                query.getSiteCode(),
                run,
                results,
                followUpRepository.listMarked(query)
        );
    }

    private boolean sameVersionEvidence(
            SalesForecastRunRecord existingRun,
            ActivityImpact activityImpact,
            OperationConfigTypedVersionEvidence lifecycleVersion
    ) {
        return safeText(activityImpact.calendarVersion.getVersionNo()).equals(safeText(existingRun.getCalendarVersionNo()))
                && safeText(lifecycleVersion.getVersionNo()).equals(safeText(existingRun.getLifecycleVersionNo()));
    }

    private boolean hasDuplicatePartnerSkuResults(List<SalesForecastResultRecord> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        Set<String> seen = new java.util.HashSet<>();
        for (SalesForecastResultRecord result : results) {
            if (result == null) {
                continue;
            }
            String key = safeText(result.getOwnerUserId() == null ? null : String.valueOf(result.getOwnerUserId()))
                    + "|"
                    + safeText(result.getStoreCode())
                    + "|"
                    + safeText(result.getSiteCode())
                    + "|"
                    + safeText(result.getPartnerSku());
            if (!seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExportFilter(SalesForecastOverviewRow row, SalesForecastExportQuery query) {
        if (query == null) {
            return true;
        }
        String keyword = safeText(query.getSearchKeyword()).trim().toLowerCase(Locale.ROOT);
        if (!keyword.isEmpty()) {
            String text = (safeText(row.getProductTitle()) + " " + safeText(row.getPartnerSku()) + " " + safeText(row.getSku()))
                    .toLowerCase(Locale.ROOT);
            if (!text.contains(keyword)) {
                return false;
            }
        }
        String lifecycleFilter = emptyToAll(query.getLifecycleFilter());
        if (!"all".equals(lifecycleFilter) && !lifecycleFilter.equals(row.getLifecycleCode())) {
            return false;
        }
        String riskFilter = emptyToAll(query.getRiskFilter());
        if ("risk".equals(riskFilter) && row.getRiskLabels().isEmpty()) {
            return false;
        }
        if ("none".equals(riskFilter) && !row.getRiskLabels().isEmpty()) {
            return false;
        }
        String confidenceFilter = emptyToAll(query.getConfidenceFilter());
        return "all".equals(confidenceFilter) || confidenceFilter.equals(row.getConfidenceLevel());
    }

    private int normalizedForecastWindow(int forecastWindow) {
        if (forecastWindow == 60 || forecastWindow == 90) {
            return forecastWindow;
        }
        return 30;
    }

    private int forecastUnits(SalesForecastOverviewRow row, int forecastWindow) {
        if (forecastWindow == 60) {
            return row.getForecastUnits60();
        }
        if (forecastWindow == 90) {
            return row.getForecastUnits90();
        }
        return row.getForecastUnits30();
    }

    private String emptyToAll(String value) {
        return safeText(value).isBlank() ? "all" : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private void appendCsvRow(StringBuilder content, List<String> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                content.append(',');
            }
            content.append(csvCell(columns.get(index)));
        }
        content.append('\n');
    }

    private String csvCell(String value) {
        String text = value == null || "null".equals(value) ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    @Override
    public SalesForecastFollowUpView setFollowUp(SalesForecastFollowUpCommand command) {
        return SalesForecastFollowUpView.fromRecord(followUpRepository.setMarked(command));
    }

    private SalesForecastResultRecord toResultRecord(
            SalesForecastFeatureSnapshot snapshot,
            SalesForecastFormulaResult formulaResult,
            LocalDate latestFactDate,
            ActivityImpact activityImpact,
            OperationConfigTypedVersionEvidence lifecycleVersion
    ) {
        RiskAssessment riskAssessment = assessRisk(snapshot, latestFactDate);
        return new SalesForecastResultRecord(
                null,
                null,
                snapshot.getOwnerUserId(),
                snapshot.getStoreCode(),
                snapshot.getSiteCode(),
                snapshot.getPartnerSku(),
                snapshot.getSku(),
                snapshot.getProductTitle(),
                snapshot.getLatestFactDate(),
                snapshot.getHistoryUnits7(),
                snapshot.getHistoryUnits30(),
                snapshot.getHistoryUnits60(),
                snapshot.getHistoryUnits90(),
                snapshot.getObservedDays(),
                snapshot.getCurrentStock(),
                snapshot.getStockCoverDays(),
                formulaResult.getForecastUnits30(),
                formulaResult.getForecastUnits60(),
                formulaResult.getForecastUnits90(),
                snapshot.getLifecycleCode(),
                snapshot.getLifecycleLabel(),
                formulaResult.getCalculationVersion(),
                formulaResult.getConfigVersion(),
                formulaResult.getBaseDailySales(),
                formulaResult.getRecentDailyTrendRate(),
                formulaResult.getTrendFactor(),
                formulaResult.getLifecycleFactor(),
                formulaResult.getFutureFactor(),
                formulaResult.getLifecycleExplanation(),
                riskAssessment.confidenceLevel,
                riskAssessment.confidenceLabel,
                riskAssessment.confidenceExplanation,
                joinCodes(riskAssessment.warningCodes),
                joinCodes(riskAssessment.riskCodes),
                activityImpact.activityWindowSummary,
                activityImpact.activityExplanation,
                formulaResult.getShortReason(),
                featureSnapshotJson(snapshot, activityImpact.calendarVersion, lifecycleVersion)
        );
    }

    private ActivityImpact resolveActivityImpact(SalesForecastQuery query, LocalDate latestFactDate) {
        OperationConfigTypedVersionEvidence calendarVersion = resolveTypedVersion(
                query,
                OperationConfigVersionType.BUSINESS_CALENDAR
        );
        List<SalesActivityWindowRecord> activeWindows = activityWindowRepository.listActive(new SalesActivityWindowScope(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                latestFactDate.plusDays(1),
                latestFactDate.plusDays(90)
        ));
        List<SalesActivityWindowRecord> safeWindows = activeWindows == null
                ? List.of()
                : activeWindows.stream()
                        .filter(SalesActivityWindowRecord::isEnabled)
                        .filter(record -> record.getFactor() != null)
                        .collect(Collectors.toList());
        if (safeWindows.isEmpty()) {
            return new ActivityImpact(
                    BigDecimal.ONE,
                    DefaultSalesForecastEngine.DEFAULT_CONFIG_VERSION,
                    "",
                    "当前预测周期未命中启用活动窗口。",
                    calendarVersion
            );
        }

        BigDecimal futureFactor = safeWindows.stream()
                .map(SalesActivityWindowRecord::getFactor)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);
        int configVersionNo = safeWindows.stream()
                .mapToInt(SalesActivityWindowRecord::getVersionNo)
                .max()
                .orElse(1);
        String operationsConfigBundleVersionNo = safeWindows.stream()
                .map(SalesActivityWindowRecord::getOperationsConfigBundleVersionNo)
                .filter(this::hasText)
                .max(String::compareTo)
                .orElse(null);
        String summary = safeWindows.stream()
                .map(record -> record.getId()
                        + ":" + record.getName()
                        + "(" + record.getFactor().setScale(4, RoundingMode.HALF_UP).toPlainString() + ")"
                        + operationsConfigSuffix(record))
                .collect(Collectors.joining(", "));
        String explanation = "活动因子 "
                + futureFactor.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + "，命中活动：" + summary;
        return new ActivityImpact(
                futureFactor,
                operationsConfigBundleVersionNo == null
                        ? DefaultSalesForecastEngine.DEFAULT_CONFIG_VERSION + "-ACTIVITY-v" + configVersionNo
                        : operationsConfigBundleVersionNo,
                summary,
                explanation,
                calendarVersion
        );
    }

    private OperationConfigTypedVersionEvidence resolveTypedVersion(
            SalesForecastQuery query,
            OperationConfigVersionType configType
    ) {
        return OperationConfigTypedVersionEvidence.resolve(
                typedVersionRepository,
                configType,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
    }

    private String operationsConfigSuffix(SalesActivityWindowRecord record) {
        if (record == null || !hasText(record.getOperationsConfigBundleVersionNo())) {
            return "";
        }
        return "@" + record.getOperationsConfigBundleVersionNo();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private RiskAssessment assessRisk(SalesForecastFeatureSnapshot snapshot, LocalDate latestFactDate) {
        Set<String> warnings = new LinkedHashSet<>(snapshot.getWarningCodes());
        Set<String> risks = new LinkedHashSet<>();

        boolean staleSalesData = latestFactDate.isBefore(LocalDate.now(clock).minusDays(3));
        boolean missingStockData = snapshot.getCurrentStock() == null;
        boolean stockoutDistortion = Integer.valueOf(0).equals(snapshot.getCurrentStock())
                && snapshot.getHistoryUnits30() > 0;
        if (staleSalesData) {
            warnings.add("stale_sales_data");
            risks.add("stale_sales_data");
        }
        if (missingStockData) {
            warnings.add("missing_stock_data");
            risks.add("missing_stock_data");
        }
        if (stockoutDistortion) {
            warnings.add("possible_stockout_distortion");
            risks.add("possible_stockout_distortion");
        }
        if (isLowStockCover(snapshot)) {
            risks.add("replenishment_risk");
        }
        if (isOverstockRisk(snapshot)) {
            risks.add("overstock_risk");
        }

        String confidenceLevel = confidenceLevel(snapshot, staleSalesData, missingStockData, stockoutDistortion);
        String confidenceLabel = confidenceLabel(confidenceLevel);
        String confidenceExplanation = confidenceExplanation(confidenceLevel, snapshot, staleSalesData, missingStockData, stockoutDistortion);
        if ("low".equals(confidenceLevel)) {
            risks.add("low_confidence");
        }
        return new RiskAssessment(
                confidenceLevel,
                confidenceLabel,
                confidenceExplanation,
                List.copyOf(warnings),
                List.copyOf(risks)
        );
    }

    private boolean isLowStockCover(SalesForecastFeatureSnapshot snapshot) {
        return snapshot.getStockCoverDays() != null
                && snapshot.getHistoryUnits30() > 0
                && snapshot.getStockCoverDays().compareTo(new BigDecimal("14.0")) < 0;
    }

    private boolean isOverstockRisk(SalesForecastFeatureSnapshot snapshot) {
        return snapshot.getStockCoverDays() != null
                && snapshot.getStockCoverDays().compareTo(new BigDecimal("120.0")) >= 0
                && ("decline".equals(snapshot.getLifecycleCode()) || "longTail".equals(snapshot.getLifecycleCode()));
    }

    private String confidenceLevel(
            SalesForecastFeatureSnapshot snapshot,
            boolean staleSalesData,
            boolean missingStockData,
            boolean stockoutDistortion
    ) {
        if (stockoutDistortion || snapshot.getObservedDays() < 30) {
            return "low";
        }
        if (staleSalesData || missingStockData || snapshot.getObservedDays() < 60) {
            return "medium";
        }
        return "high";
    }

    private String confidenceLabel(String confidenceLevel) {
        if ("high".equals(confidenceLevel)) {
            return "高";
        }
        if ("medium".equals(confidenceLevel)) {
            return "中";
        }
        return "低";
    }

    private String confidenceExplanation(
            String confidenceLevel,
            SalesForecastFeatureSnapshot snapshot,
            boolean staleSalesData,
            boolean missingStockData,
            boolean stockoutDistortion
    ) {
        if (stockoutDistortion) {
            return "当前库存为 0 且近 30 天有销量，预测可能被断货压低，置信度低。";
        }
        if (snapshot.getObservedDays() < 30) {
            return "可用销量样本少于 30 天，预测需人工复核。";
        }
        if (staleSalesData) {
            return "最新销量数据过期，预测置信度降为" + confidenceLabel(confidenceLevel) + "。";
        }
        if (missingStockData) {
            return "缺少当前库存投影，预测置信度降为" + confidenceLabel(confidenceLevel) + "。";
        }
        return "销量样本和库存投影满足 P0 预测要求，置信度" + confidenceLabel(confidenceLevel) + "。";
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        return String.join(",", codes);
    }

    private String featureSnapshotJson(
            SalesForecastFeatureSnapshot snapshot,
            OperationConfigTypedVersionEvidence calendarVersion,
            OperationConfigTypedVersionEvidence lifecycleVersion
    ) {
        return "{"
                + "\"latestFactDate\":\"" + snapshot.getLatestFactDate() + "\","
                + "\"historyUnits7\":" + snapshot.getHistoryUnits7() + ","
                + "\"historyUnits30\":" + snapshot.getHistoryUnits30() + ","
                + "\"historyUnits60\":" + snapshot.getHistoryUnits60() + ","
                + "\"historyUnits90\":" + snapshot.getHistoryUnits90() + ","
                + "\"observedDays\":" + snapshot.getObservedDays() + ","
                + "\"currentStock\":" + numberOrNull(snapshot.getCurrentStock()) + ","
                + "\"stockCoverDays\":" + numberOrNull(snapshot.getStockCoverDays()) + ","
                + "\"lifecycleCode\":\"" + jsonText(snapshot.getLifecycleCode()) + "\","
                + "\"lifecycleRuleVersion\":\"" + jsonText(snapshot.getLifecycleRuleVersion()) + "\","
                + "\"lifecycleQualityState\":\"" + jsonText(snapshot.getLifecycleQualityState()) + "\","
                + "\"lifecycleEvidence\":" + jsonObjectOrNull(snapshot.getLifecycleEvidenceJson()) + ","
                + "\"calendarVersionNo\":\"" + jsonText(calendarVersion.getVersionNo()) + "\","
                + "\"calendarVersionName\":\"" + jsonText(calendarVersion.getVersionName()) + "\","
                + "\"calendarVersionSourceLabel\":\"" + jsonText(calendarVersion.getSourceLabel()) + "\","
                + "\"lifecycleVersionNo\":\"" + jsonText(lifecycleVersion.getVersionNo()) + "\","
                + "\"lifecycleVersionName\":\"" + jsonText(lifecycleVersion.getVersionName()) + "\","
                + "\"lifecycleVersionSourceLabel\":\"" + jsonText(lifecycleVersion.getSourceLabel()) + "\""
                + "}";
    }

    private String numberOrNull(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String jsonObjectOrNull(String value) {
        if (value == null || value.isBlank()) {
            return "null";
        }
        return value;
    }

    private String jsonText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class RiskAssessment {
        private final String confidenceLevel;
        private final String confidenceLabel;
        private final String confidenceExplanation;
        private final List<String> warningCodes;
        private final List<String> riskCodes;

        private RiskAssessment(
                String confidenceLevel,
                String confidenceLabel,
                String confidenceExplanation,
                List<String> warningCodes,
                List<String> riskCodes
        ) {
            this.confidenceLevel = confidenceLevel;
            this.confidenceLabel = confidenceLabel;
            this.confidenceExplanation = confidenceExplanation;
            this.warningCodes = warningCodes;
            this.riskCodes = riskCodes;
        }
    }

    private static class ActivityImpact {
        private final BigDecimal futureFactor;
        private final String configVersion;
        private final String activityWindowSummary;
        private final String activityExplanation;
        private final OperationConfigTypedVersionEvidence calendarVersion;

        private ActivityImpact(
                BigDecimal futureFactor,
                String configVersion,
                String activityWindowSummary,
                String activityExplanation,
                OperationConfigTypedVersionEvidence calendarVersion
        ) {
            this.futureFactor = futureFactor;
            this.configVersion = configVersion;
            this.activityWindowSummary = activityWindowSummary;
            this.activityExplanation = activityExplanation;
            this.calendarVersion = calendarVersion;
        }
    }
}
