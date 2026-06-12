package com.nuono.next.competitoranalysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompetitorProductChangeListView {
    private List<CompetitorProductChangeGroupView> items = new ArrayList<>();

    public static CompetitorProductChangeListView fromRows(List<CompetitorProductChangeEventRow> rows) {
        CompetitorProductChangeListView view = new CompetitorProductChangeListView();
        Map<String, CompetitorProductChangeGroupView> groups = new LinkedHashMap<>();
        for (CompetitorProductChangeEventRow row : rows == null ? List.<CompetitorProductChangeEventRow>of() : rows) {
            CompetitorProductChangeGroupView group = groups.computeIfAbsent(
                    CompetitorProductChangeGroupView.groupKey(row),
                    ignored -> CompetitorProductChangeGroupView.fromRow(row)
            );
            group.getChanges().add(CompetitorProductChangeFieldView.fromRow(row));
        }
        view.setItems(new ArrayList<>(groups.values()));
        return view;
    }

    public List<CompetitorProductChangeGroupView> getItems() { return items; }
    public void setItems(List<CompetitorProductChangeGroupView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
