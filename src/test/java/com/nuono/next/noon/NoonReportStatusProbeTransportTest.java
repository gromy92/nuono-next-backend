package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NoonReportStatusProbeTransportTest {

    @Test
    void usesOneProviderRouteForOneReportReadWithoutPreflightOrWhoami() throws Exception {
        try (NoonSessionGatewayPinnedEgressTest.ScriptedProxy proxy =
                     NoonSessionGatewayPinnedEgressTest.ScriptedProxy.connectStatus(200);
                NoonSessionGatewayPinnedEgressTest.ProxyProvider provider =
                     new NoonSessionGatewayPinnedEgressTest.ProxyProvider(proxy.port())) {
            NoonReportStatusProbeTransport transport = new NoonReportStatusProbeTransport(
                    new ObjectMapper(), true, "HTTP", "", 0, provider.url(),
                    "PROVIDER", "", ""
            );

            JsonNode response = transport.poll(
                    "http://noon.test/status",
                    "PRJ108065",
                    "STR108065-NSA",
                    "SA",
                    "EXP4CP4RTOQO",
                    "sid=existing"
            );

            assertEquals("COMPLETE", response.path("export").path("status_code").asText());
            assertEquals(1, provider.requestCount());
            assertEquals(0, proxy.connectCount());
            assertEquals(1, proxy.httpCount());
        }
    }
}
