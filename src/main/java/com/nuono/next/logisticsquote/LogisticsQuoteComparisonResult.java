package com.nuono.next.logisticsquote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogisticsQuoteComparisonResult {

    private final List<LogisticsPriceRuleFact> rows;

    public LogisticsQuoteComparisonResult(List<LogisticsPriceRuleFact> rows) {
        this.rows = rows == null ? Collections.emptyList() : new ArrayList<>(rows);
    }

    public List<LogisticsPriceRuleFact> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public LogisticsPriceRuleFact getCheapest() {
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }
}
