package com.nuono.next.sales;

import java.util.List;

public class SalesImportBatchListView {

    private final int total;
    private final List<SalesImportBatchRecord> items;

    public SalesImportBatchListView(List<SalesImportBatchRecord> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.total = this.items.size();
    }

    public int getTotal() {
        return total;
    }

    public List<SalesImportBatchRecord> getItems() {
        return items;
    }
}
