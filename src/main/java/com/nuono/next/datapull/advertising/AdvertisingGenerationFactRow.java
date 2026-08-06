package com.nuono.next.datapull.advertising;

import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.util.Objects;

/** One validated fact written to an invisible generation in source order. */
public final class AdvertisingGenerationFactRow {
    private final long taskId;
    private final int pageNo;
    private final int itemOrdinal;
    private final String normalizedIdentity;
    private final String contentFingerprint;
    private final NoonAdvertisingCampaignFact campaign;
    private final NoonAdvertisingQueryFact query;

    private AdvertisingGenerationFactRow(
            long taskId,
            AdvertisingRawStageRow source,
            String normalizedIdentity,
            NoonAdvertisingCampaignFact campaign,
            NoonAdvertisingQueryFact query
    ) {
        this.taskId = taskId;
        this.pageNo = Objects.requireNonNull(source.getPageNo(), "source.pageNo");
        this.itemOrdinal = Objects.requireNonNull(source.getItemOrdinal(), "source.itemOrdinal");
        this.normalizedIdentity = AdvertisingAdvertiser.requireIdentity(
                normalizedIdentity,
                "normalizedIdentity"
        );
        this.contentFingerprint = AdvertisingAdvertiser.requireIdentity(
                source.getContentFingerprint(),
                "contentFingerprint"
        );
        this.campaign = campaign;
        this.query = query;
        if ((campaign == null) == (query == null)) {
            throw new IllegalArgumentException("exactly one advertising fact kind is required");
        }
    }

    public static AdvertisingGenerationFactRow campaign(
            long taskId,
            AdvertisingRawStageRow source,
            String identity,
            NoonAdvertisingCampaignFact fact
    ) {
        return new AdvertisingGenerationFactRow(
                taskId, source, identity, Objects.requireNonNull(fact, "fact"), null
        );
    }

    public static AdvertisingGenerationFactRow query(
            long taskId,
            AdvertisingRawStageRow source,
            String identity,
            NoonAdvertisingQueryFact fact
    ) {
        return new AdvertisingGenerationFactRow(
                taskId, source, identity, null, Objects.requireNonNull(fact, "fact")
        );
    }

    public long getTaskId() { return taskId; }
    public int getPageNo() { return pageNo; }
    public int getItemOrdinal() { return itemOrdinal; }
    public String getNormalizedIdentity() { return normalizedIdentity; }
    public String getContentFingerprint() { return contentFingerprint; }
    public NoonAdvertisingCampaignFact getCampaign() { return campaign; }
    public NoonAdvertisingQueryFact getQuery() { return query; }
}
