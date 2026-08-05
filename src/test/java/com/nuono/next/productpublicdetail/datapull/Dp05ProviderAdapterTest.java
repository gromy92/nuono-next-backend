package com.nuono.next.productpublicdetail.datapull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Dp05ProviderAdapterTest {

    @Test
    void frontendOnlyTreatsExplicitNotFoundAsPartnerEligible() {
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(51L, "ZABCDEF51");
        AtomicReference<NoonPublicProductDetailResult> result = new AtomicReference<>();
        NoonPublicProductDetailAdapter delegate = request -> result.get();
        Dp05FrontendDetailProviderAdapter adapter = new Dp05FrontendDetailProviderAdapter(delegate);
        Dp05FetchRequest request = new Dp05FetchRequest(Dp05TestSupport.SCOPE, candidate);

        NoonPublicProductDetailResult notFound = failure(
                candidate,
                ProductPublicDetailSyncStatus.NOT_FOUND,
                "PUBLIC_DETAIL_NOT_FOUND",
                404
        );
        result.set(notFound);
        assertEquals(ProviderOutcomeType.NOT_FOUND, adapter.fetch(request).getType());

        NoonPublicProductDetailResult parseFailure = failure(
                candidate,
                ProductPublicDetailSyncStatus.FAILED,
                "PARSE_FAILED",
                200
        );
        result.set(parseFailure);
        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, adapter.fetch(request).getType());

        NoonPublicProductDetailResult risk = failure(
                candidate,
                ProductPublicDetailSyncStatus.FAILED,
                "RATE_LIMITED",
                429
        );
        risk.setProviderRetryAfter(Duration.ofSeconds(37));
        result.set(risk);
        ProviderOutcome<Dp05ProviderValue> riskOutcome = adapter.fetch(request);
        assertEquals(ProviderOutcomeType.RISK_CONTROL, riskOutcome.getType());
        assertEquals(Duration.ofSeconds(37), riskOutcome.getRetryAfter());

        NoonPublicProductDetailResult serverFailure = failure(
                candidate,
                ProductPublicDetailSyncStatus.FAILED,
                "PROVIDER_UNAVAILABLE",
                503
        );
        result.set(serverFailure);
        assertEquals(ProviderOutcomeType.TRANSIENT, adapter.fetch(request).getType());
    }

    @Test
    void frontendThrown403IsRiskWhile401IsAuthentication() {
        Dp05FetchRequest request = new Dp05FetchRequest(
                Dp05TestSupport.SCOPE,
                Dp05TestSupport.candidate(52L, "ZABCDEF52")
        );
        Dp05FrontendDetailProviderAdapter forbidden = new Dp05FrontendDetailProviderAdapter(
                ignored -> { throw new NoonHttpException(403, "unauthorized", "/frontend"); }
        );
        Dp05FrontendDetailProviderAdapter unauthorized = new Dp05FrontendDetailProviderAdapter(
                ignored -> { throw new NoonHttpException(401, "invalid session", "/frontend"); }
        );

        assertEquals(ProviderOutcomeType.RISK_CONTROL, forbidden.fetch(request).getType());
        assertEquals(ProviderOutcomeType.AUTH_REQUIRED, unauthorized.fetch(request).getType());
    }

    @Test
    void partnerUsesOneExactSearchAndMapsOnlyTheExactHit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(61L, "ZABCDEF61");
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        AtomicReference<Map<String, String>> requestHeaders = new AtomicReference<>();
        JsonNode response = mapper.readTree("{\"data\":{\"total\":1,\"hits\":[{"
                + "\"partner_sku\":\"PSKU-61\","
                + "\"csku_parent\":\"ZABCDEF61\","
                + "\"title\":\"Partner product\","
                + "\"sale_price\":\"22.50\","
                + "\"currency\":\"SAR\"}]}}");
        NoonPullGatewaySession session = session((url, body, headers) -> {
            requestBody.set(body);
            requestHeaders.set(headers);
            return response;
        });
        NoonPartnerDp05DetailProvider adapter = partnerAdapter(mapper, session);

        ProviderOutcome<Dp05ProviderValue> outcome = adapter.fetch(
                new Dp05FetchRequest(Dp05TestSupport.SCOPE, candidate)
        );

        assertEquals(ProviderOutcomeType.SUCCESS, outcome.getType());
        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, outcome.getValue().getDetailResult().getStatus());
        assertEquals("Partner product", outcome.getValue().getDetailResult().getTitleEn());
        assertEquals("PSKU-61", requestBody.get().path("search").asText());
        assertEquals(1, requestBody.get().path("page").asInt());
        assertEquals("PRJ108065", requestHeaders.get().get("X-Project"));
    }

    @Test
    void partnerDistinguishesRiskAuthNotFoundAndIncompleteContainers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProductPublicDetailCandidate candidate = Dp05TestSupport.candidate(71L, "ZABCDEF71");
        Dp05FetchRequest request = new Dp05FetchRequest(Dp05TestSupport.SCOPE, candidate);

        NoonPartnerDp05DetailProvider risk = partnerAdapter(
                mapper,
                session((url, body, headers) -> {
                    throw new NoonHttpException(
                            403,
                            "<title>Access Denied</title> errors.edgesuite.net",
                            "/offer/list/noon"
                    );
                })
        );
        assertEquals(ProviderOutcomeType.RISK_CONTROL, risk.fetch(request).getType());

        NoonPartnerDp05DetailProvider forbidden = partnerAdapter(
                mapper,
                session((url, body, headers) -> {
                    throw new NoonHttpException(403, "project session forbidden", "/offer/list/noon");
                })
        );
        assertEquals(ProviderOutcomeType.RISK_CONTROL, forbidden.fetch(request).getType());

        NoonPartnerDp05DetailProvider auth = partnerAdapter(
                mapper,
                session((url, body, headers) -> {
                    throw new NoonHttpException(401, "invalid session", "/offer/list/noon");
                })
        );
        assertEquals(ProviderOutcomeType.AUTH_REQUIRED, auth.fetch(request).getType());

        NoonPartnerDp05DetailProvider notFound = partnerAdapter(
                mapper,
                session((url, body, headers) -> mapper.readTree("{\"data\":{\"total\":0,\"hits\":[]}}"))
        );
        assertEquals(ProviderOutcomeType.NOT_FOUND, notFound.fetch(request).getType());

        NoonPartnerDp05DetailProvider incomplete = partnerAdapter(
                mapper,
                session((url, body, headers) -> mapper.readTree("{\"data\":{\"total\":2,\"hits\":[]}}"))
        );
        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, incomplete.fetch(request).getType());
    }

    private NoonPartnerDp05DetailProvider partnerAdapter(
            ObjectMapper mapper,
            NoonPullGatewaySession session
    ) {
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                307L,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "108065",
                "user@example.test",
                "project-user@example.test",
                "persisted-cookie"
        );
        when(resolver.resolve(any(NoonInterfacePullRequest.class))).thenReturn(binding);
        return new NoonPartnerDp05DetailProvider(mapper, resolver, actual -> {
            assertSame(binding, actual);
            return session;
        });
    }

    private NoonPullGatewaySession session(Call call) {
        return new NoonPullGatewaySession() {
            @Override
            public JsonNode postJson(
                    String url,
                    JsonNode body,
                    boolean withProject,
                    Map<String, String> headers
            ) {
                try {
                    return call.invoke(url, body, headers);
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }

            @Override
            public byte[] getBytes(String url, boolean withProject, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private NoonPublicProductDetailResult failure(
            ProductPublicDetailCandidate candidate,
            ProductPublicDetailSyncStatus status,
            String code,
            int httpStatus
    ) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(status);
        result.setNoonProductCode(candidate.getNoonProductCode());
        result.setFailureCode(code);
        result.setProviderHttpStatus(httpStatus);
        return result;
    }

    @FunctionalInterface
    private interface Call {
        JsonNode invoke(String url, JsonNode body, Map<String, String> headers) throws Exception;
    }
}
