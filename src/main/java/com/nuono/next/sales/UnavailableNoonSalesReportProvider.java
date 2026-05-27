package com.nuono.next.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "nuono.sales.noon.report-provider",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class UnavailableNoonSalesReportProvider implements NoonSalesReportProvider {

    @Override
    public NoonSalesReportPayload fetch(NoonSalesReportRequest request) {
        throw new IllegalStateException("Noon sales report provider is not configured for automatic sync.");
    }
}
