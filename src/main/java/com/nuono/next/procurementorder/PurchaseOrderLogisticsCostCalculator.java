package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderBasePriceRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderTransportFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderWarehouseProcessingFeeRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsCostComponentView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanLineView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsPlanView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsRecommendationView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.util.StringUtils;

public class PurchaseOrderLogisticsCostCalculator {

    private static final String TRANSPORT_AIR = "AIR";
    private static final String TRANSPORT_SEA = "SEA";
    private static final BigDecimal CUBIC_CM_PER_CBM = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_AIR_VOLUME_DIVISOR = new BigDecimal("6000");

    public PurchaseOrderLogisticsRecommendationView enrich(
            PurchaseOrderLogisticsRecommendationView target,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderBasePriceRecord> segmentBasePrices,
            List<ForwarderWarehouseProcessingFeeRecord> warehouseFees,
            List<ForwarderTransportFeeRecord> transportFees
    ) {
        PurchaseOrderLogisticsRecommendationView recommendation = target == null
                ? new PurchaseOrderLogisticsRecommendationView()
                : target;
        addHeadhaul(recommendation, candidate, plan);
        if (isEtSaudiFbnRoute(candidate)) {
            addEtWarehouseInbound(recommendation, candidate, plan, warehouseFees);
            addEtWarehousePicking(recommendation, candidate, plan, warehouseFees);
            addEtLastMile(recommendation, candidate, plan, segmentBasePrices);
            addEtStorageDaily(recommendation, candidate, plan, warehouseFees);
            recommendation.excludedCostNotes.add("未默认计入贴标、换包装、托盘、偏远派送、跨仓派送和异常处理费；触发时需人工确认。");
            if (transportFees != null && !transportFees.isEmpty()) {
                recommendation.excludedCostNotes.add("已读取末端附加费规则；偏远、托盘、跨仓等费用按触发条件另计。");
            }
        }
        recalculateTotals(recommendation);
        return recommendation;
    }

    private void addHeadhaul(
            PurchaseOrderLogisticsRecommendationView target,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan
    ) {
        if (candidate == null) {
            return;
        }
        if (TRANSPORT_AIR.equals(candidate.transportMode)) {
            BigDecimal unitPrice = preferredAirKgUnitPrice(candidate);
            BigDecimal quantity = totalAirChargeableWeightKg(plan, candidate.volumeDivisor, candidate.minBillableUnit);
            if (unitPrice != null && quantity != null && quantity.signum() > 0) {
                addComponent(
                        target,
                        "HEADHAUL",
                        "干线空运费",
                        null,
                        candidate.currency,
                        unitPrice,
                        "KG",
                        quantity,
                        unitPrice.multiply(quantity),
                        true,
                        "max(实重, 体积重, 最低计费重) * KG单价",
                        candidate.serviceCode,
                        null,
                        "基础报价"
                );
            }
            return;
        }
        if (TRANSPORT_SEA.equals(candidate.transportMode)) {
            BigDecimal unitPrice = preferredSeaCbmUnitPrice(candidate);
            BigDecimal quantity = totalSeaLooseVolumeCbm(plan, candidate.minBillableUnit);
            if (unitPrice != null && quantity != null && quantity.signum() > 0) {
                addComponent(
                        target,
                        "HEADHAUL",
                        "干线海运费",
                        null,
                        candidate.currency,
                        unitPrice,
                        "CBM",
                        quantity,
                        unitPrice.multiply(quantity),
                        true,
                        "max(散货体积, 最低计费体积) * CBM单价",
                        candidate.serviceCode,
                        null,
                        "基础报价"
                );
            }
        }
    }

    private void addEtWarehouseInbound(
            PurchaseOrderLogisticsRecommendationView target,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderWarehouseProcessingFeeRecord> fees
    ) {
        ForwarderWarehouseProcessingFeeRecord fee = firstWarehouseFee(fees, "INBOUND", "PIECE", "小件");
        BigDecimal quantity = BigDecimal.valueOf(totalRouteQuantity(plan, candidate.transportMode));
        if (fee != null && fee.amount != null && quantity.signum() > 0) {
            addComponent(
                    target,
                    "WAREHOUSE_INBOUND",
                    "海外仓入库上架",
                    fee.id,
                    fee.currency,
                    fee.amount,
                    fee.billingUnit,
                    quantity,
                    fee.amount.multiply(quantity),
                    true,
                    "散件小件数量 * 入库上架单价",
                    fee.serviceCode,
                    fee.feeName,
                    null
            );
        }
    }

    private void addEtWarehousePicking(
            PurchaseOrderLogisticsRecommendationView target,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderWarehouseProcessingFeeRecord> fees
    ) {
        ForwarderWarehouseProcessingFeeRecord fee = firstWarehouseFee(fees, "PICKING", "PIECE", "小件");
        BigDecimal quantity = BigDecimal.valueOf(totalRouteQuantity(plan, candidate.transportMode));
        if (fee != null && fee.amount != null && quantity.signum() > 0) {
            BigDecimal amount = fee.amount.multiply(quantity);
            if (fee.minCharge != null && amount.compareTo(fee.minCharge) < 0) {
                amount = fee.minCharge;
            }
            addComponent(
                    target,
                    "WAREHOUSE_PICKING",
                    "海外仓拣货出库",
                    fee.id,
                    fee.currency,
                    fee.amount,
                    fee.billingUnit,
                    quantity,
                    amount,
                    true,
                    "max(散件小件数量 * 拣货单价, 最低消费)",
                    fee.serviceCode,
                    fee.feeName,
                    null
            );
        }
    }

