package com.nuono.next.operationsconfig;

public class OperationConfigCurrentBundleView {

    private final OperationConfigBundleVersionView bundle;
    private final String matchType;

    public OperationConfigCurrentBundleView(OperationConfigBundleVersionView bundle, String matchType) {
        this.bundle = bundle;
        this.matchType = matchType;
    }

    public OperationConfigBundleVersionView getBundle() {
        return bundle;
    }

    public String getMatchType() {
        return matchType;
    }
}
