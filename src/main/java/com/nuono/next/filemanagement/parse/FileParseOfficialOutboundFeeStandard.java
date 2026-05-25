package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FileParseOfficialOutboundFeeStandard {

    public static final String LEGACY_OUTBOUND_FEE_RULE = "outbound_fee_rule";
    public static final String SIZE_CLASSIFICATION = "outbound_size_classification_rule";
    public static final String FEE_WEIGHT_SLAB = "outbound_fee_weight_slab_rule";
    public static final String CALCULATION_POLICY = "outbound_fee_calculation_policy";

    private static final List<ItemTypeDefinition> STRUCTURED_ITEM_TYPES = buildStructuredItemTypes();
    private static final Map<String, ItemTypeDefinition> DEFINITIONS_BY_TYPE = buildDefinitionsByType();

    private FileParseOfficialOutboundFeeStandard() {
    }

    public static List<ItemTypeDefinition> structuredItemTypes() {
        return STRUCTURED_ITEM_TYPES;
    }

    public static List<String> structuredItemTypeNames() {
        List<String> names = new ArrayList<>();
        for (ItemTypeDefinition definition : STRUCTURED_ITEM_TYPES) {
            names.add(definition.getItemType());
        }
        return Collections.unmodifiableList(names);
    }

    public static List<String> supportedItemTypeNames() {
        List<String> names = new ArrayList<>();
        names.add(LEGACY_OUTBOUND_FEE_RULE);
        names.addAll(structuredItemTypeNames());
        return Collections.unmodifiableList(names);
    }

    public static ItemTypeDefinition definition(String itemType) {
        ItemTypeDefinition definition = DEFINITIONS_BY_TYPE.get(itemType);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported official outbound fee item type: " + itemType);
        }
        return definition;
    }

    private static Map<String, ItemTypeDefinition> buildDefinitionsByType() {
        Map<String, ItemTypeDefinition> definitions = new LinkedHashMap<>();
        for (ItemTypeDefinition definition : STRUCTURED_ITEM_TYPES) {
            definitions.put(definition.getItemType(), definition);
        }
        return Collections.unmodifiableMap(definitions);
    }

    private static List<ItemTypeDefinition> buildStructuredItemTypes() {
        List<ItemTypeDefinition> definitions = new ArrayList<>();
        definitions.add(new ItemTypeDefinition(
                SIZE_CLASSIFICATION,
                "出仓费规格分级",
                list("country", "platform", "fulfillmentType", "classificationName", "effectiveDate"),
                fieldTypes(
                        "country", "string",
                        "platform", "string",
                        "fulfillmentType", "string",
                        "classificationName", "string",
                        "longestSideMaxCm", "decimal",
                        "medianSideMaxCm", "decimal",
                        "shortestSideMaxCm", "decimal",
                        "maxShippingWeightGrams", "decimal",
                        "packagingWeightGrams", "decimal",
                        "priority", "integer",
                        "dimensionUnit", "string",
                        "weightUnit", "string",
                        "effectiveDate", "date",
                        "sourceVersion", "string"
                ),
                list(
                        "country", "platform", "fulfillmentType", "classificationName",
                        "longestSideMaxCm", "medianSideMaxCm", "shortestSideMaxCm",
                        "maxShippingWeightGrams", "packagingWeightGrams", "effectiveDate"
                ),
                list("country", "platform", "fulfillmentType", "classificationName"),
                list(
                        "longestSideMaxCm", "medianSideMaxCm", "shortestSideMaxCm",
                        "maxShippingWeightGrams", "packagingWeightGrams", "priority",
                        "dimensionUnit", "weightUnit", "effectiveDate", "sourceVersion"
                )
        ));
        definitions.add(new ItemTypeDefinition(
                FEE_WEIGHT_SLAB,
                "出仓费重量费用",
                list(
                        "country", "platform", "fulfillmentType", "classificationName",
                        "weightMinGrams", "weightMinInclusive", "weightMaxGrams", "weightMaxInclusive",
                        "currency", "effectiveDate"
                ),
                fieldTypes(
                        "country", "string",
                        "platform", "string",
                        "fulfillmentType", "string",
                        "classificationName", "string",
                        "weightMinGrams", "decimal",
                        "weightMinInclusive", "boolean",
                        "weightMaxGrams", "decimal",
                        "weightMaxInclusive", "boolean",
                        "standardFeeAmount", "decimal",
                        "highAspFeeAmount", "decimal",
                        "salesPriceThresholdAmount", "decimal",
                        "thresholdCurrency", "string",
                        "extraWeightStepGrams", "decimal",
                        "extraFeeAmount", "decimal",
                        "currency", "string",
                        "effectiveDate", "date",
                        "sourceVersion", "string"
                ),
                list(
                        "country", "platform", "fulfillmentType", "classificationName",
                        "weightMinGrams", "weightMaxGrams", "standardFeeAmount", "highAspFeeAmount",
                        "currency", "salesPriceThresholdAmount", "thresholdCurrency",
                        "extraWeightStepGrams", "extraFeeAmount", "effectiveDate"
                ),
                list(
                        "country", "platform", "fulfillmentType", "classificationName",
                        "weightMaxGrams", "standardFeeAmount", "currency"
                ),
                list(
                        "weightMinGrams", "weightMinInclusive", "weightMaxGrams", "weightMaxInclusive",
                        "standardFeeAmount", "highAspFeeAmount", "salesPriceThresholdAmount",
                        "thresholdCurrency", "extraWeightStepGrams", "extraFeeAmount",
                        "currency", "effectiveDate", "sourceVersion"
                )
        ));
        definitions.add(new ItemTypeDefinition(
                CALCULATION_POLICY,
                "出仓费计算策略",
                list("country", "platform", "fulfillmentType", "effectiveDate"),
                fieldTypes(
                        "country", "string",
                        "platform", "string",
                        "fulfillmentType", "string",
                        "policyName", "string",
                        "shippingWeightFormula", "string",
                        "dimensionSortRule", "string",
                        "weightBoundaryRule", "string",
                        "roundingRule", "string",
                        "salesPriceThresholdAmount", "decimal",
                        "thresholdCurrency", "string",
                        "dimensionUnit", "string",
                        "weightUnit", "string",
                        "effectiveDate", "date",
                        "sourceVersion", "string"
                ),
                list(
                        "country", "platform", "fulfillmentType", "shippingWeightFormula",
                        "dimensionSortRule", "weightBoundaryRule", "roundingRule",
                        "salesPriceThresholdAmount", "thresholdCurrency", "effectiveDate"
                ),
                list("country", "platform", "fulfillmentType", "shippingWeightFormula"),
                list(
                        "policyName", "shippingWeightFormula", "dimensionSortRule", "weightBoundaryRule",
                        "roundingRule", "salesPriceThresholdAmount", "thresholdCurrency",
                        "dimensionUnit", "weightUnit", "effectiveDate", "sourceVersion"
                )
        ));
        return Collections.unmodifiableList(definitions);
    }

    private static List<String> list(String... values) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, values);
        return Collections.unmodifiableList(list);
    }

    private static Map<String, String> fieldTypes(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("fieldTypes requires key/value pairs");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put(keyValues[i], keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(fields);
    }

    public static final class ItemTypeDefinition {

        private final String itemType;
        private final String label;
        private final List<String> naturalKeyFields;
        private final Map<String, String> fieldTypes;
        private final List<String> displayColumns;
        private final List<String> requiredFields;
        private final List<String> compareFields;

        private ItemTypeDefinition(
                String itemType,
                String label,
                List<String> naturalKeyFields,
                Map<String, String> fieldTypes,
                List<String> displayColumns,
                List<String> requiredFields,
                List<String> compareFields
        ) {
            this.itemType = itemType;
            this.label = label;
            this.naturalKeyFields = naturalKeyFields;
            this.fieldTypes = fieldTypes;
            this.displayColumns = displayColumns;
            this.requiredFields = requiredFields;
            this.compareFields = compareFields;
        }

        public String getItemType() {
            return itemType;
        }

        public String getLabel() {
            return label;
        }

        public List<String> getNaturalKeyFields() {
            return naturalKeyFields;
        }

        public Map<String, String> getFieldTypes() {
            return fieldTypes;
        }

        public List<String> getDisplayColumns() {
            return displayColumns;
        }

        public List<String> getRequiredFields() {
            return requiredFields;
        }

        public List<String> getCompareFields() {
            return compareFields;
        }
    }
}
