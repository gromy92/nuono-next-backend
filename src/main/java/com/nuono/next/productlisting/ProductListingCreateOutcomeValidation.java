package com.nuono.next.productlisting;

import java.util.Objects;

final class ProductListingCreateOutcomeValidation {

    private ProductListingCreateOutcomeValidation() {
    }

    static void requireVerifiable(ProductListingTaskView task) {
        boolean unknownOutcome = task != null
                && ("noon_create_outcome_unknown".equalsIgnoreCase(task.getFailureCode())
                || "real_run_interrupted".equalsIgnoreCase(task.getFailureCode()));
        if (task == null
                || !"REAL_RUN".equalsIgnoreCase(task.getMode())
                || !"written_verify_failed".equalsIgnoreCase(task.getStatus())
                || !unknownOutcome) {
            throw new IllegalArgumentException(
                    "Only a real-run with an unknown create outcome can be checked.");
        }
    }

    static void requireLatestVerifiable(
            ProductListingTaskView authorized,
            ProductListingTaskRecord latest
    ) {
        boolean sameIdentity = latest != null
                && Objects.equals(authorized.getTaskId(), latest.getId())
                && Objects.equals(authorized.getOwnerUserId(), latest.getOwnerUserId())
                && Objects.equals(authorized.getDraftId(), latest.getDraftId())
                && sameText(authorized.getStoreCode(), latest.getStoreCode());
        boolean unknownOutcome = latest != null
                && ("noon_create_outcome_unknown".equalsIgnoreCase(latest.getFailureCode())
                || "real_run_interrupted".equalsIgnoreCase(latest.getFailureCode()));
        if (!sameIdentity
                || !"REAL_RUN".equalsIgnoreCase(latest.getMode())
                || !"written_verify_failed".equalsIgnoreCase(latest.getStatus())
                || !unknownOutcome) {
            throw new IllegalArgumentException(
                    "The product listing task changed; reload the workflow before checking again.");
        }
    }

    private static boolean sameText(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }
}
