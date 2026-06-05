package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@ConditionalOnProperty(
        prefix = "nuono.procurement.ali1688.historical-order.open-api",
        name = "enabled",
        havingValue = "true"
)
public class HttpAli1688HistoricalOrderProvider implements Ali1688HistoricalOrderProvider {

    private static final DateTimeFormatter ALI1688_COMPACT_MILLIS_OFFSET =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSZ");
    private static final DateTimeFormatter ALI1688_COMPACT_OFFSET =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ");
    private static final DateTimeFormatter MYSQL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Ali1688OpenApiSigner signer;
    private final Ali1688TokenCipher tokenCipher;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Ali1688HistoricalOrderMapper mapper;

    @Autowired
    public HttpAli1688HistoricalOrderProvider(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Ali1688OpenApiSigner signer,
            Ali1688TokenCipher tokenCipher,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            Ali1688HistoricalOrderMapper mapper
    ) {
        this(
                properties,
                signer,
                tokenCipher,
                objectMapper,
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .setReadTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                        .build(),
                mapper
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
            Ali1688HistoricalOrderMapper mapper
    ) {
        this.properties = properties;
        this.signer = signer;
        this.tokenCipher = tokenCipher;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }

    @Override
    public Page fetchPage(Ali1688HistoricalOrderAuthorizationRow authorization, String cursor) {
        if (!isConfigured()) {
            return failurePage(Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED, "1688 OpenAPI provider 未配置。");
        }
        try {
            String accessToken = resolveAccessToken(authorization);
            if (!StringUtils.hasText(accessToken)) {
                return failurePage(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, "1688 授权 token 为空，请重新授权。");
            }
            return fetchPageWithAccessToken(accessToken, cursor);
        } catch (RestClientException exception) {
            return failurePage(Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE, "1688 OpenAPI 调用失败：" + exception.getMessage());
        } catch (RuntimeException exception) {
            return failurePage(Ali1688HistoricalOrderFailureCode.AUTH_REQUIRED, "1688 授权 token 不可用，请重新授权。");
        }
    }

    private Page fetchPageWithAccessToken(String accessToken, String cursor) {
        int pageNo = parsePage(cursor);
        try {
            JsonNode listPayload = callOpenApi(
                    openApiPath(properties.getBuyerOrderListNamespace(), properties.getBuyerOrderListApiName()),
                    Map.of(
                            "access_token", accessToken,
                            "page", String.valueOf(pageNo),
                            "pageSize", String.valueOf(Math.max(1, properties.getPageSize()))
                    )
            );
            if (hasProviderError(listPayload)) {
                return providerErrorPage(listPayload);
            }
            List<OrderSnapshot> orders = new ArrayList<>();
            JsonNode resultPayload = unwrapResult(listPayload);
            for (JsonNode orderNode : orderNodes(resultPayload)) {
                OrderSnapshot order = toOrderSnapshot(orderNode);
                if (order.getItems().isEmpty() && StringUtils.hasText(order.getProviderOrderNo())) {
                    order = mergeOrder(order, fetchOrderDetail(accessToken, order.getProviderOrderNo()));
                }
                orders.add(order);
            }
            Page page = new Page(orders);
            boolean hasMore = hasMore(listPayload, resultPayload, pageNo, orders.size());
            page.setHasMore(hasMore);
            page.setNextCursor(hasMore ? String.valueOf(pageNo + 1) : null);
            page.setProgressPercent(hasMore ? Math.min(99, pageNo * 10) : 100);
            return page;
        } catch (RestClientException exception) {
            return failurePage(Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE, "1688 OpenAPI 调用失败：" + exception.getMessage());
        } catch (RuntimeException exception) {
            return failurePage(Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE, "1688 OpenAPI 响应解析失败：" + exception.getMessage());
        }
    }

    private String resolveAccessToken(Ali1688HistoricalOrderAuthorizationRow authorization) {
        if (authorization == null) {
            return null;
        }
        if (shouldRefreshToken(authorization)) {
            return refreshAccessToken(authorization);
        }
        return tokenCipher.decrypt(authorization.getAccessTokenCipher());
    }

    private boolean shouldRefreshToken(Ali1688HistoricalOrderAuthorizationRow authorization) {
        LocalDateTime expiresAt = authorization.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(LocalDateTime.now().plusMinutes(2));
    }

    private String refreshAccessToken(Ali1688HistoricalOrderAuthorizationRow authorization) {
        String refreshToken = tokenCipher.decrypt(authorization.getRefreshTokenCipher());
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("need_refresh_token", "true");
        body.add("client_id", trim(properties.getAppKey()));
        body.add("client_secret", trim(properties.getAppSecret()));
        body.add("refresh_token", trim(refreshToken));

        ResponseEntity<String> response = restTemplate.exchange(
                tokenUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );
        TokenPayload token = parseTokenPayload(response.getBody());
        authorization.setStatus("authorized");
        authorization.setAccessTokenCipher(tokenCipher.encrypt(token.accessToken));
        authorization.setRefreshTokenCipher(tokenCipher.encrypt(defaultText(token.refreshToken, refreshToken)));
        authorization.setExpiresAt(token.expiresAt);
        if (StringUtils.hasText(token.providerAccountId)) {
            authorization.setProviderAccountId(token.providerAccountId);
        }
        if (StringUtils.hasText(token.accountLabel)) {
            authorization.setAccountLabel(token.accountLabel);
        }
        if (mapper != null) {
            mapper.updateAuthorizationTokens(authorization);
        }
        return token.accessToken;
    }

    private OrderSnapshot fetchOrderDetail(String accessToken, String orderId) {
        JsonNode detailPayload = callOpenApi(
                openApiPath(properties.getBuyerOrderDetailNamespace(), properties.getBuyerOrderDetailApiName()),
                Map.of(
                        "access_token", accessToken,
                        "orderId", orderId
                )
        );
        JsonNode result = unwrapResult(detailPayload);
        JsonNode orderNode = firstObject(result, "order", "data", "result");
        return toOrderSnapshot(orderNode == null ? result : orderNode);
    }

    private JsonNode callOpenApi(String path, Map<String, String> rawParams) {
        Map<String, String> params = new LinkedHashMap<>(rawParams);
        params.put("_aop_timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("_aop_signature", signer.apiSignature(path, params, properties.getAppSecret()));
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl() + path);
        params.forEach(builder::queryParam);
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build().encode().toUriString(),
                HttpMethod.GET,
                null,
                String.class
        );
        try {
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception exception) {
            throw new IllegalStateException("invalid_json", exception);
        }
    }

