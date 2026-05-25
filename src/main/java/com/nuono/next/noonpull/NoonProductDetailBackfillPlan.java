package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;

public class NoonProductDetailBackfillPlan {
    private final List<String> skuParents;
    private final String mode;
    private final boolean blindFullStoreFetch;

    public NoonProductDetailBackfillPlan(List<String> skuParents, String mode, boolean blindFullStoreFetch) {
        this.skuParents = skuParents == null ? new ArrayList<>() : new ArrayList<>(skuParents);
        this.mode = mode;
        this.blindFullStoreFetch = blindFullStoreFetch;
    }

    public List<String> getSkuParents() {
        return skuParents;
    }

    public String getMode() {
        return mode;
    }

    public boolean isBlindFullStoreFetch() {
        return blindFullStoreFetch;
    }
}
