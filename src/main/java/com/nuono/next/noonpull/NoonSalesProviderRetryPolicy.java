package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class NoonSalesProviderRetryPolicy {
    private static final int MAX_PROVIDER_ATTEMPTS_PER_TARGET = 2;

    private NoonSalesProviderRetryPolicy() {
    }

    static boolean isDue(
            NoonPullPlanRecord plan,
            LocalDateTime persistedNow,
            LocalDate targetTo,
            boolean reportReady,
            List<NoonPullTaskRecord> tasks
    ) {
        if (plan.getPullType() != NoonPullType.REPORT
                || plan.getDataDomain() != NoonPullDataDomain.SALES
                || plan.getNextRetryAt() == null
                || plan.getNextRetryAt().isAfter(persistedNow)
                || !reportReady) {
            return false;
        }
        String targetIdentity = "sales:" + targetTo.minusDays(29) + ".." + targetTo;
        long failures = tasks.stream()
                .filter(task -> plan.getId().equals(task.getPlanId()))
                .filter(task -> targetIdentity.equals(task.getTargetIdentity()))
                .filter(task -> task.getStatus() == NoonPullTaskStatus.FAILED)
                .filter(task -> NoonPullFailureType.PROVIDER_UNAVAILABLE.code().equals(task.getFailureType()))
                .count();
        return failures > 0 && failures < MAX_PROVIDER_ATTEMPTS_PER_TARGET;
    }
}
