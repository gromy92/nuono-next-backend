package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.salesforecast.SalesForecastQuery;
import com.nuono.next.salesforecast.SalesForecastRunRecord;
import com.nuono.next.salesforecast.SalesForecastRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplenishmentForecastRefreshTest {

    @Test
    void readsLatestCompletedForecastWithoutTriggeringForecastCalculation() {
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
        SalesForecastRunRecord completedRun = new SalesForecastRunRecord(
                200L,
                307L,
                "STR108065-NAE",
                "AE",
                refreshedFactDate,
                "SALES_FORECAST_V1_5",
                "default",
                "succeeded",
                0,
                LocalDateTime.of(2026, 7, 22, 9, 0)
        );
        when(forecastRunRepository.findLatestCompleted(any(SalesForecastQuery.class))).thenReturn(completedRun);
        when(forecastRunRepository.listResults(200L)).thenReturn(List.of());
        DefaultReplenishmentPlanService service = new DefaultReplenishmentPlanService(
                forecastRunRepository,
                repository,
                configResolver,
                new ReplenishmentPlanCalculator(),
                Clock.fixed(Instant.parse("2026-07-22T01:00:00Z"), ZoneOffset.UTC)
        );

        ReplenishmentPlanOverviewView overview = service.getOverview(query);

        assertEquals(refreshedFactDate, overview.getAnchorDate());
        assertTrue(overview.getRows().isEmpty());
        verify(forecastRunRepository).findLatestCompleted(any(SalesForecastQuery.class));
        verify(forecastRunRepository).listResults(200L);
        verify(forecastRunRepository, never()).saveRun(any());
        verify(forecastRunRepository, never()).saveResults(any(), any());
        verify(forecastRunRepository, never()).saveRunWithResults(any(), any());
    }
}
