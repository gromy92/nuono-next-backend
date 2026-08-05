package com.nuono.next.datapull.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContractFailurePolicyTest {

    @Test
    void deterministicProviderContractFailureWaitsWithoutApplyingTheContainer() {
        assertEquals(
                ContractFailurePolicy.Decision.RETRY_WITH_BACKOFF,
                ContractFailurePolicy.decide(
                        ProviderOutcome.contractError("CONTAINER_SCHEMA_INVALID"),
                        ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                )
        );
    }

    @Test
    void notFoundMeaningIsDeclaredByTheDpCallSite() {
        ProviderOutcome<?> notFound = ProviderOutcome.notFound("REMOTE_OBJECT_NOT_FOUND");

        assertEquals(
                ContractFailurePolicy.Decision.FAIL_TASK,
                ContractFailurePolicy.decide(
                        notFound,
                        ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                )
        );
        assertEquals(
                ContractFailurePolicy.Decision.RETRY_SAME_RESOURCE,
                ContractFailurePolicy.decide(
                        notFound,
                        ContractFailurePolicy.NotFoundHandling.RETRY_SAME_RESOURCE
                )
        );
        assertEquals(
                ContractFailurePolicy.Decision.WAIT_RECONCILE,
                ContractFailurePolicy.decide(
                        notFound,
                        ContractFailurePolicy.NotFoundHandling.WAIT_RECONCILE
                )
        );
    }

    @Test
    void riskTransientAndAuthHaveNoAttemptExhaustionDecision() {
        for (int ignored = 0; ignored < 10_000; ignored++) {
            assertEquals(
                    ContractFailurePolicy.Decision.RETRY_WITH_BACKOFF,
                    ContractFailurePolicy.decide(
                            ProviderOutcome.riskControl("HTTP_429"),
                            ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                    )
            );
            assertEquals(
                    ContractFailurePolicy.Decision.RETRY_WITH_BACKOFF,
                    ContractFailurePolicy.decide(
                            ProviderOutcome.transientFailure("HTTP_503"),
                            ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                    )
            );
            assertEquals(
                    ContractFailurePolicy.Decision.WAIT_AUTH,
                    ContractFailurePolicy.decide(
                            ProviderOutcome.authRequired("AUTH_REQUIRED"),
                            ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                    )
            );
        }
    }

    @Test
    void unknownOutcomeAlwaysRemainsReconcileOnly() {
        assertEquals(
                ContractFailurePolicy.Decision.WAIT_RECONCILE,
                ContractFailurePolicy.decide(
                        ProviderOutcome.unknownOutcome("CREATE_OUTCOME_UNKNOWN"),
                        ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractFailurePolicy.decide(
                        ProviderOutcome.success("value"),
                        ContractFailurePolicy.NotFoundHandling.FAIL_TASK
                )
        );
    }
}
