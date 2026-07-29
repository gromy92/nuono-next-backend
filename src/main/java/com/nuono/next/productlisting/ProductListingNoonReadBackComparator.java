package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.product.NoonProductListFieldSupport;
import java.util.ArrayList;
import java.util.List;

final class ProductListingNoonReadBackComparator {

    private final ProductListingRealWriteProperties properties;
    private final ProductListingNoonReadBackValueSupport values;
    private final ProductListingNoonReadBackAttributeVerifier attributes;

    ProductListingNoonReadBackComparator(
            ProductListingRealWriteProperties properties,
            ProductListingNoonReadBackValueSupport values
    ) {
        this.properties = properties;
        this.values = values;
        this.attributes = new ProductListingNoonReadBackAttributeVerifier(values);
    }

    List<String> mismatches(
            ProductListingDraftCommand draft,
            List<String> expectedImageValues,
            JsonNode product,
            JsonNode offer,
            JsonNode pricing
    ) {
        List<String> fields = new ArrayList<>();
        JsonNode common = product.path("attributes").path("common");
        JsonNode en = product.path("attributes").path("en");
        JsonNode ar = product.path("attributes").path("ar");

        values.requireText(
                fields, "brand",
                values.firstNonBlank(
                        draft.getProductBrand(), draft.getProductBrandCode()),
                values.text(common, "brand"), true, true);
        attributes.requireProductFullType(fields, draft, common);
        values.requireText(
                fields, "product_title_en", draft.getProductTitleEn(),
                values.text(en, "product_title"), false, false);
        values.requireText(
                fields, "product_title_ar", draft.getProductTitleAr(),
                values.text(ar, "product_title"), false, false);
        values.requireMeaningfulText(
                fields, "long_description_en", draft.getProductDescriptionEn(),
                values.text(en, "long_description"));
        values.requireMeaningfulText(
                fields, "long_description_ar", draft.getProductDescriptionAr(),
                values.text(ar, "long_description"));
        attributes.requireHighlights(
                fields, "feature_bullet_en_",
                draft.getProductHighlightsEn(), en);
        attributes.requireHighlights(
                fields, "feature_bullet_ar_",
                draft.getProductHighlightsAr(), ar);
        attributes.requireDetailedAttributes(fields, draft, en, ar);
        attributes.requireImages(fields, expectedImageValues, common);

        values.requireText(
                fields, "partner_sku", draft.getPsku(),
                values.text(offer, "partner_sku"), true, false);
        if (draft.getBarcode() != null
                && NoonProductListFieldSupport.barcodes(offer).stream()
                .noneMatch(value -> values.sameText(
                        draft.getBarcode(), value, true))) {
            fields.add("barcode");
        }
        values.requireDecimal(fields, "price", draft.getPrice(), pricing.path("price"));
        if (properties.isOfferUpsertEnabled()) {
            values.requireDecimal(
                    fields, "price_min",
                    values.firstNonNull(draft.getPriceMin(), draft.getPrice()),
                    pricing.path("price_min"));
            values.requireDecimal(
                    fields, "price_max",
                    values.firstNonNull(draft.getPriceMax(), draft.getPrice()),
                    pricing.path("price_max"));
            values.requireDecimal(
                    fields, "sale_price", draft.getSalePrice(),
                    pricing.path("sale_price"));
            values.requireOfferDate(
                    fields, "sale_start", values.expectedSaleStart(draft),
                    values.text(pricing, "sale_start"));
            values.requireOfferDate(
                    fields, "sale_end", values.expectedSaleEnd(draft),
                    values.text(pricing, "sale_end"));
        }
        if (!warrantyMatches(draft.getIdWarranty(), pricing)) {
            fields.add("id_warranty");
        }
        if (properties.isOfferUpsertEnabled()
                && properties.isOfferSplitWriteEnabled()) {
            values.requireText(
                    fields, "offer_note", draft.getOfferNote(),
                    values.text(pricing, "offer_note"), false, false);
            if (draft.getIsActive() != null
                    && (!pricing.has("is_active")
                    || draft.getIsActive().booleanValue()
                    != pricing.path("is_active").asBoolean())) {
                fields.add("is_active");
            }
        }
        return fields;
    }

    private boolean warrantyMatches(Integer expected, JsonNode pricing) {
        if (expected == null) {
            return true;
        }
        JsonNode actual = pricing.path("id_warranty");
        if (expected == 0 && (actual.isMissingNode() || actual.isNull())) {
            return true;
        }
        return expected == actual.asInt(Integer.MIN_VALUE);
    }
}
