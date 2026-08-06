package com.nuono.next.datapull.snapshot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validated, explicitly encoded page ready for an atomic stage transaction. */
final class SnapshotStagePageCandidate<T> {
    private static final int IDENTITY_MAX_LENGTH = 240;
    private static final int PAYLOAD_MAX_UTF8_BYTES = 16_711_680;
    private static final int PAGE_PAYLOAD_MAX_UTF8_BYTES = PAYLOAD_MAX_UTF8_BYTES;
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private final int pageNo;
    private final Integer nextPage;
    private final Boolean lastPage;
    private final Integer totalPages;
    private final List<EncodedItem<T>> items;
    private final SnapshotCollectionAuthority authority;
    private final SnapshotPage.AuthorityMode authorityMode;
    private final int sourceItemCount;
    private final int businessSkippedItemCount;
    private final List<String> businessSkippedComparisonFingerprints;

    private SnapshotStagePageCandidate(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<EncodedItem<T>> items,
            SnapshotCollectionAuthority authority,
            SnapshotPage.AuthorityMode authorityMode,
            int sourceItemCount,
            int businessSkippedItemCount,
            List<String> businessSkippedComparisonFingerprints
    ) {
        this.pageNo = pageNo;
        this.nextPage = nextPage;
        this.lastPage = lastPage;
        this.totalPages = totalPages;
        this.items = List.copyOf(items);
        this.authority = authority;
        this.authorityMode = authorityMode;
        this.sourceItemCount = sourceItemCount;
        this.businessSkippedItemCount = businessSkippedItemCount;
        this.businessSkippedComparisonFingerprints = List.copyOf(
                businessSkippedComparisonFingerprints
        );
    }

