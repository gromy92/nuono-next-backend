package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class WarehouseShippingQuoteChannelIdentity {

    private static final String PENDING = "PENDING_QUOTE";
    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";
    private static final String SUBMITTED = "SUBMITTED";
    private static final String YITE_FORWARDER_CODE = "YT";

    private WarehouseShippingQuoteChannelIdentity() {
    }

    static boolean matches(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        return candidate != null && matches(
                line,
                candidate.forwarderCode,
                candidate.routeCode,
                candidate.serviceCode
        );
    }

    static boolean matches(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ShippingOrderSegmentRecord segment
    ) {
        return segment != null && matches(
                line,
                segment.forwarderCode,
                segment.routeCode,
                segment.serviceCode
        );
    }

    static boolean matches(
            PurchaseOrderLogisticsQuoteLineRecord line,
            String forwarderCode,
            String routeCode,
            String serviceCode
    ) {
        return line != null
                && sameCode(line.forwarderCode, forwarderCode)
                && sameCode(line.routeCode, routeCode)
                && sameNullableCode(line.serviceCode, serviceCode);
    }

    static void applyChannel(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        applyChannel(
                line,
                candidate.forwarderCode,
                defaultText(candidate.forwarderName, candidate.forwarderCode),
                candidate.routeCode,
                candidate.routeName,
                candidate.serviceCode,
                candidate.serviceName
        );
        line.currency = defaultText(candidate.currency, "RMB");
        line.billingUnit = candidate.billingUnit;
    }

    static void applyChannel(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ShippingOrderSegmentRecord segment
    ) {
        applyChannel(
                line,
                segment.forwarderCode,
                segment.forwarderName,
                segment.routeCode,
                segment.routeName,
                segment.serviceCode,
                segment.serviceName
        );
    }

    static ForwarderRouteRecommendationRecord candidateFrom(ShippingOrderSegmentRecord segment) {
        if (segment == null) {
            return null;
        }
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = segment.forwarderCode;
        candidate.forwarderName = segment.forwarderName;
        candidate.routeCode = segment.routeCode;
        candidate.routeName = segment.routeName;
        candidate.serviceCode = segment.serviceCode;
        candidate.serviceName = segment.serviceName;
        candidate.siteCode = segment.siteCode;
        candidate.transportMode = segment.transportMode;
        return candidate;
    }

    static ForwarderRouteRecommendationRecord candidateFrom(PurchaseOrderLogisticsQuoteLineRecord line) {
        if (line == null) {
            return null;
        }
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = line.forwarderCode;
        candidate.forwarderName = line.forwarderName;
        candidate.routeCode = line.routeCode;
        candidate.routeName = line.routeName;
        candidate.serviceCode = line.serviceCode;
        candidate.serviceName = line.serviceName;
        candidate.siteCode = line.siteCode;
        candidate.transportMode = line.plannedTransportMode;
        return candidate;
    }

    static void applyChannel(
            PurchaseOrderLogisticsQuoteLineRecord line,
            String forwarderCode,
            String forwarderName,
            String routeCode,
            String routeName,
            String serviceCode,
            String serviceName
    ) {
        line.forwarderCode = forwarderCode;
        line.forwarderName = forwarderName;
        line.routeCode = routeCode;
        line.routeName = routeName;
        line.serviceCode = serviceCode;
        line.serviceName = serviceName;
    }

    static boolean isYite(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ShippingOrderSegmentRecord segment
    ) {
        return YITE_FORWARDER_CODE.equalsIgnoreCase(defaultText(
                segment == null ? null : segment.forwarderCode,
                line == null ? null : line.forwarderCode
        ));
    }

    static boolean isZd(PurchaseOrderLogisticsQuoteLineRecord line) {
        return line != null && isZd(line.forwarderCode, line.routeCode);
    }

    static boolean isZd(ShippingOrderSegmentRecord segment) {
        return segment != null && isZd(segment.forwarderCode, segment.routeCode);
    }

    static String normalizeStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : PENDING;
    }

    static String normalizeSubmitStatus(String value) {
        return SUBMITTED.equalsIgnoreCase(defaultText(value, "")) ? SUBMITTED : NOT_SUBMITTED;
    }

    static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean isZd(String code, String routeCode) {
        return "ZD".equalsIgnoreCase(defaultText(code, ""))
                || defaultText(routeCode, "").toUpperCase(Locale.ROOT).startsWith("ZD-");
    }

    static boolean sameCode(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean sameNullableCode(String left, String right) {
        return defaultText(left, "").equalsIgnoreCase(defaultText(right, ""));
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
