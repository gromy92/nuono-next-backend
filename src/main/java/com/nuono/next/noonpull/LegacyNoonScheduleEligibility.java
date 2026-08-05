package com.nuono.next.noonpull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Applies legacy plan pause, retry, cooldown and provider-availability gates. */
final class LegacyNoonScheduleEligibility {
    private static final Duration PROVIDER_UNAVAILABLE_COOLDOWN = Duration.ofHours(6);

    private final NoonPullFoundationService foundationService;
    private final LegacyNoonScheduleCalendar calendar;
    private final NoonProviderAvailability providerAvailability;

    LegacyNoonScheduleEligibility(
            NoonPullFoundationService foundationService,
            LegacyNoonScheduleCalendar calendar,
            NoonProviderAvailability providerAvailability
    ) {
        this.foundationService = foundationService;
        this.calendar = calendar;
        this.providerAvailability = providerAvailability == null
                ? plan -> true : providerAvailability;
    }

    boolean isRunnable(NoonPullPlanRecord plan) {
        if (plan == null || !plan.isEnabled() || plan.isPaused()) {
            return false;
        }
        LocalDateTime persistedNow = LocalDateTime.ofInstant(
                calendar.clock().instant(), ZoneOffset.UTC
        );
        if (plan.getNextRetryAt() != null && plan.getNextRetryAt().isAfter(persistedNow)) {
            return false;
        }
        if (providerUnavailableCircuitOpen(plan, persistedNow)) {
            return false;
        }
        if (plan.getLatestSuccessAt() != null
                && plan.getCooldownSeconds() != null
                && plan.getCooldownSeconds() > 0
                && plan.getLatestSuccessAt().plusSeconds(plan.getCooldownSeconds())
                    .isAfter(persistedNow)) {
            return false;
        }
        return providerAvailability.isAvailable(plan);
    }

    private boolean providerUnavailableCircuitOpen(
            NoonPullPlanRecord plan,
            LocalDateTime persistedNow
    ) {
        return plan.getTriggerMode() == NoonPullTriggerMode.SCHEDULED_DAILY
                && NoonPullFailureType.PROVIDER_UNAVAILABLE.code()
                    .equals(plan.getLatestFailureType())
                && plan.getLatestFailureAt() != null
                && (plan.getLatestSuccessAt() == null
                    || plan.getLatestFailureAt().isAfter(plan.getLatestSuccessAt()))
                && !NoonSalesProviderRetryPolicy.isDue(
                        plan, persistedNow, calendar.latestAvailableDate(),
                        calendar.salesLatestDayReady(), foundationService.listTasks()
                )
                && plan.getLatestFailureAt().plus(PROVIDER_UNAVAILABLE_COOLDOWN)
                    .isAfter(persistedNow);
    }
}
