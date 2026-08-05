package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** V2 binds all campaign work and apply to one provider-native collection authority. */
final class AdvertisingCheckpointCodec {
    private static final String VERSION = "v2";
    private static final String LEGACY_VERSION = "v1";
    private static final String NONE = "-";

    String encode(AdvertisingCheckpoint checkpoint) {
        List<String> parts = new ArrayList<>();
        parts.add(VERSION);
        parts.add(checkpoint.getPhase().name());
        parts.add(String.valueOf(checkpoint.getConsecutiveRetryAttempt()));
        parts.add(String.valueOf(checkpoint.getNextCampaignIndex()));
        parts.add(checkpoint.getAdvertiser() == null
                ? NONE
                : encodeText(checkpoint.getAdvertiser().getAdvertiserCode()));
        appendAuthority(parts, checkpoint.getAuthority());
        parts.add(String.valueOf(checkpoint.getActiveCampaigns().size()));
        for (AdvertisingCampaignRef campaign : checkpoint.getActiveCampaigns()) {
            parts.add(encodeText(campaign.getCampaignCode()));
            parts.add(encodeText(campaign.getCampaignName()));
        }
        return String.join("|", parts);
    }

    AdvertisingCheckpoint decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return AdvertisingCheckpoint.initial();
        }
        if (!encoded.equals(encoded.trim())) {
            throw new IllegalArgumentException("advertising checkpoint has outer whitespace");
        }
        String[] parts = encoded.split("\\|", -1);
        try {
            if (parts.length >= 1 && LEGACY_VERSION.equals(parts[0])) {
                return decodeLegacy(parts);
            }
            if (parts.length < 11 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported advertising checkpoint");
            }
            AdvertisingCheckpoint.Phase phase = AdvertisingCheckpoint.Phase.valueOf(parts[1]);
            int retryAttempt = Integer.parseInt(parts[2]);
            int nextCampaignIndex = Integer.parseInt(parts[3]);
            AdvertisingAdvertiser advertiser = advertiser(parts[4]);
            AdvertisingCampaignEnumerationAuthority authority = authority(parts);
            List<AdvertisingCampaignRef> campaigns = campaigns(parts, 10);
            return AdvertisingCheckpoint.restored(
                    phase,
                    advertiser,
                    campaigns,
                    authority,
                    nextCampaignIndex,
                    retryAttempt
            );
        } catch (AuthoritylessCheckpointException legacy) {
            throw legacy;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid advertising checkpoint", invalid);
        }
    }

    private AdvertisingCheckpoint decodeLegacy(String[] parts) {
        if (parts.length < 6) {
            throw new IllegalArgumentException("unsupported advertising checkpoint");
        }
        AdvertisingCheckpoint.Phase phase = AdvertisingCheckpoint.Phase.valueOf(parts[1]);
        int retryAttempt = Integer.parseInt(parts[2]);
        int nextCampaignIndex = Integer.parseInt(parts[3]);
        AdvertisingAdvertiser advertiser = advertiser(parts[4]);
        List<AdvertisingCampaignRef> campaigns = campaigns(parts, 5);
        if (phase == AdvertisingCheckpoint.Phase.CAMPAIGN_QUERY
                || phase == AdvertisingCheckpoint.Phase.APPLY) {
            throw new AuthoritylessCheckpointException();
        }
        return AdvertisingCheckpoint.restored(
                phase,
                advertiser,
                campaigns,
                null,
                nextCampaignIndex,
                retryAttempt
        );
    }

    private void appendAuthority(
            List<String> parts,
            AdvertisingCampaignEnumerationAuthority authority
    ) {
        if (authority == null) {
            for (int index = 0; index < 5; index++) {
                parts.add(NONE);
            }
            return;
        }
        parts.add(SnapshotCollectionAuthority.Kind.COMPLETE_EXPORT.name());
        parts.add(authority.getGenerationTokenSha256());
        parts.add(encodeText(authority.getProviderAsOfUtc().toString()));
        parts.add(String.valueOf(authority.getDeclaredCampaignCount()));
        parts.add(String.valueOf(authority.isComplete()));
    }

    private AdvertisingCampaignEnumerationAuthority authority(String[] parts) {
        boolean absent = true;
        for (int index = 5; index <= 9; index++) {
            absent &= NONE.equals(parts[index]);
        }
        if (absent) {
            return null;
        }
        for (int index = 5; index <= 9; index++) {
            if (NONE.equals(parts[index])) {
                throw new IllegalArgumentException("partial advertising authority");
            }
        }
        if (!SnapshotCollectionAuthority.Kind.COMPLETE_EXPORT.name().equals(parts[5])) {
            throw new IllegalArgumentException("invalid advertising authority kind");
        }
        if (!"true".equals(parts[9]) && !"false".equals(parts[9])) {
            throw new IllegalArgumentException("invalid advertising authority completeness");
        }
        return AdvertisingCampaignEnumerationAuthority.fromPersistedFields(
                parts[6],
                LocalDateTime.parse(decodeText(parts[7])),
                Long.parseLong(parts[8]),
                Boolean.parseBoolean(parts[9])
        );
    }

    private AdvertisingAdvertiser advertiser(String value) {
        return NONE.equals(value) ? null : new AdvertisingAdvertiser(decodeText(value));
    }

    private List<AdvertisingCampaignRef> campaigns(String[] parts, int countIndex) {
        int count = Integer.parseInt(parts[countIndex]);
        int firstCampaign = countIndex + 1;
        if (count < 0 || parts.length != firstCampaign + Math.multiplyExact(count, 2)) {
            throw new IllegalArgumentException("advertising campaign checkpoint count mismatch");
        }
        List<AdvertisingCampaignRef> campaigns = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            campaigns.add(new AdvertisingCampaignRef(
                    decodeText(parts[firstCampaign + index * 2]),
                    decodeText(parts[firstCampaign + index * 2 + 1])
            ));
        }
        return campaigns;
    }

    private String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String decodeText(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    static final class AuthoritylessCheckpointException extends IllegalArgumentException {
        private AuthoritylessCheckpointException() {
            super("legacy advertising checkpoint has no collection authority");
        }
    }
}
