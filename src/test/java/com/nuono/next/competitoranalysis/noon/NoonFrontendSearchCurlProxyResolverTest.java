package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NoonFrontendSearchCurlProxyResolverTest {

    @Test
    void staticHttpProxyBecomesCurlProxy() {
        NoonFrontendSearchCurlProxyResolver resolver = new NoonFrontendSearchCurlProxyResolver(
                true,
                "HTTP",
                "proxy.example",
                18080,
                "",
                () -> ""
        );

        assertEquals("http://proxy.example:18080", resolver.resolveCurlProxy());
    }

    @Test
    void providerProxyResponseBecomesCurlProxy() {
        NoonFrontendSearchCurlProxyResolver resolver = new NoonFrontendSearchCurlProxyResolver(
                true,
                "HTTP",
                "",
                0,
                "configured",
                () -> "{\"data\":[{\"ip\":\"10.0.0.7\",\"port\":\"18888\"}]}"
        );

        assertEquals("http://10.0.0.7:18888", resolver.resolveCurlProxy());
    }

    @Test
    void socksProxyUsesSocks5hCurlScheme() {
        NoonFrontendSearchCurlProxyResolver resolver = new NoonFrontendSearchCurlProxyResolver(
                true,
                "SOCKS",
                "127.0.0.1",
                1080,
                "",
                () -> ""
        );

        assertEquals("socks5h://127.0.0.1:1080", resolver.resolveCurlProxy());
    }
}
