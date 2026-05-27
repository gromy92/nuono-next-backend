package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class NoonOrderReportSchedulePolicy {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final LocalTime READY_AFTER = LocalTime.of(8, 30);

    private final Clock clock;

    public NoonOrderReportSchedulePolicy() {
        this(Clock.system(SHANGHAI));
    }

    public NoonOrderReportSchedulePolicy(Clock clock) {
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
    }

    public NoonOrderDailyPullPlan dailyPlan(Long ownerUserId, String storeCode, String siteCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate targetDate = now.toLocalDate().minusDays(1);
        LocalDateTime scheduledAt = now.toLocalDate()
                .atTime(READY_AFTER)
                .plusMinutes(jitterMinutes(ownerUserId, storeCode, siteCode));
        return new NoonOrderDailyPullPlan(
                targetDate,
                READY_AFTER,
                scheduledAt,
                !now.isBefore(scheduledAt)
        );
    }

    private int jitterMinutes(Long ownerUserId, String storeCode, String siteCode) {
        String seed = String.valueOf(ownerUserId) + "|" + String.valueOf(storeCode) + "|" + String.valueOf(siteCode);
        int sum = 0;
        for (int i = 0; i < seed.length(); i++) {
            sum += seed.charAt(i);
        }
        return Math.floorMod(sum, 11);
    }
}
