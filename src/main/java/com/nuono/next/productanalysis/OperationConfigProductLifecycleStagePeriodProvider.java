package com.nuono.next.productanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.operationsconfig.OperationConfigDefaultVersionCatalog;
import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class OperationConfigProductLifecycleStagePeriodProvider implements ProductLifecycleStagePeriodProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private final OperationConfigTypedVersionRepository repository;

    public OperationConfigProductLifecycleStagePeriodProvider(OperationConfigTypedVersionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProductLifecycleStagePeriodConfig resolveStagePeriods(ProductLifecycleAnalysisQuery query) {
        Optional<OperationConfigTypedVersion> version = resolveEffectiveVersion(query);
        if (version.isEmpty()) {
            return ProductLifecycleStagePeriodConfig.missingRuleConfig();
        }
        return new ProductLifecycleStagePeriodConfig(parseDurations(version.get().getContentJson()));
    }

    private Optional<OperationConfigTypedVersion> resolveEffectiveVersion(ProductLifecycleAnalysisQuery query) {
        String exactScope = scopeSummary(query);
        OperationConfigTypedVersion exact = null;
        OperationConfigTypedVersion global = null;
        OperationConfigTypedVersion systemDefault = null;
        for (OperationConfigTypedVersion version : repository.listVersions()) {
            if (!OperationConfigVersionType.PRODUCT_LIFECYCLE.name().equals(version.getConfigType())) {
                continue;
            }
            if ("CURRENT".equals(version.getStatus())) {
                if (exactScope != null && exactScope.equals(version.getScopeSummary())) {
                    exact = later(exact, version);
                } else if ("全局当前".equals(version.getScopeSummary())) {
                    global = later(global, version);
                }
                continue;
            }
            if ("SYSTEM_DEFAULT".equals(version.getStatus())
                    && OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO.equals(version.getVersionNo())) {
                systemDefault = later(systemDefault, version);
            }
        }
        if (exact != null) {
            return Optional.of(exact);
        }
        if (global != null) {
            return Optional.of(global);
        }
        return Optional.ofNullable(systemDefault);
    }

    private OperationConfigTypedVersion later(
            OperationConfigTypedVersion left,
            OperationConfigTypedVersion right
    ) {
        if (left == null) {
            return right;
        }
        Comparator<OperationConfigTypedVersion> comparator = Comparator
                .comparing(OperationConfigTypedVersion::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(OperationConfigTypedVersion::getId, Comparator.nullsFirst(Long::compareTo));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private String scopeSummary(ProductLifecycleAnalysisQuery query) {
        if (query == null
                || query.getOwnerUserId() == null
                || !hasText(query.getStoreCode())
                || !hasText(query.getSiteCode())) {
            return null;
        }
        return query.getOwnerUserId()
                + "/" + query.getStoreCode().trim().toUpperCase(Locale.ROOT)
                + "/" + query.getSiteCode().trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Integer> parseDurations(String contentJson) {
        if (!hasText(contentJson)) {
            return Map.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            if (!root.isArray()) {
                return Map.of();
            }
            Map<String, Integer> durations = new HashMap<>();
            for (JsonNode item : root) {
                String itemName = text(item, "itemName");
                if (!isDurationItem(itemName)) {
                    continue;
                }
                String lifecycleCode = lifecycleCode(text(item, "groupName"), itemName);
                Integer durationDays = firstPositiveInteger(text(item, "defaultValue"));
                if (lifecycleCode != null && durationDays != null) {
                    durations.put(lifecycleCode, durationDays);
                }
            }
            return Map.copyOf(durations);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private boolean isDurationItem(String itemName) {
        if (!hasText(itemName)) {
            return false;
        }
        if (itemName.contains("最小周期")) {
            return false;
        }
        return itemName.contains("最长周期")
                || itemName.contains("持续周期")
                || itemName.contains("阶段周期")
                || itemName.contains("持续天数");
    }

    private String lifecycleCode(String groupName, String itemName) {
        String text = (safe(groupName) + " " + safe(itemName)).trim();
        if (text.contains("新品")) {
            return "new";
        }
        if (text.contains("成长")) {
            return "growth";
        }
        if (text.contains("稳定") || text.contains("成熟")) {
            return "stable";
        }
        if (text.contains("衰退")) {
            return "decline";
        }
        if (text.contains("长尾")) {
            return "longTail";
        }
        return null;
    }

    private Integer firstPositiveInteger(String value) {
        if (!hasText(value)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int integer = new BigDecimal(matcher.group()).intValue();
        return integer > 0 ? integer : null;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
