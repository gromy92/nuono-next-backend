package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.sales.NoonSalesReportBinding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** Read-only Noon Offer List adapter used to prove ASN product identities. */
final class OfficialWarehouseNoonProductIdentityAdapter {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;

    private final OfficialWarehouseNoonInboundClient inboundClient;

    OfficialWarehouseNoonProductIdentityAdapter(OfficialWarehouseNoonInboundClient inboundClient) {
        this.inboundClient = inboundClient;
    }

    List<JsonNode> search(
            NoonSession session,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            String partnerSku
    ) {
        String normalizedPartnerSku = text(partnerSku);
        if (normalizedPartnerSku == null) {
            throw new IllegalArgumentException("官方仓商品预检缺少 partnerSku。");
        }
        List<JsonNode> offers = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.put("page", page);
            body.put("per_page", PAGE_SIZE);
            body.put("noon_store_code", binding.getStoreCode());
            body.put("noonChannelType", "noon");
            body.put("search", normalizedPartnerSku);
            JsonNode root = inboundClient.searchProductOffersPage(session, binding, context, body);
            JsonNode error = root == null ? null : root.get("error");
            if (hasBusinessError(error)) {
                throw new IllegalStateException("Noon 商品身份预检返回业务错误，未创建 ASN。");
            }
            JsonNode data = root == null ? null : root.path("data");
            JsonNode hits = data == null ? null : data.path("hits");
            JsonNode totalNode = data == null ? null : data.get("total");
            if (!validPage(data, hits, totalNode)) {
                throw new IllegalStateException("Noon 商品身份预检返回结构异常，未创建 ASN。");
            }
            hits.forEach(offers::add);
            int total = totalNode.asInt();
            if (hits.isEmpty() && total > 0) {
                throw new IllegalStateException("Noon 商品身份预检分页结果不一致，未创建 ASN。");
            }
            if (hits.isEmpty() || page * PAGE_SIZE >= total) {
                return offers;
            }
        }
        throw new IllegalStateException("Noon 商品身份预检超过安全分页上限，未创建 ASN。");
    }

    private boolean validPage(JsonNode data, JsonNode hits, JsonNode total) {
        return data != null && data.isObject() && hits != null && hits.isArray()
                && total != null && total.isIntegralNumber() && total.canConvertToInt()
                && total.asInt() >= hits.size();
    }

    private boolean hasBusinessError(JsonNode error) {
        return error != null && !error.isNull()
                && !(error.isTextual() && !StringUtils.hasText(error.asText()));
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
