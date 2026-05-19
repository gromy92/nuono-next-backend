package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.util.StringUtils;

class SheinProductStructuredDataParser {

    private static final int MAX_SCRIPT_LENGTH = 3 * 1024 * 1024;
    private static final Pattern SHEIN_IMAGE_PATTERN = Pattern.compile(
            "(?:https?:)?//[^\\s\"'<>]+(?:ltwebstatic|shein)[^\\s\"'<>]+\\.(?:jpg|jpeg|png|webp)(?:\\?[^\\s\"'<>]*)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRICE_TEXT_PATTERN = Pattern.compile(
            "(?:(?:SAR|AED|USD|EUR|GBP|\\$|£|€|ر\\.س|د\\.إ)\\s*\\d[\\d,.]*)|(?:\\d[\\d,.]*\\s*(?:SAR|AED|USD|EUR|GBP|ر\\.س|د\\.إ))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> TITLE_KEYS = Set.of(
            "goodsname", "goods_name", "productname", "product_name", "producttitle", "title"
    );
    private static final Set<String> SKU_KEYS = Set.of(
            "goodssn", "goods_sn", "sku", "skucode", "sku_code", "productid", "product_id", "goodsid", "goods_id"
    );
    private static final Set<String> BRAND_KEYS = Set.of("brand", "brandname", "brand_name");
    private static final Set<String> COLOR_KEYS = Set.of("color", "colour", "colorname", "colourname", "color_name", "colour_name");
    private static final Set<String> MATERIAL_KEYS = Set.of("material", "materialname", "material_name");
    private static final Set<String> COMPOSITION_KEYS = Set.of("composition", "fabric", "fabriccomposition");
    private static final Set<String> SIZE_KEYS = Set.of("size", "sizename", "size_name");
    private static final Set<String> DESCRIPTION_KEYS = Set.of(
            "description", "goodsdesc", "goods_desc", "productdescription", "product_description", "detaildescription"
    );
    private static final List<String> SCRIPT_MARKERS = List.of(
            "productIntroData",
            "productIntro",
            "productDetail",
            "goodsInfo",
            "goodsDetail",
            "detailInfo",
            "gbRawData",
            "gbCommonInfo",
            "__INITIAL_STATE__",
            "__NEXT_DATA__"
    );

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;
    private final ObjectMapper objectMapper;

    SheinProductStructuredDataParser(
            ProductSelectionSourceCollectionHtmlParser htmlParser,
            ObjectMapper objectMapper
    ) {
        this.htmlParser = htmlParser;
        this.objectMapper = objectMapper;
    }

