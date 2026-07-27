package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

public final class ProductListingDryRunFreshness {

    private final ObjectMapper objectMapper;

    public ProductListingDryRunFreshness(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean matches(String currentDraftJson, String dryRunSnapshotJson) {
        if (!StringUtils.hasText(currentDraftJson) || !StringUtils.hasText(dryRunSnapshotJson)) {
            return false;
        }
        try {
            JsonNode current = objectMapper.readTree(currentDraftJson);
            JsonNode snapshot = objectMapper.readTree(dryRunSnapshotJson);
            return current != null && current.equals(snapshot);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }
}
