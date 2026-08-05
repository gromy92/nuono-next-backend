package com.nuono.next.officialwarehouse.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Domain-separated observation fingerprint for one deterministic DP-07-A business skip. */
final class Dp07BusinessSkippedRowFingerprint {
    private static final byte[] DOMAIN =
            "nuono:dp07a:business-skipped-raw-row:v1".getBytes(StandardCharsets.UTF_8);

    private Dp07BusinessSkippedRowFingerprint() {
    }

    static String from(InventoryItem item, ObjectMapper objectMapper) {
        try {
            if (item == null || item.rawPayload == null) {
                throw new IllegalArgumentException("DP-07-A skipped raw row is missing");
            }
            String canonical = Dp07InventorySnapshotItem.canonicalJson(
                    objectMapper, item.rawPayload
            );
            Dp07InventoryColumnContract.boundedJson(canonical);
            MessageDigest digest = sha256();
            add(digest, DOMAIN);
            add(digest, canonical.getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (Dp07InventoryColumnContract.ContainerContractException capacity) {
            throw capacity;
        } catch (RuntimeException invalidRawRow) {
            throw new Dp07InventorySnapshotItem.ProviderRowContractException(
                    "DP-07-A skipped raw row cannot be normalized", invalidRawRow
            );
        }
    }

    private static void add(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
