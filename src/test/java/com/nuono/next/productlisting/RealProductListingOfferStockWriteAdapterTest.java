package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealProductListingOfferStockWriteAdapterTest {

    @Test
    void writesOfferNoteAndActiveStateThroughSplitOfferEndpointsOnly() {
        FakeSession session = new FakeSession();
        ProductListingOfferStockWriteRequest request = request();
        request.setOfferNote("Launch note");
        request.setIsActive(false);
        request.setWarehouseCode("W00752151SA");
        request.setQuantity(100);

        ProductListingNoonWriteStepResult step = new RealProductListingOfferStockWriteAdapter(new ObjectMapper())
                .writeOfferStock(request, session, new ProductListingRealWriteProperties.Endpoints(), headers());

        assertEquals("upsert_offer", step.getStepKey());
        assertEquals("succeeded", step.getStatus());
        assertEquals("written=offer_note,is_active;skipped=warehouse_stock", step.getExternalReference());
        assertEquals(2, session.calls.size());
        assertEquals(
                ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_OFFER_NOTE_URL,
                session.calls.get(0).url
        );
        assertEquals(
                ProductListingRealWriteProperties.Endpoints.DEFAULT_UPSERT_IS_ACTIVE_URL,
                session.calls.get(1).url
        );
        assertEquals("PSKU_CODE_1", session.calls.get(0).body.path("pskuCode").asText());
        assertEquals("NN-TEST-PSKU", session.calls.get(0).body.path("partnerSku").asText());
        assertEquals("SA", session.calls.get(0).body.path("countryCode").asText());
        assertEquals("Launch note", session.calls.get(0).body.path("offerNote").asText());
        assertEquals(false, session.calls.get(1).body.path("isActive").asBoolean());
        assertTrue(!session.calls.stream().anyMatch(call -> call.url.contains("/_svc/mp-partner-catalog/offer/upsert")));
    }

    @Test
    void skipsWhenOnlyWarehouseOrStockFieldsArePresent() {
        FakeSession session = new FakeSession();
        ProductListingOfferStockWriteRequest request = request();
        request.setWarehouseId("73001");
        request.setWarehouseCode("W00752151SA");
        request.setQuantity(100);
        request.setFbp(true);

        ProductListingNoonWriteStepResult step = new RealProductListingOfferStockWriteAdapter(new ObjectMapper())
                .writeOfferStock(request, session, new ProductListingRealWriteProperties.Endpoints(), headers());

        assertEquals("upsert_offer", step.getStepKey());
        assertEquals("skipped", step.getStatus());
        assertEquals("noon_offer_stock_warehouse_stock_not_supported", step.getFailureCode());
        assertEquals(0, session.calls.size());
    }

    private ProductListingOfferStockWriteRequest request() {
        ProductListingOfferStockWriteRequest request = new ProductListingOfferStockWriteRequest();
        request.setStoreCode("STR245027-NSA");
        request.setSiteCode("SA");
        request.setIdPartner("245027");
        request.setPartnerSku("NN-TEST-PSKU");
        request.setPskuCode("PSKU_CODE_1");
        request.setSubmittedBy(90001L);
        return request;
    }

    private Map<String, String> headers() {
        return Map.of(
                "x-project", "PRJ245027",
                "x-locale", "en-SA",
                "Country-Code", "SA",
                "Id-Partner", "245027"
        );
    }

    private static class FakeSession implements NoonPullGatewaySession {
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final List<Call> calls = new ArrayList<>();

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            throw new UnsupportedOperationException("read call not expected");
        }

        @Override
        public JsonNode postWriteJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            calls.add(new Call(url, body, withProject, extraHeaders));
            return objectMapper.createObjectNode();
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            throw new UnsupportedOperationException("get call not expected");
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
