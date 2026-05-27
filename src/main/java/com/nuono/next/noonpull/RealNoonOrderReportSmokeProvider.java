package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class RealNoonOrderReportSmokeProvider extends NoonSalesDashboardExportProvider implements NoonOrderReportSmokeProvider {
    private static final String DEFAULT_GENERATE_URL =
            "https://reports.noon.partners/_vs/mp/mp-inventory-health-api-sales-dashboard/export/generate";
    private static final String DEFAULT_LATEST_URL =
            "https://reports.noon.partners/_vs/mp/mp-inventory-health-api-sales-dashboard/export/latest";

    public RealNoonOrderReportSmokeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.sales-dashboard-export.generate-url:" + DEFAULT_GENERATE_URL + "}") String generateUrl,
            @Value("${nuono.noon.pull.real-provider.sales-dashboard-export.latest-url:" + DEFAULT_LATEST_URL + "}") String latestUrl,
            @Value("${nuono.noon.pull.real-provider.report.download-proxy-url-template:}") String downloadProxyUrlTemplate
    ) {
        super(
                objectMapper,
                bindingResolver,
                sessionFactory,
                StringUtils.hasText(generateUrl) ? generateUrl.trim() : DEFAULT_GENERATE_URL,
                StringUtils.hasText(latestUrl) ? latestUrl.trim() : DEFAULT_LATEST_URL,
                downloadProxyUrlTemplate,
                "sales dashboard export",
                "sales-dashboard-export",
                RealNoonOrderReportSmokeProvider::exportBody
        );
    }

    private static ObjectNode exportBody(
            ObjectMapper objectMapper,
            NoonPullStoreBinding binding,
            NoonReportPullRequest request
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("country_code", siteCode(binding));
        body.set("filters", objectMapper.createObjectNode());
        body.put("search", "");
        body.put("from_date", request.getDateFrom().toString());
        body.put("to_date", request.getDateTo().toString());
        return body;
    }

    private static String siteCode(NoonPullStoreBinding binding) {
        return binding.getSiteCode() == null ? "AE" : binding.getSiteCode().toUpperCase(Locale.ROOT);
    }
}
