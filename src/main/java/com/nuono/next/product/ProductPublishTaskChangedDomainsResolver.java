package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.util.StringUtils;

class ProductPublishTaskChangedDomainsResolver implements Function<ProductPublishTaskRecord, List<String>> {

    private final ObjectMapper objectMapper;
    private final ChangedDomainsRecomputer changedDomainsRecomputer;

    ProductPublishTaskChangedDomainsResolver(
            ObjectMapper objectMapper,
            ChangedDomainsRecomputer changedDomainsRecomputer
    ) {
        this.objectMapper = objectMapper;
        this.changedDomainsRecomputer = changedDomainsRecomputer;
    }

    @Override
    public List<String> apply(ProductPublishTaskRecord task) {
        return resolve(task);
    }

    List<String> resolve(ProductPublishTaskRecord task) {
        List<String> domains = parseStringList(task != null ? task.getChangedDomainsJson() : null);
        boolean needsRecompute = domains.isEmpty()
                || domains.stream().anyMatch((domain) -> "unknown".equalsIgnoreCase(normalize(domain)));
        if (!needsRecompute || task == null) {
            return domains;
        }
        try {
            ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
            ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
            List<String> recomputedDomains = changedDomainsRecomputer == null
                    ? List.of()
                    : changedDomainsRecomputer.recompute(draft, baseline, task.getCurrentSiteCode());
            return recomputedDomains == null || recomputedDomains.isEmpty() ? domains : recomputedDomains;
        } catch (Exception exception) {
            return domains;
        }
    }

    private ProductMasterSnapshotView readTaskSnapshot(String json) throws Exception {
        return objectMapper.readValue(json, ProductMasterSnapshotView.class);
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> values = new ArrayList<>();
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    if (item != null && item.isTextual()) {
                        values.add(item.asText());
                    }
                }
            }
            return values;
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    interface ChangedDomainsRecomputer {

        List<String> recompute(
                ProductMasterSnapshotView draft,
                ProductMasterSnapshotView baseline,
                String currentSiteCode
        );
    }
}
