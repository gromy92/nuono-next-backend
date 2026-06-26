package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanLineView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsRecommendationView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseOrderLogisticsCostCalculatorTest {

    private final PurchaseOrderLogisticsCostCalculator calculator = new PurchaseOrderLogisticsCostCalculator();

    @Test
    void etSaudiAirAddsHeadhaulWarehouseAndRiyadhLastMileComponents() {
        PurchaseOrderLogisticsPlanView plan = new PurchaseOrderLogisticsPlanView();
        plan.lines.add(line("SGGRB115", 20, "6.00000000", "0.06480000"));
        plan.lines.add(line("SGGRB116", 20, "6.80000000", "0.01000000"));
        plan.lines.add(line("SGGRB304", 24, "12.00000000", "0.13440000"));

        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.routeCode = "ET-SAU-AIR-FBN-RUH-20260604";
        candidate.forwarderCode = "ET";
        candidate.serviceCode = "ET-SAU-AIR-TIER1-WH-20260604";
        candidate.transportMode = "AIR";
        candidate.country = "沙特";
        candidate.targetPlatform = "FBN";
        candidate.currency = "RMB";
        candidate.kgMinUnitPrice = new BigDecimal("67");
        candidate.volumeDivisor = new BigDecimal("5000");
        candidate.minBillableUnit = new BigDecimal("10");
        candidate.minBillableUnitType = "KG";
        candidate.deliveryCity = "利雅得/RUH";

        PurchaseOrderLogisticsRecommendationView view = calculator.enrich(
                new PurchaseOrderLogisticsRecommendationView(),
                candidate,
                plan,
                List.of(lastMileBasePrice()),
                List.of(
                        warehouseFee(1L, "散件仓按件上架-小件", "INBOUND", "0.3", "PIECE", null),
                        warehouseFee(2L, "按件拣货-小件", "PICKING", "0.6", "PIECE", "8"),
                        warehouseFee(3L, "产品储存仓储费", "STORAGE", "8", "CBM_DAY", null)
                ),
                List.of(transportFee())
        );

        assertThat(view.estimatedTotalAmount).isEqualByComparingTo("2892.26");
        assertThat(view.estimatedTotalCostText).isEqualTo("RMB 2892.26");
        assertThat(view.recurringAmountPerDay).isEqualByComparingTo("1.67");
        assertThat(view.recurringCostText).isEqualTo("RMB 1.67/天");
        assertThat(view.costComponents)
                .extracting(component -> component.componentType)
                .containsExactly(
                        "HEADHAUL",
                        "WAREHOUSE_INBOUND",
                        "WAREHOUSE_PICKING",
                        "LAST_MILE",
                        "WAREHOUSE_STORAGE_DAILY"
                );
        assertThat(view.costComponents.stream().filter(component -> component.includedInTotal)).hasSize(4);
        assertThat(view.excludedCostNotes).anyMatch(note -> note.contains("贴标"));
    }

    private static PurchaseOrderLogisticsPlanLineView line(String psku, int airQuantity, String actualKg, String cbm) {
        PurchaseOrderLogisticsPlanLineView line = new PurchaseOrderLogisticsPlanLineView();
        line.partnerSku = psku;
        line.airQuantity = airQuantity;
        line.airActualWeightKg = new BigDecimal(actualKg);
        line.airLooseVolumeCbm = new BigDecimal(cbm);
        return line;
    }

    private static ForwarderWarehouseProcessingFeeRecord warehouseFee(
            Long id,
            String name,
            String type,
            String amount,
            String unit,
            String minCharge
    ) {
        ForwarderWarehouseProcessingFeeRecord fee = new ForwarderWarehouseProcessingFeeRecord();
        fee.id = id;
        fee.serviceCode = "ET-WH-PROCESS-20260604";
        fee.feeName = name;
        fee.feeType = type;
        fee.pricingModel = "PER_PIECE";
        fee.currency = "RMB";
        fee.amount = new BigDecimal(amount);
        fee.billingUnit = unit;
        fee.minCharge = minCharge == null ? null : new BigDecimal(minCharge);
        return fee;
    }

    private static ForwarderBasePriceRecord lastMileBasePrice() {
        ForwarderBasePriceRecord price = new ForwarderBasePriceRecord();
        price.id = 4L;
        price.serviceCode = "ET-LAST-MILE-20260604";
        price.priceRuleCode = "ET-20260604-LAST-MILE-RUH-CBM";
        price.cargoCategoryName = "平台仓送仓利雅得";
        price.pricingModel = "PER_CBM";
        price.currency = "RMB";
        price.unitPrice = new BigDecimal("150");
        price.billingUnit = "CBM";
        price.deliveryCity = "利雅得/RUH";
        price.priceStatus = "NORMAL";
        return price;
    }

    private static ForwarderTransportFeeRecord transportFee() {
        ForwarderTransportFeeRecord fee = new ForwarderTransportFeeRecord();
        fee.id = 5L;
        fee.serviceCode = "ET-LAST-MILE-20260604";
        fee.feeName = "沙特偏远派送费";
        fee.feeType = "REMOTE_AREA";
        fee.currency = "SAR";
        fee.amount = new BigDecimal("750");
        fee.billingUnit = "SHIPMENT";
        return fee;
    }
}
