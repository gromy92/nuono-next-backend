package com.nuono.next.salesforecast;

import com.nuono.next.operationsconfig.OperationConfigTypedVersionEvidence;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import com.nuono.next.operationsconfig.OperationBusinessCalendarFactorResolver;
import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.SalesActivityWindowRepository;
import com.nuono.next.sales.SalesFactRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultSalesForecastService implements SalesForecastService {

    private static final BigDecimal LOW_SAMPLE_FAMILY_FLOOR_RATIO = new BigDecimal("0.10");

    private final SalesFactRepository salesFactRepository;
    private final SalesForecastRunRepository forecastRunRepository;
    private final SalesForecastStockRepository stockRepository;
    private final SalesForecastFollowUpRepository followUpRepository;
    private final SalesForecastFeatureBuilder featureBuilder;
    private final DefaultSalesForecastEngine forecastEngine;
    private final Clock clock;
    private final OperationConfigTypedVersionRepository typedVersionRepository;
    private final OperationBusinessCalendarFactorResolver calendarFactorResolver;
    private final SalesForecastHistoricalStockRepository historicalStockRepository;

    @Autowired
    public DefaultSalesForecastService(
            SalesFactRepository salesFactRepository,
            SalesForecastRunRepository forecastRunRepository,
            SalesForecastStockRepository stockRepository,
            SalesActivityWindowRepository activityWindowRepository,
            SalesForecastFollowUpRepository followUpRepository,
            SalesForecastFeatureBuilder featureBuilder,
            DefaultSalesForecastEngine forecastEngine,
            ObjectProvider<OperationConfigTypedVersionRepository> typedVersionRepositoryProvider,
            ObjectProvider<OperationBusinessCalendarFactorResolver> calendarFactorResolverProvider,
            ObjectProvider<SalesForecastHistoricalStockRepository> historicalStockRepositoryProvider
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
                typedVersionRepositoryProvider == null ? null : typedVersionRepositoryProvider.getIfAvailable(),
                calendarFactorResolverProvider == null ? null : calendarFactorResolverProvider.getIfAvailable(),
                historicalStockRepositoryProvider == null ? null : historicalStockRepositoryProvider.getIfAvailable()
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
                null,
                null,
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
        this(
                salesFactRepository,
                forecastRunRepository,
                stockRepository,
                activityWindowRepository,
                followUpRepository,
                featureBuilder,
                forecastEngine,
                clock,
                typedVersionRepository,
                null,
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
            OperationConfigTypedVersionRepository typedVersionRepository,
            OperationBusinessCalendarFactorResolver calendarFactorResolver
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
                typedVersionRepository,
                calendarFactorResolver,
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
            OperationConfigTypedVersionRepository typedVersionRepository,
            OperationBusinessCalendarFactorResolver calendarFactorResolver,
            SalesForecastHistoricalStockRepository historicalStockRepository
    ) {
        this.salesFactRepository = salesFactRepository;
        this.forecastRunRepository = forecastRunRepository;
        this.stockRepository = stockRepository;
        this.followUpRepository = followUpRepository;
        this.featureBuilder = featureBuilder;
        this.forecastEngine = forecastEngine;
        this.clock = clock;
        this.typedVersionRepository = typedVersionRepository;
        this.calendarFactorResolver = calendarFactorResolver == null && typedVersionRepository != null
                ? new OperationBusinessCalendarFactorResolver(typedVersionRepository)
                : calendarFactorResolver;
        this.historicalStockRepository = historicalStockRepository;
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

        ActivityImpact activityImpact = resolveActivityImpact(query);
        List<SalesForecastStockSnapshot> stockSnapshots = stockRepository.listCurrentStock(query);
        SalesForecastRunRecord existingRun = forecastRunRepository.findLatestCompleted(query);
        if (existingRun != null
                && latestFactDate.equals(existingRun.getSourceDataDate())
                && activityImpact.configVersion.equals(existingRun.getConfigVersion())
                && sameVersionEvidence(existingRun, activityImpact)) {
            List<SalesForecastResultRecord> existingResults = forecastRunRepository.listResults(existingRun.getId());
            if (!hasDuplicatePartnerSkuResults(existingResults)
                    && !hasCurrentProductBoundaryMismatch(existingResults, stockSnapshots)) {
                return readyView(query, existingRun, existingResults);
            }
        }

        SalesForecastRunRecord savedRun = calculateAndSaveRun(query, latestFactDate, activityImpact, stockSnapshots);
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
            ActivityImpact activityImpact = resolveActivityImpact(query);
            SalesForecastRunRecord savedRun = calculateAndSaveRun(
                    query,
                    latestFactDate,
                    activityImpact,
                    stockRepository.listCurrentStock(query)
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
                .append("currentStock,stockCoverDays,confidence,risks,shortReason,calculationVersion,configVersion\n");
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
            List<SalesForecastStockSnapshot> stockSnapshots
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
        List<SalesForecastHistoricalStockSnapshot> historicalStockSnapshots = historicalStockSnapshots(
                query,
                latestFactDate.minusDays(89),
                latestFactDate,
                stockSnapshots
        );
        List<SalesForecastFeatureSnapshot> snapshots = featureBuilder.build(
                facts,
                latestFactDate,
                stockSnapshots,
                this::historyDailyFactors,
                historicalStockSnapshots
        );
        if (snapshots.isEmpty()) {
            return null;
        }
        Map<String, BigDecimal> lowSampleDailyFloorByProduct = lowSampleDailyFloorByProduct(snapshots);

        List<SalesForecastResultRecord> resultRecords = new ArrayList<>();
        for (SalesForecastFeatureSnapshot snapshot : snapshots) {
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation historyCalendarFactorExplanation =
                    historyCalendarFactorExplanation(snapshot, latestFactDate);
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation calendarFactorExplanation =
                    calendarFactorExplanation(snapshot, query, latestFactDate);
            BigDecimal calendarFactor30 = calendarFactorExplanation.averageFactor(30);
            BigDecimal calendarFactor60 = calendarFactorExplanation.averageFactor(60);
            BigDecimal calendarFactor90 = calendarFactorExplanation.averageFactor(90);
            SalesForecastFormulaResult formulaResult = forecastEngine.forecast(
                    snapshot,
                    activityImpact.configVersion,
                    calendarFactor30,
                    calendarFactor60,
                    calendarFactor90,
                    lowSampleDailyFloorByProduct.getOrDefault(productKey(snapshot.getPartnerSku()), BigDecimal.ZERO)
            );
            resultRecords.add(toResultRecord(
                    snapshot,
                    formulaResult,
                    latestFactDate,
                    activityImpact,
                    calendarFactorExplanation,
                    historyCalendarFactorExplanation
            ));
        }

        SalesForecastRunRecord savedRun = forecastRunRepository.saveRunWithResults(SalesForecastRunRecord.succeeded(
                query,
                latestFactDate,
                DefaultSalesForecastEngine.CALCULATION_VERSION,
                activityImpact.configVersion,
                activityImpact.calendarVersion.getVersionNo(),
                activityImpact.calendarVersion.getVersionName(),
                activityImpact.calendarVersion.getSourceLabel(),
                resultRecords.size()
        ), resultRecords);
        return savedRun;
    }

    private Map<String, BigDecimal> lowSampleDailyFloorByProduct(List<SalesForecastFeatureSnapshot> snapshots) {
        Map<String, List<BigDecimal>> exactDailyValues = new HashMap<>();
        Map<String, List<BigDecimal>> familyDailyValues = new HashMap<>();
        for (SalesForecastFeatureSnapshot snapshot : snapshots) {
            if (snapshot.getHistoryUnits30() < 10 || snapshot.getAdjustedHistoryUnits30().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal daily30 = snapshot.getAdjustedHistoryUnits30()
                    .divide(new BigDecimal("30"), 8, RoundingMode.HALF_UP);
            String familyKey = familyKey(snapshot);
            if (hasText(familyKey)) {
                familyDailyValues.computeIfAbsent(familyKey, ignored -> new ArrayList<>()).add(daily30);
            }
            String exactKey = exactCategoryKey(snapshot);
            if (hasText(exactKey)) {
                exactDailyValues.computeIfAbsent(exactKey, ignored -> new ArrayList<>()).add(daily30);
            }
        }
        Map<String, BigDecimal> result = new HashMap<>();
        for (SalesForecastFeatureSnapshot snapshot : snapshots) {
            if (!isLowSampleSnapshot(snapshot)) {
                continue;
            }
            BigDecimal median = median(exactDailyValues.get(exactCategoryKey(snapshot)));
            if (median == null) {
                median = median(familyDailyValues.get(familyKey(snapshot)));
            }
            if (median == null) {
                continue;
            }
            result.put(
                    productKey(snapshot.getPartnerSku()),
                    median.multiply(LOW_SAMPLE_FAMILY_FLOOR_RATIO).setScale(8, RoundingMode.HALF_UP)
            );
        }
        return result;
    }

    private boolean isLowSampleSnapshot(SalesForecastFeatureSnapshot snapshot) {
        return snapshot.getObservedDays() < 30 || snapshot.getHistoryUnits30() <= 10;
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1)
                .add(sorted.get(middle))
                .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
    }

    private String exactCategoryKey(SalesForecastFeatureSnapshot snapshot) {
        String family = familyKey(snapshot);
        String fulltype = safeText(snapshot.getProductFulltype()).trim().toLowerCase(Locale.ROOT);
        if (!hasText(family) || !hasText(fulltype)) {
            return "";
        }
        return family + "|" + fulltype;
    }

    private String familyKey(SalesForecastFeatureSnapshot snapshot) {
        return safeText(snapshot.getProductFamily()).trim().toLowerCase(Locale.ROOT);
    }

    private List<SalesForecastHistoricalStockSnapshot> historicalStockSnapshots(
            SalesForecastQuery query,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<SalesForecastStockSnapshot> stockSnapshots
    ) {
        if (historicalStockRepository == null) {
            return List.of();
        }
        List<String> partnerSkus = stockSnapshots == null ? List.of() : stockSnapshots.stream()
                .map(SalesForecastStockSnapshot::getPartnerSku)
                .filter(this::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
        if (partnerSkus.isEmpty()) {
            return List.of();
        }
        return historicalStockRepository.listHistoricalStock(query, dateFrom, dateTo, partnerSkus);
    }

    private OperationBusinessCalendarFactorResolver.CalendarFactorExplanation calendarFactorExplanation(
            SalesForecastFeatureSnapshot snapshot,
            SalesForecastQuery query,
            LocalDate latestFactDate
    ) {
        if (calendarFactorResolver == null) {
            return OperationBusinessCalendarFactorResolver.CalendarFactorExplanation.neutral(90);
        }
        return calendarFactorResolver.explainFactors(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                latestFactDate,
                90,
                new OperationBusinessCalendarFactorResolver.ProductScope(
                        snapshot.getSiteCode(),
                        snapshot.getBrand(),
                        snapshot.getProductFulltype(),
                        snapshot.getProductFamily()
                )
        );
    }

    private List<BigDecimal> historyDailyFactors(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String brand,
            String productFulltype,
            String productFamily
    ) {
        if (calendarFactorResolver == null) {
            return List.of();
        }
        return calendarFactorResolver.explainFactorsForDateRange(
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                new OperationBusinessCalendarFactorResolver.ProductScope(
                        siteCode,
                        brand,
                        productFulltype,
                        productFamily
                )
        ).getDailyFactors();
    }

    private OperationBusinessCalendarFactorResolver.CalendarFactorExplanation historyCalendarFactorExplanation(
            SalesForecastFeatureSnapshot snapshot,
            LocalDate latestFactDate
    ) {
        if (calendarFactorResolver == null || latestFactDate == null) {
            return OperationBusinessCalendarFactorResolver.CalendarFactorExplanation.neutral(90);
        }
        return calendarFactorResolver.explainFactorsForDateRange(
                snapshot.getOwnerUserId(),
                snapshot.getStoreCode(),
                snapshot.getSiteCode(),
                latestFactDate.minusDays(89),
                latestFactDate,
                new OperationBusinessCalendarFactorResolver.ProductScope(
                        snapshot.getSiteCode(),
                        snapshot.getBrand(),
                        snapshot.getProductFulltype(),
                        snapshot.getProductFamily()
                )
        );
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
            ActivityImpact activityImpact
    ) {
        return DefaultSalesForecastEngine.CALCULATION_VERSION.equals(safeText(existingRun.getCalculationVersion()))
                && safeText(activityImpact.calendarVersion.getVersionNo()).equals(safeText(existingRun.getCalendarVersionNo()));
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

    private boolean hasCurrentProductBoundaryMismatch(
            List<SalesForecastResultRecord> results,
            List<SalesForecastStockSnapshot> stockSnapshots
    ) {
        if (stockSnapshots == null || stockSnapshots.isEmpty()) {
            return false;
        }
        Set<String> currentProductKeys = stockSnapshots.stream()
                .map(SalesForecastStockSnapshot::getPartnerSku)
                .filter(this::hasText)
                .map(this::productKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (currentProductKeys.isEmpty()) {
            return false;
        }
        Set<String> resultProductKeys = results == null
                ? Set.of()
                : results.stream()
                        .map(SalesForecastResultRecord::getPartnerSku)
                        .filter(this::hasText)
                        .map(this::productKey)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return !resultProductKeys.equals(currentProductKeys);
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

    private String productKey(String value) {
        return safeText(value).trim().toUpperCase(Locale.ROOT);
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
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation calendarFactorExplanation,
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation historyCalendarFactorExplanation
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
                formulaResult.getCalculationVersion(),
                formulaResult.getConfigVersion(),
                formulaResult.getBaseDailySales(),
                formulaResult.getRecentDailyTrendRate(),
                formulaResult.getTrendFactor(),
                formulaResult.getFutureFactor(),
                riskAssessment.confidenceLevel,
                riskAssessment.confidenceLabel,
                riskAssessment.confidenceExplanation,
                joinCodes(riskAssessment.warningCodes),
                joinCodes(riskAssessment.riskCodes),
                activityImpact.activityWindowSummary,
                activityImpact.activityExplanation,
                formulaResult.getShortReason(),
                featureSnapshotJson(
                        snapshot,
                        formulaResult,
                        activityImpact.calendarVersion,
                        calendarFactorExplanation,
                        historyCalendarFactorExplanation
                )
        );
    }

    private ActivityImpact resolveActivityImpact(SalesForecastQuery query) {
        OperationConfigTypedVersionEvidence calendarVersion = resolveTypedVersion(
                query,
                OperationConfigVersionType.BUSINESS_CALENDAR
        );
        return new ActivityImpact(
                hasText(calendarVersion.getVersionNo())
                        ? calendarVersion.getVersionNo()
                        : DefaultSalesForecastEngine.DEFAULT_CONFIG_VERSION,
                "",
                "业务日历因子来自运营配置版本：" + safeText(calendarVersion.getVersionName()),
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private RiskAssessment assessRisk(SalesForecastFeatureSnapshot snapshot, LocalDate latestFactDate) {
        Set<String> warnings = new LinkedHashSet<>(snapshot.getWarningCodes());
        Set<String> risks = new LinkedHashSet<>();

        boolean noSalesTrainingData = snapshot.getObservedDays() <= 0 || warnings.contains("no_sales_training_data");
        boolean staleSalesData = latestFactDate.isBefore(LocalDate.now(clock).minusDays(3));
        if (noSalesTrainingData) {
            warnings.add("no_sales_training_data");
            risks.add("no_sales_training_data");
        }
        if (staleSalesData) {
            warnings.add("stale_sales_data");
            risks.add("stale_sales_data");
        }

        String confidenceLevel = confidenceLevel(snapshot, noSalesTrainingData, staleSalesData);
        String confidenceLabel = confidenceLabel(confidenceLevel);
        String confidenceExplanation = confidenceExplanation(confidenceLevel, snapshot, noSalesTrainingData, staleSalesData);
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

    private String confidenceLevel(
            SalesForecastFeatureSnapshot snapshot,
            boolean noSalesTrainingData,
            boolean staleSalesData
    ) {
        if (noSalesTrainingData || snapshot.getObservedDays() < 30) {
            return "low";
        }
        if (staleSalesData || snapshot.getObservedDays() < 60) {
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
            boolean noSalesTrainingData,
            boolean staleSalesData
    ) {
        if (noSalesTrainingData) {
            return "截至当前数据日没有自身销量训练样本，预测只能依赖同类目兜底或按 0 处理，需人工复核。";
        }
        if (snapshot.getObservedDays() < 30) {
            return "可用销量样本少于 30 天，预测需人工复核。";
        }
        if (staleSalesData) {
            return "最新销量数据过期，预测置信度降为" + confidenceLabel(confidenceLevel) + "。";
        }
        return "销量样本满足 P0 预测要求，置信度" + confidenceLabel(confidenceLevel) + "。";
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        return String.join(",", codes);
    }

    private String featureSnapshotJson(
            SalesForecastFeatureSnapshot snapshot,
            SalesForecastFormulaResult formulaResult,
            OperationConfigTypedVersionEvidence calendarVersion,
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation calendarFactorExplanation,
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation historyCalendarFactorExplanation
    ) {
        return "{"
                + "\"latestFactDate\":\"" + snapshot.getLatestFactDate() + "\","
                + "\"historyUnits7\":" + snapshot.getHistoryUnits7() + ","
                + "\"historyUnits30\":" + snapshot.getHistoryUnits30() + ","
                + "\"historyUnits60\":" + snapshot.getHistoryUnits60() + ","
                + "\"historyUnits90\":" + snapshot.getHistoryUnits90() + ","
                + "\"adjustedHistoryUnits7\":\"" + decimalText(snapshot.getAdjustedHistoryUnits7()) + "\","
                + "\"adjustedHistoryUnits30\":\"" + decimalText(snapshot.getAdjustedHistoryUnits30()) + "\","
                + "\"adjustedHistoryUnits60\":\"" + decimalText(snapshot.getAdjustedHistoryUnits60()) + "\","
                + "\"adjustedHistoryUnits90\":\"" + decimalText(snapshot.getAdjustedHistoryUnits90()) + "\","
                + "\"observedDays\":" + snapshot.getObservedDays() + ","
                + "\"currentStock\":" + numberOrNull(snapshot.getCurrentStock()) + ","
                + "\"stockCoverDays\":" + numberOrNull(snapshot.getStockCoverDays()) + ","
                + "\"calendarVersionNo\":\"" + jsonText(calendarVersion.getVersionNo()) + "\","
                + "\"calendarVersionName\":\"" + jsonText(calendarVersion.getVersionName()) + "\","
                + "\"calendarVersionSourceLabel\":\"" + jsonText(calendarVersion.getSourceLabel()) + "\","
                + "\"calendarFactor30\":\"" + decimalText(formulaResult.getFutureFactor30()) + "\","
                + "\"calendarFactor60\":\"" + decimalText(formulaResult.getFutureFactor60()) + "\","
                + "\"calendarFactor90\":\"" + decimalText(formulaResult.getFutureFactor90()) + "\","
                + "\"lowSampleDailyFloor\":\"" + decimalText(formulaResult.getLowSampleDailyFloor()) + "\","
                + "\"calendarFactorImpacts\":" + calendarFactorImpactsJson(calendarFactorExplanation) + ","
                + "\"historyCalendarFactorImpacts\":" + calendarFactorImpactsJson(historyCalendarFactorExplanation)
                + "}";
    }

    private String calendarFactorImpactsJson(
            OperationBusinessCalendarFactorResolver.CalendarFactorExplanation calendarFactorExplanation
    ) {
        if (calendarFactorExplanation == null || calendarFactorExplanation.getImpacts().isEmpty()) {
            return "[]";
        }
        return calendarFactorExplanation.getImpacts().stream()
                .map(impact -> "{"
                        + "\"ruleName\":\"" + jsonText(impact.getRuleName()) + "\","
                        + "\"activityType\":\"" + jsonText(impact.getActivityType()) + "\","
                        + "\"dateFrom\":\"" + impact.getDateFrom() + "\","
                        + "\"dateTo\":\"" + impact.getDateTo() + "\","
                        + "\"targetScopeType\":\"" + jsonText(impact.getTargetScopeType()) + "\","
                        + "\"targetScopeValue\":\"" + jsonText(impact.getTargetScopeValue()) + "\","
                        + "\"factorValue\":\"" + impact.getFactorValue().setScale(4, RoundingMode.HALF_UP).toPlainString() + "\","
                        + "\"matchedScopeLabel\":\"" + jsonText(impact.getMatchedScopeLabel()) + "\","
                        + "\"affectedDays30\":" + impact.getAffectedDays30() + ","
                        + "\"affectedDays60\":" + impact.getAffectedDays60() + ","
                        + "\"affectedDays90\":" + impact.getAffectedDays90()
                        + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String numberOrNull(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String decimalText(BigDecimal value) {
        return value == null ? "" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
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
        private final String configVersion;
        private final String activityWindowSummary;
        private final String activityExplanation;
        private final OperationConfigTypedVersionEvidence calendarVersion;

        private ActivityImpact(
                String configVersion,
                String activityWindowSummary,
                String activityExplanation,
                OperationConfigTypedVersionEvidence calendarVersion
        ) {
            this.configVersion = configVersion;
            this.activityWindowSummary = activityWindowSummary;
            this.activityExplanation = activityExplanation;
            this.calendarVersion = calendarVersion;
        }
    }
}
