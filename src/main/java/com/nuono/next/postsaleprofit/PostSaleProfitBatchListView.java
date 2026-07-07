package com.nuono.next.postsaleprofit;

import java.util.List;

public class PostSaleProfitBatchListView {
    private final List<PostSaleProfitBatchRowView> rows;
    private final int total;
    private final boolean recalculationRequired;

    public PostSaleProfitBatchListView(
            List<PostSaleProfitBatchRowView> rows,
            int total,
            boolean recalculationRequired
    ) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        this.total = total;
        this.recalculationRequired = recalculationRequired;
    }

    public List<PostSaleProfitBatchRowView> getRows() { return rows; }
    public int getTotal() { return total; }
    public boolean isRecalculationRequired() { return recalculationRequired; }
}
