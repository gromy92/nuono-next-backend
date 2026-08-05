package com.nuono.next.sales;

import java.util.List;

public class SalesDailyFactsView {

    private final int total;
    private final List<DailySalesFact> items;

    public SalesDailyFactsView(List<DailySalesFact> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.total = this.items.size();
    }

    public int getTotal() {
        return total;
    }

    public List<DailySalesFact> getItems() {
        return items;
    }

}
