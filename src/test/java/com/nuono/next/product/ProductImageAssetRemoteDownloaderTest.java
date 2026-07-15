package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
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
import org.junit.jupiter.api.Test;

class ProductImageAssetRemoteDownloaderTest {

    @Test
    void shouldDownloadImageBytes() throws Exception {
        HttpClient client = new StubHttpClient(response(
                200,
                "https://1.1.1.1/path/product",
                "image/jpeg",
                new byte[] {1, 2, 3}
        ));
        ProductImageAssetRemoteDownloader downloader = new ProductImageAssetRemoteDownloader(client);

        ProductImageAssetRemoteDownload download = downloader.download("https://1.1.1.1/path/product");

        assertEquals("product.jpg", download.fileName);
        assertEquals("image/jpeg", download.contentType);
        assertArrayEquals(new byte[] {1, 2, 3}, download.content);
    }

    @Test
    void shouldNormalizeInvisibleCharactersBeforeResolvingDomain() {
        HttpClient client = new StubHttpClient(response(
                200,
                "https://1.1.1.1/path/product",
                "image/jpeg",
                new byte[] {1, 2, 3}
        ));
        ProductImageAssetRemoteDownloader downloader = new ProductImageAssetRemoteDownloader(client);

        ProductImageAssetRemoteDownload download = downloader.download(" https://1.1.1.1\u200B/path/\nproduct ");

        assertEquals("product.jpg", download.fileName);
        assertArrayEquals(new byte[] {1, 2, 3}, download.content);
    }

    @Test
    void shouldUseResponseContentTypeForDownloadedFilenameExtension() {
        HttpClient client = new StubHttpClient(response(
                200,
                "https://1.1.1.1/path/product.jpg",
                "image/avif",
                new byte[] {1, 2, 3}
        ));
        ProductImageAssetRemoteDownloader downloader = new ProductImageAssetRemoteDownloader(client);

        ProductImageAssetRemoteDownload download = downloader.download("https://1.1.1.1/path/product.jpg");

        assertEquals("product.avif", download.fileName);
        assertEquals("image/avif", download.contentType);
    }

    @Test
    void shouldRejectLocalhostUrlsBeforeSendingRequest() {
        ProductImageAssetRemoteDownloader downloader = new ProductImageAssetRemoteDownloader(new StubHttpClient(null));

        assertThrows(
                IllegalArgumentException.class,
                () -> downloader.download("http://127.0.0.1:18081/private.jpg")
        );
    }

    @Test
    void shouldRejectNonImageResponse() throws Exception {
        HttpClient client = new StubHttpClient(response(
                200,
                "https://1.1.1.1/path/product.txt",
                "text/plain",
                new byte[] {1, 2, 3}
        ));
        ProductImageAssetRemoteDownloader downloader = new ProductImageAssetRemoteDownloader(client);

        assertThrows(
                IllegalStateException.class,
                () -> downloader.download("https://1.1.1.1/path/product.txt")
        );
    }

    private static HttpResponse<InputStream> response(
            int status,
            String uri,
            String contentType,
            byte[] content
    ) {
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.uri()).thenReturn(URI.create(uri));
        when(response.headers()).thenReturn(HttpHeaders.of(
                Map.of(
                        "content-type", List.of(contentType),
                        "content-length", List.of(String.valueOf(content.length))
                ),
                (name, value) -> true
        ));
        when(response.body()).thenReturn(new ByteArrayInputStream(content));
        return response;
    }

    private static final class StubHttpClient extends HttpClient {
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
            return Redirect.NORMAL;
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            if (response == null) {
                throw new AssertionError("request should not be sent");
            }
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
}
