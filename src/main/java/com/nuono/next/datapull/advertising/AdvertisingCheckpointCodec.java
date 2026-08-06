package com.nuono.next.datapull.advertising;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** V3 stores only campaign identities/status plus bounded cursors; raw facts stay in stage tables. */
final class AdvertisingCheckpointCodec {
    private static final String VERSION = "v3";
    private static final String NONE = "-";

    String encode(AdvertisingCheckpoint checkpoint) {
        List<String> parts = new ArrayList<>();
        parts.add(VERSION);
        parts.add(checkpoint.getPhase().name());
        parts.add(String.valueOf(checkpoint.getConsecutiveRetryAttempt()));
        parts.add(String.valueOf(checkpoint.getNextCampaignPage()));
        parts.add(String.valueOf(checkpoint.getCampaignPageCount()));
        parts.add(String.valueOf(checkpoint.getNextCampaignIndex()));
        parts.add(checkpoint.getDeclaredCampaignCount() == null
                ? NONE : String.valueOf(checkpoint.getDeclaredCampaignCount()));
        parts.add(checkpoint.getAdvertiser() == null
                ? NONE : encodeText(checkpoint.getAdvertiser().getAdvertiserCode()));
        appendAuthority(parts, checkpoint.getAuthority());
        parts.add(String.valueOf(checkpoint.getCampaigns().size()));
        for (AdvertisingCampaignObservation observation : checkpoint.getCampaigns()) {
            parts.add(encodeText(observation.getCampaign().getCampaignCode()));
            parts.add(encodeText(observation.getCampaign().getCampaignName()));
            parts.add(String.valueOf(observation.isActive()));
        }
        return String.join("|", parts);
    }

    AdvertisingCheckpoint decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return AdvertisingCheckpoint.initial();
        if (!encoded.equals(encoded.trim())) {
            throw new IllegalArgumentException("advertising checkpoint has outer whitespace");
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length > 0 && !VERSION.equals(parts[0])) {
            throw new LegacyCheckpointException();
        }
        try {
            if (parts.length < 12) throw new IllegalArgumentException("checkpoint is truncated");
            AdvertisingCheckpoint.Phase phase = AdvertisingCheckpoint.Phase.valueOf(parts[1]);
            int retry = Integer.parseInt(parts[2]);
            int nextPage = Integer.parseInt(parts[3]);
            int pageCount = Integer.parseInt(parts[4]);
            int nextCampaign = Integer.parseInt(parts[5]);
            Long declaredCount = NONE.equals(parts[6]) ? null : Long.parseLong(parts[6]);
            AdvertisingAdvertiser advertiser = NONE.equals(parts[7])
                    ? null : new AdvertisingAdvertiser(decodeText(parts[7]));
            AdvertisingCampaignEnumerationAuthority authority = authority(parts);
            int count = Integer.parseInt(parts[11]);
            if (count < 0 || parts.length != 12 + Math.multiplyExact(count, 3)) {
                throw new IllegalArgumentException("campaign checkpoint count mismatch");
            }
            List<AdvertisingCampaignObservation> campaigns = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int base = 12 + index * 3;
                String active = parts[base + 2];
                if (!"true".equals(active) && !"false".equals(active)) {
                    throw new IllegalArgumentException("invalid campaign active flag");
                }
                campaigns.add(new AdvertisingCampaignObservation(
                        new AdvertisingCampaignRef(
                                decodeText(parts[base]),
                                decodeText(parts[base + 1])
                        ),
                        Boolean.parseBoolean(active)
                ));
            }
            return AdvertisingCheckpoint.restored(
                    phase, advertiser, campaigns, authority, nextPage, pageCount,
                    nextCampaign, declaredCount, retry
            );
        } catch (RuntimeException invalid) {
            if (invalid instanceof LegacyCheckpointException) throw invalid;
            throw new IllegalArgumentException("invalid advertising checkpoint", invalid);
        }
    }

    private void appendAuthority(
            List<String> parts,
            AdvertisingCampaignEnumerationAuthority authority
    ) {
        if (authority == null) {
            parts.add(NONE); parts.add(NONE); parts.add(NONE);
            return;
        }
        parts.add(authority.getGenerationTokenSha256());
        parts.add(String.valueOf(authority.getDeclaredCampaignCount()));
        parts.add(String.valueOf(authority.isComplete()));
    }

    private AdvertisingCampaignEnumerationAuthority authority(String[] parts) {
        if (NONE.equals(parts[8]) && NONE.equals(parts[9]) && NONE.equals(parts[10])) {
            return null;
        }
        if (NONE.equals(parts[8]) || NONE.equals(parts[9]) || NONE.equals(parts[10])
                || (!"true".equals(parts[10]) && !"false".equals(parts[10]))) {
            throw new IllegalArgumentException("partial advertising authority");
        }
        return AdvertisingCampaignEnumerationAuthority.fromPersistedFields(
                parts[8], Long.parseLong(parts[9]), Boolean.parseBoolean(parts[10])
        );
    }

    private String encodeText(String value) {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                safe.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String decodeText(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    static final class LegacyCheckpointException extends IllegalArgumentException {
        private LegacyCheckpointException() {
            super("legacy advertising checkpoint requires a clean stage restart");
        }
    }
}
