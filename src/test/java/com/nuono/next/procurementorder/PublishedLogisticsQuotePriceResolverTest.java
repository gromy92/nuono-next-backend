package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublishedLogisticsQuotePriceResolverTest {

    @Mock
    private ProcurementPurchaseOrderMapper mapper;

    @Test
    void hydrateReturnsCategoryPricesAndApplicableRouteSurcharges() {
        ForwarderRouteRecommendationRecord route = route();
        LogisticsQuoteExportOption option = new LogisticsQuoteExportOption();
        option.candidate = route;
        ForwarderBasePriceRecord generalCargo = price(route.serviceCode, "沙特空运（普货）", "67");
        generalCargo.volumeDivisor = new BigDecimal("6000");
        generalCargo.minBillableUnit = new BigDecimal("10");
        generalCargo.minBillableUnitType = "KG";
        ForwarderBasePriceRecord liquid = price(route.serviceCode, "沙特空运（化妆品及液体）", "82");
        ForwarderTransportFeeRecord fbnDelivery = fee(route.serviceCode, "沙特利雅得FBN/FBA送仓费", "2");
        ForwarderTransportFeeRecord includedFee = fee(route.serviceCode, "已含费用", "3");
        includedFee.includedInBasePrice = true;
        ForwarderTransportFeeRecord wrongDestination = fee(route.serviceCode, "迪拜送仓费", "4");
        wrongDestination.deliveryCity = "迪拜/DB";

        when(mapper.listBasePricesByServiceCodes(List.of(route.serviceCode)))
                .thenReturn(List.of(generalCargo, liquid));
        when(mapper.listRouteSegments(List.of(route.routeCode)))
                .thenReturn(List.of(segment(route.routeCode, route.serviceCode)));
        when(mapper.listTransportFeesByServiceCodes(List.of(route.serviceCode)))
                .thenReturn(List.of(fbnDelivery, includedFee, wrongDestination));

        PublishedLogisticsQuotePriceResolver.hydrate(mapper, List.of(option));

        assertThat(option.publishedPrices)
                .extracting(price -> price.cargoCategoryName)
                .containsExactly("沙特空运（普货）", "沙特空运（化妆品及液体）");
        assertThat(option.publishedPrices)
                .extracting(price -> price.unitPrice)
                .containsExactly(new BigDecimal("67"), new BigDecimal("82"));
        assertThat(option.publishedPrices.get(0).volumeDivisor).isEqualByComparingTo("6000");
        assertThat(option.publishedPrices.get(0).minBillableUnit).isEqualByComparingTo("10");
        assertThat(option.surcharges).singleElement().satisfies(fee -> {
            assertThat(fee.feeName).isEqualTo("沙特利雅得FBN/FBA送仓费");
            assertThat(fee.amount).isEqualByComparingTo("2");
            assertThat(fee.billingUnit).isEqualTo("KG");
        });
    }

    private ForwarderRouteRecommendationRecord route() {
        ForwarderRouteRecommendationRecord route = new ForwarderRouteRecommendationRecord();
        route.routeCode = "QIKE-SAU-AIR-FBN-RUH-20260523";
        route.serviceCode = route.routeCode;
        route.quoteVersionCode = "QIKE-20260523";
        route.targetPlatform = "FBN";
        route.deliveryCity = "利雅得/RUH";
        return route;
    }

    private ForwarderBasePriceRecord price(String serviceCode, String category, String amount) {
        ForwarderBasePriceRecord price = new ForwarderBasePriceRecord();
        price.serviceCode = serviceCode;
        price.priceRuleCode = serviceCode + "-" + amount;
        price.cargoCategoryName = category;
        price.priceStatus = "NORMAL";
        price.currency = "RMB";
        price.unitPrice = new BigDecimal(amount);
        price.billingUnit = "KG";
        return price;
    }

    private ForwarderRouteSegmentRecord segment(String routeCode, String serviceCode) {
        ForwarderRouteSegmentRecord segment = new ForwarderRouteSegmentRecord();
        segment.routeCode = routeCode;
        segment.segmentNo = 1;
        segment.segmentRole = "HEADHAUL";
        segment.serviceCode = serviceCode;
        return segment;
    }

    private ForwarderTransportFeeRecord fee(String serviceCode, String name, String amount) {
        ForwarderTransportFeeRecord fee = new ForwarderTransportFeeRecord();
        fee.serviceCode = serviceCode;
        fee.feeName = name;
        fee.feeType = "FBN_DELIVERY";
        fee.targetPlatform = "FBN";
        fee.deliveryCity = "利雅得/RUH";
        fee.currency = "RMB";
        fee.amount = new BigDecimal(amount);
        fee.billingUnit = "KG";
        fee.includedInBasePrice = false;
        return fee;
    }
}
