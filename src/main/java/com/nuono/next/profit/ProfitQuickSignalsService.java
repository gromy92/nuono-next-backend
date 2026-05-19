package com.nuono.next.profit;

import com.nuono.next.profit.ProfitCalculationResult.ScenarioResult;
import com.nuono.next.profit.ProfitQuickSignalsRequest.Candidate;
import com.nuono.next.profit.ProfitQuickSignalsRequest.Context;
import com.nuono.next.profit.ProfitQuickSignalsView.DetailSeedView;
import com.nuono.next.profit.ProfitQuickSignalsView.InputSnapshotView;
import com.nuono.next.profit.ProfitQuickSignalsView.QuickScenarioView;
import com.nuono.next.profit.ProfitQuickSignalsView.SignalView;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProfitQuickSignalsService {

    private final ProfitCalculationEngine profitCalculationEngine;

    public ProfitQuickSignalsService(ProfitCalculationEngine profitCalculationEngine) {
        this.profitCalculationEngine = profitCalculationEngine;
    }

    public ProfitQuickSignalsView calculate(ProfitQuickSignalsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("缺少快速利润信号参数。");
        }
        if (request.getCandidates() == null || request.getCandidates().isEmpty()) {
            throw new IllegalArgumentException("请至少传入一条候选商品。");
        }

        ProfitQuickSignalsView view = new ProfitQuickSignalsView();
        view.setReady(true);
        view.setMessage("已按统一利润口径生成候选快速利润信号。");
        view.setSignalVersion("profit-v1.1");

        Context context = request.getContext();
        for (Candidate candidate : request.getCandidates()) {
            if (candidate == null) {
                continue;
            }
            PreparedProfitCalculationInput prepared = profitCalculationEngine.prepare(toInput(context, candidate));
            SignalView signal = new SignalView();
            signal.setCandidateKey(candidate.getCandidateKey());
            signal.setCandidateId(candidate.getCandidateId());
            signal.setTitle(StringUtils.hasText(candidate.getTitle()) ? candidate.getTitle().trim() : prepared.getTitle());
            signal.setMissingInputs(new ArrayList<>(prepared.getMissingRequiredFields()));
            signal.setUsedDefaults(new ArrayList<>(prepared.getUsedDefaultFields()));
            signal.setInputSnapshot(buildInputSnapshot(prepared));
            signal.setDetailSeed(buildDetailSeed(prepared));

            if (prepared.isBlocked()) {
                signal.setStatus(ProfitQuickSignalStatus.BLOCKED);
            } else {
                ProfitCalculationResult result = profitCalculationEngine.calculate(prepared);
                signal.setQuickScenarios(buildQuickScenarios(result.getScenarios()));
                boolean hasCompleteQuickScenarios = signal.getQuickScenarios().size() == 2;
                boolean usedDefaults = !signal.getUsedDefaults().isEmpty();
                signal.setStatus(hasCompleteQuickScenarios && !usedDefaults
                        ? ProfitQuickSignalStatus.READY
                        : ProfitQuickSignalStatus.PARTIAL);
                if (view.getMarketCurrency() == null) {
                    view.setMarketCurrency(result.getMarketCurrency());
                }
            }
            if (view.getMarketCurrency() == null) {
                view.setMarketCurrency(prepared.getMarketCurrency());
            }
            view.getSignals().add(signal);
        }
        return view;
    }

    private ProfitCalculationInput toInput(Context context, Candidate candidate) {
        ProfitCalculationInput input = new ProfitCalculationInput();
        input.setTitle(resolveTitle(context, candidate));
        if (context != null) {
            input.setSite(context.getSite());
            input.setSalePrice(context.getSalePrice());
            input.setVatRate(context.getVatRate());
            input.setExchangeRate(context.getExchangeRate());
            input.setDomesticShippingFee(context.getDomesticShippingFee());
            input.setWarehouseDeliveryUnitPrice(context.getWarehouseDeliveryUnitPrice());
            input.setAirFreightUnitPrice(context.getAirFreightUnitPrice());
            input.setOceanFreightUnitPrice(context.getOceanFreightUnitPrice());
            input.setAirFreightDimFactor(context.getAirFreightDimFactor());
            input.setFbnCommissionRate(context.getFbnCommissionRate());
            input.setFbpCommissionRate(context.getFbpCommissionRate());
            input.setFbnOutboundFee(context.getFbnOutboundFee());
            input.setFbpDirectShipFee(context.getFbpDirectShipFee());
            input.setFulfillmentFee(context.getFulfillmentFee());
        }
        input.setPurchasePrice(candidate.getPurchasePrice());
        input.setLengthCm(candidate.getLengthCm());
        input.setWidthCm(candidate.getWidthCm());
        input.setHeightCm(candidate.getHeightCm());
        input.setWeightGrams(candidate.getWeightGrams());
        return input;
    }

    private String resolveTitle(Context context, Candidate candidate) {
        if (candidate != null && StringUtils.hasText(candidate.getTitle())) {
            return candidate.getTitle().trim();
        }
        if (context != null && StringUtils.hasText(context.getTitlePrefix())) {
            return context.getTitlePrefix().trim();
        }
        return null;
    }

    private InputSnapshotView buildInputSnapshot(PreparedProfitCalculationInput prepared) {
        InputSnapshotView snapshot = new InputSnapshotView();
        snapshot.setSite(prepared.getSite());
        snapshot.setSalePrice(prepared.getSalePrice());
        snapshot.setPurchasePrice(prepared.getPurchasePrice());
        snapshot.setLengthCm(prepared.getLengthCm());
        snapshot.setWidthCm(prepared.getWidthCm());
        snapshot.setHeightCm(prepared.getHeightCm());
        snapshot.setWeightGrams(prepared.getWeightGrams());
        return snapshot;
    }

    private DetailSeedView buildDetailSeed(PreparedProfitCalculationInput prepared) {
        DetailSeedView detailSeed = new DetailSeedView();
        detailSeed.setTitle(prepared.getTitle());
        detailSeed.setSite(prepared.getSite());
        detailSeed.setSalePrice(prepared.getSalePrice());
        detailSeed.setPurchasePrice(prepared.getPurchasePrice());
        detailSeed.setLengthCm(prepared.getLengthCm());
        detailSeed.setWidthCm(prepared.getWidthCm());
        detailSeed.setHeightCm(prepared.getHeightCm());
        detailSeed.setWeightGrams(prepared.getWeightGrams());
        detailSeed.setVatRate(prepared.getVatRate());
        detailSeed.setExchangeRate(prepared.getExchangeRate());
        detailSeed.setDomesticShippingFee(prepared.getDomesticShippingFee());
        detailSeed.setWarehouseDeliveryUnitPrice(prepared.getWarehouseDeliveryUnitPrice());
        detailSeed.setAirFreightUnitPrice(prepared.getAirFreightUnitPrice());
        detailSeed.setOceanFreightUnitPrice(prepared.getOceanFreightUnitPrice());
        detailSeed.setAirFreightDimFactor(prepared.getAirFreightDimFactor());
        detailSeed.setFbnCommissionRate(prepared.getFbnCommissionRate());
        detailSeed.setFbpCommissionRate(prepared.getFbpCommissionRate());
        detailSeed.setFbnOutboundFee(prepared.getFbnOutboundFee());
        detailSeed.setFbpDirectShipFee(prepared.getFbpDirectShipFee());
        detailSeed.setFulfillmentFee(prepared.getFulfillmentFee());
        return detailSeed;
    }

    private List<QuickScenarioView> buildQuickScenarios(List<ScenarioResult> scenarios) {
        List<QuickScenarioView> quickScenarios = new ArrayList<>();
        if (scenarios == null) {
            return quickScenarios;
        }
        for (ScenarioResult scenario : scenarios) {
            if (scenario == null) {
                continue;
            }
            if (!"FBN_AIR".equals(scenario.getCode()) && !"FBN_OCEAN".equals(scenario.getCode())) {
                continue;
            }
            QuickScenarioView quickScenario = new QuickScenarioView();
            quickScenario.setCode(scenario.getCode());
            quickScenario.setLabel("FBN_AIR".equals(scenario.getCode()) ? "空运毛利" : "海运毛利");
            quickScenario.setProfitRmb(scenario.getProfitRmb());
            quickScenario.setMarginRatePct(scenario.getMarginRatePct());
            quickScenario.setFirstLegFeeRmb(scenario.getFirstLegFeeRmb());
            quickScenarios.add(quickScenario);
        }
        return quickScenarios;
    }
}
