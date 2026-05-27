package com.nuono.next.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nuono.sales.noon.report-provider", name = "enabled", havingValue = "true")
public class NoonProductViewsSalesReportProvider implements NoonSalesReportProvider {

    private final NoonSalesReportBindingResolver bindingResolver;
    private final NoonProductViewsSalesReportExporter exporter;

    public NoonProductViewsSalesReportProvider(
            NoonSalesReportBindingResolver bindingResolver,
            NoonProductViewsSalesReportExporter exporter
    ) {
        this.bindingResolver = bindingResolver;
        this.exporter = exporter;
    }

    @Override
    public NoonSalesReportPayload fetch(NoonSalesReportRequest request) {
        NoonSalesReportBinding binding = bindingResolver.resolve(request);
        return exporter.export(binding, request.getDateFrom(), request.getDateTo());
    }
}
