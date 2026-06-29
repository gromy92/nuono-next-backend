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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseFbnInventoryProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Test
    void fetchPageUsesFbnInventoryRequestContractWithoutSecretHeaders() {
        when(storeSyncMapper.selectOwnerStore(307L, "STR108065-NSA")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.enqueuePostResponse(objectMapper.createObjectNode()
                .set("data", objectMapper.createObjectNode()
                        .set("rows", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("warehouse_code", "RUH01")
                                        .put("qty", 7)
                                        .put("inventory_type", "saleable")
                                        .put("sku", "N422")
                                        .put("partner_sku", "PAPERSAYSB422")
                                        .put("inventory_snapshot_at", "2026-06-18 10:11:12"))
                                .add(objectMapper.createObjectNode()
                                        .put("warehouse_code", "RUH01")
                                        .put("qty", 2)
                                        .put("inventory_type", "graded_returns_cir")
                                        .put("reason_code", "customer_return")
                                        .put("sku", "N422")
                                        .put("partner_sku", "PAPERSAYSB422"))
                                .add(objectMapper.createObjectNode()
                                        .put("warehouse_code", "RUH01")
                                        .put("qty", 3)
                                        .put("inventory_type", "inbound_ps")
                                        .put("reason_code", "rr_damaged_packing")
                                        .put("sku", "N422")
                                        .put("partner_sku", "PAPERSAYSB422")))));
        OfficialWarehouseFbnInventoryProvider provider = new OfficialWarehouseFbnInventoryProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session)
        );

        OfficialWarehouseFbnInventoryProvider.InventoryPage page = provider.fetchPage(
                new OfficialWarehouseFbnInventoryProvider.PullRequest(307L, "STR108065-NSA", "SA"),
                1
        );

        assertThat(session.posts).hasSize(1);
        PostCall post = session.posts.get(0);
        assertThat(post.url).isEqualTo("https://fbn.noon.partners/_svc/sc-fbn/api/v5/seller-lab/fbn-inventory");
        assertThat(post.withProject).isFalse();
        assertThat(post.body.path("inventory_tab_name").asText()).isEqualTo("export");
        assertThat(post.body.path("filters").isObject()).isTrue();
        assertThat(post.body.path("pagination").path("page").asInt()).isEqualTo(1);
        assertThat(post.extraHeaders)
                .containsEntry("Country-Code", "sa")
                .containsEntry("Id-Partner", "108065")
                .containsEntry("X-Locale", "en-sa")
                .containsEntry("X-Platform", "web")
                .containsEntry("X-Project", "PRJ108065")
                .doesNotContainKeys("Cookie", "Authorization");
        assertThat(page.items).hasSize(3);
        assertThat(page.items.get(0).stockBucket).isEqualTo("SELLABLE");
        assertThat(page.items.get(1).stockBucket).isEqualTo("RETURNED");
        assertThat(page.items.get(2).stockBucket).isEqualTo("RECEIVING_EXCEPTION");
    }

    @Test
    void fetchPageParsesFbnInventoryCsvResponseReturnedByNoon() {
        when(storeSyncMapper.selectOwnerStore(307L, "STR108065-NSA")).thenReturn(boundStore());
        RecordingGatewaySession session = new RecordingGatewaySession(objectMapper);
        session.postJsonFailure = new IllegalStateException("请求 Noon 失败：Unrecognized token 'box_barcode'");
        session.postBytes = ("box_barcode,warehouse_code,barcode,qty,id_partner,inventory_type,pbarcode,sku,partner_sku,weight,volumetric_weight,shortest_side,median_side,longest_side,title,brand,family,reason_code,inventory_snapshot_at,country_code,classification_code\n"
                + ",RUH01S,,7,108065,saleable,PAPERSAYSB422,N422,PAPERSAYSB422,,,,,,Paper Says Cards,Paper Says,,,\"2026-06-17, 09:37:08\",SA,standard_parcel\n"
                + ",RUH01S,,2,108065,graded_returns_cir,PAPERSAYSB422,N422,PAPERSAYSB422,,,,,,Paper Says Cards,Paper Says,,customer_return,\"2026-06-17, 09:37:08\",SA,standard_parcel\n"
                + ",RUH01S,,3,108065,inbound_ps,PAPERSAYSB422,N422,PAPERSAYSB422,,,,,,Paper Says Cards,Paper Says,,rr_damaged_packing,\"2026-06-17, 09:37:08\",SA,standard_parcel\n")
                .getBytes(StandardCharsets.UTF_8);
        OfficialWarehouseFbnInventoryProvider provider = new OfficialWarehouseFbnInventoryProvider(
                objectMapper,
                new NoonPullStoreBindingResolver(storeSyncMapper),
                new RecordingGatewaySessionFactory(session)
        );

        OfficialWarehouseFbnInventoryProvider.InventoryPage page = provider.fetchPage(
                new OfficialWarehouseFbnInventoryProvider.PullRequest(307L, "STR108065-NSA", "SA"),
                1
        );

        assertThat(session.posts).hasSize(1);
        PostCall post = session.posts.get(0);
        assertThat(post.extraHeaders)
                .containsEntry("Accept", "text/csv,application/json,*/*")
                .doesNotContainKeys("Cookie", "Authorization");
        assertThat(page.hasNextPage).isFalse();
        assertThat(page.items).hasSize(3);
        assertThat(page.items)
                .extracting(item -> item.stockBucket)
                .containsExactly("SELLABLE", "RETURNED", "RECEIVING_EXCEPTION");
        assertThat(page.items)
                .extracting(item -> item.inventorySnapshotAt)
                .containsOnly("2026-06-17 09:37:08");
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
        private final ObjectMapper objectMapper;
        private final List<PostCall> posts = new ArrayList<>();
        private final List<JsonNode> postResponses = new ArrayList<>();
        private RuntimeException postJsonFailure;
        private byte[] postBytes = new byte[0];

        private RecordingGatewaySession(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        private void enqueuePostResponse(JsonNode response) {
            postResponses.add(response);
        }

        @Override
        public JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            if (postJsonFailure != null) {
                throw postJsonFailure;
            }
            posts.add(new PostCall(url, body, withProject, extraHeaders));
            return postResponses.remove(0);
        }

        @Override
        public byte[] postBytes(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders) {
            posts.add(new PostCall(url, body, withProject, extraHeaders));
            if (postBytes.length > 0) {
                return postBytes;
            }
            try {
                return objectMapper.writeValueAsBytes(postResponses.remove(0));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
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
