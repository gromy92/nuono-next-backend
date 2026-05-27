package com.nuono.next.noonsync;

import java.time.Duration;

public class NoonSalesCorrectionMeasurement {

    private final int windowDays;
    private final Duration reportDuration;
    private final int rowVolume;
    private final int observedHistoricalChangeCount;

    public NoonSalesCorrectionMeasurement(
            int windowDays,
            Duration reportDuration,
            int rowVolume,
            int observedHistoricalChangeCount
    ) {
        this.windowDays = windowDays;
        this.reportDuration = reportDuration;
        this.rowVolume = rowVolume;
        this.observedHistoricalChangeCount = observedHistoricalChangeCount;
    }

    public int getWindowDays() {
        return windowDays;
    }

    public Duration getReportDuration() {
        return reportDuration;
    }

    public int getRowVolume() {
        return rowVolume;
    }

    public int getObservedHistoricalChangeCount() {
        return observedHistoricalChangeCount;
    }
}
