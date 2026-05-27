package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("local-db")
@ConditionalOnProperty(prefix = "nuono.product-selection.ali1688.image-search", name = "enabled", havingValue = "true")
public class HttpAli1688ImageSearchGateway implements Ali1688ImageSearchGateway {

    private final Ali1688ImageSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public HttpAli1688ImageSearchGateway(
            Ali1688ImageSearchProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this(
                properties,
                objectMapper,
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .build()
        );
    }

    HttpAli1688ImageSearchGateway(
            Ali1688ImageSearchProperties properties,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public Ali1688ImageSearchResult search(Ali1688ImageSearchRequest request) {
        if (!StringUtils.hasText(properties.getEndpointUrl())) {
            throw new Ali1688GatewayException(
                    "gateway_disabled",
                    "1688 图搜 HTTP gateway 已启用，但未配置 endpointUrl。",
                    false,
                    null,
                    null,
                    null
            );
        }
        if (request == null || request.sourceCollectionId == null || request.taskId == null) {
            throw new Ali1688GatewayException(
                    "unexpected_response",
                    "1688 图搜必须提供任务和源头采集上下文。",
                    false,
                    null,
                    null,
                    null
            );
        }
        if (!StringUtils.hasText(request.sourceImageUrl)) {
            throw new Ali1688GatewayException(
                    "source_image_missing",
                    "源头商品缺少可用于 1688 图搜的图片。",
                    false,
                    null,
                    null,
                    null
            );
        }

        try {
            ResponseEntity<Ali1688GatewayResponse> response = restTemplate.exchange(
                    properties.getEndpointUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(buildRequest(request), buildJsonHeaders()),
                    Ali1688GatewayResponse.class
            );
            return toResult(response.getBody());
        } catch (HttpStatusCodeException exception) {
            Ali1688GatewayResponse errorResponse = readGatewayResponse(exception.getResponseBodyAsString());
            if (errorResponse != null) {
                return toResult(errorResponse);
            }
            throw httpStatusException(exception);
        } catch (RestClientException exception) {
            throw new Ali1688GatewayException(
                    "gateway_timeout",
                    "1688 图搜 HTTP gateway 调用失败：" + exception.getMessage(),
                    true,
                    null,
                    null,
                    null
            );
        }
    }

    @Override
    public Ali1688GatewayOperationalStatus getOperationalStatus() {
        if (!StringUtils.hasText(properties.getEndpointUrl())) {
            return Ali1688GatewayOperationalStatus.unavailable(
                    "system_browser_gateway",
                    "gateway_disabled",
                    false,
                    false
            );
        }
        try {
            ResponseEntity<Ali1688GatewayHealthResponse> response = restTemplate.exchange(
                    healthEndpointUrl(),
                    HttpMethod.GET,
                    new HttpEntity<>(buildJsonHeaders()),
                    Ali1688GatewayHealthResponse.class
            );
            Ali1688GatewayHealthResponse body = response.getBody();
            if (body == null) {
                return Ali1688GatewayOperationalStatus.unavailable(
                        "system_browser_gateway",
                        "unexpected_response",
                        false,
                        false
                );
            }
            return Ali1688GatewayOperationalStatus.from(
                    defaultText(body.gatewayServiceKind, "system_browser_gateway"),
                    defaultText(body.sessionState, "unexpected_response"),
                    body.runtimeReady,
                    body.captchaAutoSolveEnabled
            );
        } catch (RestClientException exception) {
            return Ali1688GatewayOperationalStatus.unavailable(
                    "system_browser_gateway",
                    "unexpected_response",
                    false,
                    false
            );
        }
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getAuthToken())) {
            headers.set(defaultText(properties.getAuthHeaderName(), "Authorization"), authHeaderValue());
        }
        return headers;
    }

    private String healthEndpointUrl() {
        String endpoint = properties.getEndpointUrl().trim();
        String suffix = "/ali1688/image-search";
        int suffixIndex = endpoint.indexOf(suffix);
        if (suffixIndex >= 0) {
            return endpoint.substring(0, suffixIndex) + "/health";
        }
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/health";
    }

    private Ali1688GatewayResponse readGatewayResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, Ali1688GatewayResponse.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Ali1688GatewayException httpStatusException(HttpStatusCodeException exception) {
        int status = exception.getRawStatusCode();
        if (status == 401 || status == 403) {
            return new Ali1688GatewayException("login_required", null, false, exception.getResponseBodyAsString(), null, null);
        }
        if (status == 408 || status == 504) {
            return new Ali1688GatewayException("gateway_timeout", null, true, exception.getResponseBodyAsString(), null, null);
        }
        if (status == 429) {
            return new Ali1688GatewayException("rate_limited", null, true, exception.getResponseBodyAsString(), null, null);
        }
        return new Ali1688GatewayException("unexpected_response", exception.getMessage(), false, exception.getResponseBodyAsString(), null, null);
    }

    private Map<String, Object> buildRequest(Ali1688ImageSearchRequest sourceRequest) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskId", sourceRequest.taskId);
        request.put("sourceCollectionId", sourceRequest.sourceCollectionId);
        request.put("requestId", sourceRequest.requestId);
        request.put("attemptCount", sourceRequest.attemptCount);
        request.put("lockToken", sourceRequest.lockToken);
        request.put("collectionNo", sourceRequest.collectionNo);
        request.put("ownerUserId", sourceRequest.ownerUserId);
        request.put("logicalStoreId", sourceRequest.logicalStoreId);
        request.put("storeCode", sourceRequest.storeCode);
        request.put("sourceType", sourceRequest.sourceType);
        request.put("sourcePlatform", sourceRequest.sourcePlatform);
        request.put("sourceUrl", sourceRequest.sourceUrl);
        request.put("pageUrl", sourceRequest.pageUrl);
        request.put("sourceTitle", sourceRequest.sourceTitle);
        request.put("sourceTitleCn", sourceRequest.sourceTitleCn);
        request.put("sourceImageUrl", sourceRequest.sourceImageUrl);
        request.put("imageUrls", sourceRequest.imageUrls == null ? List.of() : sourceRequest.imageUrls);
        request.put("priceSummary", sourceRequest.priceSummary);
        request.put("moqHint", sourceRequest.moqHint);
        request.put("shippingFrom", sourceRequest.shippingFrom);
        request.put("brandName", sourceRequest.brandName);
        request.put("unitCount", sourceRequest.unitCount);
        request.put("colorName", sourceRequest.colorName);
        request.put("specHints", sourceRequest.specHints == null ? List.of() : sourceRequest.specHints);
        request.put("sourceDescriptionEn", sourceRequest.sourceDescriptionEn);
        request.put("selectedText", sourceRequest.selectedText);
        request.put("maxCandidates", Math.max(1, Math.min(
                sourceRequest.maxCandidates == null ? properties.getMaxCandidates() : sourceRequest.maxCandidates,
                10
        )));
        return request;
    }

    private Ali1688ImageSearchResult toResult(Ali1688GatewayResponse response) {
        if (response == null) {
            throw new Ali1688GatewayException(
                    "unexpected_response",
                    "1688 图搜 HTTP gateway 返回空响应。",
                    false,
                    null,
                    null,
                    null
            );
        }
        if (Boolean.FALSE.equals(response.success) || StringUtils.hasText(response.errorCode)) {
            throw new Ali1688GatewayException(
                    response.errorCode,
                    response.message,
                    Boolean.TRUE.equals(response.retryable),
                    response.rawSnapshotJson,
                    response.officialSearchUrl,
                    response.providerTraceId
            );
        }
        Ali1688ImageSearchResult result = new Ali1688ImageSearchResult();
        result.searchMode = defaultText(response.searchMode, "主图图搜");
        result.officialSearchUrl = response.officialSearchUrl;
        result.searchImageId = response.searchImageId;
        result.searchImageIds = response.searchImageIds == null ? new ArrayList<>() : new ArrayList<>(response.searchImageIds);
        result.rawSnapshotJson = StringUtils.hasText(response.rawSnapshotJson) ? response.rawSnapshotJson : writeJson(response);

        List<Ali1688GatewayCandidate> candidates = response.candidates == null ? List.of() : response.candidates;
        int limit = Math.max(1, Math.min(properties.getMaxCandidates(), 10));
        for (Ali1688GatewayCandidate item : candidates) {
            if (item == null || result.candidates.size() >= limit) {
                break;
            }
            Ali1688ImageSearchResult.Candidate candidate = new Ali1688ImageSearchResult.Candidate();
            candidate.offerId = trim(item.offerId);
            candidate.candidateUrl = trim(item.candidateUrl);
            candidate.title = trim(item.title);
            candidate.supplierName = trim(item.supplierName);
            candidate.priceText = trim(item.priceText);
            candidate.priceMin = item.priceMin;
            candidate.priceMax = item.priceMax;
            candidate.moqText = trim(item.moqText);
            candidate.moqValue = item.moqValue;
            candidate.locationText = trim(item.locationText);
            candidate.mainImageUrl = trim(item.mainImageUrl);
            candidate.imageUrls = item.imageUrls == null ? new ArrayList<>() : new ArrayList<>(item.imageUrls);
            candidate.badges = copyMap(item.badges);
            candidate.skuSnapshot = copyMap(item.skuSnapshot);
            candidate.supplierSnapshot = copyMap(item.supplierSnapshot);
            candidate.logisticsSnapshot = copyMap(item.logisticsSnapshot);
            result.candidates.add(candidate);
        }
        return result;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String authHeaderValue() {
        String token = properties.getAuthToken().trim();
        if (!"Authorization".equalsIgnoreCase(defaultText(properties.getAuthHeaderName(), ""))) {
            return token;
        }
        return token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length()) ? token : "Bearer " + token;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    static class Ali1688GatewayResponse {
        public Boolean success;
        public String errorCode;
        public String message;
        public Boolean retryable;
        public String searchMode;
        public String officialSearchUrl;
        public String searchImageId;
        public List<String> searchImageIds = new ArrayList<>();
        public String rawSnapshotJson;
        public String providerTraceId;
        public List<Ali1688GatewayCandidate> candidates = new ArrayList<>();
    }

    static class Ali1688GatewayHealthResponse {
        public String gatewayServiceKind;
        public String sessionState;
        public Boolean runtimeReady;
        public Boolean captchaAutoSolveEnabled;
    }

    static class Ali1688GatewayCandidate {
        public String offerId;
        public String candidateUrl;
        public String title;
        public String supplierName;
        public String priceText;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String moqText;
        public Integer moqValue;
        public String locationText;
        public String mainImageUrl;
        public List<String> imageUrls = new ArrayList<>();
        public Map<String, Object> badges;
        public Map<String, Object> skuSnapshot;
        public Map<String, Object> supplierSnapshot;
        public Map<String, Object> logisticsSnapshot;
    }
}
