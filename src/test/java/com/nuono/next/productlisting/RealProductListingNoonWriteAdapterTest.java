package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class RealProductListingNoonWriteAdapterTest {

    @Test
    void realAdapterBuildsExpectedNoonWriteRequests() {
        FakeBindingResolver bindingResolver = new FakeBindingResolver();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                bindingResolver,
                sessionFactory,
                new ProductListingRealWriteProperties(),
                new FakeImageDownloader()
        );

        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setProductDescriptionEn("English long description");
        request.getDraft().setProductDescriptionAr("Arabic long description");
        request.getDraft().setProductHighlightsEn(List.of("Noise cancelling", "USB-C charging"));
        request.getDraft().setProductHighlightsAr(List.of("Arabic noise cancelling"));

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(List.of(
                "create_product",
                "sku_cache",
                "upsert_zsku_base",
                "upload_images",
                "upsert_zsku_content_en",
                "upsert_zsku_content_ar",
                "upsert_price",
                "upsert_offer",
                "upsert_warranty",
                "upsert_barcode",
                "verify_noon_readback"
        ), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        assertEquals(10002L, bindingResolver.request.getOwnerUserId());
        assertEquals("STR245027-NAE", bindingResolver.request.getStoreCode());
        assertEquals(NoonPullDataDomain.PRODUCT, bindingResolver.request.getDataDomain());
        assertEquals(8, sessionFactory.session.calls.size());
        assertEquals(1, sessionFactory.session.uploadCalls.size());
        assertEquals(1, sessionFactory.session.retrieveCallCount);

        FakeSession.Call createProduct = sessionFactory.session.calls.get(0);
        assertEquals(ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL, createProduct.url);
        assertEquals("AE", createProduct.extraHeaders.get("Country-Code"));
        assertEquals("240053", createProduct.extraHeaders.get("Id-Partner"));
        assertEquals("NN-TEST-PSKU", createProduct.body.at("/productCreate/0/variations/0/partnerSku").asText());
        assertEquals(false, createProduct.body.at("/productCreate/0/gated_zsku").asBoolean());

        JsonNode upsertZskuBase = sessionFactory.session.calls.get(2).body;
        assertEquals("ZPARENT", upsertZskuBase.at("/skuParent").asText());
        assertEquals("Generic", upsertZskuBase.at("/attributes/brand").asText());
        assertEquals("Electronic Accessories", upsertZskuBase.at("/attributes/family").asText());
        assertEquals("Headphones", upsertZskuBase.at("/attributes/product_type").asText());
        assertEquals("Wired Headphones", upsertZskuBase.at("/attributes/product_subtype").asText());

        JsonNode contentEn = sessionFactory.session.calls.get(3).body;
        assertEquals("en", contentEn.at("/lang").asText());
        assertEquals("Wired headphones with microphone", contentEn.at("/attributes/product_title").asText());
        assertEquals("English long description", contentEn.at("/attributes/long_description").asText());
        assertEquals("Noise cancelling", contentEn.at("/attributes/feature_bullet_1").asText());
        assertEquals("USB-C charging", contentEn.at("/attributes/feature_bullet_2").asText());
        assertEquals("noon-uploaded/sku-main.jpg", contentEn.at("/attributes/image_url_1").asText());

        JsonNode contentAr = sessionFactory.session.calls.get(4).body;
        assertEquals("ar", contentAr.at("/lang").asText());
        assertEquals("Arabic wired headphones title", contentAr.at("/attributes/product_title").asText());
        assertEquals("Arabic long description", contentAr.at("/attributes/long_description").asText());
        assertEquals("Arabic noise cancelling", contentAr.at("/attributes/feature_bullet_1").asText());

        FakeSession.UploadCall uploadImage = sessionFactory.session.uploadCalls.get(0);
        assertEquals(ProductListingRealWriteProperties.Endpoints.DEFAULT_UPLOAD_IMAGE_URL, uploadImage.url);
        assertEquals("file", uploadImage.fieldName);
        assertEquals("sku-main.jpg", uploadImage.fileName);
        assertEquals("image/jpeg", uploadImage.contentType);

        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals("PSKU_CODE_1", price.at("/pskuCode").asText());
        assertEquals("NN-TEST-PSKU", price.at("/partnerSku").asText());
        assertEquals("manual", price.at("/pricingMethod").asText());

        ProductListingNoonWriteStepResult offer = result.getSteps().get(7);
        assertEquals("upsert_offer", offer.getStepKey());
        assertEquals("skipped", offer.getStatus());
        assertEquals("noon_offer_stock_write_not_enabled", offer.getFailureCode());

        JsonNode warranty = sessionFactory.session.calls.get(6).body;
        assertEquals("PSKU_CODE_1", warranty.at("/pskuCode").asText());
        assertEquals("NN-TEST-PSKU", warranty.at("/partnerSku").asText());
        assertEquals(24, warranty.at("/idWarranty").asInt());

        JsonNode barcode = sessionFactory.session.calls.get(7).body;
        assertEquals("NN-TEST-PSKU", barcode.at("/pbarcodeUpsert/0/partnerSku").asText());
        assertEquals("6290000000001", barcode.at("/pbarcodeUpsert/0/partnerBarcode").asText());
        assertTrue(barcode.at("/forceMapping").asBoolean());
    }

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
    void realAdapterDelegatesOfferStockWriteWhenEnabled() {
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
        writeRequest.getDraft().setPriceMin(new BigDecimal("45.00"));
        writeRequest.getDraft().setPriceMax(new BigDecimal("59.00"));
        writeRequest.getDraft().setSalePrice(new BigDecimal("39.90"));
        writeRequest.getDraft().setSaleStart("2026-07-02T00:00:00+08:00");
        writeRequest.getDraft().setSaleEnd("2026-07-31 23:59:59");

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
        assertEquals(true, request.getFbp());
        assertEquals("73001", request.getWarehouseId());
        assertEquals("W00752151SA", request.getWarehouseCode());
        assertEquals(100, request.getQuantity());

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
        writeRequest.getDraft().setFbp(null);
        writeRequest.getDraft().setWarehouseId(null);
        writeRequest.getDraft().setWarehouseCode(null);
        writeRequest.getDraft().setQuantity(null);
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
    void realAdapterDoesNotFailWhenNoonOmitsProductFullTypeReadBack() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        sessionFactory.session.readBackProductFullType = null;
        sessionFactory.session.readBackProductFullTypeCode = null;
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
    void realAdapterReadBackOnlyDoesNotCallNoonWriteEndpoints() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
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
        assertEquals("noon_write_failed", result.getFailureCode());
        assertTrue(result.getFailureMessage().contains("fulltype"));
        assertEquals(List.of("create_product", "sku_cache", "upsert_zsku_base"), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        ProductListingNoonWriteStepResult failedStep = result.getSteps().get(2);
        assertEquals("failed", failedStep.getStatus());
        assertTrue(failedStep.getFailureMessage().contains("partner_error"));
    }

    private ProductListingNoonWriteRequest writeRequest() {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(10002L);
        request.setStoreCode("STR245027-NAE");
        request.setDraftId(10001L);
        request.setDryRunTaskId(20001L);
        request.setRealRunTaskId(20002L);
        request.setSubmittedBy(90001L);
        request.setDraft(ProductListingTestFixtures.validCommand());
        request.setConfirmation(ProductListingTestFixtures.confirmedCommand());
        return request;
    }

    private static class FakeBindingResolver extends NoonPullStoreBindingResolver {
        private NoonInterfacePullRequest request;

        FakeBindingResolver() {
            super(null);
        }

        @Override
        public NoonPullStoreBinding resolve(NoonInterfacePullRequest request) {
            this.request = request;
            return new NoonPullStoreBinding(
                    request.getOwnerUserId(),
                    "PRJ240053",
                    request.getStoreCode(),
                    "AE",
                    "240053",
                    "merchant@example.test",
                    "secret",
                    null,
                    "sid=test"
            );
        }
    }

    private static class FakeSessionFactory implements NoonPullGatewaySessionFactory {
        private final FakeSession session = new FakeSession();

        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            return session;
        }
    }

    private static class FakeSession implements NoonPullGatewaySession {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<Call> calls = new ArrayList<>();
        private final List<UploadCall> uploadCalls = new ArrayList<>();
        private int retrieveCallCount;
        private int readBackImagesAvailableAfterAttempt = 1;
        private boolean baseUpsertReturnsInvalid;
        private int zskuUpsertCount;
        private String readBackBrand = "Generic";
        private String readBackProductFullType = "electronic_accessories-headphones-wired_headphones";
        private String readBackProductFullTypeCode;
        private String taxonomyProductFullTypeCode = "electronic_accessories-headphones-wired_headphones";
        private String taxonomyFamilyNameEn = "Electronic Accessories";
        private String taxonomyProductTypeNameEn = "Headphones";
        private String taxonomyProductSubTypeNameEn = "Wired Headphones";
        private boolean failOnIdProductFullTypeLookup;

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            retrieveCallCount++;
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode product = root.putObject("ZPARENT");
            ObjectNode attributes = product.putObject("attributes");
            ObjectNode common = attributes.putObject("common");
            common.put("brand", readBackBrand);
            if (readBackProductFullType != null) {
                common.put("product_fulltype", readBackProductFullType);
            }
            if (readBackProductFullTypeCode != null) {
                common.put("product_fulltype_code", readBackProductFullTypeCode);
            }
            if (retrieveCallCount >= readBackImagesAvailableAfterAttempt) {
                common.put("image_url_1", "noon-uploaded/sku-main.jpg");
            }
            attributes.putObject("en").put("product_title", "Wired headphones with microphone");
            attributes.putObject("ar").put("product_title", "Arabic wired headphones title");
            return root;
        }

        @Override
        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            calls.add(new Call(url, body, withProject, extraHeaders));
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL.equals(url)) {
                ObjectNode response = objectMapper.createObjectNode();
                ArrayNode products = response.putArray("products");
                ObjectNode product = products.addObject();
                product.set("parent", objectMapper.createObjectNode().put("skuParent", "ZPARENT"));
                product.putArray("children").addObject().put("pskuCode", "PSKU_CODE_1");
                return response;
            }
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_ZSKU_URL.equals(url)) {
                zskuUpsertCount++;
                if (baseUpsertReturnsInvalid && zskuUpsertCount == 1) {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("invalid", 1);
                    response.putObject("error").put("partner_error", "fulltype is invalid");
                    return response;
                }
            }
            return objectMapper.createObjectNode();
        }

        @Override
        public JsonNode postMultipartFile(
                String url,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            uploadCalls.add(new UploadCall(url, fieldName, fileName, contentType, content, withProject, extraHeaders));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("upload_path", "noon-uploaded/sku-main.jpg");
            return response;
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            if (failOnIdProductFullTypeLookup && url != null && url.contains("id_product_fulltype")) {
                throw new AssertionError("stale id_product_fulltype lookup should not be used");
            }
            return ("{"
                    + "\"data\":[{"
                    + "\"id_product_fulltype\":3066,"
                    + "\"product_fulltype_code\":\"" + taxonomyProductFullTypeCode + "\","
                    + "\"family_name_en\":\"" + taxonomyFamilyNameEn + "\","
                    + "\"product_type_name_en\":\"" + taxonomyProductTypeNameEn + "\","
                    + "\"product_subtype_name_en\":\"" + taxonomyProductSubTypeNameEn + "\""
                    + "}]"
                    + "}").getBytes();
        }

        private static class Call {
            private final String url;
            private final JsonNode body;
            private final boolean withProject;
            private final Map<String, String> extraHeaders;

            private Call(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
                this.url = url;
                this.body = body;
                this.withProject = withProject;
                this.extraHeaders = extraHeaders;
            }
        }

        private static class UploadCall {
            private final String url;
            private final String fieldName;
            private final String fileName;
            private final String contentType;
            private final byte[] content;
            private final boolean withProject;
            private final Map<String, String> extraHeaders;

            private UploadCall(
                    String url,
                    String fieldName,
                    String fileName,
                    String contentType,
                    byte[] content,
                    boolean withProject,
                    Map<String, String> extraHeaders
            ) {
                this.url = url;
                this.fieldName = fieldName;
                this.fileName = fileName;
                this.contentType = contentType;
                this.content = content;
                this.withProject = withProject;
                this.extraHeaders = extraHeaders;
            }
        }
    }

    private static class FakeImageDownloader implements ProductListingImageDownloader {
        @Override
        public ProductListingImageDownload download(String imageUrl) {
            return new ProductListingImageDownload(
                    "sku-main.jpg",
                    "image/jpeg",
                    new byte[] {1, 2, 3}
            );
        }
    }

    private static class FakeOfferStockWriteAdapter implements ProductListingOfferStockWriteAdapter {
        private int callCount;
        private ProductListingOfferStockWriteRequest request;

        @Override
        public ProductListingNoonWriteStepResult writeOfferStock(
                ProductListingOfferStockWriteRequest request,
                NoonPullGatewaySession session,
                ProductListingRealWriteProperties.Endpoints endpoints,
                Map<String, String> headers
        ) {
            callCount++;
            this.request = request;
            ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
            step.setStepKey("upsert_offer");
            step.setStatus("succeeded");
            step.setExternalReference("offerStockAdapter=called");
            return step;
        }
    }
}
