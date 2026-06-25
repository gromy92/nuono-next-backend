package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoonRealProviderBehaviorTest {

    private static final String OFFER_LIST_URL = "https://noon.test/offer/list/noon";
    private static final String EXPORT_CREATE_URL = "https://noon.test/export/create";
    private static final String EXPORT_STATUS_URL = "https://noon.test/export/status";
    private static final String SALES_PAGE_LIST_URL =
            "https://reports.noon.test/_vs/mp/mp-inventory-health-api-sales-dashboard/sales-dashboard/list";
    private static final String SALES_DASHBOARD_EXPORT_GENERATE_URL =
            "https://reports.noon.test/_vs/mp/mp-inventory-health-api-sales-dashboard/export/generate";
    private static final String SALES_DASHBOARD_EXPORT_LATEST_URL =
            "https://reports.noon.test/_vs/mp/mp-inventory-health-api-sales-dashboard/export/latest";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Test
    void productProviderShouldFetchOfferListThroughGatewaySession() {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode()
                        .put("total", 101)
                        .set("hits", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("zsku_parent", "Z-PRODUCT-1")
                                        .put("zsku_child", "Z-PRODUCT-1-1")))));
        RecordingGatewaySessionFactory sessionFactory = new RecordingGatewaySessionFactory(session);
        RealNoonProductInterfaceSmokeProvider provider = new RealNoonProductInterfaceSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                sessionFactory,
                OFFER_LIST_URL,
                100
        );

        NoonInterfacePullPage page = provider.fetchPage(productRequest(), 1);

        assertEquals("PRJ245027", sessionFactory.lastBinding.getProjectCode());
        assertEquals("STR245027-NAE", sessionFactory.lastBinding.getStoreCode());
        assertEquals("AE", sessionFactory.lastBinding.getSiteCode());
        assertEquals(OFFER_LIST_URL, session.posts.get(0).url);
        assertTrue(session.posts.get(0).withProject);
        assertEquals(1, session.posts.get(0).body.get("page").asInt());
        assertEquals(100, session.posts.get(0).body.get("per_page").asInt());
        assertEquals("STR245027-NAE", session.posts.get(0).body.get("noon_store_code").asText());
        assertEquals("noon", session.posts.get(0).body.get("noonChannelType").asText());
        assertEquals(1, page.getItems().size());
        assertEquals("Z-PRODUCT-1", page.getItems().get(0).get("zsku_parent"));
        assertEquals(101, page.getTotalItems());
        assertTrue(page.isHasNextPage());
        assertEquals(1, page.getRequestCount());
    }

    @Test
    void salesReportProviderShouldCreatePollAndDownloadThroughGatewaySession() throws Exception {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.enqueuePostResponse(objectMapper.createObjectNode().put("export", "EXP-1"));
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("export", objectMapper.createObjectNode()
                        .put("status_code", "COMPLETE")
                        .put("result", "{\"total_rows\":1}")
                        .put("download_url", "https://download.test/sales.csv")));
        session.downloadBytes = "date,sku_parent,units_sold,sales_amount,currency\n"
                .getBytes(StandardCharsets.UTF_8);
        RealNoonSalesReportSmokeProvider provider = new RealNoonSalesReportSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session),
                EXPORT_CREATE_URL,
                EXPORT_STATUS_URL,
                ""
        );
        NoonReportPullRequest request = salesRequest();

        String exportId = provider.createExport(request);
        NoonReportExportStatus status = provider.pollExport(request, exportId);
        byte[] downloaded = provider.download(request, status.getDownloadUrl());

        assertEquals("EXP-1", exportId);
        assertEquals("https://download.test/sales.csv", status.getDownloadUrl());
        assertSame(session.downloadBytes, downloaded);
        assertEquals(EXPORT_CREATE_URL, session.posts.get(0).url);
        assertTrue(session.posts.get(0).withProject);
        assertEquals("PRJ245027", session.posts.get(0).extraHeaders.get("X-Project"));
        assertEquals("en-ae", session.posts.get(0).extraHeaders.get("X-Locale"));
        assertEquals("en", session.posts.get(0).extraHeaders.get("X-Lang"));
        assertEquals("noon_catalog_reports_productviewsandsalesdata", session.posts.get(0).body.get("exportCategoryCode").asText());
        JsonNode params = objectMapper.readTree(session.posts.get(0).body.get("params").asText());
        assertEquals("245027", params.get("id_partner").asText());
        assertEquals("ae", params.get("country").asText());
        assertEquals("2026-05-21", params.get("from_date").asText());
        assertEquals("2026-05-21", params.get("to_date").asText());
        assertEquals(EXPORT_STATUS_URL, session.posts.get(1).url);
        assertTrue(session.posts.get(1).withProject);
        assertEquals("PRJ245027", session.posts.get(1).extraHeaders.get("X-Project"));
        assertEquals("en-ae", session.posts.get(1).extraHeaders.get("X-Locale"));
        assertEquals("EXP-1", session.posts.get(1).body.get("exportCode").asText());
        assertEquals("https://download.test/sales.csv", session.downloadUrl);
    }

    @Test
    void salesReportProviderShouldNotFallbackToOwnerAggregatedCookie() {
        StoreSyncStoreRecord store = boundStore();
        store.setNoonPartnerCookie(null);
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setNoonPartnerCookie("session=wrong-project");
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(store);
        when(storeSyncMapper.selectOwnerContext(10002L)).thenReturn(owner);
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.enqueuePostResponse(objectMapper.createObjectNode().put("export", "EXP-1"));
        RecordingGatewaySessionFactory sessionFactory = new RecordingGatewaySessionFactory(session);
        RealNoonSalesReportSmokeProvider provider = new RealNoonSalesReportSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                sessionFactory,
                EXPORT_CREATE_URL,
                EXPORT_STATUS_URL,
                ""
        );

        provider.createExport(salesRequest());

        assertEquals(null, sessionFactory.lastBinding.getPersistedCookie());
    }

    @Test
    void salesReportProviderShouldWrapDownloadUrlWithConfiguredProxyTemplate() throws Exception {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        RealNoonSalesReportSmokeProvider provider = new RealNoonSalesReportSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session),
                EXPORT_CREATE_URL,
                EXPORT_STATUS_URL,
                "https://proxy.test/v1/?api_key=redacted&url={encodedUrl}"
        );

        String downloadUrl = "https://storage.test/report file.csv?x=1&y=two";
        provider.download(salesRequest(), downloadUrl);

        assertEquals(
                "https://proxy.test/v1/?api_key=redacted&url="
                        + URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8),
                session.downloadUrl
        );
        assertFalse(session.downloadWithProject);
    }

    @Test
    void salesPageQueryProviderShouldFetchDashboardListThroughGatewaySession() {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        ObjectNode response = objectMapper.createObjectNode();
        for (int index = 1; index <= 10; index++) {
            response.withArray("hits").add(objectMapper.createObjectNode()
                    .put("item_nr", "NAEI5000000000" + index + "-1")
                    .put("partner_sku", "PARTNER-SKU-" + index)
                    .put("sku", "Z-SKU-" + index)
                    .put("status", "Shipped")
                    .put("gmv_lcy", 49.5)
                    .put("currency_code", "AED")
                    .put("order_timestamp", "2026-05-21T21:31:59"));
        }
        response.put("total", 21);
        session.enqueuePostResponse(response);
        RealNoonSalesPageQueryProvider provider = new RealNoonSalesPageQueryProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session),
                SALES_PAGE_LIST_URL,
                10
        );

        NoonInterfacePullPage page = provider.fetchPage(salesPageRequest(), 1);

        assertEquals(SALES_PAGE_LIST_URL, session.posts.get(0).url);
        assertFalse(session.posts.get(0).withProject);
        assertEquals("PRJ245027", session.posts.get(0).extraHeaders.get("X-Project"));
        assertEquals("en-ae", session.posts.get(0).extraHeaders.get("X-Locale"));
        assertEquals("en", session.posts.get(0).extraHeaders.get("X-Lang"));
        assertEquals("AE", session.posts.get(0).body.get("country_code").asText());
        assertEquals(1, session.posts.get(0).body.get("page").asInt());
        assertEquals(10, session.posts.get(0).body.get("per_page").asInt());
        assertTrue(session.posts.get(0).body.get("filters").isObject());
        assertEquals("", session.posts.get(0).body.get("search").asText());
        assertEquals("2026-05-01", session.posts.get(0).body.get("from_date").asText());
        assertEquals("2026-05-22", session.posts.get(0).body.get("to_date").asText());
        assertEquals(10, page.getItems().size());
        assertEquals("NAEI50000000001-1", page.getItems().get(0).get("item_nr"));
        assertEquals(21, page.getTotalItems());
        assertTrue(page.isHasNextPage());
        assertEquals(1, page.getRequestCount());
    }

    @Test
    void providerShouldMapMissingBindingToProviderNotConfigured() {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(null);
        RealNoonProductInterfaceSmokeProvider provider = new RealNoonProductInterfaceSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(new RecordingGatewaySession(objectMapper)),
                OFFER_LIST_URL,
                100
        );

        NoonInterfacePullException exception = assertThrows(
                NoonInterfacePullException.class,
                () -> provider.fetchPage(productRequest(), 1)
        );

        assertTrue(exception.getMessage().contains("provider not configured"));
    }

    @Test
    void providerShouldMapGatewayRateLimitFailureToTypedFailureText() {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.postFailure = new IllegalStateException("HTTP 429 Too Many Requests");
        RealNoonProductInterfaceSmokeProvider provider = new RealNoonProductInterfaceSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session),
                OFFER_LIST_URL,
                100
        );

        NoonInterfacePullException exception = assertThrows(
                NoonInterfacePullException.class,
                () -> provider.fetchPage(productRequest(), 1)
        );

        assertTrue(exception.getMessage().contains("rate limited"));
    }

    @Test
    void orderReportProviderShouldGenerateLatestAndDownloadSalesDashboardExport() {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.enqueuePostResponse(objectMapper.getNodeFactory().textNode("ok"));
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .put("id_exports", 572271)
                .put("status", "Success")
                .set("export_attachment", objectMapper.createObjectNode()
                        .put("file_type", "csv")
                        .put("file_name", "sales_export_05-26-2026_03:08:15_245027_AE_572271.csv")
                        .put("url", "https://download.test/order-sales.csv")));
        session.downloadBytes = "id_partner,offer_price,currency_code\n245027,49.5,AED\n"
                .getBytes(StandardCharsets.UTF_8);
        RealNoonOrderReportSmokeProvider provider = new RealNoonOrderReportSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session),
                SALES_DASHBOARD_EXPORT_GENERATE_URL,
                SALES_DASHBOARD_EXPORT_LATEST_URL,
                ""
        );

        String exportId = provider.createExport(orderRequest());
        NoonReportExportStatus status = provider.pollExport(orderRequest(), exportId);
        byte[] downloaded = provider.download(orderRequest(), status.getDownloadUrl());

        assertEquals("sales-dashboard-export:2026-05-21..2026-05-21", exportId);
        assertTrue(status.isReady());
        assertEquals("https://download.test/order-sales.csv", status.getDownloadUrl());
        assertSame(session.downloadBytes, downloaded);
        assertEquals(SALES_DASHBOARD_EXPORT_GENERATE_URL, session.posts.get(0).url);
        assertFalse(session.posts.get(0).withProject);
        assertEquals("PRJ245027", session.posts.get(0).extraHeaders.get("X-Project"));
        assertEquals("en-ae", session.posts.get(0).extraHeaders.get("X-Locale"));
        assertEquals("en", session.posts.get(0).extraHeaders.get("X-Lang"));
        assertEquals("AE", session.posts.get(0).body.get("country_code").asText());
        assertTrue(session.posts.get(0).body.get("filters").isObject());
        assertEquals("", session.posts.get(0).body.get("search").asText());
        assertEquals("2026-05-21", session.posts.get(0).body.get("from_date").asText());
        assertEquals("2026-05-21", session.posts.get(0).body.get("to_date").asText());
        assertEquals(SALES_DASHBOARD_EXPORT_LATEST_URL, session.posts.get(1).url);
        assertFalse(session.posts.get(1).withProject);
        assertEquals("https://download.test/order-sales.csv", session.downloadUrl);
        assertFalse(session.downloadWithProject);
    }

    @Test
    void orderReportProviderShouldIncludeScopeWhenSalesDashboardExportFails() {
        when(storeSyncMapper.selectOwnerStore(10002L, "STR245027-NAE")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.enqueuePostResponse(objectMapper.createObjectNode().put("error", "Export is not configured"));
        RealNoonOrderReportSmokeProvider provider = new RealNoonOrderReportSmokeProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session),
                SALES_DASHBOARD_EXPORT_GENERATE_URL,
                SALES_DASHBOARD_EXPORT_LATEST_URL,
                ""
        );

        NoonInterfacePullException exception = assertThrows(
                NoonInterfacePullException.class,
                () -> provider.createExport(orderRequest())
        );

        assertTrue(exception.getMessage().contains("provider not configured"));
        assertTrue(exception.getMessage().contains("ownerUserId=10002"));
        assertTrue(exception.getMessage().contains("storeCode=STR245027-NAE"));
        assertTrue(exception.getMessage().contains("siteCode=AE"));
    }

    private NoonInterfacePullRequest productRequest() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .targetIdentity("catalog:list")
                .build();
    }

    private NoonReportPullRequest salesRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.SALES)
                .reportType("productviewsandsalesdata")
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .build();
    }

    private NoonInterfacePullRequest salesPageRequest() {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.SALES)
                .requestName("sales-page-query")
                .targetIdentity("sales-page-query:2026-05-01..2026-05-22")
                .dateFrom(LocalDate.of(2026, 5, 1))
                .dateTo(LocalDate.of(2026, 5, 22))
                .build();
    }

    private NoonReportPullRequest orderRequest() {
        return NoonReportPullRequest.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.ORDER)
                .reportType(NoonOrderReportDescriptor.REPORT_TYPE)
                .dateFrom(LocalDate.of(2026, 5, 21))
                .dateTo(LocalDate.of(2026, 5, 21))
                .build();
    }

    private StoreSyncStoreRecord boundStore() {
        StoreSyncStoreRecord record = new StoreSyncStoreRecord();
        record.setProjectCode("PRJ245027");
        record.setStoreCode("STR245027-NAE");
        record.setSite("AE");
        record.setNoonPartnerId("245027");
        record.setNoonPartnerProjectUser("project-user@example.com");
        record.setNoonPartnerPwd("secret");
        record.setNoonPartnerCookie("session=already-present");
        return record;
    }

    private static class RecordingGatewaySessionFactory implements NoonPullGatewaySessionFactory {
        private final RecordingGatewaySession session;
        private NoonPullStoreBinding lastBinding;

        private RecordingGatewaySessionFactory(RecordingGatewaySession session) {
            this.session = session;
        }

        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            lastBinding = binding;
            return session;
        }
    }

    private static class RecordingGatewaySession implements NoonPullGatewaySession {
        private final ObjectMapper objectMapper;
        private final List<PostCall> posts = new ArrayList<>();
        private final List<JsonNode> postResponses = new ArrayList<>();
        private RuntimeException postFailure;
        private byte[] downloadBytes = new byte[0];
        private String downloadUrl;
        private boolean downloadWithProject;

        private RecordingGatewaySession(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        private void enqueuePostResponse(JsonNode response) {
            postResponses.add(response);
        }

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            if (postFailure != null) {
                throw postFailure;
            }
            posts.add(new PostCall(url, body, withProject, extraHeaders));
            if (postResponses.isEmpty()) {
                return objectMapper.createObjectNode();
            }
            return postResponses.remove(0);
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            downloadUrl = url;
            downloadWithProject = withProject;
            return downloadBytes;
        }
    }

    private static class PostCall {
        private final String url;
        private final JsonNode body;
        private final boolean withProject;
        private final Map<String, String> extraHeaders;

        private PostCall(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            this.url = url;
            this.body = body;
            this.withProject = withProject;
            this.extraHeaders = extraHeaders;
        }
    }
}
