package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.product.NoonProductListFieldSupport;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingCatalogSessionPreflight {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;

    private ProductListingCatalogSessionPreflight() {
    }

    static ProductListingNoonWriteStepResult verifyCreateTargetAbsent(
            ObjectMapper objectMapper,
            NoonPullGatewaySession session,
            NoonPullStoreBinding binding,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers,
            String partnerSku,
            Long realRunTaskId
    ) {
        String normalizedPartnerSku = normalize(partnerSku);
        if (!StringUtils.hasText(normalizedPartnerSku)) {
            throw new IllegalArgumentException("商品创建预检缺少 partnerSku。");
        }
        for (int page = 1; page <= MAX_PAGES; page++) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("page", page);
            body.put("per_page", PAGE_SIZE);
            body.put("noon_store_code", binding.getStoreCode());
            body.put("noonChannelType", "noon");
            body.put("search", normalizedPartnerSku);
            JsonNode root = ProductListingNoonCallGuard.requireAuthorized(
                    session.postJson(endpoints.getOfferListUrl(), body, true, headers));
            JsonNode error = root == null ? null : root.get("error");
            if (hasBusinessError(error)) {
                throw new IllegalStateException("Noon Catalog 商品接口预检返回业务错误，未发起商品创建。");
            }
            JsonNode data = root == null ? null : root.path("data");
            JsonNode hits = data == null ? null : data.path("hits");
            if (data == null || !data.isObject() || hits == null || !hits.isArray()) {
                throw new IllegalStateException("Noon Catalog 商品接口预检返回结构异常，未发起商品创建。");
            }
            for (JsonNode hit : hits) {
                if (normalizedPartnerSku.equalsIgnoreCase(normalize(text(hit, "partner_sku")))) {
                    return existingProduct(hit);
                }
            }
            JsonNode totalNode = data.get("total");
            if (totalNode == null
                    || !totalNode.isIntegralNumber()
                    || !totalNode.canConvertToInt()
                    || totalNode.asInt() < hits.size()) {
                throw new IllegalStateException(
                        "Noon Catalog 商品接口预检分页结构异常，未发起商品创建。");
            }
            int total = totalNode.asInt();
            if (hits.isEmpty() && total > 0) {
                throw new IllegalStateException(
                        "Noon Catalog 商品接口预检分页结果不一致，无法确认 PSKU 不存在，未发起商品创建。");
            }
            if (hits.isEmpty() || page * PAGE_SIZE >= total) {
                return absenceProof(
                        binding.getStoreCode(), normalizedPartnerSku, realRunTaskId);
            }
        }
        throw new IllegalStateException(
                "Noon Catalog 商品接口预检结果超过安全分页上限，无法确认 PSKU 不存在，未发起商品创建。");
    }

    private static ProductListingNoonWriteStepResult existingProduct(JsonNode hit) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("pre_create_absence_verified");
        step.setStatus("failed");
        step.setFailureCode("partner_sku_already_exists");
        step.setFailureMessage("Noon 中已存在相同店铺 PSKU，系统未发起商品创建。");
        String skuParent = firstNonBlank(
                text(hit, "csku_parent"),
                text(hit, "zsku_parent"),
                text(hit, "sku_parent"),
                text(hit, "skuParent"),
                text(hit, "catalog_sku")
        );
        String pskuCode = NoonProductListFieldSupport.pskuCode(hit);
        if (StringUtils.hasText(skuParent) && StringUtils.hasText(pskuCode)) {
            step.setExternalReference(
                    "skuParent=" + normalize(skuParent) + ";pskuCode=" + normalize(pskuCode));
        }
        step.setWriteMayHaveOccurred(false);
        return step;
    }

    private static ProductListingNoonWriteStepResult absenceProof(
            String storeCode,
            String partnerSku,
            Long realRunTaskId
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("pre_create_absence_verified");
        step.setStatus("succeeded");
        step.setExternalReference(
                "storeCode=" + normalize(storeCode)
                        + ";partnerSku=" + partnerSku
                        + ";realRunTaskId=" + realRunTaskId
                        + ";checkedAt=" + OffsetDateTime.now()
        );
        step.setWriteMayHaveOccurred(false);
        return step;
    }

    private static boolean hasBusinessError(JsonNode error) {
        return error != null
                && !error.isNull()
                && !error.isMissingNode()
                && !(error.isTextual() && !StringUtils.hasText(error.asText()));
    }

    private static String text(JsonNode node, String field) {
        return node == null ? null : normalize(node.path(field).asText(null));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
