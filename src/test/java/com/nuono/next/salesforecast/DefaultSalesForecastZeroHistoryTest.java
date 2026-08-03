package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.sales.SalesActivityWindowRepository;
import com.nuono.next.sales.SalesFactRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSalesForecastZeroHistoryTest {

    private static final SalesForecastQuery QUERY =
            new SalesForecastQuery(10002L, "STR245027-NSA", "SA");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 29);

    @Mock
    private SalesFactRepository salesFactRepository;
    @Mock
    private SalesForecastRunRepository runRepository;
    @Mock
    private SalesForecastStockRepository stockRepository;
    @Mock
    private SalesActivityWindowRepository activityWindowRepository;
    @Mock
    private SalesForecastFollowUpRepository followUpRepository;

    private DefaultSalesForecastService service;

    @BeforeEach
    void setUp() {
        service = new DefaultSalesForecastService(
                salesFactRepository,
                runRepository,
                stockRepository,
                activityWindowRepository,
                followUpRepository,
                new SalesForecastFeatureBuilder(),
                new DefaultSalesForecastEngine(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC)
        );
        when(salesFactRepository.findLatestFactDate(10002L, "STR245027-NSA", "SA")).thenReturn(null);
    }

    @Test
    void forecastsMaintainedProductsWhenTheStoreHasNoSalesFactsAtAll() {
        List<SalesForecastStockSnapshot> products = List.of(new SalesForecastStockSnapshot(
                10002L,
                "STR245027-NSA",
                "SA",
                "PAPERSAY-COLD",
                "SKU-COLD",
                12,
                "PAPERSAY",
                "gift_wrap_paper",
                "paper",
                "New maintained product"
        ));
        AtomicReference<List<SalesForecastResultRecord>> savedResults = new AtomicReference<>(List.of());
        when(stockRepository.listCurrentStock(QUERY)).thenReturn(products);
        when(salesFactRepository.list(any())).thenReturn(List.of());
        when(runRepository.saveRunWithResults(any(), anyList())).thenAnswer(invocation -> {
            SalesForecastRunRecord run = invocation.getArgument(0);
            savedResults.set(List.copyOf(invocation.getArgument(1)));
            return run.withIdAndCalculatedAt(70001L, LocalDateTime.of(2026, 6, 29, 0, 0));
        });
        when(runRepository.listResults(70001L)).thenAnswer(ignored -> savedResults.get());
        when(followUpRepository.listMarked(QUERY)).thenReturn(List.of());

        SalesForecastOverviewView overview = service.getOverview(QUERY);

        assertEquals("ready", overview.getState());
        assertEquals(TODAY, overview.getSourceDataDate());
        assertEquals(1, overview.getRows().size());
        SalesForecastResultRecord coldStart = savedResults.get().get(0);
        assertEquals("PAPERSAY-COLD", coldStart.getPartnerSku());
        assertEquals(0, coldStart.getObservedDays());
        assertEquals(0, coldStart.getForecastUnits30());
        assertTrue(coldStart.getWarningCodes().contains("no_sales_training_data"));
    }

    @Test
    void remainsEmptyWhenThereAreNeitherMaintainedProductsNorSalesFacts() {
        when(stockRepository.listCurrentStock(QUERY)).thenReturn(List.of());

        SalesForecastOverviewView overview = service.getOverview(QUERY);

        assertEquals("empty", overview.getState());
        assertEquals("missing_sales_data", overview.getEmptyState().getCode());
        verify(runRepository, never()).saveRunWithResults(any(), anyList());
    }
}
