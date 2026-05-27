package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NoonOrderBackfillPlannerTest {

    @Test
    void shouldSplitHistoricalOrderBackfillIntoBudgetedDateWindows() {
        NoonOrderBackfillPlanner planner = new NoonOrderBackfillPlanner();

        NoonOrderBackfillPlan plan = planner.plan(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 12),
                5,
                2
        );

        assertEquals(2, plan.getWindows().size());
        assertEquals(LocalDate.of(2026, 5, 1), plan.getWindows().get(0).getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 5), plan.getWindows().get(0).getDateTo());
        assertEquals(LocalDate.of(2026, 5, 6), plan.getWindows().get(1).getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 10), plan.getWindows().get(1).getDateTo());
        assertEquals(LocalDate.of(2026, 5, 11), plan.getNextResumeDate());
    }
}