    static <T> SnapshotStagePageCandidate<T> from(
            SnapshotPage<T> page,
            SnapshotItemDescriptor<T> descriptor,
            SnapshotPayloadCodec<T> payloadCodec
    ) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(payloadCodec, "payloadCodec");
        List<EncodedItem<T>> encodedItems = new ArrayList<>();
        int pagePayloadBytes = 0;
        for (T item : page.getItems()) {
            T value = Objects.requireNonNull(item, "snapshot item");
            String identity = stableValue(
                    descriptor.stableIdentity(value), "stable identity", IDENTITY_MAX_LENGTH
            );
            String fingerprint = requireFingerprint(descriptor.stableContentFingerprint(value));
            String payload = requirePayload(payloadCodec.encode(value));
            pagePayloadBytes = Math.addExact(
                    pagePayloadBytes, payload.getBytes(StandardCharsets.UTF_8).length
            );
            if (pagePayloadBytes > PAGE_PAYLOAD_MAX_UTF8_BYTES) {
                throw new IllegalArgumentException("snapshot provider page payload is too large");
            }
            encodedItems.add(new EncodedItem<>(
                    value, identity, fingerprint, payload,
                    descriptor.isValidatedIdentityCandidate(value),
                    descriptor.isAbsenceReconciliationSafe(value)
            ));
        }
        List<String> skippedFingerprints = new ArrayList<>();
        for (String fingerprint : page.getBusinessSkippedComparisonFingerprints()) {
            skippedFingerprints.add(requireFingerprint(fingerprint));
        }
        return new SnapshotStagePageCandidate<>(
                page.getPageNo(),
                page.getNextPage().isPresent() ? page.getNextPage().getAsInt() : null,
                page.getLastPage().orElse(null),
                page.getTotalPages().isPresent() ? page.getTotalPages().getAsInt() : null,
                encodedItems,
                page.getAuthority().orElse(null),
                page.getAuthorityMode(),
                page.getSourceItemCount(),
                page.getBusinessSkippedItemCount(),
                skippedFingerprints
        );
    }

    boolean sameMetadata(SnapshotStagePageRow row) {
        return row != null
                && Objects.equals(row.getPageNo(), pageNo)
                && Objects.equals(row.getNextPage(), nextPage)
                && Objects.equals(row.getLastPage(), lastPage)
                && Objects.equals(row.getTotalPages(), totalPages)
                && Objects.equals(row.getItemCount(), items.size())
                && Objects.equals(row.getSourceItemCount(), sourceItemCount)
                && Objects.equals(
                        row.getBusinessSkippedItemCount(), businessSkippedItemCount
                );
    }

    boolean sameContent(List<SnapshotStageItemRow> rows) {
        if (rows == null || rows.size() != items.size()) {
            return false;
        }
        for (int index = 0; index < items.size(); index++) {
            SnapshotStageItemRow row = rows.get(index);
            EncodedItem<T> item = items.get(index);
            if (!Objects.equals(row.getItemOrdinal(), index)
                    || !Objects.equals(row.getStableIdentity(), item.stableIdentity)
                    || !Objects.equals(row.getContentFingerprint(), item.contentFingerprint)
                    || !Objects.equals(row.getValidatedIdentityCandidate(),
                            item.validatedIdentityCandidate)
                    || !Objects.equals(row.getAbsenceReconciliationSafe(),
                            item.absenceReconciliationSafe)) {
                return false;
            }
        }
        return true;
    }

    private static String stableValue(String value, String label, int maxLength) {
        String nonNull = Objects.requireNonNull(value, label);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim()) || nonNull.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(label + " must be a stable non-blank value");
        }
        if (nonNull.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds its persistence column");
        }
        return nonNull;
    }

    private static String requireFingerprint(String value) {
        String fingerprint = Objects.requireNonNull(value, "content fingerprint");
        if (!FINGERPRINT.matcher(fingerprint).matches()) {
            throw new IllegalArgumentException(
                    "content fingerprint must be 64 lowercase hexadecimal characters"
            );
        }
        return fingerprint;
    }

    private static String requirePayload(String value) {
        String payload = Objects.requireNonNull(value, "encoded payload");
        if (payload.getBytes(StandardCharsets.UTF_8).length > PAYLOAD_MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("encoded payload exceeds its persistence column");
        }
        return payload;
    }

    int getPageNo() {
        return pageNo;
    }

    Integer getNextPage() {
        return nextPage;
    }

    Boolean getLastPage() {
        return lastPage;
    }

    Integer getTotalPages() {
        return totalPages;
    }

    List<EncodedItem<T>> getItems() {
        return items;
    }

    SnapshotCollectionAuthority getAuthority() {
        return authority;
    }

    SnapshotPage.AuthorityMode getAuthorityMode() {
        return authorityMode;
    }

    int getSourceItemCount() {
        return sourceItemCount;
    }

    int getBusinessSkippedItemCount() {
        return businessSkippedItemCount;
    }

    List<String> getBusinessSkippedComparisonFingerprints() {
        return businessSkippedComparisonFingerprints;
    }

    static final class EncodedItem<T> {
        private final T value;
        private final String stableIdentity;
        private final String contentFingerprint;
        private final String payload;
        private final boolean validatedIdentityCandidate;
        private final boolean absenceReconciliationSafe;

        private EncodedItem(
                T value,
                String stableIdentity,
                String contentFingerprint,
                String payload,
                boolean validatedIdentityCandidate,
                boolean absenceReconciliationSafe
        ) {
            this.value = value;
            this.stableIdentity = stableIdentity;
            this.contentFingerprint = contentFingerprint;
            this.payload = payload;
            this.validatedIdentityCandidate = validatedIdentityCandidate;
            this.absenceReconciliationSafe = absenceReconciliationSafe;
        }

        T getValue() {
            return value;
        }

        String getStableIdentity() {
            return stableIdentity;
        }

        String getContentFingerprint() {
            return contentFingerprint;
        }

        String getPayload() {
            return payload;
        }

        boolean isValidatedIdentityCandidate() {
            return validatedIdentityCandidate;
        }

        boolean isAbsenceReconciliationSafe() {
            return absenceReconciliationSafe;
        }
    }
}
