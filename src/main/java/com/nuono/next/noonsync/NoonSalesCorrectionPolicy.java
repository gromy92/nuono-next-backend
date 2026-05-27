package com.nuono.next.noonsync;

import java.util.List;

public class NoonSalesCorrectionPolicy {

    public static final int MIN_FIRST_VERSION_WINDOW_DAYS = 30;
    public static final int MAX_FIRST_VERSION_WINDOW_DAYS = 60;
    public static final int DEFAULT_WINDOW_DAYS = 45;
    public static final int WEEKLY_INTERVAL_DAYS = 7;
    public static final List<Integer> DEFAULT_CANDIDATE_WINDOW_DAYS = List.of(30, 45, 60);

    private final int windowDays;
    private final int intervalDays;

    private NoonSalesCorrectionPolicy(int windowDays, int intervalDays) {
        if (windowDays < MIN_FIRST_VERSION_WINDOW_DAYS || windowDays > MAX_FIRST_VERSION_WINDOW_DAYS) {
            throw new IllegalArgumentException("Sales correction window must stay within the first-version 30-60 day range.");
        }
        this.windowDays = windowDays;
        this.intervalDays = intervalDays;
    }

    public static NoonSalesCorrectionPolicy defaultPolicy() {
        return weekly(DEFAULT_WINDOW_DAYS);
    }

    public static NoonSalesCorrectionPolicy weekly(int windowDays) {
        return new NoonSalesCorrectionPolicy(windowDays, WEEKLY_INTERVAL_DAYS);
    }

    public int getWindowDays() {
        return windowDays;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public List<Integer> getCandidateWindowDays() {
        return DEFAULT_CANDIDATE_WINDOW_DAYS;
    }
}
