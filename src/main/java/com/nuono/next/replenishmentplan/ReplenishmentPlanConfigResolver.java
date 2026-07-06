package com.nuono.next.replenishmentplan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReplenishmentPlanConfigResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

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
        Optional<OperationConfigTypedVersion> version = resolveCurrentVersion(ownerUserId, storeCode, siteCode);
        return version.map(this::toConfig).orElseGet(ReplenishmentPlanConfig::defaultBasicV1);
    }

    private Optional<OperationConfigTypedVersion> resolveCurrentVersion(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        List<OperationConfigTypedVersion> versions = typedVersionRepository.listVersions();
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        String exactScope = scopeSummary(ownerUserId, storeCode, siteCode);
        OperationConfigTypedVersion exact = null;
        OperationConfigTypedVersion global = null;
        for (OperationConfigTypedVersion version : versions) {
            if (version == null
                    || !OperationConfigVersionType.REPLENISHMENT_PLAN.name().equals(version.getConfigType())
                    || !"CURRENT".equals(version.getStatus())) {
                continue;
            }
            if (exactScope != null && exactScope.equals(version.getScopeSummary())) {
                exact = later(exact, version);
                continue;
            }
            if ("全局当前".equals(version.getScopeSummary())) {
                global = later(global, version);
            }
        }
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(global);
    }

    private ReplenishmentPlanConfig toConfig(OperationConfigTypedVersion version) {
        ReplenishmentPlanConfig defaults = ReplenishmentPlanConfig.defaultBasicV1();
        Map<String, String> values;
        try {
            values = parseValues(version.getContentJson());
        } catch (RuntimeException exception) {
            return defaults;
        }
        try {
            return new ReplenishmentPlanConfig(
                    textOrFallback(version.getVersionNo(), defaults.getVersionNo()),
                    positiveInt(values.get("空运运输天数"), defaults.getAirLeadDays()),
                    positiveInt(values.get("空运覆盖天数"), defaults.getAirCoverDays()),
                    positiveInt(values.get("海运运输天数"), defaults.getSeaLeadDays()),
                    positiveInt(values.get("海运覆盖天数"), defaults.getSeaCoverDays()),
                    positiveInt(values.get("预测窗口天数"), defaults.getForecastHorizonDays()),
                    inventorySources(values.get("库存来源"), defaults.getInventorySources()),
                    booleanValue(values.get("在途必须有 ETA"), defaults.isRequireInboundEtaDate()),
                    booleanValue(values.get("空运只应急"), defaults.isAirEmergencyOnly()),
                    roundingMode(values.get("建议数量取整"), defaults.getRoundingMode())
            );
        } catch (RuntimeException exception) {
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
                    values.put(itemName.trim(), textValue(item, "defaultValue"));
                }
            }
            return values;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("replenishment plan config content parsing failed", exception);
        }
    }

    private static OperationConfigTypedVersion later(
            OperationConfigTypedVersion left,
            OperationConfigTypedVersion right
    ) {
        if (left == null) {
            return right;
        }
        Comparator<OperationConfigTypedVersion> comparator = Comparator
                .comparing(OperationConfigTypedVersion::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(OperationConfigTypedVersion::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private static String scopeSummary(Long ownerUserId, String storeCode, String siteCode) {
        if (ownerUserId == null || !hasText(storeCode) || !hasText(siteCode)) {
            return null;
        }
        return ownerUserId
                + "/"
                + storeCode.trim().toUpperCase(Locale.ROOT)
                + "/"
                + siteCode.trim().toUpperCase(Locale.ROOT);
    }

    private static int positiveInt(String value, int fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(value.trim());
        if (!matcher.find()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(matcher.group());
            return parsed >= 1 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
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
        return fallback;
    }

    private static String roundingMode(String value, String fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "ceil".equals(normalized) ? normalized : fallback;
    }

    private static List<String> inventorySources(String value, List<String> fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        List<String> sources = new ArrayList<>();
        for (String token : value.split("[,，/\\s]+")) {
            if (!hasText(token)) {
                continue;
            }
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (!"FBN".equals(normalized) && !"SUPERMALL".equals(normalized)) {
                return fallback;
            }
            if (!sources.contains(normalized)) {
                sources.add(normalized);
            }
        }
        if (sources.size() == 2 && sources.contains("FBN") && sources.contains("SUPERMALL")) {
            return sources;
        }
        return fallback;
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
