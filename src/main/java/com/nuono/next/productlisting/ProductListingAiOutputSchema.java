package com.nuono.next.productlisting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProductListingAiOutputSchema {

    private ProductListingAiOutputSchema() {
    }

    static Map<String, Object> create() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("inputCompleteness", objectSchema(
                List.of("summary", "missingCritical", "missingOptional"),
                Map.of(
                        "summary", stringSchema(),
                        "missingCritical", stringArraySchema(),
                        "missingOptional", stringArraySchema()
                )
        ));
        properties.put("productUnderstanding", objectSchema(
                List.of("productType", "buyerUseCases", "confirmedFacts"),
                Map.of(
                        "productType", stringSchema(),
                        "buyerUseCases", stringArraySchema(),
                        "confirmedFacts", stringArraySchema()
                )
        ));
        properties.put("styleDecision", objectSchema(
                List.of("style", "rationale"),
                Map.of("style", stringSchema(), "rationale", stringSchema())
        ));
        properties.put("keywords", objectSchema(
                List.of("english", "arabic"),
                Map.of("english", stringArraySchema(), "arabic", stringArraySchema())
        ));
        properties.put("attributeGuardrails", objectSchema(
                List.of("confirmedAttributes", "usableSellingPoints", "forbiddenClaims"),
                Map.of(
                        "confirmedAttributes", stringArraySchema(),
                        "usableSellingPoints", stringArraySchema(),
                        "forbiddenClaims", stringArraySchema()
                )
        ));
        properties.put("listingStrategy", objectSchema(
                List.of("english", "arabic"),
                Map.of("english", stringSchema(), "arabic", stringSchema())
        ));
        properties.put("englishListing", listingSchema());
        properties.put("arabicListing", listingSchema());
        properties.put("qualityCheck", objectSchema(
                List.of("score", "findings", "uploadNotes", "removeMarkdownBeforeUpload"),
                Map.of(
                        "score", boundedIntegerSchema(0, 100),
                        "findings", stringArraySchema(),
                        "uploadNotes", stringArraySchema(),
                        "removeMarkdownBeforeUpload", booleanSchema()
                )
        ));
        properties.put("warnings", stringArraySchema());
        properties.put("needsHumanConfirmation", stringArraySchema());
        properties.put("noonUploadDraft", noonUploadDraftSchema());
        return objectSchema(
                List.of(
                        "inputCompleteness",
                        "productUnderstanding",
                        "styleDecision",
                        "keywords",
                        "attributeGuardrails",
                        "listingStrategy",
                        "englishListing",
                        "arabicListing",
                        "qualityCheck",
                        "warnings",
                        "needsHumanConfirmation",
                        "noonUploadDraft"
                ),
                properties
        );
    }

    private static Map<String, Object> listingSchema() {
        return objectSchema(
                List.of("title", "bullets", "longDescription"),
                Map.of(
                        "title", boundedStringSchema(20, 160),
                        "bullets", boundedStringArraySchema(3, 5, 10, 250),
                        "longDescription", boundedStringSchema(250, 4000)
                )
        );
    }

    private static Map<String, Object> noonUploadDraftSchema() {
        return objectSchema(
                List.of(
                        "productTitleEn",
                        "productTitleAr",
                        "productHighlightsEn",
                        "productHighlightsAr",
                        "productDescriptionEn",
                        "productDescriptionAr"
                ),
                Map.of(
                        "productTitleEn", boundedStringSchema(20, 160),
                        "productTitleAr", boundedStringSchema(20, 160),
                        "productHighlightsEn", boundedStringArraySchema(3, 5, 10, 250),
                        "productHighlightsAr", boundedStringArraySchema(3, 5, 10, 250),
                        "productDescriptionEn", boundedStringSchema(250, 4000),
                        "productDescriptionAr", boundedStringSchema(250, 4000)
                )
        );
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", required);
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> stringArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", stringSchema());
        return schema;
    }

    private static Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> boundedStringSchema(int minLength, int maxLength) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("minLength", minLength);
        schema.put("maxLength", maxLength);
        return schema;
    }

    private static Map<String, Object> boundedStringArraySchema(
            int minItems,
            int maxItems,
            int itemMinLength,
            int itemMaxLength
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("minItems", minItems);
        schema.put("maxItems", maxItems);
        schema.put("items", boundedStringSchema(itemMinLength, itemMaxLength));
        return schema;
    }

    private static Map<String, Object> boundedIntegerSchema(int minimum, int maximum) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "integer");
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        return schema;
    }

    private static Map<String, Object> booleanSchema() {
        return Map.of("type", "boolean");
    }
}
