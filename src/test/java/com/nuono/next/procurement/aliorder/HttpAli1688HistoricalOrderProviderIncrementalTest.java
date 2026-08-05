package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAli1688HistoricalOrderProviderIncrementalTest
        extends HttpAli1688HistoricalOrderProviderTestSupport {

    @Test
    void dp10ListUsesFixedOfficialWindowPageAndPartitionContract() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("page=2")))
                .andExpect(requestTo(containsString("pageSize=20")))
                .andExpect(requestTo(containsString(
                        "modifyStartTime=20260801123000000%2B0800"
                )))
                .andExpect(requestTo(containsString(
                        "modifyEndTime=20260802120000000%2B0800"
                )))
                .andExpect(requestTo(containsString("isHis=false")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"totalRecord\":41,\"result\":["
                                + incrementalOrderJson("ORDER-002", "20260801123000000+0800")
                                + ","
                                + incrementalOrderJson("ORDER-001", "20260801122500000+0800")
                                + "]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        2,
                        20,
                        Instant.parse("2026-08-01T04:30:00Z"),
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );

        server.verify();
        assertThat(page.hasFailure()).isFalse();
        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(20);
        assertThat(page.getTotalRecord()).isEqualTo(41L);
        assertThat(page.isEndOfStream()).isFalse();
    }

    @Test
    void fullListOmitsStartButKeepsFixedEndAndHistoryPartition() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andExpect(requestTo(not(containsString("modifyStartTime"))))
                .andExpect(requestTo(containsString(
                        "modifyEndTime=20260802120000000%2B0800"
                )))
                .andExpect(requestTo(containsString("isHis=true")))
                .andRespond(withSuccess(
                        "{\"totalRecord\":2,\"result\":["
                                + incrementalOrderJson("ORDER-002", "20260801123000000+0800")
                                + ","
                                + incrementalOrderJson("ORDER-001", "20260801122500000+0800")
                                + "]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.FULL,
                        Ali1688HistoricalOrderProvider.Partition.HISTORY,
                        1,
                        20,
                        null,
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );

        server.verify();
        assertThat(page.hasFailure()).isFalse();
        assertThat(page.getTotalRecord()).isEqualTo(2L);
        assertThat(page.isEndOfStream()).isTrue();
    }

    @Test
    void incrementalListFailsClosedWhenContractIsMissingOrUnsafe() {
        Ali1688HistoricalOrderOpenApiProperties missing = properties();
        missing.setAppSecret("");
        assertNotConfigured(missing);
        Ali1688HistoricalOrderOpenApiProperties unsafe = incrementalProperties();
        unsafe.setModifiedToParameterName("wrongModifyEnd");
        assertNotConfigured(unsafe);
    }

    @Test
    void malformedSingleBusinessOrderDoesNotInvalidateItsContainer() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andRespond(withSuccess(
                        "{\"totalRecord\":2,\"result\":[{\"baseInfo\":{}},"
                                + incrementalOrderJson("ORDER-001", "20260801123000000+0800")
                                + "]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        1,
                        20,
                        Instant.parse("2026-08-01T04:20:00Z"),
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );

        server.verify();
        assertThat(page.hasFailure()).isFalse();
        assertThat(page.getOrders()).hasSize(2);
        assertThat(page.getOrders().get(0).getProviderOrderNo()).isNull();
        assertThat(page.getOrders().get(1).getProviderOrderNo()).isEqualTo("ORDER-001");
    }

    @Test
    void pageBeyondShrunkTotalStillReturnsProvenEnvelopeForRuntimeDriftClassification() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("page=2")))
                .andExpect(requestTo(containsString("pageSize=1")))
                .andRespond(withSuccess(
                        "{\"totalRecord\":1,\"result\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        2,
                        1,
                        Instant.parse("2026-08-01T04:20:00Z"),
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );

        server.verify();
        assertThat(page.hasFailure()).isFalse();
        assertThat(page.isPaginationProven()).isTrue();
        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getTotalRecord()).isEqualTo(1L);
        assertThat(page.getExpectedPages()).isEqualTo(1);
        assertThat(page.isEndOfStream()).isTrue();
    }

    @Test
    void fixedWindowPreservesExactLongTotalRecord() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("pageSize=2")))
                .andRespond(withSuccess(
                        "{\"totalRecord\":3000000000,\"result\":["
                                + incrementalOrderJson("ORDER-LONG", "20260801123000000+0800")
                                + ","
                                + incrementalOrderJson("ORDER-LONG-2", "20260801122500000+0800")
                                + "]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        1,
                        2,
                        Instant.parse("2026-08-01T04:20:00Z"),
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );

        server.verify();
        assertThat(page.hasFailure()).isFalse();
        assertThat(page.getTotalRecord()).isEqualTo(3_000_000_000L);
        assertThat(page.getExpectedPages()).isEqualTo(1_500_000_000);
        assertThat(page.isHasMore()).isTrue();
    }

    @Test
    void fixedWindowFailsClosedWhenLongTotalNeedsUnsupportedPageNumber() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("pageSize=1")))
                .andRespond(withSuccess(
                        "{\"totalRecord\":9223372036854775807,\"result\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        1,
                        1,
                        Instant.parse("2026-08-01T04:20:00Z"),
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );

        server.verify();
        assertThat(page.getFailureCode()).isEqualTo("unexpected_response");
        assertThat(page.isPaginationProven()).isFalse();
    }

    @Test
    void totalRecordParserRejectsFractionalAndOutOfLongRangeValues() {
        Ali1688OpenApiJson json = new Ali1688OpenApiJson(new ObjectMapper());

        assertThat(json.longInteger(json.read("{\"totalRecord\":3000000000}"), "totalRecord"))
                .isEqualTo(3_000_000_000L);
        assertThat(json.longInteger(json.read("{\"totalRecord\":1.5}"), "totalRecord"))
                .isNull();
        assertThat(json.longInteger(
                json.read("{\"totalRecord\":9223372036854775808}"), "totalRecord"))
                .isNull();
    }

    private void assertNotConfigured(Ali1688HistoricalOrderOpenApiProperties properties) {
        RestTemplate restTemplate = new RestTemplate();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        1,
                        20,
                        Instant.parse("2026-08-01T04:30:00Z"),
                        Instant.parse("2026-08-02T04:00:00Z")
                )
        );
        assertThat(page.getFailureCode()).isEqualTo("provider_not_configured");
    }
}
