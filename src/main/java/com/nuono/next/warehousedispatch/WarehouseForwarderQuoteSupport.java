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

abstract class WarehouseForwarderQuoteSupport extends WarehouseDispatchSummarySupport {

    protected WarehouseForwarderQuoteSupport(WarehouseDispatchMapper mapper, ObjectMapper objectMapper) {
        super(mapper, objectMapper);
    }

protected Map<String, List<ForwarderRouteQuoteRecord>> forwarderRouteQuotes(Collection<PendingShippingLine> lines) {
        List<String> routeCodes = routeCodes(lines);
        if (routeCodes.isEmpty()) {
            return Map.of();
        }
        return emptyIfNull(mapper.listForwarderRouteQuotes(routeCodes)).stream()
                .collect(Collectors.groupingBy(
                        quote -> quote.routeCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

protected List<ForwarderRouteQuoteRecord> quotesForRoute(
            Map<String, List<ForwarderRouteQuoteRecord>> routeQuotes,
            String routeCode
    ) {
        if (!StringUtils.hasText(routeCode) || routeQuotes == null || routeQuotes.isEmpty()) {
            return List.of();
        }
        return routeQuotes.getOrDefault(routeCode, List.of());
    }

protected CargoCategoryEstimate inferCargoCategory(List<String> sensitiveReasons) {
        if (sensitiveReasons == null || sensitiveReasons.isEmpty()) {
            return new CargoCategoryEstimate("A", "普货", false);
        }
        String joined = sensitiveReasons.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        boolean reviewRequired = containsAny(joined, "人工确认", "manual", "敏货");
        if (containsAny(joined, "食品", "food", "化妆", "美妆", "cosmetic", "液体", "粉末", "膏体", "酒精", "医疗")) {
            return new CargoCategoryEstimate("D", "特殊敏货", reviewRequired);
        }
        if (containsAny(joined, "刀具", "blade", "品牌", "侵权", "按摩", "摄像", "太阳能", "灯具", "卫浴", "眼镜")) {
            return new CargoCategoryEstimate("C", "需确认敏感货", reviewRequired);
        }
        if (containsAny(joined, "带电", "电器", "电池", "插电", "磁", "battery", "electric", "magnetic")) {
            return new CargoCategoryEstimate("B", "带电带磁类", reviewRequired);
        }
        return new CargoCategoryEstimate("B", "一般敏感货", true);
    }

protected ForwarderRouteQuoteRecord selectRouteQuote(
            List<ForwarderRouteQuoteRecord> quotes,
            CargoCategoryEstimate cargoCategory,
            PendingShippingLine line
    ) {
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }
        List<String> tokens = preferredQuoteTokens(cargoCategory, line);
        return quotes.stream()
                .filter(quote -> quote.minUnitPrice != null && StringUtils.hasText(quote.billingUnit))
                .map(quote -> Map.entry(quote, quoteTokenIndex(quote, tokens)))
                .filter(entry -> entry.getValue() >= 0)
                .min(Comparator
                        .comparingInt((Map.Entry<ForwarderRouteQuoteRecord, Integer> entry) -> entry.getValue())
                        .thenComparing(entry -> entry.getKey().minUnitPrice))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

protected List<String> preferredQuoteTokens(CargoCategoryEstimate cargoCategory, PendingShippingLine line) {
        String cargoCode = cargoCategory == null ? "A" : cargoCategory.code;
        String reasonText = normalizeQuoteText(line.sensitiveReasons().stream().collect(Collectors.joining(" ")));
        if ("ET-SAU-AIR-FBN-RUH-20260604".equals(line.routeCode)) {
            if (containsAny(reasonText, "食品", "food", "大牌", "奢侈", "品牌侵权", "侵权")) {
                return List.of("CAT-GF", "GF类", "CAT-D", "D类", "CAT-STD", "一档");
            }
            if (containsAny(reasonText, "医疗", "眼镜")) {
                return List.of("CAT-E", "E类", "CAT-BC", "BC类", "CAT-STD", "一档");
            }
            if (containsAny(reasonText, "液体", "粉末", "膏体", "凝胶", "化妆", "美妆", "cosmetic")) {
                return List.of("CAT-D", "D类", "CAT-GF", "GF类", "CAT-STD", "一档");
            }
            if (containsAny(reasonText, "电池", "battery", "扫码")) {
                return List.of("CAT-H", "H类", "CAT-BC", "BC类", "CAT-STD", "一档");
            }
            if ("C".equals(cargoCode) || "B".equals(cargoCode)) {
                return List.of("CAT-BC", "BC类", "CAT-B", "B类", "CAT-STD", "一档");
            }
            if ("D".equals(cargoCode)) {
                return List.of("CAT-D", "D类", "CAT-GF", "GF类", "CAT-STD", "一档");
            }
            return List.of("CAT-A", "A类", "CAT-STD", "一档");
        }
        if ("ET-AE-SEA-WH-20260604".equals(line.routeCode)) {
            if (containsAny(reasonText, "特殊敏货", "粉末", "食品", "food", "大牌", "香水", "充电宝", "纯电池", "液体", "膏体", "化妆", "美妆", "cosmetic")
                    || "D".equals(cargoCode)) {
                return List.of("CAT-C", "C类", "1880起");
            }
            if ("B".equals(cargoCode) || "C".equals(cargoCode)) {
                return List.of("CAT-B", "B类", "CAT-C", "C类");
            }
            return List.of("CAT-A", "A类", "普货");
        }
        if ("ZD".equals(line.targetForwarderCode) && TRANSPORT_AIR.equals(line.actualTransportMode)) {
            return "A".equals(cargoCode) ? List.of("CAT-001", "普货") : List.of("CAT-002", "敏货");
        }
        if ("YT-SAU-SEA-FBN-RUH".equals(line.routeCode)) {
            if ("A".equals(cargoCode)) {
                return List.of("CAT-020", "普货");
            }
            if (containsAny(reasonText, "灯", "卫浴", "太阳能")) {
                return List.of("CAT-022", "灯具", "卫浴");
            }
            if (containsAny(reasonText, "带电", "插电", "电器", "电池", "磁")) {
                return List.of("CAT-021", "带电带插带磁");
            }
            if ("D".equals(cargoCode)) {
                return List.of("CAT-028", "敏感货", "CAT-025", "特殊类");
            }
            return List.of("CAT-023", "一般敏感货", "CAT-021");
        }
        if ("ZD-SAU-SEA-FBN-RUH".equals(line.routeCode)) {
            if ("A".equals(cargoCode)) {
                return List.of("CAT-003", "A类");
            }
            if (containsAny(reasonText, "食品", "化妆", "美妆", "液体", "粉末", "膏体")) {
                return List.of("CAT-014", "G类", "CAT-011", "D类");
            }
            if (containsAny(reasonText, "按摩", "医疗")) {
                return List.of("CAT-017", "J类");
            }
            if (containsAny(reasonText, "电器", "插电")) {
                return List.of("CAT-012", "E类");
            }
            if (containsAny(reasonText, "带电", "磁", "电池")) {
                return List.of("CAT-006", "B2类", "CAT-004", "B类");
            }
            if ("C".equals(cargoCode)) {
                return List.of("CAT-009", "C类");
            }
            if ("D".equals(cargoCode)) {
                return List.of("CAT-014", "G类", "CAT-011", "D类");
            }
            return List.of("CAT-004", "B类");
        }
        if ("D".equals(cargoCode) && containsAny(reasonText, "食品", "food", "化妆", "美妆", "cosmetic")) {
            return List.of("CAT-G", "G类", "CAT-D", "D类");
        }
        if ("D".equals(cargoCode)) {
            return List.of("CAT-D", "D类", "CAT-G", "G类");
        }
        if ("C".equals(cargoCode)) {
            return List.of("CAT-C", "C类", "CAT-BC", "BC类");
        }
        if ("B".equals(cargoCode)) {
            return List.of("CAT-B", "B类", "CAT-BC", "BC类");
        }
        return List.of("CAT-A", "A类", "普货", "CAT-STD");
    }
}
