package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Single locality for every legacy rollback schedule window. */
final class LegacyNoonScheduleCalendar {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final LocalTime SALES_READY_AFTER = LocalTime.of(8, 0);
    private static final LocalTime SALES_LATEST_DAY_READY_AFTER = LocalTime.of(20, 0);
    private static final LocalTime ADS_READY_AFTER = LocalTime.of(8, 0);
    private static final LocalTime FINANCE_READY_AFTER = LocalTime.of(22, 30);
    private static final LocalTime INVENTORY_READY_AFTER = LocalTime.of(23, 0);
    private static final LocalTime FBN_RECEIVED_READY_AFTER = LocalTime.of(23, 30);

    private final Clock clock;

    LegacyNoonScheduleCalendar(Clock clock) {
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
    }

    Clock clock() {
        return clock;
    }

    LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    LocalDate latestAvailableDate() {
        return currentDate().minusDays(1);
    }

    boolean salesReady() {
        return reached(SALES_READY_AFTER);
    }

    boolean salesLatestDayReady() {
        return reached(SALES_LATEST_DAY_READY_AFTER);
    }

    boolean advertisingReady() {
        return reached(ADS_READY_AFTER);
    }

    boolean financeReady() {
        return reached(FINANCE_READY_AFTER);
    }

    boolean inventoryReady() {
        return reached(INVENTORY_READY_AFTER);
    }

    boolean fbnReceivedReady() {
        return reached(FBN_RECEIVED_READY_AFTER);
    }

    private boolean reached(LocalTime readyAfter) {
        return !LocalTime.now(clock).isBefore(readyAfter);
    }
}
