package com.nuono.next.datapull.advertising;

import java.util.List;

/** One bounded, integrity-checked raw-stage advance ready for one local transaction. */
public final class AdvertisingFactChunk {
    private final List<AdvertisingGenerationFactRow> campaigns;
    private final List<AdvertisingGenerationFactRow> queries;
    private final int processedCount;
    private final int skippedIdentityCount;
    private final int campaignSkippedIdentityCount;
    private final int queryPageProofCount;
    private final int matchedActiveCount;
    private final int lastPageNo;
    private final int lastItemOrdinal;
    private final String digestChainSha256;

    AdvertisingFactChunk(
            List<AdvertisingGenerationFactRow> campaigns,
            List<AdvertisingGenerationFactRow> queries,
            int processedCount,
            int skippedIdentityCount,
            int campaignSkippedIdentityCount,
            int queryPageProofCount,
            int matchedActiveCount,
            int lastPageNo,
            int lastItemOrdinal,
            String digestChainSha256
    ) {
        this.campaigns = List.copyOf(campaigns);
        this.queries = List.copyOf(queries);
        this.processedCount = processedCount;
        this.skippedIdentityCount = skippedIdentityCount;
        this.campaignSkippedIdentityCount = campaignSkippedIdentityCount;
        this.queryPageProofCount = queryPageProofCount;
        this.matchedActiveCount = matchedActiveCount;
        this.lastPageNo = lastPageNo;
        this.lastItemOrdinal = lastItemOrdinal;
        this.digestChainSha256 = digestChainSha256;
    }

    public List<AdvertisingGenerationFactRow> getCampaigns() { return campaigns; }
    public List<AdvertisingGenerationFactRow> getQueries() { return queries; }
    public int getProcessedCount() { return processedCount; }
    public int getSkippedIdentityCount() { return skippedIdentityCount; }
    public int getCampaignSkippedIdentityCount() { return campaignSkippedIdentityCount; }
    public int getQueryPageProofCount() { return queryPageProofCount; }
    public int getMatchedActiveCount() { return matchedActiveCount; }
    public int getLastPageNo() { return lastPageNo; }
    public int getLastItemOrdinal() { return lastItemOrdinal; }
    public String getDigestChainSha256() { return digestChainSha256; }
}
