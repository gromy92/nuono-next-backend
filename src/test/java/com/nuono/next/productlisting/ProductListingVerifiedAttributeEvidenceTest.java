package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductListingVerifiedAttributeEvidenceTest {

    @Test
    void shouldExposeOnlySelectedValuesAndExcludeAttributeOptionDictionaries() {
        List<Map<String, Object>> evidence = ProductListingVerifiedAttributeEvidence.selectedAttributes(List.of(
                Map.of(
                        "code", "colour_family",
                        "commonValue", "black",
                        "options", List.of(Map.of("value", "blue", "en", "Blue"))
                ),
                Map.of(
                        "code", "lighting_technology",
                        "options", List.of(Map.of("value", "led", "en", "LED", "ar", "LED"))
                ),
                Map.of("code", "mpn", "enValue", "MILKYWAYA04")
        ));

        assertEquals(2, evidence.size());
        assertEquals(Map.of("code", "colour_family", "commonValue", "black"), evidence.get(0));
        assertEquals(Map.of("code", "mpn", "enValue", "MILKYWAYA04"), evidence.get(1));
        assertFalse(evidence.toString().contains("options"));
        assertFalse(evidence.toString().contains("LED"));
    }

    @Test
    void shouldAcceptSelectedValueFragmentsButRejectOptionDictionaryFragments() {
        List<Map<String, Object>> attributes = List.of(
                Map.of("code", "colour_family", "commonValue", "black"),
                Map.of("code", "lighting_technology", "options", List.of(
                        Map.of("value", "led", "en", "LED", "ar", "LED")
                ))
        );

        assertTrue(ProductListingVerifiedAttributeEvidence.containsSource(
                attributes,
                "\"commonValue\":\"black\""
        ));
        assertFalse(ProductListingVerifiedAttributeEvidence.containsSource(
                attributes,
                "\"value\":\"led\",\"en\":\"LED\",\"ar\":\"LED\""
        ));
    }

    @Test
    void shouldExcludeTitleAndClassificationFieldsFromProtectedFactEvidence() {
        List<Map<String, Object>> evidence = ProductListingVerifiedAttributeEvidence.selectedAttributes(List.of(
                Map.of("code", "product_title", "enValue", "Galaxy Projector Night Light"),
                Map.of("code", "family", "commonValue", "home_decor"),
                Map.of("code", "product_type", "commonValue", "lighting"),
                Map.of("code", "product_subtype", "commonValue", "table_lamps"),
                Map.of("code", "colour_family", "commonValue", "black")
        ));

        assertEquals(List.of(Map.of("code", "colour_family", "commonValue", "black")), evidence);
    }
}
