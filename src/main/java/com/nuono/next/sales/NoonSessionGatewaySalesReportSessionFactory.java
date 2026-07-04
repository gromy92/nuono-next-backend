package com.nuono.next.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "nuono.sales.noon.report-provider", name = "enabled", havingValue = "true")
public class NoonSessionGatewaySalesReportSessionFactory implements NoonSalesReportSessionFactory {

    private final NoonSessionGateway noonSessionGateway;

    public NoonSessionGatewaySalesReportSessionFactory(NoonSessionGateway noonSessionGateway) {
        this.noonSessionGateway = noonSessionGateway;
    }

    @Override
    public NoonSalesReportSession login(NoonSalesReportBinding binding) {
        NoonSession session = StringUtils.hasText(binding.getNoonEmailAuthCode())
                ? noonSessionGateway.loginWithEmailAuthCode(
                binding.getOwnerUserId(),
                binding.getNoonUser(),
                binding.getNoonEmailAuthCode(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        )
                : noonSessionGateway.hasConfiguredMerchantEmailLogin()
                ? noonSessionGateway.loginWithConfiguredEmailAuthCode(
                binding.getOwnerUserId(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        )
                : noonSessionGateway.login(
                binding.getOwnerUserId(),
                binding.getNoonUser(),
                binding.getNoonPassword(),
                binding.getPersistedCookie(),
                binding.getProjectCode(),
                binding.getStoreCode()
        );
        return new GatewaySessionAdapter(session);
    }

    private static class GatewaySessionAdapter implements NoonSalesReportSession {
        private final NoonSession session;

        private GatewaySessionAdapter(NoonSession session) {
            this.session = session;
        }

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            return session.postJson(url, body, withProject, extraHeaders);
        }

        @Override
        public String getText(String url, boolean withProject, Map<String, String> extraHeaders) {
            return session.getText(url, withProject, extraHeaders);
        }
    }
}
