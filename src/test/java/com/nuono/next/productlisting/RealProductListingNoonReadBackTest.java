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

class RealProductListingNoonReadBackTest extends RealProductListingNoonWriteAdapterTest {
    @Test
    void realAdapterFailsClosedWhenOfferPriceWarrantyAndBarcodeCannotBeReadBack() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.omitReadBackPricing = true;
        sessionFactory.session.omitReadBackBarcode = true;
        ProductListingRealWriteProperties properties =
                new ProductListingRealWriteProperties();
        properties.setReadBackMaxAttempts(1);
        properties.setReadBackRetryDelayMillis(0L);
        RealProductListingNoonWriteAdapter adapter =
                new RealProductListingNoonWriteAdapter(
                        new ObjectMapper(),
                        new FakeBindingResolver(),
                        sessionFactory,
                        properties,
                        new FakeImageDownloader()
                );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        ProductListingNoonWriteStepResult readBack =
                result.getSteps().get(result.getSteps().size() - 1);
        assertEquals("noon_listing_readback_incomplete", readBack.getFailureCode());
        assertTrue(readBack.getFailureMessage().contains("price"));
        assertTrue(readBack.getFailureMessage().contains("id_warranty"));
        assertTrue(readBack.getFailureMessage().contains("barcode"));
    }

    @Test
    void realAdapterReadBackOnlyDoesNotCallNoonWriteEndpoints() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.standaloneReadBackSeeded = true;
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setReadBackMaxAttempts(1);
        properties.setReadBackRetryDelayMillis(0L);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader()
        );

        ProductListingNoonWriteStepResult readBack = adapter.verifyReadBack(
                writeRequest(),
                "ZPARENT",
                "PSKU_CODE_1",
                List.of("noon-uploaded/sku-main.jpg")
        );

        assertEquals("verify_noon_readback", readBack.getStepKey());
        assertEquals("succeeded", readBack.getStatus());
        assertEquals(1, sessionFactory.session.retrieveCallCount);
        assertEquals(0, sessionFactory.session.calls.size());
        assertEquals(0, sessionFactory.session.uploadCalls.size());
    }

    @Test
    void realAdapterFailsWhenNoonWriteResponseContainsBusinessError() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.baseUpsertReturnsInvalid = true;
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("noon_api", result.getFailureCategory());
        assertEquals("noon_write_rejected", result.getFailureCode());
        assertTrue(result.getFailureMessage().contains("fulltype"));
        assertEquals(List.of("create_product", "sku_cache", "upsert_zsku_base"), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        ProductListingNoonWriteStepResult failedStep = result.getSteps().get(2);
        assertEquals("failed", failedStep.getStatus());
        assertTrue(failedStep.getFailureMessage().contains("partner_error"));
    }

    @Test
    void createBusinessRejectionIsDecisivelyNotStarted() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.createReturnsInvalid = true;
        RealProductListingNoonWriteAdapter adapter =
                new RealProductListingNoonWriteAdapter(
                        new ObjectMapper(),
                        new FakeBindingResolver(),
                        sessionFactory,
                        new ProductListingRealWriteProperties(),
                        new FakeImageDownloader()
                );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("noon_create_rejected", result.getFailureCode());
        assertEquals("noon_create_rejected", result.getSteps().get(0).getFailureCode());
    }

    @Test
    void createAuthenticationRedirectIsDecisivelyNotStartedAndRequiresReauthentication() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.failCreateAuthentication = true;
        RealProductListingNoonWriteAdapter adapter =
                new RealProductListingNoonWriteAdapter(
                        new ObjectMapper(),
                        new FakeBindingResolver(),
                        sessionFactory,
                        new ProductListingRealWriteProperties(),
                        new FakeImageDownloader()
                );

        ProductListingNoonWriteResult result =
                adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("noon_auth_required", result.getFailureCode());
        assertEquals(
                "noon_auth_required",
                result.getSteps().get(0).getFailureCode()
        );
    }

    @Test
    void preCreateAuthenticationFailureIsStructurallyClassifiedAsNotStarted() {
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

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("authentication", result.getFailureCategory());
        assertEquals("noon_auth_required", result.getFailureCode());
        assertEquals("pre_create", result.getSteps().get(0).getStepKey());
    }

    @Test
    void catalogAuthenticationFailureStopsBeforeCreateWrite() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.failOfferListAuthentication = true;
        RealProductListingNoonWriteAdapter adapter =
                new RealProductListingNoonWriteAdapter(
                        new ObjectMapper(),
                        new FakeBindingResolver(),
                        sessionFactory,
                        new ProductListingRealWriteProperties(),
                        new FakeImageDownloader()
                );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("authentication", result.getFailureCategory());
        assertEquals("noon_auth_required", result.getFailureCode());
        assertEquals("pre_create", result.getSteps().get(0).getStepKey());
        assertEquals(0, sessionFactory.session.calls.size());
        assertEquals(1, sessionFactory.session.offerListCallCount);
    }

    @Test
    void adapterRejectsUnsupportedWarehouseStockBeforeAnyNoonCall() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter =
                new RealProductListingNoonWriteAdapter(
                        new ObjectMapper(),
                        new FakeBindingResolver(),
                        sessionFactory,
                        new ProductListingRealWriteProperties(),
                        new FakeImageDownloader()
                );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setQuantity(100);

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(!result.isSuccess());
        assertEquals("validation", result.getFailureCategory());
        assertEquals(
                "noon_warehouse_stock_not_supported",
                result.getFailureCode()
        );
        assertEquals(0, sessionFactory.session.calls.size());
        assertEquals(0, sessionFactory.session.retrieveCallCount);
    }

    @Test
    void createTransportFailureIsReportedAsUnknownOutcome() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.failCreateTransport = true;
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("noon_create_outcome_unknown", result.getFailureCode());
        assertEquals("noon_create_outcome_unknown", result.getSteps().get(0).getFailureCode());
    }

    @Test
    void createSuccessWithoutNoonReferencesIsReportedAsUnknownOutcome() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.createResponseMissingReferences = true;
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        assertEquals("noon_create_outcome_unknown", result.getFailureCode());
        assertEquals("failed", result.getSteps().get(0).getStatus());
        assertEquals("noon_create_outcome_unknown", result.getSteps().get(0).getFailureCode());
    }

    @Test
    void createReferenceLookupFindsExistingNoonProductByPartnerSku() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.offerListContainsProduct = true;
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteStepResult result = adapter.resolveCreateReference(writeRequest());

        assertEquals("succeeded", result.getStatus());
        assertEquals("skuParent=ZPARENT;pskuCode=PSKU_CODE_1", result.getExternalReference());
    }

}
