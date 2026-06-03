package com.nuono.next.productanalysis;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleTimelineProjector {

    private static final List<String> ORDER = List.of("new", "growth", "stable", "decline", "longTail");

    public ProductLifecycleTimelineProjection project(ProductLifecycleTimelineProjectionInput input) {
        if (!hasText(input.getCurrentLifecycleCode())) {
            return missing("lifecycle_missing", "商品尚未计算生命周期。", "currentLifecycle");
        }
        if ("data_insufficient".equals(input.getCurrentLifecycleCode())) {
            return missing(
                    "lifecycle_data_insufficient",
                    "当前生命周期为数据不足，不能生成未来阶段时间线。",
                    "currentLifecycle=data_insufficient"
            );
        }
        if (input.getPeriodConfig() == null || !input.getPeriodConfig().isRuleConfigAvailable()) {
            return missing(
                    "lifecycle_rule_config_missing",
                    "缺少已发布生命周期规则配置，无法计算。",
                    "lifecycleRuleConfig"
            );
        }
        if (input.getCurrentStageStartDate() == null
                || input.getAnalysisDate() == null
                || input.getCurrentStageStartDate().isAfter(input.getAnalysisDate())) {
            return missing(
                    "lifecycle_stage_start_date_missing",
                    "无法确定当前生命周期阶段开始日期。",
                    "currentStageStartDate"
            );
        }
        List<String> missingPeriods = missingPeriodRequirements(input);
        if (!missingPeriods.isEmpty()) {
            return ProductLifecycleTimelineProjection.missing(
                    "lifecycle_period_config_missing",
                    "生命周期周期参数缺失，无法计算。",
                    missingPeriods
            );
        }

        int elapsedDays = Math.toIntExact(ChronoUnit.DAYS.between(
                input.getCurrentStageStartDate(),
                input.getAnalysisDate()
        ) + 1);
        if (isTerminal(input.getCurrentLifecycleCode())) {
            List<ProductLifecycleTimelinePointView> futureTimeline = new ArrayList<>();
            for (int offset = 1; offset <= input.getForecastDays(); offset++) {
                futureTimeline.add(new ProductLifecycleTimelinePointView(
                        input.getAnalysisDate().plusDays(offset),
                        input.getCurrentLifecycleCode(),
                        labelFor(input.getCurrentLifecycleCode(), input)
                ));
            }
            return new ProductLifecycleTimelineProjection(
                    "ready",
                    "生命周期时间线已生成。",
                    List.of(),
                    elapsedDays,
                    0,
                    null,
                    null,
                    null,
                    futureTimeline
            );
        }
        int currentDurationDays = input.getPeriodConfig().getDurationDays(input.getCurrentLifecycleCode());
        int remainingDays = Math.max(0, currentDurationDays - elapsedDays);
        List<ProductLifecycleTimelinePointView> futureTimeline = new ArrayList<>();
        String previousCode = input.getCurrentLifecycleCode();
        String nextCode = null;
        LocalDate nextTransitionDate = null;

        for (int offset = 1; offset <= input.getForecastDays(); offset++) {
            LocalDate date = input.getAnalysisDate().plusDays(offset);
            String lifecycleCode = lifecycleCodeAt(
                    input.getCurrentLifecycleCode(),
                    input.getCurrentStageStartDate(),
                    date,
                    input.getPeriodConfig()
            );
            if (!lifecycleCode.equals(previousCode) && nextCode == null) {
                nextCode = lifecycleCode;
                nextTransitionDate = date;
            }
            futureTimeline.add(new ProductLifecycleTimelinePointView(
                    date,
                    lifecycleCode,
                    labelFor(lifecycleCode, input)
            ));
            previousCode = lifecycleCode;
        }

        return new ProductLifecycleTimelineProjection(
                "ready",
                "生命周期时间线已生成。",
                List.of(),
                elapsedDays,
                remainingDays,
                nextCode,
                nextCode == null ? null : labelFor(nextCode, input),
                nextTransitionDate,
                futureTimeline
        );
    }

    private List<String> missingPeriodRequirements(ProductLifecycleTimelineProjectionInput input) {
        List<String> missing = new ArrayList<>();
        String code = input.getCurrentLifecycleCode();
        LocalDate stageStartDate = input.getCurrentStageStartDate();
        LocalDate horizonDate = input.getAnalysisDate().plusDays(input.getForecastDays());
        while (!isTerminal(code)) {
            Integer durationDays = input.getPeriodConfig() == null ? null : input.getPeriodConfig().getDurationDays(code);
            if (durationDays == null || durationDays <= 0) {
                missing.add(code + ".durationDays");
                break;
            }
            LocalDate transitionDate = stageStartDate.plusDays(durationDays);
            if (transitionDate.isAfter(horizonDate)) {
                break;
            }
            code = nextStage(code);
            stageStartDate = transitionDate;
        }
        return missing;
    }

    private String lifecycleCodeAt(
            String currentCode,
            LocalDate currentStageStartDate,
            LocalDate date,
            ProductLifecycleStagePeriodConfig periodConfig
    ) {
        String code = currentCode;
        LocalDate stageStartDate = currentStageStartDate;
        while (!isTerminal(code)) {
            Integer durationDays = periodConfig.getDurationDays(code);
            if (durationDays == null || durationDays <= 0) {
                return code;
            }
            LocalDate transitionDate = stageStartDate.plusDays(durationDays);
            if (date.isBefore(transitionDate)) {
                return code;
            }
            code = nextStage(code);
            stageStartDate = transitionDate;
        }
        return code;
    }

    private ProductLifecycleTimelineProjection missing(String state, String message, String requirement) {
        return ProductLifecycleTimelineProjection.missing(state, message, List.of(requirement));
    }

    private boolean isTerminal(String code) {
        return "longTail".equals(code);
    }

    private String nextStage(String code) {
        int index = ORDER.indexOf(code);
        if (index < 0 || index >= ORDER.size() - 1) {
            return code;
        }
        return ORDER.get(index + 1);
    }

    private String labelFor(String code, ProductLifecycleTimelineProjectionInput input) {
        if (code.equals(input.getCurrentLifecycleCode()) && hasText(input.getCurrentLifecycleLabel())) {
            return input.getCurrentLifecycleLabel();
        }
        switch (code) {
            case "new":
                return "新品期";
            case "growth":
                return "成长期";
            case "stable":
                return "稳定期";
            case "decline":
                return "衰退期";
            case "longTail":
                return "长尾期";
            default:
                return code;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
