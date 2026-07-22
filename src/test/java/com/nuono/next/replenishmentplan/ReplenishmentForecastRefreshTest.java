package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.salesforecast.SalesForecastOverviewView;
import com.nuono.next.salesforecast.SalesForecastRunRepository;
import com.nuono.next.salesforecast.SalesForecastService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplenishmentForecastRefreshTest {

    @Test
    void refreshesForecastAndReadsTheExactReturnedRunBeforeBuildingThePlan() {
        SalesForecastService forecastService = mock(SalesForecastService.class);
        SalesForecastRunRepository forecastRunRepository = mock(SalesForecastRunRepository.class);
        ReplenishmentPlanRepository repository = mock(ReplenishmentPlanRepository.class);
        ReplenishmentPlanConfigResolver configResolver = mock(ReplenishmentPlanConfigResolver.class);
        ReplenishmentPlanRecords.PlanQuery query = new ReplenishmentPlanRecords.PlanQuery(
                307L,
                "STR108065-NAE",
                "AE"
        );
        LocalDate refreshedFactDate = LocalDate.of(2026, 7, 20);
        when(configResolver.resolve(307L, "STR108065-NAE", "AE"))
                .thenReturn(ReplenishmentPlanConfig.defaultBasicV1());
        when(forecastService.getOverview(any())).thenReturn(new SalesForecastOverviewView(
                "ready",
                200L,
                "STR108065-NAE",
                "AE",
                refreshedFactDate,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                "SALES_FORECAST_V1_4",
                "default",
                null,
                List.of()
        ));
        when(forecastRunRepository.listResults(200L)).thenReturn(List.of());
        DefaultReplenishmentPlanService service = new DefaultReplenishmentPlanService(
                forecastService,
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.fixed(Instant.parse("2026-07-22T01:00:00Z"), ZoneOffset.UTC)
        );

        ReplenishmentPlanRecords.PlanOverviewView overview = service.getOverview(query);

        assertEquals(refreshedFactDate, overview.getAnchorDate());
        assertTrue(overview.getRows().isEmpty());
        verify(forecastService).getOverview(any());
        verify(forecastRunRepository).listResults(200L);
    }
}
