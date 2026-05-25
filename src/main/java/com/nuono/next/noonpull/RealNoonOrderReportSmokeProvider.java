package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class RealNoonOrderReportSmokeProvider extends AbstractRealNoonReportSmokeProvider
        implements NoonOrderReportSmokeProvider {

    public RealNoonOrderReportSmokeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.report.export-create-url:https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/create}") String createUrl,
            @Value("${nuono.noon.pull.real-provider.report.export-status-url:https://noon-catalog.noon.partners/_svc/mp-partner-impex-api/export/status}") String statusUrl,
            @Value("${nuono.noon.pull.real-provider.report.download-proxy-url-template:}") String downloadProxyUrlTemplate
    ) {
        super(objectMapper, bindingResolver, sessionFactory, createUrl, statusUrl, downloadProxyUrlTemplate);
    }

    @Override
    protected String emptyReportCsv() {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add("id_partner");
        joiner.add("src_country");
        joiner.add("country_code");
        joiner.add("dest_country");
        joiner.add("bayan_nr");
        for (String column : NoonOrderReportDescriptor.requiredColumns()) {
            if (!"id_partner".equals(column)
                    && !"src_country".equals(column)
                    && !"country_code".equals(column)
                    && !"dest_country".equals(column)) {
                joiner.add(column);
            }
        }
        return joiner + "\n";
    }
}
