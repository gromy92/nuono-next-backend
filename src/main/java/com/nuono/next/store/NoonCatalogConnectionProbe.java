package com.nuono.next.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonCatalogApiRoutes;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Verifies a listing session through the same read-only Catalog offer-list API used by products. */
@Component
@Profile("local-db")
public class NoonCatalogConnectionProbe {

    static final String DEFAULT_OFFER_LIST_URL = NoonCatalogApiRoutes.OFFER_LIST_NOON;

    private final NoonSessionGateway noonSessionGateway;
    private final String offerListUrl;

    public NoonCatalogConnectionProbe(
            NoonSessionGateway noonSessionGateway,
            @Value("${nuono.noon.pull.real-provider.product.offer-list-url:"
                    + DEFAULT_OFFER_LIST_URL + "}") String offerListUrl
    ) {
        this.noonSessionGateway = noonSessionGateway;
        this.offerListUrl = StringUtils.hasText(offerListUrl)
                ? offerListUrl.trim()
                : DEFAULT_OFFER_LIST_URL;
    }

    public JsonNode verify(
            Long ownerUserId,
            String sessionProjectUser,
            String persistedCookie,
            String projectCode,
            String storeCode,
            String siteCode,
            String partnerId
    ) {
        NoonSession session = noonSessionGateway.loginWithPersistedCookie(
                ownerUserId,
                sessionProjectUser,
                persistedCookie,
                projectCode,
                storeCode
        );
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("page", 1);
        body.put("per_page", 1);
        body.put("noon_store_code", storeCode);
        body.put("noonChannelType", "noon");

        String site = normalizeSite(siteCode);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Project", projectCode);
        headers.put("X-Locale", "en-" + site.toLowerCase(Locale.ROOT));
        headers.put("X-Lang", "en");
        headers.put("Country-Code", site);
        if (StringUtils.hasText(partnerId)) {
            headers.put("Id-Partner", partnerId.trim());
        }
        return requireValidCatalogResponse(
                session.postJson(offerListUrl, body, true, headers)
        );
    }

    static JsonNode requireValidCatalogResponse(JsonNode root) {
        JsonNode error = root == null ? null : root.get("error");
        if (error != null && !error.isNull() && !error.isMissingNode()) {
            boolean emptyText = error.isTextual() && !StringUtils.hasText(error.asText());
            if (!emptyText) {
                throw new IllegalStateException("Noon 商品接口返回业务错误。");
            }
        }
        if (root == null
                || !root.path("data").isObject()
                || !root.path("data").path("hits").isArray()) {
            throw new IllegalStateException("Noon 商品接口返回结构异常。");
        }
        return root;
    }

    private String normalizeSite(String siteCode) {
        return StringUtils.hasText(siteCode)
                ? siteCode.trim().toUpperCase(Locale.ROOT)
                : "AE";
    }
}
