package com.nuono.next.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
        classes = MobileAppHttpsProxyIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class MobileAppHttpsProxyIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @Test
    void treatsProductionHttpsOriginAsSecureSameOriginBehindLocalProxy() throws Exception {
        String response = postFromOrigin("https://www.nuoon.com");

        assertThat(response).startsWith("http/1.1 200");
        assertThat(response).contains("\"secure\":true");
        assertThat(response).contains("\"scheme\":\"https\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "capacitor://localhost"})
    void preservesNativeWarehouseAppOrigins(String origin) throws Exception {
        String response = postFromOrigin(origin);

        assertThat(response).startsWith("http/1.1 200");
        assertThat(response).contains("access-control-allow-origin: " + origin);
    }

    @Test
    void rejectsUntrustedBrowserOrigin() throws Exception {
        String response = postFromOrigin("https://attacker.example");

        assertThat(response).startsWith("http/1.1 403");
        assertThat(response).contains("invalid cors request");
    }

    private String postFromOrigin(String origin) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), serverPort)) {
            String request = "POST /api/proxy-probe HTTP/1.1\r\n"
                    + "Host: www.nuoon.com\r\n"
                    + "Origin: " + origin + "\r\n"
                    + "X-Forwarded-For: 203.0.113.10\r\n"
                    + "X-Forwarded-Proto: https\r\n"
                    + "Content-Length: 0\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({MobileAppCorsWebMvcConfig.class, ProxyProbeController.class})
    static class TestApplication {
    }

    @RestController
    static class ProxyProbeController {

        @PostMapping("/api/proxy-probe")
        Map<String, Object> probe(HttpServletRequest request) {
            return Map.of(
                    "secure", request.isSecure(),
                    "scheme", request.getScheme()
            );
        }
    }
}
