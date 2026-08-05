package com.nuono.next.datapull.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Test-only immutable page representation for the in-memory snapshot state machine. */
final class InMemorySnapshotPageRecord<T> {
    private final int pageNo;
    private final Integer nextPage;
    private final Boolean lastPage;
    private final Integer totalPages;
    private final List<ItemRecord<T>> items;
    private final SnapshotCollectionAuthority authority;
    private final SnapshotPage.AuthorityMode authorityMode;
    private final int sourceItemCount;
    private final int businessSkippedItemCount;
    private final List<String> businessSkippedComparisonFingerprints;

    private InMemorySnapshotPageRecord(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<ItemRecord<T>> items,
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

    static <T> InMemorySnapshotPageRecord<T> from(
            SnapshotPage<T> page,
            SnapshotItemDescriptor<T> itemDescriptor
    ) {
        List<ItemRecord<T>> items = new ArrayList<>();
        for (T item : page.getItems()) {
            T nonNullItem = Objects.requireNonNull(item, "snapshot item");
            String identity = requireStableValue(
                    itemDescriptor.stableIdentity(nonNullItem),
                    "stable identity"
            );
            String fingerprint = requireStableValue(
                    itemDescriptor.stableContentFingerprint(nonNullItem),
                    "stable content fingerprint"
            );
            items.add(new ItemRecord<>(
                    nonNullItem,
                    identity,
                    fingerprint,
                    itemDescriptor.isValidatedIdentityCandidate(nonNullItem)
            ));
        }
        return new InMemorySnapshotPageRecord<>(
                page.getPageNo(),
                page.getNextPage().isPresent() ? page.getNextPage().getAsInt() : null,
                page.getLastPage().orElse(null),
                page.getTotalPages().isPresent() ? page.getTotalPages().getAsInt() : null,
                items,
                page.getAuthority().orElse(null),
                page.getAuthorityMode(),
                page.getSourceItemCount(),
                page.getBusinessSkippedItemCount(),
                page.getBusinessSkippedComparisonFingerprints()
        );
    }

    int pageNo() {
        return pageNo;
    }

    Integer nextPage() {
        return nextPage;
    }

    Boolean lastPage() {
        return lastPage;
    }

    Integer totalPages() {
        return totalPages;
    }

    boolean sameMetadata(InMemorySnapshotPageRecord<T> other) {
        return pageNo == other.pageNo
                && Objects.equals(nextPage, other.nextPage)
                && Objects.equals(lastPage, other.lastPage)
                && Objects.equals(totalPages, other.totalPages)
                && Objects.equals(authority, other.authority)
                && authorityMode == other.authorityMode
                && sourceItemCount == other.sourceItemCount
                && businessSkippedItemCount == other.businessSkippedItemCount;
    }

    boolean sameContent(InMemorySnapshotPageRecord<T> other) {
        if (items.size() != other.items.size()) {
            return false;
        }
        for (int index = 0; index < items.size(); index++) {
            if (!items.get(index).sameContent(other.items.get(index))) {
                return false;
            }
        }
        return true;
    }

    int appendFirstIdentitiesTo(Map<String, SelectedItem<T>> firstItems) {
        int skipped = 0;
        for (ItemRecord<T> item : items) {
            SelectedItem<T> existing = firstItems.get(item.identity);
            if (existing != null) {
                skipped += 1;
                if (!existing.validatedIdentityCandidate
                        && item.validatedIdentityCandidate) {
                    firstItems.put(
                            item.identity,
                            new SelectedItem<>(item.value, true)
                    );
                }
            } else {
                firstItems.put(
                        item.identity,
                        new SelectedItem<>(item.value, item.validatedIdentityCandidate)
                );
            }
        }
        return skipped;
    }

    SnapshotCollectionAuthority authority() { return authority; }
    SnapshotPage.AuthorityMode authorityMode() { return authorityMode; }
    int sourceItemCount() { return sourceItemCount; }
    int businessSkippedItemCount() { return businessSkippedItemCount; }

    boolean sameVerificationEnvelope(InMemorySnapshotPageRecord<T> other) {
        return other != null
                && pageNo == other.pageNo
                && Objects.equals(nextPage, other.nextPage)
                && Objects.equals(lastPage, other.lastPage)
                && Objects.equals(totalPages, other.totalPages);
    }

    Map<String, Long> fingerprintCounts() {
        Map<String, Long> counts = new TreeMap<>();
        for (ItemRecord<T> item : items) {
            counts.merge(item.fingerprint, 1L, Math::addExact);
        }
        for (String fingerprint : businessSkippedComparisonFingerprints) {
            counts.merge(fingerprint, 1L, Math::addExact);
        }
        return counts;
    }

    String replaySignature() {
        StringBuilder value = new StringBuilder()
                .append(pageNo).append('|').append(nextPage).append('|')
                .append(lastPage).append('|').append(totalPages).append('|')
                .append(sourceItemCount).append('|').append(businessSkippedItemCount);
        for (ItemRecord<T> item : items) value.append('|').append(item.fingerprint);
        for (String fingerprint : businessSkippedComparisonFingerprints) {
            value.append('|').append(fingerprint);
        }
        return value.toString();
    }

    private static String requireStableValue(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a non-blank stable value");
        }
        return nonNull;
    }

    private static final class ItemRecord<T> {
        private final T value;
        private final String identity;
        private final String fingerprint;
        private final boolean validatedIdentityCandidate;

        private ItemRecord(
                T value,
                String identity,
                String fingerprint,
                boolean validatedIdentityCandidate
        ) {
            this.value = value;
            this.identity = identity;
            this.fingerprint = fingerprint;
            this.validatedIdentityCandidate = validatedIdentityCandidate;
        }

        private boolean sameContent(ItemRecord<T> other) {
            return identity.equals(other.identity) && fingerprint.equals(other.fingerprint);
        }
    }

    static final class SelectedItem<T> {
        private final T value;
        private final boolean validatedIdentityCandidate;

        private SelectedItem(T value, boolean validatedIdentityCandidate) {
            this.value = value;
            this.validatedIdentityCandidate = validatedIdentityCandidate;
        }

        T value() {
            return value;
        }
    }
}
