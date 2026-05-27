package com.nuono.next.sales;

import com.nuono.next.noonsync.NoonBusinessSyncStatusService;
import com.nuono.next.noonsync.NoonSalesSurfaceSyncInput;
import com.nuono.next.noonsync.NoonSalesSyncReadModel;
import com.nuono.next.noonsync.NoonSyncScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SalesAnalyticsService {

    private final SalesFactRepository salesFactRepository;
    private final ProductLifecycleClassifier lifecycleClassifier;
    private final NoonBusinessSyncStatusService syncStatusService;
    private final ProductLifecycleStateRepository lifecycleStateRepository;
    private final SalesProductDimensionRepository productDimensionRepository;
    private final SalesPriceTrendRepository priceTrendRepository;
    private final SalesHistoryBackfillService historyBackfillService;

    @Autowired
    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ObjectProvider<NoonBusinessSyncStatusService> syncStatusServiceProvider,
            ObjectProvider<ProductLifecycleStateRepository> lifecycleStateRepositoryProvider,
            ObjectProvider<SalesProductDimensionRepository> productDimensionRepositoryProvider,
            ObjectProvider<SalesPriceTrendRepository> priceTrendRepositoryProvider,
            ObjectProvider<SalesHistoryBackfillService> historyBackfillServiceProvider
    ) {
        this(
                salesFactRepository,
                new ProductLifecycleClassifier(),
                syncStatusServiceProvider == null ? null : syncStatusServiceProvider.getIfAvailable(),
                lifecycleStateRepositoryProvider == null ? null : lifecycleStateRepositoryProvider.getIfAvailable(),
                productDimensionRepositoryProvider == null ? null : productDimensionRepositoryProvider.getIfAvailable(),
                priceTrendRepositoryProvider == null ? null : priceTrendRepositoryProvider.getIfAvailable(),
                historyBackfillServiceProvider == null ? null : historyBackfillServiceProvider.getIfAvailable()
        );
    }

    public SalesAnalyticsService(SalesFactRepository salesFactRepository) {
        this(salesFactRepository, new ProductLifecycleClassifier(), null, null, null, null, null);
    }

    public SalesAnalyticsService(SalesFactRepository salesFactRepository, ProductLifecycleClassifier lifecycleClassifier) {
        this(salesFactRepository, lifecycleClassifier, null, null, null, null, null);
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ProductLifecycleClassifier lifecycleClassifier,
            NoonBusinessSyncStatusService syncStatusService
    ) {
        this(salesFactRepository, lifecycleClassifier, syncStatusService, null, null, null, null);
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ProductLifecycleClassifier lifecycleClassifier,
            NoonBusinessSyncStatusService syncStatusService,
            ProductLifecycleStateRepository lifecycleStateRepository
    ) {
        this(salesFactRepository, lifecycleClassifier, syncStatusService, lifecycleStateRepository, null, null, null);
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ProductLifecycleClassifier lifecycleClassifier,
            NoonBusinessSyncStatusService syncStatusService,
            ProductLifecycleStateRepository lifecycleStateRepository,
            SalesProductDimensionRepository productDimensionRepository
    ) {
        this(salesFactRepository, lifecycleClassifier, syncStatusService, lifecycleStateRepository, productDimensionRepository, null, null);
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ProductLifecycleClassifier lifecycleClassifier,
            NoonBusinessSyncStatusService syncStatusService,
            ProductLifecycleStateRepository lifecycleStateRepository,
            SalesProductDimensionRepository productDimensionRepository,
            SalesPriceTrendRepository priceTrendRepository
    ) {
        this(
                salesFactRepository,
                lifecycleClassifier,
                syncStatusService,
                lifecycleStateRepository,
                productDimensionRepository,
                priceTrendRepository,
                null
        );
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ProductLifecycleClassifier lifecycleClassifier,
            NoonBusinessSyncStatusService syncStatusService,
            ProductLifecycleStateRepository lifecycleStateRepository,
            SalesProductDimensionRepository productDimensionRepository,
            SalesPriceTrendRepository priceTrendRepository,
            SalesHistoryBackfillService historyBackfillService
    ) {
        this.salesFactRepository = salesFactRepository;
        this.lifecycleClassifier = lifecycleClassifier;
        this.syncStatusService = syncStatusService;
        this.lifecycleStateRepository = lifecycleStateRepository;
        this.productDimensionRepository = productDimensionRepository;
        this.priceTrendRepository = priceTrendRepository;
        this.historyBackfillService = historyBackfillService;
    }

    public SalesDailyFactsView listDailyFacts(SalesFactQuery query) {
        List<DailySalesFact> facts = salesFactRepository.list(query);
        if (facts == null || facts.isEmpty()) {
            List<SalesDataQualityState> qualityStates = qualityStatesForEmptyFacts(query);
            return new SalesDailyFactsView(List.of(), qualityStates, syncStatusFor(query, List.of(), qualityStates));
        }
        return new SalesDailyFactsView(facts, List.of(), syncStatusFor(query, facts, List.of()));
    }

    public SalesAnalyticsSummary getSummary(SalesFactQuery query) {
        List<DailySalesFact> rawFacts = listFacts(query);
        List<DailySalesFact> facts = applyProductFilters(query, rawFacts);
        List<SalesDataQualityState> qualityStates = rawFacts.isEmpty()
                ? qualityStatesForEmptyFacts(query)
                : List.of();
        NoonSalesSyncReadModel syncStatus = syncStatusFor(query, rawFacts, qualityStates);
        return summarize(facts, syncStatus);
    }

    public List<SalesTrendBucket> getTrends(SalesFactQuery query, String granularity) {
        List<DailySalesFact> facts = filteredFacts(query);
        Map<LocalDate, List<DailySalesFact>> factsByBucket = new TreeMap<>();
        for (DailySalesFact fact : facts) {
            LocalDate bucketStart = bucketStart(fact.getFactDate(), granularity);
            factsByBucket.computeIfAbsent(bucketStart, ignored -> new ArrayList<>()).add(fact);
        }
        List<SalesTrendBucket> buckets = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DailySalesFact>> entry : factsByBucket.entrySet()) {
            buckets.add(new SalesTrendBucket(
                    entry.getKey(),
                    bucketLabel(entry.getKey(), granularity),
                    summarize(entry.getValue())
            ));
        }
        return buckets;
    }

    public List<SalesProductRow> listProductRows(SalesFactQuery query) {
        List<DailySalesFact> facts = filteredFacts(query);
        Map<String, List<DailySalesFact>> factsByProduct = new TreeMap<>();
        for (DailySalesFact fact : facts) {
            factsByProduct.computeIfAbsent(productKey(fact), ignored -> new ArrayList<>()).add(fact);
        }
        Map<String, SalesProductDimensionSnapshot> dimensionsByProduct = dimensionsByProduct(query);
        return factsByProduct.values().stream()
                .map(factsForProduct -> toProductRow(
                        query,
                        factsForProduct,
                        dimensionsByProduct.get(productKey(latestFact(factsForProduct)))
                ))
                .filter(row -> matchesProductDimensionFilters(query, row))
                .sorted(Comparator.comparingInt(SalesProductRow::getNetUnits).reversed()
                        .thenComparing(SalesProductRow::getPartnerSku, Comparator.nullsLast(String::compareTo))
                        .thenComparing(SalesProductRow::getSku, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    public SalesProductDetail getProductDetail(SalesFactQuery query) {
        List<DailySalesFact> facts = filteredFacts(query);
        Map<String, SalesProductDimensionSnapshot> dimensionsByProduct = dimensionsByProduct(query);
        SalesProductDimensionSnapshot queryDimension = dimensionsByProduct.get(productKey(query.getPartnerSku(), query.getSku()));
        SalesPriceTrendResult priceTrend = priceTrendFor(query, "day");
        SalesHistoryCoverage historyCoverage = historyCoverageFor(query, facts, priceTrend);
        if (facts.isEmpty()) {
            return new SalesProductDetail(
                    query.getPartnerSku(),
                    query.getSku(),
                    null,
                    null,
                    List.of(),
                    summarize(facts),
                    facts,
                    queryDimension == null ? null : queryDimension.getImageUrl(),
                    queryDimension == null ? null : queryDimension.getCurrentStock(),
                    queryDimension == null ? null : queryDimension.getFbnStock(),
                    queryDimension == null ? null : queryDimension.getSupermallStock(),
                    queryDimension == null ? null : queryDimension.getFbpStock(),
                    null,
                    priceTrend.getBuckets(),
                    priceTrend.getState(),
                    historyCoverage
            );
        }
        DailySalesFact latest = latestFact(facts);
        SalesAnalyticsSummary summary = summarize(facts);
        SalesProductDimensionSnapshot dimension = dimensionsByProduct.get(productKey(latest));
        return new SalesProductDetail(
                latest.getPartnerSku(),
                latest.getSku(),
                latest.getProductTitle(),
                latest.getFactDate(),
                sourceSystems(facts),
                summary,
                facts,
                dimension == null ? null : dimension.getImageUrl(),
                dimension == null ? null : dimension.getCurrentStock(),
                dimension == null ? null : dimension.getFbnStock(),
                dimension == null ? null : dimension.getSupermallStock(),
                dimension == null ? null : dimension.getFbpStock(),
                stockCoverDays(dimension, summary, facts),
                priceTrend.getBuckets(),
                priceTrend.getState(),
                historyCoverage
        );
    }

    public SalesHistoryBackfillResult requestHistoryBackfill(SalesHistoryBackfillCommand command) {
        if (historyBackfillService == null) {
            throw new IllegalStateException("历史补全服务未启用。");
        }
        return historyBackfillService.requestBackfill(command);
    }

    private SalesPriceTrendResult priceTrendFor(SalesFactQuery query, String granularity) {
        if (priceTrendRepository == null) {
            return SalesPriceTrendResult.empty();
        }
        SalesPriceTrendResult result = priceTrendRepository.getPriceTrend(query, granularity);
        return result == null ? SalesPriceTrendResult.empty() : result;
    }

    private SalesHistoryCoverage historyCoverageFor(
            SalesFactQuery query,
            List<DailySalesFact> facts,
            SalesPriceTrendResult priceTrend
    ) {
        if (historyBackfillService == null) {
            return null;
        }
        return historyBackfillService.coverage(query, facts, priceTrend);
    }

    public String exportDailyFactsCsv(SalesFactQuery query) {
        List<DailySalesFact> facts = filteredFacts(query);
        Map<String, SalesProductDimensionSnapshot> dimensionsByProduct = dimensionsByProduct(query);
        StringBuilder builder = new StringBuilder();
        builder.append("factDate,sourceSystem,partnerSku,sku,brand,productFulltype,dataQualityCodes,productTitle,netUnits,grossUnits,shippedUnits,cancelledUnits,revenueShipped,yourVisitors,totalVisitors,conversionVisitorsPercentage,buyBoxVisitorPercentage");
        for (DailySalesFact fact : facts) {
            SalesProductDimensionSnapshot dimension = dimensionsByProduct.get(productKey(fact));
            builder.append('\n')
                    .append(csv(fact.getFactDate()))
                    .append(',').append(csv(fact.getSourceSystem()))
                    .append(',').append(csv(fact.getPartnerSku()))
                    .append(',').append(csv(fact.getSku()))
                    .append(',').append(csv(dimension == null ? null : dimension.getBrand()))
                    .append(',').append(csv(dimension == null ? null : dimension.getProductFulltype()))
                    .append(',').append(csv(String.join("|", dataQualityCodes(dimension))))
                    .append(',').append(csv(fact.getProductTitle()))
                    .append(',').append(csv(fact.getNetUnits()))
                    .append(',').append(csv(fact.getGrossUnits()))
                    .append(',').append(csv(fact.getShippedUnits()))
                    .append(',').append(csv(fact.getCancelledUnits()))
                    .append(',').append(csv(fact.getRevenueShipped()))
                    .append(',').append(csv(fact.getYourVisitors()))
                    .append(',').append(csv(fact.getTotalVisitors()))
                    .append(',').append(csv(fact.getConversionVisitorsPercentage()))
                    .append(',').append(csv(fact.getBuyBoxVisitorPercentage()));
        }
        return builder.toString();
    }

    private SalesAnalyticsSummary summarize(List<DailySalesFact> facts) {
        return summarize(facts, null);
    }

    private SalesAnalyticsSummary summarize(List<DailySalesFact> facts, NoonSalesSyncReadModel syncStatus) {
        int netUnits = 0;
        int grossUnits = 0;
        int shippedUnits = 0;
        int cancelledUnits = 0;
        int yourVisitors = 0;
        int totalVisitors = 0;
        BigDecimal revenueShipped = BigDecimal.ZERO;
        BigDecimal conversionTotal = BigDecimal.ZERO;
        int conversionCount = 0;
        BigDecimal buyBoxTotal = BigDecimal.ZERO;
        int buyBoxCount = 0;

        for (DailySalesFact fact : facts) {
            netUnits += fact.getNetUnits();
            grossUnits += valueOrZero(fact.getGrossUnits());
            shippedUnits += valueOrZero(fact.getShippedUnits());
            cancelledUnits += valueOrZero(fact.getCancelledUnits());
            yourVisitors += valueOrZero(fact.getYourVisitors());
            totalVisitors += valueOrZero(fact.getTotalVisitors());
            if (fact.getRevenueShipped() != null) {
                revenueShipped = revenueShipped.add(fact.getRevenueShipped());
            }
            if (fact.getConversionVisitorsPercentage() != null) {
                conversionTotal = conversionTotal.add(fact.getConversionVisitorsPercentage());
                conversionCount++;
            }
            if (fact.getBuyBoxVisitorPercentage() != null) {
                buyBoxTotal = buyBoxTotal.add(fact.getBuyBoxVisitorPercentage());
                buyBoxCount++;
            }
        }
        return new SalesAnalyticsSummary(
                netUnits,
                grossUnits,
                shippedUnits,
                cancelledUnits,
                revenueShipped,
                yourVisitors,
                totalVisitors,
                average(conversionTotal, conversionCount),
                average(buyBoxTotal, buyBoxCount),
                syncStatus,
                syncStatus == null || syncStatus.isBusinessMetricsAllowed()
        );
    }

    private NoonSalesSyncReadModel syncStatusFor(
            SalesFactQuery query,
            List<DailySalesFact> facts,
            List<SalesDataQualityState> qualityStates
    ) {
        if (syncStatusService == null) {
            return null;
        }
        LocalDate latestFactDate = salesFactRepository.findLatestFactDate(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        if (latestFactDate == null && facts != null && !facts.isEmpty()) {
            latestFactDate = facts.stream()
                    .map(DailySalesFact::getFactDate)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        }
        return syncStatusService.describeSalesSurface(new NoonSalesSurfaceSyncInput(
                NoonSyncScope.of(query.getOwnerUserId(), null, query.getStoreCode(), query.getSiteCode()),
                latestFactDate,
                query.getDateTo(),
                qualityStates,
                false
        ));
    }

    private LocalDate bucketStart(LocalDate factDate, String granularity) {
        if ("month".equalsIgnoreCase(granularity)) {
            return YearMonth.from(factDate).atDay(1);
        }
        if ("week".equalsIgnoreCase(granularity)) {
            return factDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return factDate;
    }

    private String bucketLabel(LocalDate bucketStart, String granularity) {
        if ("month".equalsIgnoreCase(granularity)) {
            return bucketStart.getYear() + "-" + twoDigits(bucketStart.getMonthValue());
        }
        if ("week".equalsIgnoreCase(granularity)) {
            WeekFields weekFields = WeekFields.ISO;
            int weekYear = bucketStart.get(weekFields.weekBasedYear());
            int week = bucketStart.get(weekFields.weekOfWeekBasedYear());
            return weekYear + "-W" + twoDigits(week);
        }
        return bucketStart.toString();
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private SalesProductRow toProductRow(
            SalesFactQuery query,
            List<DailySalesFact> facts,
            SalesProductDimensionSnapshot dimension
    ) {
        DailySalesFact latest = latestFact(facts);
        SalesAnalyticsSummary summary = summarize(facts);
        SalesAnalyticsSummary latestSummary = summarize(facts.stream()
                .filter(fact -> latest.getFactDate().equals(fact.getFactDate()))
                .collect(Collectors.toList()));
        boolean dimensionMatched = dimension != null;
        List<String> dimensionQualityCodes = dimensionQualityCodes(dimension);
        List<String> dataQualityCodes = dataQualityCodes(dimension);
        return new SalesProductRow(
                latest.getPartnerSku(),
                latest.getSku(),
                latest.getProductTitle(),
                latest.getFactDate(),
                sourceSystems(facts),
                lifecycleForProduct(query, facts),
                summary,
                dimension == null ? null : dimension.getBrand(),
                dimension == null ? null : dimension.getProductFulltype(),
                dimensionMatched,
                dimensionMatched ? "PRODUCT_MANAGEMENT" : null,
                dimensionQualityCodes,
                dataQualityCodes,
                latestSummary,
                dimension == null ? null : dimension.getImageUrl(),
                dimension == null ? null : dimension.getCurrentStock(),
                dimension == null ? null : dimension.getFbnStock(),
                dimension == null ? null : dimension.getSupermallStock(),
                dimension == null ? null : dimension.getFbpStock(),
                stockCoverDays(dimension, summary, facts)
        );
    }

    private BigDecimal stockCoverDays(
            SalesProductDimensionSnapshot dimension,
            SalesAnalyticsSummary summary,
            List<DailySalesFact> facts
    ) {
        if (dimension == null || dimension.getCurrentStock() == null || summary == null || summary.getNetUnits() <= 0) {
            return null;
        }
        long observedDays = observedDays(facts);
        if (observedDays <= 0) {
            return null;
        }
        return BigDecimal.valueOf(dimension.getCurrentStock())
                .multiply(BigDecimal.valueOf(observedDays))
                .divide(BigDecimal.valueOf(summary.getNetUnits()), 1, RoundingMode.HALF_UP);
    }

    private long observedDays(List<DailySalesFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return 0L;
        }
        LocalDate min = facts.stream()
                .map(DailySalesFact::getFactDate)
                .min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate max = facts.stream()
                .map(DailySalesFact::getFactDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (min == null || max == null || max.isBefore(min)) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(min, max) + 1L;
    }

    private List<DailySalesFact> filteredFacts(SalesFactQuery query) {
        List<DailySalesFact> facts = listFacts(query);
        return applyProductFilters(query, facts);
    }

    private List<DailySalesFact> applyProductFilters(SalesFactQuery query, List<DailySalesFact> facts) {
        List<DailySalesFact> dimensionFiltered = applyProductDimensionFilters(query, facts);
        if (!hasText(query.getLifecycleCode())) {
            return dimensionFiltered;
        }
        Map<String, List<DailySalesFact>> factsByProduct = new TreeMap<>();
        for (DailySalesFact fact : dimensionFiltered) {
            factsByProduct.computeIfAbsent(productKey(fact), ignored -> new ArrayList<>()).add(fact);
        }
        return factsByProduct.values().stream()
                .filter(factsForProduct -> query.getLifecycleCode().equals(lifecycleForProduct(query, factsForProduct).getCode()))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<DailySalesFact> listFacts(SalesFactQuery query) {
        List<DailySalesFact> facts = salesFactRepository.list(query);
        return facts == null ? List.of() : facts;
    }

    private List<DailySalesFact> applyProductDimensionFilters(SalesFactQuery query, List<DailySalesFact> facts) {
        if (!hasProductDimensionFilters(query)) {
            return facts;
        }
        Map<String, SalesProductDimensionSnapshot> dimensionsByProduct = dimensionsByProduct(query);
        return facts.stream()
                .filter(fact -> matchesProductDimensionFilters(query, fact, dimensionsByProduct.get(productKey(fact))))
                .collect(Collectors.toList());
    }

    private Map<String, SalesProductDimensionSnapshot> dimensionsByProduct(SalesFactQuery query) {
        if (productDimensionRepository == null) {
            return Map.of();
        }
        Map<String, SalesProductDimensionSnapshot> values = new HashMap<>();
        for (SalesProductDimensionSnapshot snapshot : productDimensionRepository.list(query)) {
            values.put(productKey(snapshot.getPartnerSku(), snapshot.getSku()), snapshot);
        }
        return values;
    }

    private List<String> dimensionQualityCodes(SalesProductDimensionSnapshot dimension) {
        if (dimension == null) {
            return List.of("product_dimension_missing");
        }
        List<String> codes = new ArrayList<>();
        codes.add("product_dimension_matched");
        if (!hasText(dimension.getBrand())) {
            codes.add("brand_missing");
        }
        if (!hasText(dimension.getProductFulltype())) {
            codes.add("backend_fulltype_missing");
        }
        return codes;
    }

    private List<String> dataQualityCodes(SalesProductDimensionSnapshot dimension) {
        List<String> codes = new ArrayList<>();
        codes.add("sales_fact_ready");
        codes.addAll(dimensionQualityCodes(dimension));
        return codes;
    }

    private boolean hasProductDimensionFilters(SalesFactQuery query) {
        return hasText(query.getBrand())
                || hasText(query.getProductFulltype())
                || hasText(query.getDataQualityCode());
    }

    private boolean matchesProductDimensionFilters(SalesFactQuery query, SalesProductRow row) {
        if (hasText(query.getBrand()) && !query.getBrand().equals(row.getBrand())) {
            return false;
        }
        if (hasText(query.getProductFulltype()) && !query.getProductFulltype().equals(row.getProductFulltype())) {
            return false;
        }
        return !hasText(query.getDataQualityCode()) || row.getDataQualityCodes().contains(query.getDataQualityCode());
    }

    private boolean matchesProductDimensionFilters(
            SalesFactQuery query,
            DailySalesFact fact,
            SalesProductDimensionSnapshot dimension
    ) {
        if (hasText(query.getBrand()) && (dimension == null || !query.getBrand().equals(dimension.getBrand()))) {
            return false;
        }
        if (hasText(query.getProductFulltype())
                && (dimension == null || !query.getProductFulltype().equals(dimension.getProductFulltype()))) {
            return false;
        }
        if (!hasText(query.getDataQualityCode())) {
            return true;
        }
        List<String> codes = new ArrayList<>();
        codes.add("sales_fact_ready");
        codes.addAll(dimensionQualityCodes(dimension));
        return codes.contains(query.getDataQualityCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ProductLifecycleResult lifecycleForProduct(SalesFactQuery query, List<DailySalesFact> facts) {
        DailySalesFact latest = latestFact(facts);
        if (lifecycleStateRepository == null) {
            return classifyLifecycle(query, facts);
        }
        ProductLifecycleCurrentState currentState = lifecycleStateRepository.findCurrentState(new ProductLifecycleStateQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                latest.getPartnerSku(),
                latest.getSku()
        ));
        if (currentState == null) {
            return new ProductLifecycleResult(
                    "pending",
                    "待计算",
                    "生命周期状态尚未完成计算。",
                    ProductLifecycleResult.DEFAULT_RULE_VERSION,
                    List.of(),
                    "pending",
                    "{\"reason\":\"lifecycle_pending\"}"
            );
        }
        return new ProductLifecycleResult(
                currentState.getLifecycleCode(),
                currentState.getLifecycleLabel(),
                currentState.getExplanation(),
                currentState.getRuleVersion(),
                lifecycleWarnings(currentState),
                currentState.getQualityState(),
                currentState.getEvidenceJson()
        );
    }

    private List<String> lifecycleWarnings(ProductLifecycleCurrentState currentState) {
        if ("stockout_hold".equals(currentState.getQualityState())) {
            return List.of("lifecycle_stockout_hold");
        }
        if ("data_insufficient".equals(currentState.getLifecycleCode())
                || "data_insufficient".equals(currentState.getQualityState())
                || "pv_unresolvable".equals(currentState.getQualityState())) {
            return List.of("lifecycle_data_insufficient");
        }
        return List.of();
    }

    private ProductLifecycleResult classifyLifecycle(SalesFactQuery query, List<DailySalesFact> facts) {
        LocalDate firstFactDate = facts.stream()
                .map(DailySalesFact::getFactDate)
                .min(Comparator.naturalOrder())
                .orElse(query.getDateFrom());
        LocalDate analysisDate = query.getDateTo() == null ? latestFact(facts).getFactDate() : query.getDateTo();
        return lifecycleClassifier.classify(new ProductLifecycleSignal(
                analysisDate,
                firstFactDate,
                null,
                facts
        ));
    }

    private DailySalesFact latestFact(List<DailySalesFact> facts) {
        return facts.stream()
                .max(Comparator.comparing(DailySalesFact::getFactDate))
                .orElseThrow();
    }

    private List<String> sourceSystems(List<DailySalesFact> facts) {
        Set<String> sourceSystems = new LinkedHashSet<>();
        facts.stream()
                .map(DailySalesFact::getSourceSystem)
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .forEach(sourceSystems::add);
        return new ArrayList<>(sourceSystems);
    }

    private String productKey(DailySalesFact fact) {
        return productKey(fact.getPartnerSku(), fact.getSku());
    }

    private String productKey(String partnerSku, String sku) {
        return nullSafe(partnerSku) + "|" + nullSafe(sku);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private List<SalesDataQualityState> qualityStatesForEmptyFacts(SalesFactQuery query) {
        List<SalesImportBatchRecord> batches = salesFactRepository.listImportBatches(new SalesImportBatchQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                query.getDateFrom(),
                query.getDateTo()
        ));
        if (batches == null || batches.isEmpty()) {
            return List.of(new SalesDataQualityState(
                    "no_usable_facts",
                    "当前筛选范围内没有可用销量事实，也没有匹配的导入批次。",
                    null
            ));
        }
        SalesImportBatchRecord latest = batches.get(0);
        if ("empty".equals(latest.getStatus())) {
            return List.of(new SalesDataQualityState(
                    "empty_report",
                    "最近一次匹配导入为空报表，没有生成销量事实。",
                    latest.getId()
            ));
        }
        if ("failed".equals(latest.getStatus())) {
            return List.of(new SalesDataQualityState(
                    "import_failed",
                    "最近一次匹配导入失败，请查看导入批次异常。",
                    latest.getId()
            ));
        }
        if ("imported_with_exceptions".equals(latest.getStatus())) {
            return List.of(new SalesDataQualityState(
                    "data_quality_exceptions",
                    "最近一次匹配导入存在异常行，当前筛选范围没有可用销量事实。",
                    latest.getId()
            ));
        }
        return List.of(new SalesDataQualityState(
                "no_usable_facts",
                "当前筛选范围内没有可用销量事实。",
                latest.getId()
        ));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal average(BigDecimal total, int count) {
        if (count == 0) {
            return null;
        }
        try {
            return total.divide(BigDecimal.valueOf(count));
        } catch (ArithmeticException exception) {
            return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}
