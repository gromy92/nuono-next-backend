package com.nuono.next.datapull.advertising;

import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Decodes and normalizes at most one bounded raw-stage chunk. */
final class AdvertisingFactChunkPreparer {
    private static final Pattern NOON_SKU = Pattern.compile("^Z[A-Za-z0-9]+(-[0-9]+)?$");
    private static final String SOURCE_SYSTEM = "noon_ads";
    private final AdvertisingStagedFactCodec codec = new AdvertisingStagedFactCodec();

    AdvertisingFactChunk prepare(
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation,
            List<AdvertisingRawStageRow> rows,
            Set<String> persistedIdentities
    ) {
        List<AdvertisingRawStageRow> source = List.copyOf(rows);
        if (source.isEmpty()) throw new IllegalArgumentException("raw stage chunk is empty");
        if (source.size() > generation.getStagedItemCount()
                - generation.getProcessedItemCount()) {
            throw new IllegalArgumentException("raw stage chunk exceeds declared accounting");
        }
        Set<String> identities = new HashSet<>(persistedIdentities);
        Set<String> activeCodes = new HashSet<>();
        for (AdvertisingCampaignRef item : command.getActiveCampaigns()) {
            activeCodes.add(item.getCampaignCode());
        }
        List<AdvertisingGenerationFactRow> campaigns = new ArrayList<>();
        List<AdvertisingGenerationFactRow> queries = new ArrayList<>();
        String digest = generation.getDigestChainSha256();
        int skipped = 0;
        int campaignSkipped = 0;
        int queryPageProofs = 0;
        int activeMatched = 0;
        AdvertisingRawStageRow previous = null;

        for (AdvertisingRawStageRow row : source) {
            validateSource(command, generation, previous, row);
            AdvertisingStagedFact staged = decodeAndVerify(row);
            digest = AdvertisingDigestChain.append(digest, row);
            if (staged.isQueryPageProof()) {
                requireQueryPage(command, row, staged.getQueryFact());
                if (row.getItemOrdinal() != 0) {
                    throw new IllegalArgumentException("query page proof must be first");
                }
                queryPageProofs++;
            } else if (staged.getKind() == AdvertisingStagedFact.Kind.CAMPAIGN) {
                requireCampaignPage(command, row);
                NoonAdvertisingCampaignFact fact = staged.getCampaignFact();
                String identity = "campaign:" + lengthPrefixed(fact.getCampaignCode());
                if (!identities.add(identity)) {
                    skipped++;
                    campaignSkipped++;
                } else {
                    scopeCampaign(command, generation, fact, campaigns.size());
                    campaigns.add(AdvertisingGenerationFactRow.campaign(
                            command.getTaskId(), row, identity, fact
                    ));
                    if (activeCodes.contains(fact.getCampaignCode())) activeMatched++;
                }
            } else {
                NoonAdvertisingQueryFact fact = staged.getQueryFact();
                requireQueryPage(command, row, fact);
                if (row.getItemOrdinal() == 0) {
                    throw new IllegalArgumentException("query page proof is missing");
                }
                normalizeQuery(fact);
                fact.setQueryHash(queryHash(fact));
                String identity = "query:" + fact.getQueryHash();
                if (!identities.add(identity)) {
                    skipped++;
                } else {
                    scopeQuery(command, generation, fact, queries.size());
                    queries.add(AdvertisingGenerationFactRow.query(
                            command.getTaskId(), row, identity, fact
                    ));
                }
            }
            previous = row;
        }
        AdvertisingRawStageRow last = source.get(source.size() - 1);
        return new AdvertisingFactChunk(
                campaigns, queries, source.size(), skipped, campaignSkipped,
                queryPageProofs, activeMatched,
                last.getPageNo(), last.getItemOrdinal(), digest
        );
    }

    private AdvertisingStagedFact decodeAndVerify(AdvertisingRawStageRow row) {
        AdvertisingStagedFact staged = Objects.requireNonNull(
                codec.decode(row.getPayload()),
                "decoded advertising fact"
        );
        if (!Objects.equals(codec.stableIdentity(staged), row.getStableIdentity())
                || !Objects.equals(
                        codec.stableContentFingerprint(staged), row.getContentFingerprint()
                )) {
            throw new IllegalArgumentException("advertising raw payload integrity drift");
        }
        return staged;
    }

