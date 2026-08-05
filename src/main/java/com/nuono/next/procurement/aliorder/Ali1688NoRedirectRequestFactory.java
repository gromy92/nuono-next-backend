package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.orchestration.DataPullAdvanceDeadline;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Prevents redirects and aborts every 1688 exchange at one total-call deadline. */
final class Ali1688NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
    static final Duration PRODUCTION_TOTAL_DEADLINE = Duration.ofSeconds(60);
    private static final ScheduledExecutorService DEADLINES =
            Executors.newSingleThreadScheduledExecutor((task) -> {
                Thread thread = new Thread(task, "ali1688-http-deadline");
                thread.setDaemon(true);
                return thread;
            });

    private final Duration totalDeadline;
    private final ThreadLocal<HttpURLConnection> preparedConnection = new ThreadLocal<>();

    Ali1688NoRedirectRequestFactory() {
        this(PRODUCTION_TOTAL_DEADLINE);
    }

    Ali1688NoRedirectRequestFactory(Duration totalDeadline) {
        if (totalDeadline == null || totalDeadline.isZero()
                || totalDeadline.isNegative()) {
            throw new IllegalArgumentException("totalDeadline must be positive");
        }
        this.totalDeadline = totalDeadline;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod)
            throws IOException {
        try {
            ClientHttpRequest request = super.createRequest(uri, httpMethod);
            HttpURLConnection connection = preparedConnection.get();
            if (connection == null) throw new IOException("HTTP connection was not prepared");
            return new DeadlineRequest(request, connection, totalDeadline);
        } finally {
            preparedConnection.remove();
        }
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String method)
            throws IOException {
        super.prepareConnection(connection, method);
        connection.setInstanceFollowRedirects(false);
        preparedConnection.set(connection);
    }

    private static final class DeadlineRequest implements ClientHttpRequest {
        private final ClientHttpRequest delegate;
        private final HttpURLConnection connection;
        private final Duration deadline;

        private DeadlineRequest(
                ClientHttpRequest delegate,
                HttpURLConnection connection,
                Duration deadline
        ) {
            this.delegate = delegate;
            this.connection = connection;
            this.deadline = deadline;
        }

        @Override public HttpMethod getMethod() { return delegate.getMethod(); }
        @Override public String getMethodValue() { return delegate.getMethodValue(); }
        @Override public URI getURI() { return delegate.getURI(); }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public java.io.OutputStream getBody() throws IOException {
            return delegate.getBody();
        }

        @Override
        public ClientHttpResponse execute() throws IOException {
            Duration effectiveDeadline;
            try {
                effectiveDeadline = DataPullAdvanceDeadline.capRemaining(deadline);
            } catch (IllegalStateException expired) {
                throw new java.net.SocketTimeoutException(
                        "1688 OpenAPI DP advance deadline exceeded"
                );
            }
            AtomicBoolean expired = new AtomicBoolean();
            ScheduledFuture<?> timeout = DEADLINES.schedule(
                    () -> {
                        expired.set(true);
                        connection.disconnect();
                    },
                    effectiveDeadline.toNanos(),
                    TimeUnit.NANOSECONDS
            );
            try {
                return new DeadlineResponse(delegate.execute(), timeout, expired);
            } catch (IOException | RuntimeException failure) {
                timeout.cancel(false);
                if (expired.get()) throw new java.net.SocketTimeoutException(
                        "1688 OpenAPI total call deadline exceeded"
                );
                throw failure;
            }
        }
    }

    private static final class DeadlineResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final ScheduledFuture<?> timeout;
        private final AtomicBoolean expired;

        private DeadlineResponse(
                ClientHttpResponse delegate,
                ScheduledFuture<?> timeout,
                AtomicBoolean expired
        ) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.expired = expired;
        }

        @Override public HttpStatus getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }
        @Override public int getRawStatusCode() throws IOException {
            return delegate.getRawStatusCode();
        }
        @Override public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }
        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        @Override public InputStream getBody() throws IOException {
            if (expired.get()) throw new java.net.SocketTimeoutException(
                    "1688 OpenAPI total call deadline exceeded"
            );
            return delegate.getBody();
        }
        @Override public void close() {
            timeout.cancel(false);
            delegate.close();
        }
    }
}
