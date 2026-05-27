package com.nuono.next.productselection;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAli1688ImageSearchGatewayTest {

    @Test
    void postsSystemGatewayRequestAndMapsGatewayResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688ImageSearchProperties properties = new Ali1688ImageSearchProperties();
        properties.setEndpointUrl("http://gateway.test/ali1688/image-search");
        properties.setAuthToken("token-123");
        properties.setMaxCandidates(10);
        HttpAli1688ImageSearchGateway gateway = new HttpAli1688ImageSearchGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo("http://gateway.test/ali1688/image-search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andExpect(content().string(containsString("\"taskId\":87001")))
                .andExpect(content().string(containsString("\"sourceCollectionId\":86001")))
                .andExpect(content().string(containsString("\"requestId\":\"ali1688-task-87001-attempt-2-ali1688\"")))
                .andExpect(content().string(containsString("\"attemptCount\":2")))
                .andExpect(content().string(containsString("\"lockToken\":\"ali1688-collection-scheduler-test\"")))
                .andExpect(content().string(containsString("\"sourceImageUrl\":\"https://images.example.com/source.jpg\"")))
                .andExpect(content().string(containsString("\"sourceTitle\":\"Artificial Flowers 6 Stems\"")))
                .andExpect(content().string(containsString("\"pageUrl\":\"https://www.amazon.com/dp/example\"")))
                .andExpect(content().string(containsString("\"maxCandidates\":10")))
                .andRespond(withSuccess(
                        "{"
                                + "\"searchMode\":\"主图图搜\","
                                + "\"officialSearchUrl\":\"https://s.1688.com/image/example\","
                                + "\"searchImageId\":\"img-001\","
                                + "\"searchImageIds\":[\"img-001\"],"
                                + "\"candidates\":[{"
                                + "\"offerId\":\"745612345678\","
                                + "\"candidateUrl\":\"https://detail.1688.com/offer/745612345678.html\","
                                + "\"title\":\"仿真花束 6 支装\","
                                + "\"supplierName\":\"义乌诚信通源头工厂\","
                                + "\"priceText\":\"¥12.80-18.60\","
                                + "\"priceMin\":12.80,"
                                + "\"priceMax\":18.60,"
                                + "\"moqText\":\"2 件起批\","
                                + "\"moqValue\":2,"
                                + "\"locationText\":\"浙江 义乌\","
                                + "\"mainImageUrl\":\"https://images.example.com/ali-main.jpg\","
                                + "\"imageUrls\":[\"https://images.example.com/ali-main.jpg\"],"
                                + "\"badges\":{\"stock\":\"现货\"},"
                                + "\"supplierSnapshot\":{\"verified\":true},"
                                + "\"logisticsSnapshot\":{\"shipFrom\":\"义乌\"}"
                                + "}]"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688ImageSearchResult result = gateway.search(request());

        server.verify();
        assertEquals("主图图搜", result.searchMode);
        assertEquals("https://s.1688.com/image/example", result.officialSearchUrl);
        assertEquals("img-001", result.searchImageId);
        assertEquals(1, result.candidates.size());
        Ali1688ImageSearchResult.Candidate candidate = result.candidates.get(0);
        assertEquals("745612345678", candidate.offerId);
        assertEquals("仿真花束 6 支装", candidate.title);
        assertEquals(new BigDecimal("12.80"), candidate.priceMin);
        assertEquals(2, candidate.moqValue);
        assertEquals("现货", candidate.badges.get("stock"));
        assertNotNull(result.rawSnapshotJson);
        assertTrue(result.rawSnapshotJson.contains("745612345678"));
    }

    @Test
    void throwsTypedGatewayExceptionWhenGatewayReturnsTypedError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688ImageSearchProperties properties = new Ali1688ImageSearchProperties();
        properties.setEndpointUrl("http://gateway.test/ali1688/image-search");
        HttpAli1688ImageSearchGateway gateway = new HttpAli1688ImageSearchGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo("http://gateway.test/ali1688/image-search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{"
                                + "\"success\":false,"
                                + "\"errorCode\":\"captcha_required\","
                                + "\"message\":\"1688 图搜出现验证码，需要人工处理。\","
                                + "\"retryable\":false,"
                                + "\"officialSearchUrl\":\"https://s.1688.com/image/captcha\","
                                + "\"providerTraceId\":\"trace-001\","
                                + "\"rawSnapshotJson\":\"{\\\"blocked\\\":true}\""
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688GatewayException exception = assertThrows(Ali1688GatewayException.class, () -> gateway.search(request()));

        server.verify();
        assertEquals("captcha_required", exception.getErrorCode());
        assertEquals("1688 图搜出现验证码，需要人工处理。", exception.getGatewayMessage());
        assertEquals(false, exception.isRetryable());
        assertEquals("https://s.1688.com/image/captcha", exception.getOfficialSearchUrl());
        assertEquals("trace-001", exception.getProviderTraceId());
        assertEquals("{\"blocked\":true}", exception.getRawSnapshotJson());
    }

    @Test
    void preservesBrowserGatewayTypedErrorWhenHttpStatusIsNotSuccessful() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688ImageSearchProperties properties = new Ali1688ImageSearchProperties();
        properties.setEndpointUrl("http://browser-gateway.test/ali1688/image-search");
        HttpAli1688ImageSearchGateway gateway = new HttpAli1688ImageSearchGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo("http://browser-gateway.test/ali1688/image-search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{"
                                + "\"success\":false,"
                                + "\"errorCode\":\"rate_limited\","
                                + "\"message\":\"1688 图搜触发限流，请稍后重试。\","
                                + "\"retryable\":true,"
                                + "\"officialSearchUrl\":\"https://s.1688.com/image/rate-limited\","
                                + "\"providerTraceId\":\"browser-trace-429\","
                                + "\"rawSnapshotJson\":\"{\\\"status\\\":429,\\\"source\\\":\\\"browser-gateway\\\"}\""
                                + "}"));

        Ali1688GatewayException exception = assertThrows(Ali1688GatewayException.class, () -> gateway.search(request()));

        server.verify();
        assertEquals("rate_limited", exception.getErrorCode());
        assertEquals("1688 图搜触发限流，请稍后重试。", exception.getGatewayMessage());
        assertTrue(exception.isRetryable());
        assertEquals("https://s.1688.com/image/rate-limited", exception.getOfficialSearchUrl());
        assertEquals("browser-trace-429", exception.getProviderTraceId());
        assertEquals("{\"status\":429,\"source\":\"browser-gateway\"}", exception.getRawSnapshotJson());
    }

    @Test
    void mapsRequiredBrowserGatewayTypedErrorCodes() {
        assertBrowserGatewayTypedError("login_required", HttpStatus.UNAUTHORIZED, false);
        assertBrowserGatewayTypedError("captcha_required", HttpStatus.OK, false);
        assertBrowserGatewayTypedError("rate_limited", HttpStatus.TOO_MANY_REQUESTS, true);
        assertBrowserGatewayTypedError("gateway_timeout", HttpStatus.GATEWAY_TIMEOUT, true);
        assertBrowserGatewayTypedError("no_candidates", HttpStatus.OK, false);
        assertBrowserGatewayTypedError("unexpected_response", HttpStatus.BAD_GATEWAY, false);
    }

    @Test
    void readsSystemGatewayHealthFromSiblingHealthEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688ImageSearchProperties properties = new Ali1688ImageSearchProperties();
        properties.setEndpointUrl("http://browser-gateway.test/ai/ali1688-gateway/ali1688/image-search");
        properties.setAuthToken("token-123");
        HttpAli1688ImageSearchGateway gateway = new HttpAli1688ImageSearchGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo("http://browser-gateway.test/ai/ali1688-gateway/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andRespond(withSuccess(
                        "{"
                                + "\"gatewayServiceKind\":\"system_browser_gateway\","
                                + "\"sessionState\":\"captcha_required\","
                                + "\"runtimeReady\":true,"
                                + "\"captchaAutoSolveEnabled\":false"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688GatewayOperationalStatus status = gateway.getOperationalStatus();

        server.verify();
        assertEquals("system_browser_gateway", status.gatewayServiceKind);
        assertEquals("captcha_required", status.sessionState);
        assertEquals(true, status.runtimeReady);
        assertEquals(false, status.captchaAutoSolveEnabled);
        assertEquals("blocked_by_captcha", status.userFacingStatus);
    }

    private Ali1688ImageSearchRequest request() {
        return Ali1688ImageSearchRequest.fromTask("ali1688-collection-scheduler-test", task(), sourceCollection(), 10);
    }

    private void assertBrowserGatewayTypedError(String errorCode, HttpStatus status, boolean retryable) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688ImageSearchProperties properties = new Ali1688ImageSearchProperties();
        properties.setEndpointUrl("http://browser-gateway.test/ali1688/image-search");
        HttpAli1688ImageSearchGateway gateway = new HttpAli1688ImageSearchGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        String traceId = "browser-trace-" + errorCode;
        server.expect(requestTo("http://browser-gateway.test/ali1688/image-search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{"
                                + "\"success\":false,"
                                + "\"errorCode\":\"" + errorCode + "\","
                                + "\"message\":\"browser gateway " + errorCode + "\","
                                + "\"retryable\":" + retryable + ","
                                + "\"officialSearchUrl\":\"https://s.1688.com/image/" + errorCode + "\","
                                + "\"providerTraceId\":\"" + traceId + "\","
                                + "\"rawSnapshotJson\":\"{\\\"errorCode\\\":\\\"" + errorCode + "\\\",\\\"source\\\":\\\"browser-gateway\\\"}\""
                                + "}"));

        Ali1688GatewayException exception = assertThrows(Ali1688GatewayException.class, () -> gateway.search(request()));

        server.verify();
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals("browser gateway " + errorCode, exception.getGatewayMessage());
        assertEquals(retryable, exception.isRetryable());
        assertEquals("https://s.1688.com/image/" + errorCode, exception.getOfficialSearchUrl());
        assertEquals(traceId, exception.getProviderTraceId());
        assertEquals("{\"errorCode\":\"" + errorCode + "\",\"source\":\"browser-gateway\"}", exception.getRawSnapshotJson());
    }

    private Ali1688CollectionRecords.TaskRecord task() {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.sourceCollectionId = 86001L;
        task.ownerUserId = 307L;
        task.logicalStoreId = 301L;
        task.lockedBy = "ali1688-collection-scheduler-test";
        task.attemptCount = 2;
        return task;
    }

    private ProductSelectionSourceCollectionRow sourceCollection() {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(86001L);
        row.setCollectionNo("PSC-20260519-001");
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(301L);
        row.setStoreCode("STR108065-NAE");
        row.setSourceType("marketplace-url");
        row.setSourcePlatform("Amazon");
        row.setSourceUrl("https://www.amazon.com/dp/example");
        row.setPageUrl("https://www.amazon.com/dp/example");
        row.setSourceTitle("Artificial Flowers 6 Stems");
        row.setSourceTitleCn("仿真花束");
        row.setSourceImageUrl("https://images.example.com/source.jpg");
        row.setImageUrlsJson("[\"https://images.example.com/source.jpg\"]");
        row.setPriceSummary("12.99");
        row.setMoqHint("1 pcs min");
        row.setShippingFrom("Amazon");
        row.setSpecHintsJson("[\"Brand: DUYONE\", \"Material: Silk\"]");
        row.setSelectedText("仿真花束");
        return row;
    }
}
