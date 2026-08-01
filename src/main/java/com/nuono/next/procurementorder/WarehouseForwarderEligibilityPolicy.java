package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class WarehouseForwarderEligibilityPolicy {

    static final String SUPPORTED = "SUPPORTED";
    static final String INQUIRY_REQUIRED = "INQUIRY_REQUIRED";
    static final String UNSUPPORTED = "UNSUPPORTED";
    static final String UNKNOWN = "UNKNOWN";
    static final Set<String> STATUSES = Set.of(SUPPORTED, INQUIRY_REQUIRED, UNSUPPORTED);

    private WarehouseForwarderEligibilityPolicy() {
    }

    static String key(
            Long ownerUserId,
            Long productVariantId,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        return String.valueOf(ownerUserId)
                + "|" + String.valueOf(productVariantId)
                + "|" + normalized(siteCode)
                + "|" + normalized(forwarderCode)
                + "|" + normalized(transportMode);
    }

    static String key(ProductForwarderTransportEligibilityRecord rule) {
        return key(rule.ownerUserId, rule.productVariantId, rule.siteCode, rule.forwarderCode, rule.transportMode);
    }

    static String normalizeStatus(String value) {
        String status = normalized(value);
        return STATUSES.contains(status) ? status : UNKNOWN;
    }

    static String effectiveStatus(String value) {
        return normalizeStatus(value);
    }

    static String statusFor(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate,
            Map<String, ProductForwarderTransportEligibilityRecord> rules
    ) {
        if (line == null || candidate == null
                || line.ownerUserId == null || line.ownerUserId <= 0
                || line.productVariantId == null || line.productVariantId <= 0) {
            return UNKNOWN;
        }
        String lineSite = normalized(line.siteCode);
        String lineMode = normalized(line.plannedTransportMode);
        String candidateSite = normalized(candidate.siteCode);
        String forwarder = normalized(candidate.forwarderCode);
        String candidateMode = normalized(candidate.transportMode);
        if (!StringUtils.hasText(lineSite) || !StringUtils.hasText(candidateSite)
                || !StringUtils.hasText(forwarder)
                || !("AIR".equals(lineMode) || "SEA".equals(lineMode))
                || !("AIR".equals(candidateMode) || "SEA".equals(candidateMode))
                || !lineSite.equals(candidateSite) || !lineMode.equals(candidateMode)) {
            return UNKNOWN;
        }
        ProductForwarderTransportEligibilityRecord rule = rules == null ? null : rules.get(
                key(line.ownerUserId, line.productVariantId, lineSite, forwarder, lineMode)
        );
        return rule == null ? SUPPORTED : normalizeStatus(rule.eligibilityStatus);
    }

    static String requiredCode(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return normalized(value);
    }

    static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    static int nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    static String blockMessage(List<PurchaseOrderLogisticsQuoteLineRecord> lines, String suffix) {
        List<PurchaseOrderLogisticsQuoteLineRecord> safeLines = safe(lines);
        String products = safeLines.stream()
                .map(line -> firstText(line.partnerSku, line.pskuCode))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .collect(Collectors.joining("、"));
        String more = safeLines.size() > 5 ? "等" + safeLines.size() + "个商品" : "";
        String scope = StringUtils.hasText(products) ? products + more : safeLines.size() + "个商品";
        return scope + suffix;
    }
}
