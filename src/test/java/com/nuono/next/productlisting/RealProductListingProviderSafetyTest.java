package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.product.ProductWriteAuthRecovery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RealProductListingProviderSafetyTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void catalogAuthEnvelopeStopsBeforeCreateAndQueuesRecovery() {
        RecordingSession session = new RecordingSession();
        session.preflightResponse = objectMapper.createObjectNode()
                .set("error", objectMapper.createObjectNode().put("status", 403));
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        when(queue.enqueueProject(10002L, "PRJ240053", "STR245027-NAE"))
                .thenReturn(Optional.of(801L));
        RealProductListingNoonWriteAdapter adapter = adapter(session, queue, (owner, project) -> false);

        ProductListingNoonWriteResult result = adapter.execute(request());

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(801L, result.getRecoveryId());
        assertEquals(Boolean.FALSE, result.getWriteMayHaveOccurred());
        assertEquals(1, session.loginCalls.get());
        assertEquals(1, session.preflightCalls.get());
        assertEquals(0, session.writeCalls.get());
        verify(queue).enqueueProject(10002L, "PRJ240053", "STR245027-NAE");
    }

    @Test
    void malformedCatalogPreflightStopsBeforeCreate() {
        RecordingSession session = new RecordingSession();
        session.preflightResponse = objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode().putArray("hits"));
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        RealProductListingNoonWriteAdapter adapter = adapter(session, queue, (owner, project) -> false);

        ProductListingNoonWriteResult result = adapter.execute(request());

        assertFalse(result.isSuccess());
        assertEquals("noon_pre_create_failed", result.getFailureCode());
        assertTrue(result.getFailureMessage().contains("结构异常"));
        assertEquals(1, session.preflightCalls.get());
        assertEquals(0, session.writeCalls.get());
        verify(queue, never()).enqueueProject(10002L, "PRJ240053", "STR245027-NAE");
    }

    @Test
    void exactPartnerSkuOnSecondPageStillStopsBeforeCreate() {
        RecordingSession session = new RecordingSession();
        ObjectNode firstPage = validPreflight();
        ArrayNode firstHits = (ArrayNode) firstPage.path("data").path("hits");
        for (int index = 0; index < 100; index++) {
            firstHits.addObject().put("partner_sku", "OTHER-" + index);
        }
        ((ObjectNode) firstPage.path("data")).put("total", 101);
        ObjectNode secondPage = validPreflight();
        ((ArrayNode) secondPage.path("data").path("hits"))
                .addObject()
                .put("partner_sku", "NN-TEST-PSKU")
                .put("zsku_parent", "ZPARENT")
                .put("psku_code", "PSKU_CODE_1");
        ((ObjectNode) secondPage.path("data")).put("total", 101);
        session.preflightResponse = firstPage;
        session.secondPreflightResponse = secondPage;
        RealProductListingNoonWriteAdapter adapter = adapter(
                session, mock(NoonProjectAuthRecoveryQueue.class), (owner, project) -> false);

        ProductListingNoonWriteResult result = adapter.execute(request());

        assertEquals("partner_sku_already_exists", result.getFailureCode());
        assertEquals(2, session.preflightCalls.get());
        assertEquals(0, session.writeCalls.get());
    }

    @Test
    void existingExactPartnerSkuStopsBeforeCreate() {
        RecordingSession session = new RecordingSession();
        ObjectNode root = validPreflight();
        ArrayNode hits = (ArrayNode) root.path("data").path("hits");
        hits
                .addObject()
                .put("partner_sku", "NN-TEST-PSKU")
                .put("zsku_parent", "ZPARENT")
                .put("psku_code", "PSKU_CODE_1");
        ((ObjectNode) root.path("data")).put("total", 1);
        session.preflightResponse = root;
        RealProductListingNoonWriteAdapter adapter = adapter(
                session, mock(NoonProjectAuthRecoveryQueue.class), (owner, project) -> false);

        ProductListingNoonWriteResult result = adapter.execute(request());

        assertEquals("partner_sku_already_exists", result.getFailureCode());
        assertEquals(Boolean.FALSE, result.getWriteMayHaveOccurred());
        assertEquals(0, session.writeCalls.get());
    }

    @Test
    void gateBlockedAfterAbsenceProofKeepsPreWriteRecoverySafe() {
        RecordingSession session = new RecordingSession();
        AtomicInteger gateChecks = new AtomicInteger();
        NoonPullProjectAuthGate gate =
                (owner, project) -> gateChecks.incrementAndGet() >= 3;
        RealProductListingNoonWriteAdapter adapter = adapter(
                session, mock(NoonProjectAuthRecoveryQueue.class), gate);

        ProductListingNoonWriteResult result = adapter.execute(request());

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(Boolean.FALSE, result.getWriteMayHaveOccurred());
        assertEquals(1, session.preflightCalls.get());
        assertEquals(0, session.writeCalls.get());
    }

    @Test
    void gateBlockedAfterCreateStopsTheNextProviderCall() {
        RecordingSession session = new RecordingSession();
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        NoonPullProjectAuthGate gate = (owner, project) -> session.writeCalls.get() >= 1;
        RealProductListingNoonWriteAdapter adapter = adapter(session, queue, gate);

        ProductListingNoonWriteResult result = adapter.execute(request());

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(Boolean.TRUE, result.getWriteMayHaveOccurred());
        assertEquals(1, session.preflightCalls.get());
        assertEquals(1, session.writeCalls.get());
        verify(queue, never()).enqueueProject(10002L, "PRJ240053", "STR245027-NAE");
    }

    @Test
    void authorizationPendingCheckDoesNotLoginOrCallProvider() {
        RecordingSession session = new RecordingSession();
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        RealProductListingNoonWriteAdapter adapter = adapter(session, queue, (owner, project) -> true);

        boolean pending = adapter.isAuthorizationRecoveryPending(request());

        assertTrue(pending);
        assertEquals(0, session.loginCalls.get());
        assertEquals(0, session.preflightCalls.get());
        assertEquals(0, session.writeCalls.get());
    }

    private RealProductListingNoonWriteAdapter adapter(
            RecordingSession session,
            NoonProjectAuthRecoveryQueue queue,
            NoonPullProjectAuthGate gate
    ) {
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                objectMapper,
                new FixedBindingResolver(),
                binding -> {
                    session.loginCalls.incrementAndGet();
                    return session;
                },
                new ProductListingRealWriteProperties(),
                imageUrl -> new ProductListingImageDownload("item.jpg", "image/jpeg", new byte[] {1})
        );
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(queue, gate));
        return adapter;
    }

    private ProductListingNoonWriteRequest request() {
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setProductFullType("Family-Type-Subtype");
        draft.setImageUrls(List.of());
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(10002L);
        request.setStoreCode("STR245027-NAE");
        request.setRealRunTaskId(88003L);
        request.setDraft(draft);
        return request;
    }

    private ObjectNode validPreflight() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.putArray("hits");
        data.put("total", 0);
        return root;
    }

    private ObjectNode createResponse() {
        ObjectNode product = objectMapper.createObjectNode();
        product.putObject("parent").put("skuParent", "ZPARENT");
        product.putArray("children").addObject().put("pskuCode", "PSKU_CODE_1");
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("products").add(product);
        return response;
    }

    private final class RecordingSession implements NoonPullGatewaySession {
        private final AtomicInteger loginCalls = new AtomicInteger();
        private final AtomicInteger preflightCalls = new AtomicInteger();
        private final AtomicInteger writeCalls = new AtomicInteger();
        private JsonNode preflightResponse;
        private JsonNode secondPreflightResponse;

        @Override
        public JsonNode postJson(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> headers
        ) {
            int call = preflightCalls.incrementAndGet();
            if (call == 2 && secondPreflightResponse != null) {
                return secondPreflightResponse;
            }
            return preflightResponse == null ? validPreflight() : preflightResponse;
        }

        @Override
        public JsonNode postWriteJson(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> headers
        ) {
            return writeCalls.incrementAndGet() == 1
                    ? createResponse()
                    : objectMapper.createObjectNode();
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> headers) {
            return new byte[0];
        }
    }

    private static final class FixedBindingResolver extends NoonPullStoreBindingResolver {
        private FixedBindingResolver() {
            super(null);
        }

        @Override
        public NoonPullStoreBinding resolve(NoonInterfacePullRequest request) {
            return new NoonPullStoreBinding(
                    request.getOwnerUserId(), "PRJ240053", request.getStoreCode(), "AE",
                    "240053", "merchant@example.test", "secret", null, "sid=test");
        }
    }
}
