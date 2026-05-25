package com.nuono.next.logisticsquote;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LogisticsQuoteComparisonService {

    private final LogisticsQuoteFactRepository repository;

    public LogisticsQuoteComparisonService(LogisticsQuoteFactRepository repository) {
        this.repository = repository;
    }

    public LogisticsQuoteComparisonResult compareBasePrices(LogisticsQuoteComparisonQuery query) {
        List<LogisticsPriceRuleFact> rows = repository.findComparablePriceRules(query)
                .stream()
                .sorted(Comparator.comparing(LogisticsPriceRuleFact::getUnitPrice))
                .collect(Collectors.toList());
        return new LogisticsQuoteComparisonResult(rows);
    }
}
