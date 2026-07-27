package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RealProductListingNoonContinuationTest extends RealProductListingNoonWriteAdapterTest {
    @Test
    void createReferenceLookupAuthenticationFailureIsStructurallyClassified() {
        FakeBindingResolver bindingResolver = new FakeBindingResolver();
        bindingResolver.failure = new NoonHttpException(
                401,
                "provider response is intentionally hidden",
                "/catalog"
        );
        RealProductListingNoonWriteAdapter adapter =
                new RealProductListingNoonWriteAdapter(
                        new ObjectMapper(),
                        bindingResolver,
                        new FakeSessionFactory(),
                        new ProductListingRealWriteProperties(),
                        new FakeImageDownloader()
                );

        ProductListingNoonWriteStepResult result =
                adapter.resolveCreateReference(writeRequest());

        assertEquals("failed", result.getStatus());
        assertEquals("noon_auth_required", result.getFailureCode());
    }

}
