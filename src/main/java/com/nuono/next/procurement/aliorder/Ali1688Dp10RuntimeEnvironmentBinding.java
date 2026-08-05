package com.nuono.next.procurement.aliorder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import org.springframework.core.env.Environment;

/** Proves that Spring's effective DP10 configuration is the attested slot environment. */
final class Ali1688Dp10RuntimeEnvironmentBinding {
    private static final Set<String> REQUIRED_PROFILES = Set.of("local-db");
    private static final long REQUIRED_CONNECTION_TIMEOUT_MILLIS = 5_000L;
    private static final long REQUIRED_POOL_SOCKET_TIMEOUT_MILLIS = 300_000L;

    private Ali1688Dp10RuntimeEnvironmentBinding() {
    }

    static boolean verify(
            Path envFile,
            Environment actualEnvironment,
            Ali1688HistoricalOrderOpenApiProperties actualOpenApi,
            DataPullRuntimeProperties actualRuntime
    ) {
        try {
            Ali1688Dp10OpenApiProbeEnvironment expectedEnvironment =
                    Ali1688Dp10OpenApiProbeEnvironment.load(envFile);
            Ali1688HistoricalOrderOpenApiProperties expectedOpenApi =
                    expectedEnvironment.openApiProperties();
            DataPullRuntimeProperties expectedRuntime =
                    expectedEnvironment.runtimeProperties();
            return expectedEnvironment.matchesRawValues(actualEnvironment)
                    && effectiveDatabaseMatches(expectedEnvironment, actualEnvironment)
                    && effectiveDatabaseDeadlineMatches(actualEnvironment)
                    && activeProfiles(actualEnvironment).equals(REQUIRED_PROFILES)
                    && "local-db".equals(actualEnvironment.getProperty("nuono.stage"))
                    && openApiContract(expectedOpenApi).equals(openApiContract(actualOpenApi))
                    && runtimeContract(expectedRuntime).equals(runtimeContract(actualRuntime));
        } catch (RuntimeException | java.io.IOException invalid) {
            return false;
        }
    }

    private static boolean effectiveDatabaseMatches(
            Ali1688Dp10OpenApiProbeEnvironment expected,
            Environment actual
    ) {
        return Objects.equals(
                expected.require("NUONO_NEXT_DB_URL"),
                actual.getProperty("spring.datasource.url")
        ) && Objects.equals(
                expected.require("NUONO_NEXT_DB_USERNAME"),
                actual.getProperty("spring.datasource.username")
        ) && Objects.equals(
                expected.require("NUONO_NEXT_DB_PASSWORD"),
                actual.getProperty("spring.datasource.password")
        ) && "com.mysql.cj.jdbc.Driver".equals(
                actual.getProperty("spring.datasource.driver-class-name")
        );
    }

    private static boolean effectiveDatabaseDeadlineMatches(Environment actual) {
        return Long.valueOf(REQUIRED_CONNECTION_TIMEOUT_MILLIS).equals(
                actual.getProperty("spring.datasource.hikari.connection-timeout", Long.class)
        ) && Long.valueOf(REQUIRED_CONNECTION_TIMEOUT_MILLIS).equals(
                actual.getProperty(
                        "spring.datasource.hikari.data-source-properties.connectTimeout",
                        Long.class
                )
        ) && Long.valueOf(REQUIRED_POOL_SOCKET_TIMEOUT_MILLIS).equals(
                actual.getProperty(
                        "spring.datasource.hikari.data-source-properties.socketTimeout",
                        Long.class
                )
        ) && Boolean.TRUE.equals(actual.getProperty(
                "spring.datasource.hikari.data-source-properties.queryTimeoutKillsConnection",
                Boolean.class
        )) && "SIMPLE".equalsIgnoreCase(actual.getProperty(
                "mybatis.configuration.default-executor-type"
        ));
    }

    private static Set<String> activeProfiles(Environment environment) {
        return new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
    }

    private static List<Object> openApiContract(
            Ali1688HistoricalOrderOpenApiProperties value
    ) {
        if (value == null) return List.of();
        return Arrays.asList(
                value.isEnabled(),
                value.isRequiredForDp10(),
                value.getAppKey(),
                value.getAppSecret(),
                value.getTokenCipherSecret(),
                value.getAuthorizeUrl(),
                value.getRedirectUri(),
                value.getSite(),
                value.getTokenUrlTemplate(),
                value.getApiGatewayBaseUrl(),
                value.getApiVersion(),
                value.getBuyerOrderListNamespace(),
                value.getBuyerOrderListApiName(),
                value.getBuyerOrderDetailNamespace(),
                value.getBuyerOrderDetailApiName(),
                value.getTimeoutSeconds(),
                value.getPageSize(),
                value.getStateTtlSeconds(),
                value.getPageNumberParameterName(),
                value.getPageSizeParameterName(),
                value.getCursorParameterName(),
                value.getNextCursorResponseFieldNames(),
                value.getModifiedFromParameterName(),
                value.getModifiedToParameterName(),
                value.getHistoryParameterName(),
                value.getModifiedFromFormat(),
                value.getModifiedAtResponseFieldNames(),
                value.getProviderZoneId()
        );
    }

    private static List<Object> runtimeContract(DataPullRuntimeProperties value) {
        if (value == null) return List.of();
        return Arrays.asList(
                value.getSchedulerInitialDelayMs(),
                value.getSchedulerFixedDelayMs(),
                value.getWorkerCount(),
                value.getLeaseSeconds(),
                value.getMaximumClaimsPerTick(),
                value.getBackoffBaseSeconds(),
                value.getBackoffMaximumSeconds(),
                value.getBackoffJitterRatio()
        );
    }
}