    private void addEtStorageDaily(
            PurchaseOrderLogisticsRecommendationView target,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderWarehouseProcessingFeeRecord> fees
    ) {
        ForwarderWarehouseProcessingFeeRecord fee = firstWarehouseFee(fees, "STORAGE", "CBM_DAY", "产品储存");
        BigDecimal quantity = totalRouteVolumeCbm(plan, candidate.transportMode);
        if (fee != null && fee.amount != null && quantity != null && quantity.signum() > 0) {
            addComponent(
                    target,
                    "WAREHOUSE_STORAGE_DAILY",
                    "海外仓仓储日费",
                    fee.id,
                    fee.currency,
                    fee.amount,
                    fee.billingUnit,
                    quantity,
                    fee.amount.multiply(quantity),
                    false,
                    "散货体积 * 每CBM每日仓储费",
                    fee.serviceCode,
                    fee.feeName,
                    "按实际仓储天数另计"
            );
        }
    }

    private void addEtLastMile(
            PurchaseOrderLogisticsRecommendationView target,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsPlanView plan,
            List<ForwarderBasePriceRecord> basePrices
    ) {
        ForwarderBasePriceRecord price = firstLastMileCbmPrice(basePrices, candidate.deliveryCity);
        BigDecimal quantity = totalRouteVolumeCbm(plan, candidate.transportMode);
        if (price != null && price.unitPrice != null && quantity != null && quantity.signum() > 0) {
            addComponent(
                    target,
                    "LAST_MILE",
                    "平台仓送仓",
                    price.id,
                    price.currency,
                    price.unitPrice,
                    price.billingUnit,
                    quantity,
                    price.unitPrice.multiply(quantity),
                    true,
                    "散货体积 * 平台仓送仓CBM单价",
                    price.serviceCode,
                    price.cargoCategoryName,
                    null
            );
        } else {
            target.excludedCostNotes.add("平台仓送仓费用未匹配到可直接计价的 CBM 报价，需要人工确认。");
        }
    }

    private boolean isEtSaudiFbnRoute(ForwarderRouteRecommendationRecord candidate) {
        return candidate != null
                && "ET".equals(candidate.forwarderCode)
                && "沙特".equals(candidate.country)
                && "FBN".equals(candidate.targetPlatform);
    }

