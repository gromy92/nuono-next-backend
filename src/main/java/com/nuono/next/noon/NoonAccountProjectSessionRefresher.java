package com.nuono.next.noon;

/** Applies one authenticated Noon account grant to its already bound Nuono Projects. */
interface NoonAccountProjectSessionRefresher {
    RefreshResult refresh(NoonAccountManualOtpGateway.AuthenticatedGrant grant, Long operatorUserId);

    final class RefreshResult {
        private final int refreshedProjects;
        private final int excludedProjects;
        private final int failedProjects;

        RefreshResult(int refreshedProjects, int failedProjects) {
            this(refreshedProjects, 0, failedProjects);
        }

        RefreshResult(int refreshedProjects, int excludedProjects, int failedProjects) {
            this.refreshedProjects = refreshedProjects;
            this.excludedProjects = excludedProjects;
            this.failedProjects = failedProjects;
        }

        int getRefreshedProjects() {
            return refreshedProjects;
        }

        int getFailedProjects() {
            return failedProjects;
        }

        int getExcludedProjects() {
            return excludedProjects;
        }
    }
}
