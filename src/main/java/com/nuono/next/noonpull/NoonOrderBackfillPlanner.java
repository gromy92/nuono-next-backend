package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NoonOrderBackfillPlanner {

    public NoonOrderBackfillPlan plan(LocalDate dateFrom, LocalDate dateTo, int maxDaysPerWindow, int maxWindowsPerRun) {
        if (dateFrom == null || dateTo == null || dateFrom.isAfter(dateTo)) {
            return new NoonOrderBackfillPlan(List.of(), null);
        }
        int daysPerWindow = Math.max(1, maxDaysPerWindow);
        int windowsPerRun = Math.max(1, maxWindowsPerRun);
        List<NoonOrderBackfillPlan.Window> windows = new ArrayList<>();
        LocalDate cursor = dateFrom;
        while (!cursor.isAfter(dateTo) && windows.size() < windowsPerRun) {
            LocalDate windowTo = cursor.plusDays(daysPerWindow - 1L);
            if (windowTo.isAfter(dateTo)) {
                windowTo = dateTo;
            }
            windows.add(new NoonOrderBackfillPlan.Window(cursor, windowTo));
            cursor = windowTo.plusDays(1);
        }
        LocalDate nextResumeDate = cursor.isAfter(dateTo) ? null : cursor;
        return new NoonOrderBackfillPlan(windows, nextResumeDate);
    }
}