    private TokenPayload parseTokenPayload(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode payload = root.has("result") && root.get("result").isObject() ? root.get("result") : root;
            TokenPayload token = new TokenPayload();
            token.accessToken = text(payload, "access_token", "accessToken");
            token.refreshToken = text(payload, "refresh_token", "refreshToken");
            token.providerAccountId = text(payload, "memberId", "member_id", "resource_owner", "loginId", "login_id", "accountId");
            token.accountLabel = text(payload, "resource_owner", "loginName", "login_id", "memberId", "member_id");
            Integer expiresIn = integer(payload, "expires_in", "expiresIn");
            token.expiresAt = expiresIn == null ? null : LocalDateTime.now().plusSeconds(Math.max(0, expiresIn));
            if (!StringUtils.hasText(token.accessToken)) {
                throw new IllegalStateException("1688 OAuth refresh 响应缺少 access_token。");
            }
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("1688 OAuth refresh 响应解析失败。", exception);
        }
    }

    private OrderSnapshot mergeOrder(OrderSnapshot base, OrderSnapshot detail) {
        if (detail == null) {
            return base;
        }
        if (!StringUtils.hasText(detail.getProviderOrderNo())) {
            detail.setProviderOrderNo(base.getProviderOrderNo());
        }
        if (!StringUtils.hasText(detail.getOrderTime())) {
            detail.setOrderTime(base.getOrderTime());
        }
        if (!StringUtils.hasText(detail.getPaidAt())) {
            detail.setPaidAt(base.getPaidAt());
        }
        if (!StringUtils.hasText(detail.getBuyerCompanyName())) {
            detail.setBuyerCompanyName(base.getBuyerCompanyName());
        }
        if (!StringUtils.hasText(detail.getBuyerMemberName())) {
            detail.setBuyerMemberName(base.getBuyerMemberName());
        }
        if (!StringUtils.hasText(detail.getSupplierName())) {
            detail.setSupplierName(base.getSupplierName());
        }
        if (!StringUtils.hasText(detail.getSellerMemberName())) {
            detail.setSellerMemberName(base.getSellerMemberName());
        }
        return detail;
    }

    private OrderSnapshot toOrderSnapshot(JsonNode orderNode) {
        OrderSnapshot order = new OrderSnapshot();
        if (orderNode == null || orderNode.isNull()) {
            return order;
        }
        JsonNode baseInfo = firstObject(orderNode, "baseInfo");
        JsonNode source = baseInfo == null ? orderNode : baseInfo;
        JsonNode buyerContact = firstObject(source, "buyerContact");
        JsonNode sellerContact = firstObject(source, "sellerContact");
        JsonNode nativeLogistics = firstObject(orderNode, "nativeLogistics");
        JsonNode receiverInfo = firstObject(source, "receiverInfo");

        order.setProviderOrderNo(defaultText(text(source, "idOfStr", "id", "orderId", "orderIdStr", "providerOrderNo"),
                text(orderNode, "idOfStr", "id", "orderId", "orderIdStr", "providerOrderNo")));
        order.setOrderTime(normalizeDateTime(defaultText(text(source, "createTime", "gmtCreate", "orderTime"), text(orderNode, "createTime", "gmtCreate", "orderTime"))));
        order.setPaidAt(normalizeDateTime(defaultText(text(source, "payTime", "gmtPayment", "paidAt"), text(orderNode, "payTime", "gmtPayment", "paidAt"))));
        order.setBuyerCompanyName(defaultText(text(source, "buyerCompanyName", "buyerCompany"), text(buyerContact, "companyName", "name")));
        order.setBuyerMemberName(defaultText(text(source, "buyerLoginId", "buyerMemberName", "buyerMemberId"), text(orderNode, "buyerLoginId", "buyerMemberName", "buyerMemberId")));
        order.setSupplierName(defaultText(text(source, "sellerCompanyName", "supplierName", "sellerName"), text(sellerContact, "companyName", "name")));
        order.setSellerMemberName(defaultText(text(source, "sellerLoginId", "sellerMemberName", "sellerMemberId"), text(orderNode, "sellerLoginId", "sellerMemberName", "sellerMemberId")));
        order.setGoodsTotalText(defaultText(text(source, "sumProductPayment", "goodsTotalText", "goodsTotal"), text(orderNode, "sumProductPayment", "goodsTotalText", "goodsTotal")));
        order.setFreightText(defaultText(text(source, "shippingFee", "freightText", "freight", "shipFee"), text(orderNode, "shippingFee", "freightText", "freight", "shipFee")));
        order.setPaidAmountText(defaultText(text(source, "sumPayment", "paidAmountText", "paidAmount", "totalAmount"), text(orderNode, "sumPayment", "paidAmountText", "paidAmount", "totalAmount")));
        order.setAmountText(defaultText(text(orderNode, "amountText", "sumPayment", "totalAmount"), order.getPaidAmountText()));
        order.setCurrency(defaultText(defaultText(text(source, "currency"), text(orderNode, "currency")), "CNY"));
        order.setOrderStatus(defaultText(text(source, "status", "orderStatus"), text(orderNode, "status", "orderStatus")));
        order.setLogisticsStatus(defaultText(text(source, "logisticsStatus", "shippingStatus"), text(orderNode, "logisticsStatus", "shippingStatus")));
        order.setShipperName(text(orderNode, "shipperName"));
        order.setOriginalUrl(text(orderNode, "originalUrl", "orderUrl"));
        order.setReceiverName(defaultText(text(orderNode, "receiverName"), defaultText(text(nativeLogistics, "contactPerson"), text(receiverInfo, "toFullName"))));
        order.setReceiverPostalCode(defaultText(text(nativeLogistics, "zip"), text(receiverInfo, "toPost")));
        order.setReceiverPhone(defaultText(text(orderNode, "receiverPhone", "receiverMobile", "receiverTelephone"), text(buyerContact, "phone", "mobile")));
        order.setReceiverMobile(defaultText(text(orderNode, "receiverMobile"), text(buyerContact, "mobile")));
        order.setReceiverAddress(defaultText(text(orderNode, "receiverAddress"), text(nativeLogistics, "address")));
        order.setBuyerRemark(defaultText(text(source, "buyerRemark", "buyerMemo"), text(orderNode, "buyerRemark", "buyerMemo")));
        order.setSupplierContact(defaultText(text(orderNode, "supplierContact"), text(sellerContact, "name", "phone", "mobile")));
        order.setInitiatorLoginName(defaultText(text(orderNode, "initiatorLoginName", "buyerLoginId"), order.getBuyerMemberName()));
        order.setRawSnapshotJson(writeJson(orderNode));

        for (JsonNode itemNode : itemNodes(orderNode)) {
            order.getItems().add(toItemSnapshot(itemNode));
        }
        return order;
    }

    private OrderItemSnapshot toItemSnapshot(JsonNode itemNode) {
        OrderItemSnapshot item = new OrderItemSnapshot();
        item.setOfferId(text(itemNode, "offerId", "offerID", "productID"));
        item.setSkuId(text(itemNode, "skuId", "skuID", "specId"));
        item.setTitle(text(itemNode, "name", "title", "productName"));
        item.setSkuText(defaultText(text(itemNode, "skuInfo", "skuText", "spec"), skuInfosText(itemNode.get("skuInfos"))));
        item.setModelText(text(itemNode, "modelText"));
        item.setProductCode(text(itemNode, "productCode", "cargoNumber"));
        item.setSingleProductCode(text(itemNode, "productCargoNumber", "singleProductCode", "cargoNumber"));
        item.setQuantity(integer(itemNode, "quantity", "amount", "num"));
        item.setUnit(text(itemNode, "unit", "unitName"));
        item.setUnitPriceText(text(itemNode, "price", "unitPriceText", "unitPrice"));
        item.setAmountText(text(itemNode, "itemAmount", "amountText", "productPayment"));
        item.setImageUrl(text(itemNode, "imageUrl", "mainImageUrl", "productImgUrl"));
        item.setLogisticsCompany(text(itemNode, "logisticsCompany", "logisticsCompanyName", "expressName"));
        item.setTrackingNo(text(itemNode, "trackingNo", "logisticsBillNo", "expressNo"));
        JsonNode logistics = firstObject(itemNode, "logistics", "logisticsInfo");
        if (logistics != null) {
            item.setLogisticsCompany(defaultText(item.getLogisticsCompany(), text(logistics, "logisticsCompany", "logisticsCompanyName")));
            item.setTrackingNo(defaultText(item.getTrackingNo(), text(logistics, "trackingNo", "logisticsBillNo")));
        }
        return item;
    }

    private String skuInfosText(JsonNode skuInfos) {
        if (skuInfos == null || !skuInfos.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode skuInfo : skuInfos) {
            String name = text(skuInfo, "name");
            String value = text(skuInfo, "value");
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                values.add(name + "：" + value);
            } else if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(" / ", values);
    }

    private List<JsonNode> orderNodes(JsonNode payload) {
        return arrayValues(payload, "orders", "orderList", "data", "items");
    }

    private List<JsonNode> itemNodes(JsonNode orderNode) {
        return arrayValues(orderNode, "productItems", "orderItems", "items", "cargoList", "entries");
    }

    private List<JsonNode> arrayValues(JsonNode node, String... names) {
        List<JsonNode> values = new ArrayList<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            node.forEach(values::add);
            return values;
        }
        for (String name : names) {
            JsonNode candidate = node.get(name);
            if (candidate != null && candidate.isArray()) {
                candidate.forEach(values::add);
                return values;
            }
        }
        return values;
    }

    private JsonNode unwrapResult(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createObjectNode();
        }
        JsonNode result = root.get("result");
        return result == null || result.isNull() ? root : result;
    }

    private JsonNode firstObject(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }

    private boolean hasMore(JsonNode rootPayload, JsonNode resultPayload, int pageNo, int resultCount) {
        String explicit = defaultText(text(rootPayload, "hasMore", "hasNext", "hasNextPage"),
                text(resultPayload, "hasMore", "hasNext", "hasNextPage"));
        if (StringUtils.hasText(explicit)) {
            return Boolean.parseBoolean(explicit) || "1".equals(explicit);
        }
        Integer total = firstNonNull(
                integer(rootPayload, "total", "totalCount", "totalRecord"),
                integer(resultPayload, "total", "totalCount", "totalRecord")
        );
        Integer pageSize = firstNonNull(
                integer(rootPayload, "pageSize", "size"),
                integer(resultPayload, "pageSize", "size")
        );
        int effectivePageSize = pageSize == null ? Math.max(1, properties.getPageSize()) : Math.max(1, pageSize);
        if (total != null) {
            return pageNo * effectivePageSize < total;
        }
        return resultCount >= effectivePageSize;
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first == null ? second : first;
    }

    private boolean hasProviderError(JsonNode payload) {
        return StringUtils.hasText(text(payload, "error_code", "errorCode", "error", "errorMessage"));
    }

    private Page providerErrorPage(JsonNode payload) {
        String message = defaultText(text(payload, "error_message", "errorMessage", "message"), "1688 OpenAPI 返回错误。");
        String normalized = message.toLowerCase();
        if (normalized.contains("rate") || normalized.contains("limit")) {
            return failurePage(Ali1688HistoricalOrderFailureCode.RATE_LIMITED, message);
        }
        if (normalized.contains("risk") || normalized.contains("captcha") || normalized.contains("forbidden")) {
            return failurePage(Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL, message);
        }
        return failurePage(Ali1688HistoricalOrderFailureCode.UNEXPECTED_RESPONSE, message);
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

    private int parsePage(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(cursor.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private Integer integer(JsonNode node, String... names) {
        String value = text(node, names);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).intValue();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            return trimmed;
        }
        try {
            if (trimmed.matches("\\d{17}[+-]\\d{4}")) {
                return OffsetDateTime.parse(trimmed, ALI1688_COMPACT_MILLIS_OFFSET)
                        .toLocalDateTime()
                        .format(MYSQL_DATETIME);
            }
            if (trimmed.matches("\\d{14}[+-]\\d{4}")) {
                return OffsetDateTime.parse(trimmed, ALI1688_COMPACT_OFFSET)
                        .toLocalDateTime()
                        .format(MYSQL_DATETIME);
            }
        } catch (RuntimeException ignored) {
            return trimmed;
        }
        return trimmed;
    }

    private String text(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                if (value.isArray()) {
                    for (JsonNode item : value) {
                        if (item != null && !item.isNull() && item.isValueNode() && StringUtils.hasText(item.asText())) {
                            return item.asText().trim();
                        }
                    }
                } else if (StringUtils.hasText(value.asText())) {
                    return value.asText().trim();
                }
            }
        }
        return null;
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isConfigured() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getAppKey())
                && StringUtils.hasText(properties.getAppSecret())
                && StringUtils.hasText(properties.getApiGatewayBaseUrl());
    }

    private String baseUrl() {
        String base = trim(properties.getApiGatewayBaseUrl());
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String tokenUrl() {
        return trim(properties.getTokenUrlTemplate()).replace("{appKey}", trim(properties.getAppKey()));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static final class TokenPayload {
        private String accessToken;
        private String refreshToken;
        private String providerAccountId;
        private String accountLabel;
        private LocalDateTime expiresAt;
    }
}
