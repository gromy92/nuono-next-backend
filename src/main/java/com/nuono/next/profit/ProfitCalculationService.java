package com.nuono.next.profit;

import com.nuono.next.outboundfee.OfficialOutboundFeeCalculationRequest;
import com.nuono.next.outboundfee.OfficialOutboundFeeCalculationResult;
import com.nuono.next.outboundfee.OfficialOutboundFeeCalculator;
import com.nuono.next.profit.ProfitCalculationResult.ScenarioResult;
import com.nuono.next.profit.ProfitCalculationView.OfficialOutboundFeeView;
import com.nuono.next.profit.ProfitCalculationView.ScenarioView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    private final ProfitCalculationEngine profitCalculationEngine;

    private final OfficialOutboundFeeCalculator officialOutboundFeeCalculator;

    public ProfitCalculationService(ProfitCalculationEngine profitCalculationEngine) {
        this(profitCalculationEngine, (OfficialOutboundFeeCalculator) null);
    }

    @Autowired
    public ProfitCalculationService(
            ProfitCalculationEngine profitCalculationEngine,
            ObjectProvider<OfficialOutboundFeeCalculator> officialOutboundFeeCalculatorProvider
    ) {
        this(profitCalculationEngine, officialOutboundFeeCalculatorProvider.getIfAvailable());
    }

    ProfitCalculationService(
            ProfitCalculationEngine profitCalculationEngine,
            OfficialOutboundFeeCalculator officialOutboundFeeCalculator
    ) {
        this.profitCalculationEngine = profitCalculationEngine;
        this.officialOutboundFeeCalculator = officialOutboundFeeCalculator;
    }

    public ProfitCalculationView calculate(ProfitCalculationCommand command) {
        PreparedProfitCalculationInput prepared = profitCalculationEngine.prepare(toInput(command));
        OfficialOutboundFeeView officialOutboundFeeView = resolveOfficialOutboundFee(prepared);
        if (prepared.isBlocked()) {
            if (officialOutboundFeeCalculator != null) {
                ProfitCalculationView blockedView = buildBaseView(prepared);
                blockedView.setReady(false);
                blockedView.setMessage(
                        profitCalculationEngine.fieldLabel(prepared.getMissingRequiredFields().get(0))
                                + "必须大于 0，无法完成利润测算。"
                );
                blockedView.setOfficialOutboundFee(officialOutboundFeeView);
                return blockedView;
            }
            throw new IllegalArgumentException(
                    profitCalculationEngine.fieldLabel(prepared.getMissingRequiredFields().get(0)) + "必须大于 0。"
            );
        }
        if (officialOutboundFeeCalculator != null && officialOutboundFeeView != null) {
            if ("CALCULATED".equals(officialOutboundFeeView.getStatus())) {
                prepared.setFbnOutboundFee(officialOutboundFeeView.getFeeAmount());
            } else if (isManualFbnOutboundFeeOverride(command)) {
                officialOutboundFeeView = manualOutboundFeeView(command, prepared);
                prepared.setFbnOutboundFee(command.getFbnOutboundFee());
            } else {
                prepared.setFbnOutboundFee(null);
            }
        }
        ProfitCalculationResult result = profitCalculationEngine.calculate(prepared);

        ProfitCalculationView view = buildBaseView(prepared);
        view.setReady(true);
        view.setMessage("已按共享利润口径完成测算，采购与后续自动选品都可以先参考这一套估算结果。");
        view.setCubeVolumeCbm(result.getCubeVolumeCbm());
        view.setDimensionalWeightGrams(result.getDimensionalWeightGrams());
        view.setWarehouseDeliveryFeeRmb(result.getWarehouseDeliveryFeeRmb());
        view.setAirFirstLegFeeRmb(result.getAirFirstLegFeeRmb());
        view.setOceanFirstLegFeeRmb(result.getOceanFirstLegFeeRmb());
        view.setOfficialOutboundFee(officialOutboundFeeView);

        List<String> notes = new ArrayList<>();
        notes.add("当前利润按旧系统同口径公式估算：售价扣佣金、平台费和 VAT 后，再减采购、头程、送仓和国内物流。");
        if (officialOutboundFeeView != null && "CALCULATED".equals(officialOutboundFeeView.getStatus())) {
            notes.add("FBN 出仓费已按已发布官方规则自动计算，响应包含命中分类、重量段和来源版本。");
        } else if (officialOutboundFeeView != null && "MANUAL_OVERRIDE".equals(officialOutboundFeeView.getStatus())) {
            notes.add("FBN 出仓费使用本次手工输入值，未标记为官方规则命中。");
        } else if (officialOutboundFeeView != null) {
            notes.add("FBN 出仓费缺少可用官方规则，本次不计算 FBN 空运/海运利润，避免继续使用旧默认值。");
        } else {
            notes.add("FBN / FBP 的佣金、出仓费和直邮费先按当前输入值计算，正式决策前请按站点真实费用表复核。");
        }
        notes.add("采购单价按 RMB 录入，售价按站点币种录入。当前默认汇率和基础物流参数可手动改成试运行值。");
        view.setNotes(notes);

        List<ScenarioView> scenarios = new ArrayList<>();
        for (ScenarioResult scenarioResult : result.getScenarios()) {
            scenarios.add(buildScenarioView(scenarioResult));
        }
        view.setScenarios(scenarios);
        return view;
    }

    private boolean isManualFbnOutboundFeeOverride(ProfitCalculationCommand command) {
        return command != null
                && Boolean.TRUE.equals(command.getManualFbnOutboundFeeOverride())
                && command.getFbnOutboundFee() != null
                && command.getFbnOutboundFee().compareTo(BigDecimal.ZERO) >= 0;
    }

    private OfficialOutboundFeeView manualOutboundFeeView(
            ProfitCalculationCommand command,
            PreparedProfitCalculationInput prepared
    ) {
        OfficialOutboundFeeView view = new OfficialOutboundFeeView();
        view.setStatus("MANUAL_OVERRIDE");
        view.setMessage("本次使用用户明确输入的手工 FBN 出仓费，不代表已命中官方规则。");
        view.setFeeAmount(command.getFbnOutboundFee());
        view.setCurrency(prepared.getMarketCurrency());
        return view;
    }

    private ProfitCalculationView buildBaseView(PreparedProfitCalculationInput prepared) {
        ProfitCalculationView view = new ProfitCalculationView();
        view.setTitle(prepared.getTitle());
        view.setSite(prepared.getSite());
        view.setMarketCurrency(prepared.getMarketCurrency());
        view.setSalePrice(prepared.getSalePrice());
        view.setPurchasePrice(prepared.getPurchasePrice());
        view.setExchangeRate(prepared.getExchangeRate());
        view.setLengthCm(prepared.getLengthCm());
        view.setWidthCm(prepared.getWidthCm());
        view.setHeightCm(prepared.getHeightCm());
        view.setWeightGrams(prepared.getWeightGrams());
        return view;
    }

    private OfficialOutboundFeeView resolveOfficialOutboundFee(PreparedProfitCalculationInput prepared) {
        if (officialOutboundFeeCalculator == null) {
            return null;
        }
        OfficialOutboundFeeCalculationResult result = officialOutboundFeeCalculator.calculate(new OfficialOutboundFeeCalculationRequest(
                resolveOutboundCountry(prepared.getSite()),
                "NOON",
                "FBN",
                prepared.getMarketCurrency(),
                prepared.getSalePrice(),
                prepared.getWeightGrams(),
                prepared.getLengthCm(),
                prepared.getWidthCm(),
                prepared.getHeightCm(),
                LocalDate.now()
        ));
        OfficialOutboundFeeView view = new OfficialOutboundFeeView();
        if (result.isSuccess()) {
            view.setStatus("CALCULATED");
            view.setMessage("已命中官方 FBN 出仓费规则。");
            view.setFeeAmount(result.getFinalFeeAmount());
            view.setCurrency(result.getCurrency());
            view.setMatchedClassificationName(result.getMatchedClassificationName());
            view.setMatchedSlabNaturalKey(result.getMatchedSlabNaturalKey());
            view.setSourceVersionId(result.getSourceVersionId());
            view.setEvidence(result.getEvidence());
            return view;
        }
        view.setStatus("FAILED");
        view.setFailureCode(result.getFailure() == null ? null : result.getFailure().name());
        view.setMessage(outboundFeeFailureMessage(view.getFailureCode()));
        return view;
    }

    private String resolveOutboundCountry(String site) {
        String normalized = site == null ? "" : site.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(normalized)) {
            return "KSA";
        }
        if ("AE".equals(normalized)) {
            return "UAE";
        }
        return normalized;
    }

    private String outboundFeeFailureMessage(String failureCode) {
        if ("MISSING_DIMENSIONS".equals(failureCode)) {
            return "缺少商品长宽高，无法匹配官方出仓费规格分类。";
        }
        if ("MISSING_WEIGHT".equals(failureCode)) {
            return "缺少商品重量，无法匹配官方出仓费重量段。";
        }
        if ("MISSING_SALE_PRICE".equals(failureCode)) {
            return "缺少目标售价，无法判断标准费用或高 ASP 费用。";
        }
        if ("POLICY_NOT_FOUND".equals(failureCode)) {
            return "当前站点没有可用的 active 官方出仓费计算策略。";
        }
        if ("CLASSIFICATION_NOT_FOUND".equals(failureCode)) {
            return "商品尺寸或重量超出当前 active 官方规格分类范围。";
        }
        if ("SLAB_NOT_FOUND".equals(failureCode)) {
            return "商品计费重量没有命中当前 active 官方费用重量段。";
        }
        if ("CURRENCY_MISMATCH".equals(failureCode)) {
            return "售价币种与当前 active 官方出仓费规则币种不一致。";
        }
        return "官方 FBN 出仓费暂时无法计算。";
    }

    private ScenarioView buildScenarioView(ScenarioResult scenarioResult) {
        ScenarioView view = new ScenarioView();
        view.setCode(scenarioResult.getCode());
        view.setLabel(scenarioResult.getLabel());
        view.setGrossRevenueRmb(scenarioResult.getGrossRevenueRmb());
        view.setCommissionRatePct(scenarioResult.getCommissionRatePct());
        view.setCommissionAmountMarket(scenarioResult.getCommissionAmountMarket());
        view.setPlatformFeeAmountMarket(scenarioResult.getPlatformFeeAmountMarket());
        view.setVatAmountMarket(scenarioResult.getVatAmountMarket());
        view.setPlatformDeductionRmb(scenarioResult.getPlatformDeductionRmb());
        view.setSettlementRevenueRmb(scenarioResult.getSettlementRevenueRmb());
        view.setPurchasePriceRmb(scenarioResult.getPurchasePriceRmb());
        view.setFirstLegFeeRmb(scenarioResult.getFirstLegFeeRmb());
        view.setWarehouseDeliveryFeeRmb(scenarioResult.getWarehouseDeliveryFeeRmb());
        view.setDomesticShippingFeeRmb(scenarioResult.getDomesticShippingFeeRmb());
        view.setFulfillmentFeeRmb(scenarioResult.getFulfillmentFeeRmb());
        view.setTotalCostRmb(scenarioResult.getTotalCostRmb());
        view.setProfitRmb(scenarioResult.getProfitRmb());
        view.setMarginRatePct(scenarioResult.getMarginRatePct());
        return view;
    }

    private ProfitCalculationInput toInput(ProfitCalculationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少利润计算参数。");
        }
        ProfitCalculationInput input = new ProfitCalculationInput();
        input.setTitle(command.getTitle());
        input.setSite(command.getSite());
        input.setSalePrice(command.getSalePrice());
        input.setPurchasePrice(command.getPurchasePrice());
        input.setLengthCm(command.getLengthCm());
        input.setWidthCm(command.getWidthCm());
        input.setHeightCm(command.getHeightCm());
        input.setWeightGrams(command.getWeightGrams());
        input.setVatRate(command.getVatRate());
        input.setExchangeRate(command.getExchangeRate());
        input.setDomesticShippingFee(command.getDomesticShippingFee());
        input.setWarehouseDeliveryUnitPrice(command.getWarehouseDeliveryUnitPrice());
        input.setAirFreightUnitPrice(command.getAirFreightUnitPrice());
        input.setOceanFreightUnitPrice(command.getOceanFreightUnitPrice());
        input.setAirFreightDimFactor(command.getAirFreightDimFactor());
        input.setFbnCommissionRate(command.getFbnCommissionRate());
        input.setFbpCommissionRate(command.getFbpCommissionRate());
        input.setFbnOutboundFee(command.getFbnOutboundFee());
        input.setFbpDirectShipFee(command.getFbpDirectShipFee());
        input.setFulfillmentFee(command.getFulfillmentFee());
        return input;
    }
}
