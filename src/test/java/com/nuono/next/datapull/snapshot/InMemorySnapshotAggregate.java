package com.nuono.next.datapull.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Test-only fenced pagination state for one in-memory snapshot task. */
final class InMemorySnapshotAggregate<T> {
    private final SnapshotItemDescriptor<T> itemDescriptor;
    private final TreeMap<Integer, InMemorySnapshotPageRecord<T>> pages = new TreeMap<>();
    private long activeFenceEpoch;
    private Integer declaredTotalPages;
    private Integer knownLastPage;
    private String poisonCode;
    private SnapshotCollectionAuthority authority;
    private SnapshotPage.AuthorityMode authorityMode;
    private final InMemorySnapshotTwoPassState<T> twoPass =
            new InMemorySnapshotTwoPassState<>();

    InMemorySnapshotAggregate(SnapshotItemDescriptor<T> itemDescriptor) {
        this.itemDescriptor = Objects.requireNonNull(itemDescriptor, "itemDescriptor");
    }

    SnapshotStageResult stagePage(long fenceEpoch, SnapshotPage<T> page) {
        if (!adoptFence(fenceEpoch)) {
            return SnapshotStageResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        if (poisonCode != null) {
            return SnapshotStageResult.rejected(poisonCode);
        }

        InMemorySnapshotPageRecord<T> candidate;
        try {
            candidate = InMemorySnapshotPageRecord.from(page, itemDescriptor);
        } catch (RuntimeException invalidItem) {
            return poison("SNAPSHOT_ITEM_DESCRIPTOR_INVALID");
        }

        InMemorySnapshotPageRecord<T> existing = pages.get(page.getPageNo());
        if (existing != null) {
            if (!existing.sameMetadata(candidate)) {
                return poison("SNAPSHOT_PAGE_METADATA_DRIFT");
            }
            if (!existing.sameContent(candidate)) {
                return poison("SNAPSHOT_PAGE_CONTENT_DRIFT");
            }
            Routing routing = routingFor(existing);
            if (routing.rejectionCode != null) {
                return poison(routing.rejectionCode);
            }
            return SnapshotStageResult.idempotentReplay(routing.nextPage, knownLastPage);
        }

        String metadataError = validateAndMergeMetadata(candidate);
        if (metadataError != null) {
            return poison(metadataError);
        }
        pages.put(candidate.pageNo(), candidate);
        if (authorityMode == SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED) {
            twoPass.stagePassOne(candidate);
        }
        Routing routing = routingFor(candidate);
        if (routing.rejectionCode != null) {
            return poison(routing.rejectionCode);
        }
        return SnapshotStageResult.staged(routing.nextPage, knownLastPage);
    }

    SnapshotStageProof<T> proveComplete(long fenceEpoch) {
        if (!adoptFence(fenceEpoch)) {
            return SnapshotStageProof.incomplete("SNAPSHOT_STAGE_STALE_FENCE");
        }
        if (poisonCode != null) {
            return SnapshotStageProof.incomplete(poisonCode);
        }
        if (knownLastPage == null) {
            return SnapshotStageProof.incomplete("SNAPSHOT_LAST_PAGE_UNKNOWN");
        }
        for (int pageNo = 1; pageNo <= knownLastPage; pageNo++) {
            if (!pages.containsKey(pageNo)) {
                return SnapshotStageProof.incomplete("SNAPSHOT_MISSING_PAGE");
            }
        }
        if (pages.lastKey() > knownLastPage) {
            return SnapshotStageProof.incomplete("SNAPSHOT_PAGE_AFTER_LAST");
        }

        Map<String, InMemorySnapshotPageRecord.SelectedItem<T>> firstItems =
                new LinkedHashMap<>();
        int skippedIdentityCount = 0;
        long businessSkippedItemCount = 0L;
        long sourceItemCount = 0L;
        for (InMemorySnapshotPageRecord<T> page : pages.values()) {
            skippedIdentityCount += page.appendFirstIdentitiesTo(firstItems);
            businessSkippedItemCount += page.businessSkippedItemCount();
            sourceItemCount += page.sourceItemCount();
        }
        return SnapshotStageProof.complete(
                knownLastPage,
                firstItems.values().stream()
                        .map(InMemorySnapshotPageRecord.SelectedItem::value)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)),
                skippedIdentityCount,
                businessSkippedItemCount,
                sourceItemCount,
                authorityMode == SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                        ? twoPass.authority() : authority
        );
    }

    SnapshotVerificationResult verifyPage(long fenceEpoch, SnapshotPage<T> page) {
        if (!adoptFence(fenceEpoch)) {
            return SnapshotVerificationResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        if (poisonCode != null) return SnapshotVerificationResult.rejected(poisonCode);
        InMemorySnapshotPageRecord<T> candidate;
        try {
            candidate = InMemorySnapshotPageRecord.from(page, itemDescriptor);
        } catch (RuntimeException invalid) {
            poisonCode = "SNAPSHOT_VERIFY_ITEM_ENCODING_INVALID";
            return SnapshotVerificationResult.rejected(poisonCode);
        }
        if (authorityMode != SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                || candidate.authorityMode() != SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                || knownLastPage == null) {
            poisonCode = "SNAPSHOT_VERIFY_MODE_INVALID";
            return SnapshotVerificationResult.rejected(poisonCode);
        }
        SnapshotVerificationResult result = twoPass.verify(
                candidate, pages.get(candidate.pageNo()), knownLastPage
        );
        if (!result.isAccepted()) poisonCode = result.getSanitizedCode();
        return result;
    }

    SnapshotComparisonResult compareNext(long fenceEpoch, int limit) {
        if (!adoptFence(fenceEpoch)) {
            return SnapshotComparisonResult.rejected("SNAPSHOT_STAGE_STALE_FENCE");
        }
        if (poisonCode != null) return SnapshotComparisonResult.rejected(poisonCode);
        SnapshotComparisonResult result = twoPass.compare(limit);
        if (!result.isAccepted()) poisonCode = result.getSanitizedCode();
        return result;
    }

    boolean canClear(long fenceEpoch) {
        return fenceEpoch >= activeFenceEpoch;
    }

    private String validateAndMergeMetadata(InMemorySnapshotPageRecord<T> page) {
        if (authorityMode != null && authorityMode != page.authorityMode()) {
            return "SNAPSHOT_AUTHORITY_MODE_DRIFT";
        }
        if (page.authorityMode() == SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                && page.authority() != null) {
            return "SNAPSHOT_TWO_PASS_AUTHORITY_CONFLICT";
        }
        if (!pages.isEmpty() && !Objects.equals(authority, page.authority())) {
            return authority == null || page.authority() == null
                    ? "SNAPSHOT_AUTHORITY_MISSING"
                    : "SNAPSHOT_AUTHORITY_GENERATION_DRIFT";
        }
        if (page.nextPage() != null && page.nextPage() != page.pageNo() + 1) {
            return "SNAPSHOT_NON_CONTIGUOUS_NEXT_PAGE";
        }
        if (page.totalPages() != null && page.totalPages() < page.pageNo()) {
            return "SNAPSHOT_TOTAL_PAGES_BEFORE_PAGE";
        }
        if (Boolean.TRUE.equals(page.lastPage()) && page.nextPage() != null) {
            return "SNAPSHOT_LAST_PAGE_HAS_NEXT";
        }
        if (Boolean.TRUE.equals(page.lastPage())
                && page.totalPages() != null
                && !page.totalPages().equals(page.pageNo())) {
            return "SNAPSHOT_LAST_TOTAL_CONFLICT";
        }
        if (Boolean.FALSE.equals(page.lastPage())
                && page.totalPages() != null
                && page.totalPages().equals(page.pageNo())) {
            return "SNAPSHOT_NOT_LAST_TOTAL_CONFLICT";
        }
        if (declaredTotalPages != null
                && page.totalPages() != null
                && !declaredTotalPages.equals(page.totalPages())) {
            return "SNAPSHOT_TOTAL_PAGES_DRIFT";
        }

        Integer candidateTotal = declaredTotalPages != null
                ? declaredTotalPages
                : page.totalPages();
        Integer candidateLast = knownLastPage;
        if (page.totalPages() != null) {
            candidateLast = mergeLastPage(candidateLast, page.totalPages());
            if (candidateLast == null) {
                return "SNAPSHOT_LAST_PAGE_DRIFT";
            }
        }
        if (Boolean.TRUE.equals(page.lastPage())) {
            candidateLast = mergeLastPage(candidateLast, page.pageNo());
            if (candidateLast == null) {
                return "SNAPSHOT_LAST_PAGE_DRIFT";
            }
        }
        if (candidateLast != null) {
            if (page.pageNo() > candidateLast) {
                return "SNAPSHOT_PAGE_AFTER_LAST";
            }
            if (Boolean.FALSE.equals(page.lastPage()) && page.pageNo() == candidateLast) {
                return "SNAPSHOT_NOT_LAST_FLAG_ON_LAST";
            }
            if (page.nextPage() != null && page.nextPage() > candidateLast) {
                return "SNAPSHOT_NEXT_PAGE_AFTER_LAST";
            }
            if (!pages.isEmpty() && pages.lastKey() > candidateLast) {
                return "SNAPSHOT_LAST_BEFORE_STAGED_PAGE";
            }
        }

        declaredTotalPages = candidateTotal;
        knownLastPage = candidateLast;
        if (pages.isEmpty()) {
            authority = page.authority();
            authorityMode = page.authorityMode();
        }
        return null;
    }

    private Routing routingFor(InMemorySnapshotPageRecord<T> page) {
        if (knownLastPage != null) {
            if (page.pageNo() == knownLastPage) {
                return Routing.last();
            }
            return Routing.next(page.pageNo() + 1);
        }
        if (page.nextPage() != null) {
            return Routing.next(page.nextPage());
        }
        if (Boolean.FALSE.equals(page.lastPage())) {
            return Routing.next(page.pageNo() + 1);
        }
        return Routing.reject("SNAPSHOT_LAST_PAGE_UNKNOWN");
    }

    private Integer mergeLastPage(Integer current, int candidate) {
        if (current == null) {
            return candidate;
        }
        return current == candidate ? current : null;
    }

    private SnapshotStageResult poison(String code) {
        poisonCode = code;
        return SnapshotStageResult.rejected(code);
    }

    private boolean adoptFence(long fenceEpoch) {
        if (fenceEpoch < activeFenceEpoch) {
            return false;
        }
        activeFenceEpoch = fenceEpoch;
        return true;
    }

    private static final class Routing {
        private final Integer nextPage;
        private final String rejectionCode;

        private Routing(Integer nextPage, String rejectionCode) {
            this.nextPage = nextPage;
            this.rejectionCode = rejectionCode;
        }

        private static Routing next(int pageNo) {
            return new Routing(pageNo, null);
        }

        private static Routing last() {
            return new Routing(null, null);
        }

        private static Routing reject(String code) {
            return new Routing(null, code);
        }
    }
}
