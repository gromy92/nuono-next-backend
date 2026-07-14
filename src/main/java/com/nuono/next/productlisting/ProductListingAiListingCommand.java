package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingAiListingCommand {

    private ProductListingDraftCommand draft;
    private String operatorRequirement;
    private List<ProductListingAiCompetitorMaterial> competitorMaterials = new ArrayList<>();

    public ProductListingDraftCommand getDraft() {
        return draft;
    }

    public void setDraft(ProductListingDraftCommand draft) {
        this.draft = draft;
    }

    public String getOperatorRequirement() {
        return operatorRequirement;
    }

    public void setOperatorRequirement(String operatorRequirement) {
        this.operatorRequirement = operatorRequirement;
    }

    public List<ProductListingAiCompetitorMaterial> getCompetitorMaterials() {
        return competitorMaterials;
    }

    public void setCompetitorMaterials(List<ProductListingAiCompetitorMaterial> competitorMaterials) {
        this.competitorMaterials = competitorMaterials == null ? new ArrayList<>() : new ArrayList<>(competitorMaterials);
    }
}
