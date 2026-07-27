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

class RealProductListingNoonWriteFailureTest extends RealProductListingNoonWriteAdapterTest {
    @Test
    void realAdapterSkipsBlankRichTextContentFields() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductDescriptionEn("<p><br></p>");
        request.getDraft().setProductHighlightsEn(List.of("  ", "<p><br></p>", "Valid feature"));

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        JsonNode contentEn = sessionFactory.session.calls.get(3).body;
        assertTrue(!contentEn.at("/attributes").has("long_description"));
        assertEquals("Valid feature", contentEn.at("/attributes/feature_bullet_1").asText());
        assertTrue(!contentEn.at("/attributes").has("feature_bullet_2"));
    }

    @Test
    void realAdapterDoesNotWriteBarcodeAfterDraftBarcodeWasDeleted() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setBarcode(null);

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        assertTrue(result.getSteps().stream().noneMatch(step -> "upsert_barcode".equals(step.getStepKey())));
    }

    @Test
    void realAdapterWritesDetailedAttributesToNoonContent() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setKeyAttributes(List.of(
                Map.of("code", "base_material", "commonValue", "pvc"),
                Map.of("code", "country_of_origin", "commonValue", "china"),
                Map.of("code", "barcode", "commonValue", "6290000000001")
        ));

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        JsonNode contentEn = sessionFactory.session.calls.get(3).body;
        assertEquals("pvc", contentEn.at("/attributes/base_material").asText());
        assertEquals("china", contentEn.at("/attributes/country_of_origin").asText());
        JsonNode contentAr = sessionFactory.session.calls.get(4).body;
        assertEquals("pvc", contentAr.at("/attributes/base_material").asText());
        assertEquals("china", contentAr.at("/attributes/country_of_origin").asText());
        assertTrue(!contentEn.at("/attributes").has("barcode"));
    }

    @Test
    void realAdapterUsesProductFullTypeCodeInsteadOfStaleTaxonomyLabels() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.taxonomyProductFullTypeCode = "electronic_accessories-phone_accessories-phone_grips_stands";
        sessionFactory.session.taxonomyFamilyNameEn = "Electronic Accessories";
        sessionFactory.session.taxonomyProductTypeNameEn = "Phone Accessories";
        sessionFactory.session.taxonomyProductSubTypeNameEn = "Phone Grips & Stands";
        sessionFactory.session.readBackProductFullType = "electronic_accessories-phone_accessories-phone_grips_stands";
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductFullType("electronic_accessories-phone_accessories-phone_grips_stands");
        request.getDraft().setIdProductFullType(null);
        request.getDraft().setFamily("Electronic Accessories");
        request.getDraft().setProductType("Headphones");
        request.getDraft().setProductSubType("Wired Headphones");

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        JsonNode upsertZskuBase = sessionFactory.session.calls.get(2).body;
        assertEquals("Electronic Accessories", upsertZskuBase.at("/attributes/family").asText());
        assertEquals("Phone Accessories", upsertZskuBase.at("/attributes/product_type").asText());
        assertEquals("Phone Grips & Stands", upsertZskuBase.at("/attributes/product_subtype").asText());
    }

    @Test
    void realAdapterDoesNotLookupTaxonomyByStaleIdWhenProductFullTypeCodeExists() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.failOnIdProductFullTypeLookup = true;
        sessionFactory.session.taxonomyProductFullTypeCode = "electronic_accessories-phone_accessories-phone_grips_stands";
        sessionFactory.session.taxonomyFamilyNameEn = "Electronic Accessories";
        sessionFactory.session.taxonomyProductTypeNameEn = "Phone Accessories";
        sessionFactory.session.taxonomyProductSubTypeNameEn = "Phone Grips & Stands";
        sessionFactory.session.readBackProductFullType = "electronic_accessories-phone_accessories-phone_grips_stands";
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductFullType("electronic_accessories-phone_accessories-phone_grips_stands");
        request.getDraft().setIdProductFullType(3066L);
        request.getDraft().setFamily("Electronic Accessories");
        request.getDraft().setProductType("Headphones");
        request.getDraft().setProductSubType("Wired Headphones");

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        JsonNode upsertZskuBase = sessionFactory.session.calls.get(2).body;
        assertEquals("Phone Accessories", upsertZskuBase.at("/attributes/product_type").asText());
        assertEquals("Phone Grips & Stands", upsertZskuBase.at("/attributes/product_subtype").asText());
    }

    @Test
    void taxonomyAuthenticationFailureRemainsAReauthenticationActionBeforeCreate() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.taxonomyFailure =
                new NoonAuthenticationRequiredException("Project authorization recovery is pending.");
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductFullType(
                "electronic_accessories-phone_accessories-phone_cases"
        );

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertFalse(result.isSuccess());
        assertEquals("authentication", result.getFailureCategory());
        assertEquals("noon_auth_required", result.getFailureCode());
        assertEquals("pre_create", result.getSteps().get(0).getStepKey());
    }

    @Test
    void realAdapterDelegatesOfferStockWriteWhenEnabled() {
        FakeOfferStockWriteAdapter offerStockWriteAdapter = new FakeOfferStockWriteAdapter();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        properties.setOfferSplitWriteEnabled(true);
        sessionFactory.session.readBackOfferNote = "Launch offer.";
        sessionFactory.session.readBackIsActive = Boolean.TRUE;
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties,
                new FakeImageDownloader(),
                offerStockWriteAdapter
        );

        ProductListingNoonWriteRequest writeRequest = writeRequest();
        writeRequest.getDraft().setPriceMin(new BigDecimal("45.00"));
        writeRequest.getDraft().setPriceMax(new BigDecimal("59.00"));
        writeRequest.getDraft().setSalePrice(new BigDecimal("39.90"));
        writeRequest.getDraft().setSaleStart("2026-07-02T00:00:00+08:00");
        writeRequest.getDraft().setSaleEnd("2026-07-31 23:59:59");
        writeRequest.getDraft().setOfferNote("Launch offer.");

        ProductListingNoonWriteResult result = adapter.execute(writeRequest);

        assertTrue(result.isSuccess());
        assertEquals(1, offerStockWriteAdapter.callCount);
        assertEquals(8, sessionFactory.session.calls.size());
        ProductListingOfferStockWriteRequest request = offerStockWriteAdapter.request;
        assertEquals(10002L, request.getOwnerUserId());
        assertEquals("STR245027-NAE", request.getStoreCode());
        assertEquals("AE", request.getSiteCode());
        assertEquals("240053", request.getIdPartner());
        assertEquals(10001L, request.getDraftId());
        assertEquals(20001L, request.getDryRunTaskId());
        assertEquals(20002L, request.getRealRunTaskId());
        assertEquals(90001L, request.getSubmittedBy());
        assertEquals("NN-TEST-PSKU", request.getPartnerSku());
        assertEquals("ZPARENT", request.getSkuParent());
        assertEquals("PSKU_CODE_1", request.getPskuCode());
        assertEquals(new BigDecimal("45.00"), request.getPriceMin());
        assertEquals(new BigDecimal("59.00"), request.getPriceMax());
        assertEquals(new BigDecimal("39.90"), request.getSalePrice());
        assertEquals("2026-07-02T00:00:00+08:00", request.getSaleStart());
        assertEquals("2026-07-31 23:59:59", request.getSaleEnd());
        assertEquals(null, request.getFbp());
        assertEquals(null, request.getWarehouseId());
        assertEquals(null, request.getWarehouseCode());
        assertEquals(null, request.getQuantity());
        assertEquals(Boolean.TRUE, request.getIsActive());
        assertEquals("Launch offer.", request.getOfferNote());

        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals(49.90, price.at("/price").asDouble());
        assertEquals(45.00, price.at("/priceMin").asDouble());
        assertEquals(59.00, price.at("/priceMax").asDouble());
        assertEquals(39.90, price.at("/salePrice").asDouble());
        assertEquals("2026-07-02", price.at("/saleStart").asText());
        assertEquals("2026-07-31", price.at("/saleEnd").asText());

        ProductListingNoonWriteStepResult offer = result.getSteps().get(7);
        assertEquals("upsert_offer", offer.getStepKey());
        assertEquals("succeeded", offer.getStatus());
        assertEquals("offerStockAdapter=called", offer.getExternalReference());
    }

}
