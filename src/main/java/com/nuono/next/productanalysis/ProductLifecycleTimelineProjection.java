package com.nuono.next.productanalysis;

import java.time.LocalDate;
import java.util.List;

public class ProductLifecycleTimelineProjection {

    private final String qualityState;
    private final String qualityMessage;
    private final List<String> missingRequirements;
    private final Integer currentStageElapsedDays;
    private final Integer currentStageRemainingDays;
    private final String nextLifecycleCode;
    private final String nextLifecycleLabel;
    private final LocalDate nextTransitionDate;
    private final List<ProductLifecycleTimelinePointView> futureTimeline;

    public ProductLifecycleTimelineProjection(
            String qualityState,
            String qualityMessage,
            List<String> missingRequirements,
            Integer currentStageElapsedDays,
            Integer currentStageRemainingDays,
            String nextLifecycleCode,
            String nextLifecycleLabel,
            LocalDate nextTransitionDate,
            List<ProductLifecycleTimelinePointView> futureTimeline
    ) {
        this.qualityState = qualityState;
        this.qualityMessage = qualityMessage;
        this.missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
        this.currentStageElapsedDays = currentStageElapsedDays;
        this.currentStageRemainingDays = currentStageRemainingDays;
        this.nextLifecycleCode = nextLifecycleCode;
        this.nextLifecycleLabel = nextLifecycleLabel;
        this.nextTransitionDate = nextTransitionDate;
        this.futureTimeline = futureTimeline == null ? List.of() : List.copyOf(futureTimeline);
    }

    public static ProductLifecycleTimelineProjection missing(
            String qualityState,
            String qualityMessage,
            List<String> missingRequirements
    ) {
        return new ProductLifecycleTimelineProjection(
                qualityState,
                qualityMessage,
                missingRequirements,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    public String getQualityState() {
        return qualityState;
    }

    public String getQualityMessage() {
        return qualityMessage;
    }

    public List<String> getMissingRequirements() {
        return missingRequirements;
    }

    public Integer getCurrentStageElapsedDays() {
        return currentStageElapsedDays;
    }

    public Integer getCurrentStageRemainingDays() {
        return currentStageRemainingDays;
    }

    public String getNextLifecycleCode() {
        return nextLifecycleCode;
    }

    public String getNextLifecycleLabel() {
        return nextLifecycleLabel;
    }

    public LocalDate getNextTransitionDate() {
        return nextTransitionDate;
    }

    public List<ProductLifecycleTimelinePointView> getFutureTimeline() {
        return futureTimeline;
    }
}
