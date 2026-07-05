package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionGroupCompetitorCommand {

    private List<ProductSelectionAnalysisCommand.CompetitorContext> competitors = new ArrayList<>();
    private Long operatorUserId;

    public List<ProductSelectionAnalysisCommand.CompetitorContext> getCompetitors() {
        return competitors;
    }

    public void setCompetitors(List<ProductSelectionAnalysisCommand.CompetitorContext> competitors) {
        this.competitors = competitors == null ? new ArrayList<>() : new ArrayList<>(competitors);
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
