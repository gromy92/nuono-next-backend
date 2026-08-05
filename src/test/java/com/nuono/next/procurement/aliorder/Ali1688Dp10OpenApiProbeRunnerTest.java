package com.nuono.next.procurement.aliorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10OpenApiProbeRunnerTest {
    private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");

    @Test
    void usesOneCurrentOneHistoryAndOneDetailWithTheSameFixedWindow() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(order("ORDER-1")), 1L));
        provider.pages.add(page(List.of(), 0L));
        provider.detail = Ali1688HistoricalOrderProvider.DetailResult.success(order("ORDER-1"));

        new Ali1688Dp10OpenApiProbeRunner(
                provider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        ).run(new Ali1688HistoricalOrderAuthorizationRow(), null, 20);

        assertEquals(2, provider.requests.size());
        assertEquals(Ali1688HistoricalOrderProvider.Partition.CURRENT,
                provider.requests.get(0).getPartition());
        assertEquals(Ali1688HistoricalOrderProvider.Partition.HISTORY,
                provider.requests.get(1).getPartition());
        assertEquals(provider.requests.get(0).getModifiedFrom(),
                provider.requests.get(1).getModifiedFrom());
        assertEquals(provider.requests.get(0).getModifiedTo(),
                provider.requests.get(1).getModifiedTo());
        assertEquals(NOW, provider.requests.get(0).getModifiedTo());
        assertEquals(1, provider.detailCalls);
        assertEquals(0, provider.refreshCalls);
    }

    @Test
    void firstListFailureStopsBeforeHistoryDetailOrRefresh() {
        ScriptedProvider provider = new ScriptedProvider();
        Ali1688HistoricalOrderProvider.Page failed = page(List.of(), 0L);
        failed.setFailureCode("BLOCKED_BY_RISK_CONTROL");
        provider.pages.add(failed);

        Ali1688Dp10OpenApiProbeRunner.ProbeFailure failure = assertThrows(
                Ali1688Dp10OpenApiProbeRunner.ProbeFailure.class,
                () -> runner(provider).run(
                        new Ali1688HistoricalOrderAuthorizationRow(),
                        "LOCAL-ORDER",
                        20
                )
        );

        assertEquals("PROBE_CURRENT_LIST_CONTRACT_UNPROVEN", failure.code());
        assertEquals(1, provider.requests.size());
        assertEquals(0, provider.detailCalls);
        assertEquals(0, provider.refreshCalls);
    }

    @Test
    void emptyListsNeedAnExistingIdentityBeforeDetailCanBeProven() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.pages.add(page(List.of(), 0L));
        provider.pages.add(page(List.of(), 0L));

        Ali1688Dp10OpenApiProbeRunner.ProbeFailure failure = assertThrows(
                Ali1688Dp10OpenApiProbeRunner.ProbeFailure.class,
                () -> runner(provider).run(
                        new Ali1688HistoricalOrderAuthorizationRow(),
                        null,
                        20
                )
        );

        assertEquals("PROBE_DETAIL_IDENTITY_UNAVAILABLE", failure.code());
        assertEquals(2, provider.requests.size());
        assertEquals(0, provider.detailCalls);
    }

    @Test
    void refreshRequiredFailsBeforeAnyExternalCall() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.refreshRequired = true;

        assertThrows(
                Ali1688Dp10OpenApiProbeRunner.ProbeFailure.class,
                () -> runner(provider).run(
                        new Ali1688HistoricalOrderAuthorizationRow(),
                        "LOCAL-ORDER",
                        20
                )
        );
        assertEquals(0, provider.requests.size());
        assertEquals(0, provider.detailCalls);
        assertEquals(0, provider.refreshCalls);
    }

    private Ali1688Dp10OpenApiProbeRunner runner(ScriptedProvider provider) {
        return new Ali1688Dp10OpenApiProbeRunner(
                provider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static Ali1688HistoricalOrderProvider.OrderSnapshot order(String identity) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo(identity);
        return order;
    }

    private static Ali1688HistoricalOrderProvider.Page page(
            List<Ali1688HistoricalOrderProvider.OrderSnapshot> orders,
            long total
    ) {
        Ali1688HistoricalOrderProvider.Page page = new Ali1688HistoricalOrderProvider.Page(orders);
        page.setContainerProven(true);
        page.setPaginationProven(true);
        page.setPageNo(1);
        page.setPageSize(20);
        page.setTotalRecord(total);
        page.setExpectedPages(1);
        page.setHasMore(false);
        page.setNextCursor(null);
        return page;
    }

    private static final class ScriptedProvider implements Ali1688HistoricalOrderProvider {
        private final List<Page> pages = new ArrayList<>();
        private final List<Ali1688HistoricalOrderRequest> requests = new ArrayList<>();
        private DetailResult detail;
        private boolean refreshRequired;
        private int detailCalls;
        private int refreshCalls;

        @Override
        public Page fetchOrderList(Ali1688HistoricalOrderRequest request) {
            requests.add(request);
            return pages.remove(0);
        }

        @Override
        public DetailResult fetchOrderDetail(
                Ali1688HistoricalOrderAuthorizationRow authorization,
                String providerOrderNo
        ) {
            detailCalls++;
            return detail;
        }

        @Override
        public boolean requiresAuthorizationRefresh(
                Ali1688HistoricalOrderAuthorizationRow authorization
        ) {
            return refreshRequired;
        }

        @Override
        public Ali1688HistoricalOrderAuthorizationRefreshResult refreshAuthorization(
                Ali1688HistoricalOrderAuthorizationRow authorization
        ) {
            refreshCalls++;
            return Ali1688HistoricalOrderAuthorizationRefreshResult.success();
        }
    }
}