    private BigDecimal totalAirChargeableWeightKg(
            PurchaseOrderLogisticsPlanView plan,
            BigDecimal volumeDivisor,
            BigDecimal minBillable
    ) {
        BigDecimal actual = totalAirActualWeightKg(plan);
        BigDecimal volume = totalAirLooseVolumeCbm(plan);
        if (actual == null || volume == null) {
            return null;
        }
        BigDecimal divisor = volumeDivisor == null || volumeDivisor.signum() <= 0
                ? DEFAULT_AIR_VOLUME_DIVISOR
                : volumeDivisor;
        BigDecimal volumeWeight = volume.multiply(CUBIC_CM_PER_CBM).divide(divisor, 8, RoundingMode.HALF_UP);
        BigDecimal result = actual.max(volumeWeight);
        if (minBillable != null && result.compareTo(minBillable) < 0) {
            result = minBillable;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal totalAirActualWeightKg(PurchaseOrderLogisticsPlanView plan) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (PurchaseOrderLogisticsPlanLineView line : plan.lines) {
            if (line.airQuantity != null && line.airQuantity > 0 && line.airActualWeightKg != null) {
                total = total.add(line.airActualWeightKg);
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    private BigDecimal totalAirLooseVolumeCbm(PurchaseOrderLogisticsPlanView plan) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (PurchaseOrderLogisticsPlanLineView line : plan.lines) {
            if (line.airQuantity != null && line.airQuantity > 0 && line.airLooseVolumeCbm != null) {
                total = total.add(line.airLooseVolumeCbm);
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    private BigDecimal totalSeaLooseVolumeCbm(PurchaseOrderLogisticsPlanView plan, BigDecimal minBillable) {
        BigDecimal total = totalRouteVolumeCbm(plan, TRANSPORT_SEA);
        if (total == null) {
            return null;
        }
        if (minBillable != null && total.compareTo(minBillable) < 0) {
            total = minBillable;
        }
        return total;
    }

    private int totalRouteQuantity(PurchaseOrderLogisticsPlanView plan, String transportMode) {
        int total = 0;
        for (PurchaseOrderLogisticsPlanLineView line : plan.lines) {
            if (TRANSPORT_AIR.equals(transportMode)) {
                total += nonNull(line.airQuantity);
            } else if (TRANSPORT_SEA.equals(transportMode)) {
                total += nonNull(line.seaQuantity);
            }
        }
        return total;
    }

    private BigDecimal totalRouteVolumeCbm(PurchaseOrderLogisticsPlanView plan, String transportMode) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (PurchaseOrderLogisticsPlanLineView line : plan.lines) {
            BigDecimal value = null;
            if (TRANSPORT_AIR.equals(transportMode) && line.airQuantity != null && line.airQuantity > 0) {
                value = line.airLooseVolumeCbm;
            } else if (TRANSPORT_SEA.equals(transportMode) && line.seaQuantity != null && line.seaQuantity > 0) {
                value = line.seaLooseVolumeCbm;
            }
            if (value != null) {
                total = total.add(value);
                hasValue = true;
            }
        }
        return hasValue ? total.setScale(6, RoundingMode.HALF_UP) : null;
    }

    private ForwarderWarehouseProcessingFeeRecord firstWarehouseFee(
            List<ForwarderWarehouseProcessingFeeRecord> fees,
            String type,
            String unit,
            String namePart
    ) {
        if (fees == null) {
            return null;
        }
        return fees.stream()
                .filter(fee -> type.equals(fee.feeType))
                .filter(fee -> unit.equalsIgnoreCase(defaultText(fee.billingUnit, "")))
                .filter(fee -> !StringUtils.hasText(namePart) || defaultText(fee.feeName, "").contains(namePart))
                .findFirst()
                .orElse(null);
    }

    private ForwarderBasePriceRecord firstLastMileCbmPrice(List<ForwarderBasePriceRecord> prices, String deliveryCity) {
        if (prices == null) {
            return null;
        }
        String cityNeedle = defaultText(deliveryCity, "").contains("吉达") || defaultText(deliveryCity, "").contains("JED")
                ? "吉达"
                : "利雅得";
        return prices.stream()
                .filter(price -> "NORMAL".equals(price.priceStatus))
                .filter(price -> "CBM".equalsIgnoreCase(defaultText(price.billingUnit, "")))
                .filter(price -> defaultText(price.cargoCategoryName, "").contains("平台仓送仓"))
                .filter(price -> defaultText(price.cargoCategoryName, "").contains(cityNeedle)
                        || defaultText(price.deliveryCity, "").contains(cityNeedle))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal preferredSeaCbmUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate.cbmMinUnitPrice != null) {
            return candidate.cbmMinUnitPrice;
        }
        if ("CBM".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private BigDecimal preferredAirKgUnitPrice(ForwarderRouteRecommendationRecord candidate) {
        if (candidate.kgMinUnitPrice != null) {
            return candidate.kgMinUnitPrice;
        }
        if ("KG".equalsIgnoreCase(defaultText(candidate.billingUnit, ""))) {
            return candidate.minUnitPrice;
        }
        return null;
    }

    private void addComponent(
            PurchaseOrderLogisticsRecommendationView target,
            String type,
            String name,
            Long sourceId,
            String currency,
            BigDecimal unitPrice,
            String billingUnit,
            BigDecimal quantity,
            BigDecimal amount,
            boolean includedInTotal,
            String formula,
            String sourceServiceCode,
            String sourceFeeName,
            String remark
    ) {
        PurchaseOrderLogisticsCostComponentView component = new PurchaseOrderLogisticsCostComponentView();
        component.componentType = type;
        component.componentName = name;
        component.sourceId = sourceId;
        component.currency = currency;
        component.unitPrice = unitPrice;
        component.billingUnit = billingUnit;
        component.billableQuantity = quantity;
        component.amount = amount.setScale(2, RoundingMode.HALF_UP);
        component.amountText = money(currency, component.amount);
        component.amountStatus = includedInTotal ? "ESTIMATED" : "RECURRING_DAILY";
        component.includedInTotal = includedInTotal;
        component.formulaText = formula;
        component.sourceServiceCode = sourceServiceCode;
        component.sourceFeeName = sourceFeeName;
        component.remark = remark;
        target.costComponents.add(component);
    }

    private void recalculateTotals(PurchaseOrderLogisticsRecommendationView target) {
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal daily = BigDecimal.ZERO;
        String currency = null;
        for (PurchaseOrderLogisticsCostComponentView component : target.costComponents) {
            if (currency == null && StringUtils.hasText(component.currency)) {
                currency = component.currency;
            }
            if (component.amount == null) {
                continue;
            }
            if (component.includedInTotal) {
                fixed = fixed.add(component.amount);
            } else if ("RECURRING_DAILY".equals(component.amountStatus)) {
                daily = daily.add(component.amount);
            }
        }
        if (!target.costComponents.isEmpty()) {
            target.estimatedTotalAmount = fixed.setScale(2, RoundingMode.HALF_UP);
            target.estimatedTotalCostText = money(currency, target.estimatedTotalAmount);
        }
        if (daily.signum() > 0) {
            target.recurringAmountPerDay = daily.setScale(2, RoundingMode.HALF_UP);
            target.recurringCostText = money(currency, target.recurringAmountPerDay) + "/天";
        }
    }

    private static int nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String money(String currency, BigDecimal amount) {
        String prefix = StringUtils.hasText(currency) ? currency + " " : "";
        return prefix + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
