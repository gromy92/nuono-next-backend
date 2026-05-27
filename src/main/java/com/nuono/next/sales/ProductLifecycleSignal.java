package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;

public class ProductLifecycleSignal {

    private final LocalDate analysisDate;
    private final LocalDate firstListedDate;
    private final Integer availableStock;
    private final List<DailySalesFact> facts;

    public ProductLifecycleSignal(
            LocalDate analysisDate,
            LocalDate firstListedDate,
            Integer availableStock,
            List<DailySalesFact> facts
    ) {
        this.analysisDate = analysisDate;
        this.firstListedDate = firstListedDate;
        this.availableStock = availableStock;
        this.facts = facts == null ? List.of() : List.copyOf(facts);
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public LocalDate getFirstListedDate() {
        return firstListedDate;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public List<DailySalesFact> getFacts() {
        return facts;
    }
}
