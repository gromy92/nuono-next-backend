package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noon.NoonCatalogApiRoutes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class RealNoonSalesReportSmokeProvider extends AbstractRealNoonReportSmokeProvider
        implements NoonSalesReportSmokeProvider {
    private static final String PRODUCT_VIEWS_AND_SALES_EXPORT_CATEGORY =
            "noon_catalog_reports_productviewsandsalesdata";

    public RealNoonSalesReportSmokeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.report.export-create-url:"
                    + NoonCatalogApiRoutes.EXPORT_CREATE + "}") String createUrl,
            @Value("${nuono.noon.pull.real-provider.report.export-status-url:"
                    + NoonCatalogApiRoutes.EXPORT_STATUS + "}") String statusUrl,
            @Value("${nuono.noon.pull.real-provider.report.download-proxy-url-template:}") String downloadProxyUrlTemplate
    ) {
        super(objectMapper, bindingResolver, sessionFactory, createUrl, statusUrl, downloadProxyUrlTemplate);
    }

    @Override
    protected String emptyReportCsv() {
        return "date,sku_parent,units_sold,sales_amount,currency\n";
    }

    @Override
    protected String exportCategoryCode(NoonReportPullRequest request) {
        return PRODUCT_VIEWS_AND_SALES_EXPORT_CATEGORY;
    }
}
