package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Defines when one data-pull operation is due and which business window belongs to each slot.
 *
 * <p>The persisted last-completed value is the exact {@link DataPullScheduleSlot#getScheduledAt()}
 * instant. Catch-up is exclusive of that completed slot and inclusive of {@code now}.</p>
 */
public interface DataPullSchedule {
    ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    OperationCode operationCode();

    default ZoneId zoneId() {
        return ZONE_ID;
    }

    List<DataPullScheduleSlot> missedSlots(
            String scopeKey,
            Instant lastCompletedSlotExclusive,
            Instant nowInclusive
    );

    default ScheduleSlotPage missedSlotsPage(
            String scopeKey,
            Instant lastCompletedSlotExclusive,
            Instant nowInclusive,
            int limit
    ) {
        throw new UnsupportedOperationException("bounded schedule paging is not implemented");
    }

    default Optional<DataPullScheduleSlot> latestMissedSlot(
            String scopeKey,
            Instant lastCompletedSlotExclusive,
            Instant nowInclusive
    ) {
        throw new UnsupportedOperationException("bounded latest-slot lookup is not implemented");
    }
}
