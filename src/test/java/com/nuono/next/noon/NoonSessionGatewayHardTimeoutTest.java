package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayHardTimeoutTest {

    @Test
    void cancelsNeverCompletingTransportAtRequestDeadline() {
        HttpClient client = mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending = new CompletableFuture<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(pending);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://noon.test/report.csv"))
                .timeout(Duration.ofMillis(25))
                .GET()
                .build();

        assertThrows(
                HttpTimeoutException.class,
                () -> NoonHardDeadlineHttpClient.send(
                        client,
                        request,
                        HttpResponse.BodyHandlers.ofByteArray()
                )
        );

        assertTrue(pending.isCancelled());
    }
}
