package com.nuono.next.productanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductLifecycleTimelineProjectorTest {

    private final ProductLifecycleTimelineProjector projector = new ProductLifecycleTimelineProjector();

    @Test
    void projectsNinetyDailyLifecyclePointsAndFirstTransition() {
        ProductLifecycleTimelineProjection projection = projector.project(new ProductLifecycleTimelineProjectionInput(
                "new",
                "新品期",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 21),
                new ProductLifecycleStagePeriodConfig(Map.of(
                        "new", 30,
                        "growth", 45,
                        "stable", 180,
                        "decline", 30
                )),
                90
        ));

        assertEquals("ready", projection.getQualityState());
        assertEquals(21, projection.getCurrentStageElapsedDays());
        assertEquals(9, projection.getCurrentStageRemainingDays());
        assertEquals("growth", projection.getNextLifecycleCode());
        assertEquals("成长期", projection.getNextLifecycleLabel());
        assertEquals(LocalDate.of(2026, 5, 31), projection.getNextTransitionDate());
        assertEquals(90, projection.getFutureTimeline().size());
        assertEquals(LocalDate.of(2026, 5, 22), projection.getFutureTimeline().get(0).getDate());
        assertEquals("new", projection.getFutureTimeline().get(0).getLifecycleCode());
        assertEquals(LocalDate.of(2026, 5, 31), projection.getFutureTimeline().get(9).getDate());
        assertEquals("growth", projection.getFutureTimeline().get(9).getLifecycleCode());
    }

    @Test
    void returnsMissingPeriodConfigurationWhenCurrentStageDurationIsAbsent() {
        ProductLifecycleTimelineProjection projection = projector.project(new ProductLifecycleTimelineProjectionInput(
                "growth",
                "成长期",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 21),
                new ProductLifecycleStagePeriodConfig(Map.of("new", 60)),
                90
        ));

        assertEquals("lifecycle_period_config_missing", projection.getQualityState());
        assertEquals(0, projection.getFutureTimeline().size());
        assertTrue(projection.getMissingRequirements().contains("growth.durationDays"));
    }

    @Test
    void returnsMissingStageStartDateWithoutSyntheticTimeline() {
        ProductLifecycleTimelineProjection projection = projector.project(new ProductLifecycleTimelineProjectionInput(
                "stable",
                "稳定期",
                null,
                LocalDate.of(2026, 5, 21),
                new ProductLifecycleStagePeriodConfig(Map.of("stable", 180)),
                90
        ));

        assertEquals("lifecycle_stage_start_date_missing", projection.getQualityState());
        assertEquals(0, projection.getFutureTimeline().size());
        assertTrue(projection.getMissingRequirements().contains("currentStageStartDate"));
    }

    @Test
    void returnsDataInsufficientWithoutSyntheticTimeline() {
        ProductLifecycleTimelineProjection projection = projector.project(new ProductLifecycleTimelineProjectionInput(
                "data_insufficient",
                "数据不足",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 21),
                new ProductLifecycleStagePeriodConfig(Map.of("new", 60)),
                90
        ));

        assertEquals("lifecycle_data_insufficient", projection.getQualityState());
        assertEquals(0, projection.getFutureTimeline().size());
        assertTrue(projection.getMissingRequirements().contains("currentLifecycle=data_insufficient"));
    }
}
