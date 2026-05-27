package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FileParseLogisticsQuoteStandard {

    public static final String LEGACY_CHANNEL_RULE = "logistics_channel_rule";
    public static final String SERVICE_LINE = "logistics_service_line";
    public static final String CARGO_CATEGORY = "logistics_cargo_category";
    public static final String BASE_PRICE = "logistics_base_price";
    public static final String SURCHARGE = "logistics_surcharge";
    public static final String BILLING_RULE = "logistics_billing_rule";
    public static final String WAREHOUSE_SERVICE_FEE = "logistics_warehouse_service_fee";
    public static final String RESTRICTION = "logistics_restriction";

    private static final List<ItemTypeDefinition> STRUCTURED_ITEM_TYPES = buildStructuredItemTypes();
    private static final Map<String, ItemTypeDefinition> DEFINITIONS_BY_TYPE = buildDefinitionsByType();

    private FileParseLogisticsQuoteStandard() {
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
        names.add(LEGACY_CHANNEL_RULE);
        names.addAll(structuredItemTypeNames());
        return Collections.unmodifiableList(names);
    }

    public static ItemTypeDefinition definition(String itemType) {
        ItemTypeDefinition definition = DEFINITIONS_BY_TYPE.get(itemType);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported logistics item type: " + itemType);
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
                SERVICE_LINE,
                "物流服务线路",
                list("forwarderCode", "country", "fulfillmentMode", "transportMode", "serviceScope", "destinationNode"),
                fieldTypes(
                        "forwarderCode", "string",
                        "forwarderName", "string",
                        "country", "string",
                        "fulfillmentMode", "string",
                        "destinationNode", "string",
                        "transportMode", "string",
                        "serviceScope", "string",
                        "originWarehouse", "string",
                        "destinationWarehouse", "string",
                        "departureFrequency", "string",
                        "leadTimeText", "string",
                        "leadTimeMinDays", "integer",
                        "leadTimeMaxDays", "integer",
                        "effectiveDate", "date",
                        "sourceVersion", "string"
                ),
                list(
                        "forwarderName", "country", "destinationNode", "transportMode",
                        "serviceScope", "originWarehouse", "destinationWarehouse", "departureFrequency",
                        "leadTimeText", "effectiveDate"
                ),
                list("forwarderCode", "country", "transportMode", "serviceScope", "destinationNode"),
                list(
                        "forwarderName", "country", "destinationNode", "transportMode",
                        "serviceScope", "originWarehouse", "destinationWarehouse", "departureFrequency",
                        "leadTimeText", "leadTimeMinDays", "leadTimeMaxDays", "effectiveDate", "sourceVersion"
                )
        ));
        definitions.add(new ItemTypeDefinition(
                CARGO_CATEGORY,
                "物流货物分类",
                list("forwarderCode", "serviceLineKey", "categoryCode", "categoryName"),
                fieldTypes(
                        "forwarderCode", "string",
                        "serviceLineKey", "string",
                        "categoryCode", "string",
                        "categoryName", "string",
                        "productExamples", "string",
                        "keywords", "string",
                        "electricType", "string",
                        "sensitiveTags", "string",
                        "packingPolicy", "string",
                        "manualConfirmRequired", "boolean"
                ),
                list(
                        "serviceLineKey", "categoryCode", "categoryName", "productExamples", "electricType",
                        "sensitiveTags", "packingPolicy", "manualConfirmRequired"
                ),
                list("forwarderCode", "categoryName"),
                list("categoryName", "productExamples", "keywords", "electricType", "sensitiveTags", "packingPolicy", "manualConfirmRequired")
        ));
        definitions.add(new ItemTypeDefinition(
                BASE_PRICE,
                "物流基础价格",
                list("forwarderCode", "serviceLineKey", "cargoCategoryKey", "pricingModel", "billingUnit", "priceStatus"),
                fieldTypes(
                        "forwarderCode", "string",
                        "serviceLineKey", "string",
                        "cargoCategoryKey", "string",
                        "unitPrice", "decimal",
                        "currency", "string",
                        "billingUnit", "string",
                        "pricingModel", "string",
                        "minimumBillableUnit", "decimal",
                        "minimumBillableUnitType", "string",
                        "volumeDivisor", "integer",
                        "seaWeightRatio", "string",
                        "roundingRule", "string",
                        "priceStatus", "string",
                        "effectiveDate", "date"
                ),
                list(
                        "serviceLineKey", "cargoCategoryKey", "unitPrice", "currency", "billingUnit",
                        "pricingModel", "minimumBillableUnit", "minimumBillableUnitType", "volumeDivisor",
                        "seaWeightRatio", "roundingRule", "priceStatus", "effectiveDate"
                ),
                list("forwarderCode", "serviceLineKey", "pricingModel", "billingUnit", "currency"),
                list(
                        "unitPrice", "currency", "billingUnit", "pricingModel", "minimumBillableUnit",
                        "minimumBillableUnitType", "volumeDivisor", "seaWeightRatio", "roundingRule",
                        "priceStatus", "effectiveDate"
                )
        ));
        definitions.add(new ItemTypeDefinition(
                SURCHARGE,
                "物流附加费",
                list("forwarderCode", "serviceLineKey", "surchargeName", "triggerCondition"),
                fieldTypes(
                        "forwarderCode", "string",
                        "serviceLineKey", "string",
                        "surchargeName", "string",
                        "surchargeType", "string",
                        "triggerCondition", "string",
                        "amount", "decimal",
                        "rate", "decimal",
                        "currency", "string",
                        "billingUnit", "string",
                        "includedInBasePrice", "boolean"
                ),
                list("serviceLineKey", "surchargeName", "surchargeType", "triggerCondition", "amount", "rate", "currency", "billingUnit", "includedInBasePrice"),
                list("forwarderCode", "surchargeName", "triggerCondition"),
                list("surchargeType", "triggerCondition", "amount", "rate", "currency", "billingUnit", "includedInBasePrice")
        ));
        definitions.add(new ItemTypeDefinition(
                BILLING_RULE,
                "物流计费规则",
                list("forwarderCode", "serviceLineKey", "ruleName", "conditionText", "actionText"),
                fieldTypes(
                        "forwarderCode", "string",
                        "serviceLineKey", "string",
                        "ruleName", "string",
                        "conditionText", "string",
                        "actionText", "string",
                        "operator", "string",
                        "thresholdValue", "decimal",
                        "thresholdUnit", "string",
                        "severity", "string"
                ),
                list("serviceLineKey", "ruleName", "conditionText", "operator", "thresholdValue", "thresholdUnit", "actionText", "severity"),
                list("forwarderCode", "ruleName", "conditionText", "actionText"),
                list("conditionText", "actionText", "operator", "thresholdValue", "thresholdUnit", "severity")
        ));
        definitions.add(new ItemTypeDefinition(
                WAREHOUSE_SERVICE_FEE,
                "海外仓服务费",
                list("forwarderCode", "warehouseNode", "serviceName", "feeType"),
                fieldTypes(
                        "forwarderCode", "string",
                        "country", "string",
                        "warehouseNode", "string",
                        "serviceName", "string",
                        "serviceType", "string",
                        "processingScope", "string",
                        "feeType", "string",
                        "amount", "decimal",
                        "rate", "decimal",
                        "currency", "string",
                        "billingUnit", "string",
                        "conditionText", "string",
                        "freeCondition", "string"
                ),
                list("country", "warehouseNode", "serviceName", "serviceType", "processingScope", "feeType", "amount", "rate", "currency", "billingUnit", "conditionText", "freeCondition"),
                list("forwarderCode", "warehouseNode", "serviceName", "feeType"),
                list("serviceType", "processingScope", "feeType", "amount", "rate", "currency", "billingUnit", "conditionText", "freeCondition")
        ));
        definitions.add(new ItemTypeDefinition(
                RESTRICTION,
                "物流禁限运与合规",
                list("forwarderCode", "serviceLineKey", "restrictionType", "itemText", "requirementText"),
                fieldTypes(
                        "forwarderCode", "string",
                        "serviceLineKey", "string",
                        "restrictionType", "string",
                        "itemText", "string",
                        "requirementText", "string",
                        "applicabilityScope", "string",
                        "severity", "string",
                        "manualConfirmRequired", "boolean"
                ),
                list("serviceLineKey", "restrictionType", "itemText", "requirementText", "applicabilityScope", "severity", "manualConfirmRequired"),
                list("forwarderCode", "restrictionType", "itemText"),
                list("requirementText", "applicabilityScope", "severity", "manualConfirmRequired")
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
