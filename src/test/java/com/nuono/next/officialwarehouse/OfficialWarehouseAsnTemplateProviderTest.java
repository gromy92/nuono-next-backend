package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfficialWarehouseAsnTemplateProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchesCurrentEligibleTemplateWithStoreSiteHeadersAndNoRefreshCall() {
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        NoonPullGatewaySessionFactory sessions = mock(NoonPullGatewaySessionFactory.class);
        NoonPullGatewaySession session = mock(NoonPullGatewaySession.class);
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                307L, "PRJ108065", "STR108065-NSA", "SA", "69486", "user@test", "cookie"
        );
        when(resolver.resolve(any(NoonInterfacePullRequest.class))).thenReturn(binding);
        when(sessions.openOneShot(binding)).thenReturn(session);
        String downloadUrl = "https://storage.googleapis.com/noon-bucket/fbn_eligible_item_69486_2026-08-23_SA.csv?signature=secret";
        when(session.postJsonOnce(eq(OfficialWarehouseAsnTemplateProvider.ELIGIBLE_TEMPLATE_URL), any(), eq(false), anyMap()))
                .thenReturn(objectMapper.createObjectNode().put("media_path", downloadUrl).put("is_synced", 1));
        when(session.getBytesOnce(eq(downloadUrl), eq(false), anyMap()))
                .thenReturn("csv-body".getBytes(StandardCharsets.UTF_8));
        OfficialWarehouseAsnTemplateProvider provider = new OfficialWarehouseAsnTemplateProvider(
                objectMapper, resolver, sessions
        );

        OfficialWarehouseAsnTemplateProvider.TemplateFile result = provider.fetchLatest(
                new OfficialWarehouseAsnTemplateProvider.PullRequest(307L, "STR108065-NSA", "SA")
        );

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(session).postJsonOnce(eq(OfficialWarehouseAsnTemplateProvider.ELIGIBLE_TEMPLATE_URL), any(), eq(false), headers.capture());
        assertThat(headers.getValue())
                .containsEntry("Country-Code", "sa")
                .containsEntry("Id-Partner", "69486")
                .containsEntry("X-Project", "PRJ108065")
                .doesNotContainKeys("Cookie", "Authorization");
        assertThat(result.fileName).isEqualTo("fbn_eligible_item_69486_2026-08-23_SA.csv");
        assertThat(result.content).isEqualTo("csv-body".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsMissingOrUnexpectedDownloadUrlBeforeDownloading() {
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        NoonPullGatewaySessionFactory sessions = mock(NoonPullGatewaySessionFactory.class);
        NoonPullGatewaySession session = mock(NoonPullGatewaySession.class);
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                307L, "PRJ108065", "STR108065-NSA", "SA", "69486", "user@test", "cookie"
        );
        when(resolver.resolve(any(NoonInterfacePullRequest.class))).thenReturn(binding);
        when(sessions.openOneShot(binding)).thenReturn(session);
        OfficialWarehouseAsnTemplateProvider provider = new OfficialWarehouseAsnTemplateProvider(
                objectMapper, resolver, sessions
        );

        when(session.postJsonOnce(any(), any(), anyBoolean(), anyMap()))
                .thenReturn(objectMapper.createObjectNode());
        assertThatThrownBy(() -> provider.fetchLatest(
                new OfficialWarehouseAsnTemplateProvider.PullRequest(307L, "STR108065-NSA", "SA")
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("尚未就绪");

        when(session.postJsonOnce(any(), any(), anyBoolean(), anyMap()))
                .thenReturn(objectMapper.createObjectNode()
                        .put("is_synced", 0)
                        .put("media_path", "https://storage.googleapis.com/noon-bucket/old.csv"));
        assertThatThrownBy(() -> provider.fetchLatest(
                new OfficialWarehouseAsnTemplateProvider.PullRequest(307L, "STR108065-NSA", "SA")
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("仍在生成");

        when(session.postJsonOnce(any(), any(), anyBoolean(), anyMap()))
                .thenReturn(objectMapper.createObjectNode().put("media_path", "https://evil.example/template.csv"));
        assertThatThrownBy(() -> provider.fetchLatest(
                new OfficialWarehouseAsnTemplateProvider.PullRequest(307L, "STR108065-NSA", "SA")
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("非预期");
    }
}
