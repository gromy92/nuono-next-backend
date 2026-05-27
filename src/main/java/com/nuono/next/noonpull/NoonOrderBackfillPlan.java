package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoonOrderBackfillPlan {
    private final List<Window> windows;
    private final LocalDate nextResumeDate;

    public NoonOrderBackfillPlan(List<Window> windows, LocalDate nextResumeDate) {
        this.windows = windows == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(windows));
        this.nextResumeDate = nextResumeDate;
    }

    public List<Window> getWindows() {
        return windows;
    }

    public LocalDate getNextResumeDate() {
        return nextResumeDate;
    }

    public static class Window {
        private final LocalDate dateFrom;
        private final LocalDate dateTo;

        public Window(LocalDate dateFrom, LocalDate dateTo) {
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
        }

        public LocalDate getDateFrom() {
            return dateFrom;
        }

        public LocalDate getDateTo() {
            return dateTo;
        }
    }
}
