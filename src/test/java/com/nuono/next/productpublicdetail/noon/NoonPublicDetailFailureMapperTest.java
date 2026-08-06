package com.nuono.next.productpublicdetail.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NoonPublicDetailFailureMapperTest {

    @Test
    void serverFailureRemainsAFrontendRetryInsteadOfBecomingNotFound() {
        NoonPublicProductDetailResult result = NoonPublicDetailFailureMapper.fromProviderException(
                "ZABCDEF12",
                new NoonSearchProviderException(
                        "PROVIDER_UNAVAILABLE",
                        "Noon customer catalog returned 500",
                        500,
                        "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u/search",
                        null,
                        Duration.ofSeconds(30)
                )
        );

        assertEquals(ProductPublicDetailSyncStatus.FAILED, result.getStatus());
        assertEquals("PROVIDER_UNAVAILABLE", result.getFailureCode());
        assertEquals(500, result.getProviderHttpStatus());
        assertEquals(Duration.ofSeconds(30), result.getProviderRetryAfter());
    }
}
