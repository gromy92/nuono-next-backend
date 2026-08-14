package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayPacingTest {

    @Test
    void waitsForSessionPacingBeforeOneShotReportDownload() throws Exception {
        byte[] export = "date,amount\n2026-08-14,10\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/whoami", exchange -> respond(exchange, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/report.csv", exchange -> {
            exchange.getResponseHeaders().set("ETag", "\"export-v1\"");
            respond(exchange, "text/csv", export);
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            NoonSessionGateway.NoonSession session = gateway(baseUrl + "/whoami")
                    .loginWithPersistedCookie(307L, "merchant@example.com", "sid=valid", "PRJ1", "STORE1");
            NoonBinaryDownloadTestSupport.RecordingSink sink =
                    new NoonBinaryDownloadTestSupport.RecordingSink(1024, 1024);

            session.getBytesOnce(baseUrl + "/report.csv", false, null, sink);

            assertTrue(sink.completed);
            assertArrayEquals(export, sink.joined());
        } finally {
            server.stop(0);
        }
    }

    private NoonSessionGateway gateway(String whoamiUrl) {
        return new NoonSessionGateway(
                new ObjectMapper(), mock(StoreSyncMapper.class), 300L, true,
                "", "", "", "", false, whoamiUrl,
                "", "", "", "", "", "", false, "HTTP", "", 0, ""
        );
    }

    private static void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            String contentType,
            byte[] body
    ) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }
}
