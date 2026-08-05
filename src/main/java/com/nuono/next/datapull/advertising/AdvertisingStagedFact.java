package com.nuono.next.datapull.advertising;

import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.util.Objects;

/** Explicit union stored in the generic fenced snapshot stage until the whole 2+C pull closes. */
public final class AdvertisingStagedFact {
    private static final String QUERY_PAGE_PROOF_TEXT = "__DP06_QUERY_PAGE_PROOF_V1__";
    private static final String QUERY_PAGE_PROOF_KIND = "dp06_query_page_proof_v1";
    public enum Kind {
        CAMPAIGN,
        QUERY
    }

    private final Kind kind;
    private final NoonAdvertisingCampaignFact campaignFact;
    private final NoonAdvertisingQueryFact queryFact;

    private AdvertisingStagedFact(
            Kind kind,
            NoonAdvertisingCampaignFact campaignFact,
            NoonAdvertisingQueryFact queryFact
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.campaignFact = campaignFact;
        this.queryFact = queryFact;
        if ((kind == Kind.CAMPAIGN) != (campaignFact != null)
                || (kind == Kind.QUERY) != (queryFact != null)) {
            throw new IllegalArgumentException("staged fact kind and payload must agree");
        }
    }

    public static AdvertisingStagedFact campaign(NoonAdvertisingCampaignFact fact) {
        return new AdvertisingStagedFact(
                Kind.CAMPAIGN,
                Objects.requireNonNull(fact, "fact"),
                null
        );
    }

    public static AdvertisingStagedFact query(NoonAdvertisingQueryFact fact) {
        return new AdvertisingStagedFact(
                Kind.QUERY,
                null,
                Objects.requireNonNull(fact, "fact")
        );
    }

    public static AdvertisingStagedFact queryPageProof(AdvertisingCampaignRef campaign) {
        AdvertisingCampaignRef value = Objects.requireNonNull(campaign, "campaign");
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode(value.getCampaignCode());
        fact.setCampaignName(value.getCampaignName());
        fact.setAdSkuCode("");
        fact.setPartnerSku("");
        fact.setQueryText(QUERY_PAGE_PROOF_TEXT);
        fact.setQueryKind(QUERY_PAGE_PROOF_KIND);
        return query(fact);
    }

    public boolean isQueryPageProof() {
        return kind == Kind.QUERY
                && QUERY_PAGE_PROOF_TEXT.equals(queryFact.getQueryText())
                && QUERY_PAGE_PROOF_KIND.equals(queryFact.getQueryKind());
    }

    public Kind getKind() {
        return kind;
    }

    public NoonAdvertisingCampaignFact getCampaignFact() {
        return campaignFact;
    }

    public NoonAdvertisingQueryFact getQueryFact() {
        return queryFact;
    }
}
