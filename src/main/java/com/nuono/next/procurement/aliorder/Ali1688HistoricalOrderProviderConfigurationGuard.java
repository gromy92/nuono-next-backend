package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails startup when a production DP-10 execution path is enabled without the real incremental adapter contract. */
@Component
@Profile("local-db")
public class Ali1688HistoricalOrderProviderConfigurationGuard implements SmartInitializingSingleton {

    private final Ali1688HistoricalOrderOpenApiProperties properties;
    private final Environment environment;

    public Ali1688HistoricalOrderProviderConfigurationGuard(
            Ali1688HistoricalOrderOpenApiProperties properties,
            Environment environment
    ) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean dp10ExecutionEnabled = DataPullExecutionMode.resolve(environment)
                == DataPullExecutionMode.RUNTIME
                || properties.isRequiredForDp10();
        if ((dp10ExecutionEnabled || properties.isEnabled()) && !properties.hasProductionDp10Configuration()) {
            throw new IllegalStateException(
                    "DP-10 requires a real 1688 OpenAPI adapter with official modified-time incremental configuration; "
                            + "Fake provider is forbidden under local-db."
            );
        }
    }
}
