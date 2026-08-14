package com.nuono.next.noonpull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Prevents scheduled and gap-planned tasks from being created while the shared Noon account
 * requires an explicit manual login.
 */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class LocalDbNoonProviderAvailability implements NoonProviderAvailability {
    private final NoonAccountSessionAttentionPort accountSessionAttention;

    public LocalDbNoonProviderAvailability(
            NoonAccountSessionAttentionPort accountSessionAttention
    ) {
        this.accountSessionAttention = accountSessionAttention;
    }

    @Override
    public boolean isAvailable(NoonPullPlanRecord plan) {
        return accountSessionAttention == null || !accountSessionAttention.blocksProviderCalls();
    }
}
