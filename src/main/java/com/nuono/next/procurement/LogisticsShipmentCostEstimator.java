package com.nuono.next.procurement;

import com.nuono.next.logisticsquote.LogisticsPriceRuleFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class LogisticsShipmentCostEstimator {

    private static final BigDecimal GRAMS_PER_KG = new BigDecimal("1000");
    private static final BigDecimal CUBIC_CM_PER_CBM = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_AIR_VOLUME_DIVISOR = new BigDecimal("6000");
    private static final BigDecimal HALF_UNIT = new BigDecimal("0.5");

    public CostEstimate estimate(ShipmentFacts shipment, LogisticsPriceRuleFact priceRule) {
        if (shipment == null || priceRule == null) {
            return CostEstimate.notComputable("缺少货物信息或报价规则，不能计算预估运费。");
        }
        if (!"NORMAL".equalsIgnoreCase(priceRule.getPriceStatus())) {
            return CostEstimate.notComputable(priceStatusReason(priceRule.getPriceStatus()));
        }
        if (priceRule.getUnitPrice() == null) {
            return CostEstimate.notComputable("报价需要人工询价，不能自动生成最终推荐。");
        }

        BigDecimal actualWeightKg = shipment.unitWeightGrams
                .multiply(BigDecimal.valueOf(shipment.quantity))
                .divide(GRAMS_PER_KG, 2, RoundingMode.HALF_UP);
        BigDecimal volumeCbm = shipment.packageLengthCm
                .multiply(shipment.packageWidthCm)
                .multiply(shipment.packageHeightCm)
                .multiply(BigDecimal.valueOf(shipment.quantity))
                .divide(CUBIC_CM_PER_CBM, 3, RoundingMode.HALF_UP);
        BigDecimal volumetricWeightKg = volumetricWeightKg(shipment, priceRule);
        BigDecimal chargeableUnit = baseChargeableUnit(shipment, priceRule, actualWeightKg, volumeCbm, volumetricWeightKg);
        chargeableUnit = applyRounding(chargeableUnit, priceRule.getRoundingRule());

        List<String> fragments = new ArrayList<>();
        fragments.add("实重 " + actualWeightKg + " kg");
        if (volumetricWeightKg != null) {
            fragments.add("体积重 " + volumetricWeightKg + " kg");
        }
        fragments.add("体积 " + volumeCbm + " cbm");

        if (priceRule.getMinimumBillableUnit() != null
                && chargeableUnit.compareTo(priceRule.getMinimumBillableUnit()) < 0) {
            chargeableUnit = priceRule.getMinimumBillableUnit().setScale(2, RoundingMode.HALF_UP);
            fragments.add("最低计费 " + strip(priceRule.getMinimumBillableUnit()) + " "
                    + displayUnit(priceRule.getMinimumBillableUnitType(), priceRule.getBillingUnit()));
        }

        BigDecimal baseCost = chargeableUnit.multiply(priceRule.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalCost = baseCost;
        if (priceRule.getMinimumCharge() != null && finalCost.compareTo(priceRule.getMinimumCharge()) < 0) {
            finalCost = priceRule.getMinimumCharge().setScale(2, RoundingMode.HALF_UP);
            fragments.add("最低收费 " + strip(priceRule.getMinimumCharge()));
        }
        fragments.add("计费重量/体积 " + chargeableUnit + " " + displayUnit(priceRule.getBillingUnit(), ""));
        fragments.add("单价 " + priceRule.getUnitPrice() + " " + priceRule.getCurrency());

        CostEstimate estimate = new CostEstimate();
        estimate.computable = true;
        estimate.actualWeightKg = actualWeightKg;
        estimate.volumetricWeightKg = volumetricWeightKg;
        estimate.volumeCbm = volumeCbm;
        estimate.chargeableUnit = chargeableUnit;
        estimate.estimatedCost = finalCost;
        estimate.currency = priceRule.getCurrency();
        estimate.breakdownText = String.join("；", fragments) + "。";
        return estimate;
    }

    private BigDecimal volumetricWeightKg(ShipmentFacts shipment, LogisticsPriceRuleFact priceRule) {
        if (!"air".equalsIgnoreCase(shipment.transportMode)) {
            return null;
        }
        BigDecimal divisor = priceRule.getVolumeDivisor() == null
                ? DEFAULT_AIR_VOLUME_DIVISOR
                : priceRule.getVolumeDivisor();
        return shipment.packageLengthCm
                .multiply(shipment.packageWidthCm)
                .multiply(shipment.packageHeightCm)
                .multiply(BigDecimal.valueOf(shipment.quantity))
                .divide(divisor, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal baseChargeableUnit(
            ShipmentFacts shipment,
            LogisticsPriceRuleFact priceRule,
            BigDecimal actualWeightKg,
            BigDecimal volumeCbm,
            BigDecimal volumetricWeightKg
    ) {
        String billingUnit = priceRule.getBillingUnit();
        if ("cbm".equalsIgnoreCase(billingUnit)) {
            return volumeCbm;
        }
        if ("sea".equalsIgnoreCase(shipment.transportMode)
                && "kg".equalsIgnoreCase(billingUnit)
                && StringUtils.hasText(priceRule.getSeaWeightRatio())) {
            BigDecimal ratioWeight = parseSeaRatio(priceRule.getSeaWeightRatio(), volumeCbm);
            return actualWeightKg.max(ratioWeight);
        }
        if ("air".equalsIgnoreCase(shipment.transportMode) && volumetricWeightKg != null) {
            return actualWeightKg.max(volumetricWeightKg);
        }
        return actualWeightKg;
    }

    private BigDecimal parseSeaRatio(String seaWeightRatio, BigDecimal volumeCbm) {
        String normalized = seaWeightRatio.trim().toLowerCase();
        String numeric = normalized.replaceAll("[^0-9.]", "");
        if (!StringUtils.hasText(numeric)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(numeric).multiply(volumeCbm);
    }

    private BigDecimal applyRounding(BigDecimal value, String roundingRule) {
        if ("CEIL_0_5".equalsIgnoreCase(roundingRule)) {
            return value.divide(HALF_UNIT, 0, RoundingMode.CEILING)
                    .multiply(HALF_UNIT)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        if ("CEIL_1".equalsIgnoreCase(roundingRule)) {
            return value.setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String priceStatusReason(String priceStatus) {
        if ("ASK_QUOTE".equalsIgnoreCase(priceStatus)) {
            return "报价需要人工询价，不能自动生成最终推荐。";
        }
        if ("STARTING_PRICE".equalsIgnoreCase(priceStatus)) {
            return "报价是起步价，不能直接作为最终推荐费用。";
        }
        return "报价状态不是普通有效价，不能自动生成最终推荐。";
    }

    private String displayUnit(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static class ShipmentFacts {

        private final BigDecimal packageLengthCm;
        private final BigDecimal packageWidthCm;
        private final BigDecimal packageHeightCm;
        private final BigDecimal unitWeightGrams;
        private final int quantity;
        private final String transportMode;

        public ShipmentFacts(
                BigDecimal packageLengthCm,
                BigDecimal packageWidthCm,
                BigDecimal packageHeightCm,
                BigDecimal unitWeightGrams,
                int quantity,
                String transportMode
        ) {
            this.packageLengthCm = packageLengthCm;
            this.packageWidthCm = packageWidthCm;
            this.packageHeightCm = packageHeightCm;
            this.unitWeightGrams = unitWeightGrams;
            this.quantity = quantity;
            this.transportMode = transportMode;
        }
    }

    public static class CostEstimate {

        private boolean computable;
        private String reason;
        private BigDecimal actualWeightKg;
        private BigDecimal volumetricWeightKg;
        private BigDecimal volumeCbm;
        private BigDecimal chargeableUnit;
        private BigDecimal estimatedCost;
        private String currency;
        private String breakdownText;

        private static CostEstimate notComputable(String reason) {
            CostEstimate estimate = new CostEstimate();
            estimate.computable = false;
            estimate.reason = reason;
            return estimate;
        }

        public boolean isComputable() {
            return computable;
        }

        public String getReason() {
            return reason;
        }

        public BigDecimal getActualWeightKg() {
            return actualWeightKg;
        }

        public BigDecimal getVolumetricWeightKg() {
            return volumetricWeightKg;
        }

        public BigDecimal getVolumeCbm() {
            return volumeCbm;
        }

        public BigDecimal getChargeableUnit() {
            return chargeableUnit;
        }

        public BigDecimal getEstimatedCost() {
            return estimatedCost;
        }

        public String getCurrency() {
            return currency;
        }

        public String getBreakdownText() {
            return breakdownText;
        }
    }
}
