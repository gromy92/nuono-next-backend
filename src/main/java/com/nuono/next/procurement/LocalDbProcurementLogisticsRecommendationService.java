package com.nuono.next.procurement;

import com.nuono.next.logisticsquote.LogisticsCargoCategoryFact;
import com.nuono.next.logisticsquote.LogisticsPriceRuleFact;
import com.nuono.next.logisticsquote.LogisticsQuoteComparisonQuery;
import com.nuono.next.logisticsquote.LogisticsQuoteFactRepository;
import com.nuono.next.logisticsquote.LogisticsQuoteFactSourceLineage;
import com.nuono.next.logisticsquote.LogisticsServiceLineFact;
import com.nuono.next.logisticsquote.LogisticsServiceLineQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementLogisticsRecommendationService {

    static final String SERVICE_SCOPE_FBN = "warehouse_to_fbn";

    private final LocalDbProcurementLogisticsRequirementService requirementService;
    private final LogisticsQuoteFactRepository repository;
    private final LogisticsShipmentCostEstimator costEstimator = new LogisticsShipmentCostEstimator();
    private final LogisticsRestrictionEvaluator restrictionEvaluator = new LogisticsRestrictionEvaluator();

    public LocalDbProcurementLogisticsRecommendationService(
            LocalDbProcurementLogisticsRequirementService requirementService,
            LogisticsQuoteFactRepository repository
    ) {
        this.requirementService = requirementService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProcurementLogisticsRecommendationView recommend(Long ownerUserId, Long demandItemId) {
        ProcurementLogisticsRequirementRow requirement =
                requirementService.requireReadyForRecommendation(ownerUserId, demandItemId);
        ProcurementLogisticsRecommendationView baseView = baseView(requirement);

        List<LogisticsServiceLineFact> serviceLines = repository.findActiveServiceLines(new LogisticsServiceLineQuery(
                null,
                requirement.getDestinationCountry(),
                requirement.getTransportMode(),
                SERVICE_SCOPE_FBN,
                requirement.getDestinationNode()
        ));
        if (serviceLines.isEmpty()) {
            return noAvailableQuote(baseView);
        }

        Map<String, LogisticsServiceLineFact> serviceLineByKey = new LinkedHashMap<>();
        for (LogisticsServiceLineFact serviceLine : serviceLines) {
            serviceLineByKey.put(serviceLine.getNaturalKey(), serviceLine);
        }

        Map<String, LogisticsCargoCategoryFact> categoryByKey = activeCategoryMap(serviceLines);
        List<Candidate> candidates = new ArrayList<>();
        LogisticsShipmentCostEstimator.ShipmentFacts shipmentFacts = shipmentFacts(requirement);
        LogisticsRestrictionEvaluator.CargoFacts cargoFacts = cargoFacts(requirement);
        for (LogisticsServiceLineFact serviceLine : serviceLines) {
            for (LogisticsPriceRuleFact priceRule : repository.findPriceRulesByServiceLineKey(serviceLine.getNaturalKey())) {
                if (!billingUnit(requirement.getTransportMode()).equalsIgnoreCase(priceRule.getBillingUnit())) {
                    continue;
                }
                LogisticsCargoCategoryFact category = categoryByKey.get(priceRule.getCargoCategoryKey());
                if (category != null) {
                    LogisticsShipmentCostEstimator.CostEstimate estimate =
                            costEstimator.estimate(shipmentFacts, priceRule);
                    LogisticsRestrictionEvaluator.EvaluationResult restrictionResult = restrictionEvaluator.evaluate(
                            cargoFacts,
                            category,
                            repository.findRestrictionRulesByServiceLineKey(serviceLine.getNaturalKey())
                    );
                    candidates.add(new Candidate(serviceLine, category, priceRule, estimate, restrictionResult));
                }
            }
        }
        candidates.sort(Comparator
                .comparing((Candidate candidate) -> !candidate.isSelectable())
                .thenComparing(candidate -> candidate.estimate.isComputable()
                        ? candidate.estimate.getEstimatedCost()
                        : BigDecimal.valueOf(Long.MAX_VALUE)));

        Candidate winner = candidates.stream()
                .filter(Candidate::isSelectable)
                .findFirst()
                .orElse(null);
        if (winner == null) {
            if (candidates.stream().anyMatch(candidate -> candidate.restrictionResult.isHardRestricted())) {
                return noSafeAutomaticRecommendation(baseView, candidates);
            }
            return noAvailableQuote(baseView);
        }

        applyWinner(baseView, winner);
        baseView.setComparedOptions(toOptions(candidates));
        return baseView;
    }

    private ProcurementLogisticsRecommendationView baseView(ProcurementLogisticsRequirementRow requirement) {
        ProcurementLogisticsRecommendationView view = new ProcurementLogisticsRecommendationView();
        view.setMode("local-db");
        view.setReady(true);
        view.setOwnerUserId(requirement.getOwnerUserId());
        view.setDemandItemId(requirement.getDemandItemId());
        view.setTransportMode(requirement.getTransportMode());
        view.setDestinationCountry(requirement.getDestinationCountry());
        view.setDestinationNode(requirement.getDestinationNode());
        return view;
    }

    private ProcurementLogisticsRecommendationView noAvailableQuote(ProcurementLogisticsRecommendationView view) {
        view.setStatus("no_available_quote");
        view.setMessage("当前没有匹配" + transportModeLabel(view.getTransportMode()) + "、目的地和 FBN 服务范围的可用货代报价。");
        view.setComparedOptions(List.of());
        view.setSourceEvidence(List.of());
        return view;
    }

    private ProcurementLogisticsRecommendationView noSafeAutomaticRecommendation(
            ProcurementLogisticsRecommendationView view,
            List<Candidate> candidates
    ) {
        view.setStatus("no_safe_automatic_recommendation");
        view.setMessage("当前没有安全的自动推荐货代；所有可用报价都命中禁限运规则，请人工确认。");
        view.setComparedOptions(toOptions(candidates));
        view.setSourceEvidence(List.of());
        return view;
    }

    private void applyWinner(ProcurementLogisticsRecommendationView view, Candidate winner) {
        LogisticsServiceLineFact serviceLine = winner.serviceLine;
        LogisticsCargoCategoryFact category = winner.category;
        LogisticsPriceRuleFact priceRule = winner.priceRule;

        view.setStatus("recommended");
        view.setMessage("已按当前运输方式生成货代推荐。");
        view.setForwarderCode(serviceLine.getForwarderCode());
        view.setForwarderName(displayText(serviceLine.getForwarderName(), serviceLine.getForwarderCode()));
        view.setServiceLineKey(serviceLine.getNaturalKey());
        view.setServiceLineName(displayText(serviceLine.getChannelName(), serviceLine.getNaturalKey()));
        view.setMatchedCargoCategoryKey(category.getNaturalKey());
        view.setMatchedCargoCategoryName(displayText(category.getCategoryName(), category.getSourceCategoryName()));
        view.setEstimatedBaseCost(priceRule.getUnitPrice());
        view.setEstimatedCost(winner.estimate.getEstimatedCost());
        view.setChargeableUnit(winner.estimate.getChargeableUnit());
        view.setCurrency(priceRule.getCurrency());
        view.setBillingUnit(priceRule.getBillingUnit());
        view.setLeadTimeText(leadTimeText(serviceLine));
        view.setWinnerReason(view.getForwarderName() + "在当前" + transportModeLabel(view.getTransportMode())
                + "可用报价中预估总费用最低：" + winner.estimate.getEstimatedCost() + " " + priceRule.getCurrency()
                + "，基础单价 " + priceRule.getUnitPrice() + " " + priceRule.getCurrency() + "。");
        view.setCalculationBreakdown(winner.estimate.getBreakdownText());
        view.setRiskPrompts(toRiskPrompts(winner.restrictionResult));
        view.setSourceEvidence(List.of(evidence(priceRule.getSourceLineage()), evidence(serviceLine.getSourceLineage())));
    }

    private Map<String, LogisticsCargoCategoryFact> activeCategoryMap(List<LogisticsServiceLineFact> serviceLines) {
        Map<String, LogisticsCargoCategoryFact> categories = new LinkedHashMap<>();
        for (LogisticsServiceLineFact serviceLine : serviceLines) {
            List<LogisticsCargoCategoryFact> rows = repository.findActiveCargoCategories(
                    serviceLine.getForwarderCode(),
                    serviceLine.getNaturalKey()
            );
            for (LogisticsCargoCategoryFact row : rows) {
                categories.put(row.getNaturalKey(), row);
            }
        }
        return categories;
    }

    private List<ProcurementLogisticsRecommendationView.OptionView> toOptions(List<Candidate> candidates) {
        List<ProcurementLogisticsRecommendationView.OptionView> options = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ProcurementLogisticsRecommendationView.OptionView option =
                    new ProcurementLogisticsRecommendationView.OptionView();
            option.setForwarderCode(candidate.serviceLine.getForwarderCode());
            option.setForwarderName(displayText(candidate.serviceLine.getForwarderName(), candidate.serviceLine.getForwarderCode()));
            option.setServiceLineKey(candidate.serviceLine.getNaturalKey());
            option.setServiceLineName(displayText(candidate.serviceLine.getChannelName(), candidate.serviceLine.getNaturalKey()));
            option.setCargoCategoryKey(candidate.category.getNaturalKey());
            option.setCargoCategoryName(displayText(candidate.category.getCategoryName(), candidate.category.getSourceCategoryName()));
            option.setEstimatedBaseCost(candidate.priceRule.getUnitPrice());
            option.setEstimatedCost(candidate.estimate.getEstimatedCost());
            option.setChargeableUnit(candidate.estimate.getChargeableUnit());
            option.setCurrency(candidate.priceRule.getCurrency());
            option.setBillingUnit(candidate.priceRule.getBillingUnit());
            option.setReason(optionReason(candidate));
            options.add(option);
        }
        return options;
    }

    private List<ProcurementLogisticsRecommendationView.RiskPromptView> toRiskPrompts(
            LogisticsRestrictionEvaluator.EvaluationResult restrictionResult
    ) {
        List<ProcurementLogisticsRecommendationView.RiskPromptView> prompts = new ArrayList<>();
        for (LogisticsRestrictionEvaluator.RiskPrompt prompt : restrictionResult.getRiskPrompts()) {
            ProcurementLogisticsRecommendationView.RiskPromptView view =
                    new ProcurementLogisticsRecommendationView.RiskPromptView();
            view.setSeverity(prompt.getSeverity());
            view.setMessage(prompt.getMessage());
            view.setManualConfirmRequired(prompt.isManualConfirmRequired());
            prompts.add(view);
        }
        return prompts;
    }

    private String optionReason(Candidate candidate) {
        List<String> fragments = new ArrayList<>();
        if (candidate.estimate.isComputable()) {
            fragments.add(candidate.estimate.getBreakdownText());
        } else {
            fragments.add(candidate.estimate.getReason());
        }
        for (LogisticsRestrictionEvaluator.RiskPrompt prompt : candidate.restrictionResult.getRiskPrompts()) {
            fragments.add(prompt.getMessage());
        }
        return String.join("；", fragments);
    }

    private LogisticsShipmentCostEstimator.ShipmentFacts shipmentFacts(ProcurementLogisticsRequirementRow requirement) {
        return new LogisticsShipmentCostEstimator.ShipmentFacts(
                requirement.getPackageLengthCm(),
                requirement.getPackageWidthCm(),
                requirement.getPackageHeightCm(),
                requirement.getUnitWeightGrams(),
                requirement.getQuantity(),
                requirement.getTransportMode()
        );
    }

    private LogisticsRestrictionEvaluator.CargoFacts cargoFacts(ProcurementLogisticsRequirementRow requirement) {
        return new LogisticsRestrictionEvaluator.CargoFacts(
                requirement.getCargoAttributes(),
                requirement.getPackageLengthCm(),
                requirement.getPackageWidthCm(),
                requirement.getPackageHeightCm(),
                requirement.getUnitWeightGrams(),
                requirement.getQuantity()
        );
    }

    private ProcurementLogisticsRecommendationView.EvidenceView evidence(LogisticsQuoteFactSourceLineage lineage) {
        ProcurementLogisticsRecommendationView.EvidenceView evidence =
                new ProcurementLogisticsRecommendationView.EvidenceView();
        if (lineage == null) {
            return evidence;
        }
        evidence.setSourceType(lineage.getSourceType());
        evidence.setSourceTaskId(lineage.getSourceTaskId());
        evidence.setSourceResultId(lineage.getSourceResultId());
        evidence.setSourceVersionId(lineage.getSourceVersionId());
        evidence.setSourceVersionItemId(lineage.getSourceVersionItemId());
        evidence.setSourceFileName(lineage.getSourceFileName());
        evidence.setSourceLocator(lineage.getSourceLocator());
        return evidence;
    }

    private String billingUnit(String transportMode) {
        return "sea".equalsIgnoreCase(transportMode) ? "cbm" : "kg";
    }

    private String transportModeLabel(String transportMode) {
        return "sea".equalsIgnoreCase(transportMode) ? "海运" : "空运";
    }

    private String leadTimeText(LogisticsServiceLineFact serviceLine) {
        Integer min = serviceLine.getEstimatedDaysMin();
        Integer max = serviceLine.getEstimatedDaysMax();
        if (min != null && max != null) {
            return min.equals(max) ? min + " 天" : min + "-" + max + " 天";
        }
        if (min != null) {
            return min + " 天起";
        }
        if (max != null) {
            return max + " 天内";
        }
        return null;
    }

    private String displayText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private static class Candidate {
        private final LogisticsServiceLineFact serviceLine;
        private final LogisticsCargoCategoryFact category;
        private final LogisticsPriceRuleFact priceRule;
        private final LogisticsShipmentCostEstimator.CostEstimate estimate;
        private final LogisticsRestrictionEvaluator.EvaluationResult restrictionResult;

        private Candidate(
                LogisticsServiceLineFact serviceLine,
                LogisticsCargoCategoryFact category,
                LogisticsPriceRuleFact priceRule,
                LogisticsShipmentCostEstimator.CostEstimate estimate,
                LogisticsRestrictionEvaluator.EvaluationResult restrictionResult
        ) {
            this.serviceLine = serviceLine;
            this.category = category;
            this.priceRule = priceRule;
            this.estimate = estimate;
            this.restrictionResult = restrictionResult;
        }

        private boolean isSelectable() {
            return estimate.isComputable() && !restrictionResult.isHardRestricted();
        }
    }
}
