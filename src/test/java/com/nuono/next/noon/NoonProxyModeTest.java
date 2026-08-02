package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.junit.jupiter.api.Test;

class NoonProxyModeTest {

    @Test
    void fixedModeChoosesConfiguredEndpointEvenWhenProviderUrlExists() {
        NoonSessionGateway gateway = gateway("127.0.0.1", 4444, "http://127.0.0.1:1/provider");
        gateway.setProxyMode("FIXED");

        Proxy proxy = gateway.resolveProxy();

        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals(new InetSocketAddress("127.0.0.1", 4444), proxy.address());
    }

    @Test
    void directModeIgnoresLegacyProxyConfiguration() {
        NoonSessionGateway gateway = gateway("127.0.0.1", 4444, "http://127.0.0.1:1/provider");
        gateway.setProxyMode("DIRECT");

        assertNull(gateway.resolveProxy());
    }

    @Test
    void fixedModeFailsBeforeNoonIoWhenEndpointIsMissing() {
        NoonSessionGateway gateway = gateway("", 0, "http://127.0.0.1:1/provider");
        gateway.setProxyMode("FIXED");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                gateway::resolveProxy
        );

        assertTrue(failure.getMessage().contains("FIXED"));
    }

    @Test
    void providerModeRequiresProviderConfiguration() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> NoonProxyMode.resolve("PROVIDER", true, true, false)
        );

        assertTrue(failure.getMessage().contains("PROVIDER"));
    }

    private NoonSessionGateway gateway(String proxyHost, int proxyPort, String providerUrl) {
        return new NoonSessionGateway(
                new ObjectMapper(),
                mock(StoreSyncMapper.class),
                0L,
                true,
                "",
                "",
                "",
                "",
                true,
                "http://noon.test/whoami",
                "http://noon.test/lookup",
                "http://noon.test/pkce",
                "http://noon.test/generate",
                "http://noon.test/validate",
                "http://noon.test/projects",
                "http://noon.test/session-create",
                true,
                "HTTP",
                proxyHost,
                proxyPort,
                providerUrl
        );
    }
}
