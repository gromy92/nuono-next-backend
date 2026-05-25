package com.nuono.next.profit;

import com.nuono.next.profit.ProfitCalculationResult.ScenarioResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProfitCalculationEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.15");
    private static final BigDecimal DEFAULT_EXCHANGE_RATE = new BigDecimal("1.8833");
    private static final BigDecimal DEFAULT_DOMESTIC_SHIPPING_FEE = new BigDecimal("2.2");
    private static final BigDecimal DEFAULT_WAREHOUSE_DELIVERY_UNIT_PRICE = new BigDecimal("2.5");
    private static final BigDecimal DEFAULT_AIR_FREIGHT_UNIT_PRICE = new BigDecimal("65");
    private static final BigDecimal DEFAULT_OCEAN_FREIGHT_UNIT_PRICE = new BigDecimal("1300");
    private static final BigDecimal DEFAULT_AIR_FREIGHT_DIM_FACTOR = new BigDecimal("5000");
    private static final BigDecimal DEFAULT_FBN_COMMISSION_RATE = new BigDecimal("0.15");
    private static final BigDecimal DEFAULT_FBP_COMMISSION_RATE = new BigDecimal("0.15");
    private static final BigDecimal DEFAULT_FBN_OUTBOUND_FEE = new BigDecimal("8");
    private static final BigDecimal DEFAULT_FBP_DIRECT_SHIP_FEE = new BigDecimal("10");
    private static final BigDecimal DEFAULT_FULFILLMENT_FEE = new BigDecimal("7");

    private static final Map<String, String> REQUIRED_FIELD_LABELS = new LinkedHashMap<>();

    static {
        REQUIRED_FIELD_LABELS.put("salePrice", "目标售价");
        REQUIRED_FIELD_LABELS.put("purchasePrice", "采购单价");
        REQUIRED_FIELD_LABELS.put("lengthCm", "商品长");
        REQUIRED_FIELD_LABELS.put("widthCm", "商品宽");
        REQUIRED_FIELD_LABELS.put("heightCm", "商品高");
        REQUIRED_FIELD_LABELS.put("weightGrams", "商品重量");
    }

    public PreparedProfitCalculationInput prepare(ProfitCalculationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("缺少利润计算参数。");
        }

        PreparedProfitCalculationInput prepared = new PreparedProfitCalculationInput();
        prepared.setTitle(normalize(input.getTitle()));
        prepared.setSite(resolveSite(input.getSite()));
        prepared.setMarketCurrency(resolveMarketCurrency(prepared.getSite()));
        prepared.setSalePrice(requiredValue(input.getSalePrice(), "salePrice", prepared));
        prepared.setPurchasePrice(requiredValue(input.getPurchasePrice(), "purchasePrice", prepared));
        prepared.setLengthCm(requiredValue(input.getLengthCm(), "lengthCm", prepared));
        prepared.setWidthCm(requiredValue(input.getWidthCm(), "widthCm", prepared));
        prepared.setHeightCm(requiredValue(input.getHeightCm(), "heightCm", prepared));
        prepared.setWeightGrams(requiredValue(input.getWeightGrams(), "weightGrams", prepared));
        prepared.setVatRate(defaultPositive(input.getVatRate(), DEFAULT_VAT_RATE, "vatRate", prepared));
        prepared.setExchangeRate(defaultPositive(input.getExchangeRate(), DEFAULT_EXCHANGE_RATE, "exchangeRate", prepared));
        prepared.setDomesticShippingFee(
                defaultPositive(input.getDomesticShippingFee(), DEFAULT_DOMESTIC_SHIPPING_FEE, "domesticShippingFee", prepared)
        );
        prepared.setWarehouseDeliveryUnitPrice(defaultPositive(
                input.getWarehouseDeliveryUnitPrice(),
                DEFAULT_WAREHOUSE_DELIVERY_UNIT_PRICE,
                "warehouseDeliveryUnitPrice",
                prepared
        ));
        prepared.setAirFreightUnitPrice(defaultPositive(
                input.getAirFreightUnitPrice(),
                DEFAULT_AIR_FREIGHT_UNIT_PRICE,
                "airFreightUnitPrice",
                prepared
        ));
        prepared.setOceanFreightUnitPrice(defaultPositive(
                input.getOceanFreightUnitPrice(),
                DEFAULT_OCEAN_FREIGHT_UNIT_PRICE,
                "oceanFreightUnitPrice",
                prepared
        ));
        prepared.setAirFreightDimFactor(defaultPositive(
                input.getAirFreightDimFactor(),
                DEFAULT_AIR_FREIGHT_DIM_FACTOR,
                "airFreightDimFactor",
                prepared
        ));
        prepared.setFbnCommissionRate(defaultPositive(
                input.getFbnCommissionRate(),
                DEFAULT_FBN_COMMISSION_RATE,
                "fbnCommissionRate",
                prepared
        ));
        prepared.setFbpCommissionRate(defaultPositive(
                input.getFbpCommissionRate(),
                DEFAULT_FBP_COMMISSION_RATE,
                "fbpCommissionRate",
                prepared
        ));
        prepared.setFbnOutboundFee(defaultNonNegative(
                input.getFbnOutboundFee(),
                DEFAULT_FBN_OUTBOUND_FEE,
                "fbnOutboundFee",
                prepared
        ));
        prepared.setFbpDirectShipFee(defaultNonNegative(
                input.getFbpDirectShipFee(),
                DEFAULT_FBP_DIRECT_SHIP_FEE,
                "fbpDirectShipFee",
                prepared
        ));
        prepared.setFulfillmentFee(defaultNonNegative(
                input.getFulfillmentFee(),
                DEFAULT_FULFILLMENT_FEE,
                "fulfillmentFee",
                prepared
        ));
        return prepared;
    }

    public ProfitCalculationResult calculate(PreparedProfitCalculationInput prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("缺少利润计算参数。");
        }
        if (prepared.isBlocked()) {
            throw new IllegalArgumentException(fieldLabel(prepared.getMissingRequiredFields().get(0)) + "必须大于 0。");
        }

        BigDecimal volume = prepared.getLengthCm().multiply(prepared.getWidthCm()).multiply(prepared.getHeightCm());
        BigDecimal cubeVolumeCbm = scale(volume.divide(ONE_MILLION, 6, RoundingMode.HALF_UP), 4);
        BigDecimal dimensionalWeightGrams = scale(
                volume.divide(prepared.getAirFreightDimFactor(), 4, RoundingMode.HALF_UP).multiply(ONE_THOUSAND),
                2
        );
        BigDecimal warehouseDeliveryFeeRmb = scale(
                prepared.getWarehouseDeliveryUnitPrice()
                        .multiply(prepared.getWeightGrams())
                        .divide(ONE_THOUSAND, 4, RoundingMode.HALF_UP),
                2
        );
        BigDecimal airFirstLegFeeRmb = scale(
                prepared.getAirFreightUnitPrice()
                        .multiply(prepared.getWeightGrams())
                        .divide(ONE_THOUSAND, 4, RoundingMode.HALF_UP),
                2
        );
        BigDecimal oceanFirstLegFeeRmb = scale(
                prepared.getOceanFreightUnitPrice().multiply(volume).divide(ONE_MILLION, 4, RoundingMode.HALF_UP),
                2
        );

        ProfitCalculationResult result = new ProfitCalculationResult();
        result.setTitle(prepared.getTitle());
        result.setSite(prepared.getSite());
        result.setMarketCurrency(prepared.getMarketCurrency());
        result.setSalePrice(scale(prepared.getSalePrice(), 2));
        result.setPurchasePrice(scale(prepared.getPurchasePrice(), 2));
        result.setExchangeRate(scale(prepared.getExchangeRate(), 4));
        result.setLengthCm(scale(prepared.getLengthCm(), 2));
        result.setWidthCm(scale(prepared.getWidthCm(), 2));
        result.setHeightCm(scale(prepared.getHeightCm(), 2));
        result.setWeightGrams(scale(prepared.getWeightGrams(), 2));
        result.setCubeVolumeCbm(cubeVolumeCbm);
        result.setDimensionalWeightGrams(dimensionalWeightGrams);
        result.setWarehouseDeliveryFeeRmb(warehouseDeliveryFeeRmb);
        result.setAirFirstLegFeeRmb(airFirstLegFeeRmb);
        result.setOceanFirstLegFeeRmb(oceanFirstLegFeeRmb);
        if (prepared.getFbnOutboundFee() != null) {
            result.getScenarios().add(buildScenario(
                    "FBN_AIR",
                    "FBN 空运利润",
                    prepared.getSalePrice(),
                    prepared.getPurchasePrice(),
                    prepared.getExchangeRate(),
                    prepared.getVatRate(),
                    prepared.getFbnCommissionRate(),
                    prepared.getFbnOutboundFee(),
                    warehouseDeliveryFeeRmb,
                    airFirstLegFeeRmb,
                    prepared.getDomesticShippingFee(),
                    ZERO
            ));
            result.getScenarios().add(buildScenario(
                    "FBN_OCEAN",
                    "FBN 海运利润",
                    prepared.getSalePrice(),
                    prepared.getPurchasePrice(),
                    prepared.getExchangeRate(),
                    prepared.getVatRate(),
                    prepared.getFbnCommissionRate(),
                    prepared.getFbnOutboundFee(),
                    warehouseDeliveryFeeRmb,
                    oceanFirstLegFeeRmb,
                    prepared.getDomesticShippingFee(),
                    ZERO
            ));
        }
        result.getScenarios().add(buildScenario(
                "FBP_AIR",
                "FBP 空运利润",
                prepared.getSalePrice(),
                prepared.getPurchasePrice(),
                prepared.getExchangeRate(),
                prepared.getVatRate(),
                prepared.getFbpCommissionRate(),
                prepared.getFbpDirectShipFee(),
                warehouseDeliveryFeeRmb,
                airFirstLegFeeRmb,
                prepared.getDomesticShippingFee(),
                prepared.getFulfillmentFee()
        ));
        return result;
    }

    public String fieldLabel(String fieldKey) {
        return REQUIRED_FIELD_LABELS.getOrDefault(fieldKey, fieldKey);
    }

    private ScenarioResult buildScenario(
            String code,
            String label,
            BigDecimal salePrice,
            BigDecimal purchasePrice,
            BigDecimal exchangeRate,
            BigDecimal vatRate,
            BigDecimal commissionRate,
            BigDecimal platformFeeMarket,
            BigDecimal warehouseDeliveryFeeRmb,
            BigDecimal firstLegFeeRmb,
            BigDecimal domesticShippingFee,
            BigDecimal fulfillmentFee
    ) {
        BigDecimal grossRevenueRmb = scale(salePrice.multiply(exchangeRate), 2);
        BigDecimal commissionAmountMarket = scale(salePrice.multiply(commissionRate), 2);
        BigDecimal vatAmountMarket = scale(commissionAmountMarket.add(platformFeeMarket).multiply(vatRate), 2);
        BigDecimal platformTotalMarket = commissionAmountMarket.add(platformFeeMarket).add(vatAmountMarket);
        BigDecimal platformDeductionRmb = scale(platformTotalMarket.multiply(exchangeRate), 2);
        BigDecimal settlementRevenueRmb = scale(grossRevenueRmb.subtract(platformDeductionRmb), 2);
        BigDecimal totalCostRmb = scale(
                purchasePrice
                        .add(firstLegFeeRmb)
                        .add(warehouseDeliveryFeeRmb)
                        .add(domesticShippingFee)
                        .add(fulfillmentFee)
                        .add(platformDeductionRmb),
                2
        );
        BigDecimal profitRmb = scale(grossRevenueRmb.subtract(totalCostRmb), 2);
        BigDecimal marginRatePct = grossRevenueRmb.compareTo(ZERO) == 0
                ? ZERO
                : scale(profitRmb.divide(grossRevenueRmb, 6, RoundingMode.HALF_UP).multiply(ONE_HUNDRED), 2);

        ScenarioResult result = new ScenarioResult();
        result.setCode(code);
        result.setLabel(label);
        result.setGrossRevenueRmb(grossRevenueRmb);
        result.setCommissionRatePct(scale(commissionRate.multiply(ONE_HUNDRED), 2));
        result.setCommissionAmountMarket(commissionAmountMarket);
        result.setPlatformFeeAmountMarket(scale(platformFeeMarket, 2));
        result.setVatAmountMarket(vatAmountMarket);
        result.setPlatformDeductionRmb(platformDeductionRmb);
        result.setSettlementRevenueRmb(settlementRevenueRmb);
        result.setPurchasePriceRmb(scale(purchasePrice, 2));
        result.setFirstLegFeeRmb(scale(firstLegFeeRmb, 2));
        result.setWarehouseDeliveryFeeRmb(scale(warehouseDeliveryFeeRmb, 2));
        result.setDomesticShippingFeeRmb(scale(domesticShippingFee, 2));
        result.setFulfillmentFeeRmb(scale(fulfillmentFee, 2));
        result.setTotalCostRmb(totalCostRmb);
        result.setProfitRmb(profitRmb);
        result.setMarginRatePct(marginRatePct);
        return result;
    }

    private BigDecimal requiredValue(BigDecimal value, String fieldKey, PreparedProfitCalculationInput prepared) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            prepared.getMissingRequiredFields().add(fieldKey);
            return null;
        }
        return value;
    }

    private BigDecimal defaultPositive(
            BigDecimal value,
            BigDecimal defaultValue,
            String fieldKey,
            PreparedProfitCalculationInput prepared
    ) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            prepared.getUsedDefaultFields().add(fieldKey);
            return defaultValue;
        }
        return value;
    }

    private BigDecimal defaultNonNegative(
            BigDecimal value,
            BigDecimal defaultValue,
            String fieldKey,
            PreparedProfitCalculationInput prepared
    ) {
        if (value == null || value.compareTo(ZERO) < 0) {
            prepared.getUsedDefaultFields().add(fieldKey);
            return defaultValue;
        }
        return value;
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private String resolveSite(String site) {
        if (!StringUtils.hasText(site)) {
            return "SA";
        }
        return site.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveMarketCurrency(String site) {
        if ("AE".equals(site)) {
            return "AED";
        }
        if ("SA".equals(site)) {
            return "SAR";
        }
        return site;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
