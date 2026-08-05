package com.nuono.next.datapull.snapshot;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Canonical, domain-separated digests for two-pass snapshot observations. */
final class SnapshotFingerprintMultiset {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final byte[] PAGE_DOMAIN = utf8("nuono:snapshot:verify-page:v1");
    private static final byte[] CHAIN_DOMAIN = utf8("nuono:snapshot:multiset-chain:v1");

    private SnapshotFingerprintMultiset() {
    }

    static List<SnapshotFingerprintCountRow> counts(SnapshotStagePageCandidate<?> page) {
        TreeMap<String, Long> counts = new TreeMap<>();
        for (SnapshotStagePageCandidate.EncodedItem<?> item : page.getItems()) {
            counts.merge(item.getContentFingerprint(), 1L, Math::addExact);
        }
        for (String fingerprint : page.getBusinessSkippedComparisonFingerprints()) {
            counts.merge(fingerprint, 1L, Math::addExact);
        }
        List<SnapshotFingerprintCountRow> rows = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            rows.add(new SnapshotFingerprintCountRow(entry.getKey(), entry.getValue(), 0L));
        }
        return rows;
    }

    static String pageDigest(SnapshotStagePageCandidate<?> page) {
        MessageDigest digest = sha256();
        add(digest, PAGE_DOMAIN);
        add(digest, String.valueOf(page.getPageNo()));
        add(digest, nullable(page.getNextPage()));
        add(digest, nullable(page.getLastPage()));
        add(digest, nullable(page.getTotalPages()));
        add(digest, String.valueOf(page.getSourceItemCount()));
        add(digest, String.valueOf(page.getBusinessSkippedItemCount()));
        for (SnapshotStagePageCandidate.EncodedItem<?> item : page.getItems()) {
            add(digest, item.getContentFingerprint());
        }
        for (String fingerprint : page.getBusinessSkippedComparisonFingerprints()) {
            add(digest, fingerprint);
        }
        return hex(digest.digest());
    }

    static String initialChainDigest() {
        return hex(digest(CHAIN_DOMAIN));
    }

    static String extendChain(String priorDigest, SnapshotFingerprintCountRow row) {
        requireDigest(priorDigest);
        String fingerprint = requireDigest(row.getContentFingerprint());
        long count = requireEqualPositiveCounts(row);
        MessageDigest digest = sha256();
        add(digest, priorDigest);
        add(digest, fingerprint);
        add(digest, String.valueOf(count));
        return hex(digest.digest());
    }

    static long requireEqualPositiveCounts(SnapshotFingerprintCountRow row) {
        if (row == null || row.getPassOneCount() == null || row.getPassTwoCount() == null
                || row.getPassOneCount() < 1L
                || !row.getPassOneCount().equals(row.getPassTwoCount())) {
            throw new IllegalArgumentException("snapshot fingerprint multiplicity drift");
        }
        return row.getPassOneCount();
    }

    private static void add(MessageDigest digest, String value) {
        add(digest, utf8(value));
    }

    private static void add(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String nullable(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String requireDigest(String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid SHA-256 digest");
        }
        return value;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] digest(byte[] value) {
        return sha256().digest(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }
}
