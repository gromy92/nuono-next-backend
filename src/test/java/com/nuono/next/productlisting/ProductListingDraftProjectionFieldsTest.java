package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductListingDraftProjectionFieldsTest {

    @Test
    void extractsSharedProjectionFieldsFromListingDraft() {
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setProductBrand(null);
        draft.setProductBrandCode("fallback-brand");
        draft.setBarcode(null);
        draft.setKeyAttributes(List.of(Map.of(
                "code", "barcode",
                "commonValue", "  6290000000099  "
        )));
        draft.setImageUrls(List.of("  https://example.test/main.jpg  "));
        draft.setSalePrice(new BigDecimal("45.50"));

        ProductListingDraftProjectionFields fields =
                ProductListingDraftProjectionFields.from(draft, "NN-TEST-PSKU");

        assertEquals("NN-TEST-PSKU", fields.partnerSku());
        assertEquals("SELF_BUILT", fields.productSourceType());
        assertEquals("fallback-brand", fields.brand());
        assertEquals("fallback-brand", fields.brandCode());
        assertEquals("6290000000099", fields.barcode());
        assertEquals("Default", fields.sizeEn());
        assertEquals("Default", fields.displaySize());
        assertEquals("https://example.test/main.jpg", fields.coverImageUrl());
        assertEquals("49.9", fields.priceText());
        assertEquals("45.5", fields.salePriceText());
        assertEquals("45.5", fields.finalPriceText());
        assertEquals("listing_sale_price", fields.finalPriceSource());
    }
}
