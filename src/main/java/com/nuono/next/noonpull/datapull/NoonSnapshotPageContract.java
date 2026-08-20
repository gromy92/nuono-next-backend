package com.nuono.next.noonpull.datapull;

import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonInterfacePullPage;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact extent proof shared by Noon paged snapshot/collection adapters. */
public final class NoonSnapshotPageContract {
    private final int pageNo;
    private final int totalPages;
    private final boolean lastPage;
    private final List<Map<String, Object>> rawItems;

    private NoonSnapshotPageContract(
            int pageNo,
            int totalPages,
            boolean lastPage,
            List<Map<String, Object>> rawItems
    ) {
        this.pageNo = pageNo;
        this.totalPages = totalPages;
        this.lastPage = lastPage;
        this.rawItems = List.copyOf(rawItems);
    }

    public static NoonSnapshotPageContract requireExact(
            SnapshotPageRequest request,
            NoonInterfacePullPage page,
            String operation
    ) {
        SnapshotPageRequest scope = Objects.requireNonNull(request, "request");
        NoonInterfacePullPage source = Objects.requireNonNull(page, "page");
        String prefix = operation == null ? "Noon" : operation;
        List<Map<String, Object>> items = source.getItems();
        if (source.getPageNumber() != scope.getPageNo()
                || source.getPageSize() < 1
                || source.getTotalItems() < 0
                || items == null
                || source.getRequestCount() != 1) {
            throw invalid(prefix, "page contract is invalid");
        }
        if (source.getTotalItems() == 0
                && (!items.isEmpty() || source.isHasNextPage())) {
            throw invalid(prefix, "empty-page metadata conflicts");
        }
        if (source.getTotalItems() > 0 && items.isEmpty()) {
            throw invalid(prefix, "non-empty collection returned an empty page");
        }
        if (items.size() > source.getTotalItems()) {
            throw invalid(prefix, "page exceeds declared total");
        }

        long offset = (long) (scope.getPageNo() - 1) * source.getPageSize();
        int expectedRows = offset >= source.getTotalItems()
                ? 0
                : (int) Math.min(
                        source.getPageSize(),
                        (long) source.getTotalItems() - offset
                );
        if (items.size() != expectedRows) {
            throw invalid(prefix, "page row count is incomplete");
        }
        int totalPages = source.getTotalItems() == 0
                ? 1
                : (int) (((long) source.getTotalItems() + source.getPageSize() - 1L)
                        / source.getPageSize());
        boolean lastPage = scope.getPageNo() == totalPages;
        if (scope.getPageNo() > totalPages
                || source.isHasNextPage() == lastPage) {
            throw invalid(prefix, "pagination metadata conflicts");
        }
        return new NoonSnapshotPageContract(
                scope.getPageNo(), totalPages, lastPage, items
        );
    }

    public <T> SnapshotPage<T> twoPass(List<T> items) {
        List<T> classified = List.copyOf(Objects.requireNonNull(items, "items"));
        if (classified.size() != rawItems.size()) {
            throw new IllegalArgumentException("classified page extent drift");
        }
        return SnapshotPage.twoPassRequired(
                pageNo,
                lastPage ? null : Math.incrementExact(pageNo),
                lastPage,
                totalPages,
                classified,
                rawItems.size(),
                0
        );
    }

    public <T> SnapshotPage<T> twoPass(
            List<T> items,
            List<String> businessSkipFingerprints
    ) {
        List<T> classified = List.copyOf(Objects.requireNonNull(items, "items"));
        List<String> skipped = List.copyOf(Objects.requireNonNull(
                businessSkipFingerprints,
                "businessSkipFingerprints"
        ));
        if (Math.addExact(classified.size(), skipped.size()) != rawItems.size()) {
            throw new IllegalArgumentException("classified page extent drift");
        }
        return SnapshotPage.twoPassRequired(
                pageNo,
                lastPage ? null : Math.incrementExact(pageNo),
                lastPage,
                totalPages,
                classified,
                rawItems.size(),
                skipped.size(),
                skipped
        );
    }

    public <T> SnapshotPage<T> providerAuthority(
            List<T> items,
            SnapshotCollectionAuthority authority
    ) {
        List<T> classified = List.copyOf(Objects.requireNonNull(items, "items"));
        if (classified.size() != rawItems.size()) {
            throw new IllegalArgumentException("classified page extent drift");
        }
        return new SnapshotPage<>(
                pageNo,
                lastPage ? null : Math.incrementExact(pageNo),
                lastPage,
                totalPages,
                classified,
                Objects.requireNonNull(authority, "authority"),
                rawItems.size(),
                0
        );
    }

    public List<Map<String, Object>> getRawItems() {
        return rawItems;
    }

    private static IllegalArgumentException invalid(String prefix, String reason) {
        return new IllegalArgumentException(prefix + ' ' + reason);
    }
}
