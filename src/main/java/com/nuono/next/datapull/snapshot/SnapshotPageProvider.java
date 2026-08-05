package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.runtime.ProviderOutcome;

/** Provider Adapter for one bounded snapshot page call. */
@FunctionalInterface
public interface SnapshotPageProvider<T> {
    ProviderOutcome<SnapshotPage<T>> fetchPage(SnapshotPageRequest request);
}
