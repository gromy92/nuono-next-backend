package com.nuono.next.sales;

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
import java.util.Locale;
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
    private final SalesProductDimensionRepository productDimensionRepository;
    private final SalesPriceTrendRepository priceTrendRepository;

    @Autowired
    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            ObjectProvider<SalesProductDimensionRepository> productDimensionRepositoryProvider,
            ObjectProvider<SalesPriceTrendRepository> priceTrendRepositoryProvider
    ) {
        this(
                salesFactRepository,
                productDimensionRepositoryProvider == null ? null : productDimensionRepositoryProvider.getIfAvailable(),
                priceTrendRepositoryProvider == null ? null : priceTrendRepositoryProvider.getIfAvailable()
        );
    }

    public SalesAnalyticsService(SalesFactRepository salesFactRepository) {
        this(salesFactRepository, (SalesProductDimensionRepository) null, null);
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            SalesProductDimensionRepository productDimensionRepository
    ) {
        this(salesFactRepository, productDimensionRepository, null);
    }

    public SalesAnalyticsService(
            SalesFactRepository salesFactRepository,
            SalesProductDimensionRepository productDimensionRepository,
            SalesPriceTrendRepository priceTrendRepository
    ) {
        this.salesFactRepository = salesFactRepository;
        this.productDimensionRepository = productDimensionRepository;
        this.priceTrendRepository = priceTrendRepository;
    }

    public SalesDailyFactsView listDailyFacts(SalesFactQuery query) {
        List<DailySalesFact> facts = salesFactRepository.list(query);
        return new SalesDailyFactsView(facts);
    }

    public SalesAnalyticsSummary getSummary(SalesFactQuery query) {
        List<DailySalesFact> rawFacts = listFacts(query);
        List<DailySalesFact> facts = applyProductFilters(query, rawFacts);
        return summarize(facts);
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
        List<DailySalesFact> facts = filteredFacts(query).stream()
                .filter(this::hasBusinessPartnerSku)
                .collect(Collectors.toList());
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
        SalesFactQuery productQuery = productDetailIdentityQuery(query);
        List<DailySalesFact> facts = filteredFacts(productQuery);
        Map<String, SalesProductDimensionSnapshot> dimensionsByProduct = dimensionsByProduct(productQuery);
        SalesProductDimensionSnapshot queryDimension = dimensionsByProduct.get(productKey(productQuery, productQuery.getPartnerSku()));
        SalesPriceTrendResult priceTrend = priceTrendFor(productQuery, "day");
        if (facts.isEmpty()) {
            return new SalesProductDetail(
                    productQuery.getPartnerSku(),
                    queryDimension == null ? productQuery.getSku() : queryDimension.getSku(),
                    queryDimension == null ? null : queryDimension.getProductTitle(),
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
                    priceTrend.getState()
            );
        }
        DailySalesFact latest = latestFact(facts);
        SalesAnalyticsSummary summary = summarize(facts);
        SalesProductDimensionSnapshot dimension = dimensionsByProduct.get(productKey(latest));
        return new SalesProductDetail(
                latest.getPartnerSku(),
                latest.getSku(),
                productTitleFor(facts, dimension),
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
                priceTrend.getState()
        );
    }

    private SalesFactQuery productDetailIdentityQuery(SalesFactQuery query) {
        if (query == null || !hasBusinessPartnerSku(query.getPartnerSku())) {
            return query;
        }
        return query.withSku(null);
    }

    private SalesPriceTrendResult priceTrendFor(SalesFactQuery query, String granularity) {
        if (priceTrendRepository == null) {
            return SalesPriceTrendResult.empty();
        }
        SalesPriceTrendResult result = priceTrendRepository.getPriceTrend(query, granularity);
        return result == null ? SalesPriceTrendResult.empty() : result;
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
                average(buyBoxTotal, buyBoxCount)
        );
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
                productTitleFor(facts, dimension),
                latest.getFactDate(),
                sourceSystems(facts),
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

    private String productTitleFor(List<DailySalesFact> facts, SalesProductDimensionSnapshot dimension) {
        if (dimension != null && hasText(dimension.getProductTitle())) {
            return dimension.getProductTitle();
        }
        if (facts == null || facts.isEmpty()) {
            return null;
        }
        for (int index = facts.size() - 1; index >= 0; index--) {
            String title = facts.get(index).getProductTitle();
            if (hasText(title)) {
                return title;
            }
        }
        return null;
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
        return applyProductDimensionFilters(query, facts);
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
            values.putIfAbsent(productKey(query, snapshot.getPartnerSku()), snapshot);
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
        return dimensionQualityCodes(dimension);
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
        return dimensionQualityCodes(dimension).contains(query.getDataQualityCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
        if (fact == null) {
            return "";
        }
        return productKey(
                fact.getOwnerUserId() == null ? null : String.valueOf(fact.getOwnerUserId()),
                fact.getStoreCode(),
                fact.getSiteCode(),
                fact.getPartnerSku()
        );
    }

    private String productKey(SalesFactQuery query, String partnerSku) {
        if (query == null) {
            return productKey(null, null, null, partnerSku);
        }
        return productKey(
                query.getOwnerUserId() == null ? null : String.valueOf(query.getOwnerUserId()),
                query.getStoreCode(),
                query.getSiteCode(),
                partnerSku
        );
    }

    private boolean hasBusinessPartnerSku(DailySalesFact fact) {
        return fact != null && hasBusinessPartnerSku(fact.getPartnerSku());
    }

    private boolean hasBusinessPartnerSku(String partnerSku) {
        return partnerSku != null && !partnerSku.isBlank() && !"-".equals(partnerSku.trim());
    }

    private String productKey(String partnerSku) {
        return nullSafe(partnerSku).trim().toUpperCase(Locale.ROOT);
    }

    private String productKey(String ownerUserId, String storeCode, String siteCode, String partnerSku) {
        return productKey(ownerUserId)
                + "|"
                + productKey(storeCode)
                + "|"
                + productKey(siteCode)
                + "|"
                + productKey(partnerSku);
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
