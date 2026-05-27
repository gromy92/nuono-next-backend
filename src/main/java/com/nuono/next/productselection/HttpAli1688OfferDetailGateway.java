package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Profile("local-db")
@ConditionalOnProperty(prefix = "nuono.product-selection.ali1688.offer-detail", name = "enabled", havingValue = "true")
public class HttpAli1688OfferDetailGateway implements Ali1688OfferDetailGateway {

    private final Ali1688OfferDetailProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public HttpAli1688OfferDetailGateway(
            Ali1688OfferDetailProperties properties,
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

    HttpAli1688OfferDetailGateway(
            Ali1688OfferDetailProperties properties,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public Ali1688OfferDetailCompletionResult enrich(
            List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> candidates
    ) {
        if (!properties.isEnabled()
                || (!StringUtils.hasText(properties.getPrimaryEndpointUrl())
                && !StringUtils.hasText(properties.getFallbackEndpointUrl()))) {
            return Ali1688OfferDetailCompletionResult.notAttempted(
                    "Known-offer detail enrichment is disabled."
            );
        }
        List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> enrichable = enrichableCandidates(candidates);
        if (enrichable.isEmpty()) {
            return Ali1688OfferDetailCompletionResult.notAttempted(
                    "No known 1688 offer candidates need detail enrichment."
            );
        }

        int attemptCount = 0;
        int enrichedCount = 0;
        int failedCount = 0;
        int captchaFailureCount = 0;
        int limit = Math.max(1, Math.min(properties.getMaxCandidates(), 10));
        for (Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate : enrichable) {
            if (attemptCount >= limit * endpointCount()) {
                break;
            }
            FetchResult fetched = null;
            if (StringUtils.hasText(properties.getPrimaryEndpointUrl())) {
                fetched = fetch(properties.getPrimaryEndpointUrl(), candidate);
                attemptCount++;
            }
            if ((fetched == null || !fetched.success) && StringUtils.hasText(properties.getFallbackEndpointUrl())) {
                fetched = fetch(properties.getFallbackEndpointUrl(), candidate);
                attemptCount++;
            }
            if (fetched != null && fetched.success) {
                if (merge(candidate, fetched.detail)) {
                    enrichedCount++;
                }
                continue;
            }
            failedCount++;
            if (fetched != null && "captcha_required".equals(fetched.errorCode)) {
                captchaFailureCount++;
            }
        }

        if (enrichedCount > 0 && failedCount == 0) {
            return Ali1688OfferDetailCompletionResult.completed(
                    attemptCount,
                    enrichedCount,
                    "Known-offer detail enrichment completed."
            );
        }
        if (enrichedCount > 0) {
            return Ali1688OfferDetailCompletionResult.partialEnriched(
                    attemptCount,
                    enrichedCount,
                    failedCount,
                    "Known-offer detail enrichment completed with partial failures."
            );
        }
        if (failedCount > 0 && failedCount == captchaFailureCount) {
            return Ali1688OfferDetailCompletionResult.blockedByCaptcha(
                    attemptCount,
                    failedCount,
                    "Known-offer detail enrichment blocked by 1688 CAPTCHA."
            );
        }
        return Ali1688OfferDetailCompletionResult.failed(
                attemptCount,
                failedCount,
                "Known-offer detail enrichment failed for " + failedCount + " candidate(s)."
        );
    }

    private List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> enrichableCandidates(
            List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> candidates
    ) {
        List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> result = new ArrayList<>();
        if (candidates == null) {
            return result;
        }
        int limit = Math.max(1, Math.min(properties.getMaxCandidates(), 10));
        for (Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate : candidates) {
            if (candidate == null || result.size() >= limit) {
                break;
            }
            if ((StringUtils.hasText(candidate.offerId) || StringUtils.hasText(candidate.candidateUrl))
                    && needsDetail(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean needsDetail(Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate) {
        return !StringUtils.hasText(candidate.title)
                || !StringUtils.hasText(candidate.supplierName)
                || !StringUtils.hasText(candidate.priceText)
                || candidate.priceMin == null
                || !StringUtils.hasText(candidate.moqText)
                || candidate.moqValue == null
                || !StringUtils.hasText(candidate.locationText)
                || !StringUtils.hasText(candidate.mainImageUrl)
                || candidate.imageUrls == null
                || candidate.imageUrls.isEmpty();
    }

    private int endpointCount() {
        int count = 0;
        if (StringUtils.hasText(properties.getPrimaryEndpointUrl())) {
            count++;
        }
        if (StringUtils.hasText(properties.getFallbackEndpointUrl())) {
            count++;
        }
        return Math.max(1, count);
    }

    private FetchResult fetch(
            String endpointTemplate,
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate
    ) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    endpointUri(endpointTemplate, candidate),
                    HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()),
                    JsonNode.class
            );
            return parseResponse(response.getBody());
        } catch (HttpStatusCodeException exception) {
            return parseErrorResponse(exception);
        } catch (RestClientException exception) {
            return FetchResult.failed("detail_service_failed", exception.getMessage());
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/json");
        return headers;
    }

    private URI endpointUri(
            String endpointTemplate,
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate
    ) {
        return UriComponentsBuilder.fromUriString(endpointTemplate)
                .encode()
                .buildAndExpand(Map.of(
                        "offerId", defaultText(candidate.offerId, ""),
                        "candidateUrl", defaultText(candidate.candidateUrl, "")
                ))
                .toUri();
    }

    private FetchResult parseResponse(JsonNode body) {
        if (body == null || body.isNull()) {
            return FetchResult.failed("unexpected_response", "empty detail response");
        }
        if (body.has("success") && !body.path("success").asBoolean(true)) {
            return FetchResult.failed(
                    defaultText(body.path("errorCode").asText(null), "detail_service_failed"),
                    body.path("message").asText(null)
            );
        }
        JsonNode detailNode = body.has("data") && body.get("data").isObject() ? body.get("data") : body;
        OfferDetail detail = objectMapper.convertValue(detailNode, OfferDetail.class);
        return FetchResult.success(detail);
    }

    private FetchResult parseErrorResponse(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (StringUtils.hasText(body)) {
            try {
                JsonNode json = objectMapper.readTree(body);
                if (json.has("errorCode") || json.has("message")) {
                    return FetchResult.failed(
                            defaultText(json.path("errorCode").asText(null), "detail_service_failed"),
                            json.path("message").asText(null)
                    );
                }
            } catch (Exception ignored) {
                // Use status fallback below.
            }
        }
        int status = exception.getRawStatusCode();
        if (status == 401 || status == 403) {
            return FetchResult.failed("login_required", exception.getMessage());
        }
        if (status == 429) {
            return FetchResult.failed("rate_limited", exception.getMessage());
        }
        return FetchResult.failed("detail_service_failed", exception.getMessage());
    }

    private boolean merge(
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate,
            OfferDetail detail
    ) {
        if (detail == null) {
            return false;
        }
        boolean changed = false;
        if (!StringUtils.hasText(candidate.title) && StringUtils.hasText(detail.title)) {
            candidate.title = detail.title.trim();
            changed = true;
        }
        if (!StringUtils.hasText(candidate.supplierName) && StringUtils.hasText(detail.supplierName)) {
            candidate.supplierName = detail.supplierName.trim();
            changed = true;
        }
        if (!StringUtils.hasText(candidate.priceText) && StringUtils.hasText(detail.priceText)) {
            candidate.priceText = detail.priceText.trim();
            changed = true;
        }
        if (candidate.priceMin == null && detail.priceMin != null) {
            candidate.priceMin = normalizePrice(detail.priceMin);
            changed = true;
        }
        if (candidate.priceMax == null && detail.priceMax != null) {
            candidate.priceMax = normalizePrice(detail.priceMax);
            changed = true;
        }
        if (!StringUtils.hasText(candidate.moqText) && StringUtils.hasText(detail.moqText)) {
            candidate.moqText = detail.moqText.trim();
            changed = true;
        }
        if (candidate.moqValue == null && detail.moqValue != null) {
            candidate.moqValue = detail.moqValue;
            changed = true;
        }
        if (!StringUtils.hasText(candidate.locationText) && StringUtils.hasText(detail.locationText)) {
            candidate.locationText = detail.locationText.trim();
            changed = true;
        }
        if (!StringUtils.hasText(candidate.mainImageUrl) && StringUtils.hasText(detail.mainImageUrl)) {
            candidate.mainImageUrl = detail.mainImageUrl.trim();
            changed = true;
        }
        if (mergeImages(candidate, detail)) {
            changed = true;
        }
        if (mergeMap(candidate, "badges", detail.badges)) {
            changed = true;
        }
        if (mergeMap(candidate, "skuSnapshot", detail.skuSnapshot)) {
            changed = true;
        }
        if (mergeMap(candidate, "supplierSnapshot", detail.supplierSnapshot)) {
            changed = true;
        }
        if (mergeMap(candidate, "logisticsSnapshot", detail.logisticsSnapshot)) {
            changed = true;
        }
        return changed;
    }

    private boolean mergeImages(
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate,
            OfferDetail detail
    ) {
        List<String> merged = new ArrayList<>();
        if (candidate.imageUrls != null) {
            for (String imageUrl : candidate.imageUrls) {
                addImage(merged, imageUrl);
            }
        }
        addImage(merged, detail.mainImageUrl);
        if (detail.imageUrls != null) {
            for (String imageUrl : detail.imageUrls) {
                addImage(merged, imageUrl);
            }
        }
        if (merged.equals(candidate.imageUrls)) {
            return false;
        }
        candidate.imageUrls = merged;
        return true;
    }

    private void addImage(List<String> images, String imageUrl) {
        String value = defaultText(imageUrl, null);
        if (StringUtils.hasText(value) && !images.contains(value)) {
            images.add(value);
        }
    }

    private boolean mergeMap(
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate,
            String field,
            Map<String, Object> detailMap
    ) {
        if (detailMap == null || detailMap.isEmpty()) {
            return false;
        }
        Map<String, Object> existing = mapFor(candidate, field);
        int before = existing.size();
        for (Map.Entry<String, Object> entry : detailMap.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || existing.containsKey(entry.getKey())) {
                continue;
            }
            existing.put(entry.getKey(), entry.getValue());
        }
        setMap(candidate, field, existing);
        return existing.size() > before;
    }

    private Map<String, Object> mapFor(
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate,
            String field
    ) {
        Map<String, Object> source;
        if ("badges".equals(field)) {
            source = candidate.badges;
        } else if ("skuSnapshot".equals(field)) {
            source = candidate.skuSnapshot;
        } else if ("supplierSnapshot".equals(field)) {
            source = candidate.supplierSnapshot;
        } else {
            source = candidate.logisticsSnapshot;
        }
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private void setMap(
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate candidate,
            String field,
            Map<String, Object> value
    ) {
        if ("badges".equals(field)) {
            candidate.badges = value;
        } else if ("skuSnapshot".equals(field)) {
            candidate.skuSnapshot = value;
        } else if ("supplierSnapshot".equals(field)) {
            candidate.supplierSnapshot = value;
        } else {
            candidate.logisticsSnapshot = value;
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private BigDecimal normalizePrice(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static class FetchResult {
        private final boolean success;
        private final OfferDetail detail;
        private final String errorCode;
        private final String message;

        private FetchResult(boolean success, OfferDetail detail, String errorCode, String message) {
            this.success = success;
            this.detail = detail;
            this.errorCode = errorCode;
            this.message = message;
        }

        private static FetchResult success(OfferDetail detail) {
            return new FetchResult(true, detail, null, null);
        }

        private static FetchResult failed(String errorCode, String message) {
            return new FetchResult(false, null, errorCode, message);
        }
    }

    private static class OfferDetail {
        public String title;
        public String supplierName;
        public String priceText;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String moqText;
        public Integer moqValue;
        public String locationText;
        public String mainImageUrl;
        public List<String> imageUrls;
        public Map<String, Object> badges;
        public Map<String, Object> skuSnapshot;
        public Map<String, Object> supplierSnapshot;
        public Map<String, Object> logisticsSnapshot;
    }
}
