package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseFbnExportProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Test
    void listExportsUsesFbnReportListContractWithoutSecretHeaders() {
        when(storeSyncMapper.selectOwnerStore(307L, "STR108065-NSA")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession();
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode()
                        .set("rows", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("exportCode", "EXP4URWS7NYN")
                                        .put("status_code", "COMPLETE")
                                        .put("exportCategoryCode", "fbn_inbound_fbnreceivedreport")
                                        .put("fileName", "fbn-received.csv")
                                        .put("createdAt", "2026-06-18T10:00:00Z")))));
        OfficialWarehouseFbnExportProvider provider = new OfficialWarehouseFbnExportProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session)
        );

        OfficialWarehouseFbnExportProvider.ExportListPage page = provider.listExports(
                new OfficialWarehouseFbnExportProvider.PullRequest(307L, "STR108065-NSA", "SA"),
                1,
                20
        );

        assertThat(session.posts).hasSize(1);
        PostCall post = session.posts.get(0);
        assertThat(post.url).isEqualTo("https://fbn.noon.partners/_svc/export/list");
        assertThat(post.withProject).isFalse();
        assertThat(post.body.path("page").asInt()).isEqualTo(1);
        assertThat(post.body.path("perPage").asInt()).isEqualTo(20);
        assertThat(post.body.path("tenantCode").asText()).isEqualTo("fbn");
        assertThat(post.extraHeaders)
                .containsEntry("Country-Code", "sa")
                .containsEntry("Id-Partner", "108065")
                .containsEntry("Origin", "https://fbn.noon.partners")
                .containsEntry("Referer", "https://fbn.noon.partners/en-sa/fbnreports?project=PRJ108065")
                .containsEntry("X-Locale", "en-sa")
                .containsEntry("X-Platform", "web")
                .containsEntry("X-Project", "PRJ108065")
                .doesNotContainKeys("Cookie", "Authorization");
        assertThat(page.items).hasSize(1);
        assertThat(page.items.get(0).exportCode).isEqualTo("EXP4URWS7NYN");
        assertThat(page.items.get(0).status).isEqualTo("COMPLETE");
        assertThat(page.items.get(0).reportType).isEqualTo("fbn_inbound_fbnreceivedreport");
        assertThat(page.items.get(0).fileName).isEqualTo("fbn-received.csv");
    }

    @Test
    void listExportsReadsNoonExportCodeArray() {
        when(storeSyncMapper.selectOwnerStore(307L, "STR108065-NAE")).thenReturn(boundAeStore());
        RecordingGatewaySession session = new RecordingGatewaySession();
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("export_code", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("export_code", "EXP4KFIA7LQZ")
                                .put("export_category_code", "fbn_inbound_scheduleddeliveryaccuracy")
                                .put("name_en", "Scheduled Delivery Accuracy")
                                .put("status_code", "COMPLETE")
                                .put("result", "{\"total_rows\": 16}")
                                .put("created_at", "2026-06-22T03:53:27"))
                        .add(objectMapper.createObjectNode()
                                .put("export_code", "EXP4FE75S6YK")
                                .put("export_category_code", "fbn_inbound_fbnreceivedreport")
                                .put("name_en", "FBN Received Report")
                                .put("status_code", "COMPLETE")
                                .put("result", "{\"total_rows\": 145}")
                                .put("created_at", "2026-06-22T03:53:20"))));
        OfficialWarehouseFbnExportProvider provider = new OfficialWarehouseFbnExportProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session)
        );

        OfficialWarehouseFbnExportProvider.ExportListPage page = provider.listExports(
                new OfficialWarehouseFbnExportProvider.PullRequest(307L, "STR108065-NAE", "AE"),
                1,
                20
        );

        PostCall post = session.posts.get(0);
        assertThat(post.extraHeaders)
                .containsEntry("Country-Code", "ae")
                .containsEntry("Referer", "https://fbn.noon.partners/en-ae/fbnreports?project=PRJ108065");
        assertThat(page.items).hasSize(2);
        assertThat(page.items.get(0).exportCode).isEqualTo("EXP4KFIA7LQZ");
        assertThat(page.items.get(0).reportType).isEqualTo("fbn_inbound_scheduleddeliveryaccuracy");
        assertThat(page.items.get(0).status).isEqualTo("COMPLETE");
        assertThat(page.items.get(0).fileName).isEqualTo("Scheduled Delivery Accuracy");
        assertThat(page.items.get(1).exportCode).isEqualTo("EXP4FE75S6YK");
        assertThat(page.items.get(1).reportType).isEqualTo("fbn_inbound_fbnreceivedreport");
        assertThat(page.items.get(1).fileName).isEqualTo("FBN Received Report");
    }

    @Test
    void exportStatusUsesExportCodeContractAndReadsDownloadUrl() {
        when(storeSyncMapper.selectOwnerStore(307L, "STR108065-NSA")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession();
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("export", objectMapper.createObjectNode()
                        .put("export_code", "EXP4URWS7NYN")
                        .put("status_code", "COMPLETE")
                        .put("download_url", "https://download.test/fbn-received.csv")
                        .put("file_name", "fbn-received.csv")
                        .set("result", objectMapper.createObjectNode().put("total_rows", 177))));
        OfficialWarehouseFbnExportProvider provider = new OfficialWarehouseFbnExportProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session)
        );

        OfficialWarehouseFbnExportProvider.ExportStatus status = provider.exportStatus(
                new OfficialWarehouseFbnExportProvider.PullRequest(307L, "STR108065-NSA", "SA"),
                "EXP4URWS7NYN",
                true
        );

        assertThat(session.posts).hasSize(1);
        PostCall post = session.posts.get(0);
        assertThat(post.url).isEqualTo("https://fbn.noon.partners/_svc/export/status");
        assertThat(post.withProject).isFalse();
        assertThat(post.body.path("exportCode").asText()).isEqualTo("EXP4URWS7NYN");
        assertThat(post.body.path("log").asBoolean()).isTrue();
        assertThat(post.extraHeaders).doesNotContainKeys("Cookie", "Authorization");
        assertThat(status.exportCode).isEqualTo("EXP4URWS7NYN");
        assertThat(status.status).isEqualTo("COMPLETE");
        assertThat(status.downloadUrl).isEqualTo("https://download.test/fbn-received.csv");
        assertThat(status.fileName).isEqualTo("fbn-received.csv");
        assertThat(status.totalRows).isEqualTo(177);
    }

    @Test
    void createExportUsesFbnReportCreateContractWithoutSecretHeaders() throws Exception {
        when(storeSyncMapper.selectOwnerStore(307L, "STR108065-NSA")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession();
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode()
                        .put("exportCode", "EXP-SDA")
                        .put("status", "PENDING")
                        .put("exportCategoryCode", "fbn_inbound_scheduleddeliveryaccuracy")));
        OfficialWarehouseFbnExportProvider provider = new OfficialWarehouseFbnExportProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session)
        );

        OfficialWarehouseFbnExportProvider.CreateExportResult result = provider.createExport(
                new OfficialWarehouseFbnExportProvider.PullRequest(307L, "STR108065-NSA", "SA"),
                new OfficialWarehouseFbnExportProvider.CreateExportRequest(
                        "fbn_inbound_scheduleddeliveryaccuracy",
                        "2026-06-20",
                        "2026-06-20"
                )
        );

        assertThat(session.posts).hasSize(1);
        PostCall post = session.posts.get(0);
        assertThat(post.url).isEqualTo("https://fbn.noon.partners/_svc/export/create");
        assertThat(post.withProject).isFalse();
        assertThat(post.body.path("exportCategoryCode").asText()).isEqualTo("fbn_inbound_scheduleddeliveryaccuracy");
        assertThat(post.body.path("channelCode").asText()).isEqualTo("web");
        JsonNode params = objectMapper.readTree(post.body.path("params").asText());
        assertThat(params.path("id_partner").asInt()).isEqualTo(108065);
        assertThat(params.path("country").asText()).isEqualTo("sa");
        assertThat(params.path("from_date").asText()).isEqualTo("2026-06-20");
        assertThat(params.path("to_date").asText()).isEqualTo("2026-06-20");
        assertThat(post.extraHeaders)
                .containsEntry("Country-Code", "sa")
                .containsEntry("Id-Partner", "108065")
                .containsEntry("Referer", "https://fbn.noon.partners/en-sa/fbnreports?project=PRJ108065")
                .doesNotContainKeys("Cookie", "Authorization");
        assertThat(result.exportCode).isEqualTo("EXP-SDA");
        assertThat(result.status).isEqualTo("PENDING");
        assertThat(result.reportType).isEqualTo("fbn_inbound_scheduleddeliveryaccuracy");
    }

    private StoreSyncStoreRecord boundStore() {
        StoreSyncStoreRecord record = new StoreSyncStoreRecord();
        record.setProjectCode("PRJ108065");
        record.setStoreCode("STR108065-NSA");
        record.setSite("SA");
        record.setNoonPartnerId("108065");
        record.setNoonPartnerProjectUser("project-user@example.com");
        record.setNoonPartnerPwd("secret");
        record.setNoonPartnerCookie("session=already-present");
        return record;
    }

    private StoreSyncStoreRecord boundAeStore() {
        StoreSyncStoreRecord record = boundStore();
        record.setStoreCode("STR108065-NAE");
        record.setSite("AE");
        return record;
    }

    private static class RecordingGatewaySessionFactory implements NoonPullGatewaySessionFactory {
        private final RecordingGatewaySession session;

        private RecordingGatewaySessionFactory(RecordingGatewaySession session) {
            this.session = session;
        }

        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            return session;
        }
    }

    private static class RecordingGatewaySession implements NoonPullGatewaySession {
        private final List<PostCall> posts = new ArrayList<>();
        private final List<JsonNode> postResponses = new ArrayList<>();

        private void enqueuePostResponse(JsonNode response) {
            postResponses.add(response);
        }

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            posts.add(new PostCall(url, body, withProject, extraHeaders));
            return postResponses.remove(0);
        }

        @Override
        public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
            return new byte[0];
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
