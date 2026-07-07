package com.nuono.next.salesforecast;

import com.nuono.next.sales.DailySalesFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SalesForecastFeatureBuilder {

    public List<SalesForecastFeatureSnapshot> build(List<DailySalesFact> facts, LocalDate latestFactDate) {
        return build(facts, latestFactDate, List.of(), null, List.of(), false);
    }

    public List<SalesForecastFeatureSnapshot> build(
            List<DailySalesFact> facts,
            LocalDate latestFactDate,
            List<SalesForecastStockSnapshot> stockSnapshots
    ) {
        return build(facts, latestFactDate, stockSnapshots, null, List.of(), true);
    }

    public List<SalesForecastFeatureSnapshot> build(
            List<DailySalesFact> facts,
            LocalDate latestFactDate,
            List<SalesForecastStockSnapshot> stockSnapshots,
            CalendarHistoryFactorProvider historyFactorProvider
    ) {
        return build(facts, latestFactDate, stockSnapshots, historyFactorProvider, List.of(), true);
    }

    public List<SalesForecastFeatureSnapshot> build(
            List<DailySalesFact> facts,
            LocalDate latestFactDate,
            List<SalesForecastStockSnapshot> stockSnapshots,
            CalendarHistoryFactorProvider historyFactorProvider,
            List<SalesForecastHistoricalStockSnapshot> historicalStockSnapshots
    ) {
        return build(facts, latestFactDate, stockSnapshots, historyFactorProvider, historicalStockSnapshots, true);
    }

    private List<SalesForecastFeatureSnapshot> build(
            List<DailySalesFact> facts,
            LocalDate latestFactDate,
            List<SalesForecastStockSnapshot> stockSnapshots,
            CalendarHistoryFactorProvider historyFactorProvider,
            List<SalesForecastHistoricalStockSnapshot> historicalStockSnapshots,
            boolean requireCurrentProductBoundary
    ) {
        if (latestFactDate == null) {
            return List.of();
        }
        List<DailySalesFact> safeFacts = facts == null ? List.of() : facts;
        Map<String, SalesForecastStockSnapshot> stockByProduct = stockByProduct(stockSnapshots);
        Map<String, Map<LocalDate, Integer>> historicalStockByProduct = historicalStockByProduct(historicalStockSnapshots);
        if (safeFacts.isEmpty() && stockByProduct.isEmpty()) {
            return List.of();
        }
        Map<String, List<DailySalesFact>> factsByProduct = new TreeMap<>();
        Map<String, List<DailySalesFact>> factsByPartnerSku = new HashMap<>();
        for (DailySalesFact fact : safeFacts) {
            if (!hasBusinessPartnerSku(fact)) {
                continue;
            }
            List<DailySalesFact> productFacts = factsByProduct.computeIfAbsent(productKey(fact), ignored -> new ArrayList<>());
            productFacts.add(fact);
            factsByPartnerSku.computeIfAbsent(partnerSkuKey(fact.getPartnerSku()), ignored -> new ArrayList<>()).add(fact);
        }
        if (!stockByProduct.isEmpty()) {
            return stockByProduct.values().stream()
                    .map(stockSnapshot -> {
                        List<DailySalesFact> productFacts = factsByPartnerSku.get(partnerSkuKey(stockSnapshot.getPartnerSku()));
                        if (productFacts == null || productFacts.isEmpty()) {
                            return buildNoTrainingSnapshot(stockSnapshot, latestFactDate);
                        }
                        String key = partnerSkuKey(stockSnapshot.getPartnerSku());
                        return buildProductSnapshot(
                                productFacts,
                                latestFactDate,
                                stockSnapshot,
                                historyFactorProvider,
                                historicalStockByProduct.get(key)
                        );
                    })
                    .sorted(snapshotComparator())
                    .collect(Collectors.toList());
        }
        if (requireCurrentProductBoundary) {
            return List.of();
        }
        return factsByProduct.values().stream()
                .map(productFacts -> buildProductSnapshot(
                        productFacts,
                        latestFactDate,
                        stockByProduct.get(partnerSkuKey(productFacts.get(0).getPartnerSku())),
                        historyFactorProvider,
                        historicalStockByProduct.get(partnerSkuKey(productFacts.get(0).getPartnerSku()))
                ))
                .sorted(snapshotComparator())
                .collect(Collectors.toList());
    }

    private Comparator<SalesForecastFeatureSnapshot> snapshotComparator() {
        return Comparator.comparingInt(SalesForecastFeatureSnapshot::getHistoryUnits30).reversed()
                .thenComparing(SalesForecastFeatureSnapshot::getPartnerSku, Comparator.nullsLast(String::compareTo))
                .thenComparing(SalesForecastFeatureSnapshot::getSku, Comparator.nullsLast(String::compareTo));
    }

    private SalesForecastFeatureSnapshot buildProductSnapshot(
            List<DailySalesFact> productFacts,
            LocalDate latestFactDate,
            SalesForecastStockSnapshot stockSnapshot,
            CalendarHistoryFactorProvider historyFactorProvider,
            Map<LocalDate, Integer> historicalStockByDate
    ) {
        DailySalesFact latestFact = productFacts.stream()
                .max(Comparator.comparing(DailySalesFact::getFactDate)
                        .thenComparing(fact -> nullSafe(fact.getSku())))
                .orElseThrow();
        LocalDate firstFactDate = productFacts.stream()
                .map(DailySalesFact::getFactDate)
                .min(Comparator.naturalOrder())
                .orElse(latestFactDate);
        Integer currentStock = stockSnapshot == null ? null : stockSnapshot.getCurrentStock();
        int historyUnits30 = unitsInWindow(productFacts, latestFactDate, 30);
        BigDecimal stockCoverDays = stockCoverDays(currentStock, historyUnits30);
        String productTitle = firstNonBlank(
                latestFact.getProductTitle(),
                stockSnapshot == null ? null : stockSnapshot.getProductTitle()
        );
        DemandSeries demandSeries = demandSeries(
                productFacts,
                latestFactDate,
                latestFact,
                stockSnapshot,
                historyFactorProvider,
                historicalStockByDate
        );
        List<String> warningCodes = demandSeries.hasHistoricalStockoutImputation()
                ? List.of("historical_stockout_imputed")
                : List.of();
        return new SalesForecastFeatureSnapshot(
                latestFact.getOwnerUserId(),
                latestFact.getStoreCode(),
                latestFact.getSiteCode(),
                latestFact.getPartnerSku(),
                latestFact.getSku(),
                productTitle,
                stockSnapshot == null ? null : stockSnapshot.getBrand(),
                stockSnapshot == null ? null : stockSnapshot.getProductFulltype(),
                stockSnapshot == null ? null : stockSnapshot.getProductFamily(),
                latestFactDate,
                unitsInWindow(productFacts, latestFactDate, 7),
                historyUnits30,
                unitsInWindow(productFacts, latestFactDate, 60),
                unitsInWindow(productFacts, latestFactDate, 90),
                demandSeries.unitsInWindow(latestFactDate, 7),
                demandSeries.unitsInWindow(latestFactDate, 30),
                demandSeries.unitsInWindow(latestFactDate, 60),
                demandSeries.unitsInWindow(latestFactDate, 90),
                observedDays(firstFactDate, latestFactDate),
                currentStock,
                stockCoverDays,
                warningCodes
        );
    }

    private SalesForecastFeatureSnapshot buildNoTrainingSnapshot(
            SalesForecastStockSnapshot stockSnapshot,
            LocalDate latestFactDate
    ) {
        return new SalesForecastFeatureSnapshot(
                stockSnapshot.getOwnerUserId(),
                stockSnapshot.getStoreCode(),
                stockSnapshot.getSiteCode(),
                stockSnapshot.getPartnerSku(),
                stockSnapshot.getSku(),
                stockSnapshot.getProductTitle(),
                stockSnapshot.getBrand(),
                stockSnapshot.getProductFulltype(),
                stockSnapshot.getProductFamily(),
                latestFactDate,
                0,
                0,
                0,
                0,
                0,
                stockSnapshot.getCurrentStock(),
                null,
                List.of("no_sales_training_data")
        );
    }

    private int unitsInWindow(List<DailySalesFact> facts, LocalDate latestFactDate, int days) {
        LocalDate dateFrom = latestFactDate.minusDays(days - 1L);
        return facts.stream()
                .filter(fact -> !fact.getFactDate().isBefore(dateFrom) && !fact.getFactDate().isAfter(latestFactDate))
                .mapToInt(DailySalesFact::getNetUnits)
                .sum();
    }

    private DemandSeries demandSeries(
            List<DailySalesFact> facts,
            LocalDate latestFactDate,
            DailySalesFact latestFact,
            SalesForecastStockSnapshot stockSnapshot,
            CalendarHistoryFactorProvider historyFactorProvider,
            Map<LocalDate, Integer> historicalStockByDate
    ) {
        LocalDate dateFrom = latestFactDate.minusDays(89);
        Map<LocalDate, Integer> rawUnitsByDate = new HashMap<>();
        for (DailySalesFact fact : facts) {
            if (fact.getFactDate().isBefore(dateFrom) || fact.getFactDate().isAfter(latestFactDate)) {
                continue;
            }
            rawUnitsByDate.merge(fact.getFactDate(), fact.getNetUnits(), Integer::sum);
        }
        Map<LocalDate, BigDecimal> factorByDate = calendarFactorByDate(
                latestFact,
                stockSnapshot,
                historyFactorProvider,
                dateFrom,
                latestFactDate
        );
        Map<LocalDate, BigDecimal> adjustedUnitsByDate = new HashMap<>();
        int imputedStockoutDays = 0;
        for (LocalDate date = dateFrom; !date.isAfter(latestFactDate); date = date.plusDays(1)) {
            BigDecimal adjustedUnits = BigDecimal.valueOf(rawUnitsByDate.getOrDefault(date, 0))
                    .divide(factorByDate.getOrDefault(date, BigDecimal.ONE), 8, RoundingMode.HALF_UP);
            if (isHistoricalStockout(date, historicalStockByDate)) {
                BigDecimal baseline = priorInStockBaseline(adjustedUnitsByDate, historicalStockByDate, dateFrom, date);
                if (baseline != null && baseline.compareTo(adjustedUnits) > 0) {
                    adjustedUnits = baseline;
                    imputedStockoutDays++;
                }
            }
            adjustedUnitsByDate.put(date, adjustedUnits);
        }
        return new DemandSeries(adjustedUnitsByDate, imputedStockoutDays);
    }

    private Map<LocalDate, BigDecimal> calendarFactorByDate(
            DailySalesFact latestFact,
            SalesForecastStockSnapshot stockSnapshot,
            CalendarHistoryFactorProvider historyFactorProvider,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        Map<LocalDate, BigDecimal> factorByDate = new HashMap<>();
        if (historyFactorProvider == null) {
            return factorByDate;
        }
        List<BigDecimal> dailyFactors = historyFactorProvider.dailyFactors(
                firstNonNull(latestFact.getOwnerUserId(), stockSnapshot == null ? null : stockSnapshot.getOwnerUserId()),
                firstNonBlank(latestFact.getStoreCode(), stockSnapshot == null ? null : stockSnapshot.getStoreCode()),
                firstNonBlank(latestFact.getSiteCode(), stockSnapshot == null ? null : stockSnapshot.getSiteCode()),
                dateFrom,
                dateTo,
                stockSnapshot == null ? null : stockSnapshot.getBrand(),
                stockSnapshot == null ? null : stockSnapshot.getProductFulltype(),
                stockSnapshot == null ? null : stockSnapshot.getProductFamily()
        );
        if (dailyFactors == null) {
            dailyFactors = List.of();
        }
        for (int index = 0; index <= ChronoUnit.DAYS.between(dateFrom, dateTo); index++) {
            BigDecimal factor = index < dailyFactors.size() ? dailyFactors.get(index) : BigDecimal.ONE;
            factorByDate.put(dateFrom.plusDays(index), safePositiveFactor(factor));
        }
        return factorByDate;
    }

    private boolean isHistoricalStockout(LocalDate date, Map<LocalDate, Integer> historicalStockByDate) {
        if (historicalStockByDate == null || !historicalStockByDate.containsKey(date)) {
            return false;
        }
        Integer sellableStock = historicalStockByDate.get(date);
        return sellableStock != null && sellableStock <= 0;
    }

    private BigDecimal priorInStockBaseline(
            Map<LocalDate, BigDecimal> adjustedUnitsByDate,
            Map<LocalDate, Integer> historicalStockByDate,
            LocalDate dateFrom,
            LocalDate stockoutDate
    ) {
        if (historicalStockByDate == null || historicalStockByDate.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (LocalDate date = stockoutDate.minusDays(1); !date.isBefore(dateFrom); date = date.minusDays(1)) {
            Integer sellableStock = historicalStockByDate.get(date);
            if (sellableStock == null || sellableStock <= 0) {
                continue;
            }
            total = total.add(adjustedUnitsByDate.getOrDefault(date, BigDecimal.ZERO));
            count++;
            if (count >= 30) {
                break;
            }
        }
        if (count == 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal safePositiveFactor(BigDecimal factor) {
        if (factor == null || factor.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return factor;
    }

    private int observedDays(LocalDate firstFactDate, LocalDate latestFactDate) {
        long days = ChronoUnit.DAYS.between(firstFactDate, latestFactDate) + 1;
        if (days < 0) {
            return 0;
        }
        return (int) Math.min(days, 90);
    }

    private BigDecimal stockCoverDays(Integer currentStock, int historyUnits30) {
        if (currentStock == null || historyUnits30 <= 0) {
            return null;
        }
        return BigDecimal.valueOf(currentStock)
                .multiply(BigDecimal.valueOf(30))
                .divide(BigDecimal.valueOf(historyUnits30), 1, RoundingMode.HALF_UP);
    }

    private Map<String, SalesForecastStockSnapshot> stockByProduct(List<SalesForecastStockSnapshot> stockSnapshots) {
        Map<String, SalesForecastStockSnapshot> stockByProduct = new HashMap<>();
        if (stockSnapshots == null) {
            return stockByProduct;
        }
        for (SalesForecastStockSnapshot stockSnapshot : stockSnapshots) {
            if (stockSnapshot == null || !hasBusinessPartnerSku(stockSnapshot.getPartnerSku())) {
                continue;
            }
            String key = partnerSkuKey(stockSnapshot.getPartnerSku());
            stockByProduct.merge(key, stockSnapshot, this::mergeStockSnapshots);
        }
        return stockByProduct;
    }

    private Map<String, Map<LocalDate, Integer>> historicalStockByProduct(
            List<SalesForecastHistoricalStockSnapshot> historicalStockSnapshots
    ) {
        Map<String, Map<LocalDate, Integer>> stockByProduct = new HashMap<>();
        if (historicalStockSnapshots == null) {
            return stockByProduct;
        }
        for (SalesForecastHistoricalStockSnapshot snapshot : historicalStockSnapshots) {
            if (snapshot == null
                    || !hasBusinessPartnerSku(snapshot.getPartnerSku())
                    || snapshot.getStockDate() == null
                    || snapshot.getSellableStock() == null) {
                continue;
            }
            stockByProduct.computeIfAbsent(partnerSkuKey(snapshot.getPartnerSku()), ignored -> new HashMap<>())
                    .merge(snapshot.getStockDate(), snapshot.getSellableStock(), Integer::sum);
        }
        return stockByProduct;
    }

    private String productKey(DailySalesFact fact) {
        if (fact == null) {
            return "";
        }
        return scopeKey(fact.getOwnerUserId() == null ? null : String.valueOf(fact.getOwnerUserId()))
                + "|"
                + scopeKey(fact.getStoreCode())
                + "|"
                + scopeKey(fact.getSiteCode())
                + "|"
                + partnerSkuKey(fact.getPartnerSku());
    }

    private boolean hasBusinessPartnerSku(DailySalesFact fact) {
        return fact != null && hasBusinessPartnerSku(fact.getPartnerSku());
    }

    private boolean hasBusinessPartnerSku(String partnerSku) {
        return partnerSku != null && !partnerSku.isBlank() && !"-".equals(partnerSku.trim());
    }

    private String partnerSkuKey(String partnerSku) {
        return hasBusinessPartnerSku(partnerSku) ? partnerSku.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String scopeKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private SalesForecastStockSnapshot mergeStockSnapshots(
            SalesForecastStockSnapshot left,
            SalesForecastStockSnapshot right
    ) {
        return new SalesForecastStockSnapshot(
                firstNonNull(left.getOwnerUserId(), right.getOwnerUserId()),
                firstNonBlank(left.getStoreCode(), right.getStoreCode()),
                firstNonBlank(left.getSiteCode(), right.getSiteCode()),
                firstNonBlank(left.getPartnerSku(), right.getPartnerSku()),
                firstNonBlank(left.getSku(), right.getSku()),
                sumStock(left.getCurrentStock(), right.getCurrentStock()),
                firstNonBlank(left.getBrand(), right.getBrand()),
                firstNonBlank(left.getProductFulltype(), right.getProductFulltype()),
                firstNonBlank(left.getProductFamily(), right.getProductFamily()),
                firstNonBlank(left.getProductTitle(), right.getProductTitle())
        );
    }

    private Integer sumStock(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return Integer.valueOf((left == null ? 0 : left) + (right == null ? 0 : right));
    }

    private String firstNonBlank(String left, String right) {
        return isBlank(left) ? right : left;
    }

    private Long firstNonNull(Long left, Long right) {
        return left == null ? right : left;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    public interface CalendarHistoryFactorProvider {
        List<BigDecimal> dailyFactors(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                LocalDate dateFrom,
                LocalDate dateTo,
                String brand,
                String productFulltype,
                String productFamily
        );
    }

    private static class DemandSeries {
        private final Map<LocalDate, BigDecimal> adjustedUnitsByDate;
        private final int imputedStockoutDays;

        private DemandSeries(Map<LocalDate, BigDecimal> adjustedUnitsByDate, int imputedStockoutDays) {
            this.adjustedUnitsByDate = adjustedUnitsByDate;
            this.imputedStockoutDays = imputedStockoutDays;
        }

        private BigDecimal unitsInWindow(LocalDate latestFactDate, int days) {
            LocalDate dateFrom = latestFactDate.minusDays(days - 1L);
            BigDecimal total = BigDecimal.ZERO;
            for (LocalDate date = dateFrom; !date.isAfter(latestFactDate); date = date.plusDays(1)) {
                total = total.add(adjustedUnitsByDate.getOrDefault(date, BigDecimal.ZERO));
            }
            return total.setScale(4, RoundingMode.HALF_UP);
        }

        private boolean hasHistoricalStockoutImputation() {
            return imputedStockoutDays > 0;
        }
    }
}
