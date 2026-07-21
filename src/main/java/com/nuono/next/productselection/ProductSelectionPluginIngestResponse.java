package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionPluginIngestResponse {

    private ProductSelectionSourceCollectionView sourceCollection;
    private List<ProductSelectionPluginIngestCommand.PluginWarning> warnings = new ArrayList<>();
    private boolean deduped;

    public ProductSelectionSourceCollectionView getSourceCollection() {
        return sourceCollection;
    }

    public void setSourceCollection(ProductSelectionSourceCollectionView sourceCollection) {
        this.sourceCollection = sourceCollection;
    }

    public List<ProductSelectionPluginIngestCommand.PluginWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ProductSelectionPluginIngestCommand.PluginWarning> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public boolean isDeduped() {
        return deduped;
    }

    public void setDeduped(boolean deduped) {
        this.deduped = deduped;
    }
}
