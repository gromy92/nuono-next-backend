package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDate;

/** Owns DP-02 provider-window validation without inferring emptiness from wall-clock time. */
final class NoonOrderReportWindowPolicy {
    NoonOrderReportWindowPolicy(Clock clock) {
        // Kept in the package-private constructor for compatibility with focused clock tests.
    }

    boolean outsideRequestedWindow(LocalDate actualDate, NoonReportPullRequest request) {
        if (actualDate == null || request.getDateFrom() == null || request.getDateTo() == null) {
            return false;
        }
        return actualDate.isBefore(request.getDateFrom()) || actualDate.isAfter(request.getDateTo());
    }

    NoonReportProcessResult emptyOrNotReady() {
        return NoonReportProcessResult.emptyReportPendingConfirmation(
                "provider_poll_row_count_required"
        );
    }
}
