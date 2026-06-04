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
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                objectMapper,
                bindingResolver,
                sessionFactory,
                properties
        );

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertTrue(result.isSuccess());
        assertEquals(List.of(
                "create_product",
                "upsert_zsku",
                "upsert_offer",
                "upsert_price",
                "upsert_warranty",
                "upsert_barcode"
        ), result.getSteps().stream()
                .map(ProductListingNoonWriteStepResult::getStepKey)
                .collect(Collectors.toList()));
        assertEquals(10002L, bindingResolver.request.getOwnerUserId());
        assertEquals("STR245027-NAE", bindingResolver.request.getStoreCode());
        assertEquals(NoonPullDataDomain.PRODUCT, bindingResolver.request.getDataDomain());
        assertEquals(6, sessionFactory.session.calls.size());

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

        JsonNode upsertZsku = sessionFactory.session.calls.get(1).body;
        assertEquals("ZPARENT", upsertZsku.at("/skuParent").asText());
        assertEquals("Generic", upsertZsku.at("/attributes/brand").asText());
        assertEquals("electronic_accessories", upsertZsku.at("/attributes/family").asText());
        assertEquals("headphones", upsertZsku.at("/attributes/product_type").asText());
        assertEquals("wired_headphones", upsertZsku.at("/attributes/product_subtype").asText());
        assertEquals("new", upsertZsku.at("/attributes/item_condition").asText());

        JsonNode offer = sessionFactory.session.calls.get(2).body;
        assertEquals("PSKU_CODE_1", offer.at("/pskus/0/pskuCode").asText());
        assertEquals("AE", offer.at("/pskus/0/country").asText());
        assertEquals(1, offer.at("/pskus/0/isActive").asInt());
        assertEquals("manual", offer.at("/pskus/0/pricingMethod").asText());
        assertEquals(0, offer.at("/pskus/0/price").decimalValue().compareTo(new BigDecimal("49.90")));
        assertEquals("W00752151SA", offer.at("/pskus/0/stocks/0/idWarehouse").asText());
        assertEquals("100", offer.at("/pskus/0/stocks/0/quantity").asText());

        JsonNode price = sessionFactory.session.calls.get(3).body;
        assertEquals("PSKU_CODE_1", price.at("/pskuCode").asText());
        assertEquals("NN-TEST-PSKU", price.at("/partnerSku").asText());
        assertEquals("manual", price.at("/pricingMethod").asText());

        JsonNode warranty = sessionFactory.session.calls.get(4).body;
        assertEquals("PSKU_CODE_1", warranty.at("/pskuCode").asText());
        assertEquals(24, warranty.at("/idWarranty").asInt());

        JsonNode barcode = sessionFactory.session.calls.get(5).body;
        assertEquals("NN-TEST-PSKU", barcode.at("/barcodeReqList/0/partnerSku").asText());
        assertEquals("6290000000001", barcode.at("/barcodeReqList/0/partnerBarcode").asText());
        assertTrue(barcode.at("/forceMapping").asBoolean());
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

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            throw new AssertionError("real listing adapter must use postWriteJson for Noon writes");
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
            throw new AssertionError("real listing adapter must not download files in this skeleton");
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
