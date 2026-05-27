package com.nuono.next.noonsync;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class NoonSalesCorrectionPlannerService {

    private final Clock clock;

    public NoonSalesCorrectionPlannerService() {
        this(Clock.systemDefaultZone());
    }

    public NoonSalesCorrectionPlannerService(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public NoonSalesCorrectionPlan planWeeklyCorrection(NoonSalesCorrectionPlanRequest request) {
        NoonSalesCorrectionPolicy policy = request.getPolicy();
        LocalDate latestAvailableSalesDay = request.getLatestAvailableSalesDay();
        LocalDate dateFrom = latestAvailableSalesDay.minusDays(policy.getWindowDays() - 1L);
        LocalDate today = LocalDate.now(clock);
        LocalDate nextEligibleDate = request.getLastSuccessfulCorrectionDate() == null
                ? today
                : request.getLastSuccessfulCorrectionDate().plusDays(policy.getIntervalDays());
        boolean due = !today.isBefore(nextEligibleDate);
        return new NoonSalesCorrectionPlan(
                request.getScope(),
                NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION,
                NoonSyncTarget.dateRange(dateFrom, latestAvailableSalesDay),
                due,
                nextEligibleDate,
                policy.getWindowDays(),
                policy.getCandidateWindowDays(),
                request.getMeasurements(),
                false,
                false
        );
    }
}
