package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class RealNoonFinanceTransactionReportProvider extends AbstractRealNoonReportSmokeProvider
        implements NoonFinanceTransactionReportProvider {

    private final String exportCategoryCode;

    public RealNoonFinanceTransactionReportProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.report.export-create-url:https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/create}") String createUrl,
            @Value("${nuono.noon.pull.real-provider.report.export-status-url:https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/status}") String statusUrl,
            @Value("${nuono.noon.pull.real-provider.report.download-proxy-url-template:}") String downloadProxyUrlTemplate,
            @Value("${nuono.noon.pull.real-provider.finance-transaction-report.export-category-code:"
                    + NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE + "}") String exportCategoryCode
    ) {
        super(objectMapper, bindingResolver, sessionFactory, createUrl, statusUrl, downloadProxyUrlTemplate);
        this.exportCategoryCode = StringUtils.hasText(exportCategoryCode)
                ? exportCategoryCode.trim()
                : NoonFinanceTransactionReportDescriptor.DEFAULT_REPORT_TYPE;
    }

    @Override
    protected String emptyReportCsv() {
        return NoonFinanceTransactionReportDescriptor.requiredColumns().stream()
                .map(this::csv)
                .collect(Collectors.joining(",")) + "\n";
    }

    @Override
    protected String exportCategoryCode(NoonReportPullRequest request) {
        return exportCategoryCode;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
