package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAli1688HistoricalOrderProviderTest {

    @Test
    void fetchPageCallsSignedOrderListApiAndMapsOrderItems() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties());
        HttpAli1688HistoricalOrderProvider provider = new HttpAli1688HistoricalOrderProvider(
                properties(),
                new Ali1688OpenApiSigner(),
                cipher,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo(containsString(
                        "http://openapi.test/openapi/param2/1/com.alibaba.trade/alibaba.trade.getBuyerOrderList/5890829?"
                )))
                .andExpect(requestTo(containsString("access_token=access-token-001")))
                .andExpect(requestTo(containsString("page=1")))
                .andExpect(requestTo(containsString("pageSize=20")))
                .andExpect(requestTo(containsString("_aop_signature=")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{"
                                + "\"totalRecord\":21,"
                                + "\"result\":[{"
                                + "\"baseInfo\":{"
                                + "\"id\":595071981285114902,"
                                + "\"idOfStr\":\"595071981285114902\","
                                + "\"createTime\":\"20200319171255000+0800\","
                                + "\"payTime\":\"20200319171302000+0800\","
                                + "\"buyerLoginId\":\"buyer-login\","
                                + "\"sellerLoginId\":\"溪潼坐便套\","
                                + "\"buyerContact\":{\"companyName\":\"买家公司\"},"
                                + "\"sellerContact\":{\"companyName\":\"任丘市溪潼针织机毡加工厂\"},"
                                + "\"status\":\"交易成功\","
                                + "\"sumProductPayment\":85.00,"
                                + "\"shippingFee\":0.00,"
                                + "\"discount\":-2.00,"
                                + "\"totalAmount\":85.00},"
                                + "\"nativeLogistics\":{\"contactPerson\":\"梁宇\",\"zip\":\"310000\",\"address\":\"浙江杭州\"},"
                                + "\"productItems\":[{"
                                + "\"productID\":\"586206234147\","
                                + "\"skuID\":\"4001301253326\","
                                + "\"name\":\"糖果色O型便携式马桶垫\","
                                + "\"skuInfos\":[{\"name\":\"颜色\",\"value\":\"淡青色\"}],"
                                + "\"productCargoNumber\":\"003\","
                                + "\"quantity\":100,"
                                + "\"unit\":\"个\","
                                + "\"price\":\"0.85\","
                                + "\"itemAmount\":\"85.00\","
                                + "\"productImgUrl\":[\"https://cbu01.alicdn.com/img.jpg\"]"
                                + "}]}]}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchPage(authorization(cipher), null);

        server.verify();
        assertThat(page.getOrders()).hasSize(1);
        Ali1688HistoricalOrderProvider.OrderSnapshot order = page.getOrders().get(0);
        assertThat(order.getProviderOrderNo()).isEqualTo("595071981285114902");
        assertThat(order.getOrderTime()).isEqualTo("2020-03-19 17:12:55");
        assertThat(order.getPaidAt()).isEqualTo("2020-03-19 17:13:02");
        assertThat(order.getBuyerCompanyName()).isEqualTo("买家公司");
        assertThat(order.getBuyerMemberName()).isEqualTo("buyer-login");
        assertThat(order.getSupplierName()).isEqualTo("任丘市溪潼针织机毡加工厂");
        assertThat(order.getSellerMemberName()).isEqualTo("溪潼坐便套");
        assertThat(order.getAdjustmentText()).isNull();
        assertThat(order.getPaidAmountText()).isEqualTo("85.0");
        assertThat(order.getReceiverName()).isEqualTo("梁宇");
        assertThat(order.getItems()).hasSize(1);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = order.getItems().get(0);
        assertThat(item.getOfferId()).isEqualTo("586206234147");
        assertThat(item.getSkuId()).isEqualTo("4001301253326");
        assertThat(item.getTitle()).isEqualTo("糖果色O型便携式马桶垫");
        assertThat(item.getSkuText()).isEqualTo("颜色：淡青色");
        assertThat(item.getSingleProductCode()).isEqualTo("003");
        assertThat(item.getQuantity()).isEqualTo(100);
        assertThat(item.getUnit()).isEqualTo("个");
        assertThat(item.getUnitPriceText()).isEqualTo("0.85");
        assertThat(item.getAmountText()).isEqualTo("85.00");
        assertThat(item.getImageUrl()).isEqualTo("https://cbu01.alicdn.com/img.jpg");
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isEqualTo("2");
    }

    @Test
    void fetchPageCallsBuyerDetailApiWhenListOrderHasNoItems() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties());
        HttpAli1688HistoricalOrderProvider provider = new HttpAli1688HistoricalOrderProvider(
                properties(),
                new Ali1688OpenApiSigner(),
                cipher,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{"
                                + "\"result\":{\"orders\":[{\"id\":\"1824972770625114902\",\"createTime\":\"2021-05-26 14:12:27\"}]}"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("alibaba.trade.get.buyerView")))
                .andExpect(requestTo(containsString("orderId=1824972770625114902")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{"
                                + "\"result\":{\"order\":{"
                                + "\"id\":\"1824972770625114902\","
                                + "\"sellerCompanyName\":\"五华县横陂镇凌志原商行\","
                                + "\"productItems\":[{"
                                + "\"offerId\":\"643741348839\","
                                + "\"skuId\":\"4813321163572\","
                                + "\"name\":\"可撕式粘毛器替换纸芯\","
                                + "\"quantity\":3,"
                                + "\"price\":\"40.63\""
                                + "}]}}"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688HistoricalOrderProvider.Page page = provider.fetchPage(authorization(cipher), null);

        server.verify();
        assertThat(page.getOrders()).hasSize(1);
        assertThat(page.getOrders().get(0).getSupplierName()).isEqualTo("五华县横陂镇凌志原商行");
        assertThat(page.getOrders().get(0).getItems()).hasSize(1);
        assertThat(page.getOrders().get(0).getItems().get(0).getOfferId()).isEqualTo("643741348839");
    }

    @Test
    void fetchPageRefreshesExpiredAccessTokenBeforeCallingOrderListApi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688HistoricalOrderOpenApiProperties properties = properties();
        Ali1688TokenCipher cipher = new Ali1688TokenCipher(properties);
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        HttpAli1688HistoricalOrderProvider provider = new HttpAli1688HistoricalOrderProvider(
                properties,
                new Ali1688OpenApiSigner(),
                cipher,
                new ObjectMapper(),
                restTemplate,
                mapper
        );

        server.expect(requestTo(containsString("/openapi/http/1/system.oauth2/getToken/5890829")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=refresh-token-001")))
                .andRespond(withSuccess(
                        "{"
                                + "\"access_token\":\"access-token-refreshed\","
                                + "\"refresh_token\":\"refresh-token-refreshed\","
                                + "\"expires_in\":3600,"
                                + "\"resource_owner\":\"buyer-login\""
                                + "}",
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(containsString("alibaba.trade.getBuyerOrderList")))
                .andExpect(requestTo(containsString("access_token=access-token-refreshed")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));

        Ali1688HistoricalOrderProvider.Page page =
                provider.fetchPage(expiredAuthorization(cipher), null);

        server.verify();
        assertThat(page.getFailureCode()).isNull();
        ArgumentCaptor<Ali1688HistoricalOrderAuthorizationRow> captor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderAuthorizationRow.class);
        verify(mapper).updateAuthorizationTokens(captor.capture());
        Ali1688HistoricalOrderAuthorizationRow refreshed = captor.getValue();
        assertThat(cipher.decrypt(refreshed.getAccessTokenCipher())).isEqualTo("access-token-refreshed");
        assertThat(cipher.decrypt(refreshed.getRefreshTokenCipher())).isEqualTo("refresh-token-refreshed");
        assertThat(refreshed.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(50));
    }

    private Ali1688HistoricalOrderOpenApiProperties properties() {
        Ali1688HistoricalOrderOpenApiProperties properties = new Ali1688HistoricalOrderOpenApiProperties();
        properties.setEnabled(true);
        properties.setAppKey("5890829");
        properties.setAppSecret("app-secret");
        properties.setTokenCipherSecret("token-cipher-secret-for-test");
        properties.setApiGatewayBaseUrl("http://openapi.test");
        properties.setTokenUrlTemplate("http://openapi.test/openapi/http/1/system.oauth2/getToken/{appKey}");
        return properties;
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization(Ali1688TokenCipher cipher) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91009L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setStatus("authorized");
        row.setAccessTokenCipher(cipher.encrypt("access-token-001"));
        return row;
    }

    private Ali1688HistoricalOrderAuthorizationRow expiredAuthorization(Ali1688TokenCipher cipher) {
        Ali1688HistoricalOrderAuthorizationRow row = authorization(cipher);
        row.setRefreshTokenCipher(cipher.encrypt("refresh-token-001"));
        row.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        return row;
    }
}
