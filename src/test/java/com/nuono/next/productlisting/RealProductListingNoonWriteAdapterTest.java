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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RealProductListingNoonWriteAdapterTest {

    @Test
    void realAdapterBuildsExpectedNoonWriteRequests() {
        ObjectMapper objectMapper = new ObjectMapper();
        FakeBindingResolver bindingResolver = new FakeBindingResolver();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        properties.setOfferUpsertEnabled(true);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                objectMapper,
                bindingResolver,
                sessionFactory,
                properties
        );

        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setWarehouseId("W00752151SA");
        request.getDraft().setWarehouseCode(null);

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(List.of(
                "create_product",
                "sku_cache",
                "upsert_zsku_base",
                "upsert_zsku_content_en",
                "upsert_zsku_content_ar",
                "upsert_price",
                "upsert_offer",
                "upsert_warranty",
                "upsert_barcode"
        ), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        assertEquals(10002L, bindingResolver.request.getOwnerUserId());
        assertEquals("STR245027-NAE", bindingResolver.request.getStoreCode());
        assertEquals(NoonPullDataDomain.PRODUCT, bindingResolver.request.getDataDomain());
        assertEquals(8, sessionFactory.session.calls.size());
        assertEquals(0, sessionFactory.session.readCalls.size());

        FakeSession.Call createProduct = sessionFactory.session.calls.get(0);
        assertEquals(
                ProductListingRealWriteProperties.Endpoints.DEFAULT_CREATE_PRODUCT_URL,
                createProduct.url
        );
        assertTrue(createProduct.withProject);
        assertEquals("AE", createProduct.extraHeaders.get("Country-Code"));
        assertEquals("240053", createProduct.extraHeaders.get("Id-Partner"));
        assertEquals("NN-TEST-PSKU", createProduct.body.at("/productCreate/0/variations/0/partnerSku").asText());
        assertEquals(false, createProduct.body.at("/productCreate/0/gated_zsku").asBoolean());

        FakeSession.Call skuCache = sessionFactory.session.calls.get(1);
        assertEquals(ProductListingRealWriteProperties.Endpoints.DEFAULT_SKU_CACHE_URL, skuCache.url);
        assertEquals("ZPARENT", skuCache.body.at("/skuParent").asText());

        JsonNode upsertZskuBase = sessionFactory.session.calls.get(2).body;
        assertEquals("ZPARENT", upsertZskuBase.at("/skuParent").asText());
        assertEquals("Generic", upsertZskuBase.at("/attributes/brand").asText());
        assertEquals("electronic_accessories", upsertZskuBase.at("/attributes/family").asText());
        assertEquals("headphones", upsertZskuBase.at("/attributes/product_type").asText());
        assertEquals("wired_headphones", upsertZskuBase.at("/attributes/product_subtype").asText());
        assertEquals("electronic_accessories-headphones-wired_headphones", upsertZskuBase.at("/attributes/product_fulltype").asText());
        assertEquals("new", upsertZskuBase.at("/attributes/item_condition").asText());
        assertTrue(upsertZskuBase.at("/attributes/update_fulltype").asBoolean());

        JsonNode contentEn = sessionFactory.session.calls.get(3).body;
        assertEquals("en", contentEn.at("/lang").asText());
        assertEquals("Wired headphones with microphone", contentEn.at("/attributes/product_title").asText());
        assertEquals("https://example.test/images/sku-main.jpg", contentEn.at("/attributes/image_url_1").asText());

        JsonNode contentAr = sessionFactory.session.calls.get(4).body;
        assertEquals("ar", contentAr.at("/lang").asText());
        assertEquals("Arabic wired headphones title", contentAr.at("/attributes/product_title").asText());

        JsonNode price = sessionFactory.session.calls.get(5).body;
        assertEquals("PSKU_CODE_1", price.at("/pskuCode").asText());
        assertEquals("NN-TEST-PSKU", price.at("/partnerSku").asText());
        assertEquals("manual", price.at("/pricingMethod").asText());

        ProductListingNoonWriteStepResult skippedOffer = result.getSteps().get(6);
        assertEquals("skipped", skippedOffer.getStatus());
        assertEquals("noon_offer_upsert_not_supported_for_new_listing", skippedOffer.getFailureCode());
        assertTrue(skippedOffer.getFailureMessage().contains("legacy create SKU chain"));

        JsonNode warranty = sessionFactory.session.calls.get(6).body;
        assertEquals("PSKU_CODE_1", warranty.at("/pskuCode").asText());
        assertEquals(24, warranty.at("/idWarranty").asInt());

        JsonNode barcode = sessionFactory.session.calls.get(7).body;
        assertEquals("NN-TEST-PSKU", barcode.at("/barcodeReqList/0/partnerSku").asText());
        assertEquals("6290000000001", barcode.at("/barcodeReqList/0/partnerBarcode").asText());
        assertTrue(barcode.at("/forceMapping").asBoolean());
    }

    @Test
    void realAdapterDoesNotResolveWarehouseForUnsupportedOfferUpsert() {
        ObjectMapper objectMapper = new ObjectMapper();
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                objectMapper,
                new FakeBindingResolver(),
                sessionFactory,
                properties
        );
        ProductListingNoonWriteRequest request = writeRequest();
        request.getDraft().setWarehouseId("73001");
        request.getDraft().setWarehouseCode("W00752151SA");

        ProductListingNoonWriteResult result = adapter.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(0, sessionFactory.session.readCalls.size());
        assertEquals(8, sessionFactory.session.calls.size());
        ProductListingNoonWriteStepResult skippedOffer = result.getSteps().get(6);
        assertEquals("upsert_offer", skippedOffer.getStepKey());
        assertEquals("skipped", skippedOffer.getStatus());
        assertEquals("noon_offer_upsert_not_supported_for_new_listing", skippedOffer.getFailureCode());
    }

    @Test
    void realAdapterSkipsOfferUpsertByDefault() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties()
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(result.isSuccess());
        assertEquals(0, sessionFactory.session.readCalls.size());
        assertEquals(List.of(
                "create_product",
                "sku_cache",
                "upsert_zsku_base",
                "upsert_zsku_content_en",
                "upsert_zsku_content_ar",
                "upsert_price",
                "upsert_warranty",
                "upsert_barcode"
        ), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
    }

    @Test
    void realAdapterSkipsUnsupportedOfferUpsertWhenEnabled() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setOfferUpsertEnabled(true);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FakeBindingResolver(),
                sessionFactory,
                properties
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(result.isSuccess());
        assertEquals(List.of(
                "create_product",
                "sku_cache",
                "upsert_zsku_base",
                "upsert_zsku_content_en",
                "upsert_zsku_content_ar",
                "upsert_price",
                "upsert_offer",
                "upsert_warranty",
                "upsert_barcode"
        ), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        assertEquals("skipped", result.getSteps().get(6).getStatus());
        assertEquals("noon_offer_upsert_not_supported_for_new_listing", result.getSteps().get(6).getFailureCode());
        assertEquals(8, sessionFactory.session.calls.size());
        assertEquals(0, sessionFactory.session.readCalls.size());
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
        private final List<Call> readCalls = new ArrayList<>();

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            readCalls.add(new Call(url, body, withProject, extraHeaders));
            throw new AssertionError("unexpected POST read call " + url);
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
            return objectMapper.createObjectNode();
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            readCalls.add(new Call(url, null, withProject, extraHeaders));
            throw new AssertionError("unexpected read call " + url);
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
    }
}
