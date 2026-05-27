package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAli1688OfferDetailGatewayTest {

    @Test
    void disabledByDefaultDoesNotAttemptDetailEnrichment() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688OfferDetailProperties properties = new Ali1688OfferDetailProperties();
        HttpAli1688OfferDetailGateway gateway = new HttpAli1688OfferDetailGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        Ali1688OfferDetailCompletionResult result = gateway.enrich(List.of(candidate()));

        server.verify();
        assertEquals("not_attempted", result.getOutcome());
        assertEquals(0, result.getAttemptCount());
        assertFalse(result.isAttempted());
    }

    @Test
    void primarySuccessEnrichesOnlyMissingCandidateFields() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688OfferDetailProperties properties = enabledProperties();
        properties.setPrimaryEndpointUrl("http://detail.test/offer/{offerId}");
        HttpAli1688OfferDetailGateway gateway = new HttpAli1688OfferDetailGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );
        Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate = candidate();
        candidate.title = "插件可信标题";
        candidate.priceText = null;
        candidate.priceMin = null;
        candidate.moqText = null;
        candidate.moqValue = null;
        candidate.locationText = null;
        candidate.supplierName = "插件供应商";

        server.expect(requestTo("http://detail.test/offer/745612345001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{"
                                + "\"success\":true,"
                                + "\"data\":{"
                                + "\"title\":\"详情页标题不应覆盖插件标题\","
                                + "\"supplierName\":\"详情页供应商不应覆盖插件供应商\","
                                + "\"priceText\":\"¥18.80\","
                                + "\"priceMin\":18.80,"
                                + "\"priceMax\":22.50,"
                                + "\"moqText\":\"2 件起批\","
                                + "\"moqValue\":2,"
                                + "\"locationText\":\"浙江 温州\","
                                + "\"mainImageUrl\":\"https://images.example.com/detail-main.jpg\","
                                + "\"imageUrls\":[\"https://images.example.com/detail-main.jpg\"],"
                                + "\"supplierSnapshot\":{\"detailVerified\":true},"
                                + "\"logisticsSnapshot\":{\"detailShipFrom\":\"浙江温州\"}"
                                + "}"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688OfferDetailCompletionResult result = gateway.enrich(List.of(candidate));

        server.verify();
        assertEquals("completed", result.getOutcome());
        assertEquals(1, result.getAttemptCount());
        assertEquals(1, result.getEnrichedCount());
        assertEquals("插件可信标题", candidate.title);
        assertEquals("插件供应商", candidate.supplierName);
        assertEquals("¥18.80", candidate.priceText);
        assertEquals(0, new BigDecimal("18.80").compareTo(candidate.priceMin));
        assertEquals(0, new BigDecimal("22.50").compareTo(candidate.priceMax));
        assertEquals("2 件起批", candidate.moqText);
        assertEquals(2, candidate.moqValue);
        assertEquals("浙江 温州", candidate.locationText);
        assertEquals("https://images.example.com/745612345001.jpg", candidate.mainImageUrl);
        assertEquals(true, candidate.supplierSnapshot.get("factory"));
        assertEquals(true, candidate.supplierSnapshot.get("detailVerified"));
        assertEquals("浙江义乌", candidate.logisticsSnapshot.get("shipFrom"));
        assertEquals("浙江温州", candidate.logisticsSnapshot.get("detailShipFrom"));
    }

    @Test
    void primaryFailureThenFallbackSuccessEnrichesCandidateFields() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688OfferDetailProperties properties = enabledProperties();
        properties.setPrimaryEndpointUrl("http://primary-detail.test/offer/{offerId}");
        properties.setFallbackEndpointUrl("http://fallback-detail.test/detail?url={candidateUrl}");
        HttpAli1688OfferDetailGateway gateway = new HttpAli1688OfferDetailGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );
        Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate = candidate();
        candidate.priceText = null;
        candidate.moqText = null;
        candidate.moqValue = null;

        server.expect(requestTo("http://primary-detail.test/offer/745612345001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(request -> {
                    String url = request.getURI().toString();
                    assertTrue(url.equals("http://fallback-detail.test/detail?url=https%3A%2F%2Fdetail.1688.com%2Foffer%2F745612345001.html")
                            || url.equals("http://fallback-detail.test/detail?url=https://detail.1688.com/offer/745612345001.html"));
                    assertFalse(url.contains("%253A"));
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{"
                                + "\"priceText\":\"¥21.60\","
                                + "\"moqText\":\"5 件起批\","
                                + "\"moqValue\":5"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688OfferDetailCompletionResult result = gateway.enrich(List.of(candidate));

        server.verify();
        assertEquals("completed", result.getOutcome());
        assertEquals(2, result.getAttemptCount());
        assertEquals(1, result.getEnrichedCount());
        assertEquals("¥21.60", candidate.priceText);
        assertEquals("5 件起批", candidate.moqText);
        assertEquals(5, candidate.moqValue);
    }

    @Test
    void bothServicesFailRecordsFailureWithoutMutatingCandidate() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688OfferDetailProperties properties = enabledProperties();
        properties.setPrimaryEndpointUrl("http://primary-detail.test/offer/{offerId}");
        properties.setFallbackEndpointUrl("http://fallback-detail.test/offer/{offerId}");
        HttpAli1688OfferDetailGateway gateway = new HttpAli1688OfferDetailGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );
        Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate = candidate();
        candidate.priceText = null;

        server.expect(requestTo("http://primary-detail.test/offer/745612345001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo("http://fallback-detail.test/offer/745612345001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Ali1688OfferDetailCompletionResult result = gateway.enrich(List.of(candidate));

        server.verify();
        assertEquals("failed", result.getOutcome());
        assertEquals(2, result.getAttemptCount());
        assertEquals(0, result.getEnrichedCount());
        assertEquals(1, result.getFailedCount());
        assertTrue(result.getMessage().contains("failed"));
        assertEquals(null, candidate.priceText);
    }

    private Ali1688OfferDetailProperties enabledProperties() {
        Ali1688OfferDetailProperties properties = new Ali1688OfferDetailProperties();
        properties.setEnabled(true);
        properties.setTimeoutSeconds(3);
        return properties;
    }

    private Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate() {
        Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate =
                new Ali1688PluginSubmissionNormalizer.NormalizedCandidate();
        candidate.offerId = "745612345001";
        candidate.candidateUrl = "https://detail.1688.com/offer/745612345001.html";
        candidate.title = "仿真花束";
        candidate.supplierName = "义乌诚信通源头工厂";
        candidate.priceText = "¥11.00";
        candidate.priceMin = new BigDecimal("11.00");
        candidate.moqText = "1 件起批";
        candidate.moqValue = 1;
        candidate.locationText = "浙江 义乌";
        candidate.mainImageUrl = "https://images.example.com/745612345001.jpg";
        candidate.imageUrls = List.of(candidate.mainImageUrl);
        candidate.supplierSnapshot = Map.of("factory", true);
        candidate.logisticsSnapshot = Map.of("shipFrom", "浙江义乌");
        return candidate;
    }
}
