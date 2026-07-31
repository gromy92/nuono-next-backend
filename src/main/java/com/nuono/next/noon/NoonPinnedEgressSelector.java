package com.nuono.next.noon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NoonPinnedEgressSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoonPinnedEgressSelector.class);

    private final NoonProxyRouteFactory routeFactory;
    private final int maxAttempts;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    NoonPinnedEgressSelector(
            NoonProxyRouteFactory routeFactory,
            int maxAttempts,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
        this.routeFactory = routeFactory;
        this.maxAttempts = Math.min(3, Math.max(1, maxAttempts));
        this.connectTimeoutMillis = Math.max(250, connectTimeoutMillis);
        this.readTimeoutMillis = Math.max(250, readTimeoutMillis);
    }

    <T> T select(
            String configuredMode,
            String projectCode,
            String storeCode,
            String targetHost,
            int targetPort,
            Function<NoonProxyRouteFactory.Route, T> sessionOpener,
            Predicate<IllegalStateException> transientFailurePolicy
    ) {
        NoonProxyMode mode = routeFactory.resolveMode(configuredMode);
        int attempts = mode == NoonProxyMode.PROVIDER ? maxAttempts : 1;
        List<String> failures = new ArrayList<>();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            NoonProxyRouteFactory.Route route;
            try {
                route = routeFactory.selectAndPreflight(
                        configuredMode,
                        targetHost,
                        targetPort,
                        connectTimeoutMillis,
                        readTimeoutMillis
                );
            } catch (NoonProxyConnectPreflight.PreflightFailure failure) {
                failures.add(failure.fingerprint() + ":" + failure.evidenceCode());
                LOGGER.warn(
                        "Noon egress preflight rejected project={} store={} fingerprint={} stage={} attempt={}/{}",
                        projectCode, storeCode, failure.fingerprint(), failure.evidenceCode(), attempt, attempts
                );
                continue;
            } catch (IllegalStateException failure) {
                if (mode != NoonProxyMode.PROVIDER) {
                    throw failure;
                }
                failures.add("provider-fetch:" + failure.getClass().getSimpleName());
                LOGGER.warn(
                        "Noon egress provider selection failed project={} store={} stage=PROVIDER_FETCH attempt={}/{}",
                        projectCode, storeCode, attempt, attempts
                );
                continue;
            }
            try {
                T session = sessionOpener.apply(route);
                LOGGER.info(
                        "Noon egress pinned project={} store={} fingerprint={} attempt={}/{}",
                        projectCode, storeCode, route.fingerprint(), attempt, attempts
                );
                return session;
            } catch (NoonSessionGateway.NoonCookieAuthRequiredException failure) {
                throw failure;
            } catch (IllegalStateException failure) {
                if (mode != NoonProxyMode.PROVIDER || !transientFailurePolicy.test(failure)) {
                    throw failure;
                }
                failures.add(route.fingerprint() + ":SESSION_PROBE");
                LOGGER.warn(
                        "Noon egress session probe rejected project={} store={} fingerprint={} stage=SESSION_PROBE attempt={}/{}",
                        projectCode, storeCode, route.fingerprint(), attempt, attempts
                );
            }
        }
        throw new IllegalStateException(
                "Noon 出口预检失败：attempts=" + attempts + " failures=" + failures
        );
    }
}
