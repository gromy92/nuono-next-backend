package com.nuono.next.competitoranalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompetitorWatchProductListView {
    private List<CompetitorWatchProductListItemView> items = new ArrayList<>();
    private CompetitorPaginationView pagination;

    public static CompetitorWatchProductListView fromRows(
            List<CompetitorWatchProductListRow> rows,
            CompetitorWatchProductQuery query,
            long total
    ) {
        CompetitorWatchProductListView view = new CompetitorWatchProductListView();
        List<CompetitorWatchProductListRow> safeRows = rows == null ? List.of() : rows;
        view.setItems(safeRows.stream()
                .map(CompetitorWatchProductListItemView::fromRow)
                .collect(Collectors.toList()));
        view.setPagination(CompetitorPaginationView.of(query, total));
        return view;
    }

    public List<CompetitorWatchProductListItemView> getItems() { return items; }
    public void setItems(List<CompetitorWatchProductListItemView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
    public CompetitorPaginationView getPagination() { return pagination; }
    public void setPagination(CompetitorPaginationView pagination) { this.pagination = pagination; }
}
