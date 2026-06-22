package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HttpClientProductListingImageDownloaderTest {

    private final HttpClientProductListingImageDownloader downloader =
            new HttpClientProductListingImageDownloader();

    @Test
    void rejectsUnsupportedSchemes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> downloader.download("file:///etc/passwd")
        );

        assertTrue(exception.getMessage().contains("Unsupported image URL scheme"));
    }

    @Test
    void rejectsLoopbackImageHosts() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> downloader.download("http://127.0.0.1/image.jpg")
        );

        assertTrue(exception.getMessage().contains("not allowed"));
    }

    @Test
    void rejectsNonImageContentTypes() {
        HttpClientProductListingImageDownloader guardedDownloader = new HttpClientProductListingImageDownloader(
                new StubHttpClient(new StubResponse(Map.of(
                        "content-type",
                        List.of("text/html")
                )))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> guardedDownloader.download("https://8.8.8.8/image.jpg")
        );

        assertTrue(exception.getMessage().contains("Unsupported image content type"));
    }

    @Test
    void rejectsLargeContentLengthBeforeReadingBody() {
        HttpClientProductListingImageDownloader guardedDownloader = new HttpClientProductListingImageDownloader(
                new StubHttpClient(new StubResponse(Map.of(
                        "content-type",
                        List.of("image/jpeg"),
                        "content-length",
                        List.of("11534336")
                )))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> guardedDownloader.download("https://8.8.8.8/image.jpg")
        );

        assertTrue(exception.getMessage().contains("exceeds max size"));
    }

    private static class StubHttpClient extends HttpClient {
        private final HttpResponse<InputStream> response;

        private StubHttpClient(HttpResponse<InputStream> response) {
            this.response = response;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) throws IOException, InterruptedException {
            return (HttpResponse<T>) response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static class StubResponse implements HttpResponse<InputStream> {
        private final HttpHeaders headers;

        private StubResponse(Map<String, List<String>> headers) {
            this.headers = HttpHeaders.of(headers, (name, value) -> true);
        }

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public InputStream body() {
            return new ByteArrayInputStream(new byte[] {1, 2, 3});
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://8.8.8.8/image.jpg");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
