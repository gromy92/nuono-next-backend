package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class WarehouseLogisticsQuoteOptionService {

    private static final String AIR = "AIR";
    private static final String SEA = "SEA";
    private static final String PENDING = "PENDING_QUOTE";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String YITE_TEMPLATE = "YITE_B2B_SINGLE_TICKET";
    private static final String ET_TEMPLATE = "ET_SKU_ONE_STEP_PACKING_IMPORT";
    private final ProcurementPurchaseOrderMapper mapper;
    private final WarehouseShippingQuoteChannelService quoteChannelService;
    private final WarehouseForwarderEligibilityService eligibilityService;

    WarehouseLogisticsQuoteOptionService(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseShippingQuoteChannelService quoteChannelService,
            WarehouseForwarderEligibilityService eligibilityService
    ) {
        this.mapper = mapper;
        this.quoteChannelService = quoteChannelService;
        this.eligibilityService = eligibilityService;
    }

    List<LogisticsQuoteExportOption> collect(List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        List<PurchaseOrderLogisticsQuoteLineRecord> routableLines = safe(lines).stream()
                .peek(line -> {
                    line.quoteStatus = quoteStatus(line.quoteStatus);
                    line.plannedTransportMode = transportMode(line.plannedTransportMode);
                })
                .filter(line -> StringUtils.hasText(normalized(line.siteCode)))
                .filter(line -> AIR.equals(line.plannedTransportMode) || SEA.equals(line.plannedTransportMode))
                .collect(Collectors.toList());
        Map<Long, List<PurchaseOrderLogisticsQuoteLineRecord>> confirmations =
                quoteChannelService.loadConfirmations(routableLines);
        Map<String, ProductForwarderTransportEligibilityRecord> eligibilityRules =
                eligibilityService.loadCurrent(routableLines);
        Map<String, List<PurchaseOrderLogisticsQuoteLineRecord>> linesByRoute = routableLines.stream()
                .collect(Collectors.groupingBy(
                        line -> routeKey(line.siteCode, line.plannedTransportMode),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<LogisticsQuoteExportOption> options = new ArrayList<>();
        for (String mode : List.of(SEA, AIR)) {
            List<String> sites = routableLines.stream()
                    .filter(line -> mode.equals(line.plannedTransportMode))
                    .map(line -> normalized(line.siteCode))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (sites.isEmpty()) {
                continue;
            }
            for (ForwarderRouteRecommendationRecord candidate : safe(
                    mapper.listRouteRecommendationCandidates(sites, mode)
            )) {
                List<PurchaseOrderLogisticsQuoteLineRecord> matching = linesByRoute.getOrDefault(
                        routeKey(candidate.siteCode, candidate.transportMode),
                        Collections.emptyList()
                );
                if (!matching.isEmpty()) {
                    options.add(buildOption(candidate, matching, confirmations, eligibilityRules));
                }
            }
        }
        return options.stream()
                .sorted(Comparator
                        .comparing((LogisticsQuoteExportOption option) -> defaultText(
                                option.candidate.forwarderName, option.candidate.forwarderCode))
                        .thenComparing(option -> defaultText(option.candidate.siteCode, ""))
                        .thenComparing(option -> defaultText(option.candidate.transportMode, ""))
                        .thenComparing(option -> defaultText(
                                option.candidate.routeName, option.candidate.routeCode)))
                .collect(Collectors.toList());
    }

    private LogisticsQuoteExportOption buildOption(
            ForwarderRouteRecommendationRecord candidate,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            Map<Long, List<PurchaseOrderLogisticsQuoteLineRecord>> confirmations,
            Map<String, ProductForwarderTransportEligibilityRecord> eligibilityRules
    ) {
        LogisticsQuoteExportOption option = new LogisticsQuoteExportOption();
        option.candidate = candidate;
        option.templateType = templateType(candidate);
        for (PurchaseOrderLogisticsQuoteLineRecord line : lines) {
            PurchaseOrderLogisticsQuoteChannelLineView view = quoteChannelService.resolvePrice(
                    line, candidate, confirmations
            );
            eligibilityService.apply(view, line, candidate, eligibilityRules);
            option.lineQuotes.add(view);
        }
        option.confirmedLineCount = countPrice(option.lineQuotes, true);
        option.pendingLineCount = countPrice(option.lineQuotes, false);
        option.supportedLineCount = countStatus(option.lineQuotes, WarehouseForwarderEligibilityService.SUPPORTED);
        option.inquiryRequiredLineCount = countStatus(
                option.lineQuotes, WarehouseForwarderEligibilityService.INQUIRY_REQUIRED
        );
        option.unsupportedLineCount = countStatus(
                option.lineQuotes, WarehouseForwarderEligibilityService.UNSUPPORTED
        );
        option.newProductLineCount = (int) lines.stream()
                .filter(line -> Boolean.TRUE.equals(line.isNewProduct))
                .count();
        return option;
    }

    private int countPrice(List<PurchaseOrderLogisticsQuoteChannelLineView> lines, boolean priced) {
        return (int) safe(lines).stream()
                .filter(line -> !WarehouseForwarderEligibilityService.UNSUPPORTED.equals(line.eligibilityStatus))
                .filter(line -> priced == positive(line.unitPrice))
                .count();
    }

    private int countStatus(List<PurchaseOrderLogisticsQuoteChannelLineView> lines, String status) {
        return (int) safe(lines).stream()
                .filter(line -> status.equals(line.eligibilityStatus))
                .count();
    }

    private String templateType(ForwarderRouteRecommendationRecord candidate) {
        String code = candidate == null ? null : candidate.forwarderCode;
        String name = candidate == null ? null : candidate.forwarderName;
        if (sameCode(code, "YT") || sameCode(code, "YITE") || contains(name, "义特")) {
            return YITE_TEMPLATE;
        }
        if (sameCode(code, "ET") || sameCode(code, "YITONG") || contains(name, "易通")) {
            return ET_TEMPLATE;
        }
        return null;
    }

    private static String routeKey(String siteCode, String mode) {
        return normalized(siteCode) + ":" + transportMode(mode);
    }

    private static String transportMode(String value) {
        String normalized = normalized(value);
        if (AIR.equals(normalized) || "空".equals(normalized) || "空运".equals(normalized)) {
            return AIR;
        }
        if (SEA.equals(normalized) || "海".equals(normalized) || "海运".equals(normalized)) {
            return SEA;
        }
        return "UNSPECIFIED";
    }

    private static String quoteStatus(String value) {
        String normalized = normalized(value);
        return CONFIRMED.equals(normalized) || "已确认".equals(value) || "确认".equals(value)
                ? CONFIRMED : PENDING;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static boolean sameCode(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean contains(String value, String fragment) {
        return StringUtils.hasText(value) && value.contains(fragment);
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
