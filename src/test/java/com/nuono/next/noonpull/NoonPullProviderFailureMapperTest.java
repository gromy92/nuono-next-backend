package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noon.NoonHttpException;
import org.junit.jupiter.api.Test;

class NoonPullProviderFailureMapperTest {

    @Test
    void mapsNoonAdsCountryAccessResponseToAdvertiserContextMismatchWithoutLeakingBody() {
        NoonHttpException providerFailure = new NoonHttpException(
                400,
                "{\"error\":\"You don't have access to run ads in this country. "
                        + "Please reach out to your account manager or send an email to adsupport@noon.com\"}",
                "/_svc/productads/v2/noon/metrics"
        );

        NoonInterfacePullException mapped = NoonPullProviderFailureMapper.map(
                "noon ads dashboard metrics",
                providerFailure
        );

        assertTrue(mapped.getMessage().startsWith("ads advertiser context mismatch:"));
        assertTrue(mapped.getMessage().contains("Noon HTTP 400"));
        assertFalse(mapped.getMessage().contains("adsupport@noon.com"));
    }
}
