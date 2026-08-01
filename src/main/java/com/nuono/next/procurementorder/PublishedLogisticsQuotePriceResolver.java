package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.util.StringUtils;

final class PublishedLogisticsQuotePriceResolver {

    private PublishedLogisticsQuotePriceResolver() {
    }

    static void hydrate(
            ProcurementPurchaseOrderMapper mapper,
            List<LogisticsQuoteExportOption> options
    ) {
        List<String> headhaulServiceCodes = options.stream()
                .map(option -> option.candidate.serviceCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, List<ForwarderBasePriceRecord>> pricesByService = headhaulServiceCodes.isEmpty()
                ? Collections.emptyMap()
                : safe(mapper.listBasePricesByServiceCodes(headhaulServiceCodes)).stream()
                        .collect(Collectors.groupingBy(
                                price -> price.serviceCode,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<String> routeCodes = options.stream()
                .map(option -> option.candidate.routeCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        List<ForwarderRouteSegmentRecord> routeSegments = routeCodes.isEmpty()
                ? Collections.emptyList()
                : safe(mapper.listRouteSegments(routeCodes));
        Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute = routeSegments.stream()
                .collect(Collectors.groupingBy(
                        segment -> segment.routeCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<String> surchargeServiceCodes = Stream.concat(
                        headhaulServiceCodes.stream(),
                        routeSegments.stream().map(segment -> segment.serviceCode)
                )
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, List<ForwarderTransportFeeRecord>> feesByService = surchargeServiceCodes.isEmpty()
                ? Collections.emptyMap()
                : safe(mapper.listTransportFeesByServiceCodes(surchargeServiceCodes)).stream()
                        .collect(Collectors.groupingBy(
                                fee -> fee.serviceCode,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        for (LogisticsQuoteExportOption option : options) {
            ForwarderRouteRecommendationRecord candidate = option.candidate;
            option.publishedPrices = pricesByService
                    .getOrDefault(candidate.serviceCode, Collections.emptyList())
                    .stream()
                    .map(PublishedLogisticsQuotePriceResolver::toPublishedPriceView)
                    .collect(Collectors.toList());
            LinkedHashSet<String> routeServiceCodes = routeServiceCodes(candidate, segmentsByRoute);
            option.surcharges = routeServiceCodes.stream()
                    .flatMap(serviceCode -> feesByService.getOrDefault(serviceCode, Collections.emptyList()).stream())
                    .filter(fee -> !Boolean.TRUE.equals(fee.includedInBasePrice))
                    .filter(fee -> matchesScope(candidate.targetPlatform, fee.targetPlatform))
                    .filter(fee -> matchesScope(candidate.deliveryCity, fee.deliveryCity))
                    .map(PublishedLogisticsQuotePriceResolver::toSurchargeView)
                    .collect(Collectors.toList());
        }
    }

    private static LinkedHashSet<String> routeServiceCodes(
            ForwarderRouteRecommendationRecord candidate,
            Map<String, List<ForwarderRouteSegmentRecord>> segmentsByRoute
    ) {
        LinkedHashSet<String> serviceCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(candidate.serviceCode)) {
            serviceCodes.add(candidate.serviceCode);
        }
        for (ForwarderRouteSegmentRecord segment :
                segmentsByRoute.getOrDefault(candidate.routeCode, Collections.emptyList())) {
            if (StringUtils.hasText(segment.serviceCode)) {
                serviceCodes.add(segment.serviceCode);
            }
        }
        return serviceCodes;
    }

    private static boolean matchesScope(String selectedValue, String scopedValue) {
        return !StringUtils.hasText(scopedValue)
                || !StringUtils.hasText(selectedValue)
                || scopedValue.trim().equalsIgnoreCase(selectedValue.trim());
    }

    private static PurchaseOrderLogisticsQuotePublishedPriceView toPublishedPriceView(
            ForwarderBasePriceRecord price
    ) {
        PurchaseOrderLogisticsQuotePublishedPriceView view =
                new PurchaseOrderLogisticsQuotePublishedPriceView();
        view.priceRuleCode = price.priceRuleCode;
        view.cargoCategoryCode = price.cargoCategoryCode;
        view.cargoCategoryName = price.cargoCategoryName;
        view.priceStatus = price.priceStatus;
        view.currency = price.currency;
        view.unitPrice = price.unitPrice;
        view.billingUnit = price.billingUnit;
        view.billingBasis = price.billingBasis;
        view.volumeDivisor = price.volumeDivisor;
        view.minBillableUnit = price.minBillableUnit;
        view.minBillableUnitType = price.minBillableUnitType;
        view.minCharge = price.minCharge;
        return view;
    }

    private static PurchaseOrderLogisticsQuoteSurchargeView toSurchargeView(
            ForwarderTransportFeeRecord fee
    ) {
        PurchaseOrderLogisticsQuoteSurchargeView view =
                new PurchaseOrderLogisticsQuoteSurchargeView();
        view.feeName = fee.feeName;
        view.feeType = fee.feeType;
        view.triggerCondition = fee.triggerCondition;
        view.currency = fee.currency;
        view.amount = fee.amount;
        view.rate = fee.rate;
        view.billingUnit = fee.billingUnit;
        view.billingBasis = fee.billingBasis;
        view.minCharge = fee.minCharge;
        view.minBillableUnit = fee.minBillableUnit;
        return view;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
