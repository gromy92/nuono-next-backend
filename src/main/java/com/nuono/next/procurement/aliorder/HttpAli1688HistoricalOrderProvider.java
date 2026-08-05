package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Service
@ConditionalOnProperty(
        prefix = "nuono.procurement.ali1688.historical-order.open-api",
        name = "enabled",
        havingValue = "true"
)
public class HttpAli1688HistoricalOrderProvider implements Ali1688HistoricalOrderProvider {
    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688OpenApiSigner signer;
    private final Ali1688TokenCipher tokenCipher;
    private final RestTemplate restTemplate;
    private final Ali1688OpenApiAuthorizationMapper authorizationMapper;
    private final Ali1688OpenApiJson json;
    private final Ali1688OpenApiOrderMapper orderMapper;
    private final Ali1688OpenApiListContract listContract;
    private final Ali1688OpenApiListRequestContract listRequestContract;
    private final Ali1688OpenApiFailureClassifier failureClassifier;
    private final Ali1688OpenApiTokenRefresher tokenRefresher;

    @Autowired
    public HttpAli1688HistoricalOrderProvider(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            Ali1688OpenApiAuthorizationMapper authorizationMapper
    ) {
        this(
                properties,
                signer,
                tokenCipher,
                objectMapper,
                restTemplateBuilder
                        .requestFactory(Ali1688NoRedirectRequestFactory::new)
                        .setConnectTimeout(Duration.ofSeconds(Math.min(10, Math.max(1, properties.getTimeoutSeconds()))))
                        .setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .build(),
                authorizationMapper
        );
    }

    HttpAli1688HistoricalOrderProvider(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this(properties, signer, tokenCipher, objectMapper, restTemplate, null);
    }

    HttpAli1688HistoricalOrderProvider(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            Ali1688OpenApiAuthorizationMapper authorizationMapper
    ) {
        this.properties = properties;
        this.signer = signer;
        this.tokenCipher = tokenCipher;
        this.restTemplate = restTemplate;
        this.authorizationMapper = authorizationMapper;
        this.json = new Ali1688OpenApiJson(objectMapper);
        this.orderMapper = new Ali1688OpenApiOrderMapper(properties, json);
        this.listContract = new Ali1688OpenApiListContract(json);
        this.listRequestContract = new Ali1688OpenApiListRequestContract(
                properties,
                listContract,
                json
        );
        this.failureClassifier = new Ali1688OpenApiFailureClassifier(json);
        this.tokenRefresher = new Ali1688OpenApiTokenRefresher(
                properties,
                tokenCipher,
                restTemplate,
                authorizationMapper,
                objectMapper
        );
    }

    @Override
    public Page fetchPage(Ali1688HistoricalOrderAuthorizationRow authorization, String cursor) {
        return fetchPage(Ali1688HistoricalOrderRequest.full(authorization, cursor));
    }

    @Override
    public Page fetchPage(Ali1688HistoricalOrderRequest request) {
        return executeList(request, true);
    }

    @Override
    public Page fetchOrderList(Ali1688HistoricalOrderRequest request) {
        return executeList(request, false);
    }
    @Override
    public int listPageSize() {
        return Math.max(1, properties.getPageSize());
    }

    private Page executeList(Ali1688HistoricalOrderRequest request, boolean includeDetails) {
        if (request == null) {
            return failurePage(Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE, "1688 OpenAPI 请求不能为空。");
        }
        if (!isConfigured()) {
            return failurePage(Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED, "1688 OpenAPI provider 未配置。");
        }
        if (request.isFixedWindow() && !isIncrementalContractConfigured()) {
            return failurePage(
                    Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED,
                    "1688 OpenAPI DP-10 官方窗口分页合同未配置。"
            );
        }
        if (request.isFixedWindow()
                && (request.getModifiedTo() == null
                || request.getMode() == SyncMode.INCREMENTAL
                        && request.getModifiedFrom() == null)) {
            return failurePage(
                    Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE,
                    "1688 OpenAPI DP-10 请求缺少固定修改时间窗口。"
            );
        }
        try {
            String accessToken = resolveAccessToken(request.getAuthorization(), includeDetails);
            if (!StringUtils.hasText(accessToken)) {
                return failurePage(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, "1688 授权 token 为空，请重新授权。");
            }
            return fetchPageWithAccessToken(accessToken, request, includeDetails);
        } catch (HttpStatusCodeException exception) {
            return httpFailurePage(exception);
        } catch (RestClientException exception) {
            return failurePage(
                    Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE,
                    failureClassifier.safeMessage(Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE)
            );
        } catch (RuntimeException exception) {
            return failurePage(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, "1688 授权 token 不可用，请重新授权。");
        }
    }

