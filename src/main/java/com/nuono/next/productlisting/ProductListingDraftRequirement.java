package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ProductListingDraftRequirement {

    enum Kind {
        TEXT,
        AMOUNT,
        OPTIONAL_POSITIVE_INTEGER
    }

    private static final List<ProductListingDraftRequirement> REQUIRED_REQUIREMENTS = List.of(
            text("storeCode", ProductListingDraftCommand::getStoreCode),
            text("psku", ProductListingDraftCommand::getPsku),
            text("productFullType", ProductListingDraftCommand::getProductFullType),
            text("productTitleEn", ProductListingDraftCommand::getProductTitleEn),
            amount("price", ProductListingDraftCommand::getPrice),
            amount("purchasePrice", ProductListingDraftCommand::getPurchasePrice),
            text("supplyEvidenceType", ProductListingDraftCommand::getSupplyEvidenceType)
    );

    private static final List<ProductListingDraftRequirement> OPTIONAL_POSITIVE_REQUIREMENTS = List.of(
            optionalPositiveInteger("quantity", ProductListingDraftCommand::getQuantity)
    );

    private final String fieldKey;
    private final Kind kind;
    private final Function<ProductListingDraftCommand, Object> valueAccessor;

    private ProductListingDraftRequirement(
            String fieldKey,
            Kind kind,
            Function<ProductListingDraftCommand, Object> valueAccessor
    ) {
        this.fieldKey = fieldKey;
        this.kind = kind;
        this.valueAccessor = valueAccessor;
    }

    static List<ProductListingDraftRequirement> validationRequirements() {
        List<ProductListingDraftRequirement> requirements = new ArrayList<>(REQUIRED_REQUIREMENTS);
        requirements.addAll(OPTIONAL_POSITIVE_REQUIREMENTS);
        return List.copyOf(requirements);
    }

    static Set<String> requiredFieldKeys() {
        return REQUIRED_REQUIREMENTS.stream()
                .map(ProductListingDraftRequirement::getFieldKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<String> optionalPositiveFieldKeys() {
        return OPTIONAL_POSITIVE_REQUIREMENTS.stream()
                .map(ProductListingDraftRequirement::getFieldKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    String getFieldKey() {
        return fieldKey;
    }

    Kind getKind() {
        return kind;
    }

    Object valueFrom(ProductListingDraftCommand command) {
        return valueAccessor.apply(command);
    }

    private static ProductListingDraftRequirement text(
            String fieldKey,
            Function<ProductListingDraftCommand, Object> valueAccessor
    ) {
        return new ProductListingDraftRequirement(fieldKey, Kind.TEXT, valueAccessor);
    }

    private static ProductListingDraftRequirement amount(
            String fieldKey,
            Function<ProductListingDraftCommand, Object> valueAccessor
    ) {
        return new ProductListingDraftRequirement(fieldKey, Kind.AMOUNT, valueAccessor);
    }

    private static ProductListingDraftRequirement optionalPositiveInteger(
            String fieldKey,
            Function<ProductListingDraftCommand, Object> valueAccessor
    ) {
        return new ProductListingDraftRequirement(fieldKey, Kind.OPTIONAL_POSITIVE_INTEGER, valueAccessor);
    }
}
