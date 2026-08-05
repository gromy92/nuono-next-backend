package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class HttpAli1688HistoricalOrderProviderTest
        extends HttpAli1688HistoricalOrderProviderTestSupport {

    @Test
    void fetchOrderListCallsOneSignedApiAndMapsOrderItems() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andExpect(requestTo(containsString("access_token=access-token-001")))
                .andExpect(requestTo(containsString("page=1")))
                .andExpect(requestTo(containsString("pageSize=20")))
                .andExpect(requestTo(containsString("_aop_signature=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(orderListPayload(), MediaType.APPLICATION_JSON));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.full(
                        authorization(properties),
                        null
                )
        );

        server.verify();
        assertThat(page.isContainerProven()).isTrue();
        assertThat(page.isPaginationProven()).isTrue();
        assertThat(page.getNextCursor()).isEqualTo("2");
        assertThat(page.getOrders()).hasSize(1);
        Ali1688HistoricalOrderProvider.OrderSnapshot order = page.getOrders().get(0);
        assertThat(order.getProviderOrderNo()).isEqualTo("595071981285114902");
        assertThat(order.getOrderTime()).isEqualTo("2020-03-19 17:12:55");
        assertThat(order.getBuyerCompanyName()).isEqualTo("买家公司");
        assertThat(order.getSupplierName()).isEqualTo("任丘市溪潼针织机毡加工厂");
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getOfferId()).isEqualTo("586206234147");
        assertThat(order.getItems().get(0).getSkuText()).isEqualTo("颜色：淡青色");
        assertThat(order.getItems().get(0).getImageUrl())
                .isEqualTo("https://cbu01.alicdn.com/img.jpg");
    }

    @Test
    void dp10ListDoesNotCallDetailForAnOrderWithoutItems() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = incrementalProperties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andRespond(withSuccess(
                        "{\"totalRecord\":1,\"result\":[{\"id\":\"ORDER-1\","
                                + "\"modifyTime\":\"20260801010000000+0800\"}]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.window(
                        authorization(properties),
                        Ali1688HistoricalOrderProvider.SyncMode.INCREMENTAL,
                        Ali1688HistoricalOrderProvider.Partition.CURRENT,
                        1,
                        20,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-02T00:00:00Z")
                )
        );

        server.verify();
        assertThat(page.hasFailure()).isFalse();
        assertThat(page.getOrders().get(0).getItems()).isEmpty();
    }

    @Test
    void legacyFetchPageStillLoadsDetailWhenRequested() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        HttpAli1688HistoricalOrderProvider provider = provider(properties, restTemplate);
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andRespond(withSuccess(
                        "{\"hasMore\":false,\"result\":{"
                                + "\"orders\":[{\"id\":\"ORDER-2\"}]}}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("alibaba.trade.get.buyerView")))
                .andExpect(requestTo(containsString("orderId=ORDER-2")))
                .andRespond(withSuccess(
                        "{\"result\":{\"order\":{\"id\":\"ORDER-2\","
                                + "\"productItems\":[{\"offerId\":\"OFFER-2\"}]}}}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchPage(
                authorization(properties),
                null
        );

        server.verify();
        assertThat(page.getOrders().get(0).getItems().get(0).getOfferId())
                .isEqualTo("OFFER-2");
    }

    @Test
    void expiredTokenRefreshIsExplicitAndPersistedBeforeASeparateListCall() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        Ali1688OpenApiAuthorizationMapper mapper = mock(
                Ali1688OpenApiAuthorizationMapper.class
        );
        when(mapper.updateAuthorizationTokens(
                org.mockito.ArgumentMatchers.any(Ali1688HistoricalOrderAuthorizationRow.class)
        )).thenReturn(1);
        HttpAli1688HistoricalOrderProvider provider = provider(
                properties,
                restTemplate,
                mapper
        );
        server.expect(requestTo(containsString("system.oauth2/getToken")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("refresh-token-001")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"access-token-refreshed\","
                                + "\"refresh_token\":\"refresh-token-refreshed\","
                                + "\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("access_token=access-token-refreshed")))
                .andRespond(withSuccess(
                        "{\"hasMore\":false,\"result\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderAuthorizationRow authorization =
                expiredAuthorization(properties);
        assertThat(provider.requiresAuthorizationRefresh(authorization)).isTrue();
        Ali1688HistoricalOrderAuthorizationRefreshResult refresh =
                provider.refreshAuthorization(authorization);

        Ali1688HistoricalOrderProvider.Page page = provider.fetchOrderList(
                Ali1688HistoricalOrderRequest.full(
                        authorization,
                        null
                )
        );

        server.verify();
        assertThat(refresh.isSuccess()).isTrue();
        assertThat(provider.requiresAuthorizationRefresh(authorization)).isFalse();
        assertThat(page.hasFailure()).isFalse();
        ArgumentCaptor<Ali1688HistoricalOrderAuthorizationRow> captor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderAuthorizationRow.class);
        verify(mapper).updateAuthorizationTokens(captor.capture());
        Ali1688HistoricalOrderAuthorizationRow refreshed = captor.getValue();
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties);
        assertThat(cipher.decrypt(refreshed.getAccessTokenCipher()))
                .isEqualTo("access-token-refreshed");
        assertThat(refreshed.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(50));
    }

    @Test
    void refreshTransportOutcomeUnknownRequiresReauthorizationAndPreservesCredentials() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        Ali1688OpenApiAuthorizationMapper mapper = mock(
                Ali1688OpenApiAuthorizationMapper.class
        );
        HttpAli1688HistoricalOrderProvider provider = provider(
                properties,
                restTemplate,
                mapper
        );
        server.expect(requestTo(containsString("system.oauth2/getToken")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("response lost after token POST");
                });
        Ali1688HistoricalOrderAuthorizationRow authorization =
                expiredAuthorization(properties);
        String originalStatus = authorization.getStatus();
        String originalAccessToken = authorization.getAccessTokenCipher();
        String originalRefreshToken = authorization.getRefreshTokenCipher();
        LocalDateTime originalExpiry = authorization.getExpiresAt();

        Ali1688HistoricalOrderAuthorizationRefreshResult result =
                provider.refreshAuthorization(authorization);

        server.verify();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("auth_refresh_outcome_unknown");
        assertThat(authorization.getStatus()).isEqualTo(originalStatus);
        assertThat(authorization.getAccessTokenCipher()).isEqualTo(originalAccessToken);
        assertThat(authorization.getRefreshTokenCipher()).isEqualTo(originalRefreshToken);
        assertThat(authorization.getExpiresAt()).isEqualTo(originalExpiry);
        verify(mapper, never()).updateAuthorizationTokens(
                org.mockito.ArgumentMatchers.any(Ali1688HistoricalOrderAuthorizationRow.class)
        );
    }

    @Test
    void redirectWithTokenShapedJsonCannotRefreshOrWriteCredentials() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        Ali1688OpenApiAuthorizationMapper mapper = mock(
                Ali1688OpenApiAuthorizationMapper.class
        );
        HttpAli1688HistoricalOrderProvider provider = provider(
                properties,
                restTemplate,
                mapper
        );
        server.expect(requestTo(containsString("system.oauth2/getToken")))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .body("{\"access_token\":\"redirect-token\","
                                + "\"refresh_token\":\"redirect-refresh\","
                                + "\"expires_in\":3600}"));
        Ali1688HistoricalOrderAuthorizationRow authorization =
                expiredAuthorization(properties);

        Ali1688HistoricalOrderAuthorizationRefreshResult result =
                provider.refreshAuthorization(authorization);

        server.verify();
        assertThat(result.isSuccess()).isFalse();
        verify(mapper, never()).updateAuthorizationTokens(
                org.mockito.ArgumentMatchers.any(Ali1688HistoricalOrderAuthorizationRow.class)
        );
    }

    @Test
    void tokenRefreshRejectsExternalAccountIdentityDrift() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        Ali1688OpenApiAuthorizationMapper mapper = mock(
                Ali1688OpenApiAuthorizationMapper.class
        );
        HttpAli1688HistoricalOrderProvider provider = provider(
                properties,
                restTemplate,
                mapper
        );
        server.expect(requestTo(containsString("system.oauth2/getToken")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"other-account-token\","
                                + "\"memberId\":\"member-other\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON
                ));
        Ali1688HistoricalOrderAuthorizationRow authorization =
                expiredAuthorization(properties);

        Ali1688HistoricalOrderAuthorizationRefreshResult result =
                provider.refreshAuthorization(authorization);

        server.verify();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo("auth_required");
        assertThat(authorization.getProviderAccountId()).isEqualTo("member-307");
        verify(mapper, never()).updateAuthorizationTokens(authorization);
    }

    private String orderListPayload() {
        return "{\"totalRecord\":21,\"result\":[{\"baseInfo\":{"
                + "\"idOfStr\":\"595071981285114902\","
                + "\"createTime\":\"20200319171255000+0800\","
                + "\"buyerContact\":{\"companyName\":\"买家公司\"},"
                + "\"sellerContact\":{\"companyName\":\"任丘市溪潼针织机毡加工厂\"}},"
                + "\"productItems\":[{\"productID\":\"586206234147\","
                + "\"skuInfos\":[{\"name\":\"颜色\",\"value\":\"淡青色\"}],"
                + "\"productImgUrl\":[\"https://cbu01.alicdn.com/img.jpg\"]}]}]}";
    }
}