    private Page fetchPageWithAccessToken(
            String accessToken,
            Ali1688HistoricalOrderRequest request,
            boolean includeDetails
    ) {
        int pageNo = listRequestContract.pageNo(request);
        int pageSize = listRequestContract.pageSize(request);
        try {
            JsonNode listPayload = callOpenApi(
                    openApiPath(properties.getBuyerOrderListNamespace(), properties.getBuyerOrderListApiName()),
                    listRequestContract.parameters(accessToken, request)
            );
            if (failureClassifier.hasProviderError(listPayload)) {
                return failureClassifier.providerError(listPayload);
            }
            JsonNode orderContainer = listContract.orderContainer(
                    listPayload,
                    request.isFixedWindow()
            );
            if (orderContainer == null) {
                return failurePage(
                        Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE,
                        "1688 OpenAPI 订单列表容器无法证明完整。"
                );
            }
            List<OrderSnapshot> orders = new ArrayList<>();
            for (JsonNode orderNode : orderContainer) {
                OrderSnapshot order = orderMapper.map(orderNode);
                if (includeDetails
                        && order.getItems().isEmpty()
                        && StringUtils.hasText(order.getProviderOrderNo())) {
                    order = orderMapper.merge(
                            order,
                            fetchOrderDetailWithAccessToken(accessToken, order.getProviderOrderNo())
                    );
                }
                orders.add(order);
            }
            Page page = new Page(orders);
            page.setContainerProven(true);
            Ali1688OpenApiListContract.Pagination pagination =
                    listRequestContract.pagination(listPayload, request, orders.size());
            if (!pagination.isProven()) {
                return failurePage(
                        Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE,
                        "1688 OpenAPI 分页结束状态无法证明。"
                );
            }
            page.setPaginationProven(true);
            page.setPageNo(pageNo);
            page.setPageSize(pageSize);
            page.setTotalRecord(pagination.totalRecord());
            page.setExpectedPages(pagination.expectedPages());
            page.setHasMore(pagination.hasMore());
            String nextCursor = pagination.hasMore() ? String.valueOf(pageNo + 1) : null;
            page.setNextCursor(nextCursor);
            page.setProgressPercent(pagination.hasMore() ? Math.min(99, pageNo * 10) : 100);
            return page;
        } catch (ProviderPageException exception) {
            return exception.getPage();
        } catch (HttpStatusCodeException exception) {
            return httpFailurePage(exception);
        } catch (RestClientException exception) {
            return failurePage(
                    Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE,
                    failureClassifier.safeMessage(Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE)
            );
        } catch (RuntimeException exception) {
            return failurePage(
                    Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE,
                    failureClassifier.safeMessage(Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE)
            );
        }
    }

