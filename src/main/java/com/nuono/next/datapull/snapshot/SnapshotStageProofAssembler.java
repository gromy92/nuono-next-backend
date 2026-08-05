package com.nuono.next.datapull.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Decodes and revalidates database rows before they can become a complete snapshot proof. */
final class SnapshotStageProofAssembler<T> {
    private final SnapshotItemDescriptor<T> descriptor;
    private final SnapshotPayloadCodec<T> payloadCodec;

    SnapshotStageProofAssembler(
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> payloadCodec
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec");
    }

    SnapshotStageProof<T> assemble(
            long taskId,
            SnapshotStageAggregateRow aggregate,
            List<SnapshotStagePageRow> pages,
            List<SnapshotStageItemRow> items
    ) {
        if (pages == null || items == null) {
            return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_STATE_INVALID");
        }
        String metadataError = SnapshotStageMetadata.validateProof(aggregate, pages);
        if (metadataError != null) {
            return SnapshotStageProof.incomplete(metadataError);
        }

        Map<String, SelectedItem<T>> firstItems = new LinkedHashMap<>();
        int skippedIdentityCount = 0;
        long businessSkippedItemCount = 0L;
        long sourceItemCount = 0L;
        int itemIndex = 0;
        for (SnapshotStagePageRow page : pages) {
            if (!Objects.equals(page.getTaskId(), taskId)
                    || page.getSourceItemCount() == null
                    || page.getBusinessSkippedItemCount() == null
                    || page.getSourceItemCount() < 0
                    || page.getBusinessSkippedItemCount() < 0
                    || (long) page.getSourceItemCount()
                            != (long) page.getItemCount()
                                    + page.getBusinessSkippedItemCount()) {
                return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_STATE_INVALID");
            }
            sourceItemCount = Math.addExact(sourceItemCount, page.getSourceItemCount());
            businessSkippedItemCount += page.getBusinessSkippedItemCount();
            for (int ordinal = 0; ordinal < page.getItemCount(); ordinal++) {
                if (itemIndex >= items.size()) {
                    return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_ITEM_MISSING");
                }
                SnapshotStageItemRow row = items.get(itemIndex++);
                if (!Objects.equals(row.getTaskId(), taskId)
                        || !Objects.equals(row.getPageNo(), page.getPageNo())
                        || !Objects.equals(row.getItemOrdinal(), ordinal)) {
                    return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_ITEM_ORDER_INVALID");
                }
                T decoded;
                try {
                    decoded = Objects.requireNonNull(
                            payloadCodec.decode(row.getPayload()),
                            "decoded payload"
                    );
                } catch (RuntimeException decodeFailure) {
                    return SnapshotStageProof.incomplete("SNAPSHOT_PAYLOAD_DECODE_FAILED");
                }
                String identity;
                String fingerprint;
                boolean validatedIdentityCandidate;
                try {
                    identity = stableValue(descriptor.stableIdentity(decoded));
                    fingerprint = stableValue(descriptor.stableContentFingerprint(decoded));
                    validatedIdentityCandidate = descriptor.isValidatedIdentityCandidate(decoded);
                } catch (RuntimeException descriptorFailure) {
                    return SnapshotStageProof.incomplete("SNAPSHOT_PAYLOAD_INTEGRITY_FAILED");
                }
                if (!Objects.equals(identity, row.getStableIdentity())
                        || !Objects.equals(fingerprint, row.getContentFingerprint())) {
                    return SnapshotStageProof.incomplete("SNAPSHOT_PAYLOAD_INTEGRITY_FAILED");
                }
                SelectedItem<T> existing = firstItems.get(identity);
                if (existing != null) {
                    skippedIdentityCount++;
                    if (!existing.validatedIdentityCandidate
                            && validatedIdentityCandidate) {
                        firstItems.put(
                                identity,
                                new SelectedItem<>(decoded, true)
                        );
                    }
                } else {
                    firstItems.put(
                            identity,
                            new SelectedItem<>(decoded, validatedIdentityCandidate)
                    );
                }
            }
        }
        if (itemIndex != items.size()) {
            return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_ITEM_ORPHAN");
        }
        SnapshotCollectionAuthority authority;
        try {
            authority = SnapshotStageAuthority.restore(aggregate);
        } catch (RuntimeException invalidAuthority) {
            return SnapshotStageProof.incomplete("SNAPSHOT_AUTHORITY_STATE_INVALID");
        }
        return SnapshotStageProof.complete(
                aggregate.getKnownLastPage(),
                firstItems.values().stream()
                        .map((selected) -> selected.value)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)),
                skippedIdentityCount,
                businessSkippedItemCount,
                sourceItemCount,
                authority
        );
    }

    private String stableValue(String value) {
        String nonNull = Objects.requireNonNull(value, "stable item value");
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim()) || nonNull.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("unstable item value");
        }
        return nonNull;
    }

    private static final class SelectedItem<T> {
        private final T value;
        private final boolean validatedIdentityCandidate;

        private SelectedItem(T value, boolean validatedIdentityCandidate) {
            this.value = value;
            this.validatedIdentityCandidate = validatedIdentityCandidate;
        }
    }
}
