package com.nuono.next.salesforecast;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesForecastDailyForecast {

    private final int dayIndex;
    private final LocalDate forecastDate;
    private final BigDecimal calendarFactor;
    private final BigDecimal forecastUnits;

    public SalesForecastDailyForecast(
            int dayIndex,
            LocalDate forecastDate,
            BigDecimal calendarFactor,
            BigDecimal forecastUnits
    ) {
        this.dayIndex = dayIndex;
        this.forecastDate = forecastDate;
        this.calendarFactor = calendarFactor;
        this.forecastUnits = forecastUnits;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public LocalDate getForecastDate() {
        return forecastDate;
    }

    public BigDecimal getCalendarFactor() {
        return calendarFactor;
    }

    public BigDecimal getForecastUnits() {
        return forecastUnits;
    }
}
