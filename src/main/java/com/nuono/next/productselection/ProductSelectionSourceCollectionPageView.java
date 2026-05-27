package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionSourceCollectionPageView {

    private List<ProductSelectionSourceCollectionView> items = new ArrayList<>();
    private int total;
    private int page;
    private int pageSize;

    public List<ProductSelectionSourceCollectionView> getItems() {
        return items;
    }

    public void setItems(List<ProductSelectionSourceCollectionView> items) {
        this.items = items;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
