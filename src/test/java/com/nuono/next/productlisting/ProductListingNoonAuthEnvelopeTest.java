package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
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

class ProductListingNoonAuthEnvelopeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Test
    void freshCreateDeterministicAuthEnvelopeHasNoWriteRisk() {
        EnvelopeSession session = new EnvelopeSession();
        session.firstWriteResponse = objectMapper.createObjectNode().put("code", 403);
        RecoveryFixture fixture = fixture(session, 701L);

        ProductListingNoonWriteResult result = fixture.adapter.execute(request(List.of()));

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertNull(result.getRecoveryId());
        assertFalse(result.getWriteMayHaveOccurred());
        assertEquals(1, session.writeCalls.get());
    }
    @Test
    void authEnvelopeAfterSuccessfulCreateStopsLaterWritesAndKeepsRisk() {
        EnvelopeSession session = new EnvelopeSession();
        session.writeResponseAfterCreate = objectMapper.createObjectNode()
                .set("error", objectMapper.createObjectNode().put("status", 307));
        RecoveryFixture fixture = fixture(session, 702L);

        ProductListingNoonWriteResult result = fixture.adapter.execute(request(List.of()));

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertTrue(result.getWriteMayHaveOccurred());
        assertEquals(2, session.writeCalls.get());
    }
    @Test
    void imageUploadAuthEnvelopeStopsBeforeContentWrites() {
        EnvelopeSession session = new EnvelopeSession();
        session.uploadResponse = objectMapper.createObjectNode()
                .put("message", "authorization rejected")
                .put("status", 403);
        RecoveryFixture fixture = fixture(session, 703L);

        ProductListingNoonWriteResult result =
                fixture.adapter.execute(request(List.of("https://img.example/item.jpg")));

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertTrue(result.getWriteMayHaveOccurred());
        assertEquals(3, session.writeCalls.get());
        assertEquals(1, session.uploadCalls.get());
    }
    @Test
    void readBackAuthEnvelopeStopsWithoutReadRetry() {
        EnvelopeSession session = new EnvelopeSession();
        session.readResponse = objectMapper.createObjectNode()
                .put("detail", "session rejected")
                .put("status", 401);
        RecoveryFixture fixture = fixture(session, 704L);

        ProductListingNoonWriteResult result = fixture.adapter.execute(request(List.of()));

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertTrue(result.getWriteMayHaveOccurred());
        assertEquals(1, session.readCalls.get());
    }
    @Test
    void splitOfferAuthEnvelopeStopsBeforeSecondOfferWrite() {
        EnvelopeSession session = new EnvelopeSession();
        session.firstWriteResponse = objectMapper.createObjectNode()
                .put("error", "authorization rejected")
                .put("code", 403);
        ProductListingOfferStockWriteRequest request = new ProductListingOfferStockWriteRequest();
        request.setPartnerSku("PARTNER-SKU");
        request.setPskuCode("PSKU-CODE");
        request.setOfferNote("note");
        request.setIsActive(true);

        ProductListingNoonWriteStepResult result =
                new RealProductListingOfferStockWriteAdapter(objectMapper).writeOfferStock(
                        request, session, new ProductListingRealWriteProperties.Endpoints(), Map.of());

        assertEquals("failed", result.getStatus());
        assertTrue(result.getFailureMessage().contains("auth_required"));
        assertFalse(result.getWriteMayHaveOccurred());
        assertEquals(1, session.writeCalls.get());
    }

    @Test
    void executionLeaseLossStopsBeforeTheNextProviderWrite() {
        EnvelopeSession session = new EnvelopeSession();
        RecoveryFixture fixture = fixture(session, 705L);
        ProductListingNoonWriteRequest request = request(List.of());
        AtomicInteger heartbeats = new AtomicInteger();
        request.setExecutionLeaseHeartbeat(() -> {
            if (heartbeats.incrementAndGet() == 4) {
                throw new IllegalStateException("stale claim");
            }
        });

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> fixture.adapter.execute(request));

        assertTrue(ProductListingNoonWriteRequest.isExecutionLeaseLost(failure));
        assertEquals(4, heartbeats.get());
        assertEquals(1, session.writeCalls.get());
    }

    @Test
    void executionLeaseHeartbeatIsNotSerialized() throws Exception {
        ProductListingNoonWriteRequest request = request(List.of());
        AtomicInteger heartbeats = new AtomicInteger();
        request.setExecutionLeaseHeartbeat(heartbeats::incrementAndGet);

        request.heartbeatOrThrow();
        String json = objectMapper.writeValueAsString(request);

        assertEquals(1, heartbeats.get());
        assertFalse(json.contains("executionLease"));
        assertFalse(json.contains("heartbeat"));
    }

    @Test
    void acceptedCreateResponseWithoutReferencesKeepsUnknownWriteRisk() {
        EnvelopeSession session = new EnvelopeSession();
        session.firstWriteResponse = objectMapper.createObjectNode();
        RecoveryFixture fixture = fixture(session, 706L);

        ProductListingNoonWriteResult result = fixture.adapter.execute(request(List.of()));

        assertEquals("noon_create_outcome_unknown", result.getFailureCode());
        assertTrue(result.getWriteMayHaveOccurred());
    }

    private RecoveryFixture fixture(EnvelopeSession session, long recoveryId) {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        when(queue.enqueue(any(NoonAuthWaitRequest.class)))
                .thenReturn(Optional.of(recoveryId));
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setReadBackMaxAttempts(5);
        properties.setReadBackRetryDelayMillis(0L);
        RealProductListingNoonWriteAdapter adapter = new RealProductListingNoonWriteAdapter(
                objectMapper,
                new FixedBindingResolver(),
                binding -> session,
                properties,
                imageUrl -> new ProductListingImageDownload("item.jpg", "image/jpeg", new byte[] {1})
        );
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                mock(com.nuono.next.noon.NoonAccountSessionAttentionPort.class)));
        return new RecoveryFixture(adapter, queue);
    }

    private static NoonAuthWaitRequest listingWaitRequest(
            String checkpoint,
            NoonAuthResumePolicy resumePolicy
    ) {
        return NoonAuthWaitRequest.task(
                10002L,
                "PRJ240053",
                "STR245027-NAE",
                "AE",
                "PRODUCT_LISTING",
                88003L,
                checkpoint,
                resumePolicy
        );
    }

    private ProductListingNoonWriteRequest request(List<String> images) {
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setProductFullType("Family-Type-Subtype");
        draft.setImageUrls(images);
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(10002L);
        request.setStoreCode("STR245027-NAE");
        request.setRealRunTaskId(88003L);
        request.setDraft(draft);
        return request;
    }

    private ObjectNode createResponse() {
        ObjectNode product = objectMapper.createObjectNode();
        product.putObject("parent").put("skuParent", "ZPARENT");
        product.putArray("children").addObject().put("pskuCode", "PSKU_CODE_1");
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("products").add(product);
        return response;
    }

    private final class EnvelopeSession implements NoonPullGatewaySession {
        private final AtomicInteger writeCalls = new AtomicInteger();
        private final AtomicInteger uploadCalls = new AtomicInteger();
        private final AtomicInteger readCalls = new AtomicInteger();
        private JsonNode firstWriteResponse;
        private JsonNode writeResponseAfterCreate;
        private JsonNode uploadResponse;
        private JsonNode readResponse;

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> headers) {
            if (ProductListingRealWriteProperties.Endpoints.DEFAULT_OFFER_LIST_URL.equals(url)) {
                ObjectNode root = objectMapper.createObjectNode();
                ObjectNode data = root.putObject("data").put("total", 0);
                data.putArray("hits");
                return root;
            }
            readCalls.incrementAndGet();
            return readResponse == null ? objectMapper.createObjectNode() : readResponse;
        }

        @Override
        public JsonNode postWriteJson(
                String url, JsonNode body, boolean withProject, Map<String, String> headers) {
            int call = writeCalls.incrementAndGet();
            if (call == 1) {
                return firstWriteResponse == null ? createResponse() : firstWriteResponse;
            }
            return writeResponseAfterCreate == null ? objectMapper.createObjectNode() : writeResponseAfterCreate;
        }

        @Override
        public JsonNode postMultipartFile(
                String url,
                String fieldName,
                String fileName,
                String contentType,
                byte[] content,
                boolean withProject,
                Map<String, String> headers
        ) {
            uploadCalls.incrementAndGet();
            return uploadResponse == null
                    ? objectMapper.createObjectNode().put("upload_path", "/image/item.jpg")
                    : uploadResponse;
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
                    "240053", "merchant@example.test", "sid=test");
        }
    }

    private static final class RecoveryFixture {
        private final RealProductListingNoonWriteAdapter adapter;
        private final NoonAuthWaitQueue queue;

        private RecoveryFixture(
                RealProductListingNoonWriteAdapter adapter,
                NoonAuthWaitQueue queue
        ) {
            this.adapter = adapter;
            this.queue = queue;
        }
    }
}
