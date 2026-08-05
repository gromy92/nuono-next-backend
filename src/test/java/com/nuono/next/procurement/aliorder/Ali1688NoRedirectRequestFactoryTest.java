package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.nuono.next.datapull.orchestration.DataPullAdvanceDeadline;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

class Ali1688NoRedirectRequestFactoryTest {

    @Test
    void doesNotFollowRedirectsEvenForGetRequests() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        AtomicInteger redirectedRequests = new AtomicInteger();
        server.createContext("/target", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/start", exchange -> {
            URI target = URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/target");
            exchange.getResponseHeaders().add("Location", target.toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            RestTemplate client = new RestTemplate(
                    new Ali1688NoRedirectRequestFactory()
            );
            ResponseEntity<String> response = client.getForEntity(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/start",
                    String.class
            );

            assertThat(response.getStatusCodeValue()).isEqualTo(302);
            assertThat(redirectedRequests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void totalDeadlineAbortsSlowDripAndReleasesTheCallerForAnotherRequest()
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService handlers = Executors.newCachedThreadPool((task) -> {
            Thread thread = new Thread(task, "slow-http-test");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(handlers);
        server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 100);
            try {
                for (int index = 0; index < 100; index++) {
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                    Thread.sleep(25L);
                }
            } catch (Exception clientClosed) {
                // Expected when the total deadline disconnects the request.
            } finally {
                exchange.close();
            }
        });
        server.createContext("/ok", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Ali1688NoRedirectRequestFactory factory =
                    new Ali1688NoRedirectRequestFactory(Duration.ofMillis(150));
            factory.setConnectTimeout(1_000);
            factory.setReadTimeout(1_000);
            RestTemplate client = new RestTemplate(factory);
            long started = System.nanoTime();

            assertThatThrownBy(() -> client.getForEntity(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/slow",
                    String.class
            )).isInstanceOf(ResourceAccessException.class);

            assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .isLessThan(Duration.ofSeconds(1));
            Ali1688NoRedirectRequestFactory longFactory =
                    new Ali1688NoRedirectRequestFactory(Duration.ofSeconds(2));
            longFactory.setConnectTimeout(1_000);
            longFactory.setReadTimeout(1_000);
            RestTemplate deadlineBoundClient = new RestTemplate(longFactory);
            long dpStarted = System.nanoTime();
            try (DataPullAdvanceDeadline ignored =
                         DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
                assertThatThrownBy(() -> deadlineBoundClient.getForEntity(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/slow",
                        String.class
                )).isInstanceOf(ResourceAccessException.class);
            }
            assertThat(Duration.ofNanos(System.nanoTime() - dpStarted))
                    .isLessThan(Duration.ofSeconds(1));
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            assertThat(client.getForObject(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/ok",
                    String.class
            )).isEqualTo("ok");
        } finally {
            server.stop(0);
            handlers.shutdownNow();
        }
    }
}
