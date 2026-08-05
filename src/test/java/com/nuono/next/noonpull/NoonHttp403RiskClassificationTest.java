package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noon.NoonHttpException;
import org.junit.jupiter.api.Test;

class NoonHttp403RiskClassificationTest {

    @Test
    void legacyProviderBoundaryAndFailurePolicyKeep403OutOfAuthRecovery() {
        NoonInterfacePullException mapped = NoonPullProviderFailureMapper.map(
                "report download",
                new NoonHttpException(403, "unauthorized project session", "/download")
        );

        assertTrue(mapped.getMessage().startsWith("blocked by risk control:"));
        assertEquals(
                NoonPullFailureType.BLOCKED_BY_RISK_CONTROL,
                new NoonPullFailurePolicy().classify(mapped.getMessage())
        );
    }
}
