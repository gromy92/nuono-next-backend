package com.nuono.next.noonpull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition;

/** Runtime providers are automatic only in RUNTIME; LEGACY keeps its existing provider flag. */
final class NoonPullRealProviderCondition extends AnyNestedCondition {

    NoonPullRealProviderCondition() {
        super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
    private static final class RuntimeMode {
    }

    @ConditionalOnProperty(
            prefix = "nuono.noon.pull.real-provider",
            name = "enabled",
            havingValue = "true"
    )
    private static final class LegacyProviderEnabled {
    }
}
