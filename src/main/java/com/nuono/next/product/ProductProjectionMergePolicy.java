package com.nuono.next.product;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ProductProjectionMergePolicy {

    public Map<String, Object> mergeProjectionFields(
            Map<String, Object> existingProjection,
            Map<String, ProductFieldRead<?>> externalReads,
            Set<String> authoritativeEmptyFields
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existingProjection != null) {
            merged.putAll(existingProjection);
        }
        if (externalReads == null || externalReads.isEmpty()) {
            return merged;
        }
        Set<String> emptyFields = authoritativeEmptyFields != null ? authoritativeEmptyFields : Set.of();
        for (Map.Entry<String, ProductFieldRead<?>> entry : externalReads.entrySet()) {
            String field = entry.getKey();
            ProductFieldRead<?> read = entry.getValue();
            if (field == null || read == null) {
                continue;
            }
            if (read.getState() == ProductFieldReadState.READ_VALUE) {
                merged.put(field, read.getValue());
                continue;
            }
            if (read.getState() == ProductFieldReadState.READ_EMPTY && emptyFields.contains(field)) {
                merged.put(field, null);
            }
        }
        return merged;
    }

    public Map<String, ProductFieldRead<?>> readsFromLegacyMap(
            Map<String, Object> incoming,
            Collection<String> fields
    ) {
        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        if (fields == null) {
            return reads;
        }
        for (String field : fields) {
            if (incoming == null || !incoming.containsKey(field)) {
                reads.put(field, ProductFieldRead.absent());
            } else {
                reads.put(field, ProductFieldRead.value(incoming.get(field)));
            }
        }
        return reads;
    }
}
