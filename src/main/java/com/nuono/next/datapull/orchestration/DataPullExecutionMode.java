package com.nuono.next.datapull.orchestration;

import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** One deployment-time choice between the predecessor schedulers and the daily pull runtime. */
public enum DataPullExecutionMode {
    LEGACY,
    RUNTIME;

    public static final String PROPERTY = "nuono.data-pull.execution-mode";
    public static final String ENVIRONMENT_VARIABLE = "NUONO_DATA_PULL_EXECUTION_MODE";
    public static final String RETIRED_ENABLED_PROPERTY = "nuono.data-pull.runtime.enabled";
    public static final String RETIRED_ENABLED_ENVIRONMENT_VARIABLE =
            "NUONO_DATA_PULL_RUNTIME_ENABLED";

    public static DataPullExecutionMode resolve(Environment environment) {
        if (environment == null) {
            return LEGACY;
        }
        return parse(environment.getProperty(PROPERTY));
    }

    public static DataPullExecutionMode parse(String value) {
        if (!StringUtils.hasText(value)) {
            return LEGACY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "Invalid " + PROPERTY + "; expected LEGACY or RUNTIME",
                    invalid
            );
        }
    }
}
