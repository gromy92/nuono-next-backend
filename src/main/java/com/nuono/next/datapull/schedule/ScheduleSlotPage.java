package com.nuono.next.datapull.schedule;

import java.util.List;
import java.util.Objects;

/** Memory-bounded exact-window schedule page. */
public final class ScheduleSlotPage {
    private final List<DataPullScheduleSlot> slots;
    private final boolean hasMore;

    public ScheduleSlotPage(List<DataPullScheduleSlot> slots, boolean hasMore, int limit) {
        if (limit < 1 || limit > 64) throw new IllegalArgumentException("slot limit is invalid");
        this.slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        if (this.slots.size() > limit || (hasMore && this.slots.size() != limit)) {
            throw new IllegalArgumentException("slot page exceeds its bounded request");
        }
        this.hasMore = hasMore;
    }

    public List<DataPullScheduleSlot> getSlots() { return slots; }
    public boolean hasMore() { return hasMore; }
}
