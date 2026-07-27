package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingNoonReadBackAttributeVerifier {

    private static final int MAX_IMAGES = 15;
    private final ProductListingNoonReadBackValueSupport values;

    ProductListingNoonReadBackAttributeVerifier(
            ProductListingNoonReadBackValueSupport values
    ) {
        this.values = values;
    }

    void requireProductFullType(
            List<String> fields,
            ProductListingDraftCommand draft,
            JsonNode common
    ) {
        if (!StringUtils.hasText(draft.getProductFullType())) {
            return;
        }
        String actualCode = values.firstNonBlank(
                values.text(common, "product_fulltype_code"),
                values.text(common, "productFulltypeCode"),
                values.text(common, "product_fulltype"),
                values.text(common, "productFulltype"));
        if (values.sameText(draft.getProductFullType(), actualCode, false)) {
            return;
        }
        String actualId = values.firstNonBlank(
                values.text(common, "id_product_fulltype"),
                values.text(common, "idProductFulltype"),
                values.text(common, "idProductFullType"));
        if (draft.getIdProductFullType() != null
                && String.valueOf(draft.getIdProductFullType()).equals(actualId)) {
            return;
        }
        fields.add("product_fulltype");
    }

    void requireDetailedAttributes(
            List<String> fields,
            ProductListingDraftCommand draft,
            JsonNode en,
            JsonNode ar
    ) {
        if (draft.getKeyAttributes() == null) {
            return;
        }
        for (Map<String, Object> item : draft.getKeyAttributes()) {
            String code = values.normalize(item == null ? null : item.get("code"));
            if (!StringUtils.hasText(code)
                    || isCoreAttribute(code) || isBarcodeAttribute(code)) {
                continue;
            }
            String commonValue = values.normalize(item.get("commonValue"));
            String enValue = values.firstNonBlank(
                    values.normalize(item.get("enValue")), commonValue);
            String arValue = values.firstNonBlank(
                    values.normalize(item.get("arValue")), commonValue);
            values.requireText(
                    fields, "attribute_en_" + code, enValue,
                    values.text(en, code), false, false);
            values.requireText(
                    fields, "attribute_ar_" + code, arValue,
                    values.text(ar, code), false, false);
            String unit = values.normalize(item.get("unit"));
            values.requireText(
                    fields, "attribute_unit_en_" + code, unit,
                    values.text(en, code + "_unit"), false, false);
            values.requireText(
                    fields, "attribute_unit_ar_" + code, unit,
                    values.text(ar, code + "_unit"), false, false);
        }
    }

    void requireHighlights(
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
            if (!values.hasMeaningfulText(value)) {
                continue;
            }
            values.requireText(
                    fields, fieldPrefix + index, value,
                    values.text(actual, "feature_bullet_" + index),
                    false, false);
            index++;
        }
    }

    void requireImages(
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
            if (!values.sameText(
                    value, values.text(common, "image_url_" + index), false)) {
                fields.add("image_url_" + index);
            }
            if (++index > MAX_IMAGES) {
                break;
            }
        }
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
            if ("gtin".equals(token) || "ean".equals(token) || "upc".equals(token)) {
                return true;
            }
        }
        return false;
    }
}
