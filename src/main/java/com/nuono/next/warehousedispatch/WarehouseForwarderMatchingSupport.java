package com.nuono.next.warehousedispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.*;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

abstract class WarehouseForwarderMatchingSupport extends WarehouseForwarderQuoteSupport {

    protected WarehouseForwarderMatchingSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected int quoteTokenIndex(ForwarderRouteQuoteRecord quote, List<String> tokens) {
        for (int index = 0; index < tokens.size(); index += 1) {
            if (quoteMatchesToken(quote, tokens.get(index))) {
                return index;
            }
        }
        return -1;
    }

protected boolean quoteMatchesToken(ForwarderRouteQuoteRecord quote, String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalizedToken = normalizeQuoteText(token);
        String normalizedCode = normalizeQuoteText(quote == null ? null : quote.cargoCategoryCode);
        if (normalizedToken.startsWith("CAT-")) {
            return normalizedCode.endsWith("-" + normalizedToken);
        }
        String normalizedName = normalizeQuoteText(quote == null ? null : quote.cargoCategoryName);
        return normalizedName.contains(normalizedToken) || normalizedCode.contains(normalizedToken);
    }

protected String normalizeQuoteText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

protected boolean containsAny(String value, String... tokens) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalizedValue = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (StringUtils.hasText(token) && normalizedValue.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

protected List<String> routeCodes(Collection<PendingShippingLine> lines) {
        return lines.stream()
                .map(line -> line.routeCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

protected void addUnique(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

protected BigDecimal zeroToNull(BigDecimal value, int scale) {
        if (value == null || value.signum() == 0) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

protected Map<String, Object> shippingLineAssignmentSnapshot(ShippingForwarderAssignment assignment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetForwarderCode", assignment.targetForwarderCode);
        result.put("targetForwarderName", assignment.targetForwarderName);
        if (StringUtils.hasText(assignment.routeCode)) {
            result.put("routeCode", assignment.routeCode);
        }
        if (StringUtils.hasText(assignment.routeName)) {
            result.put("routeName", assignment.routeName);
        }
        if (!StringUtils.hasText(assignment.routeCode)) {
            result.put("warning", "no_route_template");
        }
        return result;
    }

protected String shippingActualTransportMode(String optionType, ShippingBatchSourceRecord source) {
        return normalizeTransportMode(source.plannedTransportMode);
    }

protected ShippingForwarderAssignment shippingForwarderAssignment(
            ShippingOptionDefinition definition,
            String actualTransportMode,
            String siteCode
    ) {
        String forwarderCode = TRANSPORT_AIR.equals(normalizeTransportMode(actualTransportMode))
                ? definition.airForwarderCode
                : definition.seaForwarderCode;
        if ("AUTO_RECOMMEND".equals(definition.optionType)
                && "AE".equalsIgnoreCase(defaultText(siteCode, ""))) {
            forwarderCode = "ET";
        }
        forwarderCode = normalizeForwarderCode(forwarderCode);
        String forwarderName = forwarderName(forwarderCode);
        ForwarderRouteSnapshot route = forwarderRouteSnapshot(forwarderCode, actualTransportMode, siteCode);
        return new ShippingForwarderAssignment(forwarderCode, forwarderName, route.routeCode, route.routeName);
    }

protected ShippingOptionDefinition customShippingOptionDefinition(CreateShippingTargetOptionCommand command) {
        String airForwarderCode = normalizeForwarderCode(command == null ? null : command.airForwarderCode);
        String seaForwarderCode = normalizeForwarderCode(command == null ? null : command.seaForwarderCode);
        if (!StringUtils.hasText(airForwarderCode) || !StringUtils.hasText(seaForwarderCode)) {
            throw new IllegalArgumentException("请选择空运和海运目标货代。");
        }
        if (!isSupportedAirForwarderCode(airForwarderCode)) {
            throw new IllegalArgumentException("空运目标货代暂只支持易通和众鸫。");
        }
        if (!isSupportedSeaForwarderCode(seaForwarderCode)) {
            throw new IllegalArgumentException("海运目标货代暂只支持易通、众鸫和义特。");
        }

        List<String> targetForwarderCodes = new ArrayList<>();
        addUnique(targetForwarderCodes, airForwarderCode);
        addUnique(targetForwarderCodes, seaForwarderCode);
        List<String> targetForwarderNames = targetForwarderCodes.stream()
                .map(this::forwarderName)
                .collect(Collectors.toList());
        String forwarderPlanType = airForwarderCode.equals(seaForwarderCode) ? "SINGLE" : "COMBINATION";
        String optionName = trimToNull(command == null ? null : command.optionName);
        if (!StringUtils.hasText(optionName)) {
            if ("SINGLE".equals(forwarderPlanType)) {
                optionName = "自定义：" + forwarderName(airForwarderCode) + "单货代";
            } else {
                optionName = "自定义：" + forwarderName(airForwarderCode) + "空运 + "
                        + forwarderName(seaForwarderCode) + "海运";
            }
        }
        return new ShippingOptionDefinition(
                "CUSTOM",
                optionName,
                80,
                forwarderPlanType,
                targetForwarderCodes,
                targetForwarderNames,
                false,
                airForwarderCode,
                seaForwarderCode
        );
    }

protected String normalizeForwarderCode(String forwarderCode) {
        String normalized = trimToNull(forwarderCode);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

protected boolean isSupportedAirForwarderCode(String forwarderCode) {
        return "ET".equals(forwarderCode) || "ZD".equals(forwarderCode);
    }

protected boolean isSupportedSeaForwarderCode(String forwarderCode) {
        return "ET".equals(forwarderCode) || "ZD".equals(forwarderCode) || "YT".equals(forwarderCode);
    }

protected String forwarderName(String forwarderCode) {
        if ("ZD".equals(forwarderCode)) {
            return "众鸫";
        }
        if ("ET".equals(forwarderCode)) {
            return "易通";
        }
        if ("YT".equals(forwarderCode)) {
            return "义特";
        }
        return forwarderCode;
    }

protected ForwarderRouteSnapshot forwarderRouteSnapshot(String forwarderCode, String transportMode, String siteCode) {
        String normalizedSiteCode = defaultText(siteCode, "").toUpperCase(Locale.ROOT);
        String normalizedTransportMode = normalizeTransportMode(transportMode);
        if ("ET".equals(forwarderCode)) {
            if ("AE".equals(normalizedSiteCode)) {
                if (TRANSPORT_AIR.equals(normalizedTransportMode)) {
                    return new ForwarderRouteSnapshot(
                            "ET-AE-AIR-WH-20260604",
                            "易通阿联酋空运仓到仓 20260604"
                    );
                }
                if (TRANSPORT_SEA.equals(normalizedTransportMode)) {
                    return new ForwarderRouteSnapshot(
                            "ET-AE-SEA-WH-20260604",
                            "易通阿联酋海运仓到仓 20260604"
                    );
                }
                return new ForwarderRouteSnapshot(null, null);
            }
            if (!"SA".equals(normalizedSiteCode)) {
                return new ForwarderRouteSnapshot(null, null);
            }
            if (TRANSPORT_AIR.equals(normalizedTransportMode)) {
                return new ForwarderRouteSnapshot(
                        "ET-SAU-AIR-FBN-RUH-20260604",
                        "易通沙特空运一档 + 海外仓 + FBN利雅得送仓 20260604"
                );
            }
            return new ForwarderRouteSnapshot(
                    "ET-SAU-SEA-FBN-RUH-20260604",
                    "易通沙特海运 + 海外仓 + FBN利雅得送仓 20260604"
            );
        }
        if (!"SA".equals(normalizedSiteCode)) {
            return new ForwarderRouteSnapshot(null, null);
        }
        if ("ZD".equals(forwarderCode)) {
            if (TRANSPORT_AIR.equals(normalizedTransportMode)) {
                return new ForwarderRouteSnapshot(
                        "ZD-SAU-AIR-FBN-RUH",
                        "众鸫沙特空运专线 FBN利雅得（含送仓报价）"
                );
            }
            return new ForwarderRouteSnapshot(
                    "ZD-SAU-SEA-FBN-RUH",
                    "众鸫沙特海运专线到海外仓 + FBN利雅得送仓"
            );
        }
        if ("YT".equals(forwarderCode) && TRANSPORT_SEA.equals(normalizedTransportMode)) {
            return new ForwarderRouteSnapshot(
                    "YT-SAU-SEA-FBN-RUH",
                    "义特沙特海运双清包税 + FBN利雅得送仓"
            );
        }
        return new ForwarderRouteSnapshot(null, null);
    }
}
