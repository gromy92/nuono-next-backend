package com.nuono.next.noonads;

import java.util.List;

public class NoonAdvertisingStructureDiagnosticResult {
    private final List<NoonAdvertisingProductDiagnostic> productDiagnostics;
    private final List<NoonAdvertisingCampaignDiagnostic> campaignDiagnostics;

    public NoonAdvertisingStructureDiagnosticResult(
            List<NoonAdvertisingProductDiagnostic> productDiagnostics,
            List<NoonAdvertisingCampaignDiagnostic> campaignDiagnostics
    ) {
        this.productDiagnostics = productDiagnostics == null ? List.of() : List.copyOf(productDiagnostics);
        this.campaignDiagnostics = campaignDiagnostics == null ? List.of() : List.copyOf(campaignDiagnostics);
    }

    public List<NoonAdvertisingProductDiagnostic> getProductDiagnostics() { return productDiagnostics; }
    public List<NoonAdvertisingCampaignDiagnostic> getCampaignDiagnostics() { return campaignDiagnostics; }
}
