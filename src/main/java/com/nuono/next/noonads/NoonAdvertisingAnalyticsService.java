package com.nuono.next.noonads;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoonAdvertisingAnalyticsService {
    private final NoonAdvertisingRepository repository;
    private final NoonAdvertisingStructureDiagnosticService diagnosticService = new NoonAdvertisingStructureDiagnosticService();

    public NoonAdvertisingAnalyticsService(NoonAdvertisingRepository repository) {
        this.repository = repository;
    }

    public NoonAdvertisingLatestReportWindowView latestReportWindow(NoonAdvertisingScopeQuery query) {
        NoonAdvertisingLatestReportWindowView view = repository.selectLatestReportWindow(query);
        return view == null ? new NoonAdvertisingLatestReportWindowView() : view;
    }

    public NoonAdvertisingDashboardView dashboard(NoonAdvertisingDashboardQuery query) {
        NoonAdvertisingSummaryView adSummary = valueOrDefault(repository.selectAdSummary(query));
        NoonAdvertisingSalesSummaryView salesSummary = valueOrDefault(repository.selectSalesSummary(query));
        salesSummary.setAdSpendShareOfSales(ratio(adSummary.getSpendAmount(), salesSummary.getRevenueShipped()));
        NoonAdvertisingDataStatus dataStatus = valueOrDefault(repository.selectDataStatus(query));
        List<NoonAdvertisingCampaignRow> campaignRows = safeList(repository.selectCampaignRows(query));
        List<NoonAdvertisingProductRow> productRows = safeList(repository.selectProductRows(query));
        List<NoonAdvertisingQueryRow> zeroOrderQueries = safeList(repository.selectZeroOrderQueryRows(query));
        List<NoonAdvertisingQueryRow> winningQueries = safeList(repository.selectWinningQueryRows(query));
        NoonAdvertisingStructureDiagnosticResult diagnostics = diagnosticService.diagnose(
                productRows,
                campaignRows,
                zeroOrderQueries,
                winningQueries
        );

        return new NoonAdvertisingDashboardView(
                adSummary,
                salesSummary,
                campaignRows,
                productRows,
                diagnostics.getProductDiagnostics(),
                diagnostics.getCampaignDiagnostics(),
                zeroOrderQueries,
                winningQueries,
                dataStatus
        );
    }

    private NoonAdvertisingSummaryView valueOrDefault(NoonAdvertisingSummaryView value) {
        return value == null ? new NoonAdvertisingSummaryView() : value;
    }

    private NoonAdvertisingSalesSummaryView valueOrDefault(NoonAdvertisingSalesSummaryView value) {
        return value == null ? new NoonAdvertisingSalesSummaryView() : value;
    }

    private NoonAdvertisingDataStatus valueOrDefault(NoonAdvertisingDataStatus value) {
        return value == null ? new NoonAdvertisingDataStatus() : value;
    }

    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal safeNumerator = numerator == null ? BigDecimal.ZERO : numerator;
        return safeNumerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }
}