    private Page httpFailurePage(HttpStatusCodeException exception) {
        int status = exception.getRawStatusCode();
        Ali1688HistoricalOrderFailureCode code;
        if (status == 401) {
            code = Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED;
        } else if (status == 403) {
            code = Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL;
        } else if (status == 429) {
            code = Ali1688HistoricalOrderFailureCode.RATE_LIMITED;
        } else if (status >= 500) {
            code = Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE;
        } else {
            code = Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE;
        }
        Page page = failurePage(code, "1688 OpenAPI HTTP " + status + "。");
        page.setRetryAfter(parseRetryAfter(exception.getResponseHeaders()));
        return page;
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Duration.ofSeconds(Math.max(0L, Long.parseLong(trimmed)));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (RuntimeException ignoredDate) {
                return null;
            }
        }
    }

    private String resolveAccessToken(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            boolean allowInlineRefresh
    ) {
        if (authorization == null) {
            return null;
        }
        if (allowInlineRefresh && tokenRefresher.requiresRefresh(authorization)) {
            Ali1688HistoricalOrderAuthorizationRefreshResult refresh =
                    tokenRefresher.refresh(authorization);
            if (!refresh.isSuccess()) {
                return null;
            }
        }
        return tokenCipher.decrypt(authorization.getAccessTokenCipher());
    }

    @Override
    public boolean requiresAuthorizationRefresh(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        return tokenRefresher.requiresRefresh(authorization);
    }

    @Override
    public Ali1688HistoricalOrderAuthorizationRefreshResult refreshAuthorization(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        if (!isConfigured() || authorizationMapper == null) {
            return Ali1688HistoricalOrderAuthorizationRefreshResult.failure(
                    Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED,
                    null
            );
        }
        return tokenRefresher.refresh(authorization);
    }

    @Override
    public DetailResult fetchOrderDetail(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String providerOrderNo
    ) {
        if (!isConfigured() || !StringUtils.hasText(providerOrderNo)) {
            return DetailResult.failure(
                    Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED.getCode(),
                    null
            );
        }
        try {
            String accessToken = resolveAccessToken(authorization, false);
            if (!StringUtils.hasText(accessToken)) {
                return DetailResult.failure(
                        Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED.getCode(),
                        null
                );
            }
            OrderSnapshot detail = fetchOrderDetailWithAccessToken(accessToken, providerOrderNo);
            return detail == null ? DetailResult.notFound() : DetailResult.success(detail);
        } catch (ProviderPageException exception) {
            Page page = exception.getPage();
            return DetailResult.failure(page.getFailureCode(), page.getRetryAfter());
        } catch (HttpStatusCodeException exception) {
            if (isOrderDetailBusinessAbsence(exception)) {
                return DetailResult.notFound();
            }
            Page page = httpFailurePage(exception);
            return DetailResult.failure(page.getFailureCode(), page.getRetryAfter());
        } catch (RestClientException exception) {
            return DetailResult.failure(
                    Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE.getCode(),
                    null
            );
        } catch (RuntimeException exception) {
            return DetailResult.failure(
                    Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE.getCode(),
                    null
            );
        }
    }

    private OrderSnapshot fetchOrderDetailWithAccessToken(String accessToken, String orderId) {
        JsonNode detailPayload;
        try {
            detailPayload = callOpenApi(
                    openApiPath(properties.getBuyerOrderDetailNamespace(), properties.getBuyerOrderDetailApiName()),
                    Map.of(
                            "access_token", accessToken,
                            "orderId", orderId
                    )
            );
        } catch (HttpStatusCodeException exception) {
            if (isOrderDetailBusinessAbsence(exception)) {
                return null;
            }
            throw exception;
        }
        if (failureClassifier.hasProviderError(detailPayload)) {
            if (failureClassifier.isStructuredOrderAbsence(detailPayload)) {
                return null;
            }
            throw new ProviderPageException(failureClassifier.providerError(detailPayload));
        }
        JsonNode result = json.unwrapResult(detailPayload);
        JsonNode orderNode = json.firstObject(result, "order", "data", "result");
        return orderMapper.map(orderNode == null ? result : orderNode);
    }

    private boolean isOrderDetailBusinessAbsence(HttpStatusCodeException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        try {
            return failureClassifier.isStructuredOrderAbsence(json.read(responseBody));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private JsonNode callOpenApi(String path, Map<String, String> rawParams) {
        Map<String, String> params = new LinkedHashMap<>(rawParams);
        params.put("_aop_timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("_aop_signature", signer.apiSignature(path, params, properties.getAppSecret()));
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl() + path);
        params.forEach((name, value) -> builder.queryParam(
                name,
                UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8)
                        .replace("+", "%2B")
        ));
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                null,
                String.class
        );
        try {
            return json.read(Ali1688OpenApiHttpResponse.requireSuccessfulBody(response));
        } catch (Exception exception) {
            throw new IllegalStateException("invalid_json", exception);
        }
    }

    private Page failurePage(Ali1688HistoricalOrderFailureCode code, String message) {
        Page page = new Page(List.of());
        page.setFailureCode(code.getCode());
        page.setFailureMessage(message);
        page.setRetryableFailure(code.isRetryable());
        page.setProgressPercent(0);
        return page;
    }

    private String openApiPath(String namespace, String apiName) {
        return "/openapi/param2/"
                + defaultText(properties.getApiVersion(), "1")
                + "/"
                + trim(namespace)
                + "/"
                + trim(apiName)
                + "/"
                + trim(properties.getAppKey());
    }

    private boolean isConfigured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getAppKey())
                && StringUtils.hasText(properties.getAppSecret())
                && StringUtils.hasText(properties.getTokenCipherSecret())
                && StringUtils.hasText(properties.getApiGatewayBaseUrl());
    }

    private boolean isIncrementalContractConfigured() {
        return properties.hasProductionDp10Configuration();
    }

    private String baseUrl() {
        String base = trim(properties.getApiGatewayBaseUrl());
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static final class ProviderPageException extends RuntimeException {
        private final Page page;

        private ProviderPageException(Page page) {
            super(page == null ? null : page.getFailureMessage());
            this.page = page;
        }

        private Page getPage() {
            return page;
        }
    }
}
