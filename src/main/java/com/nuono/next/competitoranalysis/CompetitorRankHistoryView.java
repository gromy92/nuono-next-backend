package com.nuono.next.competitoranalysis;

import java.util.ArrayList;
import java.util.List;

public class CompetitorRankHistoryView {
    private List<CompetitorLatestRankPointRow> items = new ArrayList<>();

    public static CompetitorRankHistoryView fromRows(List<CompetitorLatestRankPointRow> rows) {
        CompetitorRankHistoryView view = new CompetitorRankHistoryView();
        view.setItems(rows);
        return view;
    }

    public List<CompetitorLatestRankPointRow> getItems() { return items; }
    public void setItems(List<CompetitorLatestRankPointRow> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
