package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoonProductViewsSalesReportProviderTest {

    @Mock
    private NoonSalesReportBindingResolver bindingResolver;

    @Mock
    private NoonProductViewsSalesReportExporter exporter;

    @Test
    void resolvesBindingAndDelegatesFetchToProductViewsExporter() {
        NoonProductViewsSalesReportProvider provider = new NoonProductViewsSalesReportProvider(bindingResolver, exporter);
        NoonSalesReportRequest request = new NoonSalesReportRequest(
                10002L,
                245027L,
                "STR245027-NSA",
                "SA",
                LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 19)
        );
        NoonSalesReportBinding binding = binding();
        NoonSalesReportPayload expected = new NoonSalesReportPayload("from-noon.csv", "csv");
        when(bindingResolver.resolve(request)).thenReturn(binding);
        when(exporter.export(binding, request.getDateFrom(), request.getDateTo())).thenReturn(expected);

        NoonSalesReportPayload actual = provider.fetch(request);

        assertEquals(expected, actual);
        ArgumentCaptor<NoonSalesReportRequest> requestCaptor = ArgumentCaptor.forClass(NoonSalesReportRequest.class);
        verify(bindingResolver).resolve(requestCaptor.capture());
        assertEquals("STR245027-NSA", requestCaptor.getValue().getStoreCode());
        verify(exporter).export(binding, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 19));
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                10002L,
                245027L,
                "PRJ245027",
                "STR245027-NSA",
                "SA",
                "245027",
                "project-user@example.com",
                "secret",
                "session=already-present"
        );
    }
}
