package com.nuono.next.sales;

import com.nuono.next.noonsync.NoonSalesSyncReadModel;
import java.util.List;

public class SalesDailyFactsView {

    private final int total;
    private final List<DailySalesFact> items;
    private final List<SalesDataQualityState> qualityStates;
    private final NoonSalesSyncReadModel syncStatus;

    public SalesDailyFactsView(List<DailySalesFact> items) {
        this(items, List.of());
    }

    public SalesDailyFactsView(List<DailySalesFact> items, List<SalesDataQualityState> qualityStates) {
        this(items, qualityStates, null);
    }

    public SalesDailyFactsView(
            List<DailySalesFact> items,
            List<SalesDataQualityState> qualityStates,
            NoonSalesSyncReadModel syncStatus
    ) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.total = this.items.size();
        this.qualityStates = qualityStates == null ? List.of() : List.copyOf(qualityStates);
        this.syncStatus = syncStatus;
    }

    public int getTotal() {
        return total;
    }

    public List<DailySalesFact> getItems() {
        return items;
    }

    public List<SalesDataQualityState> getQualityStates() {
        return qualityStates;
    }

    public NoonSalesSyncReadModel getSyncStatus() {
        return syncStatus;
    }
}
