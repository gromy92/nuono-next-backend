package com.nuono.next.salesforecast;

import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.ProductLifecycleClassifier;
import com.nuono.next.sales.ProductLifecycleCurrentState;
import com.nuono.next.sales.ProductLifecycleResult;
import com.nuono.next.sales.ProductLifecycleSignal;
import com.nuono.next.sales.ProductLifecycleStateQuery;
import com.nuono.next.sales.ProductLifecycleStateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SalesForecastFeatureBuilder {

    private final ProductLifecycleClassifier lifecycleClassifier;
    private final ProductLifecycleStateRepository lifecycleStateRepository;

    @Autowired
    public SalesForecastFeatureBuilder(
            ProductLifecycleClassifier lifecycleClassifier,
            ObjectProvider<ProductLifecycleStateRepository> lifecycleStateRepositoryProvider
    ) {
        this(
                lifecycleClassifier,
                lifecycleStateRepositoryProvider == null ? null : lifecycleStateRepositoryProvider.getIfAvailable()
        );
    }

    public SalesForecastFeatureBuilder(ProductLifecycleClassifier lifecycleClassifier) {
        this(lifecycleClassifier, (ProductLifecycleStateRepository) null);
    }

    public SalesForecastFeatureBuilder(
            ProductLifecycleClassifier lifecycleClassifier,
            ProductLifecycleStateRepository lifecycleStateRepository
    ) {
        this.lifecycleClassifier = lifecycleClassifier;
        this.lifecycleStateRepository = lifecycleStateRepository;
    }

    public List<SalesForecastFeatureSnapshot> build(List<DailySalesFact> facts, LocalDate latestFactDate) {
        return build(facts, latestFactDate, List.of());
    }

    public List<SalesForecastFeatureSnapshot> build(
            List<DailySalesFact> facts,
            LocalDate latestFactDate,
            List<SalesForecastStockSnapshot> stockSnapshots
    ) {
        if (facts == null || facts.isEmpty() || latestFactDate == null) {
            return List.of();
        }
        Map<String, SalesForecastStockSnapshot> stockByProduct = stockByProduct(stockSnapshots);
        Map<String, List<DailySalesFact>> factsByProduct = new TreeMap<>();
        for (DailySalesFact fact : facts) {
            factsByProduct.computeIfAbsent(productKey(fact), ignored -> new ArrayList<>()).add(fact);
        }
        return factsByProduct.values().stream()
                .map(productFacts -> buildProductSnapshot(productFacts, latestFactDate, stockByProduct.get(productKey(productFacts.get(0)))))
                .sorted(Comparator.comparingInt(SalesForecastFeatureSnapshot::getHistoryUnits30).reversed()
                        .thenComparing(SalesForecastFeatureSnapshot::getPartnerSku, Comparator.nullsLast(String::compareTo))
                        .thenComparing(SalesForecastFeatureSnapshot::getSku, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private SalesForecastFeatureSnapshot buildProductSnapshot(
            List<DailySalesFact> productFacts,
            LocalDate latestFactDate,
            SalesForecastStockSnapshot stockSnapshot
    ) {
        DailySalesFact latestFact = productFacts.stream()
                .max(Comparator.comparing(DailySalesFact::getFactDate))
                .orElseThrow();
        LocalDate firstFactDate = productFacts.stream()
                .map(DailySalesFact::getFactDate)
                .min(Comparator.naturalOrder())
                .orElse(latestFactDate);
        Integer currentStock = stockSnapshot == null ? null : stockSnapshot.getCurrentStock();
        int historyUnits30 = unitsInWindow(productFacts, latestFactDate, 30);
        BigDecimal stockCoverDays = stockCoverDays(currentStock, historyUnits30);
        ProductLifecycleResult lifecycle = lifecycle(latestFact, latestFactDate, firstFactDate, currentStock, productFacts);
        return new SalesForecastFeatureSnapshot(
                latestFact.getOwnerUserId(),
                latestFact.getStoreCode(),
                latestFact.getSiteCode(),
                latestFact.getPartnerSku(),
                latestFact.getSku(),
                latestFact.getProductTitle(),
                latestFactDate,
                unitsInWindow(productFacts, latestFactDate, 7),
                historyUnits30,
                unitsInWindow(productFacts, latestFactDate, 60),
                unitsInWindow(productFacts, latestFactDate, 90),
                observedDays(firstFactDate, latestFactDate),
                currentStock,
                stockCoverDays,
                lifecycle.getCode(),
                lifecycle.getLabel(),
                lifecycle.getRuleVersion(),
                lifecycle.getQualityState(),
                lifecycle.getEvidenceJson(),
                lifecycle.getWarningCodes()
        );
    }

    private ProductLifecycleResult lifecycle(
            DailySalesFact latestFact,
            LocalDate latestFactDate,
            LocalDate firstFactDate,
            Integer currentStock,
            List<DailySalesFact> productFacts
    ) {
        if (lifecycleStateRepository == null) {
            return lifecycleClassifier.classify(new ProductLifecycleSignal(
                    latestFactDate,
                    firstFactDate,
                    currentStock,
                    productFacts
            ));
        }
        ProductLifecycleCurrentState currentState = lifecycleStateRepository.findCurrentState(new ProductLifecycleStateQuery(
                latestFact.getOwnerUserId(),
                latestFact.getStoreCode(),
                latestFact.getSiteCode(),
                latestFact.getPartnerSku(),
                latestFact.getSku()
        ));
        if (currentState == null) {
            return new ProductLifecycleResult(
                    "pending",
                    "待计算",
                    "生命周期状态尚未完成计算，预测先保留待计算标记。",
                    ProductLifecycleResult.DEFAULT_RULE_VERSION,
                    List.of("lifecycle_pending"),
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

    private List<String> lifecycleWarnings(ProductLifecycleCurrentState state) {
        if ("stockout_hold".equals(state.getQualityState())) {
            return List.of("lifecycle_stockout_hold");
        }
        if ("data_insufficient".equals(state.getLifecycleCode())
                || "data_insufficient".equals(state.getQualityState())
                || "pv_unresolvable".equals(state.getQualityState())) {
            return List.of("lifecycle_data_insufficient");
        }
        return List.of();
    }

    private int unitsInWindow(List<DailySalesFact> facts, LocalDate latestFactDate, int days) {
        LocalDate dateFrom = latestFactDate.minusDays(days - 1L);
        return facts.stream()
                .filter(fact -> !fact.getFactDate().isBefore(dateFrom) && !fact.getFactDate().isAfter(latestFactDate))
                .mapToInt(DailySalesFact::getNetUnits)
                .sum();
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
            stockByProduct.put(productKey(stockSnapshot.getPartnerSku(), stockSnapshot.getSku()), stockSnapshot);
        }
        return stockByProduct;
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
}
