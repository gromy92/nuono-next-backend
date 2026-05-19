package com.nuono.next.product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductPublishPlan {

    private final ProductMasterSnapshotView publishableSnapshot;
    private final List<String> blockers;

    public ProductPublishPlan(ProductMasterSnapshotView publishableSnapshot, List<String> blockers) {
        this.publishableSnapshot = publishableSnapshot;
        this.blockers = blockers == null ? List.of() : new ArrayList<>(blockers);
    }

    public ProductMasterSnapshotView getPublishableSnapshot() {
        return publishableSnapshot;
    }

    public List<String> getBlockers() {
        return Collections.unmodifiableList(blockers);
    }

    public boolean isPublishable() {
        return blockers.isEmpty();
    }
}
