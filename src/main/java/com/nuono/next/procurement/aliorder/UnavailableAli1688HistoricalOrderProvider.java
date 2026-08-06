package com.nuono.next.procurement.aliorder;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production-safe fallback: DP-10 can report a configuration failure but can never persist fixture
 * orders when the real 1688 OpenAPI adapter is disabled.
 */
@Component
@Profile("local-db")
@ConditionalOnProperty(
        prefix = "nuono.procurement.ali1688.historical-order.open-api",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class UnavailableAli1688HistoricalOrderProvider implements Ali1688HistoricalOrderProvider {

    @Override
    public Page fetchOrderList(Ali1688HistoricalOrderRequest request) {
        Page page = new Page(List.of());
        page.setFailureCode(Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED.getCode());
        page.setFailureMessage("DP-10 已拒绝执行：local-db 未配置真实 1688 OpenAPI provider。");
        page.setRetryableFailure(false);
        page.setProgressPercent(0);
        return page;
    }
}
