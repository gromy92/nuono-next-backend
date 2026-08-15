package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.product.ProductWriteAuthRecovery;
import com.nuono.next.product.noon.NoonProductError;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RealProductListingNoonWriteAuthRecoveryTest {

    @Test
    void newAuthFailureAfterCreateRequiresManualLoginAndPreservesWriteRisk() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        when(queue.enqueue(any(NoonAuthWaitRequest.class)))
                .thenReturn(Optional.of(991L));
        AtomicInteger writeCalls = new AtomicInteger();
        NoonPullGatewaySession session = sessionThatFailsAfterCreate(writeCalls);
        RealProductListingNoonWriteAdapter adapter = adapter(binding -> session);
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                mock(com.nuono.next.noon.NoonAccountSessionAttentionPort.class)));

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertFalse(result.isSuccess());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertNull(result.getRecoveryId());
        assertTrue(result.getWriteMayHaveOccurred());
        assertEquals(2, writeCalls.get());
        assertEquals("succeeded", result.getSteps().get(0).getStatus());
        assertEquals("create_product", result.getSteps().get(0).getStepKey());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getSteps().get(1).getFailureCode());
        assertTrue(result.getSteps().get(1).getWriteMayHaveOccurred());
    }

    @Test
    void unavailableSharedAccountBlocksContinuationBeforeSessionLogin() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        com.nuono.next.noon.NoonAccountSessionAttentionPort attention =
                mock(com.nuono.next.noon.NoonAccountSessionAttentionPort.class);
        when(attention.blocksProviderCalls()).thenReturn(true);
        AtomicInteger loginCalls = new AtomicInteger();
        RealProductListingNoonWriteAdapter adapter = adapter(binding -> {
            loginCalls.incrementAndGet();
            throw new AssertionError("authorization gate must stop before login");
        });
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(attention));

        ProductListingNoonWriteResult result =
                adapter.continueAfterCreate(writeRequest(), "ZPARENT", "PSKU_CODE_1");

        assertFalse(result.isSuccess());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(Boolean.FALSE, result.getWriteMayHaveOccurred());
        assertEquals(0, loginCalls.get());

        ProductListingNoonWriteStepResult readBack =
                adapter.verifyReadBack(writeRequest(), "ZPARENT", "PSKU_CODE_1", List.of());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, readBack.getFailureCode());
        assertEquals(Boolean.TRUE, readBack.getWriteMayHaveOccurred());
        assertEquals(0, loginCalls.get());
    }

    @Test
    void authFailureDuringReadBackStopsImmediatelyAndPreservesWriteRisk() {
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        when(queue.enqueue(any(NoonAuthWaitRequest.class)))
                .thenReturn(Optional.of(992L));
        AtomicInteger writeCalls = new AtomicInteger();
        AtomicInteger readBackCalls = new AtomicInteger();
        RealProductListingNoonWriteAdapter adapter =
                adapter(binding -> sessionThatFailsOnReadBack(writeCalls, readBackCalls));
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                mock(com.nuono.next.noon.NoonAccountSessionAttentionPort.class)));

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertNull(result.getRecoveryId());
        assertTrue(result.getWriteMayHaveOccurred());
        assertTrue(writeCalls.get() > 1);
        assertEquals(1, readBackCalls.get());
    }

    private RealProductListingNoonWriteAdapter adapter(NoonPullGatewaySessionFactory sessionFactory) {
        return new RealProductListingNoonWriteAdapter(
                new ObjectMapper(),
                new FixedBindingResolver(),
                sessionFactory,
                new ProductListingRealWriteProperties(),
                imageUrl -> new ProductListingImageDownload("image.jpg", "image/jpeg", new byte[] {1})
        );
    }

    private ProductListingNoonWriteRequest writeRequest() {
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

    private static void assertListingWaitRequest(
            NoonAuthWaitRequest request,
            String checkpoint,
            NoonAuthResumePolicy resumePolicy
    ) {
        assertEquals(10002L, request.getOwnerUserId());
        assertEquals("PRJ240053", request.getProjectCode());
        assertEquals("STR245027-NAE", request.getStoreCode());
        assertEquals("AE", request.getSiteCode());
        assertEquals("PRODUCT_LISTING", request.getSourceDomain());
        assertEquals(88003L, request.getSourceTaskId());
        assertEquals(checkpoint, request.getCheckpoint());
        assertEquals(resumePolicy, request.getResumePolicy());
    }

    private NoonPullGatewaySession sessionThatFailsAfterCreate(AtomicInteger writeCalls) {
        ObjectMapper mapper = new ObjectMapper();
        return new NoonPullGatewaySession() {
            @Override
            public JsonNode postJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> extraHeaders
            ) {
                return catalogPreflight(mapper);
            }

            @Override
            public JsonNode postWriteJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> extraHeaders
            ) {
                if (writeCalls.incrementAndGet() == 1) {
                    ObjectNode response = mapper.createObjectNode();
                    ArrayNode products = response.putArray("products");
                    ObjectNode product = products.addObject();
                    product.putObject("parent").put("skuParent", "ZPARENT");
                    product.putArray("children").addObject().put("pskuCode", "PSKU_CODE_1");
                    return response;
                }
                throw authFailure();
            }

            @Override
            public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
                return new byte[0];
            }
        };
    }

    private NoonPullGatewaySession sessionThatFailsOnReadBack(
            AtomicInteger writeCalls,
            AtomicInteger readBackCalls
    ) {
        ObjectMapper mapper = new ObjectMapper();
        return new NoonPullGatewaySession() {
            @Override
            public JsonNode postJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> extraHeaders
            ) {
                if (ProductListingRealWriteProperties.Endpoints.DEFAULT_OFFER_LIST_URL.equals(url)) {
                    return catalogPreflight(mapper);
                }
                readBackCalls.incrementAndGet();
                throw authFailure();
            }

            @Override
            public JsonNode postWriteJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> extraHeaders
            ) {
                if (writeCalls.incrementAndGet() != 1) {
                    return mapper.createObjectNode();
                }
                ObjectNode response = mapper.createObjectNode();
                ObjectNode product = response.putArray("products").addObject();
                product.putObject("parent").put("skuParent", "ZPARENT");
                product.putArray("children").addObject().put("pskuCode", "PSKU_CODE_1");
                return response;
            }

            @Override
            public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
                return new byte[0];
            }
        };
    }

    private ObjectNode catalogPreflight(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.putArray("hits");
        data.put("total", 0);
        return root;
    }

    private NoonProductException authFailure() {
        return new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_AUTH_REQUIRED,
                        false,
                        "auth_required: WHOAMI HTTP 307"
                ),
                null
        );
    }

    private static final class FixedBindingResolver extends NoonPullStoreBindingResolver {
        private FixedBindingResolver() {
            super(null);
        }

        @Override
        public NoonPullStoreBinding resolve(NoonInterfacePullRequest request) {
            return new NoonPullStoreBinding(
                    request.getOwnerUserId(),
                    "PRJ240053",
                    request.getStoreCode(),
                    "AE",
                    "240053",
                    "merchant@example.test",
                    "sid=test"
            );
        }
    }
}