    void parse(Document document, SheinProductSnapshot snapshot) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            parseJsonPayload(script.html(), snapshot);
        }
        for (Element script : document.select("script")) {
            String content = script.html();
            if (!StringUtils.hasText(content) || content.length() > MAX_SCRIPT_LENGTH || !looksLikeSheinProductScript(content)) {
                continue;
            }
            parseScriptJson(content, snapshot);
            collectImageUrlsFromText(content, snapshot);
        }
        collectVisibleImageUrls(document, snapshot);
    }

    void parseJsonPayload(String json, SheinProductSnapshot snapshot) {
        try {
            inspectJsonNode("", objectMapper.readTree(json), snapshot, 0);
        } catch (Exception ignored) {
            // SHEIN embeds mixed JS and JSON. Invalid snippets are ignored and fallback remains available.
        }
    }

    private boolean looksLikeSheinProductScript(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return lower.contains("goods_name")
                || lower.contains("goodsname")
                || lower.contains("productintro")
                || lower.contains("goodssn")
                || lower.contains("goods_sn")
                || lower.contains("ltwebstatic");
    }

    private void parseScriptJson(String content, SheinProductSnapshot snapshot) {
        String trimmed = content.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            parseJsonPayload(trimmed, snapshot);
        }
        for (String marker : SCRIPT_MARKERS) {
            int markerIndex = content.indexOf(marker);
            if (markerIndex < 0) {
                continue;
            }
            String json = extractBalancedJson(content, markerIndex);
            if (StringUtils.hasText(json)) {
                parseJsonPayload(json, snapshot);
            }
        }
    }

    private String extractBalancedJson(String content, int fromIndex) {
        int start = -1;
        char open = '\0';
        for (int index = fromIndex; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '{' || current == '[') {
                start = index;
                open = current;
                break;
            }
            if (current == ';' || current == '\n') {
                return "";
            }
        }
        if (start < 0) {
            return "";
        }
        return findBalancedJsonEnd(content, start, open);
    }

    private String findBalancedJsonEnd(String content, int start, char open) {
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        char quote = '\0';
        boolean escaping = false;
        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == quote) {
                    inString = false;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                inString = true;
                quote = current;
                continue;
            }
            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return content.substring(start, index + 1);
                }
            }
        }
        return "";
    }

    private void inspectJsonNode(String fieldName, JsonNode node, SheinProductSnapshot snapshot, int depth) {
        if (node == null || node.isMissingNode() || node.isNull() || depth > 14) {
            return;
        }
        String normalizedField = normalizeKey(fieldName);
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            inspectScalar(normalizedField, node.asText(), snapshot);
            return;
        }
        if (node.isArray()) {
            inspectArray(normalizedField, node, snapshot, depth);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        inspectSpecPair(node, snapshot);
        inspectProductObject(normalizedField, node, snapshot);
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            inspectJsonNode(field.getKey(), field.getValue(), snapshot, depth + 1);
        }
    }

    private void inspectScalar(String normalizedField, String rawValue, SheinProductSnapshot snapshot) {
        String value = cleanValue(rawValue, 900);
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (looksLikeJsonPayload(value)) {
            parseJsonPayload(value, snapshot);
        }
        if (TITLE_KEYS.contains(normalizedField)) {
            snapshot.setTitle(value);
        } else if (SKU_KEYS.contains(normalizedField)) {
            snapshot.setSku(value);
        } else if (BRAND_KEYS.contains(normalizedField)) {
            snapshot.setBrand(value);
        } else if (COLOR_KEYS.contains(normalizedField)) {
            snapshot.setColor(value);
        } else if (MATERIAL_KEYS.contains(normalizedField)) {
            snapshot.setMaterial(value);
        } else if (COMPOSITION_KEYS.contains(normalizedField)) {
            snapshot.setComposition(value);
        } else if (SIZE_KEYS.contains(normalizedField)) {
            snapshot.setSize(value);
        } else if (DESCRIPTION_KEYS.contains(normalizedField)) {
            snapshot.setDescription(value);
        }
        if (normalizedField.contains("price") || normalizedField.contains("amountwithsymbol")) {
            snapshot.setPrice(normalizePrice(value, ""));
        }
        if (normalizedField.contains("image") || normalizedField.contains("img") || normalizedField.contains("src")) {
            snapshot.addImage(value);
        }
        if (normalizedField.contains("selling") || normalizedField.contains("bullet") || normalizedField.contains("feature")) {
            snapshot.addSellingPoint(value);
        }
    }

    private boolean looksLikeJsonPayload(String value) {
        return value.length() <= MAX_SCRIPT_LENGTH
                && ((value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]")));
    }

    private void inspectArray(String normalizedField, JsonNode node, SheinProductSnapshot snapshot, int depth) {
        boolean imageArray = normalizedField.contains("image") || normalizedField.contains("img");
        boolean sellingArray = normalizedField.contains("selling") || normalizedField.contains("bullet") || normalizedField.contains("feature");
        for (JsonNode item : node) {
            if (imageArray && item.isTextual()) {
                snapshot.addImage(item.asText());
            } else if (sellingArray && item.isTextual()) {
                snapshot.addSellingPoint(item.asText());
            }
            inspectJsonNode(normalizedField, item, snapshot, depth + 1);
        }
    }

    private void inspectSpecPair(JsonNode node, SheinProductSnapshot snapshot) {
        String label = firstNodeText(node, "attr_name", "attrName", "attributeName", "name", "label", "key", "propertyName");
        String value = firstNodeText(node, "attr_value", "attrValue", "attributeValue", "value", "val", "propertyValue");
        if (!StringUtils.hasText(label) || !StringUtils.hasText(value) || isIgnoredSpecLabel(label)) {
            return;
        }
        snapshot.addSpec(label, value);
        String normalizedLabel = normalizeKey(label);
        if (BRAND_KEYS.contains(normalizedLabel)) {
            snapshot.setBrand(value);
        } else if (COLOR_KEYS.contains(normalizedLabel)) {
            snapshot.setColor(value);
        } else if (MATERIAL_KEYS.contains(normalizedLabel)) {
            snapshot.setMaterial(value);
        } else if (COMPOSITION_KEYS.contains(normalizedLabel)) {
            snapshot.setComposition(value);
        } else if (SIZE_KEYS.contains(normalizedLabel)) {
            snapshot.setSize(value);
        }
    }

    private void inspectProductObject(String normalizedField, JsonNode node, SheinProductSnapshot snapshot) {
        boolean productLike = "product".equalsIgnoreCase(node.path("@type").asText())
                || node.has("sku")
                || node.has("offers")
                || node.has("image")
                || node.has("goods_name")
                || node.has("goodsName");
        if (productLike) {
            snapshot.setTitle(firstNodeText(node, "name", "title", "goods_name", "goodsName", "productName"));
            snapshot.setSku(firstNodeText(node, "sku", "goods_sn", "goodsSn", "goods_id", "goodsId", "product_id", "productId"));
            snapshot.setDescription(firstNodeText(node, "description", "goods_desc", "goodsDesc"));
        }
        if (BRAND_KEYS.contains(normalizedField) && node.isObject()) {
            snapshot.setBrand(firstNodeText(node, "name", "brandName", "brand_name", "value"));
        }
        if (normalizedField.contains("price") || "offers".equals(normalizedField)) {
            String amount = firstNodeText(node, "amountWithSymbol", "price", "sale_price", "salePrice", "retailPrice", "value");
            String currency = firstNodeText(node, "priceCurrency", "currency", "currencyCode");
            snapshot.setPrice(normalizePrice(amount, currency));
        }
    }

    private void collectVisibleImageUrls(Document document, SheinProductSnapshot snapshot) {
        for (Element image : document.select("img[src], img[data-src], img[data-lazy-src], img[data-original], img[data-origin-src], img[data-crop-image], source[srcset]")) {
            if (snapshot.imageCount() >= 20) {
                break;
            }
            for (String attribute : List.of("srcset", "src", "data-src", "data-lazy-src", "data-original", "data-origin-src", "data-crop-image")) {
                String value = image.attr(attribute);
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String candidate = "srcset".equals(attribute) ? value.split(",")[0].trim().split("\\s+")[0] : value;
                snapshot.addImage(image.absUrl(attribute));
                snapshot.addImage(candidate);
            }
        }
    }

    private void collectImageUrlsFromText(String content, SheinProductSnapshot snapshot) {
        Matcher matcher = SHEIN_IMAGE_PATTERN.matcher(content);
        while (matcher.find() && snapshot.imageCount() < 20) {
            snapshot.addImage(matcher.group());
        }
    }

    private String firstNodeText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = cleanValue(value.isValueNode() ? value.asText() : value.path("name").asText(), 480);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private String normalizePrice(String amount, String currency) {
        String value = cleanValue(amount, 120);
        String code = cleanValue(currency, 20);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        Matcher matcher = PRICE_TEXT_PATTERN.matcher(value);
        String price = matcher.find() ? htmlParser.compactText(matcher.group()) : value;
        if (StringUtils.hasText(code) && !price.toLowerCase(Locale.ROOT).contains(code.toLowerCase(Locale.ROOT))) {
            return code + " " + price;
        }
        return price;
    }

    private boolean isIgnoredSpecLabel(String label) {
        String normalized = normalizeKey(label);
        return !StringUtils.hasText(normalized)
                || "name".equals(normalized)
                || "title".equals(normalized)
                || "url".equals(normalized)
                || "image".equals(normalized)
                || "description".equals(normalized);
    }

    private String cleanValue(String value, int maxLength) {
        String text = Jsoup.parse(htmlParser.compactText(value)).text();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String normalizeKey(String value) {
        return htmlParser.compactText(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "");
    }
}
