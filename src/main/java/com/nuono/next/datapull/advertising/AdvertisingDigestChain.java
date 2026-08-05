package com.nuono.next.datapull.advertising;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Restart-safe ordered SHA-256 chain; only its 64-byte state is persisted between chunks. */
final class AdvertisingDigestChain {
    private AdvertisingDigestChain() {
    }

    static String seed(AdvertisingApplyCommand command, AdvertisingStageManifestRow manifest) {
        MessageDigest digest = sha256();
        update(digest, "dp06-generation-v1");
        update(digest, String.valueOf(command.getOwnerUserId()));
        update(digest, command.getProjectCode());
        update(digest, command.getStoreCode());
        update(digest, command.getSiteCode());
        update(digest, command.getBusinessWindowKey());
        update(digest, command.getAuthority().getGenerationTokenSha256());
        update(digest, String.valueOf(manifest.getStagedItemCount()));
        update(digest, String.valueOf(manifest.getSourceItemCount()));
        update(digest, String.valueOf(manifest.getBusinessSkippedItemCount()));
        update(digest, String.valueOf(manifest.getDashboardItemCount()));
        update(digest, String.valueOf(manifest.getDashboardBusinessSkippedItemCount()));
        for (AdvertisingCampaignRef campaign : command.getActiveCampaigns()) {
            update(digest, campaign.getCampaignCode());
        }
        return hex(digest.digest());
    }

    static String activeCampaignDigest(AdvertisingApplyCommand command) {
        MessageDigest digest = sha256();
        update(digest, "dp06-active-campaigns-v1");
        for (AdvertisingCampaignRef campaign : command.getActiveCampaigns()) {
            update(digest, campaign.getCampaignCode());
        }
        return hex(digest.digest());
    }

    static String append(String current, AdvertisingRawStageRow row) {
        MessageDigest digest = sha256();
        update(digest, AdvertisingAdvertiser.requireIdentity(current, "digestChain"));
        update(digest, String.valueOf(row.getPageNo()));
        update(digest, String.valueOf(row.getItemOrdinal()));
        update(digest, row.getStableIdentity());
        update(digest, row.getContentFingerprint());
        return hex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
