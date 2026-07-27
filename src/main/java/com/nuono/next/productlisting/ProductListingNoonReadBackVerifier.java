package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.product.NoonProductListFieldSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingNoonReadBackVerifier {

    private static final int MAX_IMAGES = 15;
    private static final int DEFAULT_SALE_WINDOW_YEARS = 20;

    private final ObjectMapper objectMapper;
    private final ProductListingRealWriteProperties properties;

    ProductListingNoonReadBackVerifier(
            ObjectMapper objectMapper,
            ProductListingRealWriteProperties properties
    ) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.properties = properties == null
                ? new ProductListingRealWriteProperties()
                : properties;
    }

    ProductListingNoonWriteStepResult verify(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            List<String> expectedImageValues,
            String skuParent,
            String pskuCode,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        int maxAttempts = Math.max(1, properties.getReadBackMaxAttempts());
        long retryDelayMillis =
                Math.max(0L, properties.getReadBackRetryDelayMillis());
        RuntimeException lastException = null;
        List<String> lastMismatches = List.of();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            step.setExternalReference(
                    externalReference(skuParent, pskuCode)
                            + ";readBackAttempts=" + attempt
            );
            try {
                JsonNode product = loadProduct(
                        session,
                        endpoints,
                        skuParent,
                        headers
                );
                JsonNode offer = loadOffer(
                        session,
                        endpoints,
                        draft,
                        binding,
                        headers
                );
                JsonNode pricing = loadPricing(
                        session,
                        endpoints,
                        draft,
                        binding,
                        headers
                );
                List<String> mismatches = mismatches(
                        draft,
                        expectedImageValues,
                        product,
                        offer,
                        pricing
                );
                if (mismatches.isEmpty()) {
                    step.setStatus("succeeded");
                    return step;
                }
                lastMismatches = mismatches;
                lastException = null;
            } catch (RuntimeException exception) {
                if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(
                        exception
                )) {
                    step.setStatus("failed");
                    step.setFailureCode("noon_auth_required");
                    step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Noon authorization is required for listing read-back.");
                    return step;
                }
                lastException = exception;
                lastMismatches = List.of();
            }
            if (attempt < maxAttempts) {
                sleep(retryDelayMillis);
            }
        }
        step.setStatus("failed");
        if (!lastMismatches.isEmpty()) {
            step.setFailureCode("noon_listing_readback_incomplete");
            step.setFailureMessage(
                    "Noon listing read-back missing or mismatched fields: "
                            + String.join(", ", lastMismatches)
            );
            return step;
        }
        step.setFailureCode("noon_listing_readback_failed");
        step.setFailureMessage(lastException != null
                && StringUtils.hasText(lastException.getMessage())
                ? lastException.getMessage()
                : "Noon listing read-back failed.");
        return step;
    }

    private JsonNode loadProduct(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            String skuParent,
            Map<String, String> headers
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("skuParents").add(skuParent);
        body.putArray("attributeCodes");
        JsonNode root = session.postJson(
                endpoints.getRetrieveZskuUrl(),
                body,
                true,
                headers
        );
        JsonNode direct = root.path(skuParent);
        if (!direct.isMissingNode() && !direct.isNull()) {
            return direct;
        }
        JsonNode data = root.path("data");
        JsonNode nested = data.path(skuParent);
        return nested.isMissingNode() ? direct : nested;
    }

    private JsonNode loadOffer(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("per_page", 100);
        body.put("noon_store_code", binding.getStoreCode());
        body.put("noonChannelType", "noon");
        body.put("search", draft.getPsku());
        JsonNode hits = session.postJson(
                endpoints.getOfferListUrl(),
                body,
                true,
                headers
        ).path("data").path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                if (sameText(draft.getPsku(), text(hit, "partner_sku"), true)) {
                    return hit;
                }
            }
        }
        return objectMapper.missingNode();
    }

    private JsonNode loadPricing(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode pskuList = body.putArray("psku_list");
        ObjectNode item = pskuList.addObject();
        item.put("psku", draft.getPsku());
        item.put("country_code", upper(binding.getSiteCode()));
        item.put("id_partner", binding.getPartnerId());
        JsonNode root = session.postJson(
                endpoints.getPricingInformationUrl(),
                body,
                true,
                headers
        );
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0);
        }
        if (root.isArray() && !root.isEmpty()) {
            return root.get(0);
        }
        return objectMapper.missingNode();
    }

    private List<String> mismatches(
            ProductListingDraftCommand draft,
            List<String> expectedImageValues,
            JsonNode product,
            JsonNode offer,
            JsonNode pricing
    ) {
        List<String> fields = new ArrayList<>();
        JsonNode attributes = product.path("attributes");
        JsonNode common = attributes.path("common");
        JsonNode en = attributes.path("en");
        JsonNode ar = attributes.path("ar");

        requireText(
                fields,
                "brand",
                firstNonBlank(
                        draft.getProductBrand(),
                        draft.getProductBrandCode()
                ),
                text(common, "brand"),
                true,
                true
        );
        requireProductFullType(fields, draft, common);
        requireText(
                fields,
                "product_title_en",
                draft.getProductTitleEn(),
                text(en, "product_title"),
                false,
                false
        );
        requireText(
                fields,
                "product_title_ar",
                draft.getProductTitleAr(),
                text(ar, "product_title"),
                false,
                false
        );
        requireMeaningfulText(
                fields,
                "long_description_en",
                draft.getProductDescriptionEn(),
                text(en, "long_description")
        );
        requireMeaningfulText(
                fields,
                "long_description_ar",
                draft.getProductDescriptionAr(),
                text(ar, "long_description")
        );
        requireHighlights(
                fields,
                "feature_bullet_en_",
                draft.getProductHighlightsEn(),
                en
        );
        requireHighlights(
                fields,
                "feature_bullet_ar_",
                draft.getProductHighlightsAr(),
                ar
        );
        requireDetailedAttributes(fields, draft, en, ar);
        requireImages(fields, expectedImageValues, common);

        requireText(
                fields,
                "partner_sku",
                draft.getPsku(),
                text(offer, "partner_sku"),
                true,
                false
        );
        if (StringUtils.hasText(draft.getBarcode())) {
            boolean barcodeFound = NoonProductListFieldSupport.barcodes(offer)
                    .stream()
                    .anyMatch(value -> sameText(
                            draft.getBarcode(),
                            value,
                            true
                    ));
            if (!barcodeFound) {
                fields.add("barcode");
            }
        }
        requireDecimal(fields, "price", draft.getPrice(), pricing.path("price"));
        if (properties.isOfferUpsertEnabled()) {
            requireDecimal(
                    fields,
                    "price_min",
                    firstNonNull(draft.getPriceMin(), draft.getPrice()),
                    pricing.path("price_min")
            );
            requireDecimal(
                    fields,
                    "price_max",
                    firstNonNull(draft.getPriceMax(), draft.getPrice()),
                    pricing.path("price_max")
            );
            requireDecimal(
                    fields,
                    "sale_price",
                    draft.getSalePrice(),
                    pricing.path("sale_price")
            );
            requireOfferDate(
                    fields,
                    "sale_start",
                    expectedSaleStart(draft),
                    text(pricing, "sale_start")
            );
            requireOfferDate(
                    fields,
                    "sale_end",
                    expectedSaleEnd(draft),
                    text(pricing, "sale_end")
            );
        }
        if (draft.getIdWarranty() != null
                && draft.getIdWarranty().intValue()
                != pricing.path("id_warranty").asInt(Integer.MIN_VALUE)) {
            fields.add("id_warranty");
        }
        if (properties.isOfferUpsertEnabled()
                && properties.isOfferSplitWriteEnabled()) {
            requireText(
                    fields,
                    "offer_note",
                    draft.getOfferNote(),
                    text(pricing, "offer_note"),
                    false,
                    false
            );
            if (draft.getIsActive() != null
                    && (!pricing.has("is_active")
                    || draft.getIsActive().booleanValue()
                    != pricing.path("is_active").asBoolean())) {
                fields.add("is_active");
            }
        }
        return fields;
    }

    private void requireProductFullType(
            List<String> fields,
            ProductListingDraftCommand draft,
            JsonNode common
    ) {
        if (!StringUtils.hasText(draft.getProductFullType())) {
            return;
        }
        String actualCode = firstNonBlank(
                text(common, "product_fulltype_code"),
                text(common, "productFulltypeCode"),
                text(common, "product_fulltype"),
                text(common, "productFulltype")
        );
        if (sameText(draft.getProductFullType(), actualCode, false)) {
            return;
        }
        String actualId = firstNonBlank(
                text(common, "id_product_fulltype"),
                text(common, "idProductFulltype"),
                text(common, "idProductFullType")
        );
        if (draft.getIdProductFullType() != null
                && String.valueOf(draft.getIdProductFullType()).equals(actualId)) {
            return;
        }
        fields.add("product_fulltype");
    }

    private void requireDetailedAttributes(
            List<String> fields,
            ProductListingDraftCommand draft,
            JsonNode en,
            JsonNode ar
    ) {
        if (draft.getKeyAttributes() == null) {
            return;
        }
        for (Map<String, Object> item : draft.getKeyAttributes()) {
            String code = normalize(item == null ? null : item.get("code"));
            if (!StringUtils.hasText(code)
                    || isCoreAttribute(code)
                    || isBarcodeAttribute(code)) {
                continue;
            }
            String commonValue = normalize(item.get("commonValue"));
            String enValue = firstNonBlank(
                    normalize(item.get("enValue")),
                    commonValue
            );
            String arValue = firstNonBlank(
                    normalize(item.get("arValue")),
                    commonValue
            );
            requireText(
                    fields,
                    "attribute_en_" + code,
                    enValue,
                    text(en, code),
                    false,
                    false
            );
            requireText(
                    fields,
                    "attribute_ar_" + code,
                    arValue,
                    text(ar, code),
                    false,
                    false
            );
            String unit = normalize(item.get("unit"));
            requireText(
                    fields,
                    "attribute_unit_en_" + code,
                    unit,
                    text(en, code + "_unit"),
                    false,
                    false
            );
            requireText(
                    fields,
                    "attribute_unit_ar_" + code,
                    unit,
                    text(ar, code + "_unit"),
                    false,
                    false
            );
        }
    }

    private void requireHighlights(
            List<String> fields,
            String fieldPrefix,
            List<String> expected,
            JsonNode actual
    ) {
        if (expected == null) {
            return;
        }
        int index = 1;
        for (String value : expected) {
            if (!hasMeaningfulText(value)) {
                continue;
            }
            requireText(
                    fields,
                    fieldPrefix + index,
                    value,
                    text(actual, "feature_bullet_" + index),
                    false,
                    false
            );
            index++;
        }
    }

    private void requireImages(
            List<String> fields,
            List<String> expected,
            JsonNode common
    ) {
        if (expected == null) {
            return;
        }
        int index = 1;
        for (String value : expected) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!sameText(
                    value,
                    text(common, "image_url_" + index),
                    false
            )) {
                fields.add("image_url_" + index);
            }
            index++;
            if (index > MAX_IMAGES) {
                break;
            }
        }
    }

    private void requireMeaningfulText(
            List<String> fields,
            String field,
            String expected,
            String actual
    ) {
        if (hasMeaningfulText(expected)
                && !sameText(expected, actual, false)) {
            fields.add(field);
        }
    }

    private void requireText(
            List<String> fields,
            String field,
            String expected,
            String actual,
            boolean ignoreCase,
            boolean brand
    ) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (brand && sameBrand(expected, actual)) {
            return;
        }
        if (!sameText(expected, actual, ignoreCase)) {
            fields.add(field);
        }
    }

    private void requireDecimal(
            List<String> fields,
            String field,
            BigDecimal expected,
            JsonNode actual
    ) {
        if (expected == null) {
            return;
        }
        BigDecimal actualValue = decimal(actual);
        if (actualValue == null || expected.compareTo(actualValue) != 0) {
            fields.add(field);
        }
    }

    private void requireOfferDate(
            List<String> fields,
            String field,
            String expected,
            String actual
    ) {
        if (StringUtils.hasText(expected)
                && !expected.equals(normalizeOfferDate(actual))) {
            fields.add(field);
        }
    }

    private String expectedSaleStart(ProductListingDraftCommand draft) {
        String explicit = normalizeOfferDate(draft.getSaleStart());
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        return draft.getSalePrice() == null ? null : LocalDate.now().toString();
    }

    private String expectedSaleEnd(ProductListingDraftCommand draft) {
        String explicit = normalizeOfferDate(draft.getSaleEnd());
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String start = expectedSaleStart(draft);
        return StringUtils.hasText(start)
                ? LocalDate.parse(start)
                .plusYears(DEFAULT_SALE_WINDOW_YEARS)
                .toString()
                : null;
    }

    private String normalizeOfferDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Noon listing read-back retry interrupted.",
                    exception
            );
        }
    }

    private boolean sameBrand(String expected, String actual) {
        String left = normalizeBrand(expected);
        String right = normalizeBrand(actual);
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equals(right);
    }

    private boolean sameText(
            String expected,
            String actual,
            boolean ignoreCase
    ) {
        String left = normalizeComparable(expected);
        String right = normalizeComparable(actual);
        if (!StringUtils.hasText(left)) {
            return true;
        }
        if (!StringUtils.hasText(right)) {
            return false;
        }
        return ignoreCase ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replaceAll("\\s+", " ")
                .trim()
                : "";
    }

    private String normalizeBrand(String value) {
        return normalizeComparable(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private boolean hasMeaningfulText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return StringUtils.hasText(value
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .trim());
    }

    private boolean isCoreAttribute(String code) {
        String value = code.toLowerCase(Locale.ROOT);
        return "brand".equals(value)
                || "family".equals(value)
                || "product_type".equals(value)
                || "product_subtype".equals(value)
                || "product_fulltype".equals(value)
                || "item_condition".equals(value)
                || "grade".equals(value)
                || "product_title".equals(value)
                || "long_description".equals(value);
    }

    private boolean isBarcodeAttribute(String code) {
        String value = code.toLowerCase(Locale.ROOT);
        if (value.contains("barcode")) {
            return true;
        }
        for (String token : value.split("[^a-z0-9]+")) {
            if ("gtin".equals(token)
                    || "ean".equals(token)
                    || "upc".equals(token)) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull()
                ? ""
                : value.asText("");
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private BigDecimal firstNonNull(BigDecimal left, BigDecimal right) {
        return left == null ? right : left;
    }

    private String upper(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private String externalReference(String skuParent, String pskuCode) {
        return "skuParent=" + normalize(skuParent)
                + ";pskuCode=" + normalize(pskuCode);
    }
}
