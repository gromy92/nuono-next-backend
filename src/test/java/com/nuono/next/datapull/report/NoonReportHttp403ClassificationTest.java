package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonHttpException;
import org.junit.jupiter.api.Test;

class NoonReportHttp403ClassificationTest {

    @Test
    void reportRuntimeClassifies403AsRiskAnd401AsAuthentication() {
        assertEquals(
                ProviderOutcomeType.RISK_CONTROL,
                NoonReportOutcomeClassifier.readFailure(
                        new NoonHttpException(403, "unauthorized", "/report")
                ).getType()
        );
        assertEquals(
                ProviderOutcomeType.AUTH_REQUIRED,
                NoonReportOutcomeClassifier.readFailure(
                        new NoonHttpException(401, "invalid session", "/report")
                ).getType()
        );
    }
}
