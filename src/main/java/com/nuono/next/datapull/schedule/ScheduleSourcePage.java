package com.nuono.next.datapull.schedule;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One bounded keyset page; an empty page is the only end-of-scan signal. */
public final class ScheduleSourcePage {

    private final List<ScheduleSourceScope> items;
    private final boolean hasMore;
    private final String nextCursor;

    public ScheduleSourcePage(List<ScheduleSourceScope> items, boolean hasMore, int requestedLimit) {
        this(items, hasMore, requestedLimit, null);
    }

    private ScheduleSourcePage(
            List<ScheduleSourceScope> items,
            boolean hasMore,
            int requestedLimit,
            String explicitNextCursor
    ) {
        if (requestedLimit < 1 || requestedLimit > 64) {
            throw new IllegalArgumentException("requestedLimit must be between 1 and 64");
        }
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (this.items.size() > requestedLimit
                || (explicitNextCursor == null && hasMore && this.items.size() != requestedLimit)) {
            throw new IllegalArgumentException("source page exceeds its bounded request");
        }
        Set<String> cursors = new HashSet<>();
        Set<String> scopes = new HashSet<>();
        for (ScheduleSourceScope item : this.items) {
            ScheduleSourceScope value = Objects.requireNonNull(item, "source item");
            if (!cursors.add(value.getSourceCursor())) {
                throw new IllegalArgumentException("source page contains a duplicate cursor");
            }
            if (!scopes.add(value.getScope().getStableScopeKey())) {
                throw new IllegalArgumentException("source page contains a duplicate scope");
            }
        }
        this.hasMore = hasMore;
        String derived = this.items.isEmpty()
                ? null : this.items.get(this.items.size() - 1).getSourceCursor();
        this.nextCursor = explicitNextCursor == null ? derived : requireCursor(explicitNextCursor);
        if (hasMore && this.nextCursor == null) {
            throw new IllegalArgumentException("a resumable source page needs a next cursor");
        }
    }

    /** Advances a nested source cursor without claiming that a logical scope is complete. */
    public static ScheduleSourcePage progress(String nextCursor, int requestedLimit) {
        return new ScheduleSourcePage(List.of(), true, requestedLimit, nextCursor);
    }

    /** Returns completed logical scopes while persisting a deeper native member cursor. */
    public static ScheduleSourcePage scanned(
            List<ScheduleSourceScope> items,
            boolean hasMore,
            int requestedLimit,
            String nextCursor
    ) {
        return new ScheduleSourcePage(items, hasMore, requestedLimit, nextCursor);
    }

    public List<ScheduleSourceScope> getItems() { return items; }
    public boolean hasMore() { return hasMore; }
    public String nextCursor() { return nextCursor; }

    private static String requireCursor(String value) {
        String cursor = Objects.requireNonNull(value, "nextCursor");
        if (cursor.isEmpty() || !cursor.equals(cursor.trim()) || cursor.length() > 512
                || cursor.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("nextCursor must fit its stable column");
        }
        return cursor;
    }
}
