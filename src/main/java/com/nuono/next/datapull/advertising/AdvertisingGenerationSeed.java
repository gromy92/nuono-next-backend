package com.nuono.next.datapull.advertising;

/** Fully validated immutable control row used only when a generation is first created. */
public final class AdvertisingGenerationSeed {
    private final AdvertisingApplyCommand command;
    private final AdvertisingStageManifestRow manifest;
    private final long batchId;
    private final Long campaignIdStart;
    private final Long queryIdStart;
    private final String digestChainSha256;
    private final String activeCampaignDigestSha256;

    public AdvertisingGenerationSeed(
            AdvertisingApplyCommand command,
            AdvertisingStageManifestRow manifest,
            long batchId,
            Long campaignIdStart,
            Long queryIdStart
    ) {
        this.command = command;
        this.manifest = manifest;
        this.batchId = batchId;
        this.campaignIdStart = campaignIdStart;
        this.queryIdStart = queryIdStart;
        this.activeCampaignDigestSha256 = AdvertisingDigestChain.activeCampaignDigest(command);
        this.digestChainSha256 = AdvertisingDigestChain.seed(command, manifest);
    }

    public AdvertisingApplyCommand getCommand() { return command; }
    public AdvertisingStageManifestRow getManifest() { return manifest; }
    public long getBatchId() { return batchId; }
    public Long getCampaignIdStart() { return campaignIdStart; }
    public Long getQueryIdStart() { return queryIdStart; }
    public String getDigestChainSha256() { return digestChainSha256; }
    public String getActiveCampaignDigestSha256() { return activeCampaignDigestSha256; }
}
