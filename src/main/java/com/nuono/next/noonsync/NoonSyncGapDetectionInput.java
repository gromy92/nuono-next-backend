package com.nuono.next.noonsync;

import java.time.LocalDate;

public class NoonSyncGapDetectionInput {

    private final NoonSyncAccountOrigin accountOrigin;
    private final NoonSyncScope scope;
    private final boolean noonBindingReady;
    private final boolean providerConfigured;
    private final NoonProductWorkspaceState productWorkspaceState;
    private final NoonSalesCoverageState salesCoverageState;
    private final LocalDate salesBackfillFrom;
    private final LocalDate salesBackfillTo;

    public NoonSyncGapDetectionInput(
            NoonSyncAccountOrigin accountOrigin,
            NoonSyncScope scope,
            boolean noonBindingReady,
            boolean providerConfigured,
            NoonProductWorkspaceState productWorkspaceState,
            NoonSalesCoverageState salesCoverageState,
            LocalDate salesBackfillFrom,
            LocalDate salesBackfillTo
    ) {
        this.accountOrigin = accountOrigin;
        this.scope = scope;
        this.noonBindingReady = noonBindingReady;
        this.providerConfigured = providerConfigured;
        this.productWorkspaceState = productWorkspaceState;
        this.salesCoverageState = salesCoverageState;
        this.salesBackfillFrom = salesBackfillFrom;
        this.salesBackfillTo = salesBackfillTo;
    }

    public NoonSyncAccountOrigin getAccountOrigin() {
        return accountOrigin;
    }

    public NoonSyncScope getScope() {
        return scope;
    }

    public boolean isNoonBindingReady() {
        return noonBindingReady;
    }

    public boolean isProviderConfigured() {
        return providerConfigured;
    }

    public NoonProductWorkspaceState getProductWorkspaceState() {
        return productWorkspaceState;
    }

    public NoonSalesCoverageState getSalesCoverageState() {
        return salesCoverageState;
    }

    public LocalDate getSalesBackfillFrom() {
        return salesBackfillFrom;
    }

    public LocalDate getSalesBackfillTo() {
        return salesBackfillTo;
    }
}
