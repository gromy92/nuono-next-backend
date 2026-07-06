package com.nuono.next.replenishmentplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionContentSupport;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReplenishmentPlanConfigResolver {
    private static final Logger log = LoggerFactory.getLogger(ReplenishmentPlanConfigResolver.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> REQUIRED_ITEM_NAMES = List.of(
            "空运运输天数",
            "空运覆盖天数",
            "海运运输天数",
            "海运覆盖天数",
            "预测窗口天数",
            "库存来源",
            "在途必须有 ETA",
            "空运只应急",
            "建议数量取整"
    );

    private final OperationConfigTypedVersionRepository typedVersionRepository;

    @Autowired
    public ReplenishmentPlanConfigResolver(
            ObjectProvider<OperationConfigTypedVersionRepository> typedVersionRepositoryProvider
    ) {
        this(typedVersionRepositoryProvider == null ? null : typedVersionRepositoryProvider.getIfAvailable());
    }

    ReplenishmentPlanConfigResolver(OperationConfigTypedVersionRepository typedVersionRepository) {
        this.typedVersionRepository = typedVersionRepository;
    }

    public ReplenishmentPlanConfig resolve(Long ownerUserId, String storeCode, String siteCode) {
        if (typedVersionRepository == null) {
            return ReplenishmentPlanConfig.defaultBasicV1();
        }
        Optional<OperationConfigTypedVersion> version = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                typedVersionRepository,
                OperationConfigVersionType.REPLENISHMENT_PLAN,
                ownerUserId,
                storeCode,
                siteCode
        );
        return version.map(this::toConfig).orElseGet(ReplenishmentPlanConfig::defaultBasicV1);
    }

    private ReplenishmentPlanConfig toConfig(OperationConfigTypedVersion version) {
        ReplenishmentPlanConfig defaults = ReplenishmentPlanConfig.defaultBasicV1();
        Map<String, String> values;
        try {
            values = parseValues(version.getContentJson());
            validateRequiredItems(values);
        } catch (RuntimeException exception) {
            logInvalidSelectedVersion(version, exception);
            return defaults;
        }
        try {
            return new ReplenishmentPlanConfig(
                    textOrFallback(version.getVersionNo(), defaults.getVersionNo()),
                    positiveInt(requiredValue(values, "空运运输天数"), defaults.getAirLeadDays()),
                    positiveInt(requiredValue(values, "空运覆盖天数"), defaults.getAirCoverDays()),
                    positiveInt(requiredValue(values, "海运运输天数"), defaults.getSeaLeadDays()),
                    positiveInt(requiredValue(values, "海运覆盖天数"), defaults.getSeaCoverDays()),
                    positiveInt(requiredValue(values, "预测窗口天数"), defaults.getForecastHorizonDays()),
                    inventorySources(requiredValue(values, "库存来源"), defaults.getInventorySources()),
                    booleanValue(requiredValue(values, "在途必须有 ETA"), defaults.isRequireInboundEtaDate()),
                    booleanValue(requiredValue(values, "空运只应急"), defaults.isAirEmergencyOnly()),
                    roundingMode(requiredValue(values, "建议数量取整"), defaults.getRoundingMode())
            );
        } catch (RuntimeException exception) {
            logInvalidSelectedVersion(version, exception);
            return defaults;
        }
    }

    private static Map<String, String> parseValues(String contentJson) {
        if (!hasText(contentJson)) {
            return Map.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contentJson);
            if (!root.isArray()) {
                throw new IllegalArgumentException("replenishment plan config content must be an item array");
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String itemName = textValue(item, "itemName");
                if (hasText(itemName)) {
                    String normalizedName = itemName.trim();
                    if (REQUIRED_ITEM_NAMES.contains(normalizedName) && values.containsKey(normalizedName)) {
                        throw new IllegalArgumentException("duplicate required replenishment plan config item: " + normalizedName);
                    }
                    values.put(normalizedName, textValue(item, "defaultValue"));
                }
            }
            return values;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("replenishment plan config content parsing failed", exception);
        }
    }

    private static void validateRequiredItems(Map<String, String> values) {
        for (String itemName : REQUIRED_ITEM_NAMES) {
            if (!values.containsKey(itemName)) {
                throw new IllegalArgumentException("missing required replenishment plan config item: " + itemName);
            }
        }
    }

    private static String requiredValue(Map<String, String> values, String itemName) {
        String value = values.get(itemName);
        if (!hasText(value)) {
            throw new IllegalArgumentException("missing required replenishment plan config value: " + itemName);
        }
        return value;
    }

    private static void logInvalidSelectedVersion(OperationConfigTypedVersion version, RuntimeException exception) {
        log.warn(
                "Invalid replenishment plan config version selected; falling back to hardcoded defaults. versionNo={}, scopeSummary={}, reason={}",
                version == null ? null : version.getVersionNo(),
                version == null ? null : version.getScopeSummary(),
                exception.toString()
        );
    }

    private static int positiveInt(String value, int fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        String trimmed = value.trim();
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException("positive integer expected");
            }
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed >= 1) {
                return parsed;
            }
            throw new IllegalArgumentException("positive integer expected");
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("positive integer expected", exception);
        }
    }

    private static boolean booleanValue(String value, boolean fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("boolean value expected");
    }

    private static String roundingMode(String value, String fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("ceil".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("rounding mode only supports ceil");
    }

    private static List<String> inventorySources(String value, List<String> fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        List<String> sources = new ArrayList<>();
        for (String token : value.split("[,，\\s]+")) {
            if (!hasText(token)) {
                continue;
            }
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (!"FBN".equals(normalized) && !"SUPERMALL".equals(normalized)) {
                throw new IllegalArgumentException("inventory sources only support FBN and SUPERMALL");
            }
            if (!sources.contains(normalized)) {
                sources.add(normalized);
            }
        }
        if (sources.size() == 2 && sources.contains("FBN") && sources.contains("SUPERMALL")) {
            return sources;
        }
        throw new IllegalArgumentException("inventory sources must include FBN and SUPERMALL");
    }

    private static String textOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
