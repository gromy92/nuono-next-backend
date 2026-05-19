package com.nuono.next.profit;

import com.nuono.next.profit.ProfitCalculationResult.ScenarioResult;
import com.nuono.next.profit.ProfitCalculationView.ScenarioView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProfitCalculationService {

    private final ProfitCalculationEngine profitCalculationEngine;

    public ProfitCalculationService(ProfitCalculationEngine profitCalculationEngine) {
        this.profitCalculationEngine = profitCalculationEngine;
    }

    public ProfitCalculationView calculate(ProfitCalculationCommand command) {
        PreparedProfitCalculationInput prepared = profitCalculationEngine.prepare(toInput(command));
        if (prepared.isBlocked()) {
            throw new IllegalArgumentException(
                    profitCalculationEngine.fieldLabel(prepared.getMissingRequiredFields().get(0)) + "必须大于 0。"
            );
        }
        ProfitCalculationResult result = profitCalculationEngine.calculate(prepared);

        ProfitCalculationView view = new ProfitCalculationView();
        view.setReady(true);
        view.setMessage("已按共享利润口径完成测算，采购与后续自动选品都可以先参考这一套估算结果。");
        view.setTitle(result.getTitle());
        view.setSite(result.getSite());
        view.setMarketCurrency(result.getMarketCurrency());
        view.setSalePrice(result.getSalePrice());
        view.setPurchasePrice(result.getPurchasePrice());
        view.setExchangeRate(result.getExchangeRate());
        view.setLengthCm(result.getLengthCm());
        view.setWidthCm(result.getWidthCm());
        view.setHeightCm(result.getHeightCm());
        view.setWeightGrams(result.getWeightGrams());
        view.setCubeVolumeCbm(result.getCubeVolumeCbm());
        view.setDimensionalWeightGrams(result.getDimensionalWeightGrams());
        view.setWarehouseDeliveryFeeRmb(result.getWarehouseDeliveryFeeRmb());
        view.setAirFirstLegFeeRmb(result.getAirFirstLegFeeRmb());
        view.setOceanFirstLegFeeRmb(result.getOceanFirstLegFeeRmb());

        List<String> notes = new ArrayList<>();
        notes.add("当前利润按旧系统同口径公式估算：售价扣佣金、平台费和 VAT 后，再减采购、头程、送仓和国内物流。");
        notes.add("FBN / FBP 的佣金、出仓费和直邮费先按当前输入值计算，正式决策前请按站点真实费用表复核。");
        notes.add("采购单价按 RMB 录入，售价按站点币种录入。当前默认汇率和基础物流参数可手动改成试运行值。");
        view.setNotes(notes);

        List<ScenarioView> scenarios = new ArrayList<>();
        for (ScenarioResult scenarioResult : result.getScenarios()) {
            scenarios.add(buildScenarioView(scenarioResult));
        }
        view.setScenarios(scenarios);
        return view;
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
