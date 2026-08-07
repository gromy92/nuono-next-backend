package com.nuono.next.procurement.aliorder;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Executes the bounded CURRENT/HISTORY/DETAIL execution-contract observation. */
final class Ali1688Dp10OpenApiProbeRunner {
    private static final long WINDOW_HOURS = 24L;

    private final Ali1688HistoricalOrderProvider provider;
    private final Clock clock;

    Ali1688Dp10OpenApiProbeRunner(
            Ali1688HistoricalOrderProvider provider,
            Clock clock
    ) {
        this.provider = provider;
        this.clock = clock;
    }

    Proof run(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String fallbackProviderOrderNo,
            int pageSize
    ) {
        if (authorization == null || pageSize < 1) throw failure("PROBE_INPUT_INVALID");
        boolean authorizationRefreshed = false;
        if (provider.requiresAuthorizationRefresh(authorization)) {
            Ali1688HistoricalOrderAuthorizationRefreshResult refresh =
                    provider.refreshAuthorization(authorization);
            if (refresh == null || !refresh.isSuccess()) {
                throw failure(refreshFailureCode(refresh));
            }
            if (provider.requiresAuthorizationRefresh(authorization)) {
                throw failure("PROBE_AUTH_REFRESH_UNPROVEN");
            }
            authorizationRefreshed = true;
        }
        Instant modifiedTo = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant modifiedFrom = modifiedTo.minus(WINDOW_HOURS, ChronoUnit.HOURS);
        Ali1688HistoricalOrderProvider.Page current = list(
                authorization,
                Ali1688HistoricalOrderProvider.Partition.CURRENT,
                pageSize,
                modifiedFrom,
                modifiedTo
        );
        validateList(current, pageSize, "PROBE_CURRENT_LIST_CONTRACT_UNPROVEN");
        Ali1688HistoricalOrderProvider.Page history = list(
                authorization,
                Ali1688HistoricalOrderProvider.Partition.HISTORY,
                pageSize,
                modifiedFrom,
                modifiedTo
        );
        validateList(history, pageSize, "PROBE_HISTORY_LIST_CONTRACT_UNPROVEN");
        String detailIdentity = firstIdentity(current.getOrders());
        if (detailIdentity == null) detailIdentity = firstIdentity(history.getOrders());
        if (detailIdentity == null) detailIdentity = trim(fallbackProviderOrderNo);
        if (detailIdentity == null) throw failure("PROBE_DETAIL_IDENTITY_UNAVAILABLE");

        Ali1688HistoricalOrderProvider.DetailResult detail =
                provider.fetchOrderDetail(authorization, detailIdentity);
        if (detail == null
                || detail.getStatus() != Ali1688HistoricalOrderProvider.DetailStatus.SUCCESS
                || detail.getOrder() == null
                || !detailIdentity.equals(trim(detail.getOrder().getProviderOrderNo()))) {
            throw failure("PROBE_DETAIL_CONTRACT_UNPROVEN");
        }
        return new Proof(modifiedFrom, modifiedTo, authorizationRefreshed);
    }

    private String refreshFailureCode(
            Ali1688HistoricalOrderAuthorizationRefreshResult refresh
    ) {
        String code = refresh == null ? null : refresh.getFailureCode();
        if (Ali1688HistoricalOrderFailureCode.BLOCKED_BY_RISK_CONTROL.getCode()
                .equals(code)) return "PROBE_AUTH_REFRESH_RISK_CONTROL";
        if (Ali1688HistoricalOrderFailureCode.RATE_LIMITED.getCode()
                .equals(code)) return "PROBE_AUTH_REFRESH_RATE_LIMITED";
        if (Ali1688HistoricalOrderFailureCode.PROVIDER_UNAVAILABLE.getCode()
                .equals(code)
                || Ali1688HistoricalOrderFailureCode.AUTH_REFRESH_OUTCOME_UNKNOWN.getCode()
                .equals(code)) return "PROBE_AUTH_REFRESH_RETRYABLE";
        return "PROBE_AUTH_REFRESH_REQUIRED";
    }

    private Ali1688HistoricalOrderProvider.Page list(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageSize,
            Instant modifiedFrom,
            Instant modifiedTo
    ) {
        return provider.fetchOrderList(Ali1688HistoricalOrderRequest.window(
                authorization,
                Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                partition,
                1,
                pageSize,
                modifiedFrom,
                modifiedTo
        ));
    }

    private void validateList(
            Ali1688HistoricalOrderProvider.Page page,
            int pageSize,
            String failureCode
    ) {
        if (page == null || page.hasFailure()
                || !page.isContainerProven() || !page.isPaginationProven()
                || page.getPageNo() != 1 || page.getPageSize() != pageSize
                || page.getTotalRecord() < 0L) {
            throw failure(failureCode);
        }
        try {
            int expectedPages = Ali1688PaginationMath.expectedPages(
                    page.getTotalRecord(),
                    pageSize
            );
            int expectedRows = Ali1688PaginationMath.expectedRowsOnPage(
                    page.getTotalRecord(),
                    1,
                    pageSize,
                    expectedPages
            );
            boolean expectedHasMore = expectedPages > 1;
            if (page.getExpectedPages() != expectedPages
                    || page.getOrders().size() != expectedRows
                    || page.isHasMore() != expectedHasMore
                    || page.isEndOfStream() == expectedHasMore) {
                throw failure(failureCode);
            }
        } catch (ArithmeticException | IllegalArgumentException invalidPagination) {
            throw failure(failureCode);
        }
    }

    private String firstIdentity(List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders) {
        for (Ali1688HistoricalOrderProvider.OrderSnapshot order : orders) {
            String identity = order == null ? null : trim(order.getProviderOrderNo());
            if (identity != null) return identity;
        }
        return null;
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private ProbeFailure failure(String code) {
        return new ProbeFailure(code);
    }

    static final class Proof {
        private final Instant modifiedFrom;
        private final Instant modifiedTo;
        private final boolean authorizationRefreshed;

        private Proof(
                Instant modifiedFrom,
                Instant modifiedTo,
                boolean authorizationRefreshed
        ) {
            this.modifiedFrom = modifiedFrom;
            this.modifiedTo = modifiedTo;
            this.authorizationRefreshed = authorizationRefreshed;
        }

        Instant modifiedFrom() { return modifiedFrom; }
        Instant modifiedTo() { return modifiedTo; }
        boolean authorizationRefreshed() { return authorizationRefreshed; }
    }

    static final class ProbeFailure extends RuntimeException {
        private final String code;

        private ProbeFailure(String code) {
            super(code);
            this.code = code;
        }

        String code() { return code; }
    }
}
