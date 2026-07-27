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

class RealProductListingNoonWriteAuthRecoveryTest {

    @Test
    void newAuthFailureAfterCreateQueuesRecoveryAndPreservesWriteRisk() {
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        when(queue.enqueueProject(10002L, "PRJ240053", "STR245027-NAE"))
                .thenReturn(Optional.of(991L));
        AtomicInteger writeCalls = new AtomicInteger();
        NoonPullGatewaySession session = sessionThatFailsAfterCreate(writeCalls);
        RealProductListingNoonWriteAdapter adapter = adapter(binding -> session);
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(queue, gate));

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertFalse(result.isSuccess());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(991L, result.getRecoveryId());
        assertTrue(result.getWriteMayHaveOccurred());
        assertEquals(2, writeCalls.get());
        assertEquals("succeeded", result.getSteps().get(0).getStatus());
        assertEquals("create_product", result.getSteps().get(0).getStepKey());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getSteps().get(1).getFailureCode());
        assertTrue(result.getSteps().get(1).getWriteMayHaveOccurred());
        verify(queue).enqueueProject(10002L, "PRJ240053", "STR245027-NAE");
    }

    @Test
    void pendingRecoveryBlocksManualContinuationBeforeSessionLogin() {
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        when(gate.isBlocked(10002L, "PRJ240053")).thenReturn(true);
        AtomicInteger loginCalls = new AtomicInteger();
        RealProductListingNoonWriteAdapter adapter = adapter(binding -> {
            loginCalls.incrementAndGet();
            throw new AssertionError("authorization gate must stop before login");
        });
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(queue, gate));

        ProductListingNoonWriteResult result =
                adapter.continueAfterCreate(writeRequest(), "ZPARENT", "PSKU_CODE_1");

        assertFalse(result.isSuccess());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(Boolean.FALSE, result.getWriteMayHaveOccurred());
        assertEquals(0, loginCalls.get());
        verify(queue, never()).enqueueProject(10002L, "PRJ240053", "STR245027-NAE");

        ProductListingNoonWriteStepResult readBack =
                adapter.verifyReadBack(writeRequest(), "ZPARENT", "PSKU_CODE_1", List.of());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, readBack.getFailureCode());
        assertEquals(Boolean.TRUE, readBack.getWriteMayHaveOccurred());
        assertEquals(0, loginCalls.get());
    }

    @Test
    void authFailureDuringReadBackStopsImmediatelyAndPreservesWriteRisk() {
        NoonProjectAuthRecoveryQueue queue = mock(NoonProjectAuthRecoveryQueue.class);
        NoonPullProjectAuthGate gate = mock(NoonPullProjectAuthGate.class);
        when(queue.enqueueProject(10002L, "PRJ240053", "STR245027-NAE"))
                .thenReturn(Optional.of(992L));
        AtomicInteger writeCalls = new AtomicInteger();
        AtomicInteger readBackCalls = new AtomicInteger();
        RealProductListingNoonWriteAdapter adapter =
                adapter(binding -> sessionThatFailsOnReadBack(writeCalls, readBackCalls));
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(queue, gate));

        ProductListingNoonWriteResult result = adapter.execute(writeRequest());

        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, result.getFailureCode());
        assertEquals(992L, result.getRecoveryId());
        assertTrue(result.getWriteMayHaveOccurred());
        assertTrue(writeCalls.get() > 1);
        assertEquals(1, readBackCalls.get());
        verify(queue).enqueueProject(10002L, "PRJ240053", "STR245027-NAE");
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
                    "secret",
                    null,
                    "sid=test"
            );
        }
    }
}
