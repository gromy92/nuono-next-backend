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

    @Override
    public NoonSalesReportExportStatus createExport(NoonSalesReportRequest request) {
        NoonSalesReportBinding binding = bindingResolver.resolve(request);
        return exporter.createExport(binding, request.getDateFrom(), request.getDateTo());
    }

    @Override
    public NoonSalesReportExportStatus pollExport(NoonSalesReportRequest request, String exportCode) {
        NoonSalesReportBinding binding = bindingResolver.resolve(request);
        return exporter.pollExport(binding, exportCode);
    }

    @Override
    public NoonSalesReportPayload download(NoonSalesReportRequest request, NoonSalesReportExportStatus status) {
        NoonSalesReportBinding binding = bindingResolver.resolve(request);
        return exporter.download(binding, status);
    }

    @Override
    public int maxPollAttempts() {
        return exporter.maxPollAttempts();
    }

    @Override
    public void waitBeforeNextPoll() {
        exporter.waitBeforeNextPoll();
    }
}
