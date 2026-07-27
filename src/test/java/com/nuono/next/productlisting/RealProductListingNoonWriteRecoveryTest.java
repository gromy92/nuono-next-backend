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

class RealProductListingNoonWriteRecoveryTest extends RealProductListingNoonWriteAdapterTest {
    @Test
    void realAdapterDoesNotDelegatePurePriceWindowToSplitOfferAdapter() {
        FakeOfferStockWriteAdapter offerStockWriteAdapter = new FakeOfferStockWriteAdapter();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader(),
                offerStockWriteAdapter
        );

        ProductListingNoonWriteRequest writeRequest = writeRequest();
        writeRequest.getDraft().setIsActive(null);
        writeRequest.getDraft().setPriceMin(new BigDecimal("45.00"));
        writeRequest.getDraft().setPriceMax(new BigDecimal("59.00"));
        writeRequest.getDraft().setSalePrice(new BigDecimal("39.90"));
        writeRequest.getDraft().setSaleStart("2026-07-02T00:00:00+08:00");
        writeRequest.getDraft().setSaleEnd("2026-07-31 23:59:59");

        ProductListingNoonWriteResult result = adapter.execute(writeRequest);

        assertTrue(result.isSuccess());
        assertEquals(0, offerStockWriteAdapter.callCount);
        assertTrue(result.getSteps().stream()
                .noneMatch(step -> "upsert_offer".equals(step.getStepKey())));
        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals(45.00, price.at("/priceMin").asDouble());
        assertEquals(59.00, price.at("/priceMax").asDouble());
        assertEquals(39.90, price.at("/salePrice").asDouble());
        assertEquals("2026-07-02", price.at("/saleStart").asText());
        assertEquals("2026-07-31", price.at("/saleEnd").asText());
    }

    @Test
    void realAdapterDefaultsPriceRangeToBasePriceWhenMissing() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest writeRequest = writeRequest();
        writeRequest.getDraft().setPrice(new BigDecimal("19.85"));
        writeRequest.getDraft().setPriceMin(null);
        writeRequest.getDraft().setPriceMax(null);
        writeRequest.getDraft().setSalePrice(null);

        ProductListingNoonWriteResult result = adapter.execute(writeRequest);

        assertTrue(result.isSuccess());
        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals(19.85, price.at("/price").asDouble());
        assertEquals(19.85, price.at("/priceMin").asDouble());
        assertEquals(19.85, price.at("/priceMax").asDouble());
        assertTrue(price.at("/salePrice").isMissingNode() || price.at("/salePrice").isNull());
    }

    @Test
    void realAdapterDefaultsSaleWindowForSalePriceWhenMissing() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest writeRequest = writeRequest();
        writeRequest.getDraft().setSalePrice(new BigDecimal("39.90"));
        writeRequest.getDraft().setSaleStart(null);
        writeRequest.getDraft().setSaleEnd(null);
        LocalDate today = LocalDate.now();

        ProductListingNoonWriteResult result = adapter.execute(writeRequest);

        assertTrue(result.isSuccess());
        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals("39.9", price.at("/salePrice").asText());
        assertEquals(today.toString(), price.at("/saleStart").asText());
        assertEquals(today.plusYears(20).toString(), price.at("/saleEnd").asText());
    }

    @Test
    void realAdapterRetriesReadBackUntilUploadedImagesAreAvailable() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.readBackImagesAvailableAfterAttempt = 2;
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setReadBackMaxAttempts(2);
        properties.setReadBackRetryDelayMillis(0L);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(result.isSuccess());
        assertEquals(2, sessionFactory.session.retrieveCallCount);
        ProductListingNoonWriteStepResult readBack = result.getSteps().get(result.getSteps().size() - 1);
        assertEquals("verify_noon_readback", readBack.getStepKey());
        assertEquals("succeeded", readBack.getStatus());
        assertTrue(readBack.getExternalReference().contains("readBackAttempts=2"));
    }

    @Test
    void realAdapterAcceptsNoonBrandCodeReadBackForDisplayBrand() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.readBackBrand = "yalla_pick";
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductBrand("Yalla Pick");
        request.getDraft().setProductBrandCode("yalla_pick");

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        ProductListingNoonWriteStepResult readBack = result.getSteps().get(result.getSteps().size() - 1);
        assertEquals("verify_noon_readback", readBack.getStepKey());
        assertEquals("succeeded", readBack.getStatus());
    }

    @Test
    void realAdapterAcceptsNoonProductFullTypeCodeReadBack() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.readBackProductFullType = null;
        sessionFactory.session.readBackProductFullTypeCode = "electronic_accessories-headphones-wired_headphones";
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(result.isSuccess());
        ProductListingNoonWriteStepResult readBack = result.getSteps().get(result.getSteps().size() - 1);
        assertEquals("verify_noon_readback", readBack.getStepKey());
        assertEquals("succeeded", readBack.getStatus());
    }

    @Test
    void realAdapterFailsClosedWhenNoonOmitsProductFullTypeReadBack() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.readBackProductFullType = null;
        sessionFactory.session.readBackProductFullTypeCode = null;
        ProductListingRealWriteProperties properties =
                new ProductListingRealWriteProperties();
        properties.setReadBackMaxAttempts(1);
        properties.setReadBackRetryDelayMillis(0L);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(!result.isSuccess());
        ProductListingNoonWriteStepResult readBack = result.getSteps().get(result.getSteps().size() - 1);
        assertEquals("verify_noon_readback", readBack.getStepKey());
        assertEquals("failed", readBack.getStatus());
        assertEquals("noon_listing_readback_incomplete", readBack.getFailureCode());
        assertTrue(readBack.getFailureMessage().contains("product_fulltype"));
    }

    @Test
    void realAdapterFailsClosedWhenNoonOmitsRichContentOrDetailedAttributes() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.omitReadBackRichContent = true;
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
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductDescriptionEn("Verified description");
        request.getDraft().setProductHighlightsEn(List.of("Verified feature"));
        request.getDraft().setKeyAttributes(List.of(Map.of(
                "code",
                "base_material",
                "commonValue",
                "pvc"
        )));

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(!result.isSuccess());
        ProductListingNoonWriteStepResult readBack =
                result.getSteps().get(result.getSteps().size() - 1);
        assertEquals("noon_listing_readback_incomplete", readBack.getFailureCode());
        assertTrue(readBack.getFailureMessage().contains("long_description_en"));
        assertTrue(readBack.getFailureMessage().contains("feature_bullet_en_1"));
        assertTrue(readBack.getFailureMessage().contains("attribute_en_base_material"));
    }

}
