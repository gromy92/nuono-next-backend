package com.nuono.next.noonads;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoonAdvertisingProductDiagnostic {
    private final String storeCode;
    private final String siteCode;
    private final String adSkuCode;
    private final String partnerSku;
    private final long campaignCount;
    private final long queryCount;
    private final NoonAdvertisingProductDiagnosisType diagnosisType;
    private final String diagnosisLabel;
    private final int priorityScore;
    private final int coreCampaignCount;
    private final int explorationCampaignCount;
    private final int unclassifiedCampaignCount;
    private final NoonAdvertisingStructureStatus structureStatus;
    private final List<String> labels;
    private final List<String> recommendedActions;
    private final Map<NoonAdvertisingPlanType, Integer> planTypeCounts;
    private final boolean rankDataAvailable;

    public NoonAdvertisingProductDiagnostic(
            String storeCode,
            String siteCode,
            String adSkuCode,
            String partnerSku,
            long campaignCount,
            long queryCount,
            NoonAdvertisingProductDiagnosisType diagnosisType,
            int priorityScore,
            int coreCampaignCount,
            int explorationCampaignCount,
            int unclassifiedCampaignCount,
            NoonAdvertisingStructureStatus structureStatus,
            List<String> labels,
            List<String> recommendedActions,
            Map<NoonAdvertisingPlanType, Integer> planTypeCounts,
            boolean rankDataAvailable
    ) {
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.adSkuCode = adSkuCode;
        this.partnerSku = partnerSku;
        this.campaignCount = campaignCount;
        this.queryCount = queryCount;
        this.diagnosisType = diagnosisType == null ? NoonAdvertisingProductDiagnosisType.INSUFFICIENT_DATA : diagnosisType;
        this.diagnosisLabel = this.diagnosisType.getLabel();
        this.priorityScore = Math.max(priorityScore, 0);
        this.coreCampaignCount = Math.max(coreCampaignCount, 0);
        this.explorationCampaignCount = Math.max(explorationCampaignCount, 0);
        this.unclassifiedCampaignCount = Math.max(unclassifiedCampaignCount, 0);
        this.structureStatus = structureStatus == null ? NoonAdvertisingStructureStatus.INSUFFICIENT_DATA : structureStatus;
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        this.recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
        this.planTypeCounts = planTypeCounts == null ? Map.of() : new LinkedHashMap<>(planTypeCounts);
        this.rankDataAvailable = rankDataAvailable;
    }

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
    public long getCampaignCount() { return campaignCount; }
    public long getQueryCount() { return queryCount; }
    public NoonAdvertisingProductDiagnosisType getDiagnosisType() { return diagnosisType; }
    public String getDiagnosisLabel() { return diagnosisLabel; }
    public int getPriorityScore() { return priorityScore; }
    public int getCoreCampaignCount() { return coreCampaignCount; }
    public int getExplorationCampaignCount() { return explorationCampaignCount; }
    public int getUnclassifiedCampaignCount() { return unclassifiedCampaignCount; }
    public NoonAdvertisingStructureStatus getStructureStatus() { return structureStatus; }
    public List<String> getLabels() { return labels; }
    public List<String> getRecommendedActions() { return recommendedActions; }
    public Map<NoonAdvertisingPlanType, Integer> getPlanTypeCounts() { return planTypeCounts; }
    public boolean isRankDataAvailable() { return rankDataAvailable; }
}
