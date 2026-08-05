package com.nuono.next.datapull.snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Test-only two-pass multiset/replay state mirroring the durable Adapter contract. */
final class InMemorySnapshotTwoPassState<T> {
    private final Map<String, Long> passOne = new TreeMap<>();
    private final Map<String, Long> passTwo = new TreeMap<>();
    private final Map<Integer, String> pageSignatures = new TreeMap<>();
    private String state = "PASS_ONE";
    private Integer nextPage;
    private long passOneSourceCount;
    private long passTwoSourceCount;
    private String compareAfter;
    private String compareDigest = sha256("nuono:snapshot:multiset-chain:v1");
    private long comparedSourceCount;
    private SnapshotCollectionAuthority authority;

    void stagePassOne(InMemorySnapshotPageRecord<T> page) {
        if (!"PASS_ONE".equals(state)) {
            throw new IllegalStateException("pass one is already closed");
        }
        merge(passOne, page.fingerprintCounts());
        passOneSourceCount = Math.addExact(passOneSourceCount, page.sourceItemCount());
    }

    SnapshotVerificationResult verify(
            InMemorySnapshotPageRecord<T> page,
            InMemorySnapshotPageRecord<T> passOnePage,
            int knownLastPage
    ) {
        if (!page.sameVerificationEnvelope(passOnePage)) {
            return SnapshotVerificationResult.rejected(
                    "SNAPSHOT_VERIFY_PAGE_METADATA_DRIFT"
            );
        }
        if ("PASS_ONE".equals(state)) {
            if (page.pageNo() != 1) {
                return SnapshotVerificationResult.rejected("SNAPSHOT_VERIFY_START_INVALID");
            }
            state = "VERIFYING";
            nextPage = 1;
        }
        String signature = page.replaySignature();
        String prior = pageSignatures.get(page.pageNo());
        if (prior != null) {
            if (!prior.equals(signature)) {
                return SnapshotVerificationResult.rejected("SNAPSHOT_VERIFY_PAGE_DRIFT");
            }
            return "COMPARING".equals(state) || "VERIFIED".equals(state)
                    ? SnapshotVerificationResult.complete()
                    : SnapshotVerificationResult.replayed(nextPage);
        }
        if (!"VERIFYING".equals(state) || !Objects.equals(nextPage, page.pageNo())) {
            return SnapshotVerificationResult.rejected("SNAPSHOT_VERIFY_CURSOR_DRIFT");
        }
        pageSignatures.put(page.pageNo(), signature);
        merge(passTwo, page.fingerprintCounts());
        passTwoSourceCount = Math.addExact(passTwoSourceCount, page.sourceItemCount());
        if (page.pageNo() == knownLastPage) {
            nextPage = null;
            state = "COMPARING";
            if (pageSignatures.size() != knownLastPage
                    || passOneSourceCount != passTwoSourceCount) {
                return SnapshotVerificationResult.rejected("SNAPSHOT_VERIFY_EXTENT_DRIFT");
            }
            return SnapshotVerificationResult.complete();
        }
        nextPage = page.pageNo() + 1;
        return SnapshotVerificationResult.accepted(nextPage);
    }

    SnapshotComparisonResult compare(int limit) {
        if ("VERIFIED".equals(state)) return SnapshotComparisonResult.verified();
        if (!"COMPARING".equals(state)) {
            return SnapshotComparisonResult.rejected("SNAPSHOT_COMPARE_STATE_INVALID");
        }
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(passOne.keySet());
        keys.addAll(passTwo.keySet());
        List<String> batch = new ArrayList<>(limit);
        for (String key : keys) {
            if ((compareAfter == null || key.compareTo(compareAfter) > 0)
                    && batch.size() < limit) {
                batch.add(key);
            }
        }
        if (batch.isEmpty()) {
            if (comparedSourceCount != passOneSourceCount) {
                return SnapshotComparisonResult.rejected("SNAPSHOT_COMPARE_EXTENT_DRIFT");
            }
            authority = SnapshotCollectionAuthority.fromTwoPassObservation(
                    compareDigest, passOneSourceCount
            );
            state = "VERIFIED";
            return SnapshotComparisonResult.verified();
        }
        for (String key : batch) {
            long first = passOne.getOrDefault(key, 0L);
            long second = passTwo.getOrDefault(key, 0L);
            if (first < 1L || first != second) {
                return SnapshotComparisonResult.rejected("SNAPSHOT_MULTISET_DRIFT");
            }
            comparedSourceCount = Math.addExact(comparedSourceCount, first);
            compareDigest = sha256(compareDigest + "|" + key + "|" + first);
            compareAfter = key;
        }
        return SnapshotComparisonResult.moreWork();
    }

    SnapshotCollectionAuthority authority() {
        return authority;
    }

    private void merge(Map<String, Long> target, Map<String, Long> source) {
        source.forEach((key, value) -> target.merge(key, value, Math::addExact));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