    private void validateSource(
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation,
            AdvertisingRawStageRow previous,
            AdvertisingRawStageRow row
    ) {
        if (row == null || !Objects.equals(row.getTaskId(), command.getTaskId())
                || row.getPageNo() == null || row.getPageNo() < 1
                || row.getPageNo() > generation.getLastPage()
                || row.getItemOrdinal() == null || row.getItemOrdinal() < 0
                || row.getPageItemCount() == null
                || row.getItemOrdinal() >= row.getPageItemCount()
                || !after(generation, row) || (previous != null && !after(previous, row))) {
            throw new IllegalArgumentException("advertising raw stage order is invalid");
        }
    }

    private void requireCampaignPage(
            AdvertisingApplyCommand command,
            AdvertisingRawStageRow row
    ) {
        if (row.getPageNo() > command.getCampaignPageCount()) {
            throw new IllegalArgumentException("campaign fact appeared outside campaign pages");
        }
    }

    private void requireQueryPage(
            AdvertisingApplyCommand command,
            AdvertisingRawStageRow row,
            NoonAdvertisingQueryFact fact
    ) {
        int index = row.getPageNo() - command.getCampaignPageCount() - 1;
        if (index < 0 || index >= command.getActiveCampaigns().size()
                || !command.getActiveCampaigns().get(index).getCampaignCode()
                        .equals(fact.getCampaignCode())) {
            throw new IllegalArgumentException("query fact campaign page mismatch");
        }
    }

    private void scopeCampaign(
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation,
            NoonAdvertisingCampaignFact fact,
            int chunkIndex
    ) {
        fact.setId(Math.addExact(
                generation.getCampaignIdStart(),
                Math.addExact(generation.getCampaignFactCount(), chunkIndex)
        ));
        fact.setBatchId(generation.getBatchId());
        fact.setSourceSystem(SOURCE_SYSTEM);
        fact.setOwnerUserId(command.getOwnerUserId());
        fact.setProjectCode(command.getProjectCode());
        fact.setStoreCode(command.getStoreCode());
        fact.setSiteCode(command.getSiteCode());
        fact.setReportDateFrom(command.getReportDate());
        fact.setReportDateTo(command.getReportDate());
    }

    private void scopeQuery(
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation,
            NoonAdvertisingQueryFact fact,
            int chunkIndex
    ) {
        fact.setId(Math.addExact(
                generation.getQueryIdStart(),
                Math.addExact(generation.getQueryFactCount(), chunkIndex)
        ));
        fact.setBatchId(generation.getBatchId());
        fact.setSourceSystem(SOURCE_SYSTEM);
        fact.setOwnerUserId(command.getOwnerUserId());
        fact.setProjectCode(command.getProjectCode());
        fact.setStoreCode(command.getStoreCode());
        fact.setSiteCode(command.getSiteCode());
        fact.setReportDateFrom(command.getReportDate());
        fact.setReportDateTo(command.getReportDate());
    }

    private void normalizeQuery(NoonAdvertisingQueryFact fact) {
        String partner = normalize(fact.getPartnerSku());
        String adSku = normalize(fact.getAdSkuCode());
        if (adSku.isEmpty() && NOON_SKU.matcher(partner).matches()) {
            adSku = partner;
            partner = "";
        }
        fact.setPartnerSku(partner);
        fact.setAdSkuCode(adSku);
    }

    private String queryHash(NoonAdvertisingQueryFact fact) {
        String source = String.join("\u001f",
                normalize(fact.getCampaignCode()), normalize(fact.getPartnerSku()),
                normalize(fact.getAdSkuCode()), normalize(fact.getQueryText()),
                normalize(fact.getQueryKind()));
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private boolean after(AdvertisingGenerationRow cursor, AdvertisingRawStageRow row) {
        return row.getPageNo() > cursor.getCursorPageNo()
                || (Objects.equals(row.getPageNo(), cursor.getCursorPageNo())
                        && row.getItemOrdinal() > cursor.getCursorItemOrdinal());
    }

    private boolean after(AdvertisingRawStageRow left, AdvertisingRawStageRow right) {
        return right.getPageNo() > left.getPageNo()
                || (Objects.equals(right.getPageNo(), left.getPageNo())
                        && right.getItemOrdinal() > left.getItemOrdinal());
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private String lengthPrefixed(String value) { return value.length() + ":" + value + "|"; }
}
