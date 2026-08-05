package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class HttpAli1688HistoricalOrderProviderFailureTest
        extends HttpAli1688HistoricalOrderProviderTestSupport {

    @Test
    void httpFailuresRetainTypedClassificationAndRetryAfter() {
        Ali1688HistoricalOrderProvider.Page unauthorized = fetchHttpFailure(
                HttpStatus.UNAUTHORIZED,
                null
        );
        Ali1688HistoricalOrderProvider.Page forbidden = fetchHttpFailure(
                HttpStatus.FORBIDDEN,
                null
        );
        Ali1688HistoricalOrderProvider.Page limited = fetchHttpFailure(
                HttpStatus.TOO_MANY_REQUESTS,
                "120"
        );
        Ali1688HistoricalOrderProvider.Page unavailable = fetchHttpFailure(
                HttpStatus.SERVICE_UNAVAILABLE,
                null
        );

        assertThat(unauthorized.getFailureCode()).isEqualTo("auth_required");
        assertThat(forbidden.getFailureCode()).isEqualTo("blocked_by_risk_control");
        assertThat(limited.getFailureCode()).isEqualTo("rate_limited");
        assertThat(limited.getRetryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(unavailable.getFailureCode()).isEqualTo("provider_unavailable");
    }

    @Test
    void transportExceptionCannotLeakAccessTokenIntoDurableFailureText() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andRespond(request -> {
                    throw new ResourceAccessException(
                            "GET https://upstream.test?access_token=top-secret-token"
                    );
                });

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.full(
                        authorization(properties),
                        null
                )
        );

        assertThat(page.getFailureCode()).isEqualTo("provider_unavailable");
        assertThat(page.getFailureMessage()).doesNotContain("top-secret-token");
        assertThat(page.getFailureMessage()).doesNotContain("access_token");
    }

    @Test
    void onlyExactStructuredProviderCodeMeansOrderNotFound() {
        Ali1688HistoricalOrderProvider.DetailResult plain404 = detailFailure(
                withStatus(HttpStatus.NOT_FOUND)
        );
        Ali1688HistoricalOrderProvider.DetailResult structured = detailFailure(
                withSuccess(
                        "{\"error_code\":\"ORDER_NOT_FOUND\","
                                + "\"error_message\":\"gone\"}",
                        MediaType.APPLICATION_JSON
                )
        );

        assertThat(plain404.getStatus())
                .isEqualTo(Ali1688HistoricalOrderProvider.DetailStatus.FAILURE);
        assertThat(structured.getStatus())
                .isEqualTo(Ali1688HistoricalOrderProvider.DetailStatus.NOT_FOUND);
    }

    @Test
    void redirectWithSuccessShapedJsonCannotProveAListContract() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .body("{\"hasMore\":false,\"result\":[]}")
                        .contentType(MediaType.APPLICATION_JSON));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.full(authorization(properties), null)
        );

        server.verify();
        assertThat(page.hasFailure()).isTrue();
        assertThat(page.getFailureCode()).isEqualTo("unexpected_response");
        assertThat(page.isContainerProven()).isFalse();
    }

    private Ali1688HistoricalOrderProvider.DetailResult detailFailure(
            org.springframework.test.web.client.ResponseCreator response
    ) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.get.buyerView")))
                .andRespond(response);
        Ali1688HistoricalOrderProvider.DetailResult result = provider.fetchOrderDetail(
                authorization(properties),
                "ORDER-404"
        );
        server.verify();
        return result;
    }

    private Ali1688HistoricalOrderProvider.Page fetchHttpFailure(
            HttpStatus status,
            String retryAfter
    ) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        DefaultResponseCreator response = withStatus(status);
        if (retryAfter != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
            response.headers(headers);
        }
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andRespond(response);
        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.full(
                        authorization(properties),
                        null
                )
        );
        server.verify();
        return page;
    }
}
