package com.nuono.next.preorderprofit;

import com.nuono.next.preorderprofit.PreOrderProfitCalculationView.CostLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PreOrderProfitCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_TARGET_MARGIN_RATE = new BigDecimal("0.30");
    private static final BigDecimal DOMESTIC_LOGISTICS_RMB_PER_KG = new BigDecimal("2");

    private static final Map<String, SiteRule> SITE_RULES = new LinkedHashMap<>();
    private static final Map<String, CategoryRule> CATEGORY_RULES = new LinkedHashMap<>();
    private static final Map<String, LogisticsRule> LOGISTICS_RULES = new LinkedHashMap<>();

    static {
        SITE_RULES.put("SA", new SiteRule("SA", "SAR", new BigDecimal("0.15"), new BigDecimal("1.8833")));
        SITE_RULES.put("AE", new SiteRule("AE", "AED", new BigDecimal("0.05"), new BigDecimal("1.95")));

        CATEGORY_RULES.put(
                "home-kitchen-sa",
                new CategoryRule("home-kitchen-sa", "SA", "Home - Kitchen & Dining", new BigDecimal("0.14"), new BigDecimal("10"))
        );
        CATEGORY_RULES.put(
                "pets-toys-sa",
                new CategoryRule("pets-toys-sa", "SA", "Pets - Toys", new BigDecimal("0.15"), new BigDecimal("8.5"))
        );
        CATEGORY_RULES.put(
                "home-storage-ae",
                new CategoryRule("home-storage-ae", "AE", "Home - Storage", new BigDecimal("0.13"), new BigDecimal("9.8"))
        );

        LOGISTICS_RULES.put(
                "et-sa-air-standard",
                new LogisticsRule("et-sa-air-standard", "SA", "ET SA Air Standard", new BigDecimal("35"), new BigDecimal("6000"))
        );
        LOGISTICS_RULES.put(
                "et-ae-air-standard",
                new LogisticsRule("et-ae-air-standard", "AE", "ET AE Air Standard", new BigDecimal("32"), new BigDecimal("6000"))
        );
        LOGISTICS_RULES.put(
                "et-sa-sea-standard",
                new LogisticsRule("et-sa-sea-standard", "SA", "ET SA Sea Standard", new BigDecimal("12"), new BigDecimal("5000"))
        );
    }

    public PreOrderProfitCalculationView calculate(PreOrderProfitCalculationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("缺少出单前利润计算参数。");
        }
        PreOrderProfitCalculationView view = new PreOrderProfitCalculationView();
        SiteRule siteRule = SITE_RULES.get(normalizeUpper(request.getSiteCode()));
        if (siteRule != null) {
            view.setCurrency(siteRule.currency);
            view.setTaxRate(scale(siteRule.taxRate, 2));
            view.setExchangeRate(scale(siteRule.exchangeRateRmbPerCurrency, 4));
        }
        collectMissingInputs(request, view, siteRule);
        if (!view.getMissingFields().isEmpty()) {
            view.setStatus("INCOMPLETE_INPUT");
            return view;
        }

        CategoryRule categoryRule = resolveCategoryRule(request.getCategoryId(), siteRule.siteCode);
        LogisticsRule logisticsRule = resolveLogisticsRule(request.getLogisticsCarrierId(), siteRule.siteCode);
        if (categoryRule == null) {
            view.getMissingRules().add("CATEGORY_RULE");
        }
        if (logisticsRule == null) {
            view.getMissingRules().add("LOGISTICS_RULE");
        }
        if (!view.getMissingRules().isEmpty()) {
            view.setStatus("MISSING_RULE");
            return view;
        }

        BigDecimal taxMultiplier = ONE.add(siteRule.taxRate);
        BigDecimal volumeWeightKg = request.getLengthCm()
                .multiply(request.getWidthCm())
                .multiply(request.getHeightCm())
                .divide(logisticsRule.volumeDivisorCm3PerKg, 8, RoundingMode.HALF_UP);
        BigDecimal billingWeightKg = max(request.getActualWeightKg(), volumeWeightKg);
        BigDecimal purchaseCost = convertRmbToSiteCurrency(request.getPurchasePriceRmb(), siteRule);
        BigDecimal domesticLogisticsFeeRmb = request.getActualWeightKg().multiply(DOMESTIC_LOGISTICS_RMB_PER_KG);
        BigDecimal domesticLogisticsFee = convertRmbToSiteCurrency(domesticLogisticsFeeRmb, siteRule);
        BigDecimal firstLegLogisticsFeeRmb = billingWeightKg.multiply(logisticsRule.unitPriceRmbPerKg);
        BigDecimal firstLegLogisticsFee = convertRmbToSiteCurrency(firstLegLogisticsFeeRmb, siteRule);
        BigDecimal commissionBase = request.getSalePrice().multiply(categoryRule.commissionRate);
        BigDecimal commissionTaxIncluded = commissionBase.multiply(taxMultiplier);
        BigDecimal fulfillmentFeeTaxIncluded = categoryRule.fulfillmentFee.multiply(taxMultiplier);
        BigDecimal fixedCost = purchaseCost
                .add(domesticLogisticsFee)
                .add(firstLegLogisticsFee)
                .add(fulfillmentFeeTaxIncluded);
        BigDecimal totalCost = fixedCost.add(commissionTaxIncluded);
        BigDecimal estimatedProfit = request.getSalePrice().subtract(totalCost);
        BigDecimal estimatedMarginPct = estimatedProfit
                .divide(request.getSalePrice(), 8, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
        BigDecimal saleVariableRate = categoryRule.commissionRate.multiply(taxMultiplier);
        BigDecimal breakEvenDenominator = ONE.subtract(saleVariableRate);
        BigDecimal targetMarginRate = positive(request.getTargetMarginRate())
                ? request.getTargetMarginRate()
                : DEFAULT_TARGET_MARGIN_RATE;
        BigDecimal targetDenominator = breakEvenDenominator.subtract(targetMarginRate);

        view.setPurchaseCost(money(purchaseCost));
        view.setDomesticLogisticsFeeRmb(scale(domesticLogisticsFeeRmb, 4));
        view.setDomesticLogisticsFee(money(domesticLogisticsFee));
        view.setVolumeWeightKg(scale(volumeWeightKg, 3));
        view.setBillingWeightKg(scale(billingWeightKg, 3));
        view.setFirstLegLogisticsFeeRmb(money(firstLegLogisticsFeeRmb));
        view.setFirstLegLogisticsFee(money(firstLegLogisticsFee));
        view.setCommissionBase(money(commissionBase));
        view.setCommissionFeeTaxIncluded(money(commissionTaxIncluded));
        view.setFulfillmentFeeBase(money(categoryRule.fulfillmentFee));
        view.setFulfillmentFeeTaxIncluded(money(fulfillmentFeeTaxIncluded));
        view.setTotalCost(money(totalCost));
        view.setEstimatedProfit(money(estimatedProfit));
        view.setEstimatedMarginRatePct(money(estimatedMarginPct));
        if (breakEvenDenominator.compareTo(ZERO) > 0) {
            view.setBreakEvenSalePrice(money(fixedCost.divide(breakEvenDenominator, 8, RoundingMode.HALF_UP)));
        } else {
            view.getFormulaIssues().add("BREAK_EVEN_DENOMINATOR");
        }
        if (targetDenominator.compareTo(ZERO) > 0) {
            view.setTargetMarginSalePrice(money(fixedCost.divide(targetDenominator, 8, RoundingMode.HALF_UP)));
        } else {
            view.getFormulaIssues().add("TARGET_MARGIN_DENOMINATOR");
        }
        addCostLines(view, siteRule, logisticsRule, categoryRule);
        view.setStatus(view.getFormulaIssues().isEmpty() ? "READY" : "INVALID_FORMULA");
        return view;
    }

    public String categoryLabel(String categoryId, String siteCode) {
        CategoryRule rule = resolveCategoryRule(categoryId, normalizeUpper(siteCode));
        return rule == null ? null : rule.label;
    }

    public String logisticsLabel(String logisticsCarrierId, String siteCode) {
        LogisticsRule rule = resolveLogisticsRule(logisticsCarrierId, normalizeUpper(siteCode));
        return rule == null ? null : rule.label;
    }

    private void collectMissingInputs(
            PreOrderProfitCalculationRequest request,
            PreOrderProfitCalculationView view,
            SiteRule siteRule
    ) {
        if (!StringUtils.hasText(request.getPurchaseUrl())) {
            view.getMissingFields().add("PURCHASE_URL");
        }
        if (siteRule == null) {
            view.getMissingFields().add("SITE_CODE");
        }
        if (!positive(request.getPurchasePriceRmb())) {
            view.getMissingFields().add("PURCHASE_PRICE_RMB");
        }
        if (!positive(request.getLengthCm())) {
            view.getMissingFields().add("LENGTH_CM");
        }
        if (!positive(request.getWidthCm())) {
            view.getMissingFields().add("WIDTH_CM");
        }
        if (!positive(request.getHeightCm())) {
            view.getMissingFields().add("HEIGHT_CM");
        }
        if (!positive(request.getActualWeightKg())) {
            view.getMissingFields().add("ACTUAL_WEIGHT_KG");
        }
        if (!StringUtils.hasText(request.getCategoryId())) {
            view.getMissingFields().add("CATEGORY_ID");
        }
        if (!StringUtils.hasText(request.getLogisticsCarrierId())) {
            view.getMissingFields().add("LOGISTICS_CARRIER_ID");
        }
        if (!positive(request.getSalePrice())) {
            view.getMissingFields().add("SALE_PRICE");
        }
    }

    private void addCostLines(
            PreOrderProfitCalculationView view,
            SiteRule siteRule,
            LogisticsRule logisticsRule,
            CategoryRule categoryRule
    ) {
        view.getCostLines().add(costLine("procurement", "采购成本", view.getPurchaseCost(), siteRule.currency, "采购单价 RMB / 固定汇率"));
        view.getCostLines().add(costLine("domestic-logistics", "国内物流费", view.getDomesticLogisticsFee(), siteRule.currency, "实际重量 kg * 2 RMB/kg"));
        view.getCostLines().add(costLine("first-leg", "头程物流成本", view.getFirstLegLogisticsFee(), siteRule.currency, logisticsRule.label));
        view.getCostLines().add(costLine("commission", "平台佣金含税", view.getCommissionFeeTaxIncluded(), siteRule.currency, categoryRule.label));
        view.getCostLines().add(costLine("fulfillment", "出舱/履约费含税", view.getFulfillmentFeeTaxIncluded(), siteRule.currency, categoryRule.label));
    }

    private CostLine costLine(String key, String label, BigDecimal amount, String currency, String note) {
        CostLine line = new CostLine();
        line.setKey(key);
        line.setLabel(label);
        line.setAmount(amount);
        line.setCurrency(currency);
        line.setNote(note);
        return line;
    }

    private CategoryRule resolveCategoryRule(String categoryId, String siteCode) {
        CategoryRule rule = CATEGORY_RULES.get(normalizeLower(categoryId));
        return rule == null || !rule.siteCode.equals(siteCode) ? null : rule;
    }

    private LogisticsRule resolveLogisticsRule(String logisticsCarrierId, String siteCode) {
        LogisticsRule rule = LOGISTICS_RULES.get(normalizeLower(logisticsCarrierId));
        return rule == null || !rule.siteCode.equals(siteCode) ? null : rule;
    }

    private BigDecimal convertRmbToSiteCurrency(BigDecimal amountRmb, SiteRule siteRule) {
        return amountRmb.divide(siteRule.exchangeRateRmbPerCurrency, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(ZERO) > 0;
    }

    private BigDecimal money(BigDecimal value) {
        return scale(value, 2);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private String normalizeUpper(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeLower(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class SiteRule {
        private final String siteCode;
        private final String currency;
        private final BigDecimal taxRate;
        private final BigDecimal exchangeRateRmbPerCurrency;

        private SiteRule(String siteCode, String currency, BigDecimal taxRate, BigDecimal exchangeRateRmbPerCurrency) {
            this.siteCode = siteCode;
            this.currency = currency;
            this.taxRate = taxRate;
            this.exchangeRateRmbPerCurrency = exchangeRateRmbPerCurrency;
        }
    }

    private static final class CategoryRule {
        private final String id;
        private final String siteCode;
        private final String label;
        private final BigDecimal commissionRate;
        private final BigDecimal fulfillmentFee;

        private CategoryRule(String id, String siteCode, String label, BigDecimal commissionRate, BigDecimal fulfillmentFee) {
            this.id = id;
            this.siteCode = siteCode;
            this.label = label;
            this.commissionRate = commissionRate;
            this.fulfillmentFee = fulfillmentFee;
        }
    }

    private static final class LogisticsRule {
        private final String id;
        private final String siteCode;
        private final String label;
        private final BigDecimal unitPriceRmbPerKg;
        private final BigDecimal volumeDivisorCm3PerKg;

        private LogisticsRule(
                String id,
                String siteCode,
                String label,
                BigDecimal unitPriceRmbPerKg,
                BigDecimal volumeDivisorCm3PerKg
        ) {
            this.id = id;
            this.siteCode = siteCode;
            this.label = label;
            this.unitPriceRmbPerKg = unitPriceRmbPerKg;
            this.volumeDivisorCm3PerKg = volumeDivisorCm3PerKg;
        }
    }
}
