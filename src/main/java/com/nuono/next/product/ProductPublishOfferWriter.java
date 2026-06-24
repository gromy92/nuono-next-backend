package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductPublishOfferWriter {

    private static final String OFFER_UPSERT_URL =
            NoonProductGateway.OFFER_UPSERT_URL;
    private static final String PRICING_METHOD_MANUAL = "manual";
    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId PRODUCT_MANAGEMENT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_SALE_WINDOW_YEARS = 10;

    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;

    ProductPublishOfferWriter(
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter
    ) {
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
    }

    void publishOffer(
            NoonSession session,
            String pskuCode,
            Map<String, Object> siteOffer,
            List<String> actionWarnings
    ) {
        String resolvedSite = resolveOfferPublishSite(siteOffer);
        ObjectNode body = buildOfferUpsertBody(pskuCode, siteOffer);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Locale", "en-" + resolvedSite.toUpperCase());
        if (session != null && StringUtils.hasText(session.getProjectCode())) {
            headers.put("X-Project", session.getProjectCode());
        }
        productNoonAdapter.postWriteJson(session, OFFER_UPSERT_URL, body, false, headers);
        if (siteOffer.get("fbnStock") != null || siteOffer.get("supermallStock") != null || siteOffer.get("fbpStock") != null) {
            actionWarnings.add("当前页面展示的是库存汇总，本轮发布不会直接改 Noon 仓库库存。");
        }
    }

    ObjectNode buildOfferUpsertBody(String pskuCode, Map<String, Object> siteOffer) {
        requireText(pskuCode, textValue(siteOffer.get("site")) + " / " + siteOfferCode(siteOffer) + " 缺少 pskuCode，暂时不能发布站点经营字段。");
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode pskus = body.putArray("pskus");
        ObjectNode offerNode = pskus.addObject();
        offerNode.put("pskuCode", pskuCode);
        offerNode.put("country", resolveOfferPublishSite(siteOffer).toLowerCase());
        if (hasOfferFieldValue(siteOffer, "isActive")) {
            offerNode.put("isActive", truthy(siteOffer.get("isActive")) ? 1 : 0);
        }
        offerNode.put("pricingMethod", PRICING_METHOD_MANUAL);
        setDecimalNode(offerNode, "price", siteOffer.get("price"));
        setDecimalNode(offerNode, "salePrice", siteOffer.get("salePrice"));
        setDecimalNode(offerNode, "priceMin", siteOffer.get("priceMin"));
        setDecimalNode(offerNode, "priceMax", siteOffer.get("priceMax"));
        Map<String, String> saleWindow = saleWindowForPublish(siteOffer);
        putIfHasText(offerNode, "saleStart", saleWindow.get("saleStart"));
        putIfHasText(offerNode, "saleEnd", saleWindow.get("saleEnd"));
        offerNode.put("idWarranty", parseInteger(siteOffer.get("idWarranty"), 0));
        putIfHasText(offerNode, "offerNote", siteOffer.get("offerNote"));
        offerNode.putNull("pricingRule");
        offerNode.putNull("priceEngineMin");
        offerNode.putNull("priceEngineMax");
        return body;
    }

    Map<String, String> saleWindowForPublish(Map<String, Object> siteOffer) {
        Map<String, String> saleWindow = new LinkedHashMap<>();
        if (siteOffer == null) {
            return saleWindow;
        }

        String saleStart = normalizeOfferDateForNoon(siteOffer.get("saleStart"));
        String saleEnd = normalizeOfferDateForNoon(siteOffer.get("saleEnd"));
        if (asBigDecimal(siteOffer.get("salePrice")) != null) {
            LocalDate today = LocalDate.now(PRODUCT_MANAGEMENT_ZONE);
            if (!StringUtils.hasText(saleStart)) {
                saleStart = today.format(NOON_OFFER_DATE_FORMATTER);
            }
            if (!StringUtils.hasText(saleEnd)) {
                saleEnd = today.plusYears(DEFAULT_SALE_WINDOW_YEARS).format(NOON_OFFER_DATE_FORMATTER);
            }
        }

        if (StringUtils.hasText(saleStart)) {
            saleWindow.put("saleStart", saleStart);
        }
        if (StringUtils.hasText(saleEnd)) {
            saleWindow.put("saleEnd", saleEnd);
        }
        return saleWindow;
    }

    private String resolveOfferPublishSite(Map<String, Object> siteOffer) {
        return firstNonBlank(
                textValue(siteOffer.get("site")),
                deriveSiteFromStoreCode(siteOfferCode(siteOffer)),
                "AE"
        );
    }

    private boolean hasOfferFieldValue(Map<String, Object> siteOffer, String field) {
        if (siteOffer == null || !siteOffer.containsKey(field)) {
            return false;
        }
        Object value = siteOffer.get(field);
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return StringUtils.hasText((String) value);
        }
        return true;
    }

    private String normalizeOfferDateForNoon(Object value) {
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(text).format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private int parseInteger(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void setDecimalNode(ObjectNode target, String key, Object value) {
        BigDecimal decimal = asBigDecimal(value);
        if (decimal == null) {
            target.putNull(key);
            return;
        }
        target.put(key, decimal);
    }

    private void putIfHasText(ObjectNode target, String key, Object value) {
        String text = textValue(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String deriveSiteFromStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode) || storeCode.length() < 2) {
            return null;
        }
        String suffix = storeCode.substring(storeCode.length() - 2).toUpperCase();
        if ("SA".equals(suffix) || "AE".equals(suffix)) {
            return suffix;
        }
        return null;
    }

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
