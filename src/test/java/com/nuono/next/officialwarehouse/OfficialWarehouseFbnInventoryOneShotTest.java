package com.nuono.next.officialwarehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnInventoryOneShotTest {

    @Test
    void dp07aUsesOneShotOpenAndOneShotBytesPostOnly() {
        ObjectMapper objectMapper = new ObjectMapper();
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        when(resolver.resolve(any(NoonInterfacePullRequest.class))).thenReturn(binding());
        RecordingSession session = new RecordingSession();
        RecordingFactory factory = new RecordingFactory(session);
        OfficialWarehouseFbnInventoryProvider provider =
                new OfficialWarehouseFbnInventoryProvider(
                        objectMapper,
                        resolver,
                        factory
                );

        OfficialWarehouseFbnInventoryProvider.InventoryPage page = provider.fetchPage(
                new OfficialWarehouseFbnInventoryProvider.PullRequest(
                        307L,
                        "STR108065-NSA",
                        "SA"
                ),
                1
        );

        assertEquals(0, page.items.size());
        assertEquals(1, factory.oneShotOpens.get());
        assertEquals(0, factory.legacyLogins.get());
        assertEquals(1, session.oneShotPosts.get());
        assertEquals(0, session.legacyPosts.get());
        assertNull(page.providerGenerationToken);
        assertNull(page.providerExportToken);
        assertNull(page.declaredCollectionCount);
    }

    private NoonPullStoreBinding binding() {
        return new NoonPullStoreBinding(
                307L,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "108065",
                "merchant@example.com",
                "project-user",
                "sid=persisted"
        );
    }

    private static final class RecordingFactory implements NoonPullGatewaySessionFactory {
        private final RecordingSession session;
        private final AtomicInteger legacyLogins = new AtomicInteger();
        private final AtomicInteger oneShotOpens = new AtomicInteger();

        private RecordingFactory(RecordingSession session) {
            this.session = session;
        }

        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            legacyLogins.incrementAndGet();
            return session;
        }

        @Override
        public NoonPullGatewaySession openOneShot(NoonPullStoreBinding binding) {
            oneShotOpens.incrementAndGet();
            return session;
        }
    }

    private static final class RecordingSession implements NoonPullGatewaySession {
        private final AtomicInteger legacyPosts = new AtomicInteger();
        private final AtomicInteger oneShotPosts = new AtomicInteger();

        @Override
        public JsonNode postJson(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            throw new AssertionError("JSON transport is not the DP07A contract");
        }

        @Override
        public byte[] postBytes(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            legacyPosts.incrementAndGet();
            return response();
        }

        @Override
        public byte[] postBytesOnce(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            oneShotPosts.incrementAndGet();
            return response();
        }

        @Override
        public byte[] getBytes(
                String url,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            return new byte[0];
        }

        private byte[] response() {
            return ("{\"data\":{\"rows\":[],\"has_next\":false,"
                    + "\"total_pages\":1}}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
