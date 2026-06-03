package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ProductLifecycleCalculationJobService {

    private final ProductLifecycleCalculationSource source;
    private final ProductLifecycleStateRepository repository;
    private final ProductLifecycleListingDateResolver listingDateResolver;
    private final ProductLifecycleFeatureBuilder featureBuilder;
    private final ProductLifecycleSalesCorrectionService salesCorrectionService;
    private final ProductLifecycleClassifier classifier;
    private final ProductLifecycleTransitionService transitionService;
    private final ProductLifecycleRuleProvider ruleProvider;

    @Autowired
    public ProductLifecycleCalculationJobService(
            ProductLifecycleCalculationSource source,
            ProductLifecycleStateRepository repository,
            ProductLifecycleListingDateResolver listingDateResolver,
            ProductLifecycleFeatureBuilder featureBuilder,
            ProductLifecycleSalesCorrectionService salesCorrectionService,
            ProductLifecycleClassifier classifier,
            ProductLifecycleTransitionService transitionService,
            ObjectProvider<ProductLifecycleRuleProvider> ruleProviderProvider
    ) {
        this.source = source;
        this.repository = repository;
        this.listingDateResolver = listingDateResolver;
        this.featureBuilder = featureBuilder;
        this.salesCorrectionService = salesCorrectionService;
        this.classifier = classifier;
        this.transitionService = transitionService;
        this.ruleProvider = ruleProviderProvider == null
                ? ProductLifecycleRuleProvider.defaultV1()
                : ruleProviderProvider.getIfAvailable(ProductLifecycleRuleProvider::defaultV1);
    }

    public ProductLifecycleCalculationJobService(
            ProductLifecycleCalculationSource source,
            ProductLifecycleStateRepository repository,
            ProductLifecycleListingDateResolver listingDateResolver,
            ProductLifecycleFeatureBuilder featureBuilder,
            ProductLifecycleSalesCorrectionService salesCorrectionService,
            ProductLifecycleClassifier classifier,
            ProductLifecycleTransitionService transitionService
    ) {
        this(
                source,
                repository,
                listingDateResolver,
                featureBuilder,
                salesCorrectionService,
                classifier,
                transitionService,
                ProductLifecycleRuleProvider.defaultV1()
        );
    }

    public ProductLifecycleCalculationJobService(
            ProductLifecycleCalculationSource source,
            ProductLifecycleStateRepository repository,
            ProductLifecycleListingDateResolver listingDateResolver,
            ProductLifecycleFeatureBuilder featureBuilder,
            ProductLifecycleSalesCorrectionService salesCorrectionService,
            ProductLifecycleClassifier classifier,
            ProductLifecycleTransitionService transitionService,
            ProductLifecycleRuleProvider ruleProvider
    ) {
        this.source = source;
        this.repository = repository;
        this.listingDateResolver = listingDateResolver;
        this.featureBuilder = featureBuilder;
        this.salesCorrectionService = salesCorrectionService;
        this.classifier = classifier;
        this.transitionService = transitionService;
        this.ruleProvider = ruleProvider == null ? ProductLifecycleRuleProvider.defaultV1() : ruleProvider;
    }

    public ProductLifecycleJobRecord run(ProductLifecycleCalculationScope scope) {
        return runForAnchors(scope, List.of(scope.getAnchorDate()), false);
    }

    public ProductLifecycleJobRecord runHistoricalBackfill(ProductLifecycleCalculationScope scope) {
        return runForAnchors(scope, null, true);
    }

    private ProductLifecycleJobRecord runForAnchors(
            ProductLifecycleCalculationScope scope,
            List<LocalDate> anchorDates,
            boolean resetBeforeRun
    ) {
        Long jobId = repository.nextJobId();
        LocalDateTime startedAt = LocalDateTime.now();
        ProductLifecycleRuleSet ruleSet = ruleProvider.resolve(scope);
        int processedCount = 0;
        int changedCount = 0;
        int heldCount = 0;
        int dataInsufficientCount = 0;
        List<String> failures = new ArrayList<>();
        try {
            if (resetBeforeRun) {
                repository.resetScope(scope);
            }
            List<LocalDate> anchors = anchorDates == null
                    ? List.of()
                    : normalizedAnchorDates(anchorDates, scope.getAnchorDate());
            List<ProductLifecycleStateQuery> productScopes = source.listProductScopes(scope);
            for (ProductLifecycleStateQuery query : productScopes) {
                List<LocalDate> productAnchors = anchorDates == null
                        ? historicalAnchorDates(scope, query)
                        : anchors;
                for (LocalDate anchor : productAnchors) {
                    ProductLifecycleCalculationScope anchorScope = scopeWithAnchorDate(scope, anchor);
                    try {
                        ProductLifecycleCurrentState before = repository.findCurrentState(query);
                        ProductLifecycleCurrentState after = calculateOne(anchorScope, query, jobId, ruleSet);
                        processedCount++;
                        if (isHeld(after)) {
                            heldCount++;
                        }
                        if ("data_insufficient".equals(after.getLifecycleCode())) {
                            dataInsufficientCount++;
                        }
                        if (before != null
                                && !before.getLifecycleCode().equals(after.getLifecycleCode())
                                && !isHeld(after)) {
                            changedCount++;
                        }
                    } catch (RuntimeException exception) {
                        failures.add(failure(query, exception));
                    }
                }
            }
        } catch (RuntimeException exception) {
            failures.add("{\"scope\":\"job\",\"message\":\"" + escape(exception.getMessage()) + "\"}");
        }
        String status = status(processedCount, failures);
        ProductLifecycleJobRecord job = new ProductLifecycleJobRecord(
                jobId,
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode(),
                scope.getAnchorDate(),
                ruleSet.getRuleVersion(),
                status,
                processedCount,
                changedCount,
                heldCount,
                dataInsufficientCount,
                failures.isEmpty() ? null : "[" + String.join(",", failures) + "]",
                startedAt,
                LocalDateTime.now(),
                scope.getTriggeredByUserId(),
                scope.getTriggerSource()
        );
        repository.saveJob(job);
        return job;
    }

    private List<LocalDate> historicalAnchorDates(
            ProductLifecycleCalculationScope scope,
            ProductLifecycleStateQuery query
    ) {
        return normalizedAnchorDates(source.listHistoricalAnchorDates(scope, query), scope.getAnchorDate());
    }

    private List<LocalDate> normalizedAnchorDates(List<LocalDate> anchorDates, LocalDate finalAnchorDate) {
        SortedSet<LocalDate> sorted = new TreeSet<>();
        if (anchorDates != null) {
            for (LocalDate anchorDate : anchorDates) {
                if (anchorDate == null) {
                    continue;
                }
                if (finalAnchorDate != null && anchorDate.isAfter(finalAnchorDate)) {
                    continue;
                }
                sorted.add(anchorDate);
            }
        }
        if (finalAnchorDate != null) {
            sorted.add(finalAnchorDate);
        }
        return new ArrayList<>(sorted);
    }

    private ProductLifecycleCalculationScope scopeWithAnchorDate(
            ProductLifecycleCalculationScope scope,
            LocalDate anchorDate
    ) {
        return new ProductLifecycleCalculationScope(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode(),
                anchorDate,
                scope.getRuleVersion(),
                scope.isRerun(),
                scope.isExplicitRuleVersion(),
                scope.getTriggeredByUserId(),
                scope.getTriggerSource()
        );
    }

    private ProductLifecycleCurrentState calculateOne(
            ProductLifecycleCalculationScope scope,
            ProductLifecycleStateQuery query,
            Long jobId,
            ProductLifecycleRuleSet ruleSet
    ) {
        LocalDate anchorDate = scope.getAnchorDate();
        LocalDate from = anchorDate.minusDays(59);
        ProductLifecycleListingSignals listingSignals = source.loadListingSignals(query, anchorDate);
        ProductLifecycleListingDateResolution listing = listingDateResolver.resolve(listingSignals, anchorDate);
        List<DailySalesFact> facts = source.loadFacts(query, from, anchorDate);
        ProductLifecycleFeatureSnapshot features = featureBuilder.build(query, anchorDate, facts);
        List<SalesActivityWindowRecord> windows = source.loadActivityWindows(query, from, anchorDate);
        ProductLifecycleCorrectedFeatureSnapshot corrected = salesCorrectionService.correct(
                query,
                anchorDate,
                features,
                facts,
                windows
        );
        ProductLifecycleResult candidate = classifier.classify(new ProductLifecycleClassificationInput(
                query,
                anchorDate,
                listing,
                features,
                corrected
        ), ruleSet);
        boolean stockoutDistorted = source.isStockoutDistorted(query, features);
        return transitionService.apply(new ProductLifecycleTransitionCommand(
                query,
                candidate,
                anchorDate,
                listing.getListingDate(),
                listing.getSource(),
                jobId,
                stockoutDistorted
        ));
    }

    private boolean isHeld(ProductLifecycleCurrentState state) {
        return "stockout_hold".equals(state.getQualityState())
                || "data_insufficient_hold".equals(state.getQualityState());
    }

    private String status(int processedCount, List<String> failures) {
        if (failures.isEmpty()) {
            return "succeeded";
        }
        return processedCount == 0 ? "failed" : "partial_failed";
    }

    private String failure(ProductLifecycleStateQuery query, RuntimeException exception) {
        return "{"
                + "\"partnerSku\":\"" + escape(query.getPartnerSku()) + "\","
                + "\"sku\":\"" + escape(query.getSku()) + "\","
                + "\"message\":\"" + escape(exception.getMessage()) + "\""
                + "}";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
