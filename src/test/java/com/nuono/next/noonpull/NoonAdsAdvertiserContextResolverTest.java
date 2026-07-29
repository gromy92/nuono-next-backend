package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonAdsAdvertiserContextResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoonAdsAdvertiserContextResolver resolver =
            new NoonAdsAdvertiserContextResolver(objectMapper, "https://ads.test/accounts");

    @Test
    void shouldResolveActiveAccountAndBuildBothAdvertiserHeaders() {
        RecordingSession session = new RecordingSession(
                "["
                        + "{\"idPartner\":69486,\"partnerCode\":\"p_69486\","
                        + "\"advertiserCode\":\"ADV_69486\",\"isActive\":1,\"isEnabled\":true},"
                        + "{\"idPartner\":69486,\"advertiserCode\":\"ADV_DISABLED\",\"isActive\":0}"
                        + "]"
        );
        NoonPullStoreBinding binding = binding("PRJ69486", "69486", "SA");

        NoonAdsAdvertiserContext context = resolver.resolve(session, binding);
        Map<String, String> headers = resolver.headers(binding, context, "*/*");

        assertEquals("ADV_69486", context.getAdvertiserCode());
        assertEquals("69486", headers.get("x-id-advertiser"));
        assertEquals("ADV_69486", headers.get("x-advertiser-codes"));
        assertEquals("true", headers.get("x-seller-view"));
        assertEquals("en-sa", headers.get("x-locale"));
        assertEquals("PRJ69486", session.headers.get("X-Project"));
    }

    @Test
    void shouldRejectMissingOrAmbiguousActiveAdvertiserContext() {
        NoonPullStoreBinding binding = binding("PRJ108065", "108065", "AE");

        NoonInterfacePullException missing = assertThrows(
                NoonInterfacePullException.class,
                () -> resolver.resolve(new RecordingSession("[]"), binding)
        );
        NoonInterfacePullException ambiguous = assertThrows(
                NoonInterfacePullException.class,
                () -> resolver.resolve(
                        new RecordingSession(
                                "["
                                        + "{\"idPartner\":108065,\"advertiserCode\":\"ADV_ONE\"},"
                                        + "{\"partnerCode\":\"p_108065\",\"advertiserCode\":\"ADV_TWO\"}"
                                        + "]"
                        ),
                        binding
                )
        );

        assertTrue(missing.getMessage().contains("expected exactly one active advertiser account but found 0"));
        assertTrue(ambiguous.getMessage().contains("expected exactly one active advertiser account but found 2"));
    }

    private NoonPullStoreBinding binding(String projectCode, String partnerId, String siteCode) {
        return new NoonPullStoreBinding(
                307L,
                projectCode,
                "STR" + partnerId,
                siteCode,
                partnerId,
                "noon-user",
                "noon-password",
                null,
                "cookie"
        );
    }

    private static final class RecordingSession implements NoonPullGatewaySession {
        private final byte[] response;
        private Map<String, String> headers;

        private RecordingSession(String response) {
            this.response = response.getBytes();
        }

        @Override
        public JsonNode postJson(
                String url,
                JsonNode body,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getBytes(
                String url,
                boolean withProject,
                Map<String, String> extraHeaders
        ) {
            assertEquals("https://ads.test/accounts", url);
            this.headers = extraHeaders;
            return response;
        }
    }
}
