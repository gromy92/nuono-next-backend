package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
    public Ali1688ImageSearchResult search(ProductSelectionSourceCollectionRow sourceCollection) {
        if (!StringUtils.hasText(properties.getEndpointUrl())) {
            throw new IllegalStateException("1688 图搜 HTTP gateway 已启用，但未配置 endpointUrl。");
        }
        if (sourceCollection == null || sourceCollection.getId() == null) {
            throw new IllegalArgumentException("1688 图搜必须提供源头采集记录。");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getAuthToken())) {
            headers.set(defaultText(properties.getAuthHeaderName(), "Authorization"), authHeaderValue());
        }

        try {
            ResponseEntity<Ali1688GatewayResponse> response = restTemplate.exchange(
                    properties.getEndpointUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(buildRequest(sourceCollection), headers),
                    Ali1688GatewayResponse.class
            );
            return toResult(response.getBody());
        } catch (RestClientException exception) {
            throw new IllegalStateException("1688 图搜 HTTP gateway 调用失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildRequest(ProductSelectionSourceCollectionRow sourceCollection) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceCollectionId", sourceCollection.getId());
        request.put("collectionNo", sourceCollection.getCollectionNo());
        request.put("ownerUserId", sourceCollection.getOwnerUserId());
        request.put("logicalStoreId", sourceCollection.getLogicalStoreId());
        request.put("storeCode", sourceCollection.getStoreCode());
        request.put("sourceType", sourceCollection.getSourceType());
        request.put("sourcePlatform", sourceCollection.getSourcePlatform());
        request.put("sourceUrl", sourceCollection.getSourceUrl());
        request.put("pageUrl", sourceCollection.getPageUrl());
        request.put("sourceTitle", sourceCollection.getSourceTitle());
        request.put("sourceTitleCn", sourceCollection.getSourceTitleCn());
        request.put("sourceImageUrl", sourceCollection.getSourceImageUrl());
        request.put("imageUrls", readStringListJson(sourceCollection.getImageUrlsJson()));
        request.put("priceSummary", sourceCollection.getPriceSummary());
        request.put("moqHint", sourceCollection.getMoqHint());
        request.put("shippingFrom", sourceCollection.getShippingFrom());
        request.put("brandName", sourceCollection.getBrandName());
        request.put("unitCount", sourceCollection.getUnitCount());
        request.put("colorName", sourceCollection.getColorName());
        request.put("specHints", readStringListJson(sourceCollection.getSpecHintsJson()));
        request.put("sourceDescriptionEn", sourceCollection.getSourceDescriptionEn());
        request.put("selectedText", sourceCollection.getSelectedText());
        request.put("maxCandidates", Math.max(1, Math.min(properties.getMaxCandidates(), 10)));
        return request;
    }

    private Ali1688ImageSearchResult toResult(Ali1688GatewayResponse response) {
        if (response == null) {
            throw new IllegalStateException("1688 图搜 HTTP gateway 返回空响应。");
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

    private List<String> readStringListJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
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
        public String searchMode;
        public String officialSearchUrl;
        public String searchImageId;
        public List<String> searchImageIds = new ArrayList<>();
        public String rawSnapshotJson;
        public List<Ali1688GatewayCandidate> candidates = new ArrayList<>();
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
