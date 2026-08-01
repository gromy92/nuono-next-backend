package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class WarehouseCurrentRouteVerifier {

    private WarehouseCurrentRouteVerifier() {
    }

    static Map<PurchaseOrderLogisticsQuoteLineRecord, ForwarderRouteRecommendationRecord> requirePurchase(
            ProcurementPurchaseOrderMapper mapper,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        List<Request<PurchaseOrderLogisticsQuoteLineRecord>> requests = safe(lines).stream()
                .map(line -> line == null
                        ? new Request<PurchaseOrderLogisticsQuoteLineRecord>(null, null, null, null, null, null)
                        : new Request<>(line, line.siteCode, line.plannedTransportMode,
                                line.forwarderCode, line.routeCode, line.serviceCode))
                .collect(Collectors.toList());
        return verify(mapper, requests);
    }

    static void requireShipping(
            ProcurementPurchaseOrderMapper mapper,
        List<PurchaseOrderLogisticsQuoteLineRecord> lines,
        List<ShippingOrderSegmentRecord> segments
    ) {
        if (safe(segments).isEmpty()) {
            throw new IllegalArgumentException("承运状态缺失或异常，不能提交发货，请刷新后重试。");
        }
        if (safe(segments).stream().anyMatch(Objects::isNull)) {
            throw unavailable();
        }
        Map<Long, ShippingOrderSegmentRecord> segmentById = safe(segments).stream()
                .filter(segment -> segment.id != null)
                .collect(Collectors.toMap(segment -> segment.id, segment -> segment, (left, ignored) -> left));
        for (PurchaseOrderLogisticsQuoteLineRecord line : safe(lines)) {
            if (line == null) {
                throw unavailable();
            }
            ShippingOrderSegmentRecord segment = segmentById.get(line.shippingOrderSegmentId);
            if (segment == null || !same(line.siteCode, segment.siteCode)
                    || !mode(line.plannedTransportMode).equals(mode(segment.transportMode))) {
                throw unavailable();
            }
        }
        verify(mapper, safe(segments).stream()
                .map(segment -> new Request<>(segment, segment.siteCode, segment.transportMode,
                        segment.forwarderCode, segment.routeCode, segment.serviceCode))
                .collect(Collectors.toList()));
    }

    private static <T> Map<T, ForwarderRouteRecommendationRecord> verify(
            ProcurementPurchaseOrderMapper mapper,
            List<Request<T>> requests
    ) {
        Map<String, LinkedHashSet<String>> sitesByMode = new LinkedHashMap<>();
        for (Request<T> request : requests) {
            request.requireIdentity();
            sitesByMode.computeIfAbsent(request.mode, ignored -> new LinkedHashSet<>()).add(request.site);
        }
        List<ForwarderRouteRecommendationRecord> candidates = new ArrayList<>();
        sitesByMode.forEach((transportMode, sites) -> candidates.addAll(safe(
                mapper.listRouteRecommendationCandidates(new ArrayList<>(sites), transportMode))));
        Map<T, ForwarderRouteRecommendationRecord> result = new IdentityHashMap<>();
        for (Request<T> request : requests) {
            List<ForwarderRouteRecommendationRecord> exact = candidates.stream()
                    .filter(request::matches)
                    .collect(Collectors.toList());
            if (exact.size() != 1) {
                throw unavailable();
            }
            result.put(request.source, exact.get(0));
        }
        return result;
    }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("所选物流渠道已失效或发生变化，请重新选择。");
    }

    private static boolean same(String left, String right) {
        return StringUtils.hasText(left) && normalized(left).equals(normalized(right));
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String mode(String value) {
        String normalized = normalized(value);
        if ("空".equals(normalized) || "空运".equals(normalized)) {
            return "AIR";
        }
        if ("海".equals(normalized) || "海运".equals(normalized)) {
            return "SEA";
        }
        return normalized;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class Request<T> {
        private final T source;
        private final String site;
        private final String mode;
        private final String forwarder;
        private final String route;
        private final String service;

        private Request(T source, String site, String mode, String forwarder, String route, String service) {
            this.source = source;
            this.site = normalized(site);
            this.mode = WarehouseCurrentRouteVerifier.mode(mode);
            this.forwarder = normalized(forwarder);
            this.route = normalized(route);
            this.service = normalized(service);
        }

        private void requireIdentity() {
            if (source == null || site.isEmpty() || mode.isEmpty() || forwarder.isEmpty() || route.isEmpty()) {
                throw unavailable();
            }
        }

        private boolean matches(ForwarderRouteRecommendationRecord candidate) {
            return candidate != null
                    && site.equals(normalized(candidate.siteCode))
                    && mode.equals(WarehouseCurrentRouteVerifier.mode(candidate.transportMode))
                    && forwarder.equals(normalized(candidate.forwarderCode))
                    && route.equals(normalized(candidate.routeCode))
                    && Objects.equals(service, normalized(candidate.serviceCode));
        }
    }
}
