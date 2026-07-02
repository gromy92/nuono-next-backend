package com.nuono.next.noonads;

import java.util.List;

public class NoonAdvertisingCampaignDiagnostic {
    private final String campaignCode;
    private final String storeCode;
    private final String siteCode;
    private final String adSkuCode;
    private final String partnerSku;
    private final NoonAdvertisingPlanType planType;
    private final NoonAdvertisingPlanTypeConfidence planTypeConfidence;
    private final String planTypeLabel;
    private final List<String> labels;
    private final List<String> recommendedActions;

    public NoonAdvertisingCampaignDiagnostic(
            String campaignCode,
            String storeCode,
            String siteCode,
            String adSkuCode,
            String partnerSku,
            NoonAdvertisingPlanType planType,
            NoonAdvertisingPlanTypeConfidence planTypeConfidence,
            List<String> labels,
            List<String> recommendedActions
    ) {
        this.campaignCode = campaignCode;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.adSkuCode = adSkuCode;
        this.partnerSku = partnerSku;
        this.planType = planType == null ? NoonAdvertisingPlanType.UNCLASSIFIED : planType;
        this.planTypeConfidence = planTypeConfidence == null ? NoonAdvertisingPlanTypeConfidence.UNKNOWN : planTypeConfidence;
        this.planTypeLabel = this.planType.getLabel();
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        this.recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }

    public String getCampaignCode() { return campaignCode; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getAdSkuCode() { return adSkuCode; }
    public String getPartnerSku() { return partnerSku; }
    public String getProductIdentityKey() { return NoonAdvertisingProductIdentity.key(storeCode, siteCode, partnerSku); }
    public String getAdvertisingIdentityKey() { return NoonAdvertisingProductIdentity.advertisingKey(storeCode, siteCode, partnerSku, adSkuCode); }
    public boolean isProductIdentityResolved() { return NoonAdvertisingProductIdentity.resolved(partnerSku); }
    /** Compatibility alias only. Business identity must use partnerSku. */
    @Deprecated
    public String getSku() { return partnerSku != null && !partnerSku.isBlank() ? partnerSku : adSkuCode; }
    public NoonAdvertisingPlanType getPlanType() { return planType; }
    public NoonAdvertisingPlanTypeConfidence getPlanTypeConfidence() { return planTypeConfidence; }
    public String getPlanTypeLabel() { return planTypeLabel; }
    public List<String> getLabels() { return labels; }
    public List<String> getRecommendedActions() { return recommendedActions; }
}
