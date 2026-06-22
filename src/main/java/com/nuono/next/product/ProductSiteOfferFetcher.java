package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

class ProductSiteOfferFetcher {

    private static final Logger log = LoggerFactory.getLogger(ProductSiteOfferFetcher.class);
    private static final String PRICING_METHOD_MANUAL = "manual";

    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ProductProjectSiteResolver productProjectSiteResolver;

    ProductSiteOfferFetcher(
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter,
            ProductProjectSiteResolver productProjectSiteResolver
    ) {
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
        this.productProjectSiteResolver = productProjectSiteResolver;
    }

    ProductSiteOfferFetchResult loadSiteOffers(
            NoonSession session,
            List<ProductProjectSiteContext> projectSites,
            String referenceStoreCode,
            String idPartner,
            String partnerSku,
            String pskuCode,
            List<String> warnings
    ) {
        List<Map<String, Object>> siteOffers = new ArrayList<>();
        JsonNode referencePricingNode = MissingNode.getInstance();
        JsonNode referenceStockNode = MissingNode.getInstance();

        if (!StringUtils.hasText(idPartner)) {
            warnings.add("当前商品没有返回 id_partner，价格读取已跳过。");
        }
        if (!StringUtils.hasText(partnerSku)) {
            warnings.add("当前索引缺少 partnerSku，站点价格读取已跳过。");
        }
        if (!StringUtils.hasText(pskuCode)) {
            warnings.add("当前索引缺少 pskuCode，站点库存摘要读取已跳过。");
        }

        for (ProductProjectSiteContext projectSite : projectSites) {
            NoonSession siteSession = session.withStoreCode(projectSite.getStoreCode());
            JsonNode pricingNode = MissingNode.getInstance();
            String resolvedSite = firstNonBlank(
                    projectSite.getSite(),
                    productProjectSiteResolver.deriveSiteFromStoreCode(projectSite.getStoreCode()),
                    "SA"
            );
            if (StringUtils.hasText(idPartner) && StringUtils.hasText(partnerSku)) {
                ObjectNode pricingBody = objectMapper.createObjectNode();
                ArrayNode pskuList = pricingBody.putArray("psku_list");
                ObjectNode pricingItem = pskuList.addObject();
                pricingItem.put("psku", partnerSku);
                pricingItem.put("country_code", resolvedSite.toUpperCase());
                pricingItem.put("id_partner", idPartner);
                long pricingStartedAt = System.nanoTime();
                pricingNode = safePostOptional(
                        siteSession,
                        NoonProductGateway.PRICING_INFO_URL,
                        pricingBody,
                        true,
                        "读取站点 " + productProjectSiteResolver.describeSite(projectSite) + " 价格信息失败"
                );
                log.info(
                        "product-management fetchSnapshot detail stage=pricing.info store={} site={} durationMs={}",
                        projectSite.getStoreCode(),
                        resolvedSite,
                        nanosToMillis(pricingStartedAt)
                );
            }

            JsonNode stockNode = MissingNode.getInstance();
            if (StringUtils.hasText(pskuCode)) {
                ObjectNode stockBody = objectMapper.createObjectNode();
                ArrayNode pskuCodes = stockBody.putArray("psku_codes");
                pskuCodes.add(pskuCode);
                stockBody.put("noon_store_code", projectSite.getStoreCode());
                long stockStartedAt = System.nanoTime();
                stockNode = safePostOptional(
                        siteSession,
                        NoonProductGateway.STOCK_INFO_URL,
                        stockBody,
                        true,
                        "读取站点 " + productProjectSiteResolver.describeSite(projectSite) + " 库存摘要失败"
                );
                log.info(
                        "product-management fetchSnapshot detail stage=stock.info store={} site={} durationMs={}",
                        projectSite.getStoreCode(),
                        resolvedSite,
                        nanosToMillis(stockStartedAt)
                );
            }

            siteOffers.add(buildSiteOffer(
                    projectSite,
                    pricingNode,
                    stockNode,
                    projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)
            ));
            if (projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)) {
                referencePricingNode = pricingNode;
                referenceStockNode = stockNode;
            }
        }

        return new ProductSiteOfferFetchResult(siteOffers, referencePricingNode, referenceStockNode);
    }

    ProductSiteOfferFetchResult reuseSiteOffersFromSnapshot(
            ProductMasterSnapshotView snapshot,
            List<ProductProjectSiteContext> projectSites,
            String referenceStoreCode
    ) {
        List<Map<String, Object>> siteOffers = new ArrayList<>();
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(snapshot != null ? snapshot.getSiteOffers() : null);
        for (ProductProjectSiteContext projectSite : projectSites) {
            Map<String, Object> baselineOffer = baselineOffers.get(projectSite.getStoreCode());
            Map<String, Object> siteOffer = baselineOffer != null
                    ? new LinkedHashMap<>(baselineOffer)
                    : buildSiteOffer(
                    projectSite,
                    MissingNode.getInstance(),
                    MissingNode.getInstance(),
                    projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)
            );
            siteOffers.add(siteOffer);
        }
        return new ProductSiteOfferFetchResult(siteOffers, MissingNode.getInstance(), MissingNode.getInstance());
    }

    Map<String, Object> buildPricing(JsonNode pricingRoot) {
        Map<String, Object> pricing = new LinkedHashMap<>();
        JsonNode pricingItem = firstDataItem(pricingRoot);
        if (pricingItem.isMissingNode()) {
            return pricing;
        }

        putIfNotBlank(pricing, "partnerSku", text(pricingItem, "psku"));
        putIfNotBlank(pricing, "offerCode", text(pricingItem, "offer_code"));
        putIfNotBlank(pricing, "currency", text(pricingItem, "currency"));
        putIfNotBlank(pricing, "barcode", firstNonBlank(
                text(pricingItem, "barcode"),
                text(pricingItem, "gtin"),
                text(pricingItem, "ean"),
                text(pricingItem, "upc")
        ));
        putIfNotNull(pricing, "price", numberOrText(pricingItem.path("price")));
        putIfNotNull(pricing, "salePrice", numberOrText(pricingItem.path("sale_price")));
        putIfNotBlank(pricing, "saleStart", text(pricingItem, "sale_start"));
        putIfNotBlank(pricing, "saleEnd", text(pricingItem, "sale_end"));
        putIfNotNull(pricing, "priceMin", numberOrText(pricingItem.path("price_min")));
        putIfNotNull(pricing, "priceMax", numberOrText(pricingItem.path("price_max")));
        putIfNotNull(
                pricing,
                "finalPrice",
                numberOrText(firstExisting(
                        pricingItem,
                        "final_price",
                        "finalPrice",
                        "final_selling_price",
                        "finalSellingPrice",
                        "selling_price",
                        "sellingPrice",
                        "current_price",
                        "currentPrice",
                        "promo_price",
                        "promoPrice",
                        "promotion_price",
                        "promotionPrice",
                        "deal_price",
                        "dealPrice"
                ))
        );
        putIfNotBlank(
                pricing,
                "finalPriceSource",
                firstNonBlank(text(pricingItem, "final_price_source"), text(pricingItem, "price_source"))
        );
        putIfNotBlank(
                pricing,
                "activePromotionCode",
                firstNonBlank(
                        text(pricingItem, "active_promotion_code"),
                        text(pricingItem, "promotion_code"),
                        text(pricingItem, "promo_code"),
                        text(pricingItem, "campaign_code"),
                        text(pricingItem, "deal_code")
                )
        );
        putIfNotBlank(
                pricing,
                "activePromotionName",
                firstNonBlank(
                        text(pricingItem, "active_promotion_name"),
                        text(pricingItem, "promotion_name"),
                        text(pricingItem, "promo_name"),
                        text(pricingItem, "campaign_name"),
                        text(pricingItem, "deal_name")
                )
        );
        putIfNotBlank(
                pricing,
                "activePromotionUrl",
                firstNonBlank(
                        text(pricingItem, "active_promotion_url"),
                        text(pricingItem, "promotion_url"),
                        text(pricingItem, "promo_url"),
                        text(pricingItem, "campaign_url"),
                        text(pricingItem, "deal_url")
                )
        );
        pricing.put("pricingMethod", PRICING_METHOD_MANUAL);
        putIfNotBlank(pricing, "offerNote", text(pricingItem, "offer_note"));
        if (!pricingItem.path("is_active").isMissingNode() && !pricingItem.path("is_active").isNull()) {
            pricing.put("isActive", pricingItem.path("is_active").asBoolean());
        }
        putIfNotNull(pricing, "idWarranty", numberOrText(pricingItem.path("id_warranty")));
        putIfNotBlank(pricing, "priceSource", text(pricingItem, "price_source"));
        putIfNotNull(pricing, "liveStatus", pricingItem.path("live_status").isBoolean()
                ? pricingItem.path("live_status").asBoolean()
                : null);
        return pricing;
    }

    Map<String, Object> buildStock(JsonNode stockRoot) {
        Map<String, Object> stock = new LinkedHashMap<>();
        JsonNode stockItem = firstDataItem(stockRoot);
        if (stockItem.isMissingNode()) {
            return stock;
        }

        putIfNotBlank(stock, "pskuCode", text(stockItem, "psku_code"));
        putIfNotNull(stock, "fbnStock", numberOrText(stockItem.path("fbn_stock")));
        putIfNotNull(
                stock,
                "supermallStock",
                numberOrText(firstExisting(stockItem, "supermall_stock", "supermall_fbn_stock", "fbn_supermall_stock"))
        );
        putIfNotNull(stock, "fbpStock", numberOrText(stockItem.path("fbp_stock")));
        return stock;
    }

    private Map<String, Object> buildSiteOffer(
            ProductProjectSiteContext projectSite,
            JsonNode pricingRoot,
            JsonNode stockRoot,
            boolean reference
    ) {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        putIfNotBlank(siteOffer, "storeCode", projectSite.getStoreCode());
        putIfNotBlank(siteOffer, "site", projectSite.getSite());
        putIfNotBlank(siteOffer, "statusCode", projectSite.getStatusCode());
        putIfNotNull(siteOffer, "reference", reference);

        Map<String, Object> pricing = buildPricing(pricingRoot);
        Map<String, Object> stock = buildStock(stockRoot);
        siteOffer.putAll(pricing);
        siteOffer.putAll(stock);
        return siteOffer;
    }

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        if (siteOffers == null) {
            return index;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            String storeCode = textValue(siteOffer.get("storeCode"));
            if (StringUtils.hasText(storeCode)) {
                index.put(storeCode, siteOffer);
            }
        }
        return index;
    }

    private JsonNode safePostOptional(
            NoonSession session,
            String url,
            JsonNode body,
            boolean withProject,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.postJson(session, url, body, withProject);
        } catch (IllegalStateException exception) {
            log.warn("{}：{}", warningPrefix, noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private String noonFailureMessage(RuntimeException exception) {
        if (productNoonAdapter == null) {
            return shrink(exception.getMessage());
        }
        return shrink(productNoonAdapter.userMessage(exception));
    }

    private JsonNode firstDataItem(JsonNode root) {
        if (root.isObject() && root.path("data").isArray() && root.path("data").size() > 0) {
            return root.path("data").get(0);
        }
        if (root.isArray() && root.size() > 0) {
            return root.get(0);
        }
        return MissingNode.getInstance();
    }

    private JsonNode firstExisting(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return MissingNode.getInstance();
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return MissingNode.getInstance();
    }

    private Object numberOrText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : MissingNode.getInstance();
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        return value.toString();
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "未返回更多错误信息";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private long nanosToMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
