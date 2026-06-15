package com.nuono.next.orderfinance;

import java.util.List;

public class OrderFinanceSkuSummaryView {
    private final OrderFinanceSummaryView summary;
    private final List<OrderFinanceSummaryView> summaries;
    private final List<OrderFinanceSkuSummaryRow> rows;
    private final OrderFinanceDataStatus dataStatus;

    public OrderFinanceSkuSummaryView(
            OrderFinanceSummaryView summary,
            List<OrderFinanceSummaryView> summaries,
            List<OrderFinanceSkuSummaryRow> rows,
            OrderFinanceDataStatus dataStatus
    ) {
        this.summary = summary;
        this.summaries = summaries == null ? List.of() : List.copyOf(summaries);
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        this.dataStatus = dataStatus;
    }

    public OrderFinanceSummaryView getSummary() { return summary; }
    public List<OrderFinanceSummaryView> getSummaries() { return summaries; }
    public List<OrderFinanceSkuSummaryRow> getRows() { return rows; }
    public OrderFinanceDataStatus getDataStatus() { return dataStatus; }
}
