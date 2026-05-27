package com.nuono.next.operationsconfig;

import com.nuono.next.product.ProductClassificationOptionRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OperationConfigProductDimensionOptionsView {

    private final boolean ready;
    private final String source;
    private final List<ProductClassificationOptionRecord> brands;
    private final List<ProductClassificationOptionRecord> productFulltypes;
    private final List<ProductClassificationOptionRecord> categories;

    public OperationConfigProductDimensionOptionsView(
            boolean ready,
            String source,
            List<ProductClassificationOptionRecord> brands,
            List<ProductClassificationOptionRecord> productFulltypes
    ) {
        this.ready = ready;
        this.source = source;
        this.brands = brands == null ? List.of() : List.copyOf(brands);
        this.productFulltypes = productFulltypes == null ? List.of() : List.copyOf(productFulltypes);
        this.categories = deriveCategories(this.productFulltypes);
    }

    public boolean isReady() { return ready; }
    public String getSource() { return source; }
    public List<ProductClassificationOptionRecord> getBrands() { return brands; }
    public List<ProductClassificationOptionRecord> getProductFulltypes() { return productFulltypes; }
    public List<ProductClassificationOptionRecord> getCategories() { return categories; }

    private static List<ProductClassificationOptionRecord> deriveCategories(
            List<ProductClassificationOptionRecord> productFulltypes
    ) {
        Map<String, ProductClassificationOptionRecord> categoriesByValue = new LinkedHashMap<>();
        for (ProductClassificationOptionRecord fulltype : productFulltypes) {
            String categoryValue = categoryValue(fulltype);
            if (categoryValue == null) {
                continue;
            }
            ProductClassificationOptionRecord category = categoriesByValue.computeIfAbsent(categoryValue, value -> {
                ProductClassificationOptionRecord record = new ProductClassificationOptionRecord();
                record.setValue(value);
                record.setLabel(value);
                return record;
            });
            Integer usageCount = addUsageCount(category.getUsageCount(), fulltype.getUsageCount());
            category.setUsageCount(usageCount);
        }
        return List.copyOf(new ArrayList<>(categoriesByValue.values()));
    }

    private static Integer addUsageCount(Integer current, Integer next) {
        if (current == null && next == null) {
            return null;
        }
        return (current == null ? 0 : current) + (next == null ? 0 : next);
    }

    private static String categoryValue(ProductClassificationOptionRecord fulltype) {
        if (fulltype == null) {
            return null;
        }
        String family = trimToNull(fulltype.getFamily());
        String productType = trimToNull(fulltype.getProductType());
        if (family != null && productType != null) {
            return family + "-" + productType;
        }
        if (family != null) {
            return family;
        }
        String rawValue = trimToNull(fulltype.getValue());
        if (rawValue == null) {
            return null;
        }
        int firstDash = rawValue.indexOf('-');
        int lastDash = rawValue.lastIndexOf('-');
        if (firstDash >= 0 && lastDash > firstDash) {
            return rawValue.substring(0, lastDash);
        }
        return rawValue;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
