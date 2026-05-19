package com.nuono.next.productselection;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class HttpAli1688ImageSearchGatewayTest {

    @Test
    void postsSourceCollectionAndMapsGatewayResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        Ali1688ImageSearchProperties properties = new Ali1688ImageSearchProperties();
        properties.setEndpointUrl("http://gateway.test/ali1688/image-search");
        properties.setAuthToken("token-123");
        properties.setMaxCandidates(10);
        HttpAli1688ImageSearchGateway gateway = new HttpAli1688ImageSearchGateway(
                properties,
                new ObjectMapper(),
                restTemplate
        );

        server.expect(requestTo("http://gateway.test/ali1688/image-search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andExpect(content().string(containsString("\"sourceCollectionId\":86001")))
                .andExpect(content().string(containsString("\"sourceImageUrl\":\"https://images.example.com/source.jpg\"")))
                .andExpect(content().string(containsString("\"maxCandidates\":10")))
                .andRespond(withSuccess(
                        "{"
                                + "\"searchMode\":\"主图图搜\","
                                + "\"officialSearchUrl\":\"https://s.1688.com/image/example\","
                                + "\"searchImageId\":\"img-001\","
                                + "\"searchImageIds\":[\"img-001\"],"
                                + "\"candidates\":[{"
                                + "\"offerId\":\"745612345678\","
                                + "\"candidateUrl\":\"https://detail.1688.com/offer/745612345678.html\","
                                + "\"title\":\"仿真花束 6 支装\","
                                + "\"supplierName\":\"义乌诚信通源头工厂\","
                                + "\"priceText\":\"¥12.80-18.60\","
                                + "\"priceMin\":12.80,"
                                + "\"priceMax\":18.60,"
                                + "\"moqText\":\"2 件起批\","
                                + "\"moqValue\":2,"
                                + "\"locationText\":\"浙江 义乌\","
                                + "\"mainImageUrl\":\"https://images.example.com/ali-main.jpg\","
                                + "\"imageUrls\":[\"https://images.example.com/ali-main.jpg\"],"
                                + "\"badges\":{\"stock\":\"现货\"},"
                                + "\"supplierSnapshot\":{\"verified\":true},"
                                + "\"logisticsSnapshot\":{\"shipFrom\":\"义乌\"}"
                                + "}]"
                                + "}",
                        MediaType.APPLICATION_JSON
                ));

        Ali1688ImageSearchResult result = gateway.search(sourceCollection());

        server.verify();
        assertEquals("主图图搜", result.searchMode);
        assertEquals("https://s.1688.com/image/example", result.officialSearchUrl);
        assertEquals("img-001", result.searchImageId);
        assertEquals(1, result.candidates.size());
        Ali1688ImageSearchResult.Candidate candidate = result.candidates.get(0);
        assertEquals("745612345678", candidate.offerId);
        assertEquals("仿真花束 6 支装", candidate.title);
        assertEquals(new BigDecimal("12.80"), candidate.priceMin);
        assertEquals(2, candidate.moqValue);
        assertEquals("现货", candidate.badges.get("stock"));
        assertNotNull(result.rawSnapshotJson);
        assertTrue(result.rawSnapshotJson.contains("745612345678"));
    }

    private ProductSelectionSourceCollectionRow sourceCollection() {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(86001L);
        row.setCollectionNo("PSC-20260519-001");
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(301L);
        row.setStoreCode("STR108065-NAE");
        row.setSourceType("marketplace-url");
        row.setSourcePlatform("Amazon");
        row.setSourceUrl("https://www.amazon.com/dp/example");
        row.setPageUrl("https://www.amazon.com/dp/example");
        row.setSourceTitle("Artificial Flowers 6 Stems");
        row.setSourceTitleCn("仿真花束");
        row.setSourceImageUrl("https://images.example.com/source.jpg");
        row.setImageUrlsJson("[\"https://images.example.com/source.jpg\"]");
        row.setPriceSummary("12.99");
        row.setMoqHint("1 pcs min");
        row.setShippingFrom("Amazon");
        row.setSpecHintsJson("[\"Brand: DUYONE\", \"Material: Silk\"]");
        row.setSelectedText("仿真花束");
        return row;
    }
}
